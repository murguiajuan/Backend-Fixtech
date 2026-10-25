package com.fixtech.service;

import com.fixtech.dto.ContraofertaRequest;
import com.fixtech.dto.RespuestaNegociacionResponse;
import com.fixtech.dto.ServicioResponse;
import com.fixtech.entity.Contraoferta;
import com.fixtech.entity.Oferta;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.Solicitud;
import com.fixtech.entity.enums.AccionContraoferta;
import com.fixtech.entity.enums.EmisorContraoferta;
import com.fixtech.entity.enums.EstadoContraoferta;
import com.fixtech.entity.enums.EstadoOferta;
import com.fixtech.entity.enums.EstadoSolicitud;
import com.fixtech.entity.enums.ParametroClave;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.BusinessException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.mapper.OfertaMapper;
import com.fixtech.repository.OfertaRepository;
import com.fixtech.repository.SolicitudRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class NegociacionService {

    private static final Set<EstadoOferta> ESTADOS_ACTIVOS = EnumSet.of(
            EstadoOferta.ENVIADA, EstadoOferta.CONTRAOFERTADA);
    private static final int MAX_MENSAJE = 300;

    private final OfertaRepository ofertaRepository;
    private final SolicitudRepository solicitudRepository;
    private final ParametroSistemaService parametroSistemaService;
    private final NotificacionService notificacionService;
    private final ServicioService servicioService;
    private final OfertaMapper ofertaMapper;

    @Transactional
    public RespuestaNegociacionResponse responder(
            Long usuarioId, Long ofertaId, ContraofertaRequest request) {
        OfertaBloqueada bloqueada = bloquear(ofertaId);
        Oferta oferta = bloqueada.oferta();
        Solicitud solicitud = bloqueada.solicitud();
        EmisorContraoferta emisor = determinarEmisor(usuarioId, oferta, solicitud);
        validarSolicitudVigente(solicitud);

        if (!ESTADOS_ACTIVOS.contains(oferta.getEstado())) {
            throw new BusinessException(
                    "La oferta ya no está disponible para negociar");
        }

        List<Contraoferta> contraofertas = oferta.getContraofertas();
        Contraoferta pendiente = buscarPendiente(contraofertas);

        return switch (request.accion()) {
            case CONTRAOFERTA -> registrarContraoferta(
                    oferta, solicitud, emisor, pendiente, contraofertas, request);
            case ACEPTAR -> aceptarContraoferta(
                    oferta, solicitud, emisor, pendiente);
            case RECHAZAR -> rechazarContraoferta(
                    oferta, solicitud, emisor, pendiente);
        };
    }

    @Transactional
    public ServicioResponse aceptar(Long usuarioId, Long ofertaId) {
        OfertaBloqueada bloqueada = bloquear(ofertaId);
        Oferta oferta = bloqueada.oferta();
        Solicitud solicitud = bloqueada.solicitud();

        if (!solicitud.getCliente().getUsuario().getId().equals(usuarioId)) {
            throw new ForbiddenException(
                    "Solo el cliente dueño de la solicitud puede aceptar la oferta");
        }
        validarSolicitudVigente(solicitud);
        if (!ESTADOS_ACTIVOS.contains(oferta.getEstado())) {
            throw new BusinessException(
                    "La oferta ya no está disponible para ser aceptada");
        }
        if (buscarPendiente(oferta.getContraofertas()) != null) {
            throw new BusinessException(
                    "Debe responder la contraoferta pendiente antes de aceptar la oferta");
        }

        return aceptarInterno(oferta, solicitud);
    }

    private RespuestaNegociacionResponse registrarContraoferta(
            Oferta oferta,
            Solicitud solicitud,
            EmisorContraoferta emisor,
            Contraoferta pendiente,
            List<Contraoferta> contraofertas,
            ContraofertaRequest request) {
        BigDecimal monto = request.montoPropuesto();
        if (monto == null || monto.signum() <= 0) {
            throw new BadRequestException(
                    "El monto propuesto debe ser mayor a cero");
        }
        monto = monto.setScale(2, RoundingMode.HALF_UP);

        String mensaje = normalizarTexto(request.mensaje());
        if (mensaje != null && mensaje.length() > MAX_MENSAJE) {
            throw new BadRequestException(
                    "El mensaje no puede superar los 300 caracteres");
        }

        if (pendiente == null) {
            if (emisor != EmisorContraoferta.CLIENTE) {
                throw new BusinessException(
                        "El cliente debe iniciar la negociación con una contraoferta");
            }
        } else if (pendiente.getEmisor() == emisor) {
            throw new BusinessException(
                    "Ya envió una contraoferta; debe esperar la respuesta de la otra parte");
        }

        int maximoRondas = maximoRondas();
        if (contraofertas.size() >= maximoRondas) {
            throw new BusinessException(
                    "Se alcanzó el máximo de %d rondas de contraoferta"
                            .formatted(maximoRondas));
        }

        if (pendiente != null) {
            pendiente.setEstado(EstadoContraoferta.SUPERADA);
        }

        Contraoferta contraoferta = Contraoferta.builder()
                .oferta(oferta)
                .emisor(emisor)
                .ronda(contraofertas.size() + 1)
                .montoPropuesto(monto)
                .mensaje(mensaje)
                .estado(EstadoContraoferta.PENDIENTE)
                .build();
        oferta.getContraofertas().add(contraoferta);
        oferta.setEstado(EstadoOferta.CONTRAOFERTADA);
        ofertaRepository.save(oferta);

        if (emisor == EmisorContraoferta.CLIENTE) {
            notificar(
                    oferta.getTecnico().getUsuario().getId(),
                    TipoNotificacion.CONTRAOFERTA_RECIBIDA,
                    "Nueva contraoferta",
                    "El cliente propuso S/ %s para el servicio. Revise la contraoferta."
                            .formatted(monto.toPlainString()),
                    oferta.getId());
        } else {
            notificar(
                    solicitud.getCliente().getUsuario().getId(),
                    TipoNotificacion.CONTRAOFERTA_RECIBIDA,
                    "Nueva contraoferta",
                    "El técnico propuso S/ %s para el servicio. Revise la contraoferta."
                            .formatted(monto.toPlainString()),
                    oferta.getId());
        }

        return new RespuestaNegociacionResponse(
                ofertaMapper.toResponse(oferta), null);
    }

    private RespuestaNegociacionResponse aceptarContraoferta(
            Oferta oferta,
            Solicitud solicitud,
            EmisorContraoferta emisor,
            Contraoferta pendiente) {
        validarPendiente(pendiente, emisor);
        pendiente.setEstado(EstadoContraoferta.ACEPTADA);
        oferta.setPrecioServicio(
                pendiente.getMontoPropuesto().setScale(2, RoundingMode.HALF_UP));

        if (emisor == EmisorContraoferta.TECNICO) {
            oferta.setEstado(EstadoOferta.ENVIADA);
            ofertaRepository.save(oferta);
            notificar(
                    solicitud.getCliente().getUsuario().getId(),
                    TipoNotificacion.OFERTA_ACTUALIZADA,
                    "Contraoferta aceptada",
                    "El técnico aceptó su propuesta de S/ %s. Puede aceptar la oferta para crear el servicio."
                            .formatted(pendiente.getMontoPropuesto().toPlainString()),
                    oferta.getId());
            return new RespuestaNegociacionResponse(
                    ofertaMapper.toResponse(oferta), null);
        }

        ServicioResponse servicio = aceptarInterno(oferta, solicitud);
        return new RespuestaNegociacionResponse(
                ofertaMapper.toResponse(oferta), servicio);
    }

    private RespuestaNegociacionResponse rechazarContraoferta(
            Oferta oferta,
            Solicitud solicitud,
            EmisorContraoferta emisor,
            Contraoferta pendiente) {
        validarPendiente(pendiente, emisor);
        pendiente.setEstado(EstadoContraoferta.RECHAZADA);
        oferta.setEstado(EstadoOferta.RECHAZADA);
        ofertaRepository.save(oferta);

        Long destinatario = pendiente.getEmisor() == EmisorContraoferta.CLIENTE
                ? solicitud.getCliente().getUsuario().getId()
                : oferta.getTecnico().getUsuario().getId();
        notificar(
                destinatario,
                TipoNotificacion.OFERTA_RECHAZADA,
                "Contraoferta rechazada",
                "Su contraoferta de S/ %s fue rechazada."
                        .formatted(pendiente.getMontoPropuesto().toPlainString()),
                oferta.getId());

        return new RespuestaNegociacionResponse(
                ofertaMapper.toResponse(oferta), null);
    }

    private ServicioResponse aceptarInterno(Oferta oferta, Solicitud solicitud) {
        oferta.setEstado(EstadoOferta.ACEPTADA);
        solicitud.setEstado(EstadoSolicitud.ACEPTADA);

        if (oferta.getContraofertas() != null) {
            for (Contraoferta contraoferta : oferta.getContraofertas()) {
                if (contraoferta.getEstado() == EstadoContraoferta.PENDIENTE) {
                    contraoferta.setEstado(EstadoContraoferta.SUPERADA);
                }
            }
        }

        ofertaRepository.save(oferta);
        solicitudRepository.save(solicitud);

        List<Oferta> otrasOfertas = ofertaRepository.buscarOtrasActivas(
                solicitud.getId(), oferta.getId(), ESTADOS_ACTIVOS);
        ofertaRepository.rechazarDemas(
                solicitud.getId(),
                oferta.getId(),
                ESTADOS_ACTIVOS,
                EstadoOferta.RECHAZADA);

        Servicio servicio = servicioService.crearDesdeOferta(oferta, solicitud);

        notificar(
                oferta.getTecnico().getUsuario().getId(),
                TipoNotificacion.OFERTA_ACEPTADA,
                "Oferta aceptada",
                "El cliente aceptó su oferta. El servicio quedó programado.",
                "SERVICIO",
                servicio.getId());
        notificar(
                solicitud.getCliente().getUsuario().getId(),
                TipoNotificacion.OFERTA_ACEPTADA,
                "Servicio creado",
                "Aceptó la oferta de %s %s. El servicio quedó programado."
                        .formatted(
                                oferta.getTecnico().getNombres(),
                                oferta.getTecnico().getApellidos()),
                "SERVICIO",
                servicio.getId());

        for (Oferta otra : otrasOfertas) {
            notificar(
                    otra.getTecnico().getUsuario().getId(),
                    TipoNotificacion.OFERTA_RECHAZADA,
                    "Oferta no seleccionada",
                    "El cliente seleccionó otra oferta para la solicitud.",
                    "OFERTA",
                    otra.getId());
        }

        return ofertaMapper.toServicioResponse(servicio);
    }

    private void validarPendiente(
            Contraoferta pendiente, EmisorContraoferta emisor) {
        if (pendiente == null) {
            throw new BusinessException("No hay una contraoferta pendiente");
        }
        if (pendiente.getEmisor() == emisor) {
            throw new BusinessException(
                    "No puede responder su propia contraoferta");
        }
    }

    private OfertaBloqueada bloquear(Long ofertaId) {
        Long solicitudId = ofertaRepository.findSolicitudIdPorOfertaId(ofertaId)
                .orElseThrow(() -> new NotFoundException("Oferta no encontrada"));
        Solicitud solicitud = solicitudRepository.findByIdForUpdate(solicitudId)
                .orElseThrow(() -> new NotFoundException("Solicitud no encontrada"));
        Oferta oferta = ofertaRepository.findByIdForUpdate(ofertaId)
                .orElseThrow(() -> new NotFoundException("Oferta no encontrada"));
        return new OfertaBloqueada(oferta, solicitud);
    }

    private EmisorContraoferta determinarEmisor(
            Long usuarioId, Oferta oferta, Solicitud solicitud) {
        if (solicitud.getCliente().getUsuario().getId().equals(usuarioId)) {
            return EmisorContraoferta.CLIENTE;
        }
        if (oferta.getTecnico().getUsuario().getId().equals(usuarioId)) {
            return EmisorContraoferta.TECNICO;
        }
        throw new ForbiddenException("No tiene acceso a esta oferta");
    }

    private Contraoferta buscarPendiente(List<Contraoferta> contraofertas) {
        if (contraofertas == null) {
            return null;
        }
        Contraoferta pendiente = null;
        for (Contraoferta contraoferta : contraofertas) {
            if (contraoferta.getEstado() == EstadoContraoferta.PENDIENTE) {
                pendiente = contraoferta;
            }
        }
        return pendiente;
    }

    private void validarSolicitudVigente(Solicitud solicitud) {
        if (solicitud.getEstado() != EstadoSolicitud.PUBLICADA
                && solicitud.getEstado() != EstadoSolicitud.CON_OFERTAS) {
            throw new BusinessException(
                    "La solicitud ya no está disponible para negociar");
        }
        if (solicitud.getFechaVencimiento() == null
                || !solicitud.getFechaVencimiento().isAfter(LocalDateTime.now())) {
            throw new BusinessException("La solicitud ya venció");
        }
    }

    private int maximoRondas() {
        int configurado = parametroSistemaService.obtenerEntero(
                ParametroClave.MAX_RONDAS_CONTRAOFERTA);
        return Math.min(Math.max(configurado, 1), 3);
    }

    private void notificar(
            Long usuarioId,
            TipoNotificacion tipo,
            String titulo,
            String mensaje,
            Long referenciaId) {
        notificar(usuarioId, tipo, titulo, mensaje, "OFERTA", referenciaId);
    }

    private void notificar(
            Long usuarioId,
            TipoNotificacion tipo,
            String titulo,
            String mensaje,
            String referenciaTipo,
            Long referenciaId) {
        notificacionService.crear(
                usuarioId, tipo, titulo, mensaje, referenciaTipo, referenciaId);
    }

    private String normalizarTexto(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }

    private record OfertaBloqueada(Oferta oferta, Solicitud solicitud) {
    }
}
