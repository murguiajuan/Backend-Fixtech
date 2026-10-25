package com.fixtech.service;

import com.fixtech.dto.CambioEstadoServicioRequest;
import com.fixtech.dto.FinalizarServicioRequest;
import com.fixtech.dto.ServicioResponse;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.enums.CanceladoPor;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.ParametroClave;
import com.fixtech.entity.enums.TipoConstancia;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.BusinessException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.mapper.ServicioMapper;
import com.fixtech.repository.ConstanciaRepository;
import com.fixtech.repository.ServicioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ServicioEjecucionService {

    private static final Set<EstadoServicio> ESTADOS_CANCELABLES = EnumSet.of(
            EstadoServicio.PROGRAMADO,
            EstadoServicio.TECNICO_EN_CAMINO,
            EstadoServicio.EQUIPO_RECIBIDO,
            EstadoServicio.EN_DIAGNOSTICO,
            EstadoServicio.PENDIENTE_AJUSTE,
            EstadoServicio.EN_PROCESO,
            EstadoServicio.EN_REPARACION,
            EstadoServicio.LISTO_PARA_RECOGER);

    private final ServicioRepository servicioRepository;
    private final ConstanciaRepository constanciaRepository;
    private final ServicioAccesoService servicioAccesoService;
    private final ServicioEventoService servicioEventoService;
    private final ParametroSistemaService parametroSistemaService;
    private final ServicioMapper servicioMapper;

    @Transactional
    public ServicioResponse cambiarEstado(
            Long usuarioId,
            Long servicioId,
            CambioEstadoServicioRequest request) {
        Servicio servicio = servicioAccesoService.bloquear(servicioId);
        boolean esCliente = servicioAccesoService.esCliente(servicio, usuarioId);
        boolean esTecnico = servicioAccesoService.esTecnico(servicio, usuarioId);
        if (!esCliente && !esTecnico) {
            throw new ForbiddenException("No tiene acceso a este servicio");
        }

        EstadoServicio actual = servicio.getEstado();
        EstadoServicio objetivo = request.estado();
        if (objetivo == actual) {
            throw new BusinessException(
                    "El servicio ya se encuentra en estado %s".formatted(actual));
        }

        if (objetivo == EstadoServicio.CANCELADO) {
            cancelar(servicio, usuarioId, esCliente, request.motivo());
            return servicioMapper.toResponse(servicio);
        }

        if (!esTecnico) {
            throw new ForbiddenException(
                    "Solo el técnico puede cambiar el estado del servicio");
        }

        validarTransicion(servicio, objetivo);
        if (actual == EstadoServicio.TECNICO_EN_CAMINO) {
            validarPin(servicio, request.pin());
            servicio.setPinValidado(true);
            servicio.setFechaInicio(LocalDateTime.now());
        }

        servicio.setEstado(objetivo);
        servicioRepository.save(servicio);
        servicioEventoService.registrarCambio(
                servicio,
                actual,
                objetivo,
                usuarioId,
                normalizarTexto(request.comentario()));
        notificarCambio(servicio, usuarioId, actual, objetivo);

        return servicioMapper.toResponse(servicio);
    }

    @Transactional
    public ServicioResponse finalizar(
            Long usuarioId, Long servicioId, FinalizarServicioRequest request) {
        Servicio servicio = servicioAccesoService.bloquear(servicioId);
        servicioAccesoService.validarTecnico(servicio, usuarioId);

        if (servicio.getModalidad() == Modalidad.DOMICILIO) {
            if (servicio.getEstado() != EstadoServicio.EN_PROCESO) {
                throw new BusinessException(
                        "El servicio a domicilio debe estar EN_PROCESO para finalizar");
            }
            if (!servicio.isPinValidado()) {
                throw new BusinessException(
                        "Debe validar el PIN de inicio antes de finalizar");
            }
        } else {
            if (servicio.getEstado() != EstadoServicio.LISTO_PARA_RECOGER) {
                throw new BusinessException(
                        "El servicio en taller debe estar LISTO_PARA_RECOGER para finalizar");
            }
            if (!constanciaRepository.existsByServicioIdAndTipo(
                    servicioId, TipoConstancia.ENTREGA)) {
                throw new BusinessException(
                        "Debe registrar la constancia de entrega antes de finalizar");
            }
        }

        BigDecimal montoFinal = request.montoFinal().setScale(2, RoundingMode.HALF_UP);
        if (montoFinal.compareTo(servicio.getMontoTotal()) != 0) {
            throw new BadRequestException(
                    "El monto final debe coincidir con el monto total aprobado de S/ %s"
                            .formatted(servicio.getMontoTotal()
                                    .setScale(2, RoundingMode.HALF_UP)
                                    .toPlainString()));
        }

        EstadoServicio anterior = servicio.getEstado();
        LocalDateTime ahora = LocalDateTime.now();
        int horas = Math.max(1, parametroSistemaService.obtenerEntero(
                ParametroClave.CONFIRMACION_AUTOMATICA_HORAS));

        servicio.setMontoFinal(montoFinal);
        servicio.setMetodoPago(request.metodoPago());
        servicio.setEstado(EstadoServicio.FINALIZADO);
        servicio.setFechaFinalizacion(ahora);
        servicio.setFechaLimiteConformidad(ahora.plusHours(horas));
        servicioRepository.save(servicio);

        servicioEventoService.registrarCambio(
                servicio,
                anterior,
                EstadoServicio.FINALIZADO,
                usuarioId,
                "Servicio finalizado con monto S/ %s y método %s"
                        .formatted(montoFinal.toPlainString(), request.metodoPago()));
        servicioEventoService.notificarContraparte(
                servicio,
                usuarioId,
                TipoNotificacion.SERVICIO_FINALIZADO,
                "Servicio finalizado",
                "El técnico finalizó el servicio. Confirme su conformidad dentro de las próximas %d horas."
                        .formatted(horas));

        return servicioMapper.toResponse(servicio);
    }

    private void validarTransicion(Servicio servicio, EstadoServicio objetivo) {
        EstadoServicio actual = servicio.getEstado();
        boolean permitida = switch (actual) {
            case PROGRAMADO -> (objetivo == EstadoServicio.TECNICO_EN_CAMINO
                    && servicio.getModalidad() == Modalidad.DOMICILIO)
                    || (objetivo == EstadoServicio.EQUIPO_RECIBIDO
                    && servicio.getModalidad() == Modalidad.TALLER
                    && constanciaRepository.existsByServicioIdAndTipo(
                            servicio.getId(), TipoConstancia.RECEPCION));
            case TECNICO_EN_CAMINO -> objetivo == EstadoServicio.EN_PROCESO
                    && servicio.getModalidad() == Modalidad.DOMICILIO;
            case EQUIPO_RECIBIDO -> objetivo == EstadoServicio.EN_DIAGNOSTICO;
            case EN_PROCESO -> objetivo == EstadoServicio.EN_DIAGNOSTICO;
            case EN_DIAGNOSTICO -> (objetivo == EstadoServicio.EN_PROCESO
                    && servicio.getModalidad() == Modalidad.DOMICILIO)
                    || (objetivo == EstadoServicio.EN_REPARACION
                    && servicio.getModalidad() == Modalidad.TALLER);
            case EN_REPARACION -> objetivo == EstadoServicio.LISTO_PARA_RECOGER
                    && servicio.getModalidad() == Modalidad.TALLER;
            case LISTO_PARA_RECOGER -> throw new BusinessException(
                    "El servicio está listo para recoger; registre la constancia de entrega");
            case PENDIENTE_AJUSTE -> throw new BusinessException(
                    "Debe responder el ajuste de precio pendiente");
            case FINALIZADO -> throw new BusinessException(
                    "El servicio ya fue finalizado; registre la conformidad");
            case CONFIRMADO, CALIFICADO -> throw new BusinessException(
                    "El servicio ya fue confirmado");
            case CANCELADO -> throw new BusinessException(
                    "El servicio está cancelado");
        };

        if (!permitida) {
            throw new BusinessException(
                    "Transición no permitida de %s a %s".formatted(actual, objetivo));
        }
    }

    private void validarPin(Servicio servicio, String pin) {
        if (!StringUtils.hasText(servicio.getPinInicio())) {
            throw new BusinessException("El servicio no tiene un PIN de inicio configurado");
        }
        if (!StringUtils.hasText(pin)) {
            throw new BadRequestException("Debe ingresar el PIN de inicio");
        }
        boolean coincide = MessageDigest.isEqual(
                servicio.getPinInicio().getBytes(StandardCharsets.UTF_8),
                pin.getBytes(StandardCharsets.UTF_8));
        if (!coincide) {
            throw new BadRequestException("El PIN de inicio es incorrecto");
        }
    }

    private void cancelar(
            Servicio servicio, Long usuarioId, boolean esCliente, String motivo) {
        if (!ESTADOS_CANCELABLES.contains(servicio.getEstado())) {
            throw new BusinessException(
                    "El servicio no se puede cancelar en su estado actual");
        }
        if (!StringUtils.hasText(motivo)) {
            throw new BadRequestException("El motivo de cancelación es obligatorio");
        }

        EstadoServicio anterior = servicio.getEstado();
        String motivoNormalizado = motivo.trim();
        if (motivoNormalizado.length() > 300) {
            motivoNormalizado = motivoNormalizado.substring(0, 300);
        }

        servicio.setEstado(EstadoServicio.CANCELADO);
        servicio.setCanceladoPor(esCliente ? CanceladoPor.CLIENTE : CanceladoPor.TECNICO);
        servicio.setMotivoCancelacion(motivoNormalizado);
        servicioRepository.save(servicio);

        servicioEventoService.registrarCambio(
                servicio,
                anterior,
                EstadoServicio.CANCELADO,
                usuarioId,
                "Cancelación: " + motivoNormalizado);
        servicioEventoService.notificarContraparte(
                servicio,
                usuarioId,
                TipoNotificacion.SERVICIO_CANCELADO,
                "Servicio cancelado",
                "%s canceló el servicio. Motivo: %s"
                        .formatted(
                                esCliente ? "El cliente" : "El técnico",
                                motivoNormalizado));
    }

    private void notificarCambio(
            Servicio servicio,
            Long usuarioId,
            EstadoServicio anterior,
            EstadoServicio nuevo) {
        switch (nuevo) {
            case TECNICO_EN_CAMINO -> servicioEventoService.notificarContraparte(
                    servicio,
                    usuarioId,
                    TipoNotificacion.SERVICIO_EN_CAMINO,
                    "Técnico en camino",
                    "El técnico está en camino al domicilio.");
            case EN_PROCESO -> servicioEventoService.notificarContraparte(
                    servicio,
                    usuarioId,
                    TipoNotificacion.SERVICIO_INICIADO,
                    anterior == EstadoServicio.TECNICO_EN_CAMINO
                            ? "Servicio iniciado"
                            : "Trabajo reanudado",
                    anterior == EstadoServicio.TECNICO_EN_CAMINO
                            ? "El PIN fue validado y el servicio inició."
                            : "El trabajo continúa tras el diagnóstico.");
            case EQUIPO_RECIBIDO -> servicioEventoService.notificarContraparte(
                    servicio,
                    usuarioId,
                    TipoNotificacion.EQUIPO_RECIBIDO,
                    "Equipo recibido",
                    "El equipo fue registrado como recibido en el taller.");
            case EN_DIAGNOSTICO -> servicioEventoService.notificarContraparte(
                    servicio,
                    usuarioId,
                    TipoNotificacion.AVANCE_REGISTRADO,
                    "Diagnóstico en curso",
                    "El técnico se encuentra diagnosticando el equipo.");
            case EN_REPARACION -> servicioEventoService.notificarContraparte(
                    servicio,
                    usuarioId,
                    TipoNotificacion.AVANCE_REGISTRADO,
                    "Reparación en curso",
                    "El técnico continúa con la reparación del equipo.");
            case LISTO_PARA_RECOGER -> servicioEventoService.notificarContraparte(
                    servicio,
                    usuarioId,
                    TipoNotificacion.SERVICIO_LISTO,
                    "Equipo listo para recoger",
                    "El equipo está listo para ser recogido en el taller.");
            default -> {
            }
        }
    }

    private String normalizarTexto(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }
}
