package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.dto.CambioEstadoServicioRequest;
import com.fixtech.dto.FinalizarServicioRequest;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.enums.CanceladoPor;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.MetodoPago;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.ParametroClave;
import com.fixtech.entity.enums.TipoConstancia;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.BusinessException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.mapper.ServicioMapper;
import com.fixtech.repository.ConstanciaRepository;
import com.fixtech.repository.ServicioRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServicioEjecucionServiceTest {

    @Mock
    private ServicioRepository servicioRepository;

    @Mock
    private ConstanciaRepository constanciaRepository;

    @Mock
    private ServicioAccesoService servicioAccesoService;

    @Mock
    private ServicioEventoService servicioEventoService;

    @Mock
    private ParametroSistemaService parametroSistemaService;

    @Spy
    private ServicioMapper servicioMapper = new ServicioMapper();

    @InjectMocks
    private ServicioEjecucionService servicioEjecucionService;

    @Test
    void tecnico_pone_el_servicio_en_camino() {
        Servicio servicio = servicio(EstadoServicio.PROGRAMADO, Modalidad.DOMICILIO);
        prepararAcceso(servicio, false, true);

        servicioEjecucionService.cambiarEstado(
                20L,
                5L,
                new CambioEstadoServicioRequest(
                        EstadoServicio.TECNICO_EN_CAMINO, null, "En camino", null));

        assertEquals(EstadoServicio.TECNICO_EN_CAMINO, servicio.getEstado());
        verify(servicioRepository).save(servicio);
        verify(servicioEventoService).registrarCambio(
                servicio,
                EstadoServicio.PROGRAMADO,
                EstadoServicio.TECNICO_EN_CAMINO,
                20L,
                "En camino");
        verify(servicioEventoService).notificarContraparte(
                eq(servicio),
                eq(20L),
                eq(TipoNotificacion.SERVICIO_EN_CAMINO),
                anyString(),
                anyString());
    }

    @Test
    void valida_el_pin_y_marca_el_inicio_del_servicio() {
        Servicio servicio = servicio(EstadoServicio.TECNICO_EN_CAMINO, Modalidad.DOMICILIO);
        prepararAcceso(servicio, false, true);

        servicioEjecucionService.cambiarEstado(
                20L,
                5L,
                new CambioEstadoServicioRequest(EstadoServicio.EN_PROCESO, "1234", null, null));

        assertEquals(EstadoServicio.EN_PROCESO, servicio.getEstado());
        assertTrue(servicio.isPinValidado());
        assertNotNull(servicio.getFechaInicio());
        verify(servicioEventoService).notificarContraparte(
                eq(servicio),
                eq(20L),
                eq(TipoNotificacion.SERVICIO_INICIADO),
                anyString(),
                anyString());
    }

    @Test
    void rechaza_un_pin_incorrecto_sin_cambiar_el_estado() {
        Servicio servicio = servicio(EstadoServicio.TECNICO_EN_CAMINO, Modalidad.DOMICILIO);
        prepararAcceso(servicio, false, true);

        assertThrows(
                BadRequestException.class,
                () -> servicioEjecucionService.cambiarEstado(
                        20L,
                        5L,
                        new CambioEstadoServicioRequest(
                                EstadoServicio.EN_PROCESO, "9999", null, null)));

        assertEquals(EstadoServicio.TECNICO_EN_CAMINO, servicio.getEstado());
        verify(servicioRepository, never()).save(any());
    }

    @Test
    void el_cliente_no_puede_cambiar_el_estado_del_tecnico() {
        Servicio servicio = servicio(EstadoServicio.PROGRAMADO, Modalidad.DOMICILIO);
        prepararAcceso(servicio, true, false);

        assertThrows(
                ForbiddenException.class,
                () -> servicioEjecucionService.cambiarEstado(
                        10L,
                        5L,
                        new CambioEstadoServicioRequest(
                                EstadoServicio.TECNICO_EN_CAMINO, null, null, null)));

        verify(servicioRepository, never()).save(any());
    }

    @Test
    void el_cliente_puede_cancelar_un_servicio_activo() {
        Servicio servicio = servicio(EstadoServicio.EN_PROCESO, Modalidad.DOMICILIO);
        prepararAcceso(servicio, true, false);

        servicioEjecucionService.cambiarEstado(
                10L,
                5L,
                new CambioEstadoServicioRequest(
                        EstadoServicio.CANCELADO, null, null, "Ya no lo necesito"));

        assertEquals(EstadoServicio.CANCELADO, servicio.getEstado());
        assertEquals(CanceladoPor.CLIENTE, servicio.getCanceladoPor());
        assertEquals("Ya no lo necesito", servicio.getMotivoCancelacion());
        verify(servicioEventoService).notificarContraparte(
                eq(servicio),
                eq(10L),
                eq(TipoNotificacion.SERVICIO_CANCELADO),
                anyString(),
                anyString());
    }

    @Test
    void no_permite_cancelar_un_servicio_terminal() {
        Servicio servicio = servicio(EstadoServicio.CONFIRMADO, Modalidad.DOMICILIO);
        prepararAcceso(servicio, true, false);

        assertThrows(
                BusinessException.class,
                () -> servicioEjecucionService.cambiarEstado(
                        10L,
                        5L,
                        new CambioEstadoServicioRequest(
                                EstadoServicio.CANCELADO, null, null, "Motivo")));
    }

    @Test
    void finaliza_un_servicio_a_domicilio_con_el_monto_exacto() {
        Servicio servicio = servicio(EstadoServicio.EN_PROCESO, Modalidad.DOMICILIO);
        servicio.setPinValidado(true);
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);
        when(parametroSistemaService.obtenerEntero(
                ParametroClave.CONFIRMACION_AUTOMATICA_HORAS))
                .thenReturn(48);

        servicioEjecucionService.finalizar(
                20L,
                5L,
                new FinalizarServicioRequest(new BigDecimal("110.00"), MetodoPago.EFECTIVO));

        assertEquals(EstadoServicio.FINALIZADO, servicio.getEstado());
        assertEquals(new BigDecimal("110.00"), servicio.getMontoFinal());
        assertEquals(MetodoPago.EFECTIVO, servicio.getMetodoPago());
        assertNotNull(servicio.getFechaFinalizacion());
        assertNotNull(servicio.getFechaLimiteConformidad());
        verify(servicioEventoService).notificarContraparte(
                eq(servicio),
                eq(20L),
                eq(TipoNotificacion.SERVICIO_FINALIZADO),
                anyString(),
                anyString());
    }

    @Test
    void rechaza_un_monto_final_distinto_al_aprobado() {
        Servicio servicio = servicio(EstadoServicio.EN_PROCESO, Modalidad.DOMICILIO);
        servicio.setPinValidado(true);
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);

        assertThrows(
                BadRequestException.class,
                () -> servicioEjecucionService.finalizar(
                        20L,
                        5L,
                        new FinalizarServicioRequest(
                                new BigDecimal("100.00"), MetodoPago.YAPE)));

        assertEquals(EstadoServicio.EN_PROCESO, servicio.getEstado());
        verify(servicioRepository, never()).save(any());
    }

    @Test
    void el_servicio_en_taller_exige_constancia_de_entrega() {
        Servicio servicio = servicio(EstadoServicio.LISTO_PARA_RECOGER, Modalidad.TALLER);
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);
        when(constanciaRepository.existsByServicioIdAndTipo(
                5L, TipoConstancia.ENTREGA))
                .thenReturn(false);

        assertThrows(
                BusinessException.class,
                () -> servicioEjecucionService.finalizar(
                        20L,
                        5L,
                        new FinalizarServicioRequest(
                                new BigDecimal("110.00"), MetodoPago.PLIN)));
    }

    @Test
    void finaliza_un_servicio_en_taller_con_constancia_de_entrega() {
        Servicio servicio = servicio(EstadoServicio.LISTO_PARA_RECOGER, Modalidad.TALLER);
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);
        when(constanciaRepository.existsByServicioIdAndTipo(
                5L, TipoConstancia.ENTREGA))
                .thenReturn(true);
        when(parametroSistemaService.obtenerEntero(
                ParametroClave.CONFIRMACION_AUTOMATICA_HORAS))
                .thenReturn(48);

        servicioEjecucionService.finalizar(
                20L,
                5L,
                new FinalizarServicioRequest(
                        new BigDecimal("110.00"), MetodoPago.TRANSFERENCIA));

        assertEquals(EstadoServicio.FINALIZADO, servicio.getEstado());
        assertEquals(MetodoPago.TRANSFERENCIA, servicio.getMetodoPago());
    }

    @Test
    void rechaza_una_transicion_no_permitida() {
        Servicio servicio = servicio(EstadoServicio.PROGRAMADO, Modalidad.DOMICILIO);
        prepararAcceso(servicio, false, true);

        assertThrows(
                BusinessException.class,
                () -> servicioEjecucionService.cambiarEstado(
                        20L,
                        5L,
                        new CambioEstadoServicioRequest(
                                EstadoServicio.EN_PROCESO, "1234", null, null)));
    }

    @Test
    void rechaza_cambiar_al_mismo_estado() {
        Servicio servicio = servicio(EstadoServicio.PROGRAMADO, Modalidad.DOMICILIO);
        prepararAcceso(servicio, false, true);

        assertThrows(
                BusinessException.class,
                () -> servicioEjecucionService.cambiarEstado(
                        20L,
                        5L,
                        new CambioEstadoServicioRequest(
                                EstadoServicio.PROGRAMADO, null, null, null)));
    }

    private void prepararAcceso(
            Servicio servicio, boolean esCliente, boolean esTecnico) {
        when(servicioAccesoService.bloquear(5L)).thenReturn(servicio);
        when(servicioAccesoService.esCliente(servicio, esCliente ? 10L : 20L))
                .thenReturn(esCliente);
        when(servicioAccesoService.esTecnico(servicio, esCliente ? 10L : 20L))
                .thenReturn(esTecnico);
    }

    private Servicio servicio(EstadoServicio estado, Modalidad modalidad) {
        return ServicioFixture.servicio(5L, estado, modalidad);
    }
}
