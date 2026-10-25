package com.fixtech.service;

import com.fixtech.dto.CambioEstadoReclamoRequest;
import com.fixtech.dto.ReclamoResponse;
import com.fixtech.dto.RespuestaReclamoRequest;
import com.fixtech.entity.Reclamo;
import com.fixtech.entity.ReclamoFoto;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.enums.EstadoReclamo;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.BusinessException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.mapper.ReclamoMapper;
import com.fixtech.repository.AdministradorRepository;
import com.fixtech.repository.ReclamoFotoRepository;
import com.fixtech.repository.ReclamoRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReclamoService {

    private static final Set<EstadoReclamo> ESTADOS_ACTIVOS = EnumSet.of(
            EstadoReclamo.ABIERTO, EstadoReclamo.EN_REVISION);
    private static final int MIN_FOTOS = 1;
    private static final int MAX_FOTOS = 5;
    private static final int MAX_DESCRIPCION = 2000;
    private static final int MAX_RESPUESTA = 1000;
    private static final int MAX_RESOLUCION = 1000;

    private final ReclamoRepository reclamoRepository;
    private final ReclamoFotoRepository reclamoFotoRepository;
    private final AdministradorRepository administradorRepository;
    private final ServicioAccesoService servicioAccesoService;
    private final ArchivoService archivoService;
    private final NotificacionService notificacionService;
    private final AuditoriaService auditoriaService;
    private final ReclamoMapper reclamoMapper;

    @Transactional
    public ReclamoResponse registrar(
            Long usuarioId,
            Long servicioId,
            String descripcion,
            List<MultipartFile> fotos) {
        Servicio servicio = servicioAccesoService.bloquear(servicioId);
        servicioAccesoService.validarCliente(servicio, usuarioId);
        if (servicio.getEstado() != EstadoServicio.CONFIRMADO
                && servicio.getEstado() != EstadoServicio.CALIFICADO) {
            throw new BusinessException(
                    "Solo se puede reclamar un servicio confirmado");
        }
        if (reclamoRepository.existsByServicioIdAndEstadoIn(
                servicioId, ESTADOS_ACTIVOS)) {
            throw new BusinessException(
                    "El servicio ya tiene un reclamo activo");
        }
        String descripcionNormalizada = normalizarObligatorio(
                descripcion, MAX_DESCRIPCION, "La descripción es obligatoria");
        validarFotos(fotos);

        Reclamo reclamo = Reclamo.builder()
                .servicio(servicio)
                .cliente(servicio.getCliente())
                .descripcion(descripcionNormalizada)
                .estado(EstadoReclamo.ABIERTO)
                .build();
        List<String> rutas = new ArrayList<>();
        try {
            reclamoRepository.save(reclamo);
            for (MultipartFile foto : fotos) {
                String ruta = archivoService.guardarImagenReclamo(reclamo.getId(), foto);
                rutas.add(ruta);
                reclamo.getFotos().add(ReclamoFoto.builder()
                        .reclamo(reclamo)
                        .rutaArchivo(ruta)
                        .build());
            }
            reclamoRepository.save(reclamo);
            notificarRegistro(reclamo);
            return reclamoMapper.toResponse(reclamo);
        } catch (RuntimeException ex) {
            rutas.forEach(archivoService::eliminar);
            throw ex;
        }
    }

    public List<ReclamoResponse> listarPorServicio(Long usuarioId, Long servicioId) {
        servicioAccesoService.obtenerParaParticipante(usuarioId, servicioId);
        return reclamoRepository.buscarPorServicio(servicioId).stream()
                .map(reclamoMapper::toResponse)
                .toList();
    }

    public ReclamoResponse obtenerDetalle(
            Long usuarioId, Long servicioId, Long reclamoId) {
        servicioAccesoService.obtenerParaParticipante(usuarioId, servicioId);
        return reclamoMapper.toResponse(obtenerReclamoDelServicio(reclamoId, servicioId));
    }

    public ArchivoService.RecursoImagen obtenerFoto(
            Long usuarioId, Long servicioId, Long reclamoId, Long fotoId) {
        servicioAccesoService.obtenerParaParticipante(usuarioId, servicioId);
        ReclamoFoto foto = reclamoFotoRepository
                .findByIdAndReclamoIdAndReclamoServicioId(
                        fotoId, reclamoId, servicioId)
                .orElseThrow(() -> new NotFoundException(
                        "Foto de reclamo no encontrada"));
        return archivoService.cargar(foto.getRutaArchivo());
    }

    public ArchivoService.RecursoImagen obtenerFotoAdmin(
            Long reclamoId, Long fotoId) {
        ReclamoFoto foto = reclamoFotoRepository
                .findByIdAndReclamoId(fotoId, reclamoId)
                .orElseThrow(() -> new NotFoundException(
                        "Foto de reclamo no encontrada"));
        return archivoService.cargar(foto.getRutaArchivo());
    }

    @Transactional
    public ReclamoResponse responder(
            Long usuarioId,
            Long servicioId,
            Long reclamoId,
            RespuestaReclamoRequest request) {
        Servicio servicio = servicioAccesoService.obtenerParaParticipante(
                usuarioId, servicioId);
        servicioAccesoService.validarTecnico(servicio, usuarioId);
        Reclamo reclamo = reclamoRepository.findByIdForUpdate(reclamoId)
                .orElseThrow(() -> new NotFoundException("Reclamo no encontrado"));
        if (!reclamo.getServicio().getId().equals(servicioId)) {
            throw new NotFoundException("Reclamo no encontrado");
        }
        if (reclamo.getEstado() != EstadoReclamo.ABIERTO
                && reclamo.getEstado() != EstadoReclamo.EN_REVISION) {
            throw new BusinessException(
                    "El reclamo ya está cerrado o resuelto");
        }
        if (StringUtils.hasText(reclamo.getRespuestaTecnico())) {
            throw new BusinessException(
                    "El reclamo ya tiene una respuesta del técnico");
        }
        String respuesta = normalizarObligatorio(
                request == null ? null : request.respuesta(),
                MAX_RESPUESTA,
                "La respuesta es obligatoria");
        reclamo.setRespuestaTecnico(respuesta);
        reclamoRepository.save(reclamo);

        notificacionService.crear(
                reclamo.getCliente().getUsuario().getId(),
                TipoNotificacion.RECLAMO_RESPUESTA,
                "El técnico respondió su reclamo",
                "El técnico respondió el reclamo #%d del servicio #%d."
                        .formatted(reclamoId, servicioId),
                "RECLAMO",
                reclamo.getId());
        return reclamoMapper.toResponse(reclamo);
    }

    public List<ReclamoResponse> listarAdmin(EstadoReclamo estado) {
        List<Reclamo> reclamos = estado == null
                ? reclamoRepository.listarTodos()
                : reclamoRepository.listarPorEstado(estado);
        return reclamos.stream().map(reclamoMapper::toResponse).toList();
    }

    public ReclamoResponse obtenerDetalleAdmin(Long reclamoId) {
        return reclamoMapper.toResponse(reclamoRepository.buscarDetallePorId(reclamoId)
                .orElseThrow(() -> new NotFoundException("Reclamo no encontrado")));
    }

    @Transactional
    public ReclamoResponse cambiarEstado(
            Long adminUsuarioId,
            Long reclamoId,
            CambioEstadoReclamoRequest request) {
        administradorRepository.findByUsuarioId(adminUsuarioId)
                .orElseThrow(() -> new ForbiddenException(
                        "El usuario no tiene perfil de administrador"));
        if (request == null || request.estado() == null) {
            throw new BadRequestException("Debe indicar el nuevo estado");
        }
        Reclamo reclamo = reclamoRepository.findByIdForUpdate(reclamoId)
                .orElseThrow(() -> new NotFoundException("Reclamo no encontrado"));
        EstadoReclamo actual = reclamo.getEstado();
        EstadoReclamo objetivo = request.estado();
        validarTransicion(actual, objetivo);

        String resolucion = normalizar(
                request.resolucion(), MAX_RESOLUCION, "La resolución");
        if (objetivo == EstadoReclamo.EN_REVISION && resolucion != null) {
            throw new BadRequestException(
                    "La resolución solo se registra al resolver o cerrar el reclamo");
        }
        boolean finaliza = objetivo == EstadoReclamo.RESUELTO
                || objetivo == EstadoReclamo.CERRADO;
        if (finaliza && resolucion == null
                && !StringUtils.hasText(reclamo.getResolucion())) {
            throw new BadRequestException(
                    "Debe indicar la resolución del reclamo");
        }
        if (resolucion != null) {
            reclamo.setResolucion(resolucion);
        }
        reclamo.setEstado(objetivo);
        if (finaliza) {
            if (reclamo.getFechaResolucion() == null) {
                reclamo.setFechaResolucion(LocalDateTime.now());
            }
            reclamo.setResueltoPorId(adminUsuarioId);
        }
        reclamoRepository.save(reclamo);

        notificarCambio(reclamo, actual, objetivo);
        auditoriaService.registrar(
                adminUsuarioId,
                accionAuditoria(objetivo),
                "reclamo",
                reclamo.getId(),
                detalleAuditoria(actual, objetivo, reclamo.getResolucion()));
        return reclamoMapper.toResponse(reclamo);
    }

    private Reclamo obtenerReclamoDelServicio(Long reclamoId, Long servicioId) {
        Reclamo reclamo = reclamoRepository.buscarDetallePorId(reclamoId)
                .orElseThrow(() -> new NotFoundException("Reclamo no encontrado"));
        if (!reclamo.getServicio().getId().equals(servicioId)) {
            throw new NotFoundException("Reclamo no encontrado");
        }
        return reclamo;
    }

    private void validarFotos(List<MultipartFile> fotos) {
        if (fotos == null || fotos.size() < MIN_FOTOS || fotos.size() > MAX_FOTOS) {
            throw new BadRequestException(
                    "Debe adjuntar entre %d y %d fotos".formatted(MIN_FOTOS, MAX_FOTOS));
        }
        for (MultipartFile foto : fotos) {
            if (foto == null || foto.isEmpty()) {
                throw new BadRequestException("Cada foto debe ser un archivo no vacío");
            }
        }
    }

    private void validarTransicion(EstadoReclamo actual, EstadoReclamo objetivo) {
        boolean permitida = switch (actual) {
            case ABIERTO -> objetivo == EstadoReclamo.EN_REVISION;
            case EN_REVISION -> objetivo == EstadoReclamo.RESUELTO
                    || objetivo == EstadoReclamo.CERRADO;
            case RESUELTO -> objetivo == EstadoReclamo.CERRADO;
            case CERRADO -> false;
        };
        if (!permitida) {
            throw new BusinessException(
                    "Transición no permitida de %s a %s"
                            .formatted(actual, objetivo));
        }
    }

    private void notificarRegistro(Reclamo reclamo) {
        notificacionService.crear(
                reclamo.getServicio().getTecnico().getUsuario().getId(),
                TipoNotificacion.RECLAMO_REGISTRADO,
                "Nuevo reclamo recibido",
                "El cliente registró un reclamo sobre el servicio #%d."
                        .formatted(reclamo.getServicio().getId()),
                "RECLAMO",
                reclamo.getId());
        for (Long adminUsuarioId : administradorRepository.buscarUsuarioIds()) {
            notificacionService.crear(
                    adminUsuarioId,
                    TipoNotificacion.RECLAMO_REGISTRADO,
                    "Reclamo por atender",
                    "El cliente registró el reclamo #%d del servicio #%d."
                            .formatted(reclamo.getId(), reclamo.getServicio().getId()),
                    "RECLAMO",
                    reclamo.getId());
        }
    }

    private void notificarCambio(
            Reclamo reclamo, EstadoReclamo anterior, EstadoReclamo nuevo) {
        Long clienteUsuarioId = reclamo.getCliente().getUsuario().getId();
        Long tecnicoUsuarioId = reclamo.getServicio().getTecnico().getUsuario().getId();
        String servicioId = reclamo.getServicio().getId().toString();
        switch (nuevo) {
            case EN_REVISION -> {
                notificacionService.crear(
                        clienteUsuarioId,
                        TipoNotificacion.RECLAMO_EN_REVISION,
                        "Reclamo en revisión",
                        "El reclamo #%d del servicio #%s está en revisión."
                                .formatted(reclamo.getId(), servicioId),
                        "RECLAMO",
                        reclamo.getId());
                notificacionService.crear(
                        tecnicoUsuarioId,
                        TipoNotificacion.RECLAMO_EN_REVISION,
                        "Reclamo en revisión",
                        "El reclamo #%d del servicio #%s está en revisión."
                                .formatted(reclamo.getId(), servicioId),
                        "RECLAMO",
                        reclamo.getId());
            }
            case RESUELTO -> {
                String mensaje = "El reclamo #%d del servicio #%s fue resuelto. Resolución: %s"
                        .formatted(reclamo.getId(), servicioId, reclamo.getResolucion());
                notificacionService.crear(
                        clienteUsuarioId,
                        TipoNotificacion.RECLAMO_RESUELTO,
                        "Reclamo resuelto",
                        mensaje,
                        "RECLAMO",
                        reclamo.getId());
                notificacionService.crear(
                        tecnicoUsuarioId,
                        TipoNotificacion.RECLAMO_RESUELTO,
                        "Reclamo resuelto",
                        mensaje,
                        "RECLAMO",
                        reclamo.getId());
            }
            case CERRADO -> {
                String mensaje = "El reclamo #%d del servicio #%s fue cerrado."
                        .formatted(reclamo.getId(), servicioId);
                notificacionService.crear(
                        clienteUsuarioId,
                        TipoNotificacion.RECLAMO_CERRADO,
                        "Reclamo cerrado",
                        mensaje,
                        "RECLAMO",
                        reclamo.getId());
                notificacionService.crear(
                        tecnicoUsuarioId,
                        TipoNotificacion.RECLAMO_CERRADO,
                        "Reclamo cerrado",
                        mensaje,
                        "RECLAMO",
                        reclamo.getId());
            }
            default -> {
            }
        }
    }

    private String accionAuditoria(EstadoReclamo objetivo) {
        return switch (objetivo) {
            case EN_REVISION -> "REVISAR_RECLAMO";
            case RESUELTO -> "RESOLVER_RECLAMO";
            case CERRADO -> "CERRAR_RECLAMO";
            default -> "CAMBIAR_ESTADO_RECLAMO";
        };
    }

    private String detalleAuditoria(
            EstadoReclamo anterior, EstadoReclamo nuevo, String resolucion) {
        String detalle = "Estado anterior %s, nuevo %s."
                .formatted(anterior, nuevo);
        return StringUtils.hasText(resolucion)
                ? detalle + " Resolución: " + resolucion
                : detalle;
    }

    private String normalizarObligatorio(
            String valor, int maximo, String mensajeObligatorio) {
        if (!StringUtils.hasText(valor)) {
            throw new BadRequestException(mensajeObligatorio);
        }
        String normalizado = valor.trim();
        if (normalizado.length() > maximo) {
            throw new BadRequestException(
                    "El texto no puede superar los %d caracteres".formatted(maximo));
        }
        return normalizado;
    }

    private String normalizar(String valor, int maximo, String nombre) {
        if (!StringUtils.hasText(valor)) {
            return null;
        }
        String normalizado = valor.trim();
        if (normalizado.length() > maximo) {
            throw new BadRequestException(
                    "%s no puede superar los %d caracteres"
                            .formatted(nombre, maximo));
        }
        return normalizado;
    }
}
