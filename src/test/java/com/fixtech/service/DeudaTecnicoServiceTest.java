package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.entity.Comision;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.enums.EstadoComision;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.ParametroClave;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.repository.ComisionRepository;
import com.fixtech.repository.TecnicoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeudaTecnicoServiceTest {

    @Mock
    private ComisionRepository comisionRepository;

    @Mock
    private TecnicoRepository tecnicoRepository;

    @Mock
    private ParametroSistemaService parametroSistemaService;

    @Mock
    private NotificacionService notificacionService;

    @InjectMocks
    private DeudaTecnicoService deudaTecnicoService;

    @Test
    void sincroniza_el_saldo_y_bloquea_al_superar_el_limite() {
        Tecnico tecnico = ServicioFixture.tecnico(2L, 20L);
        when(tecnicoRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(tecnico));
        when(comisionRepository.sumarMontoPorTecnicoYEstado(
                2L, EstadoComision.PENDIENTE))
                .thenReturn(new BigDecimal("31.00"));
        when(comisionRepository.countByTecnicoIdAndEstadoAndFechaLimitePagoBefore(
                eq(2L), eq(EstadoComision.PENDIENTE), any(LocalDate.class)))
                .thenReturn(0L);
        when(parametroSistemaService.obtenerDecimal(
                ParametroClave.LIMITE_DEUDA_COMISION))
                .thenReturn(new BigDecimal("30.00"));

        deudaTecnicoService.sincronizar(tecnico);

        assertEquals(new BigDecimal("31.00"), tecnico.getSaldoComisionPendiente());
        assertTrue(tecnico.isBloqueadoPorDeuda());
        verify(tecnicoRepository).save(tecnico);
        verify(notificacionService).crear(
                eq(20L),
                eq(TipoNotificacion.BLOQUEO_DEUDA),
                any(),
                contains("límite"),
                eq("TECNICO"),
                eq(2L));
    }

    @Test
    void no_bloquea_cuando_el_saldo_es_igual_al_limite() {
        Tecnico tecnico = ServicioFixture.tecnico(2L, 20L);
        when(tecnicoRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(tecnico));
        when(comisionRepository.sumarMontoPorTecnicoYEstado(
                2L, EstadoComision.PENDIENTE))
                .thenReturn(new BigDecimal("30.00"));
        when(comisionRepository.countByTecnicoIdAndEstadoAndFechaLimitePagoBefore(
                eq(2L), eq(EstadoComision.PENDIENTE), any(LocalDate.class)))
                .thenReturn(0L);
        when(parametroSistemaService.obtenerDecimal(
                ParametroClave.LIMITE_DEUDA_COMISION))
                .thenReturn(new BigDecimal("30.00"));

        deudaTecnicoService.sincronizar(tecnico);

        assertEquals(new BigDecimal("30.00"), tecnico.getSaldoComisionPendiente());
        assertFalse(tecnico.isBloqueadoPorDeuda());
        verify(notificacionService, never()).crear(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void bloquea_por_comision_vencida_aunque_el_saldo_sea_bajo() {
        Tecnico tecnico = ServicioFixture.tecnico(2L, 20L);
        when(tecnicoRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(tecnico));
        when(comisionRepository.sumarMontoPorTecnicoYEstado(
                2L, EstadoComision.PENDIENTE))
                .thenReturn(new BigDecimal("5.00"));
        when(comisionRepository.countByTecnicoIdAndEstadoAndFechaLimitePagoBefore(
                eq(2L), eq(EstadoComision.PENDIENTE), any(LocalDate.class)))
                .thenReturn(1L);
        when(parametroSistemaService.obtenerDecimal(
                ParametroClave.LIMITE_DEUDA_COMISION))
                .thenReturn(new BigDecimal("30.00"));

        deudaTecnicoService.sincronizar(tecnico);

        assertTrue(tecnico.isBloqueadoPorDeuda());
        verify(notificacionService).crear(
                eq(20L),
                eq(TipoNotificacion.BLOQUEO_DEUDA),
                any(),
                contains("vencida"),
                eq("TECNICO"),
                eq(2L));
    }

    @Test
    void desbloquea_cuando_el_tecnico_regulariza_su_deuda() {
        Tecnico tecnico = ServicioFixture.tecnico(2L, 20L);
        tecnico.setBloqueadoPorDeuda(true);
        when(tecnicoRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(tecnico));
        when(comisionRepository.sumarMontoPorTecnicoYEstado(
                2L, EstadoComision.PENDIENTE))
                .thenReturn(BigDecimal.ZERO);
        when(comisionRepository.countByTecnicoIdAndEstadoAndFechaLimitePagoBefore(
                eq(2L), eq(EstadoComision.PENDIENTE), any(LocalDate.class)))
                .thenReturn(0L);
        when(parametroSistemaService.obtenerDecimal(
                ParametroClave.LIMITE_DEUDA_COMISION))
                .thenReturn(new BigDecimal("30.00"));

        deudaTecnicoService.sincronizar(tecnico);

        assertFalse(tecnico.isBloqueadoPorDeuda());
        assertEquals(new BigDecimal("0.00"), tecnico.getSaldoComisionPendiente());
        verify(notificacionService).crear(
                eq(20L),
                eq(TipoNotificacion.DESBLOQUEO_DEUDA),
                any(),
                any(),
                eq("TECNICO"),
                eq(2L));
    }

    @Test
    void evalua_a_los_tecnicos_con_comisiones_y_a_los_bloqueados() {
        Tecnico tecnico = ServicioFixture.tecnico(2L, 20L);
        Tecnico bloqueado = ServicioFixture.tecnico(3L, 30L);
        bloqueado.setBloqueadoPorDeuda(true);
        when(comisionRepository.buscarTecnicoIdsPorEstado(EstadoComision.PENDIENTE))
                .thenReturn(List.of(2L));
        when(tecnicoRepository.buscarIdsBloqueadosPorDeuda())
                .thenReturn(List.of(3L));
        when(tecnicoRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(tecnico));
        when(tecnicoRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(bloqueado));
        when(comisionRepository.sumarMontoPorTecnicoYEstado(
                2L, EstadoComision.PENDIENTE)).thenReturn(BigDecimal.ZERO);
        when(comisionRepository.sumarMontoPorTecnicoYEstado(
                3L, EstadoComision.PENDIENTE)).thenReturn(BigDecimal.ZERO);
        when(comisionRepository.countByTecnicoIdAndEstadoAndFechaLimitePagoBefore(
                eq(2L), eq(EstadoComision.PENDIENTE), any(LocalDate.class)))
                .thenReturn(0L);
        when(comisionRepository.countByTecnicoIdAndEstadoAndFechaLimitePagoBefore(
                eq(3L), eq(EstadoComision.PENDIENTE), any(LocalDate.class)))
                .thenReturn(0L);
        when(parametroSistemaService.obtenerDecimal(
                ParametroClave.LIMITE_DEUDA_COMISION))
                .thenReturn(new BigDecimal("30.00"));

        assertEquals(1, deudaTecnicoService.evaluarTodos());
        assertFalse(bloqueado.isBloqueadoPorDeuda());
    }

    @Test
    void lista_deudores_y_bloqueados_sin_comisiones() {
        Servicio servicio = ServicioFixture.servicio(
                5L, EstadoServicio.CONFIRMADO, Modalidad.DOMICILIO);
        Comision vencida = ServicioFixture.comision(
                1L, servicio, "10.00", LocalDate.now().minusDays(1));
        Comision vigente = ServicioFixture.comision(
                2L, servicio, "20.00", LocalDate.now().plusDays(2));
        Tecnico bloqueado = ServicioFixture.tecnico(3L, 30L);
        bloqueado.setBloqueadoPorDeuda(true);
        when(parametroSistemaService.obtenerDecimal(
                ParametroClave.LIMITE_DEUDA_COMISION))
                .thenReturn(new BigDecimal("30.00"));
        when(comisionRepository.findByEstadoOrderByFechaLimitePagoAscIdAsc(
                EstadoComision.PENDIENTE)).thenReturn(List.of(vencida, vigente));
        when(tecnicoRepository.findByBloqueadoPorDeudaTrue())
                .thenReturn(List.of(bloqueado));

        var deudores = deudaTecnicoService.listarDeudores();

        assertEquals(2, deudores.size());
        assertEquals(2L, deudores.get(0).tecnicoId());
        assertEquals(new BigDecimal("30.00"), deudores.get(0).saldoPendiente());
        assertEquals(1, deudores.get(0).comisionesVencidas());
        assertEquals(2, deudores.get(0).comisionesPendientes());
        assertEquals(LocalDate.now().minusDays(1), deudores.get(0).proximoVencimiento());
        assertEquals(3L, deudores.get(1).tecnicoId());
        assertTrue(deudores.get(1).bloqueadoPorDeuda());
        assertEquals(new BigDecimal("0.00"), deudores.get(1).saldoPendiente());
    }
}
