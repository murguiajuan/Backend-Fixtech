package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fixtech.dto.PerfilUsuarioResponse;
import com.fixtech.entity.Administrador;
import com.fixtech.entity.Cliente;
import com.fixtech.entity.Distrito;
import com.fixtech.entity.Especialidad;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.EstadoUsuario;
import com.fixtech.entity.enums.EstadoVerificacion;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.Rol;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.exception.UnauthorizedException;
import com.fixtech.repository.AdministradorRepository;
import com.fixtech.repository.ClienteRepository;
import com.fixtech.repository.TecnicoRepository;
import com.fixtech.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PerfilUsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private TecnicoRepository tecnicoRepository;

    @Mock
    private AdministradorRepository administradorRepository;

    @InjectMocks
    private PerfilUsuarioService perfilUsuarioService;

    @Test
    void devuelve_el_perfil_de_un_cliente() {
        Usuario usuario = usuario(Rol.CLIENTE);
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(usuario));
        when(clienteRepository.findByUsuarioId(7L)).thenReturn(Optional.of(
                Cliente.builder()
                        .id(3L)
                        .usuario(usuario)
                        .nombres("Lucía")
                        .apellidos("Ramos")
                        .celular("988888888")
                        .fotoUrl("cliente.png")
                        .build()));

        PerfilUsuarioResponse perfil = perfilUsuarioService.obtener(7L);

        assertEquals(7L, perfil.id());
        assertEquals("cliente@fixtech.pe", perfil.correo());
        assertEquals(Rol.CLIENTE, perfil.rol());
        assertEquals("Lucía", perfil.nombres());
        assertEquals("Ramos", perfil.apellidos());
        assertEquals("cliente.png", perfil.fotoUrl());
        assertNull(perfil.tecnico());
    }

    @Test
    void devuelve_el_perfil_tecnico_con_verificacion_deuda_y_modalidades() {
        Usuario usuario = usuario(Rol.TECNICO);
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(usuario));
        when(tecnicoRepository.findByUsuarioId(7L)).thenReturn(Optional.of(
                Tecnico.builder()
                        .id(20L)
                        .usuario(usuario)
                        .nombres("Carlos")
                        .apellidos("Mena")
                        .dni("12345678")
                        .celular("999999999")
                        .fotoUrl("tecnico.png")
                        .estadoVerificacion(EstadoVerificacion.OBSERVADO)
                        .motivoVerificacion("DNI ilegible")
                        .bloqueadoPorDeuda(true)
                        .saldoComisionPendiente(new BigDecimal("35.50"))
                        .ofreceDomicilio(true)
                        .ofreceTaller(true)
                        .direccionTaller("Av. Grau 123")
                        .referenciaTaller("Frente al mercado")
                        .horarioTaller("Lunes a sábado")
                        .garantiaDiasDefecto(15)
                        .especialidades(Set.of(especialidad(1L, "Redes y WiFi")))
                        .distritos(Set.of(distrito(2L, "Castilla")))
                        .descripcion("Especialista en refrigeración")
                        .aniosExperiencia((byte) 8)
                        .calificacionPromedio(new BigDecimal("4.75"))
                        .totalResenas(12)
                        .serviciosCompletados(31)
                        .destacado(true)
                        .build()));

        PerfilUsuarioResponse perfil = perfilUsuarioService.obtener(7L);

        assertEquals("Carlos", perfil.nombres());
        assertEquals("/api/tecnicos/20/foto", perfil.fotoUrl());
        assertEquals(20L, perfil.tecnico().id());
        assertEquals("12345678", perfil.tecnico().dni());
        assertEquals("999999999", perfil.tecnico().celular());
        assertEquals(EstadoVerificacion.OBSERVADO,
                perfil.tecnico().estadoVerificacion());
        assertEquals("DNI ilegible", perfil.tecnico().motivoVerificacion());
        assertTrue(perfil.tecnico().bloqueadoPorDeuda());
        assertEquals(new BigDecimal("35.50"), perfil.tecnico().saldoComisionPendiente());
        assertEquals(List.of(Modalidad.DOMICILIO, Modalidad.TALLER),
                perfil.tecnico().modalidades());
        assertTrue(perfil.tecnico().ofreceDomicilio());
        assertTrue(perfil.tecnico().ofreceTaller());
        assertEquals(1, perfil.tecnico().especialidades().size());
        assertEquals("Redes y WiFi", perfil.tecnico().especialidades().get(0).nombre());
        assertEquals(1, perfil.tecnico().distritos().size());
        assertEquals("Castilla", perfil.tecnico().distritos().get(0).nombre());
        assertEquals("Av. Grau 123", perfil.tecnico().direccionTaller());
        assertEquals("Frente al mercado", perfil.tecnico().referenciaTaller());
        assertEquals("Lunes a sábado", perfil.tecnico().horarioTaller());
        assertEquals(15, perfil.tecnico().garantiaDiasDefecto());
        assertEquals(8, perfil.tecnico().aniosExperiencia());
        assertEquals(new BigDecimal("4.75"), perfil.tecnico().calificacionPromedio());
        assertEquals(12, perfil.tecnico().totalResenas());
        assertEquals(31, perfil.tecnico().serviciosCompletados());
        assertTrue(perfil.tecnico().destacado());
    }

    @Test
    void devuelve_el_perfil_de_un_administrador() {
        Usuario usuario = usuario(Rol.ADMIN);
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(usuario));
        when(administradorRepository.findByUsuarioId(7L)).thenReturn(Optional.of(
                Administrador.builder()
                        .id(1L)
                        .usuario(usuario)
                        .nombres("Ana")
                        .apellidos("Torres")
                        .build()));

        PerfilUsuarioResponse perfil = perfilUsuarioService.obtener(7L);

        assertEquals(Rol.ADMIN, perfil.rol());
        assertEquals("Ana", perfil.nombres());
        assertEquals("Torres", perfil.apellidos());
        assertNull(perfil.fotoUrl());
        assertNull(perfil.tecnico());
    }

    @Test
    void rechaza_un_usuario_inexistente() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class,
                () -> perfilUsuarioService.obtener(99L));

        verifyNoInteractions(clienteRepository, tecnicoRepository, administradorRepository);
    }

    @Test
    void rechaza_una_cuenta_no_activa() {
        Usuario usuario = usuario(Rol.CLIENTE);
        usuario.setEstado(EstadoUsuario.SUSPENDIDO);
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(usuario));

        assertThrows(ForbiddenException.class,
                () -> perfilUsuarioService.obtener(7L));

        verifyNoInteractions(clienteRepository, tecnicoRepository, administradorRepository);
    }

    @Test
    void falla_cuando_no_existe_el_perfil_del_rol() {
        Usuario usuario = usuario(Rol.TECNICO);
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(usuario));
        when(tecnicoRepository.findByUsuarioId(7L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> perfilUsuarioService.obtener(7L));
    }

    @Test
    void un_tecnico_sin_modalidades_devuelve_lista_vacia() {
        Usuario usuario = usuario(Rol.TECNICO);
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(usuario));
        when(tecnicoRepository.findByUsuarioId(7L)).thenReturn(Optional.of(
                Tecnico.builder()
                        .id(20L)
                        .usuario(usuario)
                        .nombres("Carlos")
                        .apellidos("Mena")
                        .dni("12345678")
                        .celular("999999999")
                        .build()));

        PerfilUsuarioResponse perfil = perfilUsuarioService.obtener(7L);

        assertTrue(perfil.tecnico().modalidades().isEmpty());
        assertFalse(perfil.tecnico().bloqueadoPorDeuda());
    }

    private Usuario usuario(Rol rol) {
        return Usuario.builder()
                .id(7L)
                .correo("cliente@fixtech.pe")
                .passwordHash("hash")
                .rol(rol)
                .estado(EstadoUsuario.ACTIVO)
                .build();
    }

    private Especialidad especialidad(Long id, String nombre) {
        return Especialidad.builder()
                .id(id)
                .nombre(nombre)
                .activo(true)
                .build();
    }

    private Distrito distrito(Long id, String nombre) {
        return Distrito.builder()
                .id(id)
                .nombre(nombre)
                .provincia("Piura")
                .topeMovilidad(new BigDecimal("15.00"))
                .activo(true)
                .build();
    }
}
