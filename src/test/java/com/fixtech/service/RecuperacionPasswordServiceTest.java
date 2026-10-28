package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fixtech.config.RecuperacionProperties;
import com.fixtech.dto.RecuperacionVerificadaResponse;
import com.fixtech.dto.TokenResponse;
import com.fixtech.entity.TokenRecuperacion;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.EstadoUsuario;
import com.fixtech.entity.enums.Rol;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.ServiceUnavailableException;
import com.fixtech.exception.TooManyRequestsException;
import com.fixtech.repository.TokenRecuperacionRepository;
import com.fixtech.repository.UsuarioRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RecuperacionPasswordServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private TokenRecuperacionRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CorreoRecuperacionService correoRecuperacionService;

    @Mock
    private RecuperacionRateLimiter rateLimiter;

    @Mock
    private AuditoriaService auditoriaService;

    @Mock
    private AutenticacionService autenticacionService;

    private RecuperacionProperties properties;
    private RecuperacionPasswordService service;

    @BeforeEach
    void configurar() {
        properties = new RecuperacionProperties();
        service = new RecuperacionPasswordService(
                usuarioRepository,
                tokenRepository,
                passwordEncoder,
                correoRecuperacionService,
                rateLimiter,
                properties,
                auditoriaService,
                autenticacionService);
    }

    @Test
    void solicitar_envia_un_codigo_de_seis_digitos_y_guarda_solo_el_hash() {
        Usuario usuario = usuario();
        when(correoRecuperacionService.estaHabilitado()).thenReturn(true);
        when(rateLimiter.permitir(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(usuarioRepository.findByCorreo("cliente@fixtech.pe")).thenReturn(Optional.of(usuario));
        when(tokenRepository.existsByUsuarioIdAndCreadoEnAfter(eq(7L), any()))
                .thenReturn(false);
        when(tokenRepository.countByUsuarioIdAndCreadoEnAfter(eq(7L), any())).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("hash-bcrypt");

        service.solicitar("  Cliente@FixTech.pe ", "127.0.0.1");

        ArgumentCaptor<TokenRecuperacion> tokenCaptor =
                ArgumentCaptor.forClass(TokenRecuperacion.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        TokenRecuperacion token = tokenCaptor.getValue();
        assertEquals(usuario, token.getUsuario());
        assertEquals("hash-bcrypt", token.getCodigoHash());
        assertFalse(token.isUsado());
        assertFalse(token.isTicketUsado());
        assertTrue(token.getExpiraEn().isAfter(LocalDateTime.now()));

        ArgumentCaptor<String> codigoCaptor = ArgumentCaptor.forClass(String.class);
        verify(correoRecuperacionService).enviarCodigo(
                eq("cliente@fixtech.pe"), codigoCaptor.capture(), eq(10));
        assertTrue(codigoCaptor.getValue().matches("\\d{6}"));

        verify(tokenRepository).invalidarTodos(7L);
        verify(auditoriaService).registrar(
                eq(7L),
                eq("RECUPERACION_SOLICITADA"),
                eq("usuario"),
                eq(7L),
                anyString());
    }

    @Test
    void solicitar_no_revela_si_el_correo_no_existe() {
        when(correoRecuperacionService.estaHabilitado()).thenReturn(true);
        when(rateLimiter.permitir(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(usuarioRepository.findByCorreo("nadie@fixtech.pe")).thenReturn(Optional.empty());

        service.solicitar("nadie@fixtech.pe", "127.0.0.1");

        verify(tokenRepository, never()).save(any());
        verify(correoRecuperacionService, never()).enviarCodigo(anyString(), anyString(), anyInt());
    }

    @Test
    void solicitar_respeta_el_tiempo_de_reenvio() {
        Usuario usuario = usuario();
        when(correoRecuperacionService.estaHabilitado()).thenReturn(true);
        when(rateLimiter.permitir(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(usuarioRepository.findByCorreo("cliente@fixtech.pe")).thenReturn(Optional.of(usuario));
        when(tokenRepository.existsByUsuarioIdAndCreadoEnAfter(eq(7L), any()))
                .thenReturn(true);

        service.solicitar("cliente@fixtech.pe", "127.0.0.1");

        verify(tokenRepository, never()).save(any());
        verify(correoRecuperacionService, never()).enviarCodigo(anyString(), anyString(), anyInt());
    }

    @Test
    void solicitar_respeta_el_maximo_de_solicitudes_por_correo() {
        Usuario usuario = usuario();
        when(correoRecuperacionService.estaHabilitado()).thenReturn(true);
        when(rateLimiter.permitir(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(usuarioRepository.findByCorreo("cliente@fixtech.pe")).thenReturn(Optional.of(usuario));
        when(tokenRepository.existsByUsuarioIdAndCreadoEnAfter(eq(7L), any()))
                .thenReturn(false);
        when(tokenRepository.countByUsuarioIdAndCreadoEnAfter(eq(7L), any()))
                .thenReturn(3L);

        service.solicitar("cliente@fixtech.pe", "127.0.0.1");

        verify(tokenRepository, never()).save(any());
        verify(correoRecuperacionService, never()).enviarCodigo(anyString(), anyString(), anyInt());
    }

    @Test
    void solicitar_rechaza_si_el_correo_no_esta_configurado() {
        when(correoRecuperacionService.estaHabilitado()).thenReturn(false);

        assertThrows(ServiceUnavailableException.class,
                () -> service.solicitar("cliente@fixtech.pe", "127.0.0.1"));

        verify(usuarioRepository, never()).findByCorreo(anyString());
    }

    @Test
    void solicitar_rechaza_demasiadas_peticiones_por_ip() {
        when(correoRecuperacionService.estaHabilitado()).thenReturn(true);
        when(rateLimiter.permitir(anyString(), anyInt(), any(Duration.class))).thenReturn(false);

        assertThrows(TooManyRequestsException.class,
                () -> service.solicitar("cliente@fixtech.pe", "127.0.0.1"));
    }

    @Test
    void verificar_un_codigo_valido_genera_un_ticket_de_un_solo_uso() {
        Usuario usuario = usuario();
        TokenRecuperacion token = token(usuario, "hash-codigo", LocalDateTime.now().plusMinutes(5));
        when(correoRecuperacionService.estaHabilitado()).thenReturn(true);
        when(rateLimiter.permitir(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(usuarioRepository.findByCorreo("cliente@fixtech.pe")).thenReturn(Optional.of(usuario));
        when(tokenRepository.findTopByUsuarioIdAndUsadoFalseOrderByCreadoEnDescIdDesc(7L))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.matches("123456", "hash-codigo")).thenReturn(true);

        RecuperacionVerificadaResponse response =
                service.verificar("cliente@fixtech.pe", "123456", "127.0.0.1");

        assertNotNull(response.ticket());
        assertFalse(response.ticket().isBlank());
        assertEquals(600L, response.expiraEnSegundos());
        assertTrue(token.isUsado());
        assertFalse(token.isTicketUsado());
        assertNotNull(token.getTicketHash());
        assertEquals(64, token.getTicketHash().length());
        assertTrue(token.getTicketExpiraEn().isAfter(LocalDateTime.now()));
        assertEquals(0, usuario.getIntentosFallidos());
        assertNull(usuario.getBloqueadoHasta());
        verify(auditoriaService).registrar(
                eq(7L),
                eq("RECUPERACION_VERIFICADA"),
                eq("usuario"),
                eq(7L),
                anyString());
    }

    @Test
    void verificar_un_codigo_incorrecto_incrementa_los_intentos() {
        Usuario usuario = usuario();
        TokenRecuperacion token = token(usuario, "hash-codigo", LocalDateTime.now().plusMinutes(5));
        token.setIntentos(1);
        when(correoRecuperacionService.estaHabilitado()).thenReturn(true);
        when(rateLimiter.permitir(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(usuarioRepository.findByCorreo("cliente@fixtech.pe")).thenReturn(Optional.of(usuario));
        when(tokenRepository.findTopByUsuarioIdAndUsadoFalseOrderByCreadoEnDescIdDesc(7L))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.matches("000000", "hash-codigo")).thenReturn(false);

        assertThrows(BadRequestException.class,
                () -> service.verificar("cliente@fixtech.pe", "000000", "127.0.0.1"));

        assertEquals(2, token.getIntentos());
        assertFalse(token.isUsado());
        verify(tokenRepository).save(token);
    }

    @Test
    void verificar_bloquea_el_codigo_al_llegar_al_maximo_de_intentos() {
        Usuario usuario = usuario();
        TokenRecuperacion token = token(usuario, "hash-codigo", LocalDateTime.now().plusMinutes(5));
        token.setIntentos(4);
        when(correoRecuperacionService.estaHabilitado()).thenReturn(true);
        when(rateLimiter.permitir(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(usuarioRepository.findByCorreo("cliente@fixtech.pe")).thenReturn(Optional.of(usuario));
        when(tokenRepository.findTopByUsuarioIdAndUsadoFalseOrderByCreadoEnDescIdDesc(7L))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.matches("000000", "hash-codigo")).thenReturn(false);

        assertThrows(BadRequestException.class,
                () -> service.verificar("cliente@fixtech.pe", "000000", "127.0.0.1"));

        assertEquals(5, token.getIntentos());
        assertTrue(token.isUsado());
    }

    @Test
    void verificar_rechaza_un_codigo_expirado_y_lo_marca_usado() {
        Usuario usuario = usuario();
        TokenRecuperacion token = token(usuario, "hash-codigo", LocalDateTime.now().minusMinutes(1));
        when(correoRecuperacionService.estaHabilitado()).thenReturn(true);
        when(rateLimiter.permitir(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(usuarioRepository.findByCorreo("cliente@fixtech.pe")).thenReturn(Optional.of(usuario));
        when(tokenRepository.findTopByUsuarioIdAndUsadoFalseOrderByCreadoEnDescIdDesc(7L))
                .thenReturn(Optional.of(token));

        assertThrows(BadRequestException.class,
                () -> service.verificar("cliente@fixtech.pe", "123456", "127.0.0.1"));

        assertTrue(token.isUsado());
        verify(tokenRepository).save(token);
    }

    @Test
    void verificar_rechaza_un_correo_inexistente_sin_revelar_el_motivo() {
        when(correoRecuperacionService.estaHabilitado()).thenReturn(true);
        when(rateLimiter.permitir(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(usuarioRepository.findByCorreo("nadie@fixtech.pe")).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class,
                () -> service.verificar("nadie@fixtech.pe", "123456", "127.0.0.1"));

        verify(tokenRepository, never())
                .findTopByUsuarioIdAndUsadoFalseOrderByCreadoEnDescIdDesc(any());
    }

    @Test
    void restablecer_cambia_la_password_y_consume_el_ticket() {
        Usuario usuario = usuario();
        TokenRecuperacion token = ticket(usuario, LocalDateTime.now().plusMinutes(5));
        when(tokenRepository.findByTicketHashAndTicketUsadoFalse(anyString()))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NuevaPassword123")).thenReturn("nuevo-hash");

        service.restablecer("ticket-opaco", "NuevaPassword123");

        assertEquals("nuevo-hash", usuario.getPasswordHash());
        assertEquals(0, usuario.getIntentosFallidos());
        assertNull(usuario.getBloqueadoHasta());
        verify(usuarioRepository).save(usuario);
        verify(tokenRepository).invalidarTodos(7L);
        verify(auditoriaService).registrar(
                eq(7L),
                eq("PASSWORD_RESTABLECIDA"),
                eq("usuario"),
                eq(7L),
                anyString());
    }

    @Test
    void restablecer_rechaza_un_ticket_expirado_y_lo_consume() {
        Usuario usuario = usuario();
        TokenRecuperacion token = ticket(usuario, LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByTicketHashAndTicketUsadoFalse(anyString()))
                .thenReturn(Optional.of(token));

        assertThrows(BadRequestException.class,
                () -> service.restablecer("ticket-opaco", "NuevaPassword123"));

        assertTrue(token.isTicketUsado());
        verify(tokenRepository).save(token);
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void restablecer_rechaza_una_cuenta_suspendida() {
        Usuario usuario = usuario();
        usuario.setEstado(EstadoUsuario.SUSPENDIDO);
        TokenRecuperacion token = ticket(usuario, LocalDateTime.now().plusMinutes(5));
        when(tokenRepository.findByTicketHashAndTicketUsadoFalse(anyString()))
                .thenReturn(Optional.of(token));

        assertThrows(ForbiddenException.class,
                () -> service.restablecer("ticket-opaco", "NuevaPassword123"));

        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void acceder_canjea_el_ticket_por_tokens_y_limpia_el_bloqueo() {
        Usuario usuario = usuario();
        usuario.setIntentosFallidos(4);
        usuario.setBloqueadoHasta(LocalDateTime.now().plusMinutes(10));
        TokenRecuperacion token = ticket(usuario, LocalDateTime.now().plusMinutes(5));
        TokenResponse tokens = TokenResponse.bearer("access", "refresh", 900L);
        when(tokenRepository.findByTicketHashAndTicketUsadoFalse(anyString()))
                .thenReturn(Optional.of(token));
        when(autenticacionService.generarTokens(usuario)).thenReturn(tokens);

        TokenResponse response = service.acceder("ticket-opaco");

        assertEquals(tokens, response);
        assertEquals(0, usuario.getIntentosFallidos());
        assertNull(usuario.getBloqueadoHasta());
        verify(usuarioRepository).save(usuario);
        verify(tokenRepository).invalidarTodos(7L);
        verify(auditoriaService).registrar(
                eq(7L),
                eq("ACCESO_OTP"),
                eq("usuario"),
                eq(7L),
                anyString());
    }

    @Test
    void acceder_rechaza_un_ticket_inexistente() {
        when(tokenRepository.findByTicketHashAndTicketUsadoFalse(anyString()))
                .thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> service.acceder("ticket-opaco"));

        verify(autenticacionService, never()).generarTokens(any());
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

    private TokenRecuperacion token(
            Usuario usuario, String codigoHash, LocalDateTime expiraEn) {
        return TokenRecuperacion.builder()
                .id(1L)
                .usuario(usuario)
                .codigoHash(codigoHash)
                .expiraEn(expiraEn)
                .intentos(0)
                .usado(false)
                .ticketUsado(false)
                .build();
    }

    private TokenRecuperacion ticket(Usuario usuario, LocalDateTime ticketExpiraEn) {
        return TokenRecuperacion.builder()
                .id(1L)
                .usuario(usuario)
                .codigoHash("hash-codigo")
                .ticketHash("hash-ticket")
                .expiraEn(LocalDateTime.now().minusMinutes(1))
                .ticketExpiraEn(ticketExpiraEn)
                .intentos(0)
                .usado(true)
                .ticketUsado(false)
                .build();
    }
}
