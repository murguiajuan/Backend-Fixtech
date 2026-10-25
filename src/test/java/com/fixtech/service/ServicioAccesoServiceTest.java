package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fixtech.entity.Servicio;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.repository.ServicioRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServicioAccesoServiceTest {

    @Mock
    private ServicioRepository servicioRepository;

    @InjectMocks
    private ServicioAccesoService servicioAccesoService;

    @Test
    void obtiene_el_servicio_para_un_participante() {
        Servicio servicio = servicio();
        when(servicioRepository.buscarDetallePorId(5L))
                .thenReturn(Optional.of(servicio));

        Servicio encontrado = servicioAccesoService.obtenerParaParticipante(10L, 5L);

        assertSame(servicio, encontrado);
    }

    @Test
    void falla_si_el_servicio_no_existe() {
        when(servicioRepository.buscarDetallePorId(5L))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> servicioAccesoService.obtenerParaParticipante(10L, 5L));
    }

    @Test
    void falla_si_el_usuario_no_es_participante() {
        Servicio servicio = servicio();
        when(servicioRepository.buscarDetallePorId(5L))
                .thenReturn(Optional.of(servicio));

        assertThrows(
                ForbiddenException.class,
                () -> servicioAccesoService.obtenerParaParticipante(99L, 5L));
    }

    @Test
    void identifica_al_cliente_y_al_tecnico_del_servicio() {
        Servicio servicio = servicio();

        assertTrue(servicioAccesoService.esCliente(servicio, 10L));
        assertFalse(servicioAccesoService.esCliente(servicio, 20L));
        assertTrue(servicioAccesoService.esTecnico(servicio, 20L));
        assertFalse(servicioAccesoService.esTecnico(servicio, 10L));
    }

    @Test
    void valida_los_roles_de_cliente_y_tecnico() {
        Servicio servicio = servicio();

        assertDoesNotThrow(() -> servicioAccesoService.validarCliente(servicio, 10L));
        assertDoesNotThrow(() -> servicioAccesoService.validarTecnico(servicio, 20L));
        assertThrows(
                ForbiddenException.class,
                () -> servicioAccesoService.validarCliente(servicio, 20L));
        assertThrows(
                ForbiddenException.class,
                () -> servicioAccesoService.validarTecnico(servicio, 10L));
    }

    @Test
    void bloquea_el_servicio_para_una_transicion() {
        Servicio servicio = servicio();
        when(servicioRepository.findByIdForUpdate(5L))
                .thenReturn(Optional.of(servicio));

        assertSame(servicio, servicioAccesoService.bloquear(5L));
    }

    @Test
    void falla_al_bloquear_un_servicio_inexistente() {
        when(servicioRepository.findByIdForUpdate(5L))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> servicioAccesoService.bloquear(5L));
    }

    private Servicio servicio() {
        return ServicioFixture.servicio(
                5L, EstadoServicio.PROGRAMADO, Modalidad.DOMICILIO);
    }
}
