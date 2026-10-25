package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.dto.ConstanciaRequest;
import com.fixtech.dto.ConstanciaResponse;
import com.fixtech.entity.Constancia;
import com.fixtech.entity.ConstanciaFoto;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.TipoConstancia;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.BusinessException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.mapper.ServicioMapper;
import com.fixtech.repository.ConstanciaFotoRepository;
import com.fixtech.repository.ConstanciaRepository;
import com.fixtech.repository.ServicioRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class ConstanciaServiceTest {

    @Mock
    private ConstanciaRepository constanciaRepository;

    @Mock
    private ConstanciaFotoRepository constanciaFotoRepository;

    @Mock
    private ServicioRepository servicioRepository;

    @Mock
    private ServicioAccesoService servicioAccesoService;

    @Mock
    private ServicioEventoService servicioEventoService;

    @Mock
    private ArchivoService archivoService;

    @Spy
    private ServicioMapper servicioMapper = new ServicioMapper();

    @InjectMocks
    private ConstanciaService constanciaService;

    @Test
    void registra_la_recepcion_del_equipo_y_actualiza_el_estado() {
        Servicio servicio = servicio(EstadoServicio.PROGRAMADO);
        prepararParticipante(servicio, false, true);
        MultipartFile foto = foto();
        when(archivoService.guardarImagenServicio(5L, "constancias", foto))
                .thenReturn("servicios/5/constancias/foto.jpg");

        ConstanciaResponse response = constanciaService.registrar(
                20L,
                5L,
                new ConstanciaRequest(TipoConstancia.RECEPCION, "Rayón", "Cargador"),
                List.of(foto));

        assertEquals(TipoConstancia.RECEPCION, response.tipo());
        assertEquals("Rayón", response.observaciones());
        assertEquals(1, response.fotoIds().size());
        assertEquals(EstadoServicio.EQUIPO_RECIBIDO, servicio.getEstado());
        ArgumentCaptor<Constancia> captor = ArgumentCaptor.forClass(Constancia.class);
        verify(constanciaRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getFotos().size());
        assertEquals(
                "servicios/5/constancias/foto.jpg",
                captor.getValue().getFotos().get(0).getRutaArchivo());
        verify(servicioEventoService).registrarCambio(
                servicio,
                EstadoServicio.PROGRAMADO,
                EstadoServicio.EQUIPO_RECIBIDO,
                20L,
                "Constancia de recepción registrada");
        verify(servicioEventoService).notificarContraparte(
                eq(servicio),
                eq(20L),
                eq(TipoNotificacion.CONSTANCIA_REGISTRADA),
                any(),
                any());
    }

    @Test
    void exige_al_menos_una_foto() {
        Servicio servicio = servicio(EstadoServicio.PROGRAMADO);
        prepararParticipante(servicio, false, true);

        assertThrows(
                BadRequestException.class,
                () -> constanciaService.registrar(
                        20L,
                        5L,
                        new ConstanciaRequest(TipoConstancia.RECEPCION, null, null),
                        List.of()));
        verify(archivoService, never()).guardarImagenServicio(any(), any(), any());
    }

    @Test
    void exige_como_maximo_cinco_fotos() {
        Servicio servicio = servicio(EstadoServicio.PROGRAMADO);
        prepararParticipante(servicio, false, true);
        List<MultipartFile> fotos = List.of(
                foto(), foto(), foto(), foto(), foto(), foto());

        assertThrows(
                BadRequestException.class,
                () -> constanciaService.registrar(
                        20L,
                        5L,
                        new ConstanciaRequest(TipoConstancia.RECEPCION, null, null),
                        fotos));
    }

    @Test
    void rechaza_constancias_en_modalidad_a_domicilio() {
        Servicio servicio = servicio(EstadoServicio.PROGRAMADO);
        servicio.setModalidad(Modalidad.DOMICILIO);
        prepararParticipante(servicio, false, true);

        assertThrows(
                BusinessException.class,
                () -> constanciaService.registrar(
                        20L,
                        5L,
                        new ConstanciaRequest(TipoConstancia.RECEPCION, null, null),
                        List.of(foto())));
    }

    @Test
    void rechaza_una_constancia_duplicada() {
        Servicio servicio = servicio(EstadoServicio.PROGRAMADO);
        prepararParticipante(servicio, false, true);
        when(constanciaRepository.existsByServicioIdAndTipo(
                5L, TipoConstancia.RECEPCION))
                .thenReturn(true);

        assertThrows(
                BusinessException.class,
                () -> constanciaService.registrar(
                        20L,
                        5L,
                        new ConstanciaRequest(TipoConstancia.RECEPCION, null, null),
                        List.of(foto())));
    }

    @Test
    void rechaza_la_entrega_antes_de_que_el_equipo_este_listo() {
        Servicio servicio = servicio(EstadoServicio.EN_REPARACION);
        prepararParticipante(servicio, false, true);

        assertThrows(
                BusinessException.class,
                () -> constanciaService.registrar(
                        20L,
                        5L,
                        new ConstanciaRequest(TipoConstancia.ENTREGA, null, null),
                        List.of(foto())));
    }

    @Test
    void rechaza_textos_que_superan_los_limites() {
        Servicio servicio = servicio(EstadoServicio.PROGRAMADO);
        prepararParticipante(servicio, false, true);

        assertThrows(
                BadRequestException.class,
                () -> constanciaService.registrar(
                        20L,
                        5L,
                        new ConstanciaRequest(
                                TipoConstancia.RECEPCION, "x".repeat(1001), null),
                        List.of(foto())));
    }

    @Test
    void elimina_la_foto_si_falla_el_registro() {
        Servicio servicio = servicio(EstadoServicio.PROGRAMADO);
        prepararParticipante(servicio, false, true);
        MultipartFile foto = foto();
        when(archivoService.guardarImagenServicio(5L, "constancias", foto))
                .thenReturn("servicios/5/constancias/foto.jpg");
        doThrow(new IllegalStateException("fallo"))
                .when(servicioEventoService)
                .notificarContraparte(any(), any(), any(), any(), any());

        assertThrows(
                IllegalStateException.class,
                () -> constanciaService.registrar(
                        20L,
                        5L,
                        new ConstanciaRequest(TipoConstancia.RECEPCION, null, null),
                        List.of(foto)));

        verify(archivoService).eliminar("servicios/5/constancias/foto.jpg");
    }

    @Test
    void el_cliente_confirma_la_constancia() {
        Servicio servicio = servicio(EstadoServicio.EQUIPO_RECIBIDO);
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);
        Constancia constancia = Constancia.builder()
                .id(7L)
                .servicio(servicio)
                .tipo(TipoConstancia.RECEPCION)
                .confirmadaPorCliente(false)
                .build();
        when(constanciaRepository.buscarDetalle(7L, 5L))
                .thenReturn(Optional.of(constancia));

        ConstanciaResponse response = constanciaService.confirmar(10L, 5L, 7L);

        assertTrue(response.confirmadaPorCliente());
        assertTrue(constancia.isConfirmadaPorCliente());
        verify(constanciaRepository).save(constancia);
        verify(servicioEventoService).notificarContraparte(
                eq(servicio),
                eq(10L),
                eq(TipoNotificacion.CONSTANCIA_CONFIRMADA),
                any(),
                any());
    }

    @Test
    void no_repite_la_notificacion_si_la_constancia_ya_fue_confirmada() {
        Servicio servicio = servicio(EstadoServicio.EQUIPO_RECIBIDO);
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);
        Constancia constancia = Constancia.builder()
                .id(7L)
                .servicio(servicio)
                .tipo(TipoConstancia.RECEPCION)
                .confirmadaPorCliente(true)
                .build();
        when(constanciaRepository.buscarDetalle(7L, 5L))
                .thenReturn(Optional.of(constancia));

        assertTrue(constanciaService.confirmar(10L, 5L, 7L).confirmadaPorCliente());
        verify(constanciaRepository, never()).save(any());
        verify(servicioEventoService, never())
                .notificarContraparte(any(), any(), any(), any(), any());
    }

    @Test
    void falla_si_la_constancia_no_pertenece_al_servicio() {
        Servicio servicio = servicio(EstadoServicio.EQUIPO_RECIBIDO);
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);
        when(constanciaRepository.buscarDetalle(7L, 5L))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> constanciaService.confirmar(10L, 5L, 7L));
    }

    @Test
    void recupera_la_foto_solo_para_participantes() {
        ArchivoService.RecursoImagen recurso = new ArchivoService.RecursoImagen(
                new org.springframework.core.io.ByteArrayResource(new byte[]{1}),
                org.springframework.http.MediaType.IMAGE_JPEG);
        ConstanciaFoto foto = ConstanciaFoto.builder()
                .id(9L)
                .rutaArchivo("servicios/5/constancias/foto.jpg")
                .build();
        when(constanciaFotoRepository
                .findByIdAndConstanciaIdAndConstanciaServicioId(9L, 7L, 5L))
                .thenReturn(Optional.of(foto));
        when(archivoService.cargar("servicios/5/constancias/foto.jpg"))
                .thenReturn(recurso);

        ArchivoService.RecursoImagen encontrado = constanciaService.obtenerFoto(
                10L, 5L, 7L, 9L);

        assertSame(recurso, encontrado);
        verify(servicioAccesoService).obtenerParaParticipante(10L, 5L);
    }

    @Test
    void falla_si_la_foto_no_existe() {
        when(constanciaFotoRepository
                .findByIdAndConstanciaIdAndConstanciaServicioId(9L, 7L, 5L))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> constanciaService.obtenerFoto(10L, 5L, 7L, 9L));
    }

    private void prepararParticipante(
            Servicio servicio, boolean esCliente, boolean esTecnico) {
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);
        when(servicioAccesoService.esCliente(servicio, esCliente ? 10L : 20L))
                .thenReturn(esCliente);
        when(servicioAccesoService.esTecnico(servicio, esCliente ? 10L : 20L))
                .thenReturn(esTecnico);
    }

    private Servicio servicio(EstadoServicio estado) {
        return ServicioFixture.servicio(5L, estado, Modalidad.TALLER);
    }

    private MultipartFile foto() {
        return new MockMultipartFile(
                "fotos", "foto.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }
}
