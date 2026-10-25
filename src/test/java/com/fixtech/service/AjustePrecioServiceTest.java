package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.dto.AjustePrecioResponse;
import com.fixtech.dto.RespuestaAjusteRequest;
import com.fixtech.entity.AjustePrecio;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.enums.CanceladoPor;
import com.fixtech.entity.enums.EstadoAjuste;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.BusinessException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.mapper.ServicioMapper;
import com.fixtech.repository.AjustePrecioRepository;
import com.fixtech.repository.ServicioRepository;
import java.math.BigDecimal;
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
class AjustePrecioServiceTest {

    @Mock
    private AjustePrecioRepository ajustePrecioRepository;

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
    private AjustePrecioService ajustePrecioService;

    @Test
    void propone_un_ajuste_con_evidencia_y_pone_el_servicio_en_espera() {
        Servicio servicio = servicio(EstadoServicio.EN_DIAGNOSTICO);
        MockMultipartFile evidencia = evidencia();
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);
        when(ajustePrecioRepository.existsByServicioIdAndEstado(
                5L, EstadoAjuste.PENDIENTE))
                .thenReturn(false);
        when(archivoService.guardarImagenServicio(5L, "ajustes", evidencia))
                .thenReturn("servicios/5/ajustes/evidencia.jpg");

        AjustePrecioResponse response = ajustePrecioService.proponer(
                20L,
                5L,
                new BigDecimal("150.00"),
                "Cambio de placa",
                evidencia);

        assertEquals(new BigDecimal("110.00"), response.montoAnterior());
        assertEquals(new BigDecimal("150.00"), response.montoNuevo());
        assertEquals(EstadoAjuste.PENDIENTE, response.estado());
        assertTrue(response.tieneEvidencia());
        assertEquals(EstadoServicio.PENDIENTE_AJUSTE, servicio.getEstado());
        verify(ajustePrecioRepository).save(any(AjustePrecio.class));
        verify(servicioRepository).save(servicio);
        verify(servicioEventoService).registrarCambio(
                servicio,
                EstadoServicio.EN_DIAGNOSTICO,
                EstadoServicio.PENDIENTE_AJUSTE,
                20L,
                "Ajuste de precio propuesto de S/ 150.00");
        verify(servicioEventoService).notificarContraparte(
                eq(servicio),
                eq(20L),
                eq(TipoNotificacion.AJUSTE_PROPUESTO),
                any(),
                any());
    }

    @Test
    void propone_un_ajuste_sin_evidencia() {
        Servicio servicio = servicio(EstadoServicio.EN_REPARACION);
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);
        when(ajustePrecioRepository.existsByServicioIdAndEstado(
                5L, EstadoAjuste.PENDIENTE))
                .thenReturn(false);

        AjustePrecioResponse response = ajustePrecioService.proponer(
                20L, 5L, new BigDecimal("130.00"), "Repuesto adicional", null);

        assertFalse(response.tieneEvidencia());
        verify(archivoService, never()).guardarImagenServicio(any(), any(), any());
    }

    @Test
    void rechaza_un_monto_que_no_supera_el_actual() {
        Servicio servicio = servicio(EstadoServicio.EN_DIAGNOSTICO);
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);
        when(ajustePrecioRepository.existsByServicioIdAndEstado(
                5L, EstadoAjuste.PENDIENTE))
                .thenReturn(false);

        assertThrows(
                BadRequestException.class,
                () -> ajustePrecioService.proponer(
                        20L, 5L, new BigDecimal("110.00"), "Justificación", null));
        verify(ajustePrecioRepository, never()).save(any());
    }

    @Test
    void rechaza_proponer_en_un_estado_no_permitido() {
        Servicio servicio = servicio(EstadoServicio.PROGRAMADO);
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);

        assertThrows(
                BusinessException.class,
                () -> ajustePrecioService.proponer(
                        20L, 5L, new BigDecimal("150.00"), "Justificación", null));
    }

    @Test
    void rechaza_un_segundo_ajuste_pendiente() {
        Servicio servicio = servicio(EstadoServicio.EN_DIAGNOSTICO);
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);
        when(ajustePrecioRepository.existsByServicioIdAndEstado(
                5L, EstadoAjuste.PENDIENTE))
                .thenReturn(true);

        assertThrows(
                BusinessException.class,
                () -> ajustePrecioService.proponer(
                        20L, 5L, new BigDecimal("150.00"), "Justificación", null));
    }

    @Test
    void exige_una_justificacion() {
        Servicio servicio = servicio(EstadoServicio.EN_DIAGNOSTICO);
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);
        when(ajustePrecioRepository.existsByServicioIdAndEstado(
                5L, EstadoAjuste.PENDIENTE))
                .thenReturn(false);

        assertThrows(
                BadRequestException.class,
                () -> ajustePrecioService.proponer(
                        20L, 5L, new BigDecimal("150.00"), "  ", null));
    }

    @Test
    void limita_la_justificacion_a_quinientos_caracteres() {
        Servicio servicio = servicio(EstadoServicio.EN_DIAGNOSTICO);
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);
        when(ajustePrecioRepository.existsByServicioIdAndEstado(
                5L, EstadoAjuste.PENDIENTE))
                .thenReturn(false);

        assertThrows(
                BadRequestException.class,
                () -> ajustePrecioService.proponer(
                        20L, 5L, new BigDecimal("150.00"), "x".repeat(501), null));
    }

    @Test
    void el_cliente_aprueba_el_ajuste_y_se_actualiza_el_monto() {
        Servicio servicio = servicio(EstadoServicio.PENDIENTE_AJUSTE);
        AjustePrecio ajuste = ajuste(servicio);
        when(ajustePrecioRepository.findById(1L)).thenReturn(Optional.of(ajuste));
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);
        when(ajustePrecioRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(ajuste));

        AjustePrecioResponse response = ajustePrecioService.responder(
                10L, 1L, new RespuestaAjusteRequest(EstadoAjuste.APROBADO));

        assertEquals(EstadoAjuste.APROBADO, response.estado());
        assertNotNull(response.fechaRespuesta());
        assertEquals(EstadoServicio.EN_DIAGNOSTICO, servicio.getEstado());
        assertEquals(new BigDecimal("150.00"), servicio.getMontoTotal());
        assertEquals(new BigDecimal("140.00"), servicio.getPrecioServicio());
        verify(servicioRepository).save(servicio);
        verify(ajustePrecioRepository).save(ajuste);
        verify(servicioEventoService).notificarContraparte(
                eq(servicio),
                eq(10L),
                eq(TipoNotificacion.AJUSTE_APROBADO),
                any(),
                any());
    }

    @Test
    void el_cliente_rechaza_el_ajuste_y_se_cancela_el_servicio() {
        Servicio servicio = servicio(EstadoServicio.PENDIENTE_AJUSTE);
        AjustePrecio ajuste = ajuste(servicio);
        when(ajustePrecioRepository.findById(1L)).thenReturn(Optional.of(ajuste));
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);
        when(ajustePrecioRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(ajuste));

        AjustePrecioResponse response = ajustePrecioService.responder(
                10L, 1L, new RespuestaAjusteRequest(EstadoAjuste.RECHAZADO));

        assertEquals(EstadoAjuste.RECHAZADO, response.estado());
        assertEquals(EstadoServicio.CANCELADO, servicio.getEstado());
        assertEquals(CanceladoPor.CLIENTE, servicio.getCanceladoPor());
        assertTrue(servicio.getMotivoCancelacion().contains("Cambio de placa"));
        verify(servicioEventoService).notificarContraparte(
                eq(servicio),
                eq(10L),
                eq(TipoNotificacion.AJUSTE_RECHAZADO),
                any(),
                any());
    }

    @Test
    void no_permite_responder_dos_veces_el_mismo_ajuste() {
        Servicio servicio = servicio(EstadoServicio.PENDIENTE_AJUSTE);
        AjustePrecio ajuste = ajuste(servicio);
        ajuste.setEstado(EstadoAjuste.APROBADO);
        when(ajustePrecioRepository.findById(1L)).thenReturn(Optional.of(ajuste));
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);
        when(ajustePrecioRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(ajuste));

        assertThrows(
                BusinessException.class,
                () -> ajustePrecioService.responder(
                        10L, 1L, new RespuestaAjusteRequest(EstadoAjuste.RECHAZADO)));
    }

    @Test
    void exige_aprobar_o_rechazar_el_ajuste() {
        Servicio servicio = servicio(EstadoServicio.PENDIENTE_AJUSTE);
        AjustePrecio ajuste = ajuste(servicio);
        when(ajustePrecioRepository.findById(1L)).thenReturn(Optional.of(ajuste));
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);
        when(ajustePrecioRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(ajuste));

        assertThrows(
                BadRequestException.class,
                () -> ajustePrecioService.responder(
                        10L, 1L, new RespuestaAjusteRequest(EstadoAjuste.PENDIENTE)));
    }

    @Test
    void exige_que_el_servicio_este_pendiente_de_ajuste() {
        Servicio servicio = servicio(EstadoServicio.EN_DIAGNOSTICO);
        AjustePrecio ajuste = ajuste(servicio);
        when(ajustePrecioRepository.findById(1L)).thenReturn(Optional.of(ajuste));
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);
        when(ajustePrecioRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(ajuste));

        assertThrows(
                BusinessException.class,
                () -> ajustePrecioService.responder(
                        10L, 1L, new RespuestaAjusteRequest(EstadoAjuste.APROBADO)));
    }

    @Test
    void falla_si_el_ajuste_no_existe() {
        when(ajustePrecioRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> ajustePrecioService.responder(
                        10L, 1L, new RespuestaAjusteRequest(EstadoAjuste.APROBADO)));
    }

    @Test
    void recupera_la_evidencia_del_ajuste() {
        Servicio servicio = servicio(EstadoServicio.PENDIENTE_AJUSTE);
        AjustePrecio ajuste = ajuste(servicio);
        ArchivoService.RecursoImagen recurso = new ArchivoService.RecursoImagen(
                new ByteArrayResource(new byte[]{1}), MediaType.IMAGE_JPEG);
        when(ajustePrecioRepository.findById(1L)).thenReturn(Optional.of(ajuste));
        when(archivoService.cargar("servicios/5/ajustes/evidencia.jpg"))
                .thenReturn(recurso);

        assertSame(recurso, ajustePrecioService.obtenerEvidencia(20L, 1L));
        verify(servicioAccesoService).validarParticipante(servicio, 20L);
    }

    @Test
    void falla_si_el_ajuste_no_tiene_evidencia() {
        Servicio servicio = servicio(EstadoServicio.PENDIENTE_AJUSTE);
        AjustePrecio ajuste = ajuste(servicio);
        ajuste.setRutaEvidencia(null);
        when(ajustePrecioRepository.findById(1L)).thenReturn(Optional.of(ajuste));

        assertThrows(
                NotFoundException.class,
                () -> ajustePrecioService.obtenerEvidencia(20L, 1L));
    }

    private Servicio servicio(EstadoServicio estado) {
        return ServicioFixture.servicio(5L, estado, Modalidad.DOMICILIO);
    }

    private AjustePrecio ajuste(Servicio servicio) {
        return AjustePrecio.builder()
                .id(1L)
                .servicio(servicio)
                .montoAnterior(new BigDecimal("110.00"))
                .montoNuevo(new BigDecimal("150.00"))
                .justificacion("Cambio de placa")
                .rutaEvidencia("servicios/5/ajustes/evidencia.jpg")
                .estado(EstadoAjuste.PENDIENTE)
                .build();
    }

    private MockMultipartFile evidencia() {
        return new MockMultipartFile(
                "evidencia", "evidencia.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }
}
