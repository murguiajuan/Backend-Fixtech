package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.dto.CambioEstadoUsuarioRequest;
import com.fixtech.dto.UsuarioAdminResponse;
import com.fixtech.entity.Administrador;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.EstadoUsuario;
import com.fixtech.entity.enums.Rol;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.BusinessException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.repository.AdministradorRepository;
import com.fixtech.repository.ClienteRepository;
import com.fixtech.repository.TecnicoRepository;
import com.fixtech.repository.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdministradorUsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private TecnicoRepository tecnicoRepository;

    @Mock
    private AdministradorRepository administradorRepository;

    @Mock
    private NotificacionService notificacionService;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private AdministradorUsuarioService administradorUsuarioService;

    @Test
    void lista_usuarios_con_sus_nombres_y_filtros() {
        Usuario usuario = usuario(2L, "cliente@fixtech.pe", Rol.CLIENTE,
                EstadoUsuario.ACTIVO);
        when(usuarioRepository.buscarParaAdministracion(
                "CLIENTE", "ACTIVO", "juan", "%juan%"))
                .thenReturn(List.of(usuario));
        when(clienteRepository.buscarNombresPorUsuarioIds(anyList()))
                .thenReturn(List.<Object[]>of(new Object[]{2L, "Juan", "Pérez"}));

        List<UsuarioAdminResponse> respuesta = administradorUsuarioService
                .listar(Rol.CLIENTE, EstadoUsuario.ACTIVO, "  juan  ");

        assertEquals(1, respuesta.size());
        assertEquals("Juan", respuesta.get(0).nombres());
        assertEquals("Pérez", respuesta.get(0).apellidos());
        assertEquals(Rol.CLIENTE, respuesta.get(0).rol());
    }

    @Test
    void lista_sin_filtros_cuando_la_busqueda_esta_vacia() {
        when(usuarioRepository.buscarParaAdministracion("", "", "", ""))
                .thenReturn(List.of());

        List<UsuarioAdminResponse> respuesta = administradorUsuarioService
                .listar(null, null, "   ");

        assertTrue(respuesta.isEmpty());
    }

    @Test
    void rechaza_una_busqueda_demasiado_larga() {
        assertThrows(BadRequestException.class,
                () -> administradorUsuarioService
                        .listar(null, null, "x".repeat(121)));
    }

    @Test
    void suspende_un_usuario_con_motivo_y_notifica() {
        conAdministrador();
        Usuario usuario = usuario(2L, "cliente@fixtech.pe", Rol.CLIENTE,
                EstadoUsuario.ACTIVO);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario));
        when(clienteRepository.buscarNombresPorUsuarioIds(anyList()))
                .thenReturn(List.<Object[]>of(new Object[]{2L, "Juan", "Pérez"}));

        UsuarioAdminResponse respuesta = administradorUsuarioService.cambiarEstado(
                1L,
                2L,
                new CambioEstadoUsuarioRequest(
                        EstadoUsuario.SUSPENDIDO, " Incumplimiento de términos "));

        assertEquals(EstadoUsuario.SUSPENDIDO, respuesta.estado());
        assertEquals("Incumplimiento de términos", usuario.getMotivoSuspension());
        assertEquals("Incumplimiento de términos", respuesta.motivoSuspension());
        verify(notificacionService).crear(
                eq(2L),
                eq(TipoNotificacion.USUARIO_SUSPENDIDO),
                anyString(),
                contains("Incumplimiento de términos"),
                eq("USUARIO"),
                eq(2L));
        verify(auditoriaService).registrar(
                eq(1L),
                eq("SUSPENDER_USUARIO"),
                eq("usuario"),
                eq(2L),
                contains("ACTIVO"));
    }

    @Test
    void reactiva_un_usuario_y_limpia_el_motivo() {
        conAdministrador();
        Usuario usuario = usuario(2L, "cliente@fixtech.pe", Rol.CLIENTE,
                EstadoUsuario.SUSPENDIDO);
        usuario.setMotivoSuspension("Deuda pendiente");
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario));

        UsuarioAdminResponse respuesta = administradorUsuarioService.cambiarEstado(
                1L,
                2L,
                new CambioEstadoUsuarioRequest(
                        EstadoUsuario.ACTIVO, "Ya regularizó"));

        assertEquals(EstadoUsuario.ACTIVO, respuesta.estado());
        assertNull(usuario.getMotivoSuspension());
        verify(notificacionService).crear(
                eq(2L),
                eq(TipoNotificacion.USUARIO_REACTIVADO),
                anyString(),
                anyString(),
                eq("USUARIO"),
                eq(2L));
        verify(auditoriaService).registrar(
                eq(1L),
                eq("REACTIVAR_USUARIO"),
                eq("usuario"),
                eq(2L),
                contains("SUSPENDIDO"));
    }

    @Test
    void no_permite_cambiar_el_estado_a_uno_igual() {
        conAdministrador();
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(
                usuario(2L, "cliente@fixtech.pe", Rol.CLIENTE,
                        EstadoUsuario.ACTIVO)));

        assertThrows(BusinessException.class,
                () -> administradorUsuarioService.cambiarEstado(
                        1L,
                        2L,
                        new CambioEstadoUsuarioRequest(
                                EstadoUsuario.ACTIVO, null)));
    }

    @Test
    void no_permite_suspender_la_propia_cuenta_ni_a_otro_administrador() {
        conAdministrador();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(
                usuario(1L, "admin@fixtech.pe", Rol.ADMIN,
                        EstadoUsuario.ACTIVO)));

        assertThrows(ForbiddenException.class,
                () -> administradorUsuarioService.cambiarEstado(
                        1L,
                        1L,
                        new CambioEstadoUsuarioRequest(
                                EstadoUsuario.SUSPENDIDO, "prueba")));

        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(
                usuario(3L, "otro-admin@fixtech.pe", Rol.ADMIN,
                        EstadoUsuario.ACTIVO)));
        assertThrows(ForbiddenException.class,
                () -> administradorUsuarioService.cambiarEstado(
                        1L,
                        3L,
                        new CambioEstadoUsuarioRequest(
                                EstadoUsuario.SUSPENDIDO, "prueba")));
    }

    @Test
    void exige_motivo_valido_para_suspender() {
        conAdministrador();
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(
                usuario(2L, "cliente@fixtech.pe", Rol.CLIENTE,
                        EstadoUsuario.ACTIVO)));

        assertThrows(BadRequestException.class,
                () -> administradorUsuarioService.cambiarEstado(
                        1L,
                        2L,
                        new CambioEstadoUsuarioRequest(
                                EstadoUsuario.SUSPENDIDO, "   ")));
        assertThrows(BadRequestException.class,
                () -> administradorUsuarioService.cambiarEstado(
                        1L,
                        2L,
                        new CambioEstadoUsuarioRequest(
                                EstadoUsuario.SUSPENDIDO, "x".repeat(301))));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void valida_el_estado_solicitado_y_los_datos_de_entrada() {
        conAdministrador();

        assertThrows(BadRequestException.class,
                () -> administradorUsuarioService.cambiarEstado(1L, 2L, null));
        assertThrows(BadRequestException.class,
                () -> administradorUsuarioService.cambiarEstado(
                        1L,
                        2L,
                        new CambioEstadoUsuarioRequest(null, null)));
        assertThrows(BadRequestException.class,
                () -> administradorUsuarioService.cambiarEstado(
                        1L,
                        2L,
                        new CambioEstadoUsuarioRequest(
                                EstadoUsuario.INACTIVO, null)));
    }

    @Test
    void falla_si_el_usuario_no_existe_o_el_actor_no_es_administrador() {
        conAdministrador();
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> administradorUsuarioService.cambiarEstado(
                        1L,
                        99L,
                        new CambioEstadoUsuarioRequest(
                                EstadoUsuario.SUSPENDIDO, "prueba")));

        when(administradorRepository.findByUsuarioId(9L))
                .thenReturn(Optional.empty());
        assertThrows(ForbiddenException.class,
                () -> administradorUsuarioService.cambiarEstado(
                        9L,
                        2L,
                        new CambioEstadoUsuarioRequest(
                                EstadoUsuario.SUSPENDIDO, "prueba")));
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

    private Usuario usuario(
            Long id, String correo, Rol rol, EstadoUsuario estado) {
        return Usuario.builder()
                .id(id)
                .correo(correo)
                .rol(rol)
                .estado(estado)
                .creadoEn(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }
}
