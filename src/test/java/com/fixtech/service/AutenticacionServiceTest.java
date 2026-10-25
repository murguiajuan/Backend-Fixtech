package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fixtech.dto.LoginRequest;
import com.fixtech.dto.RefreshTokenRequest;
import com.fixtech.dto.TokenResponse;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.EstadoUsuario;
import com.fixtech.entity.enums.Rol;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.UnauthorizedException;
import com.fixtech.repository.UsuarioRepository;
import com.fixtech.security.JwtService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AutenticacionServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AutenticacionService autenticacionService;

    @Test
    void login_con_credenciales_validas_devuelve_tokens() {
        Usuario usuario = usuario();
        when(usuarioRepository.findByCorreo("cliente@fixtech.pe")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Password123", "hash")).thenReturn(true);
        when(jwtService.generarAccessToken(usuario)).thenReturn("access");
        when(jwtService.generarRefreshToken(usuario)).thenReturn("refresh");
        when(jwtService.expiracionAccessSegundos()).thenReturn(900L);

        TokenResponse response =
                autenticacionService.login(new LoginRequest("cliente@fixtech.pe", "Password123"));

        assertEquals("access", response.accessToken());
        assertEquals("refresh", response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(900L, response.expiraEnSegundos());
        assertEquals(0, usuario.getIntentosFallidos());
        assertNull(usuario.getBloqueadoHasta());
    }

    @Test
    void login_con_password_incorrecta_incrementa_los_intentos() {
        Usuario usuario = usuario();
        when(usuarioRepository.findByCorreo("cliente@fixtech.pe")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("incorrecta", "hash")).thenReturn(false);

        assertThrows(UnauthorizedException.class,
                () -> autenticacionService.login(new LoginRequest("cliente@fixtech.pe", "incorrecta")));

        assertEquals(1, usuario.getIntentosFallidos());
        assertNull(usuario.getBloqueadoHasta());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void login_bloquea_la_cuenta_al_quinto_intento_fallido() {
        Usuario usuario = usuario();
        usuario.setIntentosFallidos(4);
        when(usuarioRepository.findByCorreo("cliente@fixtech.pe")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("incorrecta", "hash")).thenReturn(false);

        assertThrows(UnauthorizedException.class,
                () -> autenticacionService.login(new LoginRequest("cliente@fixtech.pe", "incorrecta")));

        assertEquals(5, usuario.getIntentosFallidos());
        assertNotNull(usuario.getBloqueadoHasta());
        assertTrue(usuario.getBloqueadoHasta().isAfter(LocalDateTime.now()));
    }

    @Test
    void login_rechaza_una_cuenta_bloqueada_sin_validar_la_password() {
        Usuario usuario = usuario();
        usuario.setBloqueadoHasta(LocalDateTime.now().plusMinutes(10));
        when(usuarioRepository.findByCorreo("cliente@fixtech.pe")).thenReturn(Optional.of(usuario));

        assertThrows(ForbiddenException.class,
                () -> autenticacionService.login(new LoginRequest("cliente@fixtech.pe", "Password123")));

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void login_rechaza_una_cuenta_suspendida() {
        Usuario usuario = usuario();
        usuario.setEstado(EstadoUsuario.SUSPENDIDO);
        when(usuarioRepository.findByCorreo("cliente@fixtech.pe")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Password123", "hash")).thenReturn(true);

        assertThrows(ForbiddenException.class,
                () -> autenticacionService.login(new LoginRequest("cliente@fixtech.pe", "Password123")));
    }

    @Test
    void login_rechaza_un_correo_inexistente() {
        when(usuarioRepository.findByCorreo("nadie@fixtech.pe")).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class,
                () -> autenticacionService.login(new LoginRequest("nadie@fixtech.pe", "Password123")));

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void login_restablece_intentos_y_bloqueo_vencido() {
        Usuario usuario = usuario();
        usuario.setIntentosFallidos(3);
        usuario.setBloqueadoHasta(LocalDateTime.now().minusMinutes(1));
        when(usuarioRepository.findByCorreo("cliente@fixtech.pe")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Password123", "hash")).thenReturn(true);
        when(jwtService.generarAccessToken(usuario)).thenReturn("access");
        when(jwtService.generarRefreshToken(usuario)).thenReturn("refresh");
        when(jwtService.expiracionAccessSegundos()).thenReturn(900L);

        autenticacionService.login(new LoginRequest("cliente@fixtech.pe", "Password123"));

        assertEquals(0, usuario.getIntentosFallidos());
        assertNull(usuario.getBloqueadoHasta());
    }

    @Test
    void refrescar_genera_nuevos_tokens() {
        Usuario usuario = usuario();
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("7");
        when(jwtService.decodificarRefresh("refresh")).thenReturn(jwt);
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(usuario));
        when(jwtService.generarAccessToken(usuario)).thenReturn("access2");
        when(jwtService.generarRefreshToken(usuario)).thenReturn("refresh2");
        when(jwtService.expiracionAccessSegundos()).thenReturn(900L);

        TokenResponse response =
                autenticacionService.refrescar(new RefreshTokenRequest("refresh"));

        assertEquals("access2", response.accessToken());
        assertEquals("refresh2", response.refreshToken());
    }

    @Test
    void refrescar_rechaza_un_usuario_inexistente() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("99");
        when(jwtService.decodificarRefresh("refresh")).thenReturn(jwt);
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class,
                () -> autenticacionService.refrescar(new RefreshTokenRequest("refresh")));
    }

    @Test
    void refrescar_rechaza_una_cuenta_suspendida() {
        Usuario usuario = usuario();
        usuario.setEstado(EstadoUsuario.SUSPENDIDO);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("7");
        when(jwtService.decodificarRefresh("refresh")).thenReturn(jwt);
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(usuario));

        assertThrows(ForbiddenException.class,
                () -> autenticacionService.refrescar(new RefreshTokenRequest("refresh")));
    }

    @Test
    void refrescar_rechaza_una_cuenta_bloqueada() {
        Usuario usuario = usuario();
        usuario.setBloqueadoHasta(LocalDateTime.now().plusMinutes(5));
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("7");
        when(jwtService.decodificarRefresh("refresh")).thenReturn(jwt);
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(usuario));

        assertThrows(ForbiddenException.class,
                () -> autenticacionService.refrescar(new RefreshTokenRequest("refresh")));
    }

    private Usuario usuario() {
        return Usuario.builder()
                .id(7L)
                .correo("cliente@fixtech.pe")
                .passwordHash("hash")
                .rol(Rol.CLIENTE)
                .estado(EstadoUsuario.ACTIVO)
                .build();
    }
}
