package com.fixtech.service;

import com.fixtech.dto.DeudaTecnicoResponse;
import com.fixtech.entity.Comision;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.enums.EstadoComision;
import com.fixtech.entity.enums.ParametroClave;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.NotFoundException;
import com.fixtech.repository.ComisionRepository;
import com.fixtech.repository.TecnicoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeudaTecnicoService {

    private final ComisionRepository comisionRepository;
    private final TecnicoRepository tecnicoRepository;
    private final ParametroSistemaService parametroSistemaService;
    private final NotificacionService notificacionService;

    @Transactional
    public void sincronizar(Tecnico tecnico) {
        Tecnico gestionado = tecnicoRepository.findByIdForUpdate(tecnico.getId())
                .orElseThrow(() -> new NotFoundException("Técnico no encontrado"));
        sincronizarInterno(gestionado);
    }

    @Transactional
    public int evaluarTodos() {
        Set<Long> tecnicoIds = new LinkedHashSet<>();
        tecnicoIds.addAll(comisionRepository.buscarTecnicoIdsPorEstado(
                EstadoComision.PENDIENTE));
        tecnicoIds.addAll(tecnicoRepository.buscarIdsBloqueadosPorDeuda());

        int cambios = 0;
        for (Long tecnicoId : tecnicoIds) {
            Tecnico tecnico = tecnicoRepository.findByIdForUpdate(tecnicoId).orElse(null);
            if (tecnico == null) {
                continue;
            }
            boolean antes = tecnico.isBloqueadoPorDeuda();
            sincronizarInterno(tecnico);
            if (antes != tecnico.isBloqueadoPorDeuda()) {
                cambios++;
            }
        }
        return cambios;
    }

    @Transactional(readOnly = true)
    public List<DeudaTecnicoResponse> listarDeudores() {
        BigDecimal limite = limiteDeuda();
        List<Comision> pendientes = comisionRepository
                .findByEstadoOrderByFechaLimitePagoAscIdAsc(EstadoComision.PENDIENTE);
        Map<Long, List<Comision>> porTecnico = pendientes.stream()
                .collect(Collectors.groupingBy(
                        comision -> comision.getTecnico().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<DeudaTecnicoResponse> deudores = new ArrayList<>();
        for (List<Comision> comisiones : porTecnico.values()) {
            deudores.add(aDeuda(comisiones.get(0).getTecnico(), comisiones, limite));
        }
        for (Tecnico tecnico : tecnicoRepository.findByBloqueadoPorDeudaTrue()) {
            if (!porTecnico.containsKey(tecnico.getId())) {
                deudores.add(aDeuda(tecnico, List.of(), limite));
            }
        }
        deudores.sort(Comparator
                .comparing(DeudaTecnicoResponse::saldoPendiente)
                .reversed()
                .thenComparing(DeudaTecnicoResponse::tecnicoId));
        return deudores;
    }

    private void sincronizarInterno(Tecnico tecnico) {
        BigDecimal saldo = comisionRepository.sumarMontoPorTecnicoYEstado(
                tecnico.getId(), EstadoComision.PENDIENTE);
        saldo = saldo == null
                ? BigDecimal.ZERO.setScale(2)
                : saldo.setScale(2, RoundingMode.HALF_UP);
        long vencidas = comisionRepository
                .countByTecnicoIdAndEstadoAndFechaLimitePagoBefore(
                        tecnico.getId(), EstadoComision.PENDIENTE, LocalDate.now());
        BigDecimal limite = limiteDeuda();
        boolean bloquear = saldo.compareTo(limite) > 0 || vencidas > 0;
        boolean antes = tecnico.isBloqueadoPorDeuda();

        tecnico.setSaldoComisionPendiente(saldo);
        tecnico.setBloqueadoPorDeuda(bloquear);
        tecnicoRepository.save(tecnico);

        if (antes != bloquear) {
            notificarCambio(tecnico, bloquear, saldo, limite, vencidas);
        }
    }

    private DeudaTecnicoResponse aDeuda(
            Tecnico tecnico, List<Comision> comisiones, BigDecimal limite) {
        BigDecimal saldo = comisiones.stream()
                .map(Comision::getMontoComision)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        LocalDate hoy = LocalDate.now();
        int vencidas = (int) comisiones.stream()
                .filter(comision -> comision.getFechaLimitePago().isBefore(hoy))
                .count();
        LocalDate proximo = comisiones.stream()
                .map(Comision::getFechaLimitePago)
                .min(LocalDate::compareTo)
                .orElse(null);
        return new DeudaTecnicoResponse(
                tecnico.getId(),
                (tecnico.getNombres() + " " + tecnico.getApellidos()).trim(),
                tecnico.getUsuario().getCorreo(),
                saldo,
                limite,
                tecnico.isBloqueadoPorDeuda(),
                comisiones.size(),
                vencidas,
                proximo);
    }

    private void notificarCambio(
            Tecnico tecnico,
            boolean bloqueado,
            BigDecimal saldo,
            BigDecimal limite,
            long vencidas) {
        String titulo;
        String mensaje;
        if (bloqueado) {
            titulo = "Bloqueo por deuda de comisiones";
            mensaje = vencidas > 0
                    ? "Su cuenta fue bloqueada: tiene %d comisión(es) vencida(s) por S/ %s."
                            .formatted(vencidas, saldo.toPlainString())
                    : "Su cuenta fue bloqueada: el saldo de S/ %s supera el límite de S/ %s."
                            .formatted(saldo.toPlainString(), limite.toPlainString());
        } else {
            titulo = "Bloqueo por deuda retirado";
            mensaje = "Su cuenta fue desbloqueada. Saldo pendiente actual: S/ %s."
                    .formatted(saldo.toPlainString());
        }
        notificacionService.crear(
                tecnico.getUsuario().getId(),
                bloqueado ? TipoNotificacion.BLOQUEO_DEUDA
                        : TipoNotificacion.DESBLOQUEO_DEUDA,
                titulo,
                mensaje,
                "TECNICO",
                tecnico.getId());
    }

    private BigDecimal limiteDeuda() {
        BigDecimal limite = parametroSistemaService.obtenerDecimal(
                ParametroClave.LIMITE_DEUDA_COMISION);
        return limite == null
                ? BigDecimal.ZERO.setScale(2)
                : limite.setScale(2, RoundingMode.HALF_UP);
    }
}
