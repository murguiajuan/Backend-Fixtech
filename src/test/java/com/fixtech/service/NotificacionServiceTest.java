package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.dto.NotificacionResponse;
import com.fixtech.dto.NotificacionesResponse;
import com.fixtech.entity.Notificacion;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.NotFoundException;
import com.fixtech.repository.NotificacionRepository;
import com.fixtech.repository.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

    @Mock
    private NotificacionRepository notificacionRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private CorreoNotificacionService correoNotificacionService;

    @InjectMocks
    private NotificacionService notificacionService;

    @Test
    void crea_una_notificacion_y_recorta_los_textos_largos() {
        Usuario usuario = Usuario.builder().id(10L).build();
        when(usuarioRepository.getReferenceById(10L)).thenReturn(usuario);

        notificacionService.crear(
                10L,
                TipoNotificacion.OFERTA_RECIBIDA,
                "Titulo",
                "x".repeat(400),
                "OFERTA",
                5L);

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepository).save(captor.capture());
        Notificacion notificacion = captor.getValue();
        assertEquals(10L, notificacion.getUsuario().getId());
        assertEquals(TipoNotificacion.OFERTA_RECIBIDA, notificacion.getTipo());
        assertEquals(300, notificacion.getMensaje().length());
        assertFalse(notificacion.isLeida());
    }

    @Test
    void lista_notificaciones_y_cuenta_las_no_leidas() {
        Notificacion leida = notificacion(1L, true);
        Notificacion noLeida = notificacion(2L, false);
        when(notificacionRepository.findByUsuarioIdOrderByCreadoEnDesc(10L))
                .thenReturn(List.of(noLeida, leida));
        when(notificacionRepository.countByUsuarioIdAndLeidaFalse(10L))
                .thenReturn(1L);

        NotificacionesResponse response = notificacionService.listar(10L, false);

        assertEquals(2, response.notificaciones().size());
        assertEquals(1L, response.noLeidas());
        assertTrue(response.notificaciones().get(0).titulo().startsWith("Titulo"));
    }

    @Test
    void marca_una_notificacion_como_leida_solo_si_pertenece_al_usuario() {
        Notificacion notificacion = notificacion(1L, false);
        when(notificacionRepository.findByIdAndUsuarioId(1L, 10L))
                .thenReturn(Optional.of(notificacion));

        NotificacionResponse response = notificacionService.marcarLeida(10L, 1L);

        assertTrue(response.leida());
        assertTrue(notificacion.isLeida());
        verify(notificacionRepository).save(notificacion);
    }

    @Test
    void falla_al_marcar_una_notificacion_ajena() {
        when(notificacionRepository.findByIdAndUsuarioId(1L, 99L))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> notificacionService.marcarLeida(99L, 1L));
    }

    @Test
    void marca_todas_las_notificaciones_del_usuario_como_leidas() {
        when(notificacionRepository.marcarTodasLeidas(10L)).thenReturn(3);

        assertEquals(3, notificacionService.marcarTodasLeidas(10L));
    }

    private Notificacion notificacion(Long id, boolean leida) {
        return Notificacion.builder()
                .id(id)
                .usuario(Usuario.builder().id(10L).build())
                .tipo(TipoNotificacion.OFERTA_RECIBIDA)
                .titulo("Titulo de prueba")
                .mensaje("Mensaje")
                .referenciaTipo("OFERTA")
                .referenciaId(5L)
                .leida(leida)
                .creadoEn(LocalDateTime.now())
                .build();
    }
}
