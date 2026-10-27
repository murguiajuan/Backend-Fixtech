package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.dto.TecnicoAdminResponse;
import com.fixtech.dto.TecnicoVerificacionResponse;
import com.fixtech.dto.VerificacionTecnicoRequest;
import com.fixtech.entity.Administrador;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.EstadoVerificacion;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.Rol;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.repository.AdministradorRepository;
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
class AdministradorTecnicoServiceTest {

    @Mock
    private TecnicoRepository tecnicoRepository;

    @Mock
    private AdministradorRepository administradorRepository;

    @Mock
    private NotificacionService notificacionService;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private AdministradorTecnicoService administradorTecnicoService;

    @Test
    void aprueba_al_tecnico_y_notifica_la_decision() {
        prepararAdministrador();
        Tecnico tecnico = tecnico();
        when(tecnicoRepository.findById(2L)).thenReturn(Optional.of(tecnico));
        when(tecnicoRepository.save(any(Tecnico.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TecnicoVerificacionResponse response = administradorTecnicoService.verificar(
                1L,
                2L,
                new VerificacionTecnicoRequest(EstadoVerificacion.APROBADO, null));

        assertEquals(EstadoVerificacion.APROBADO, response.estadoVerificacion());
        assertNull(response.motivoVerificacion());
        assertEquals(1L, tecnico.getVerificadoPorId());
        assertNotNull(tecnico.getFechaVerificacion());
        verify(notificacionService).crear(
                eq(20L),
                eq(TipoNotificacion.VERIFICACION_TECNICO),
                anyString(),
                anyString(),
                eq("TECNICO"),
                eq(2L));
        verify(auditoriaService).registrar(
                eq(1L),
                eq("VERIFICAR_TECNICO"),
                eq("tecnico"),
                eq(2L),
                contains("PENDIENTE -> APROBADO"));
    }

    @Test
    void exige_motivo_para_observar_al_tecnico() {
        prepararAdministrador();
        when(tecnicoRepository.findById(2L)).thenReturn(Optional.of(tecnico()));

        assertThrows(
                BadRequestException.class,
                () -> administradorTecnicoService.verificar(
                        1L,
                        2L,
                        new VerificacionTecnicoRequest(
                                EstadoVerificacion.OBSERVADO, null)));
        verify(tecnicoRepository, never()).save(any(Tecnico.class));
    }

    @Test
    void rechaza_al_tecnico_con_motivo_y_notifica() {
        prepararAdministrador();
        Tecnico tecnico = tecnico();
        when(tecnicoRepository.findById(2L)).thenReturn(Optional.of(tecnico));
        when(tecnicoRepository.save(any(Tecnico.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TecnicoVerificacionResponse response = administradorTecnicoService.verificar(
                1L,
                2L,
                new VerificacionTecnicoRequest(
                        EstadoVerificacion.RECHAZADO, "Documentación incompleta"));

        assertEquals(EstadoVerificacion.RECHAZADO, response.estadoVerificacion());
        assertEquals("Documentación incompleta", response.motivoVerificacion());
        verify(notificacionService).crear(
                eq(20L),
                eq(TipoNotificacion.VERIFICACION_TECNICO),
                anyString(),
                anyString(),
                eq("TECNICO"),
                eq(2L));
    }

    @Test
    void no_permite_establecer_el_estado_pendiente_desde_el_panel() {
        prepararAdministrador();
        when(tecnicoRepository.findById(2L)).thenReturn(Optional.of(tecnico()));

        assertThrows(
                BadRequestException.class,
                () -> administradorTecnicoService.verificar(
                        1L,
                        2L,
                        new VerificacionTecnicoRequest(
                                EstadoVerificacion.PENDIENTE, null)));
    }

    @Test
    void exige_perfil_de_administrador() {
        when(administradorRepository.findByUsuarioId(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                ForbiddenException.class,
                () -> administradorTecnicoService.verificar(
                        99L,
                        2L,
                        new VerificacionTecnicoRequest(
                                EstadoVerificacion.APROBADO, null)));
    }

    @Test
    void falla_si_el_tecnico_no_existe() {
        prepararAdministrador();
        when(tecnicoRepository.findById(2L)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> administradorTecnicoService.verificar(
                        1L,
                        2L,
                        new VerificacionTecnicoRequest(
                                EstadoVerificacion.APROBADO, null)));
    }

    @Test
    void lista_tecnicos_con_ambos_identificadores_y_modalidades() {
        Tecnico tecnico = tecnico();
        tecnico.getUsuario().setCorreo("tecnico@fixtech.pe");
        tecnico.setOfreceDomicilio(true);
        tecnico.setOfreceTaller(true);
        tecnico.setBloqueadoPorDeuda(true);
        tecnico.setSaldoComisionPendiente(new BigDecimal("12.50"));
        tecnico.setCalificacionPromedio(new BigDecimal("4.50"));
        tecnico.setTotalResenas(4);
        tecnico.setServiciosCompletados(9);
        when(tecnicoRepository.buscarParaAdministracion("", ""))
                .thenReturn(List.of(tecnico));

        List<TecnicoAdminResponse> tecnicos =
                administradorTecnicoService.listar(null, null);

        assertEquals(1, tecnicos.size());
        TecnicoAdminResponse response = tecnicos.getFirst();
        assertEquals(2L, response.id());
        assertEquals(20L, response.usuarioId());
        assertEquals("tecnico@fixtech.pe", response.correo());
        assertEquals(EstadoVerificacion.PENDIENTE, response.estadoVerificacion());
        assertTrue(response.bloqueadoPorDeuda());
        assertEquals(new BigDecimal("12.50"), response.saldoComisionPendiente());
        assertEquals(List.of(Modalidad.DOMICILIO, Modalidad.TALLER),
                response.modalidades());
        assertEquals(new BigDecimal("4.50"), response.calificacionPromedio());
        assertEquals(4, response.totalResenas());
        assertEquals(9, response.serviciosCompletados());
    }

    @Test
    void filtra_por_estado_y_normaliza_la_busqueda() {
        when(tecnicoRepository.buscarParaAdministracionPorEstado(
                EstadoVerificacion.OBSERVADO, "carlos", "%carlos%"))
                .thenReturn(List.of());

        List<TecnicoAdminResponse> tecnicos = administradorTecnicoService.listar(
                EstadoVerificacion.OBSERVADO, "  CARLOS  ");

        assertTrue(tecnicos.isEmpty());
    }

    @Test
    void rechaza_una_busqueda_demasiado_larga() {
        String busqueda = "x".repeat(121);

        assertThrows(
                BadRequestException.class,
                () -> administradorTecnicoService.listar(null, busqueda));
        verify(tecnicoRepository, never()).buscarParaAdministracion(anyString(), anyString());
    }

    private void prepararAdministrador() {
        when(administradorRepository.findByUsuarioId(1L))
                .thenReturn(Optional.of(Administrador.builder()
                        .id(1L)
                        .usuario(Usuario.builder().id(1L).rol(Rol.ADMIN).build())
                        .nombres("Admin")
                        .apellidos("Prueba")
                        .build()));
    }

    private Tecnico tecnico() {
        return Tecnico.builder()
                .id(2L)
                .usuario(Usuario.builder().id(20L).rol(Rol.TECNICO).build())
                .nombres("Tecnico")
                .apellidos("Prueba")
                .dni("12345678")
                .celular("988888888")
                .estadoVerificacion(EstadoVerificacion.PENDIENTE)
                .build();
    }
}
