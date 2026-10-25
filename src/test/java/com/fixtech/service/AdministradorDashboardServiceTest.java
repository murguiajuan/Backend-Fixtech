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

import com.fixtech.dto.DashboardResponse;
import com.fixtech.entity.Administrador;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.EstadoComision;
import com.fixtech.entity.enums.EstadoPagoComision;
import com.fixtech.entity.enums.EstadoReclamo;
import com.fixtech.entity.enums.EstadoResena;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.EstadoUsuario;
import com.fixtech.entity.enums.EstadoVerificacion;
import com.fixtech.entity.enums.Rol;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.repository.AdministradorRepository;
import com.fixtech.repository.ComisionRepository;
import com.fixtech.repository.PagoComisionRepository;
import com.fixtech.repository.ReclamoRepository;
import com.fixtech.repository.ResenaRepository;
import com.fixtech.repository.ServicioRepository;
import com.fixtech.repository.SolicitudRepository;
import com.fixtech.repository.TecnicoRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdministradorDashboardServiceTest {

    @Mock
    private AdministradorRepository administradorRepository;

    @Mock
    private SolicitudRepository solicitudRepository;

    @Mock
    private ServicioRepository servicioRepository;

    @Mock
    private ComisionRepository comisionRepository;

    @Mock
    private PagoComisionRepository pagoComisionRepository;

    @Mock
    private TecnicoRepository tecnicoRepository;

    @Mock
    private ResenaRepository resenaRepository;

    @Mock
    private ReclamoRepository reclamoRepository;

    @InjectMocks
    private AdministradorDashboardService administradorDashboardService;

    @Test
    void calcula_los_indicadores_del_dashboard() {
        conAdministrador();
        when(solicitudRepository.count()).thenReturn(12L);
        when(solicitudRepository.contarSinOfertas(anyList(), anyList())).thenReturn(3L);
        when(servicioRepository.count()).thenReturn(8L);
        when(servicioRepository.contarPorEstado()).thenReturn(List.of(
                new Object[]{EstadoServicio.CONFIRMADO, 5L},
                new Object[]{EstadoServicio.CANCELADO, 3L}));
        when(comisionRepository.sumarComisionTotal(EstadoComision.ANULADA))
                .thenReturn(new BigDecimal("120.50"));
        when(comisionRepository.sumarComisionEntre(
                any(), any(), eq(EstadoComision.ANULADA)))
                .thenReturn(new BigDecimal("20.00"));
        when(comisionRepository.countByEstado(EstadoComision.PENDIENTE)).thenReturn(4L);
        when(comisionRepository.sumarMontoPorEstado(EstadoComision.PENDIENTE))
                .thenReturn(new BigDecimal("30.00"));
        when(pagoComisionRepository.countByEstado(EstadoPagoComision.PENDIENTE))
                .thenReturn(2L);
        when(tecnicoRepository.contarActivos(
                EstadoVerificacion.APROBADO, EstadoUsuario.ACTIVO)).thenReturn(6L);
        when(resenaRepository.promedioGlobal(EstadoResena.VISIBLE)).thenReturn(4.567);
        when(resenaRepository.countByEstado(EstadoResena.VISIBLE)).thenReturn(10L);
        when(resenaRepository.countByEstado(EstadoResena.REPORTADA)).thenReturn(1L);
        when(reclamoRepository.countByEstadoIn(anyList())).thenReturn(2L);
        when(servicioRepository.tecnicosMasActivos(
                any(), any(), eq(EstadoServicio.CANCELADO))).thenReturn(List.of(
                        new Object[]{2L, "Ana", "Uno", new BigDecimal("4.50"), 9L},
                        new Object[]{3L, "Luis", "Dos", new BigDecimal("4.20"), 7L},
                        new Object[]{4L, "Eva", "Tres", new BigDecimal("4.10"), 6L},
                        new Object[]{5L, "Rosa", "Cuatro", new BigDecimal("4.00"), 5L},
                        new Object[]{6L, "Mario", "Cinco", new BigDecimal("3.90"), 4L},
                        new Object[]{7L, "Nora", "Seis", new BigDecimal("3.80"), 3L}));

        DashboardResponse respuesta = administradorDashboardService.obtener(1L);

        assertEquals(12L, respuesta.totalSolicitudes());
        assertEquals(3L, respuesta.solicitudesSinOfertas());
        assertEquals(8L, respuesta.totalServicios());
        assertEquals(12, respuesta.serviciosPorEstado().size());
        assertEquals(5L, respuesta.serviciosPorEstado().get(EstadoServicio.CONFIRMADO));
        assertEquals(0L, respuesta.serviciosPorEstado().get(EstadoServicio.PROGRAMADO));
        assertEquals(new BigDecimal("120.50"), respuesta.ingresoTotalComisiones());
        assertEquals(new BigDecimal("20.00"), respuesta.ingresoMesActual());
        assertEquals(4L, respuesta.comisionesPendientes());
        assertEquals(new BigDecimal("30.00"), respuesta.deudaComisionesPendiente());
        assertEquals(2L, respuesta.pagosPorValidar());
        assertEquals(6L, respuesta.tecnicosActivos());
        assertEquals(new BigDecimal("4.57"), respuesta.calificacionPromedio());
        assertEquals(10L, respuesta.totalResenas());
        assertEquals(2L, respuesta.reclamosAbiertos());
        assertEquals(1L, respuesta.resenasReportadas());
        assertEquals(5, respuesta.tecnicosMasActivos().size());
        assertEquals("Ana Uno", respuesta.tecnicosMasActivos().get(0).nombre());
        assertEquals(9L, respuesta.tecnicosMasActivos().get(0).servicios());
        assertEquals(new BigDecimal("4.50"),
                respuesta.tecnicosMasActivos().get(0).calificacionPromedio());
    }

    @Test
    void devuelve_ceros_cuando_no_existen_datos() {
        conAdministrador();
        when(servicioRepository.contarPorEstado()).thenReturn(List.of());
        when(comisionRepository.sumarComisionTotal(EstadoComision.ANULADA)).thenReturn(null);
        when(comisionRepository.sumarComisionEntre(
                any(), any(), eq(EstadoComision.ANULADA))).thenReturn(null);
        when(comisionRepository.sumarMontoPorEstado(EstadoComision.PENDIENTE))
                .thenReturn(null);
        when(resenaRepository.promedioGlobal(EstadoResena.VISIBLE)).thenReturn(null);
        when(servicioRepository.tecnicosMasActivos(
                any(), any(), eq(EstadoServicio.CANCELADO))).thenReturn(List.of());

        DashboardResponse respuesta = administradorDashboardService.obtener(1L);

        assertEquals(new BigDecimal("0.00"), respuesta.ingresoTotalComisiones());
        assertEquals(new BigDecimal("0.00"), respuesta.ingresoMesActual());
        assertEquals(new BigDecimal("0.00"), respuesta.deudaComisionesPendiente());
        assertEquals(new BigDecimal("0.00"), respuesta.calificacionPromedio());
        assertEquals(12, respuesta.serviciosPorEstado().size());
        assertTrue(respuesta.serviciosPorEstado().values().stream()
                .allMatch(valor -> valor == 0L));
        assertTrue(respuesta.tecnicosMasActivos().isEmpty());
    }

    @Test
    void exige_perfil_de_administrador() {
        when(administradorRepository.findByUsuarioId(9L)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> administradorDashboardService.obtener(9L));
        verify(solicitudRepository, never()).count();
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
