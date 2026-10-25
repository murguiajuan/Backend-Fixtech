package com.fixtech.service;

import com.fixtech.dto.PagoComisionResponse;
import com.fixtech.dto.ValidacionPagoComisionRequest;
import com.fixtech.entity.Comision;
import com.fixtech.entity.PagoComision;
import com.fixtech.entity.PagoComisionDetalle;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.enums.EstadoComision;
import com.fixtech.entity.enums.EstadoPagoComision;
import com.fixtech.entity.enums.MetodoPago;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.BusinessException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.mapper.ComisionMapper;
import com.fixtech.repository.AdministradorRepository;
import com.fixtech.repository.ComisionRepository;
import com.fixtech.repository.PagoComisionDetalleRepository;
import com.fixtech.repository.PagoComisionRepository;
import com.fixtech.repository.TecnicoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PagoComisionService {

    private static final Set<MetodoPago> METODOS_PERMITIDOS = EnumSet.of(
            MetodoPago.YAPE, MetodoPago.PLIN, MetodoPago.TRANSFERENCIA);
    private static final Set<EstadoPagoComision> PAGOS_ACTIVOS = EnumSet.of(
            EstadoPagoComision.PENDIENTE, EstadoPagoComision.VALIDADO);
    private static final int MAX_OBSERVACION = 300;
    private static final int MAX_NUMERO_OPERACION = 40;

    private final PagoComisionRepository pagoComisionRepository;
    private final PagoComisionDetalleRepository pagoComisionDetalleRepository;
    private final ComisionRepository comisionRepository;
    private final TecnicoRepository tecnicoRepository;
    private final AdministradorRepository administradorRepository;
    private final ArchivoService archivoService;
    private final NotificacionService notificacionService;
    private final DeudaTecnicoService deudaTecnicoService;
    private final AuditoriaService auditoriaService;
    private final ComisionMapper comisionMapper;

    @Transactional
    public PagoComisionResponse registrar(
            Long usuarioId,
            List<Long> comisionIds,
            MetodoPago metodo,
            String numeroOperacion,
            MultipartFile comprobante) {
        Tecnico tecnico = obtenerTecnico(usuarioId);
        Tecnico tecnicoBloqueado = tecnicoRepository.findByIdForUpdate(tecnico.getId())
                .orElseThrow(() -> new NotFoundException("Técnico no encontrado"));
        MetodoPago metodoValidado = validarMetodo(metodo);
        List<Long> ids = normalizarComisionIds(comisionIds);
        String operacion = normalizarNumeroOperacion(numeroOperacion);

        if (pagoComisionRepository.existsByTecnicoIdAndNumeroOperacionAndEstadoIn(
                tecnicoBloqueado.getId(), operacion, PAGOS_ACTIVOS)) {
            throw new BusinessException(
                    "Ya existe un pago registrado con el número de operación indicado");
        }

        List<Comision> comisiones = comisionRepository.findByIdsForUpdate(ids);
        validarComisiones(tecnicoBloqueado, ids, comisiones);
        BigDecimal monto = comisiones.stream()
                .map(Comision::getMontoComision)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        String ruta = archivoService.guardarComprobantePago(
                tecnicoBloqueado.getId(), comprobante);
        try {
            PagoComision pago = PagoComision.builder()
                    .tecnico(tecnicoBloqueado)
                    .monto(monto)
                    .metodo(metodoValidado)
                    .numeroOperacion(operacion)
                    .rutaComprobante(ruta)
                    .estado(EstadoPagoComision.PENDIENTE)
                    .creadoEn(LocalDateTime.now())
                    .build();
            comisiones.stream()
                    .sorted(Comparator.comparing(Comision::getId))
                    .forEach(pago::agregarComision);
            pagoComisionRepository.save(pago);
            notificarRegistro(pago);
            return comisionMapper.toResponse(pago);
        } catch (RuntimeException ex) {
            archivoService.eliminar(ruta);
            throw ex;
        }
    }

    public List<PagoComisionResponse> listarMios(
            Long usuarioId, EstadoPagoComision estado) {
        Tecnico tecnico = obtenerTecnico(usuarioId);
        List<PagoComision> pagos = estado == null
                ? pagoComisionRepository.findByTecnicoIdOrderByCreadoEnDescIdDesc(
                        tecnico.getId())
                : pagoComisionRepository
                        .findByTecnicoIdAndEstadoOrderByCreadoEnDescIdDesc(
                                tecnico.getId(), estado);
        return pagos.stream().map(comisionMapper::toResponse).toList();
    }

    public ArchivoService.RecursoImagen obtenerComprobanteTecnico(
            Long usuarioId, Long pagoId) {
        Tecnico tecnico = obtenerTecnico(usuarioId);
        PagoComision pago = pagoComisionRepository
                .findByIdAndTecnicoId(pagoId, tecnico.getId())
                .orElseThrow(() -> new NotFoundException("Pago de comisión no encontrado"));
        return archivoService.cargar(pago.getRutaComprobante());
    }

    public List<PagoComisionResponse> listarAdmin(EstadoPagoComision estado) {
        List<PagoComision> pagos = estado == null
                ? pagoComisionRepository.findAllByOrderByCreadoEnDescIdDesc()
                : pagoComisionRepository.findByEstadoOrderByCreadoEnAscIdAsc(estado);
        return pagos.stream().map(comisionMapper::toResponse).toList();
    }

    public PagoComisionResponse obtenerDetalleAdmin(Long pagoId) {
        return comisionMapper.toResponse(obtenerPagoDetalle(pagoId));
    }

    public ArchivoService.RecursoImagen obtenerComprobanteAdmin(Long pagoId) {
        PagoComision pago = pagoComisionRepository.findById(pagoId)
                .orElseThrow(() -> new NotFoundException("Pago de comisión no encontrado"));
        return archivoService.cargar(pago.getRutaComprobante());
    }

    @Transactional
    public PagoComisionResponse validar(
            Long adminUsuarioId,
            Long pagoId,
            ValidacionPagoComisionRequest request) {
        administradorRepository.findByUsuarioId(adminUsuarioId)
                .orElseThrow(() -> new ForbiddenException(
                        "El usuario no tiene perfil de administrador"));
        PagoComision pago = pagoComisionRepository.findByIdForUpdate(pagoId)
                .orElseThrow(() -> new NotFoundException("Pago de comisión no encontrado"));
        if (pago.getEstado() != EstadoPagoComision.PENDIENTE) {
            throw new BusinessException("Solo se puede validar un pago pendiente");
        }
        EstadoPagoComision nuevoEstado = request.estado();
        if (nuevoEstado != EstadoPagoComision.VALIDADO
                && nuevoEstado != EstadoPagoComision.RECHAZADO) {
            throw new BadRequestException(
                    "El estado de validación debe ser VALIDADO o RECHAZADO");
        }
        String observacion = normalizarObservacion(request.observacion());
        if (nuevoEstado == EstadoPagoComision.RECHAZADO && observacion == null) {
            throw new BadRequestException(
                    "Debe indicar una observación para rechazar el pago");
        }

        LocalDateTime ahora = LocalDateTime.now();
        if (nuevoEstado == EstadoPagoComision.VALIDADO) {
            validarComisionesPendientes(pago);
            for (PagoComisionDetalle detalle : pago.getDetalles()) {
                Comision comision = detalle.getComision();
                comision.setEstado(EstadoComision.PAGADA);
                comision.setFechaPago(ahora);
                comisionRepository.save(comision);
            }
        }

        pago.setEstado(nuevoEstado);
        pago.setValidadoPorId(adminUsuarioId);
        pago.setFechaValidacion(ahora);
        pago.setObservacion(observacion);
        pagoComisionRepository.save(pago);

        if (nuevoEstado == EstadoPagoComision.VALIDADO) {
            deudaTecnicoService.sincronizar(pago.getTecnico());
        }
        notificarValidacion(pago, observacion);
        auditoriaService.registrar(
                adminUsuarioId,
                nuevoEstado == EstadoPagoComision.VALIDADO
                        ? "VALIDAR_PAGO_COMISION"
                        : "RECHAZAR_PAGO_COMISION",
                "pago_comision",
                pago.getId(),
                detalleAuditoria(pago, nuevoEstado, observacion));
        return comisionMapper.toResponse(pago);
    }

    private PagoComision obtenerPagoDetalle(Long pagoId) {
        return pagoComisionRepository.buscarDetallePorId(pagoId)
                .orElseThrow(() -> new NotFoundException("Pago de comisión no encontrado"));
    }

    private void validarComisiones(
            Tecnico tecnico, List<Long> ids, List<Comision> comisiones) {
        if (comisiones.size() != ids.size()) {
            throw new NotFoundException("Comisión no encontrada");
        }
        Map<Long, Comision> porId = comisiones.stream()
                .collect(Collectors.toMap(Comision::getId, Function.identity()));
        for (Long id : ids) {
            Comision comision = porId.get(id);
            if (comision == null) {
                throw new NotFoundException("Comisión no encontrada");
            }
            if (!comision.getTecnico().getId().equals(tecnico.getId())) {
                throw new ForbiddenException(
                        "No puede pagar comisiones de otro técnico");
            }
            if (comision.getEstado() != EstadoComision.PENDIENTE) {
                throw new BusinessException(
                        "La comisión #%d ya no está pendiente".formatted(id));
            }
        }
        List<Long> incluidas = pagoComisionDetalleRepository
                .buscarComisionesConPagoActivo(ids, PAGOS_ACTIVOS);
        if (!incluidas.isEmpty()) {
            throw new BusinessException(
                    "Una o más comisiones ya están incluidas en un pago activo");
        }
    }

    private void validarComisionesPendientes(PagoComision pago) {
        if (pago.getDetalles() == null || pago.getDetalles().isEmpty()) {
            throw new BusinessException("El pago no tiene comisiones asociadas");
        }
        for (PagoComisionDetalle detalle : pago.getDetalles()) {
            if (detalle.getComision().getEstado() != EstadoComision.PENDIENTE) {
                throw new BusinessException(
                        "La comisión #%d ya no está pendiente"
                                .formatted(detalle.getComision().getId()));
            }
        }
    }

    private void notificarRegistro(PagoComision pago) {
        Long tecnicoUsuarioId = pago.getTecnico().getUsuario().getId();
        notificacionService.crear(
                tecnicoUsuarioId,
                TipoNotificacion.PAGO_COMISION_REGISTRADO,
                "Pago de comisión registrado",
                "Registramos su pago de S/ %s por %d comisión(es). Queda pendiente de validación."
                        .formatted(
                                pago.getMonto().toPlainString(),
                                pago.getDetalles().size()),
                "PAGO_COMISION",
                pago.getId());

        String nombre = (pago.getTecnico().getNombres() + " "
                + pago.getTecnico().getApellidos()).trim();
        for (Long adminUsuarioId : administradorRepository.buscarUsuarioIds()) {
            notificacionService.crear(
                    adminUsuarioId,
                    TipoNotificacion.PAGO_COMISION_REGISTRADO,
                    "Pago de comisión por validar",
                    "El técnico %s registró un pago de S/ %s (operación %s)."
                            .formatted(
                                    nombre,
                                    pago.getMonto().toPlainString(),
                                    pago.getNumeroOperacion()),
                    "PAGO_COMISION",
                    pago.getId());
        }
    }

    private void notificarValidacion(PagoComision pago, String observacion) {
        Long tecnicoUsuarioId = pago.getTecnico().getUsuario().getId();
        if (pago.getEstado() == EstadoPagoComision.VALIDADO) {
            notificacionService.crear(
                    tecnicoUsuarioId,
                    TipoNotificacion.PAGO_COMISION_VALIDADO,
                    "Pago de comisión validado",
                    "Su pago de S/ %s por %d comisión(es) fue validado."
                            .formatted(
                                    pago.getMonto().toPlainString(),
                                    pago.getDetalles().size()),
                    "PAGO_COMISION",
                    pago.getId());
            return;
        }
        notificacionService.crear(
                tecnicoUsuarioId,
                TipoNotificacion.PAGO_COMISION_RECHAZADO,
                "Pago de comisión rechazado",
                "Su pago de S/ %s fue rechazado. Motivo: %s"
                        .formatted(pago.getMonto().toPlainString(), observacion),
                "PAGO_COMISION",
                pago.getId());
    }

    private String detalleAuditoria(
            PagoComision pago, EstadoPagoComision estado, String observacion) {
        if (estado == EstadoPagoComision.VALIDADO) {
            return "Pago validado por S/ %s con %d comisión(es)."
                    .formatted(pago.getMonto().toPlainString(), pago.getDetalles().size());
        }
        return "Pago rechazado por S/ %s. Motivo: %s"
                .formatted(pago.getMonto().toPlainString(), observacion);
    }

    private MetodoPago validarMetodo(MetodoPago metodo) {
        if (metodo == null || !METODOS_PERMITIDOS.contains(metodo)) {
            throw new BadRequestException(
                    "El método de pago debe ser YAPE, PLIN o TRANSFERENCIA");
        }
        return metodo;
    }

    private List<Long> normalizarComisionIds(List<Long> comisionIds) {
        if (comisionIds == null || comisionIds.isEmpty()) {
            throw new BadRequestException(
                    "Debe seleccionar al menos una comisión para pagar");
        }
        if (comisionIds.stream().anyMatch(id -> id == null)) {
            throw new BadRequestException("Las comisiones seleccionadas no son válidas");
        }
        List<Long> ids = comisionIds.stream().distinct().toList();
        if (ids.size() != comisionIds.size()) {
            throw new BadRequestException("No se puede repetir una comisión en el pago");
        }
        return ids;
    }

    private String normalizarNumeroOperacion(String numeroOperacion) {
        if (!StringUtils.hasText(numeroOperacion)) {
            throw new BadRequestException("El número de operación es obligatorio");
        }
        String normalizado = numeroOperacion.trim();
        if (normalizado.length() > MAX_NUMERO_OPERACION) {
            throw new BadRequestException(
                    "El número de operación no puede superar los %d caracteres"
                            .formatted(MAX_NUMERO_OPERACION));
        }
        return normalizado;
    }

    private String normalizarObservacion(String observacion) {
        if (!StringUtils.hasText(observacion)) {
            return null;
        }
        String normalizada = observacion.trim();
        if (normalizada.length() > MAX_OBSERVACION) {
            throw new BadRequestException(
                    "La observación no puede superar los %d caracteres"
                            .formatted(MAX_OBSERVACION));
        }
        return normalizada;
    }

    private Tecnico obtenerTecnico(Long usuarioId) {
        return tecnicoRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new ForbiddenException(
                        "El usuario no tiene perfil de técnico"));
    }
}
