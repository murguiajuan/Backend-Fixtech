package com.fixtech.service;

import com.fixtech.dto.ComisionResponse;
import com.fixtech.dto.MiSaldoComisionesResponse;
import com.fixtech.entity.Comision;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.enums.EstadoComision;
import com.fixtech.entity.enums.ParametroClave;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.mapper.ComisionMapper;
import com.fixtech.repository.ComisionRepository;
import com.fixtech.repository.PagoComisionRepository;
import com.fixtech.repository.TecnicoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComisionConsultaService {

    private final ComisionRepository comisionRepository;
    private final PagoComisionRepository pagoComisionRepository;
    private final TecnicoRepository tecnicoRepository;
    private final ParametroSistemaService parametroSistemaService;
    private final ComisionMapper comisionMapper;

    public MiSaldoComisionesResponse obtenerMiSaldo(Long usuarioId) {
        Tecnico tecnico = obtenerTecnico(usuarioId);
        List<Comision> pendientes = comisionRepository
                .findByTecnicoIdAndEstadoOrderByFechaLimitePagoAscIdAsc(
                        tecnico.getId(), EstadoComision.PENDIENTE);
        BigDecimal saldo = sumar(pendientes);
        BigDecimal totalPagado = comisionRepository.sumarMontoPorTecnicoYEstado(
                tecnico.getId(), EstadoComision.PAGADA);
        totalPagado = totalPagado == null
                ? BigDecimal.ZERO.setScale(2)
                : totalPagado.setScale(2, RoundingMode.HALF_UP);
        BigDecimal limite = parametroSistemaService.obtenerDecimal(
                        ParametroClave.LIMITE_DEUDA_COMISION)
                .setScale(2, RoundingMode.HALF_UP);

        LocalDate hoy = LocalDate.now();
        int vencidas = (int) pendientes.stream()
                .filter(comision -> comision.getFechaLimitePago().isBefore(hoy))
                .count();
        LocalDate proximoVencimiento = pendientes.stream()
                .map(Comision::getFechaLimitePago)
                .min(LocalDate::compareTo)
                .orElse(null);

        return new MiSaldoComisionesResponse(
                saldo,
                totalPagado,
                limite,
                tecnico.isBloqueadoPorDeuda(),
                vencidas,
                proximoVencimiento,
                pendientes.stream().map(comisionMapper::toResponse).toList(),
                pagoComisionRepository
                        .findByTecnicoIdOrderByCreadoEnDescIdDesc(tecnico.getId())
                        .stream()
                        .map(comisionMapper::toResponse)
                        .toList());
    }

    public List<ComisionResponse> listarMias(Long usuarioId, EstadoComision estado) {
        Tecnico tecnico = obtenerTecnico(usuarioId);
        EstadoComision filtro = estado == null ? EstadoComision.PENDIENTE : estado;
        return comisionRepository
                .findByTecnicoIdAndEstadoOrderByFechaLimitePagoAscIdAsc(
                        tecnico.getId(), filtro)
                .stream()
                .map(comisionMapper::toResponse)
                .toList();
    }

    private BigDecimal sumar(List<Comision> comisiones) {
        return comisiones.stream()
                .map(Comision::getMontoComision)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Tecnico obtenerTecnico(Long usuarioId) {
        return tecnicoRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new ForbiddenException(
                        "El usuario no tiene perfil de técnico"));
    }
}
