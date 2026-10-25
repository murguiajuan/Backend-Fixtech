package com.fixtech.service;

import com.fixtech.dto.ImagenSolicitudResponse;
import com.fixtech.dto.SolicitudDisponibleResponse;
import com.fixtech.dto.SolicitudRequest;
import com.fixtech.dto.SolicitudResponse;
import com.fixtech.dto.SolicitudResumenResponse;
import com.fixtech.entity.Cliente;
import com.fixtech.entity.Distrito;
import com.fixtech.entity.ImagenSolicitud;
import com.fixtech.entity.Solicitud;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.TipoEquipo;
import com.fixtech.entity.TipoServicio;
import com.fixtech.entity.enums.EstadoSolicitud;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.ParametroClave;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.BusinessException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.repository.ClienteRepository;
import com.fixtech.repository.DistritoRepository;
import com.fixtech.repository.ImagenSolicitudRepository;
import com.fixtech.repository.SolicitudRepository;
import com.fixtech.repository.TecnicoRepository;
import com.fixtech.repository.TipoEquipoRepository;
import com.fixtech.repository.TipoServicioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class SolicitudService {

    private static final int MAX_IMAGENES_PERMITIDAS = 5;
    private static final int MAX_MOTIVO_CANCELACION = 300;

    private final SolicitudRepository solicitudRepository;
    private final ImagenSolicitudRepository imagenSolicitudRepository;
    private final ClienteRepository clienteRepository;
    private final TecnicoRepository tecnicoRepository;
    private final TipoEquipoRepository tipoEquipoRepository;
    private final TipoServicioRepository tipoServicioRepository;
    private final DistritoRepository distritoRepository;
    private final ParametroSistemaService parametroSistemaService;
    private final ArchivoService archivoService;

    @Transactional
    public SolicitudResponse crear(
            Long usuarioId, SolicitudRequest request, List<MultipartFile> fotos) {
        validarFotos(fotos);
        Cliente cliente = obtenerCliente(usuarioId);
        TipoEquipo tipoEquipo = resolverTipoEquipo(request.tipoEquipoId());
        TipoServicio tipoServicio = resolverTipoServicio(request.tipoServicioId());
        Distrito distrito = resolverDistrito(request.distritoId());
        validarDireccion(request);
        validarFechaPreferida(request.fechaPreferida());

        int horasVigencia = parametroSistemaService.obtenerEntero(
                ParametroClave.VENCIMIENTO_SOLICITUD_HORAS);
        LocalDateTime ahora = LocalDateTime.now();

        Solicitud solicitud = Solicitud.builder()
                .cliente(cliente)
                .tipoEquipo(tipoEquipo)
                .tipoServicio(tipoServicio)
                .marca(normalizarTexto(request.marca()))
                .modelo(normalizarTexto(request.modelo()))
                .descripcion(request.descripcion().trim())
                .urgencia(request.urgencia())
                .modalidad(request.modalidad())
                .distrito(distrito)
                .direccion(normalizarTexto(request.direccion()))
                .referencia(normalizarTexto(request.referencia()))
                .fechaPreferida(request.fechaPreferida())
                .horarioPreferido(normalizarTexto(request.horarioPreferido()))
                .presupuestoReferencial(escalar(request.presupuestoReferencial()))
                .estado(EstadoSolicitud.PUBLICADA)
                .fechaVencimiento(ahora.plusHours(horasVigencia))
                .build();

        solicitudRepository.save(solicitud);

        List<String> rutasGuardadas = new ArrayList<>();
        try {
            for (int i = 0; i < fotos.size(); i++) {
                String ruta = archivoService.guardarImagenSolicitud(solicitud.getId(), fotos.get(i));
                rutasGuardadas.add(ruta);
                solicitud.getImagenes().add(ImagenSolicitud.builder()
                        .solicitud(solicitud)
                        .rutaArchivo(ruta)
                        .orden((byte) (i + 1))
                        .build());
            }
            solicitudRepository.saveAndFlush(solicitud);
        } catch (RuntimeException ex) {
            rutasGuardadas.forEach(archivoService::eliminar);
            throw ex;
        }

        return aResponse(solicitud);
    }

    @Transactional(readOnly = true)
    public List<SolicitudResumenResponse> listarMias(Long usuarioId, EstadoSolicitud estado) {
        Cliente cliente = obtenerCliente(usuarioId);
        List<Solicitud> solicitudes = estado == null
                ? solicitudRepository.findByClienteIdOrderByCreadoEnDesc(cliente.getId())
                : solicitudRepository.findByClienteIdAndEstadoOrderByCreadoEnDesc(
                        cliente.getId(), estado);
        return solicitudes.stream().map(this::aResumen).toList();
    }

    @Transactional(readOnly = true)
    public SolicitudResponse obtenerMia(Long usuarioId, Long solicitudId) {
        return aResponse(obtenerSolicitudDelCliente(solicitudId, usuarioId));
    }

    @Transactional
    public SolicitudResponse actualizar(
            Long usuarioId, Long solicitudId, SolicitudRequest request) {
        Solicitud solicitud = obtenerSolicitudDelCliente(solicitudId, usuarioId);
        if (solicitud.getEstado() != EstadoSolicitud.PUBLICADA) {
            throw new BusinessException(
                    "Solo se puede editar una solicitud en estado PUBLICADA");
        }
        validarDireccion(request);
        validarFechaPreferida(request.fechaPreferida());

        solicitud.setTipoEquipo(resolverTipoEquipo(request.tipoEquipoId()));
        solicitud.setTipoServicio(resolverTipoServicio(request.tipoServicioId()));
        solicitud.setMarca(normalizarTexto(request.marca()));
        solicitud.setModelo(normalizarTexto(request.modelo()));
        solicitud.setDescripcion(request.descripcion().trim());
        solicitud.setUrgencia(request.urgencia());
        solicitud.setModalidad(request.modalidad());
        solicitud.setDistrito(resolverDistrito(request.distritoId()));
        solicitud.setDireccion(normalizarTexto(request.direccion()));
        solicitud.setReferencia(normalizarTexto(request.referencia()));
        solicitud.setFechaPreferida(request.fechaPreferida());
        solicitud.setHorarioPreferido(normalizarTexto(request.horarioPreferido()));
        solicitud.setPresupuestoReferencial(escalar(request.presupuestoReferencial()));
        solicitudRepository.save(solicitud);

        return aResponse(solicitud);
    }

    @Transactional
    public SolicitudResponse cancelar(Long usuarioId, Long solicitudId, String motivo) {
        Solicitud solicitud = obtenerSolicitudDelCliente(solicitudId, usuarioId);
        if (solicitud.getEstado() != EstadoSolicitud.PUBLICADA
                && solicitud.getEstado() != EstadoSolicitud.CON_OFERTAS) {
            throw new BusinessException(
                    "La solicitud ya no se puede cancelar en su estado actual");
        }

        String motivoNormalizado = normalizarTexto(motivo);
        if (motivoNormalizado != null && motivoNormalizado.length() > MAX_MOTIVO_CANCELACION) {
            throw new BadRequestException(
                    "El motivo de cancelación no puede superar los 300 caracteres");
        }

        solicitud.setEstado(EstadoSolicitud.CANCELADA);
        solicitud.setMotivoCancelacion(motivoNormalizado);
        solicitudRepository.save(solicitud);

        return aResponse(solicitud);
    }

    @Transactional(readOnly = true)
    public List<SolicitudDisponibleResponse> listarDisponibles(Long usuarioId) {
        Tecnico tecnico = obtenerTecnico(usuarioId);
        Set<Long> distritos = tecnico.getDistritos().stream()
                .map(Distrito::getId)
                .collect(Collectors.toSet());
        Set<Modalidad> modalidades = modalidadesDe(tecnico);
        if (distritos.isEmpty() || modalidades.isEmpty()) {
            return List.of();
        }

        return solicitudRepository.buscarDisponibles(
                        List.of(EstadoSolicitud.PUBLICADA, EstadoSolicitud.CON_OFERTAS),
                        LocalDateTime.now(),
                        distritos,
                        modalidades)
                .stream()
                .map(this::aDisponible)
                .toList();
    }

    @Transactional(readOnly = true)
    public SolicitudDisponibleResponse obtenerDisponible(Long usuarioId, Long solicitudId) {
        Tecnico tecnico = obtenerTecnico(usuarioId);
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new NotFoundException("Solicitud no encontrada"));
        if (!esCompatible(tecnico, solicitud, LocalDateTime.now())) {
            throw new ForbiddenException(
                    "La solicitud no está disponible para este técnico");
        }
        return aDisponible(solicitud);
    }

    @Transactional(readOnly = true)
    public ArchivoService.RecursoImagen obtenerImagen(
            Long usuarioId, Long solicitudId, Long imagenId) {
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new NotFoundException("Solicitud no encontrada"));
        ImagenSolicitud imagen = imagenSolicitudRepository
                .findByIdAndSolicitudId(imagenId, solicitudId)
                .orElseThrow(() -> new NotFoundException("Imagen no encontrada"));

        boolean esDueno = solicitud.getCliente().getUsuario().getId().equals(usuarioId);
        if (!esDueno) {
            Tecnico tecnico = tecnicoRepository.findByUsuarioId(usuarioId)
                    .orElseThrow(() -> new ForbiddenException(
                            "No tiene acceso a esta imagen"));
            if (!esCompatible(tecnico, solicitud, LocalDateTime.now())) {
                throw new ForbiddenException("No tiene acceso a esta imagen");
            }
        }

        return archivoService.cargar(imagen.getRutaArchivo());
    }

    @Transactional
    public int vencerSolicitudes() {
        return solicitudRepository.marcarVencidas(
                EstadoSolicitud.VENCIDA,
                List.of(EstadoSolicitud.PUBLICADA, EstadoSolicitud.CON_OFERTAS),
                LocalDateTime.now());
    }

    private void validarFotos(List<MultipartFile> fotos) {
        int maximo = obtenerMaximoFotos();
        if (fotos == null || fotos.isEmpty()) {
            throw new BadRequestException("Debe adjuntar al menos una foto");
        }
        if (fotos.size() > maximo) {
            throw new BadRequestException(
                    "Puede adjuntar como máximo %d fotos".formatted(maximo));
        }
    }

    private int obtenerMaximoFotos() {
        int configurado = parametroSistemaService.obtenerEntero(
                ParametroClave.MAX_IMAGENES_SOLICITUD);
        return Math.min(Math.max(configurado, 1), MAX_IMAGENES_PERMITIDAS);
    }

    private void validarDireccion(SolicitudRequest request) {
        if (request.modalidad() != Modalidad.TALLER
                && !StringUtils.hasText(request.direccion())) {
            throw new BadRequestException(
                    "La dirección es obligatoria para la modalidad seleccionada");
        }
    }

    private void validarFechaPreferida(LocalDate fechaPreferida) {
        if (fechaPreferida != null && fechaPreferida.isBefore(LocalDate.now())) {
            throw new BadRequestException("La fecha preferida no puede ser pasada");
        }
    }

    private Cliente obtenerCliente(Long usuarioId) {
        return clienteRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new ForbiddenException(
                        "El usuario no tiene perfil de cliente"));
    }

    private Tecnico obtenerTecnico(Long usuarioId) {
        return tecnicoRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new ForbiddenException(
                        "El usuario no tiene perfil de técnico"));
    }

    private Solicitud obtenerSolicitudDelCliente(Long solicitudId, Long usuarioId) {
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new NotFoundException("Solicitud no encontrada"));
        if (!solicitud.getCliente().getUsuario().getId().equals(usuarioId)) {
            throw new ForbiddenException("No tiene acceso a esta solicitud");
        }
        return solicitud;
    }

    private TipoEquipo resolverTipoEquipo(Long id) {
        TipoEquipo tipoEquipo = tipoEquipoRepository.findById(id)
                .orElseThrow(() -> new BadRequestException(
                        "El tipo de equipo no existe"));
        if (!tipoEquipo.isActivo()) {
            throw new BadRequestException("El tipo de equipo no está activo");
        }
        return tipoEquipo;
    }

    private TipoServicio resolverTipoServicio(Long id) {
        TipoServicio tipoServicio = tipoServicioRepository.findById(id)
                .orElseThrow(() -> new BadRequestException(
                        "El tipo de servicio no existe"));
        if (!tipoServicio.isActivo()) {
            throw new BadRequestException("El tipo de servicio no está activo");
        }
        return tipoServicio;
    }

    private Distrito resolverDistrito(Long id) {
        Distrito distrito = distritoRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("El distrito no existe"));
        if (!distrito.isActivo()) {
            throw new BadRequestException("El distrito no está activo");
        }
        return distrito;
    }

    private Set<Modalidad> modalidadesDe(Tecnico tecnico) {
        Set<Modalidad> modalidades = EnumSet.noneOf(Modalidad.class);
        if (tecnico.isOfreceDomicilio()) {
            modalidades.add(Modalidad.DOMICILIO);
        }
        if (tecnico.isOfreceTaller()) {
            modalidades.add(Modalidad.TALLER);
        }
        if (!modalidades.isEmpty()) {
            modalidades.add(Modalidad.INDISTINTO);
        }
        return modalidades;
    }

    private boolean esCompatible(Tecnico tecnico, Solicitud solicitud, LocalDateTime ahora) {
        if (solicitud.getEstado() != EstadoSolicitud.PUBLICADA
                && solicitud.getEstado() != EstadoSolicitud.CON_OFERTAS) {
            return false;
        }
        if (!solicitud.getFechaVencimiento().isAfter(ahora)) {
            return false;
        }
        boolean mismoDistrito = tecnico.getDistritos().stream()
                .anyMatch(distrito -> distrito.getId().equals(solicitud.getDistrito().getId()));
        if (!mismoDistrito) {
            return false;
        }
        return switch (solicitud.getModalidad()) {
            case DOMICILIO -> tecnico.isOfreceDomicilio();
            case TALLER -> tecnico.isOfreceTaller();
            case INDISTINTO -> tecnico.isOfreceDomicilio() || tecnico.isOfreceTaller();
        };
    }

    private SolicitudResponse aResponse(Solicitud solicitud) {
        return new SolicitudResponse(
                solicitud.getId(),
                solicitud.getEstado(),
                solicitud.getUrgencia(),
                solicitud.getModalidad(),
                solicitud.getTipoEquipo().getId(),
                solicitud.getTipoEquipo().getNombre(),
                solicitud.getTipoServicio().getId(),
                solicitud.getTipoServicio().getNombre(),
                solicitud.getDistrito().getId(),
                solicitud.getDistrito().getNombre(),
                solicitud.getMarca(),
                solicitud.getModelo(),
                solicitud.getDescripcion(),
                solicitud.getDireccion(),
                solicitud.getReferencia(),
                solicitud.getFechaPreferida(),
                solicitud.getHorarioPreferido(),
                solicitud.getPresupuestoReferencial(),
                solicitud.getFechaVencimiento(),
                solicitud.getMotivoCancelacion(),
                creadoEn(solicitud),
                aImagenes(solicitud));
    }

    private SolicitudResumenResponse aResumen(Solicitud solicitud) {
        List<ImagenSolicitudResponse> imagenes = aImagenes(solicitud);
        String imagenPrincipal = imagenes.isEmpty() ? null : imagenes.get(0).url();
        return new SolicitudResumenResponse(
                solicitud.getId(),
                solicitud.getEstado(),
                solicitud.getUrgencia(),
                solicitud.getModalidad(),
                solicitud.getTipoEquipo().getNombre(),
                solicitud.getTipoServicio().getNombre(),
                solicitud.getDistrito().getNombre(),
                solicitud.getMarca(),
                solicitud.getModelo(),
                solicitud.getDescripcion(),
                solicitud.getPresupuestoReferencial(),
                solicitud.getFechaVencimiento(),
                creadoEn(solicitud),
                imagenPrincipal);
    }

    private SolicitudDisponibleResponse aDisponible(Solicitud solicitud) {
        return new SolicitudDisponibleResponse(
                solicitud.getId(),
                solicitud.getEstado(),
                solicitud.getUrgencia(),
                solicitud.getModalidad(),
                solicitud.getTipoEquipo().getNombre(),
                solicitud.getTipoServicio().getNombre(),
                solicitud.getDistrito().getId(),
                solicitud.getDistrito().getNombre(),
                solicitud.getMarca(),
                solicitud.getModelo(),
                solicitud.getDescripcion(),
                solicitud.getFechaPreferida(),
                solicitud.getHorarioPreferido(),
                solicitud.getPresupuestoReferencial(),
                solicitud.getFechaVencimiento(),
                creadoEn(solicitud),
                aImagenes(solicitud));
    }

    private List<ImagenSolicitudResponse> aImagenes(Solicitud solicitud) {
        return solicitud.getImagenes().stream()
                .sorted(Comparator.comparingInt(imagen -> Byte.toUnsignedInt(imagen.getOrden())))
                .map(imagen -> new ImagenSolicitudResponse(
                        imagen.getId(),
                        Byte.toUnsignedInt(imagen.getOrden()),
                        urlImagen(solicitud.getId(), imagen.getId())))
                .toList();
    }

    private String urlImagen(Long solicitudId, Long imagenId) {
        return "/api/solicitudes/%d/imagenes/%d".formatted(solicitudId, imagenId);
    }

    private LocalDateTime creadoEn(Solicitud solicitud) {
        return solicitud.getCreadoEn() != null ? solicitud.getCreadoEn() : LocalDateTime.now();
    }

    private BigDecimal escalar(BigDecimal valor) {
        return valor == null ? null : valor.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizarTexto(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }
}
