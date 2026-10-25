package com.fixtech.service;

import com.fixtech.dto.ModeracionResenaRequest;
import com.fixtech.dto.ReporteResenaRequest;
import com.fixtech.dto.ResenaRequest;
import com.fixtech.dto.ResenaResponse;
import com.fixtech.dto.RespuestaResenaRequest;
import com.fixtech.entity.Resena;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.enums.EstadoResena;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.ParametroClave;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.BusinessException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.mapper.ResenaMapper;
import com.fixtech.repository.AdministradorRepository;
import com.fixtech.repository.ResenaRepository;
import com.fixtech.repository.ServicioRepository;
import com.fixtech.repository.TecnicoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResenaService {

    private static final int MAX_COMENTARIO = 1000;
    private static final int MAX_RESPUESTA = 1000;
    private static final int MAX_MOTIVO = 300;

    private final ResenaRepository resenaRepository;
    private final TecnicoRepository tecnicoRepository;
    private final ServicioRepository servicioRepository;
    private final AdministradorRepository administradorRepository;
    private final ServicioAccesoService servicioAccesoService;
    private final ServicioEventoService servicioEventoService;
    private final ParametroSistemaService parametroSistemaService;
    private final NotificacionService notificacionService;
    private final AuditoriaService auditoriaService;
    private final ResenaMapper resenaMapper;

    @Transactional
    public ResenaResponse registrar(
            Long usuarioId, Long servicioId, ResenaRequest request) {
        Servicio servicio = servicioAccesoService.bloquear(servicioId);
        servicioAccesoService.validarCliente(servicio, usuarioId);
        if (resenaRepository.existsByServicioId(servicioId)) {
            throw new BusinessException("Ya registró una reseña para este servicio");
        }
        if (servicio.getEstado() != EstadoServicio.CONFIRMADO) {
            throw new BusinessException(
                    "Solo se puede reseñar un servicio confirmado");
        }
        validarRequest(request);
        validarPlazo(servicio);

        Resena resena = Resena.builder()
                .servicio(servicio)
                .cliente(servicio.getCliente())
                .tecnico(servicio.getTecnico())
                .estrellas(request.estrellas())
                .puntualidad(request.puntualidad())
                .trato(request.trato())
                .calidad(request.calidad())
                .precioJusto(request.precioJusto())
                .comentario(normalizarTexto(request.comentario(), MAX_COMENTARIO, "El comentario"))
                .estado(EstadoResena.VISIBLE)
                .build();
        resenaRepository.save(resena);

        EstadoServicio anterior = servicio.getEstado();
        servicio.setEstado(EstadoServicio.CALIFICADO);
        servicioRepository.save(servicio);
        servicioEventoService.registrarCambio(
                servicio,
                anterior,
                EstadoServicio.CALIFICADO,
                usuarioId,
                "Reseña registrada por el cliente");

        recalcularEstadisticas(servicio.getTecnico());
        notificacionService.crear(
                servicio.getTecnico().getUsuario().getId(),
                TipoNotificacion.RESENA_REGISTRADA,
                "Nueva reseña recibida",
                "El cliente calificó el servicio #%d con %d estrella(s)."
                        .formatted(servicioId, request.estrellas()),
                "RESENA",
                resena.getId());
        return resenaMapper.toResponse(resena);
    }

    public ResenaResponse obtenerPorServicio(Long usuarioId, Long servicioId) {
        servicioAccesoService.obtenerParaParticipante(usuarioId, servicioId);
        Resena resena = resenaRepository.findByServicioId(servicioId)
                .orElseThrow(() -> new NotFoundException("Reseña no encontrada"));
        return resenaMapper.toResponse(resena);
    }

    public List<ResenaResponse> listarPorTecnico(Long tecnicoId) {
        tecnicoRepository.findById(tecnicoId)
                .orElseThrow(() -> new NotFoundException("Técnico no encontrado"));
        return resenaRepository
                .findByTecnicoIdAndEstadoOrderByCreadoEnDescIdDesc(
                        tecnicoId, EstadoResena.VISIBLE)
                .stream()
                .map(resenaMapper::toResponse)
                .toList();
    }

    @Transactional
    public ResenaResponse responder(
            Long usuarioId,
            Long servicioId,
            Long resenaId,
            RespuestaResenaRequest request) {
        Servicio servicio = servicioAccesoService.obtenerParaParticipante(
                usuarioId, servicioId);
        servicioAccesoService.validarTecnico(servicio, usuarioId);
        Resena resena = obtenerResenaBloqueada(resenaId, servicioId);
        if (StringUtils.hasText(resena.getRespuestaTecnico())) {
            throw new BusinessException("La reseña ya tiene una respuesta del técnico");
        }
        String respuesta = normalizarTextoObligatorio(
                request == null ? null : request.respuesta(),
                MAX_RESPUESTA,
                "La respuesta es obligatoria");
        resena.setRespuestaTecnico(respuesta);
        resena.setFechaRespuesta(LocalDateTime.now());
        resenaRepository.save(resena);

        notificacionService.crear(
                resena.getCliente().getUsuario().getId(),
                TipoNotificacion.RESENA_RESPUESTA,
                "El técnico respondió su reseña",
                "El técnico respondió la reseña del servicio #%d.".formatted(servicioId),
                "RESENA",
                resena.getId());
        return resenaMapper.toResponse(resena);
    }

    @Transactional
    public ResenaResponse reportar(
            Long usuarioId,
            Long servicioId,
            Long resenaId,
            ReporteResenaRequest request) {
        Servicio servicio = servicioAccesoService.obtenerParaParticipante(
                usuarioId, servicioId);
        servicioAccesoService.validarTecnico(servicio, usuarioId);
        String motivo = normalizarTextoObligatorio(
                request == null ? null : request.motivo(),
                MAX_MOTIVO,
                "El motivo del reporte es obligatorio");
        Resena resena = obtenerResenaBloqueada(resenaId, servicioId);
        if (resena.getEstado() != EstadoResena.VISIBLE) {
            throw new BusinessException(
                    "Solo se puede reportar una reseña visible");
        }
        resena.setEstado(EstadoResena.REPORTADA);
        resena.setMotivoReporte(motivo);
        resenaRepository.save(resena);
        recalcularEstadisticas(resena.getTecnico());

        Long clienteUsuarioId = resena.getCliente().getUsuario().getId();
        for (Long adminUsuarioId : administradorRepository.buscarUsuarioIds()) {
            notificacionService.crear(
                    adminUsuarioId,
                    TipoNotificacion.RESENA_REPORTADA,
                    "Reseña reportada",
                    "El técnico reportó la reseña del servicio #%d. Motivo: %s"
                            .formatted(servicioId, motivo),
                    "RESENA",
                    resena.getId());
        }
        notificacionService.crear(
                clienteUsuarioId,
                TipoNotificacion.RESENA_REPORTADA,
                "Su reseña fue reportada",
                "La reseña del servicio #%d será revisada por administración."
                        .formatted(servicioId),
                "RESENA",
                resena.getId());
        return resenaMapper.toResponse(resena);
    }

    public List<ResenaResponse> listarAdmin(EstadoResena estado) {
        List<Resena> resenas = estado == null
                ? resenaRepository.findAllByOrderByCreadoEnDescIdDesc()
                : resenaRepository.findByEstadoOrderByCreadoEnDescIdDesc(estado);
        return resenas.stream().map(resenaMapper::toResponse).toList();
    }

    @Transactional
    public ResenaResponse moderar(
            Long adminUsuarioId,
            Long resenaId,
            ModeracionResenaRequest request) {
        administradorRepository.findByUsuarioId(adminUsuarioId)
                .orElseThrow(() -> new ForbiddenException(
                        "El usuario no tiene perfil de administrador"));
        if (request == null || request.estado() == null) {
            throw new BadRequestException("Debe indicar el estado de la reseña");
        }
        if (request.estado() != EstadoResena.VISIBLE
                && request.estado() != EstadoResena.OCULTA) {
            throw new BadRequestException(
                    "La moderación solo permite los estados VISIBLE u OCULTA");
        }
        Resena resena = resenaRepository.findByIdForUpdate(resenaId)
                .orElseThrow(() -> new NotFoundException("Reseña no encontrada"));
        if (resena.getEstado() != EstadoResena.REPORTADA) {
            throw new BusinessException(
                    "Solo se puede moderar una reseña reportada");
        }

        String motivo = resena.getMotivoReporte();
        resena.setEstado(request.estado());
        if (request.estado() == EstadoResena.VISIBLE) {
            resena.setMotivoReporte(null);
        }
        resenaRepository.save(resena);
        recalcularEstadisticas(resena.getTecnico());

        notificacionService.crear(
                resena.getCliente().getUsuario().getId(),
                TipoNotificacion.RESENA_MODERADA,
                "Reseña moderada",
                "La reseña del servicio #%d fue %s por administración."
                        .formatted(
                                resena.getServicio().getId(),
                                request.estado() == EstadoResena.VISIBLE
                                        ? "habilitada"
                                        : "ocultada"),
                "RESENA",
                resena.getId());
        notificacionService.crear(
                resena.getTecnico().getUsuario().getId(),
                TipoNotificacion.RESENA_MODERADA,
                "Reseña moderada",
                "La reseña del servicio #%d fue %s por administración."
                        .formatted(
                                resena.getServicio().getId(),
                                request.estado() == EstadoResena.VISIBLE
                                        ? "habilitada"
                                        : "ocultada"),
                "RESENA",
                resena.getId());
        auditoriaService.registrar(
                adminUsuarioId,
                "MODERAR_RESENA",
                "resena",
                resena.getId(),
                "Estado anterior REPORTADA, nuevo %s. Motivo: %s"
                        .formatted(request.estado(), motivo));
        return resenaMapper.toResponse(resena);
    }

    private Resena obtenerResenaBloqueada(Long resenaId, Long servicioId) {
        Resena resena = resenaRepository.findByIdForUpdate(resenaId)
                .orElseThrow(() -> new NotFoundException("Reseña no encontrada"));
        if (!resena.getServicio().getId().equals(servicioId)) {
            throw new NotFoundException("Reseña no encontrada");
        }
        return resena;
    }

    private void validarRequest(ResenaRequest request) {
        if (request == null || request.estrellas() == null) {
            throw new BadRequestException("Debe indicar una calificación");
        }
        validarCriterio(request.estrellas(), "La calificación");
        validarCriterio(request.puntualidad(), "La puntualidad");
        validarCriterio(request.trato(), "El trato");
        validarCriterio(request.calidad(), "La calidad");
        validarCriterio(request.precioJusto(), "El precio justo");
        if (request.comentario() != null
                && request.comentario().trim().length() > MAX_COMENTARIO) {
            throw new BadRequestException(
                    "El comentario no puede superar los %d caracteres"
                            .formatted(MAX_COMENTARIO));
        }
    }

    private void validarCriterio(Integer valor, String nombre) {
        if (valor != null && (valor < 1 || valor > 5)) {
            throw new BadRequestException(
                    "%s debe estar entre 1 y 5".formatted(nombre));
        }
    }

    private void validarPlazo(Servicio servicio) {
        if (servicio.getFechaConfirmacion() == null) {
            throw new BusinessException(
                    "El servicio no tiene una fecha de confirmación registrada");
        }
        int dias = parametroSistemaService.obtenerEntero(ParametroClave.DIAS_PARA_RESENAR);
        LocalDateTime limite = servicio.getFechaConfirmacion().plusDays(dias);
        if (LocalDateTime.now().isAfter(limite)) {
            throw new BusinessException(
                    "El plazo de %d días para reseñar el servicio ya venció"
                            .formatted(dias));
        }
    }

    private void recalcularEstadisticas(Tecnico tecnico) {
        Tecnico bloqueado = tecnicoRepository.findByIdForUpdate(tecnico.getId())
                .orElseThrow(() -> new NotFoundException("Técnico no encontrado"));
        long total = resenaRepository.countByTecnicoIdAndEstado(
                bloqueado.getId(), EstadoResena.VISIBLE);
        Double promedio = total == 0
                ? null
                : resenaRepository.promedioEstrellas(
                        bloqueado.getId(), EstadoResena.VISIBLE);
        bloqueado.setTotalResenas(Math.toIntExact(total));
        bloqueado.setCalificacionPromedio(promedio == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(promedio)
                        .setScale(2, RoundingMode.HALF_UP));
        tecnicoRepository.save(bloqueado);
    }

    private String normalizarTexto(String valor, int maximo, String nombre) {
        if (!StringUtils.hasText(valor)) {
            return null;
        }
        String normalizado = valor.trim();
        if (normalizado.length() > maximo) {
            throw new BadRequestException(
                    "%s no puede superar los %d caracteres".formatted(nombre, maximo));
        }
        return normalizado;
    }

    private String normalizarTextoObligatorio(
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
}
