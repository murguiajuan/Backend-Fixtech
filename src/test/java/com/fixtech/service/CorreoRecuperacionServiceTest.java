package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.config.CorreoProperties;
import com.fixtech.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class CorreoRecuperacionServiceTest {

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    private JavaMailSender mailSender;

    private CorreoProperties properties;
    private CorreoRecuperacionService service;

    @BeforeEach
    void configurar() {
        properties = new CorreoProperties();
        properties.setRemitente("no-reply@fixtech.pe");
        service = new CorreoRecuperacionService(mailSenderProvider, properties);
    }

    @Test
    void rechaza_el_envio_si_el_correo_esta_deshabilitado() {
        assertThrows(ServiceUnavailableException.class,
                () -> service.enviarCodigo("cliente@fixtech.pe", "123456", 10));

        verify(mailSenderProvider, never()).getIfAvailable();
    }

    @Test
    void rechaza_el_envio_si_no_hay_smtp_configurado() {
        properties.setHabilitado(true);
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);

        assertThrows(ServiceUnavailableException.class,
                () -> service.enviarCodigo("cliente@fixtech.pe", "123456", 10));
    }

    @Test
    void envia_el_codigo_con_remitente_destinatario_y_vigencia() {
        properties.setHabilitado(true);
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);

        service.enviarCodigo("cliente@fixtech.pe", "123456", 10);

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage mensaje = captor.getValue();
        assertEquals("no-reply@fixtech.pe", mensaje.getFrom());
        assertEquals("cliente@fixtech.pe", mensaje.getTo()[0]);
        assertEquals("Código de recuperación FIXTECH", mensaje.getSubject());
        assertTrue(mensaje.getText().contains("123456"));
        assertTrue(mensaje.getText().contains("10 minutos"));
    }

    @Test
    void convierte_un_fallo_de_smtp_en_servicio_no_disponible() {
        properties.setHabilitado(true);
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        doThrow(new MailSendException("fallo")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThrows(ServiceUnavailableException.class,
                () -> service.enviarCodigo("cliente@fixtech.pe", "123456", 10));
    }
}
