package com.fixtech.service;

import com.fixtech.dto.AjustePrecioResponse;
import com.fixtech.dto.RespuestaAjusteRequest;
import com.fixtech.entity.AjustePrecio;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.enums.CanceladoPor;
import com.fixtech.entity.enums.EstadoAjuste;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.BusinessException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.mapper.ServicioMapper;
import com.fixtech.repository.AjustePrecioRepository;
import com.fixtech.repository.ServicioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AjustePrecioService {

    private static final Set<EstadoServicio> ESTADOS_PROPONIBLES = EnumSet.of(
            EstadoServicio.EN_DIAGNOSTICO,
            EstadoServicio.EN_PROCESO,
            EstadoServicio.EN_REPARACION);

    private static final int MAX_JUSTIFICACION = 500;

    private final AjustePrecioRepository ajustePrecioRepository;
    private final ServicioRepository servicioRepository;
    private final ServicioAccesoService servicioAccesoService;
    private final ServicioEventoService servicioEventoService;
    private final ArchivoService archivoService;
    private final ServicioMapper servicioMapper;

    @Transactional
    public AjustePrecioResponse proponer(
            Long usuarioId,
            Long servicioId,
            BigDecimal montoNuevo,
            String justificacion,
            MultipartFile evidencia) {
        Servicio servicio = servicioAccesoService.bloquear(servicioId);
        servicioAccesoService.validarTecnico(servicio, usuarioId);

        if (!ESTADOS_PROPONIBLES.contains(servicio.getEstado())) {
            throw new BusinessException(
                    "El ajuste de precio se propone durante el diagnóstico o la reparación");
        }
        if (ajustePrecioRepository.existsByServicioIdAndEstado(
                servicioId, EstadoAjuste.PENDIENTE)) {
            throw new BusinessException(
                    "Ya existe un ajuste de precio pendiente para este servicio");
        }
        if (montoNuevo == null) {
            throw new BadRequestException("Debe indicar el monto propuesto");
        }
        String justificacionNormalizada = validarJustificacion(justificacion);

        BigDecimal montoAnterior = servicio.getMontoTotal()
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal monto = montoNuevo.setScale(2, RoundingMode.HALF_UP);
        if (monto.compareTo(montoAnterior) <= 0) {
            throw new BadRequestException(
                    "El monto propuesto debe ser mayor al monto actual de S/ %s"
                            .formatted(montoAnterior.toPlainString()));
        }

        String ruta = evidencia != null && !evidencia.isEmpty()
                ? archivoService.guardarImagenServicio(servicioId, "ajustes", evidencia)
                : null;
        try {
            AjustePrecio ajuste = AjustePrecio.builder()
                    .servicio(servicio)
                    .montoAnterior(montoAnterior)
                    .montoNuevo(monto)
                    .justificacion(justificacionNormalizada)
                    .rutaEvidencia(ruta)
                    .estado(EstadoAjuste.PENDIENTE)
                    .build();
            ajustePrecioRepository.save(ajuste);

            EstadoServicio anterior = servicio.getEstado();
            servicio.setEstado(EstadoServicio.PENDIENTE_AJUSTE);
            servicioRepository.save(servicio);
            servicioEventoService.registrarCambio(
                    servicio,
                    anterior,
                    EstadoServicio.PENDIENTE_AJUSTE,
                    usuarioId,
                    "Ajuste de precio propuesto de S/ %s".formatted(monto.toPlainString()));
            servicioEventoService.notificarContraparte(
                    servicio,
                    usuarioId,
                    TipoNotificacion.AJUSTE_PROPUESTO,
                    "Ajuste de precio propuesto",
                    "El técnico propone un nuevo monto de S/ %s. Revise la justificación y responda."
                            .formatted(monto.toPlainString()));

            return servicioMapper.toAjuste(ajuste);
        } catch (RuntimeException ex) {
            if (ruta != null) {
                archivoService.eliminar(ruta);
            }
            throw ex;
        }
    }

    @Transactional
    public AjustePrecioResponse responder(
            Long usuarioId, Long ajusteId, RespuestaAjusteRequest request) {
        AjustePrecio referencia = ajustePrecioRepository.findById(ajusteId)
                .orElseThrow(() -> new NotFoundException("Ajuste de precio no encontrado"));
        Servicio servicio = servicioAccesoService.bloquear(
                referencia.getServicio().getId());
        servicioAccesoService.validarCliente(servicio, usuarioId);

        AjustePrecio ajuste = ajustePrecioRepository.findByIdForUpdate(ajusteId)
                .orElseThrow(() -> new NotFoundException("Ajuste de precio no encontrado"));
        if (ajuste.getEstado() != EstadoAjuste.PENDIENTE) {
            throw new BusinessException("El ajuste de precio ya fue respondido");
        }
        if (request.estado() == EstadoAjuste.PENDIENTE) {
            throw new BadRequestException(
                    "Debe aprobar o rechazar el ajuste de precio");
        }
        if (servicio.getEstado() != EstadoServicio.PENDIENTE_AJUSTE) {
            throw new BusinessException(
                    "El servicio no se encuentra pendiente de ajuste de precio");
        }

        ajuste.setEstado(request.estado());
        ajuste.setFechaRespuesta(LocalDateTime.now());
        ajustePrecioRepository.save(ajuste);

        if (request.estado() == EstadoAjuste.APROBADO) {
            aprobar(servicio, ajuste, usuarioId);
        } else {
            rechazar(servicio, ajuste, usuarioId);
        }
        return servicioMapper.toAjuste(ajuste);
    }

    @Transactional(readOnly = true)
    public ArchivoService.RecursoImagen obtenerEvidencia(
            Long usuarioId, Long ajusteId) {
        AjustePrecio ajuste = ajustePrecioRepository.findById(ajusteId)
                .orElseThrow(() -> new NotFoundException("Ajuste de precio no encontrado"));
        servicioAccesoService.validarParticipante(
                ajuste.getServicio(), usuarioId);
        if (ajuste.getRutaEvidencia() == null) {
            throw new NotFoundException("El ajuste no tiene evidencia adjunta");
        }
        return archivoService.cargar(ajuste.getRutaEvidencia());
    }

    private void aprobar(Servicio servicio, AjustePrecio ajuste, Long usuarioId) {
        BigDecimal movilidad = servicio.getCostoMovilidad() == null
                ? BigDecimal.ZERO
                : servicio.getCostoMovilidad().setScale(2, RoundingMode.HALF_UP);
        BigDecimal precioServicio = ajuste.getMontoNuevo()
                .subtract(movilidad)
                .setScale(2, RoundingMode.HALF_UP);
        if (precioServicio.signum() < 0) {
            throw new BusinessException(
                    "El monto aprobado no cubre el costo de movilidad del servicio");
        }

        servicio.setPrecioServicio(precioServicio);
        servicio.setMontoTotal(ajuste.getMontoNuevo().setScale(2, RoundingMode.HALF_UP));
        servicio.setEstado(EstadoServicio.EN_DIAGNOSTICO);
        servicioRepository.save(servicio);

        servicioEventoService.registrarCambio(
                servicio,
                EstadoServicio.PENDIENTE_AJUSTE,
                EstadoServicio.EN_DIAGNOSTICO,
                usuarioId,
                "Ajuste de precio aprobado; nuevo monto S/ %s"
                        .formatted(ajuste.getMontoNuevo().toPlainString()));
        servicioEventoService.notificarContraparte(
                servicio,
                usuarioId,
                TipoNotificacion.AJUSTE_APROBADO,
                "Ajuste de precio aprobado",
                "El cliente aprobó el nuevo monto de S/ %s. Puede continuar con el servicio."
                        .formatted(ajuste.getMontoNuevo().toPlainString()));
    }

    private void rechazar(Servicio servicio, AjustePrecio ajuste, Long usuarioId) {
        servicio.setEstado(EstadoServicio.CANCELADO);
        servicio.setCanceladoPor(CanceladoPor.CLIENTE);
        servicio.setMotivoCancelacion(
                "Ajuste de precio rechazado: " + ajuste.getJustificacion());
        servicioRepository.save(servicio);

        servicioEventoService.registrarCambio(
                servicio,
                EstadoServicio.PENDIENTE_AJUSTE,
                EstadoServicio.CANCELADO,
                usuarioId,
                "Ajuste de precio rechazado; el servicio fue cancelado");
        servicioEventoService.notificarContraparte(
                servicio,
                usuarioId,
                TipoNotificacion.AJUSTE_RECHAZADO,
                "Ajuste de precio rechazado",
                "El cliente rechazó el ajuste de S/ %s y el servicio fue cancelado."
                        .formatted(ajuste.getMontoNuevo().toPlainString()));
    }

    private String validarJustificacion(String justificacion) {
        if (!StringUtils.hasText(justificacion)) {
            throw new BadRequestException(
                    "La justificación del ajuste es obligatoria");
        }
        String normalizada = justificacion.trim();
        if (normalizada.length() > MAX_JUSTIFICACION) {
            throw new BadRequestException(
                    "La justificación no puede superar los %d caracteres"
                            .formatted(MAX_JUSTIFICACION));
        }
        return normalizada;
    }
}
