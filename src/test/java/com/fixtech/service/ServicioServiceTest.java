package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.entity.Cliente;
import com.fixtech.entity.Distrito;
import com.fixtech.entity.Oferta;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.Solicitud;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.EstadoOferta;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.EstadoSolicitud;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.Rol;
import com.fixtech.exception.BusinessException;
import com.fixtech.repository.ServicioRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServicioServiceTest {

    @Mock
    private ServicioRepository servicioRepository;

    @Mock
    private ServicioEventoService servicioEventoService;

    @InjectMocks
    private ServicioService servicioService;

    @Test
    void crea_servicio_programado_con_monto_total_servicio_mas_movilidad() {
        Solicitud solicitud = solicitud();
        Oferta oferta = oferta(solicitud);

        when(servicioRepository.existsByOfertaId(100L)).thenReturn(false);
        when(servicioRepository.save(any(Servicio.class))).thenAnswer(invocation -> {
            Servicio servicio = invocation.getArgument(0);
            servicio.setId(300L);
            return servicio;
        });

        Servicio servicio = servicioService.crearDesdeOferta(oferta, solicitud);

        assertEquals(300L, servicio.getId());
        assertEquals(EstadoServicio.PROGRAMADO, servicio.getEstado());
        assertEquals(new BigDecimal("100.00"), servicio.getPrecioServicio());
        assertEquals(new BigDecimal("15.00"), servicio.getCostoMovilidad());
        assertEquals(new BigDecimal("5.00"), servicio.getCostoDiagnostico());
        assertEquals(new BigDecimal("115.00"), servicio.getMontoTotal());
        assertEquals(7, servicio.getGarantiaDias());
        assertNotNull(servicio.getPinInicio());
        assertEquals(4, servicio.getPinInicio().length());
        assertNull(servicio.getFechaProgramada());
        assertSame(servicio, oferta.getServicio());
        verify(servicioRepository).save(any(Servicio.class));
        verify(servicioEventoService).registrarCambio(
                servicio,
                null,
                EstadoServicio.PROGRAMADO,
                null,
                "Servicio creado al aceptar la oferta");
    }

    @Test
    void rechaza_crear_dos_servicios_para_la_misma_oferta() {
        Solicitud solicitud = solicitud();
        Oferta oferta = oferta(solicitud);

        when(servicioRepository.existsByOfertaId(100L)).thenReturn(true);

        assertThrows(
                BusinessException.class,
                () -> servicioService.crearDesdeOferta(oferta, solicitud));
        verify(servicioRepository, never()).save(any(Servicio.class));
    }

    private Solicitud solicitud() {
        Cliente cliente = Cliente.builder()
                .id(1L)
                .usuario(Usuario.builder().id(10L).rol(Rol.CLIENTE).build())
                .nombres("Cliente")
                .apellidos("Prueba")
                .celular("999999999")
                .build();
        Tecnico tecnico = Tecnico.builder()
                .id(2L)
                .usuario(Usuario.builder().id(1L).rol(Rol.TECNICO).build())
                .nombres("Tecnico")
                .apellidos("Prueba")
                .dni("12345678")
                .celular("988888888")
                .distritos(Set.of(Distrito.builder().id(1L).nombre("Piura").build()))
                .build();
        return Solicitud.builder()
                .id(50L)
                .cliente(cliente)
                .tipoEquipo(null)
                .tipoServicio(null)
                .distrito(Distrito.builder().id(1L).nombre("Piura").build())
                .modalidad(Modalidad.DOMICILIO)
                .estado(EstadoSolicitud.CON_OFERTAS)
                .fechaVencimiento(LocalDateTime.now().plusDays(1))
                .descripcion("Descripcion")
                .build();
    }

    private Oferta oferta(Solicitud solicitud) {
        return Oferta.builder()
                .id(100L)
                .solicitud(solicitud)
                .tecnico(Tecnico.builder()
                        .id(2L)
                        .usuario(Usuario.builder().id(1L).rol(Rol.TECNICO).build())
                        .nombres("Tecnico")
                        .apellidos("Prueba")
                        .dni("12345678")
                        .celular("988888888")
                        .build())
                .modalidad(Modalidad.DOMICILIO)
                .precioServicio(new BigDecimal("100.00"))
                .costoMovilidad(new BigDecimal("15.00"))
                .costoDiagnostico(new BigDecimal("5.00"))
                .tiempoEstimadoHoras(2)
                .garantiaDias(7)
                .estado(EstadoOferta.ENVIADA)
                .build();
    }
}
