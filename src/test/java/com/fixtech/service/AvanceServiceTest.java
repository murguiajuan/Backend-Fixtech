package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.dto.AvanceRequest;
import com.fixtech.dto.AvanceResponse;
import com.fixtech.entity.AvanceServicio;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.TipoAvance;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.BusinessException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.mapper.ServicioMapper;
import com.fixtech.repository.AvanceServicioRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class AvanceServiceTest {

    @Mock
    private AvanceServicioRepository avanceRepository;

    @Mock
    private ServicioAccesoService servicioAccesoService;

    @Mock
    private ServicioEventoService servicioEventoService;

    @Mock
    private ArchivoService archivoService;

    @Spy
    private ServicioMapper servicioMapper = new ServicioMapper();

    @InjectMocks
    private AvanceService avanceService;

    @Test
    void registra_un_diagnostico_con_foto() {
        Servicio servicio = servicio(EstadoServicio.EN_DIAGNOSTICO);
        MockMultipartFile foto = foto();
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);
        when(archivoService.guardarImagenServicio(5L, "avances", foto))
                .thenReturn("servicios/5/avances/diagnostico.jpg");

        AvanceResponse response = avanceService.registrar(
                20L,
                5L,
                new AvanceRequest(TipoAvance.DIAGNOSTICO, "Fuente dañada"),
                foto);

        assertEquals(TipoAvance.DIAGNOSTICO, response.tipo());
        assertEquals("Fuente dañada", response.descripcion());
        assertTrue(response.tieneFoto());
        verify(avanceRepository).save(any(AvanceServicio.class));
        verify(servicioEventoService).notificarContraparte(
                eq(servicio),
                eq(20L),
                eq(TipoNotificacion.AVANCE_REGISTRADO),
                eq("Diagnóstico registrado"),
                any());
    }

    @Test
    void registra_un_avance_sin_foto() {
        Servicio servicio = servicio(EstadoServicio.EN_REPARACION);
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);

        AvanceResponse response = avanceService.registrar(
                20L,
                5L,
                new AvanceRequest(TipoAvance.AVANCE, "Se cambió el componente"),
                null);

        assertFalse(response.tieneFoto());
        verify(avanceRepository).save(any(AvanceServicio.class));
        verify(servicioEventoService).notificarContraparte(
                eq(servicio),
                eq(20L),
                eq(TipoNotificacion.AVANCE_REGISTRADO),
                eq("Avance registrado"),
                any());
    }

    @Test
    void rechaza_avances_en_un_estado_no_permitido() {
        Servicio servicio = servicio(EstadoServicio.PROGRAMADO);
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);

        assertThrows(
                BusinessException.class,
                () -> avanceService.registrar(
                        20L,
                        5L,
                        new AvanceRequest(TipoAvance.AVANCE, "Texto"),
                        null));
    }

    @Test
    void el_diagnostico_solo_se_registra_en_estado_de_diagnostico() {
        Servicio servicio = servicio(EstadoServicio.EN_REPARACION);
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);

        assertThrows(
                BusinessException.class,
                () -> avanceService.registrar(
                        20L,
                        5L,
                        new AvanceRequest(TipoAvance.DIAGNOSTICO, "Texto"),
                        null));
    }

    @Test
    void exige_una_descripcion_no_vacia() {
        Servicio servicio = servicio(EstadoServicio.EN_DIAGNOSTICO);
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);

        assertThrows(
                BadRequestException.class,
                () -> avanceService.registrar(
                        20L,
                        5L,
                        new AvanceRequest(TipoAvance.AVANCE, "  "),
                        null));
    }

    @Test
    void limita_la_descripcion_a_mil_caracteres() {
        Servicio servicio = servicio(EstadoServicio.EN_DIAGNOSTICO);
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);

        assertThrows(
                BadRequestException.class,
                () -> avanceService.registrar(
                        20L,
                        5L,
                        new AvanceRequest(TipoAvance.AVANCE, "x".repeat(1001)),
                        null));
    }

    @Test
    void elimina_la_foto_si_falla_el_registro() {
        Servicio servicio = servicio(EstadoServicio.EN_DIAGNOSTICO);
        MockMultipartFile foto = foto();
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);
        when(archivoService.guardarImagenServicio(5L, "avances", foto))
                .thenReturn("servicios/5/avances/foto.jpg");
        when(avanceRepository.save(any(AvanceServicio.class)))
                .thenThrow(new IllegalStateException("fallo"));

        assertThrows(
                IllegalStateException.class,
                () -> avanceService.registrar(
                        20L,
                        5L,
                        new AvanceRequest(TipoAvance.AVANCE, "Texto"),
                        foto));

        verify(archivoService).eliminar("servicios/5/avances/foto.jpg");
    }

    @Test
    void lista_los_avances_del_servicio() {
        Servicio servicio = servicio(EstadoServicio.EN_REPARACION);
        AvanceServicio avance = AvanceServicio.builder()
                .id(8L)
                .servicio(servicio)
                .tipo(TipoAvance.AVANCE)
                .descripcion("Texto")
                .creadoEn(LocalDateTime.now())
                .build();
        when(avanceRepository.findByServicioIdOrderByCreadoEnAscIdAsc(5L))
                .thenReturn(List.of(avance));

        List<AvanceResponse> respuesta = avanceService.listar(10L, 5L);

        assertEquals(1, respuesta.size());
        assertEquals(8L, respuesta.get(0).id());
        verify(servicioAccesoService).obtenerParaParticipante(10L, 5L);
    }

    @Test
    void recupera_la_foto_de_un_avance() {
        Servicio servicio = servicio(EstadoServicio.EN_REPARACION);
        ArchivoService.RecursoImagen recurso = new ArchivoService.RecursoImagen(
                new ByteArrayResource(new byte[]{1}), MediaType.IMAGE_JPEG);
        AvanceServicio avance = AvanceServicio.builder()
                .id(8L)
                .servicio(servicio)
                .tipo(TipoAvance.AVANCE)
                .descripcion("Texto")
                .rutaFoto("servicios/5/avances/foto.jpg")
                .build();
        when(avanceRepository.findByIdAndServicioId(8L, 5L))
                .thenReturn(Optional.of(avance));
        when(archivoService.cargar("servicios/5/avances/foto.jpg"))
                .thenReturn(recurso);

        assertSame(recurso, avanceService.obtenerFoto(10L, 5L, 8L));
    }

    @Test
    void falla_si_el_avance_no_tiene_foto() {
        Servicio servicio = servicio(EstadoServicio.EN_REPARACION);
        AvanceServicio avance = AvanceServicio.builder()
                .id(8L)
                .servicio(servicio)
                .tipo(TipoAvance.AVANCE)
                .descripcion("Texto")
                .build();
        when(avanceRepository.findByIdAndServicioId(8L, 5L))
                .thenReturn(Optional.of(avance));

        assertThrows(
                NotFoundException.class,
                () -> avanceService.obtenerFoto(10L, 5L, 8L));
    }

    @Test
    void falla_si_el_avance_no_existe() {
        when(avanceRepository.findByIdAndServicioId(8L, 5L))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> avanceService.obtenerFoto(10L, 5L, 8L));
    }

    private Servicio servicio(EstadoServicio estado) {
        return ServicioFixture.servicio(5L, estado, Modalidad.TALLER);
    }

    private MockMultipartFile foto() {
        return new MockMultipartFile(
                "foto", "foto.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }
}
