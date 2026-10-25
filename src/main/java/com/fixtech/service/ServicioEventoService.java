package com.fixtech.service;

import com.fixtech.entity.HistorialEstadoServicio;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.repository.HistorialEstadoServicioRepository;
import com.fixtech.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ServicioEventoService {

    private static final int MAX_COMENTARIO = 300;

    private final HistorialEstadoServicioRepository historialRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionService notificacionService;

    public void registrarCambio(
            Servicio servicio,
            EstadoServicio anterior,
            EstadoServicio nuevo,
            Long usuarioId,
            String comentario) {
        HistorialEstadoServicio historial = HistorialEstadoServicio.builder()
                .servicio(servicio)
                .estadoAnterior(anterior)
                .estadoNuevo(nuevo)
                .cambiadoPor(usuarioId == null
                        ? null
                        : usuarioRepository.getReferenceById(usuarioId))
                .comentario(recortar(comentario))
                .build();
        historialRepository.save(historial);
    }

    public void notificar(
            Long usuarioId,
            TipoNotificacion tipo,
            String titulo,
            String mensaje,
            Long servicioId) {
        notificacionService.crear(
                usuarioId, tipo, titulo, mensaje, "SERVICIO", servicioId);
    }

    public void notificarSinCorreo(
            Long usuarioId,
            TipoNotificacion tipo,
            String titulo,
            String mensaje,
            Long servicioId) {
        notificacionService.crearSinCorreo(
                usuarioId, tipo, titulo, mensaje, "SERVICIO", servicioId);
    }

    public void notificarContraparte(
            Servicio servicio,
            Long actorUsuarioId,
            TipoNotificacion tipo,
            String titulo,
            String mensaje) {
        Long clienteId = servicio.getCliente().getUsuario().getId();
        Long tecnicoId = servicio.getTecnico().getUsuario().getId();
        Long destinatario = actorUsuarioId != null && actorUsuarioId.equals(clienteId)
                ? tecnicoId
                : clienteId;
        notificar(destinatario, tipo, titulo, mensaje, servicio.getId());
    }

    private String recortar(String valor) {
        if (valor == null) {
            return null;
        }
        String normalizado = valor.trim();
        if (normalizado.isEmpty()) {
            return null;
        }
        return normalizado.length() <= MAX_COMENTARIO
                ? normalizado
                : normalizado.substring(0, MAX_COMENTARIO);
    }
}
