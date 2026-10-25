package com.fixtech.service;

import com.fixtech.dto.OfertaOrden;
import com.fixtech.dto.OfertaRequest;
import com.fixtech.dto.OfertaResponse;
import com.fixtech.entity.Contraoferta;
import com.fixtech.entity.Oferta;
import com.fixtech.entity.Solicitud;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.enums.EstadoContraoferta;
import com.fixtech.entity.enums.EstadoOferta;
import com.fixtech.entity.enums.EstadoSolicitud;
import com.fixtech.entity.enums.EstadoVerificacion;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.ParametroClave;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.BusinessException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.mapper.OfertaMapper;
import com.fixtech.repository.OfertaRepository;
import com.fixtech.repository.SolicitudRepository;
import com.fixtech.repository.TecnicoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OfertaService {

    private static final Set<EstadoOferta> ESTADOS_ACTIVOS = EnumSet.of(
            EstadoOferta.ENVIADA, EstadoOferta.CONTRAOFERTADA);
    private static final int MAX_MENSAJE = 500;

    private final OfertaRepository ofertaRepository;
    private final SolicitudRepository solicitudRepository;
    private final TecnicoRepository tecnicoRepository;
    private final ParametroSistemaService parametroSistemaService;
    private final NotificacionService notificacionService;
    private final OfertaMapper ofertaMapper;

    @Transactional
    public OfertaResponse crear(Long usuarioId, Long solicitudId, OfertaRequest request) {
        Tecnico tecnico = obtenerTecnico(usuarioId);
        Solicitud solicitud = solicitudRepository.findByIdForUpdate(solicitudId)
                .orElseThrow(() -> new NotFoundException("Solicitud no encontrada"));
        validarTecnicoHabilitado(tecnico);
        validarSolicitudOfertables(solicitud);
        validarCompatibilidad(tecnico, solicitud);
        DatosOferta datos = validarDatos(solicitud, tecnico, request);

        Oferta existente = ofertaRepository
                .findBySolicitudIdAndTecnicoId(solicitudId, tecnico.getId())
                .orElse(null);
        boolean reactivada = existente != null;

        if (reactivada) {
            if (ESTADOS_ACTIVOS.contains(existente.getEstado())) {
                throw new BusinessException(
                        "Ya tiene una oferta activa para esta solicitud");
            }
            if (existente.getEstado() == EstadoOferta.ACEPTADA) {
                throw new BusinessException(
                        "La oferta ya fue aceptada y no se puede volver a enviar");
            }
            existente.getContraofertas().clear();
        }

        Oferta oferta = reactivada
                ? existente
                : Oferta.builder()
                        .solicitud(solicitud)
                        .tecnico(tecnico)
                        .build();

        aplicarDatos(oferta, datos);
        oferta.setEstado(EstadoOferta.ENVIADA);

        if (solicitud.getEstado() == EstadoSolicitud.PUBLICADA) {
            solicitud.setEstado(EstadoSolicitud.CON_OFERTAS);
            solicitudRepository.save(solicitud);
        }
        ofertaRepository.save(oferta);

        notificarCliente(
                solicitud,
                reactivada ? TipoNotificacion.OFERTA_ACTUALIZADA
                        : TipoNotificacion.OFERTA_RECIBIDA,
                reactivada ? "Oferta actualizada" : "Nueva oferta recibida",
                "%s %s %s una oferta de S/ %s para su solicitud."
                        .formatted(
                                tecnico.getNombres(),
                                tecnico.getApellidos(),
                                reactivada ? "actualizó" : "envió",
                                ofertaMapper.precioTotal(oferta).toPlainString()),
                oferta.getId());

        return ofertaMapper.toResponse(oferta);
    }

    @Transactional
    public OfertaResponse actualizar(
            Long usuarioId, Long ofertaId, OfertaRequest request) {
        Oferta oferta = ofertaRepository.findByIdForUpdate(ofertaId)
                .orElseThrow(() -> new NotFoundException("Oferta no encontrada"));
        Tecnico tecnico = obtenerTecnico(usuarioId);
        validarPropietario(oferta, tecnico);

        if (!ESTADOS_ACTIVOS.contains(oferta.getEstado())) {
            throw new BusinessException(
                    "Solo se puede editar una oferta activa");
        }

        Declaracion declaracion = validarDeclaracion(oferta, tecnico, request);
        if (oferta.getEstado() == EstadoOferta.CONTRAOFERTADA) {
            superarContraofertasPendientes(oferta);
        }
        aplicarDatos(oferta, declaracion.datos());
        oferta.setEstado(EstadoOferta.ENVIADA);
        ofertaRepository.save(oferta);

        notificarCliente(
                declaracion.solicitud(),
                TipoNotificacion.OFERTA_ACTUALIZADA,
                "Oferta actualizada",
                "%s %s actualizó su oferta a S/ %s."
                        .formatted(
                                tecnico.getNombres(),
                                tecnico.getApellidos(),
                                ofertaMapper.precioTotal(oferta).toPlainString()),
                oferta.getId());

        return ofertaMapper.toResponse(oferta);
    }

    @Transactional
    public OfertaResponse retirar(Long usuarioId, Long ofertaId) {
        Oferta oferta = ofertaRepository.findByIdForUpdate(ofertaId)
                .orElseThrow(() -> new NotFoundException("Oferta no encontrada"));
        Tecnico tecnico = obtenerTecnico(usuarioId);
        validarPropietario(oferta, tecnico);

        if (!ESTADOS_ACTIVOS.contains(oferta.getEstado())) {
            throw new BusinessException(
                    "Solo se puede retirar una oferta activa");
        }

        superarContraofertasPendientes(oferta);
        oferta.setEstado(EstadoOferta.RETIRADA);
        ofertaRepository.save(oferta);

        notificarCliente(
                oferta.getSolicitud(),
                TipoNotificacion.OFERTA_RETIRADA,
                "Oferta retirada",
                "%s %s retiró su oferta para su solicitud."
                        .formatted(tecnico.getNombres(), tecnico.getApellidos()),
                oferta.getId());

        return ofertaMapper.toResponse(oferta);
    }

    @Transactional(readOnly = true)
    public List<OfertaResponse> listarDelCliente(
            Long usuarioId, Long solicitudId, OfertaOrden orden) {
        Solicitud solicitud = obtenerSolicitudDelCliente(usuarioId, solicitudId);
        List<Oferta> ofertas = ofertaRepository.buscarPorSolicitud(solicitud.getId())
                .stream()
                .filter(oferta -> oferta.getEstado() != EstadoOferta.RETIRADA
                        && oferta.getEstado() != EstadoOferta.EXPIRADA)
                .toList();

        Comparator<Oferta> comparador = comparador(orden == null ? OfertaOrden.PRECIO : orden);
        return ofertas.stream()
                .sorted(comparador)
                .map(ofertaMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OfertaResponse> comparar(
            Long usuarioId, Long solicitudId, List<Long> ofertaIds) {
        obtenerSolicitudDelCliente(usuarioId, solicitudId);
        if (ofertaIds == null || ofertaIds.isEmpty()) {
            throw new BadRequestException("Debe indicar al menos una oferta para comparar");
        }
        if (ofertaIds.size() > 3) {
            throw new BadRequestException("Solo se pueden comparar hasta tres ofertas");
        }
        if (ofertaIds.stream().distinct().count() != ofertaIds.size()) {
            throw new BadRequestException("No se puede repetir una oferta en la comparación");
        }

        List<OfertaResponse> resultado = new ArrayList<>();
        for (Long ofertaId : ofertaIds) {
            Oferta oferta = ofertaRepository.buscarDetallePorId(ofertaId)
                    .orElseThrow(() -> new NotFoundException("Oferta no encontrada"));
            if (!oferta.getSolicitud().getId().equals(solicitudId)) {
                throw new BadRequestException(
                        "La oferta no pertenece a la solicitud indicada");
            }
            resultado.add(ofertaMapper.toResponse(oferta));
        }
        return resultado;
    }

    @Transactional(readOnly = true)
    public List<OfertaResponse> listarMias(Long usuarioId) {
        Tecnico tecnico = obtenerTecnico(usuarioId);
        return ofertaRepository.buscarPorTecnico(tecnico.getId()).stream()
                .map(ofertaMapper::toResponse)
                .toList();
    }

    @Transactional
    public int vencerOfertas() {
        return ofertaRepository.marcarExpiradasDeSolicitudes(
                EstadoOferta.EXPIRADA,
                ESTADOS_ACTIVOS,
                List.of(EstadoSolicitud.VENCIDA, EstadoSolicitud.CANCELADA));
    }

    private Declaracion validarDeclaracion(
            Oferta oferta, Tecnico tecnico, OfertaRequest request) {
        validarTecnicoHabilitado(tecnico);
        Solicitud solicitud = oferta.getSolicitud();
        validarSolicitudOfertables(solicitud);
        validarCompatibilidad(tecnico, solicitud);
        return new Declaracion(solicitud, validarDatos(solicitud, tecnico, request));
    }

    private DatosOferta validarDatos(
            Solicitud solicitud, Tecnico tecnico, OfertaRequest request) {
        Modalidad modalidad = request.modalidad();
        if (modalidad == null || modalidad == Modalidad.INDISTINTO) {
            throw new BadRequestException(
                    "La oferta debe indicar la modalidad DOMICILIO o TALLER");
        }
        validarModalidadDeLaSolicitud(solicitud.getModalidad(), modalidad);
        validarModalidadDelTecnico(tecnico, modalidad);

        BigDecimal precioServicio = escalar(request.precioServicio());
        if (precioServicio == null || precioServicio.signum() <= 0) {
            throw new BadRequestException("El precio del servicio debe ser mayor a cero");
        }

        BigDecimal costoMovilidad = escalar(
                request.costoMovilidad() == null
                        ? BigDecimal.ZERO
                        : request.costoMovilidad());
        if (costoMovilidad.signum() < 0) {
            throw new BadRequestException("La movilidad no puede ser negativa");
        }

        BigDecimal costoDiagnostico = escalar(
                request.costoDiagnostico() == null
                        ? BigDecimal.ZERO
                        : request.costoDiagnostico());
        if (costoDiagnostico.signum() < 0) {
            throw new BadRequestException("El diagnóstico no puede ser negativo");
        }

        if (modalidad == Modalidad.TALLER) {
            if (costoMovilidad.signum() > 0) {
                throw new BadRequestException(
                        "La movilidad solo aplica a la modalidad a domicilio");
            }
        } else {
            BigDecimal tope = solicitud.getDistrito().getTopeMovilidad();
            if (tope == null) {
                tope = parametroSistemaService.obtenerDecimal(
                        ParametroClave.TOPE_MOVILIDAD_DEFECTO);
            }
            if (tope != null && costoMovilidad.compareTo(tope) > 0) {
                throw new BadRequestException(
                        "La movilidad no puede superar el tope de S/ %s para el distrito"
                                .formatted(tope.setScale(2, RoundingMode.HALF_UP)
                                        .toPlainString()));
            }
        }

        Integer tiempoEstimado = request.tiempoEstimadoHoras();
        if (tiempoEstimado == null || tiempoEstimado <= 0) {
            throw new BadRequestException(
                    "El tiempo estimado debe ser mayor a cero");
        }

        int garantia = request.garantiaDias() == null
                ? tecnico.getGarantiaDiasDefecto()
                : request.garantiaDias();
        if (garantia < 0) {
            throw new BadRequestException("La garantía no puede ser negativa");
        }

        String mensaje = normalizarTexto(request.mensaje());
        if (mensaje != null && mensaje.length() > MAX_MENSAJE) {
            throw new BadRequestException(
                    "El mensaje no puede superar los 500 caracteres");
        }

        return new DatosOferta(
                modalidad,
                precioServicio,
                costoMovilidad,
                costoDiagnostico,
                tiempoEstimado,
                garantia,
                mensaje);
    }

    private void validarModalidadDeLaSolicitud(
            Modalidad modalidadSolicitud, Modalidad modalidadOferta) {
        boolean compatible = switch (modalidadSolicitud) {
            case DOMICILIO -> modalidadOferta == Modalidad.DOMICILIO;
            case TALLER -> modalidadOferta == Modalidad.TALLER;
            case INDISTINTO -> true;
        };
        if (!compatible) {
            throw new BadRequestException(
                    "La modalidad de la oferta no coincide con la solicitud");
        }
    }

    private void validarModalidadDelTecnico(Tecnico tecnico, Modalidad modalidad) {
        if (modalidad == Modalidad.DOMICILIO && !tecnico.isOfreceDomicilio()) {
            throw new BadRequestException(
                    "El técnico no ofrece la modalidad a domicilio");
        }
        if (modalidad == Modalidad.TALLER && !tecnico.isOfreceTaller()) {
            throw new BadRequestException(
                    "El técnico no ofrece la modalidad en taller");
        }
    }

    private void validarCompatibilidad(Tecnico tecnico, Solicitud solicitud) {
        boolean mismoDistrito = tecnico.getDistritos() != null
                && tecnico.getDistritos().stream()
                        .anyMatch(distrito -> distrito.getId()
                                .equals(solicitud.getDistrito().getId()));
        if (!mismoDistrito) {
            throw new ForbiddenException(
                    "La solicitud no está disponible para este técnico");
        }
    }

    private void validarTecnicoHabilitado(Tecnico tecnico) {
        if (tecnico.getEstadoVerificacion() != EstadoVerificacion.APROBADO) {
            throw new ForbiddenException(
                    "El técnico debe estar aprobado para enviar ofertas");
        }
        if (tecnico.isBloqueadoPorDeuda()) {
            throw new ForbiddenException(
                    "El técnico está bloqueado por deuda y no puede enviar ofertas");
        }
    }

    private void validarSolicitudOfertables(Solicitud solicitud) {
        if (solicitud.getEstado() != EstadoSolicitud.PUBLICADA
                && solicitud.getEstado() != EstadoSolicitud.CON_OFERTAS) {
            throw new BusinessException(
                    "La solicitud no está disponible para recibir ofertas");
        }
        if (solicitud.getFechaVencimiento() == null
                || !solicitud.getFechaVencimiento().isAfter(LocalDateTime.now())) {
            throw new BusinessException("La solicitud ya venció");
        }
    }

    private void aplicarDatos(Oferta oferta, DatosOferta datos) {
        oferta.setModalidad(datos.modalidad());
        oferta.setPrecioServicio(datos.precioServicio());
        oferta.setCostoMovilidad(datos.costoMovilidad());
        oferta.setCostoDiagnostico(datos.costoDiagnostico());
        oferta.setTiempoEstimadoHoras(datos.tiempoEstimadoHoras());
        oferta.setGarantiaDias(datos.garantiaDias());
        oferta.setMensaje(datos.mensaje());
    }

    private void superarContraofertasPendientes(Oferta oferta) {
        if (oferta.getContraofertas() == null) {
            return;
        }
        for (Contraoferta contraoferta : oferta.getContraofertas()) {
            if (contraoferta.getEstado() == EstadoContraoferta.PENDIENTE) {
                contraoferta.setEstado(EstadoContraoferta.SUPERADA);
            }
        }
    }

    private Comparator<Oferta> comparador(OfertaOrden orden) {
        Comparator<Oferta> porPrecio = Comparator
                .comparing(ofertaMapper::precioTotal);
        Comparator<Oferta> porCalificacion = Comparator
                .comparing(this::calificacion)
                .reversed();
        Comparator<Oferta> porTiempo = Comparator
                .comparingInt(Oferta::getTiempoEstimadoHoras);

        return switch (orden) {
            case PRECIO -> porPrecio
                    .thenComparing(porCalificacion)
                    .thenComparing(Oferta::getId);
            case CALIFICACION -> porCalificacion
                    .thenComparing(porPrecio)
                    .thenComparing(Oferta::getId);
            case TIEMPO -> porTiempo
                    .thenComparing(porPrecio)
                    .thenComparing(Oferta::getId);
        };
    }

    private BigDecimal calificacion(Oferta oferta) {
        BigDecimal calificacion = oferta.getTecnico().getCalificacionPromedio();
        return calificacion == null ? BigDecimal.ZERO : calificacion;
    }

    private void validarPropietario(Oferta oferta, Tecnico tecnico) {
        if (!oferta.getTecnico().getId().equals(tecnico.getId())) {
            throw new ForbiddenException("No tiene acceso a esta oferta");
        }
    }

    private Tecnico obtenerTecnico(Long usuarioId) {
        return tecnicoRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new ForbiddenException(
                        "El usuario no tiene perfil de técnico"));
    }

    private Solicitud obtenerSolicitud(Long solicitudId) {
        return solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new NotFoundException("Solicitud no encontrada"));
    }

    private Solicitud obtenerSolicitudDelCliente(Long usuarioId, Long solicitudId) {
        Solicitud solicitud = obtenerSolicitud(solicitudId);
        if (!solicitud.getCliente().getUsuario().getId().equals(usuarioId)) {
            throw new ForbiddenException("No tiene acceso a esta solicitud");
        }
        return solicitud;
    }

    private void notificarCliente(
            Solicitud solicitud,
            TipoNotificacion tipo,
            String titulo,
            String mensaje,
            Long ofertaId) {
        notificacionService.crear(
                solicitud.getCliente().getUsuario().getId(),
                tipo,
                titulo,
                mensaje,
                "OFERTA",
                ofertaId);
    }

    private BigDecimal escalar(BigDecimal valor) {
        return valor == null ? null : valor.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizarTexto(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }

    private record DatosOferta(
            Modalidad modalidad,
            BigDecimal precioServicio,
            BigDecimal costoMovilidad,
            BigDecimal costoDiagnostico,
            int tiempoEstimadoHoras,
            int garantiaDias,
            String mensaje) {
    }

    private record Declaracion(Solicitud solicitud, DatosOferta datos) {
    }
}
