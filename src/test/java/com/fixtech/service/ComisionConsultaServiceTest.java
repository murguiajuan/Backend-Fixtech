package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fixtech.dto.ComisionResponse;
import com.fixtech.dto.MiSaldoComisionesResponse;
import com.fixtech.entity.Comision;
import com.fixtech.entity.PagoComision;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.enums.EstadoComision;
import com.fixtech.entity.enums.EstadoPagoComision;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.MetodoPago;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.ParametroClave;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.mapper.ComisionMapper;
import com.fixtech.repository.ComisionRepository;
import com.fixtech.repository.PagoComisionRepository;
import com.fixtech.repository.TecnicoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ComisionConsultaServiceTest {

    @Mock
    private ComisionRepository comisionRepository;

    @Mock
    private PagoComisionRepository pagoComisionRepository;

    @Mock
    private TecnicoRepository tecnicoRepository;

    @Mock
    private ParametroSistemaService parametroSistemaService;

    @Spy
    private ComisionMapper comisionMapper = new ComisionMapper();

    @InjectMocks
    private ComisionConsultaService comisionConsultaService;

    @Test
    void calcula_el_saldo_las_comisiones_vencidas_y_el_proximo_vencimiento() {
        Tecnico tecnico = ServicioFixture.tecnico(2L, 20L);
        tecnico.setBloqueadoPorDeuda(true);
        Comision vencida = ServicioFixture.comision(
                1L, servicio(), "10.00", LocalDate.now().minusDays(1));
        Comision vigente = ServicioFixture.comision(
                2L, servicio(), "20.00", LocalDate.now().plusDays(4));
        when(tecnicoRepository.findByUsuarioId(20L)).thenReturn(Optional.of(tecnico));
        when(comisionRepository.findByTecnicoIdAndEstadoOrderByFechaLimitePagoAscIdAsc(
                2L, EstadoComision.PENDIENTE)).thenReturn(List.of(vencida, vigente));
        when(comisionRepository.sumarMontoPorTecnicoYEstado(
                2L, EstadoComision.PAGADA)).thenReturn(new BigDecimal("40.00"));
        when(parametroSistemaService.obtenerDecimal(
                ParametroClave.LIMITE_DEUDA_COMISION))
                .thenReturn(new BigDecimal("30.00"));
        when(pagoComisionRepository.findByTecnicoIdOrderByCreadoEnDescIdDesc(2L))
                .thenReturn(List.of(pago(tecnico, vencida)));

        MiSaldoComisionesResponse respuesta = comisionConsultaService
                .obtenerMiSaldo(20L);

        assertEquals(new BigDecimal("30.00"), respuesta.saldoPendiente());
        assertEquals(new BigDecimal("40.00"), respuesta.totalPagado());
        assertEquals(new BigDecimal("30.00"), respuesta.limiteDeuda());
        assertTrue(respuesta.bloqueadoPorDeuda());
        assertEquals(1, respuesta.comisionesVencidas());
        assertEquals(LocalDate.now().minusDays(1), respuesta.proximoVencimiento());
        assertEquals(2, respuesta.comisionesPendientes().size());
        assertEquals(1, respuesta.pagos().size());
        assertEquals(1L, respuesta.comisionesPendientes().get(0).id());
    }

    @Test
    void devuelve_saldos_en_cero_cuando_no_hay_comisiones_ni_pagos() {
        Tecnico tecnico = ServicioFixture.tecnico(2L, 20L);
        when(tecnicoRepository.findByUsuarioId(20L)).thenReturn(Optional.of(tecnico));
        when(comisionRepository.findByTecnicoIdAndEstadoOrderByFechaLimitePagoAscIdAsc(
                2L, EstadoComision.PENDIENTE)).thenReturn(List.of());
        when(comisionRepository.sumarMontoPorTecnicoYEstado(
                2L, EstadoComision.PAGADA)).thenReturn(null);
        when(parametroSistemaService.obtenerDecimal(
                ParametroClave.LIMITE_DEUDA_COMISION))
                .thenReturn(new BigDecimal("30.00"));
        when(pagoComisionRepository.findByTecnicoIdOrderByCreadoEnDescIdDesc(2L))
                .thenReturn(List.of());

        MiSaldoComisionesResponse respuesta = comisionConsultaService
                .obtenerMiSaldo(20L);

        assertEquals(new BigDecimal("0.00"), respuesta.saldoPendiente());
        assertEquals(new BigDecimal("0.00"), respuesta.totalPagado());
        assertEquals(0, respuesta.comisionesVencidas());
        assertNull(respuesta.proximoVencimiento());
        assertTrue(respuesta.comisionesPendientes().isEmpty());
        assertTrue(respuesta.pagos().isEmpty());
    }

    @Test
    void lista_las_comisiones_del_tecnico_usando_pendientes_por_defecto() {
        Tecnico tecnico = ServicioFixture.tecnico(2L, 20L);
        Comision pendiente = ServicioFixture.comision(
                1L, servicio(), "6.60", LocalDate.now().plusDays(3));
        Comision pagada = ServicioFixture.comision(
                2L, servicio(), "7.20", LocalDate.now().plusDays(3));
        pagada.setEstado(EstadoComision.PAGADA);
        when(tecnicoRepository.findByUsuarioId(20L)).thenReturn(Optional.of(tecnico));
        when(comisionRepository.findByTecnicoIdAndEstadoOrderByFechaLimitePagoAscIdAsc(
                2L, EstadoComision.PENDIENTE)).thenReturn(List.of(pendiente));
        when(comisionRepository.findByTecnicoIdAndEstadoOrderByFechaLimitePagoAscIdAsc(
                2L, EstadoComision.PAGADA)).thenReturn(List.of(pagada));

        List<ComisionResponse> porDefecto = comisionConsultaService
                .listarMias(20L, null);
        List<ComisionResponse> pagadas = comisionConsultaService
                .listarMias(20L, EstadoComision.PAGADA);

        assertEquals(1, porDefecto.size());
        assertEquals(EstadoComision.PENDIENTE, porDefecto.get(0).estado());
        assertEquals(1, pagadas.size());
        assertEquals(EstadoComision.PAGADA, pagadas.get(0).estado());
    }

    @Test
    void falla_si_el_usuario_no_tiene_perfil_de_tecnico() {
        when(tecnicoRepository.findByUsuarioId(20L)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> comisionConsultaService.obtenerMiSaldo(20L));
        assertThrows(ForbiddenException.class,
                () -> comisionConsultaService.listarMias(20L, null));
    }

    private com.fixtech.entity.Servicio servicio() {
        return ServicioFixture.servicio(
                5L, EstadoServicio.CONFIRMADO, Modalidad.DOMICILIO);
    }

    private PagoComision pago(Tecnico tecnico, Comision comision) {
        PagoComision pago = PagoComision.builder()
                .id(7L)
                .tecnico(tecnico)
                .monto(new BigDecimal("10.00"))
                .metodo(MetodoPago.YAPE)
                .numeroOperacion("OP-001")
                .rutaComprobante("pagos/2/comprobante.png")
                .estado(EstadoPagoComision.PENDIENTE)
                .build();
        pago.setCreadoEn(LocalDateTime.now());
        pago.agregarComision(comision);
        return pago;
    }
}
