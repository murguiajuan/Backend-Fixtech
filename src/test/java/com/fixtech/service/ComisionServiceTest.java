package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.entity.Comision;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.enums.EstadoComision;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.ParametroClave;
import com.fixtech.exception.BusinessException;
import com.fixtech.repository.ComisionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ComisionServiceTest {

    @Mock
    private ComisionRepository comisionRepository;

    @Mock
    private ParametroSistemaService parametroSistemaService;

    @Mock
    private DeudaTecnicoService deudaTecnicoService;

    @InjectMocks
    private ComisionService comisionService;

    @Test
    void calcula_la_comision_con_redondeo_y_el_plazo_de_pago() {
        Servicio servicio = servicio();
        servicio.setMontoFinal(new BigDecimal("110.00"));
        when(comisionRepository.findByServicioId(5L)).thenReturn(Optional.empty());
        when(parametroSistemaService.obtenerDecimal(
                ParametroClave.COMISION_PORCENTAJE))
                .thenReturn(new BigDecimal("0.0600"));
        when(parametroSistemaService.obtenerEntero(
                ParametroClave.PLAZO_PAGO_COMISION_DIAS))
                .thenReturn(7);
        when(comisionRepository.save(any(Comision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Comision comision = comisionService.registrarParaServicio(servicio);

        assertEquals(new BigDecimal("110.00"), comision.getMontoBase());
        assertEquals(new BigDecimal("0.0600"), comision.getPorcentaje());
        assertEquals(new BigDecimal("6.60"), comision.getMontoComision());
        assertEquals(new BigDecimal("103.40"), comision.getMontoNetoTecnico());
        assertEquals(EstadoComision.PENDIENTE, comision.getEstado());
        assertEquals(LocalDate.now().plusDays(7), comision.getFechaLimitePago());
        assertEquals(servicio.getTecnico(), comision.getTecnico());
    }

    @Test
    void reutiliza_la_comision_existente_del_servicio() {
        Servicio servicio = servicio();
        Comision existente = Comision.builder().id(99L).servicio(servicio).build();
        when(comisionRepository.findByServicioId(5L))
                .thenReturn(Optional.of(existente));

        Comision comision = comisionService.registrarParaServicio(servicio);

        assertEquals(99L, comision.getId());
        verify(comisionRepository, never()).save(any(Comision.class));
        verify(parametroSistemaService, never())
                .obtenerDecimal(any(ParametroClave.class));
    }

    @Test
    void falla_si_no_existe_un_monto_base_valido() {
        Servicio servicio = servicio();
        servicio.setMontoTotal(null);
        servicio.setMontoFinal(null);
        when(comisionRepository.findByServicioId(5L)).thenReturn(Optional.empty());

        assertThrows(
                BusinessException.class,
                () -> comisionService.registrarParaServicio(servicio));
        verify(comisionRepository, never()).save(any(Comision.class));
    }

    @Test
    void usa_el_monto_total_cuando_no_hay_monto_final() {
        Servicio servicio = servicio();
        when(comisionRepository.findByServicioId(5L)).thenReturn(Optional.empty());
        when(parametroSistemaService.obtenerDecimal(
                ParametroClave.COMISION_PORCENTAJE))
                .thenReturn(new BigDecimal("0.06"));
        when(parametroSistemaService.obtenerEntero(
                ParametroClave.PLAZO_PAGO_COMISION_DIAS))
                .thenReturn(0);
        when(comisionRepository.save(any(Comision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Comision comision = comisionService.registrarParaServicio(servicio);

        assertEquals(new BigDecimal("110.00"), comision.getMontoBase());
        assertEquals(6.60, comision.getMontoComision().doubleValue());
    }

    @Test
    void redondea_hacia_arriba_el_porcentaje_y_la_comision() {
        Servicio servicio = servicio();
        servicio.setMontoFinal(new BigDecimal("99.99"));
        when(comisionRepository.findByServicioId(5L)).thenReturn(Optional.empty());
        when(parametroSistemaService.obtenerDecimal(
                ParametroClave.COMISION_PORCENTAJE))
                .thenReturn(new BigDecimal("0.06005"));
        when(parametroSistemaService.obtenerEntero(
                ParametroClave.PLAZO_PAGO_COMISION_DIAS))
                .thenReturn(7);
        when(comisionRepository.save(any(Comision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Comision comision = comisionService.registrarParaServicio(servicio);

        assertEquals(new BigDecimal("0.0601"), comision.getPorcentaje());
        assertEquals(new BigDecimal("6.01"), comision.getMontoComision());
        assertEquals(new BigDecimal("93.98"), comision.getMontoNetoTecnico());
    }

    private Servicio servicio() {
        return ServicioFixture.servicio(
                5L, EstadoServicio.CONFIRMADO, Modalidad.DOMICILIO);
    }
}
