package com.fixtech.service;

import com.fixtech.dto.ServicioResponse;
import com.fixtech.entity.Comision;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BusinessException;
import com.fixtech.mapper.ServicioMapper;
import com.fixtech.repository.ServicioRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConformidadService {

    private final ServicioRepository servicioRepository;
    private final ServicioAccesoService servicioAccesoService;
    private final ServicioEventoService servicioEventoService;
    private final ComisionService comisionService;
    private final ServicioMapper servicioMapper;

    @Transactional
    public ServicioResponse confirmar(Long usuarioId, Long servicioId) {
        Servicio servicio = servicioAccesoService.bloquear(servicioId);
        servicioAccesoService.validarCliente(servicio, usuarioId);
        if (servicio.getEstado() != EstadoServicio.FINALIZADO) {
            throw new BusinessException(
                    "Solo se puede confirmar un servicio finalizado");
        }
        confirmarInterno(servicio, usuarioId, false);
        return servicioMapper.toResponse(servicio);
    }

    @Transactional
    public boolean confirmarAutomaticamente(Long servicioId) {
        Servicio servicio = servicioAccesoService.bloquear(servicioId);
        if (servicio.getEstado() != EstadoServicio.FINALIZADO
                || servicio.getFechaLimiteConformidad() == null
                || servicio.getFechaLimiteConformidad().isAfter(LocalDateTime.now())) {
            return false;
        }
        confirmarInterno(servicio, null, true);
        return true;
    }

    @Transactional
    public int confirmarVencidos() {
        List<Long> ids = servicioRepository.buscarIdsParaConfirmacionAutomatica(
                EstadoServicio.FINALIZADO, LocalDateTime.now());
        int confirmados = 0;
        for (Long id : ids) {
            if (confirmarAutomaticamente(id)) {
                confirmados++;
            }
        }
        return confirmados;
    }

    private void confirmarInterno(
            Servicio servicio, Long usuarioId, boolean automatica) {
        LocalDateTime ahora = LocalDateTime.now();
        servicio.setEstado(EstadoServicio.CONFIRMADO);
        servicio.setFechaConfirmacion(ahora);
        servicio.setConfirmadoAutomaticamente(automatica);
        servicioRepository.save(servicio);

        servicioEventoService.registrarCambio(
                servicio,
                EstadoServicio.FINALIZADO,
                EstadoServicio.CONFIRMADO,
                usuarioId,
                automatica
                        ? "Confirmación automática por vencimiento del plazo"
                        : "Conformidad registrada por el cliente");

        Comision comision = comisionService.registrarParaServicio(servicio);
        Long clienteId = servicio.getCliente().getUsuario().getId();
        Long tecnicoId = servicio.getTecnico().getUsuario().getId();

        if (automatica) {
            servicioEventoService.notificar(
                    clienteId,
                    TipoNotificacion.SERVICIO_CONFIRMADO,
                    "Servicio confirmado automáticamente",
                    "El servicio #%d se confirmó automáticamente al vencer el plazo de conformidad."
                            .formatted(servicio.getId()),
                    servicio.getId());
            servicioEventoService.notificar(
                    tecnicoId,
                    TipoNotificacion.SERVICIO_CONFIRMADO,
                    "Servicio confirmado automáticamente",
                    "El servicio #%d se confirmó automáticamente al vencer el plazo."
                            .formatted(servicio.getId()),
                    servicio.getId());
        } else {
            servicioEventoService.notificar(
                    clienteId,
                    TipoNotificacion.SERVICIO_CONFIRMADO,
                    "Conformidad registrada",
                    "Confirmó el servicio #%d. Gracias por usar FIXTECH."
                            .formatted(servicio.getId()),
                    servicio.getId());
            servicioEventoService.notificarContraparte(
                    servicio,
                    usuarioId,
                    TipoNotificacion.SERVICIO_CONFIRMADO,
                    "Servicio confirmado",
                    "El cliente confirmó la conformidad del servicio #%d."
                            .formatted(servicio.getId()));
        }

        servicioEventoService.notificar(
                tecnicoId,
                TipoNotificacion.COMISION_GENERADA,
                "Comisión generada",
                "Se generó una comisión de S/ %s con vencimiento el %s."
                        .formatted(
                                comision.getMontoComision().toPlainString(),
                                comision.getFechaLimitePago()),
                servicio.getId());
    }
}
