package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.dto.MensajeRequest;
import com.fixtech.dto.MensajeResponse;
import com.fixtech.entity.Mensaje;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BadRequestException;
import com.fixtech.mapper.ServicioMapper;
import com.fixtech.repository.MensajeRepository;
import com.fixtech.repository.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MensajeServiceTest {

    @Mock
    private MensajeRepository mensajeRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ServicioAccesoService servicioAccesoService;

    @Mock
    private ServicioEventoService servicioEventoService;

    @Spy
    private ServicioMapper servicioMapper = new ServicioMapper();

    @InjectMocks
    private MensajeService mensajeService;

    @Test
    void envia_un_mensaje_y_notifica_a_la_contraparte() {
        Servicio servicio = servicio();
        Usuario emisor = Usuario.builder().id(10L).build();
        when(servicioAccesoService.obtenerParaParticipante(10L, 5L))
                .thenReturn(servicio);
        when(usuarioRepository.getReferenceById(10L)).thenReturn(emisor);

        MensajeResponse response = mensajeService.enviar(
                10L, 5L, new MensajeRequest("  ¿Cuándo llega?  "));

        assertEquals("¿Cuándo llega?", response.contenido());
        assertEquals(10L, response.emisorId());
        assertEquals("Cliente Prueba", response.emisorNombre());
        assertTrue(response.mio());
        verify(mensajeRepository).save(any(Mensaje.class));
        verify(servicioEventoService).notificarContraparte(
                eq(servicio),
                eq(10L),
                eq(TipoNotificacion.MENSAJE_NUEVO),
                any(),
                any());
    }

    @Test
    void rechaza_un_mensaje_vacio() {
        Servicio servicio = servicio();
        when(servicioAccesoService.obtenerParaParticipante(10L, 5L))
                .thenReturn(servicio);

        assertThrows(
                BadRequestException.class,
                () -> mensajeService.enviar(10L, 5L, new MensajeRequest("   ")));
    }

    @Test
    void limita_el_mensaje_a_mil_caracteres() {
        Servicio servicio = servicio();
        when(servicioAccesoService.obtenerParaParticipante(10L, 5L))
                .thenReturn(servicio);

        assertThrows(
                BadRequestException.class,
                () -> mensajeService.enviar(
                        10L, 5L, new MensajeRequest("x".repeat(1001))));
    }

    @Test
    void lista_los_mensajes_y_marca_los_recibidos_como_leidos() {
        Servicio servicio = servicio();
        Mensaje mensaje = Mensaje.builder()
                .id(9L)
                .servicio(servicio)
                .emisor(servicio.getTecnico().getUsuario())
                .contenido("Estoy en camino")
                .leido(false)
                .creadoEn(LocalDateTime.now())
                .build();
        when(servicioAccesoService.obtenerParaParticipante(10L, 5L))
                .thenReturn(servicio);
        when(mensajeRepository.buscarPorServicio(5L))
                .thenReturn(List.of(mensaje));

        List<MensajeResponse> respuesta = mensajeService.listar(10L, 5L);

        assertEquals(1, respuesta.size());
        assertFalse(respuesta.get(0).mio());
        assertEquals("Tecnico Prueba", respuesta.get(0).emisorNombre());
        verify(mensajeRepository).marcarLeidos(5L, 10L);
    }

    @Test
    void guarda_el_mensaje_con_el_emisor_del_token() {
        Servicio servicio = servicio();
        Usuario emisor = Usuario.builder().id(20L).build();
        when(servicioAccesoService.obtenerParaParticipante(20L, 5L))
                .thenReturn(servicio);
        when(usuarioRepository.getReferenceById(20L)).thenReturn(emisor);

        mensajeService.enviar(20L, 5L, new MensajeRequest("Voy llegando"));

        ArgumentCaptor<Mensaje> captor = ArgumentCaptor.forClass(Mensaje.class);
        verify(mensajeRepository).save(captor.capture());
        assertEquals(emisor, captor.getValue().getEmisor());
        assertFalse(captor.getValue().isLeido());
    }

    private Servicio servicio() {
        return ServicioFixture.servicio(
                5L, EstadoServicio.EN_PROCESO, Modalidad.DOMICILIO);
    }
}
