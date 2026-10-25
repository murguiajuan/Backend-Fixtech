package com.fixtech.service;

import com.fixtech.config.CorreoProperties;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CorreoNotificacionService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final CorreoProperties properties;
    private final UsuarioRepository usuarioRepository;

    public void enviar(
            Long usuarioId,
            TipoNotificacion tipo,
            String titulo,
            String mensaje) {
        if (!properties.isHabilitado()) {
            log.debug("Correo deshabilitado para la notificación {} del usuario {}",
                    tipo, usuarioId);
            return;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("Correo habilitado sin SMTP configurado; no se envió la notificación {}",
                    tipo);
            return;
        }

        Usuario usuario = usuarioRepository.findById(usuarioId).orElse(null);
        if (usuario == null) {
            return;
        }

        SimpleMailMessage correo = new SimpleMailMessage();
        correo.setFrom(properties.getRemitente());
        correo.setTo(usuario.getCorreo());
        correo.setSubject(titulo);
        correo.setText(mensaje + "\n\nFIXTECH");
        try {
            mailSender.send(correo);
        } catch (MailException ex) {
            log.warn("No se pudo enviar el correo de la notificación {}", tipo, ex);
        }
    }
}
