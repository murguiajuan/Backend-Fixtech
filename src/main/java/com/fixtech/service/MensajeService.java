package com.fixtech.service;

import com.fixtech.dto.MensajeRequest;
import com.fixtech.dto.MensajeResponse;
import com.fixtech.entity.Mensaje;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BadRequestException;
import com.fixtech.mapper.ServicioMapper;
import com.fixtech.repository.MensajeRepository;
import com.fixtech.repository.UsuarioRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MensajeService {

    private static final int MAX_CONTENIDO = 1000;

    private final MensajeRepository mensajeRepository;
    private final UsuarioRepository usuarioRepository;
    private final ServicioAccesoService servicioAccesoService;
    private final ServicioEventoService servicioEventoService;
    private final ServicioMapper servicioMapper;

    @Transactional
    public MensajeResponse enviar(
            Long usuarioId, Long servicioId, MensajeRequest request) {
        Servicio servicio = servicioAccesoService.obtenerParaParticipante(
                usuarioId, servicioId);
        if (request == null || !StringUtils.hasText(request.contenido())) {
            throw new BadRequestException("El mensaje no puede estar vacío");
        }
        String contenido = request.contenido().trim();
        if (contenido.length() > MAX_CONTENIDO) {
            throw new BadRequestException(
                    "El mensaje no puede superar los %d caracteres"
                            .formatted(MAX_CONTENIDO));
        }

        Mensaje mensaje = Mensaje.builder()
                .servicio(servicio)
                .emisor(usuarioRepository.getReferenceById(usuarioId))
                .contenido(contenido)
                .leido(false)
                .build();
        mensajeRepository.save(mensaje);

        servicioEventoService.notificarContraparte(
                servicio,
                usuarioId,
                TipoNotificacion.MENSAJE_NUEVO,
                "Nuevo mensaje en el servicio",
                "Recibió un nuevo mensaje sobre el servicio #%d."
                        .formatted(servicioId));

        return servicioMapper.toMensaje(mensaje, usuarioId);
    }

    @Transactional
    public List<MensajeResponse> listar(Long usuarioId, Long servicioId) {
        servicioAccesoService.obtenerParaParticipante(usuarioId, servicioId);
        mensajeRepository.marcarLeidos(servicioId, usuarioId);
        return mensajeRepository.buscarPorServicio(servicioId).stream()
                .map(mensaje -> servicioMapper.toMensaje(mensaje, usuarioId))
                .toList();
    }
}
