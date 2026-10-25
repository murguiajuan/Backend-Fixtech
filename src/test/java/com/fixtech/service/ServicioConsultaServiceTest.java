package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.dto.ServicioDetalleResponse;
import com.fixtech.dto.ServicioResumenResponse;
import com.fixtech.entity.Cliente;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.mapper.ServicioMapper;
import com.fixtech.repository.AjustePrecioRepository;
import com.fixtech.repository.AvanceServicioRepository;
import com.fixtech.repository.ClienteRepository;
import com.fixtech.repository.ConstanciaRepository;
import com.fixtech.repository.HistorialEstadoServicioRepository;
import com.fixtech.repository.ServicioRepository;
import com.fixtech.repository.TecnicoRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServicioConsultaServiceTest {

    @Mock
    private ServicioRepository servicioRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private TecnicoRepository tecnicoRepository;

    @Mock
    private ServicioAccesoService servicioAccesoService;

    @Mock
    private HistorialEstadoServicioRepository historialRepository;

    @Mock
    private ConstanciaRepository constanciaRepository;

    @Mock
    private AvanceServicioRepository avanceRepository;

    @Mock
    private AjustePrecioRepository ajustePrecioRepository;

    @Spy
    private ServicioMapper servicioMapper = new ServicioMapper();

    @InjectMocks
    private ServicioConsultaService servicioConsultaService;

    @Test
    void lista_los_servicios_del_cliente_filtrando_por_estado() {
        Servicio programado = ServicioFixture.servicio(
                5L, EstadoServicio.PROGRAMADO, Modalidad.DOMICILIO);
        Servicio finalizado = ServicioFixture.servicio(
                6L, EstadoServicio.FINALIZADO, Modalidad.DOMICILIO);
        Cliente cliente = programado.getCliente();
        when(clienteRepository.findByUsuarioId(10L))
                .thenReturn(Optional.of(cliente));
        when(servicioRepository.buscarPorCliente(1L))
                .thenReturn(List.of(programado, finalizado));

        List<ServicioResumenResponse> respuesta = servicioConsultaService
                .listarMios(10L, EstadoServicio.PROGRAMADO);

        assertEquals(1, respuesta.size());
        assertEquals(5L, respuesta.get(0).id());
        assertEquals("Tecnico Prueba", respuesta.get(0).contraparteNombre());
    }

    @Test
    void lista_los_servicios_del_tecnico() {
        Servicio servicio = ServicioFixture.servicio(
                5L, EstadoServicio.PROGRAMADO, Modalidad.DOMICILIO);
        Tecnico tecnico = servicio.getTecnico();
        when(clienteRepository.findByUsuarioId(20L))
                .thenReturn(Optional.empty());
        when(tecnicoRepository.findByUsuarioId(20L))
                .thenReturn(Optional.of(tecnico));
        when(servicioRepository.buscarPorTecnico(2L))
                .thenReturn(List.of(servicio));

        List<ServicioResumenResponse> respuesta = servicioConsultaService
                .listarMios(20L, null);

        assertEquals(1, respuesta.size());
        assertEquals("Cliente Prueba", respuesta.get(0).contraparteNombre());
    }

    @Test
    void falla_si_el_usuario_no_tiene_perfil() {
        when(clienteRepository.findByUsuarioId(99L))
                .thenReturn(Optional.empty());
        when(tecnicoRepository.findByUsuarioId(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                ForbiddenException.class,
                () -> servicioConsultaService.listarMios(99L, null));
    }

    @Test
    void obtiene_el_detalle_para_el_cliente_con_pin_y_direccion() {
        Servicio servicio = ServicioFixture.servicio(
                5L, EstadoServicio.PROGRAMADO, Modalidad.DOMICILIO);
        when(servicioAccesoService.obtenerParaParticipante(10L, 5L))
                .thenReturn(servicio);
        when(servicioAccesoService.esCliente(servicio, 10L)).thenReturn(true);
        prepararListasVacias(5L);

        ServicioDetalleResponse detalle = servicioConsultaService
                .obtenerDetalle(10L, 5L);

        assertEquals(5L, detalle.id());
        assertEquals("1234", detalle.pinInicio());
        assertEquals("Av. Siempre Viva 123", detalle.solicitud().direccion());
        assertEquals(0, detalle.historial().size());
        verify(ajustePrecioRepository)
                .findTopByServicioIdAndEstadoOrderByCreadoEnDesc(
                        eq(5L), any());
    }

    @Test
    void oculta_el_pin_y_la_direccion_al_tecnico() {
        Servicio servicio = ServicioFixture.servicio(
                5L, EstadoServicio.PROGRAMADO, Modalidad.DOMICILIO);
        when(servicioAccesoService.obtenerParaParticipante(20L, 5L))
                .thenReturn(servicio);
        when(servicioAccesoService.esCliente(servicio, 20L)).thenReturn(false);
        prepararListasVacias(5L);

        ServicioDetalleResponse detalle = servicioConsultaService
                .obtenerDetalle(20L, 5L);

        assertNull(detalle.pinInicio());
        assertEquals("Av. Siempre Viva 123", detalle.solicitud().direccion());
    }

    private void prepararListasVacias(Long servicioId) {
        when(historialRepository.findByServicioIdOrderByCreadoEnAscIdAsc(servicioId))
                .thenReturn(List.of());
        when(constanciaRepository.buscarPorServicio(servicioId))
                .thenReturn(List.of());
        when(avanceRepository.findByServicioIdOrderByCreadoEnAscIdAsc(servicioId))
                .thenReturn(List.of());
        when(ajustePrecioRepository.findByServicioIdOrderByCreadoEnDesc(servicioId))
                .thenReturn(List.of());
        when(ajustePrecioRepository
                .findTopByServicioIdAndEstadoOrderByCreadoEnDesc(
                        eq(servicioId), any()))
                .thenReturn(Optional.empty());
    }
}
