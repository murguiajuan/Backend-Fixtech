package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.entity.HistorialEstadoServicio;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.repository.HistorialEstadoServicioRepository;
import com.fixtech.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServicioEventoServiceTest {

    @Mock
    private HistorialEstadoServicioRepository historialRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private NotificacionService notificacionService;

    @InjectMocks
    private ServicioEventoService servicioEventoService;

    @Test
    void registra_un_cambio_con_usuario_y_recorta_el_comentario() {
        Servicio servicio = servicio();
        Usuario usuario = Usuario.builder().id(10L).build();
        when(usuarioRepository.getReferenceById(10L)).thenReturn(usuario);

        servicioEventoService.registrarCambio(
                servicio,
                EstadoServicio.PROGRAMADO,
                EstadoServicio.TECNICO_EN_CAMINO,
                10L,
                "x".repeat(400));

        ArgumentCaptor<HistorialEstadoServicio> captor =
                ArgumentCaptor.forClass(HistorialEstadoServicio.class);
        verify(historialRepository).save(captor.capture());
        HistorialEstadoServicio historial = captor.getValue();
        assertEquals(EstadoServicio.PROGRAMADO, historial.getEstadoAnterior());
        assertEquals(EstadoServicio.TECNICO_EN_CAMINO, historial.getEstadoNuevo());
        assertEquals(10L, historial.getCambiadoPor().getId());
        assertEquals(300, historial.getComentario().length());
    }

    @Test
    void registra_un_cambio_automatico_sin_usuario() {
        Servicio servicio = servicio();

        servicioEventoService.registrarCambio(
                servicio,
                EstadoServicio.FINALIZADO,
                EstadoServicio.CONFIRMADO,
                null,
                "   ");

        ArgumentCaptor<HistorialEstadoServicio> captor =
                ArgumentCaptor.forClass(HistorialEstadoServicio.class);
        verify(historialRepository).save(captor.capture());
        assertNull(captor.getValue().getCambiadoPor());
        assertNull(captor.getValue().getComentario());
    }

    @Test
    void notifica_con_y_sin_correo() {
        servicioEventoService.notificar(
                10L,
                TipoNotificacion.SERVICIO_CONFIRMADO,
                "Titulo",
                "Mensaje",
                5L);
        servicioEventoService.notificarSinCorreo(
                10L,
                TipoNotificacion.MENSAJE_NUEVO,
                "Titulo",
                "Mensaje",
                5L);

        verify(notificacionService).crear(
                10L,
                TipoNotificacion.SERVICIO_CONFIRMADO,
                "Titulo",
                "Mensaje",
                "SERVICIO",
                5L);
        verify(notificacionService).crearSinCorreo(
                10L,
                TipoNotificacion.MENSAJE_NUEVO,
                "Titulo",
                "Mensaje",
                "SERVICIO",
                5L);
    }

    @Test
    void notifica_a_la_contraparte_segun_el_actor() {
        Servicio servicio = servicio();

        servicioEventoService.notificarContraparte(
                servicio,
                10L,
                TipoNotificacion.AVANCE_REGISTRADO,
                "Titulo",
                "Mensaje");
        verify(notificacionService).crear(
                eq(20L),
                eq(TipoNotificacion.AVANCE_REGISTRADO),
                eq("Titulo"),
                eq("Mensaje"),
                eq("SERVICIO"),
                eq(5L));

        servicioEventoService.notificarContraparte(
                servicio,
                20L,
                TipoNotificacion.AVANCE_REGISTRADO,
                "Titulo",
                "Mensaje");
        verify(notificacionService).crear(
                eq(10L),
                eq(TipoNotificacion.AVANCE_REGISTRADO),
                eq("Titulo"),
                eq("Mensaje"),
                eq("SERVICIO"),
                eq(5L));
    }

    private Servicio servicio() {
        return ServicioFixture.servicio(
                5L, EstadoServicio.PROGRAMADO, Modalidad.DOMICILIO);
    }
}
