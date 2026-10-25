package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fixtech.dto.RegistroClienteRequest;
import com.fixtech.dto.RegistroTecnicoRequest;
import com.fixtech.dto.UsuarioRegistradoResponse;
import com.fixtech.entity.Cliente;
import com.fixtech.entity.Distrito;
import com.fixtech.entity.Especialidad;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.EstadoUsuario;
import com.fixtech.entity.enums.EstadoVerificacion;
import com.fixtech.entity.enums.Rol;
import com.fixtech.exception.BusinessException;
import com.fixtech.repository.ClienteRepository;
import com.fixtech.repository.DistritoRepository;
import com.fixtech.repository.EspecialidadRepository;
import com.fixtech.repository.TecnicoRepository;
import com.fixtech.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegistroServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private TecnicoRepository tecnicoRepository;

    @Mock
    private EspecialidadRepository especialidadRepository;

    @Mock
    private DistritoRepository distritoRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private RegistroService registroService;

    @Test
    void registra_un_cliente_con_password_cifrada_y_rol_cliente() {
        when(usuarioRepository.existsByCorreo("ana@fixtech.pe")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario usuario = invocation.getArgument(0);
            usuario.setId(1L);
            return usuario;
        });
        when(clienteRepository.save(any(Cliente.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UsuarioRegistradoResponse response = registroService.registrarCliente(clienteRequest());

        assertEquals(1L, response.id());
        assertEquals("ana@fixtech.pe", response.correo());
        assertEquals(Rol.CLIENTE, response.rol());

        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioCaptor.capture());
        Usuario usuario = usuarioCaptor.getValue();
        assertEquals("hash", usuario.getPasswordHash());
        assertEquals(Rol.CLIENTE, usuario.getRol());
        assertEquals(EstadoUsuario.ACTIVO, usuario.getEstado());
        assertTrue(usuario.isAceptoTerminos());
        assertNotNull(usuario.getFechaAceptoTerminos());

        ArgumentCaptor<Cliente> clienteCaptor = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteRepository).save(clienteCaptor.capture());
        assertEquals("Ana", clienteCaptor.getValue().getNombres());
        assertEquals("12345678", clienteCaptor.getValue().getDni());
    }

    @Test
    void rechaza_un_correo_de_cliente_ya_registrado() {
        when(usuarioRepository.existsByCorreo("ana@fixtech.pe")).thenReturn(true);

        assertThrows(BusinessException.class, () -> registroService.registrarCliente(clienteRequest()));

        verify(usuarioRepository, never()).save(any(Usuario.class));
        verifyNoInteractions(clienteRepository, passwordEncoder);
    }

    @Test
    void registra_un_tecnico_pendiente_con_especialidades_y_distritos() {
        when(usuarioRepository.existsByCorreo("luis@fixtech.pe")).thenReturn(false);
        when(tecnicoRepository.existsByDni("87654321")).thenReturn(false);
        when(especialidadRepository.findAllById(Set.of(1L)))
                .thenReturn(List.of(Especialidad.builder().id(1L).nombre("Redes").build()));
        when(distritoRepository.findAllById(Set.of(2L)))
                .thenReturn(List.of(Distrito.builder()
                        .id(2L)
                        .nombre("Piura")
                        .topeMovilidad(new BigDecimal("15.00"))
                        .build()));
        when(passwordEncoder.encode("Password123")).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario usuario = invocation.getArgument(0);
            usuario.setId(2L);
            return usuario;
        });
        when(tecnicoRepository.save(any(Tecnico.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UsuarioRegistradoResponse response = registroService.registrarTecnico(tecnicoRequest());

        assertEquals(2L, response.id());
        assertEquals(Rol.TECNICO, response.rol());

        ArgumentCaptor<Tecnico> tecnicoCaptor = ArgumentCaptor.forClass(Tecnico.class);
        verify(tecnicoRepository).save(tecnicoCaptor.capture());
        Tecnico tecnico = tecnicoCaptor.getValue();
        assertEquals(EstadoVerificacion.PENDIENTE, tecnico.getEstadoVerificacion());
        assertEquals("87654321", tecnico.getDni());
        assertEquals(5, tecnico.getAniosExperiencia());
        assertTrue(tecnico.isOfreceDomicilio());
        assertTrue(tecnico.isOfreceTaller());
        assertEquals(1, tecnico.getEspecialidades().size());
        assertEquals(1, tecnico.getDistritos().size());
    }

    @Test
    void rechaza_un_tecnico_sin_modalidades() {
        RegistroTecnicoRequest request = tecnicoRequest(false, false, null);

        assertThrows(BusinessException.class, () -> registroService.registrarTecnico(request));

        verifyNoInteractions(usuarioRepository, tecnicoRepository, especialidadRepository,
                distritoRepository, passwordEncoder);
    }

    @Test
    void rechaza_un_tecnico_con_taller_sin_direccion() {
        RegistroTecnicoRequest request = tecnicoRequest(true, true, " ");

        assertThrows(BusinessException.class, () -> registroService.registrarTecnico(request));

        verifyNoInteractions(usuarioRepository, tecnicoRepository, especialidadRepository,
                distritoRepository, passwordEncoder);
    }

    @Test
    void rechaza_un_tecnico_con_especialidad_inexistente() {
        when(usuarioRepository.existsByCorreo("luis@fixtech.pe")).thenReturn(false);
        when(tecnicoRepository.existsByDni("87654321")).thenReturn(false);
        when(especialidadRepository.findAllById(Set.of(1L))).thenReturn(List.of());

        assertThrows(BusinessException.class, () -> registroService.registrarTecnico(tecnicoRequest()));

        verify(usuarioRepository, never()).save(any(Usuario.class));
        verify(tecnicoRepository, never()).save(any(Tecnico.class));
        verifyNoInteractions(distritoRepository);
    }

    @Test
    void rechaza_un_tecnico_con_dni_duplicado() {
        when(usuarioRepository.existsByCorreo("luis@fixtech.pe")).thenReturn(false);
        when(tecnicoRepository.existsByDni("87654321")).thenReturn(true);

        assertThrows(BusinessException.class, () -> registroService.registrarTecnico(tecnicoRequest()));

        verify(usuarioRepository, never()).save(any(Usuario.class));
        verifyNoInteractions(especialidadRepository, distritoRepository);
    }

    private RegistroClienteRequest clienteRequest() {
        return new RegistroClienteRequest(
                "Ana", "Perez", " Ana@Fixtech.pe ", "987654321", "12345678", "Password123", true);
    }

    private RegistroTecnicoRequest tecnicoRequest() {
        return tecnicoRequest(true, true, "Av. Taller 123");
    }

    private RegistroTecnicoRequest tecnicoRequest(
            boolean ofreceDomicilio, boolean ofreceTaller, String direccionTaller) {
        return new RegistroTecnicoRequest(
                "Luis",
                "Gomez",
                "Luis@Fixtech.pe",
                "987654322",
                "87654321",
                "Password123",
                true,
                "Especialista en redes",
                5,
                ofreceDomicilio,
                ofreceTaller,
                direccionTaller,
                "Frente al parque",
                "L-V 9-18",
                Set.of(1L),
                Set.of(2L));
    }
}
