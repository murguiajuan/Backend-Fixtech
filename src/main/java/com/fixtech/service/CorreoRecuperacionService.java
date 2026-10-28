package com.fixtech.service;

import com.fixtech.config.CorreoProperties;
import com.fixtech.exception.ServiceUnavailableException;
import java.util.Objects;
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
public class CorreoRecuperacionService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final CorreoProperties properties;

    public boolean estaHabilitado() {
        return properties.isHabilitado();
    }

    public void enviarCodigo(String correo, String codigo, int minutosVigencia) {
        if (!properties.isHabilitado()) {
            throw new ServiceUnavailableException(
                    "El envío de correos no está configurado. Intente más tarde.");
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new ServiceUnavailableException(
                    "El envío de correos no está configurado. Intente más tarde.");
        }

        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(properties.getRemitente());
        mensaje.setTo(Objects.requireNonNull(correo));
        mensaje.setSubject("Código de recuperación FIXTECH");
        mensaje.setText("""
                Tu código de recuperación de FIXTECH es: %s

                El código vence en %d minutos y solo puede usarse una vez.
                Si no solicitaste este código, ignora este mensaje.

                FIXTECH
                """.formatted(codigo, minutosVigencia));

        try {
            mailSender.send(mensaje);
        } catch (MailException ex) {
            log.warn("No se pudo enviar el código de recuperación a {}", correo, ex);
            throw new ServiceUnavailableException(
                    "No se pudo enviar el código de recuperación. Intente más tarde.");
        }
    }
}
