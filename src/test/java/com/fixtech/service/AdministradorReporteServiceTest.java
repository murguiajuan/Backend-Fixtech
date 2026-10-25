package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.dto.ReporteResponse;
import com.fixtech.entity.Administrador;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.EstadoComision;
import com.fixtech.entity.enums.EstadoReclamo;
import com.fixtech.entity.enums.EstadoResena;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.Rol;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.repository.AdministradorRepository;
import com.fixtech.repository.ComisionRepository;
import com.fixtech.repository.ReclamoRepository;
import com.fixtech.repository.ResenaRepository;
import com.fixtech.repository.ServicioRepository;
import com.fixtech.repository.SolicitudRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdministradorReporteServiceTest {

    @Mock
    private AdministradorRepository administradorRepository;

    @Mock
    private SolicitudRepository solicitudRepository;

    @Mock
    private ServicioRepository servicioRepository;

    @Mock
    private ComisionRepository comisionRepository;

    @Mock
    private ResenaRepository resenaRepository;

    @Mock
    private ReclamoRepository reclamoRepository;

    @InjectMocks
    private AdministradorReporteService administradorReporteService;

    private final LocalDate desde = LocalDate.of(2026, 10, 1);
    private final LocalDate hasta = LocalDate.of(2026, 10, 25);

    @Test
    void genera_el_reporte_del_periodo() {
        conAdministrador();
        when(solicitudRepository
                .countByCreadoEnGreaterThanEqualAndCreadoEnLessThan(any(), any()))
                .thenReturn(15L);
        when(solicitudRepository.contarSinOfertasEntre(
                any(), any(), anyList(), anyList())).thenReturn(2L);
        when(servicioRepository
                .countByCreadoEnGreaterThanEqualAndCreadoEnLessThan(any(), any()))
                .thenReturn(9L);
        when(servicioRepository.contarPorEstadoEntre(any(), any())).thenReturn(List.of(
                new Object[]{EstadoServicio.CONFIRMADO, 4L},
                new Object[]{EstadoServicio.CANCELADO, 1L}));
        when(comisionRepository.sumarVolumenEntre(
                any(), any(), eq(EstadoComision.ANULADA)))
                .thenReturn(new BigDecimal("500.00"));
        when(comisionRepository.sumarComisionEntre(
                any(), any(), eq(EstadoComision.ANULADA)))
                .thenReturn(new BigDecimal("30.00"));
        when(comisionRepository.ingresosPorMes(any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{"2026-10", 4L,
                    new BigDecimal("200.00"), new BigDecimal("12.00")}));
        when(servicioRepository.tecnicosMasActivos(
                any(), any(), eq(EstadoServicio.CANCELADO))).thenReturn(List.<Object[]>of(
                        new Object[]{2L, "Ana", "Uno", new BigDecimal("4.50"), 9L}));
        when(resenaRepository.promedioEntre(
                eq(EstadoResena.VISIBLE), any(), any())).thenReturn(4.25);
        when(resenaRepository.contarEntre(
                eq(EstadoResena.VISIBLE), any(), any())).thenReturn(7L);
        when(reclamoRepository
                .countByCreadoEnGreaterThanEqualAndCreadoEnLessThan(any(), any()))
                .thenReturn(3L);
        when(reclamoRepository.contarPorEstadoEntre(any(), any())).thenReturn(List.of(
                new Object[]{EstadoReclamo.ABIERTO, 2L},
                new Object[]{EstadoReclamo.RESUELTO, 1L}));

        ReporteResponse respuesta = administradorReporteService
                .obtener(1L, desde, hasta);

        assertEquals(desde, respuesta.desde());
        assertEquals(hasta, respuesta.hasta());
        assertEquals(15L, respuesta.totalSolicitudes());
        assertEquals(2L, respuesta.solicitudesSinOfertas());
        assertEquals(9L, respuesta.totalServicios());
        assertEquals(4L, respuesta.serviciosPorEstado().get(EstadoServicio.CONFIRMADO));
        assertEquals(0L, respuesta.serviciosPorEstado().get(EstadoServicio.PROGRAMADO));
        assertEquals(new BigDecimal("500.00"), respuesta.volumenServicios());
        assertEquals(new BigDecimal("30.00"), respuesta.ingresoComisiones());
        assertEquals(1, respuesta.ingresosPorMes().size());
        assertEquals("2026-10", respuesta.ingresosPorMes().get(0).mes());
        assertEquals(new BigDecimal("12.00"),
                respuesta.ingresosPorMes().get(0).ingresoFixtech());
        assertEquals(1, respuesta.tecnicosMasActivos().size());
        assertEquals("Ana Uno", respuesta.tecnicosMasActivos().get(0).nombre());
        assertEquals(new BigDecimal("4.25"), respuesta.calificacionPromedio());
        assertEquals(7L, respuesta.totalResenas());
        assertEquals(3L, respuesta.totalReclamos());
        assertEquals(2L, respuesta.reclamosPorEstado().get(EstadoReclamo.ABIERTO));
        assertEquals(0L, respuesta.reclamosPorEstado().get(EstadoReclamo.CERRADO));

        verify(solicitudRepository).contarSinOfertasEntre(
                eq(LocalDateTime.of(2026, 10, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 10, 26, 0, 0)),
                anyList(),
                anyList());
    }

    @Test
    void devuelve_ceros_cuando_el_periodo_no_tiene_datos() {
        conAdministrador();
        when(servicioRepository.contarPorEstadoEntre(any(), any())).thenReturn(List.of());
        when(comisionRepository.ingresosPorMes(any(), any())).thenReturn(List.of());
        when(comisionRepository.sumarVolumenEntre(
                any(), any(), eq(EstadoComision.ANULADA))).thenReturn(null);
        when(comisionRepository.sumarComisionEntre(
                any(), any(), eq(EstadoComision.ANULADA))).thenReturn(null);
        when(servicioRepository.tecnicosMasActivos(
                any(), any(), eq(EstadoServicio.CANCELADO))).thenReturn(List.of());
        when(resenaRepository.promedioEntre(
                eq(EstadoResena.VISIBLE), any(), any())).thenReturn(null);
        when(reclamoRepository.contarPorEstadoEntre(any(), any())).thenReturn(List.of());

        ReporteResponse respuesta = administradorReporteService
                .obtener(1L, desde, hasta);

        assertEquals(new BigDecimal("0.00"), respuesta.volumenServicios());
        assertEquals(new BigDecimal("0.00"), respuesta.ingresoComisiones());
        assertEquals(new BigDecimal("0.00"), respuesta.calificacionPromedio());
        assertTrue(respuesta.ingresosPorMes().isEmpty());
        assertTrue(respuesta.tecnicosMasActivos().isEmpty());
        assertTrue(respuesta.serviciosPorEstado().values().stream()
                .allMatch(valor -> valor == 0L));
        assertTrue(respuesta.reclamosPorEstado().values().stream()
                .allMatch(valor -> valor == 0L));
    }

    @Test
    void valida_las_fechas_del_periodo() {
        conAdministrador();

        assertThrows(BadRequestException.class,
                () -> administradorReporteService.obtener(1L, null, hasta));
        assertThrows(BadRequestException.class,
                () -> administradorReporteService.obtener(1L, desde, null));
        assertThrows(BadRequestException.class,
                () -> administradorReporteService.obtener(
                        1L, hasta, desde));
        assertThrows(BadRequestException.class,
                () -> administradorReporteService.obtener(
                        1L, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 2)));
        verify(solicitudRepository, never())
                .countByCreadoEnGreaterThanEqualAndCreadoEnLessThan(any(), any());
    }

    @Test
    void exige_perfil_de_administrador() {
        when(administradorRepository.findByUsuarioId(9L)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> administradorReporteService.obtener(9L, desde, hasta));
    }

    private void conAdministrador() {
        when(administradorRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(Administrador.builder()
                        .id(1L)
                        .usuario(Usuario.builder().id(1L).rol(Rol.ADMIN).build())
                        .nombres("Admin")
                        .apellidos("Prueba")
                        .build()));
    }
}
