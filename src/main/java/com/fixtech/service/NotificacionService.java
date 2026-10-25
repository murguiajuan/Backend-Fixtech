package com.fixtech.service;

import com.fixtech.dto.NotificacionResponse;
import com.fixtech.dto.NotificacionesResponse;
import com.fixtech.entity.Notificacion;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.NotFoundException;
import com.fixtech.repository.NotificacionRepository;
import com.fixtech.repository.UsuarioRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificacionService {

    private static final int MAX_TITULO = 120;
    private static final int MAX_MENSAJE = 300;

    private final NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final CorreoNotificacionService correoNotificacionService;

    @Transactional
    public void crear(
            Long usuarioId,
            TipoNotificacion tipo,
            String titulo,
            String mensaje,
            String referenciaTipo,
            Long referenciaId) {
        guardar(usuarioId, tipo, titulo, mensaje, referenciaTipo, referenciaId);
        correoNotificacionService.enviar(usuarioId, tipo, titulo, mensaje);
    }

    @Transactional
    public void crearSinCorreo(
            Long usuarioId,
            TipoNotificacion tipo,
            String titulo,
            String mensaje,
            String referenciaTipo,
            Long referenciaId) {
        guardar(usuarioId, tipo, titulo, mensaje, referenciaTipo, referenciaId);
    }

    public NotificacionesResponse listar(Long usuarioId, boolean soloNoLeidas) {
        List<Notificacion> notificaciones = soloNoLeidas
                ? notificacionRepository
                        .findByUsuarioIdAndLeidaFalseOrderByCreadoEnDesc(usuarioId)
                : notificacionRepository.findByUsuarioIdOrderByCreadoEnDesc(usuarioId);
        long noLeidas = notificacionRepository.countByUsuarioIdAndLeidaFalse(usuarioId);
        return new NotificacionesResponse(
                notificaciones.stream().map(this::aResponse).toList(),
                noLeidas);
    }

    @Transactional
    public NotificacionResponse marcarLeida(Long usuarioId, Long notificacionId) {
        Notificacion notificacion = notificacionRepository
                .findByIdAndUsuarioId(notificacionId, usuarioId)
                .orElseThrow(() -> new NotFoundException("Notificación no encontrada"));
        notificacion.setLeida(true);
        notificacionRepository.save(notificacion);
        return aResponse(notificacion);
    }

    @Transactional
    public int marcarTodasLeidas(Long usuarioId) {
        return notificacionRepository.marcarTodasLeidas(usuarioId);
    }

    private void guardar(
            Long usuarioId,
            TipoNotificacion tipo,
            String titulo,
            String mensaje,
            String referenciaTipo,
            Long referenciaId) {
        Notificacion notificacion = Notificacion.builder()
                .usuario(usuarioRepository.getReferenceById(usuarioId))
                .tipo(tipo)
                .titulo(recortar(titulo, MAX_TITULO))
                .mensaje(recortar(mensaje, MAX_MENSAJE))
                .referenciaTipo(normalizarTexto(referenciaTipo))
                .referenciaId(referenciaId)
                .leida(false)
                .build();
        notificacionRepository.save(notificacion);
    }

    private NotificacionResponse aResponse(Notificacion notificacion) {
        return new NotificacionResponse(
                notificacion.getId(),
                notificacion.getTipo(),
                notificacion.getTitulo(),
                notificacion.getMensaje(),
                notificacion.getReferenciaTipo(),
                notificacion.getReferenciaId(),
                notificacion.isLeida(),
                notificacion.getCreadoEn());
    }

    private String recortar(String valor, int maximo) {
        String normalizado = valor == null ? "" : valor.trim();
        return normalizado.length() <= maximo
                ? normalizado
                : normalizado.substring(0, maximo);
    }

    private String normalizarTexto(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }
}
