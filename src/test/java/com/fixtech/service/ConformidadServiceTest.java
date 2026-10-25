package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.dto.ServicioResponse;
import com.fixtech.entity.Comision;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.enums.EstadoComision;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BusinessException;
import com.fixtech.mapper.ServicioMapper;
import com.fixtech.repository.ServicioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConformidadServiceTest {

    @Mock
    private ServicioRepository servicioRepository;

    @Mock
    private ServicioAccesoService servicioAccesoService;

    @Mock
    private ServicioEventoService servicioEventoService;

    @Mock
    private ComisionService comisionService;

    @Spy
    private ServicioMapper servicioMapper = new ServicioMapper();

    @InjectMocks
    private ConformidadService conformidadService;

    @Test
    void el_cliente_confirma_el_servicio_y_se_genera_la_comision() {
        Servicio servicio = servicioFinalizado(LocalDateTime.now().plusHours(24));
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);
        Comision comision = comision(servicio);
        when(comisionService.registrarParaServicio(servicio)).thenReturn(comision);

        ServicioResponse response = conformidadService.confirmar(10L, 5L);

        assertEquals(EstadoServicio.CONFIRMADO, response.estado());
        assertEquals(EstadoServicio.CONFIRMADO, servicio.getEstado());
        assertNotNull(servicio.getFechaConfirmacion());
        assertFalse(servicio.isConfirmadoAutomaticamente());
        verify(servicioRepository).save(servicio);
        verify(servicioEventoService).registrarCambio(
                servicio,
                EstadoServicio.FINALIZADO,
                EstadoServicio.CONFIRMADO,
                10L,
                "Conformidad registrada por el cliente");
        verify(servicioEventoService).notificar(
                eq(10L),
                eq(TipoNotificacion.SERVICIO_CONFIRMADO),
                any(),
                any(),
                eq(5L));
        verify(servicioEventoService).notificarContraparte(
                eq(servicio),
                eq(10L),
                eq(TipoNotificacion.SERVICIO_CONFIRMADO),
                any(),
                any());
        verify(servicioEventoService).notificar(
                eq(20L),
                eq(TipoNotificacion.COMISION_GENERADA),
                any(),
                any(),
                eq(5L));
    }

    @Test
    void solo_se_confirma_un_servicio_finalizado() {
        Servicio servicio = ServicioFixture.servicio(
                5L, EstadoServicio.EN_PROCESO, Modalidad.DOMICILIO);
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);

        assertThrows(
                BusinessException.class,
                () -> conformidadService.confirmar(10L, 5L));
    }

    @Test
    void confirma_automaticamente_al_vencer_el_plazo() {
        Servicio servicio = servicioFinalizado(LocalDateTime.now().minusHours(1));
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);
        when(comisionService.registrarParaServicio(servicio))
                .thenReturn(comision(servicio));

        assertTrue(conformidadService.confirmarAutomaticamente(5L));

        assertEquals(EstadoServicio.CONFIRMADO, servicio.getEstado());
        assertTrue(servicio.isConfirmadoAutomaticamente());
        verify(servicioEventoService).registrarCambio(
                servicio,
                EstadoServicio.FINALIZADO,
                EstadoServicio.CONFIRMADO,
                null,
                "Confirmación automática por vencimiento del plazo");
        verify(servicioEventoService, times(3))
                .notificar(any(), any(), any(), any(), eq(5L));
    }

    @Test
    void no_confirma_automaticamente_antes_del_plazo() {
        Servicio servicio = servicioFinalizado(LocalDateTime.now().plusHours(1));
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);

        assertFalse(conformidadService.confirmarAutomaticamente(5L));

        verify(servicioRepository, never()).save(any());
        verify(comisionService, never()).registrarParaServicio(any());
    }

    @Test
    void no_confirma_automaticamente_un_servicio_no_finalizado() {
        Servicio servicio = ServicioFixture.servicio(
                5L, EstadoServicio.EN_PROCESO, Modalidad.DOMICILIO);
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);

        assertFalse(conformidadService.confirmarAutomaticamente(5L));
    }

    @Test
    void confirma_todos_los_servicios_vencidos() {
        Servicio primero = servicioFinalizado(LocalDateTime.now().minusHours(2));
        Servicio segundo = servicioFinalizado(LocalDateTime.now().minusHours(1));
        segundo.setId(6L);
        when(servicioRepository.buscarIdsParaConfirmacionAutomatica(
                eq(EstadoServicio.FINALIZADO), any(LocalDateTime.class)))
                .thenReturn(List.of(5L, 6L));
        when(servicioAccesoService.bloquear(5L)).thenReturn(primero);
        when(servicioAccesoService.bloquear(6L)).thenReturn(segundo);
        when(comisionService.registrarParaServicio(primero))
                .thenReturn(comision(primero));
        when(comisionService.registrarParaServicio(segundo))
                .thenReturn(comision(segundo));

        assertEquals(2, conformidadService.confirmarVencidos());
        assertEquals(EstadoServicio.CONFIRMADO, primero.getEstado());
        assertEquals(EstadoServicio.CONFIRMADO, segundo.getEstado());
        verify(comisionService, times(2)).registrarParaServicio(any());
    }

    private Servicio servicioFinalizado(LocalDateTime fechaLimite) {
        Servicio servicio = ServicioFixture.servicio(
                5L, EstadoServicio.FINALIZADO, Modalidad.DOMICILIO);
        servicio.setFechaLimiteConformidad(fechaLimite);
        servicio.setMontoFinal(new BigDecimal("110.00"));
        return servicio;
    }

    private Comision comision(Servicio servicio) {
        return Comision.builder()
                .servicio(servicio)
                .tecnico(servicio.getTecnico())
                .montoBase(new BigDecimal("110.00"))
                .porcentaje(new BigDecimal("0.0600"))
                .montoComision(new BigDecimal("6.60"))
                .montoNetoTecnico(new BigDecimal("103.40"))
                .estado(EstadoComision.PENDIENTE)
                .fechaLimitePago(LocalDate.now().plusDays(7))
                .build();
    }
}
