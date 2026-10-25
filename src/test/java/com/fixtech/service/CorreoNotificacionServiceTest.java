package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.config.CorreoProperties;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.repository.UsuarioRepository;
import java.util.Optional;
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
class CorreoNotificacionServiceTest {

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private UsuarioRepository usuarioRepository;

    private CorreoProperties properties;
    private CorreoNotificacionService correoNotificacionService;

    @BeforeEach
    void configurar() {
        properties = new CorreoProperties();
        properties.setRemitente("no-reply@fixtech.pe");
        correoNotificacionService = new CorreoNotificacionService(
                mailSenderProvider, properties, usuarioRepository);
    }

    @Test
    void no_envia_correo_si_esta_deshabilitado() {
        correoNotificacionService.enviar(
                10L, TipoNotificacion.SERVICIO_CONFIRMADO, "Titulo", "Mensaje");

        verify(usuarioRepository, never()).findById(any());
        verify(mailSenderProvider, never()).getIfAvailable();
    }

    @Test
    void no_envia_correo_si_no_hay_smtp_configurado() {
        properties.setHabilitado(true);
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);

        assertDoesNotThrow(() -> correoNotificacionService.enviar(
                10L, TipoNotificacion.SERVICIO_CONFIRMADO, "Titulo", "Mensaje"));

        verify(usuarioRepository, never()).findById(any());
    }

    @Test
    void envia_el_correo_al_usuario() {
        properties.setHabilitado(true);
        Usuario usuario = Usuario.builder()
                .id(10L)
                .correo("cliente@fixtech.pe")
                .build();
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));

        correoNotificacionService.enviar(
                10L,
                TipoNotificacion.SERVICIO_CONFIRMADO,
                "Servicio confirmado",
                "Su servicio fue confirmado");

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage correo = captor.getValue();
        assertEquals("no-reply@fixtech.pe", correo.getFrom());
        assertEquals("cliente@fixtech.pe", correo.getTo()[0]);
        assertEquals("Servicio confirmado", correo.getSubject());
        assertEquals(
                "Su servicio fue confirmado\n\nFIXTECH", correo.getText());
    }

    @Test
    void no_envia_correo_si_el_usuario_no_existe() {
        properties.setHabilitado(true);
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.empty());

        correoNotificacionService.enviar(
                10L, TipoNotificacion.SERVICIO_CONFIRMADO, "Titulo", "Mensaje");

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void no_propaga_errores_del_servidor_de_correo() {
        properties.setHabilitado(true);
        Usuario usuario = Usuario.builder()
                .id(10L)
                .correo("cliente@fixtech.pe")
                .build();
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));
        org.mockito.Mockito.doThrow(new MailSendException("fallo"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> correoNotificacionService.enviar(
                10L, TipoNotificacion.SERVICIO_CONFIRMADO, "Titulo", "Mensaje"));
    }
}
