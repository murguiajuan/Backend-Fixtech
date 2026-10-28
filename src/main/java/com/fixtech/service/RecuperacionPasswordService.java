package com.fixtech.service;

import com.fixtech.config.RecuperacionProperties;
import com.fixtech.dto.RecuperacionVerificadaResponse;
import com.fixtech.dto.TokenResponse;
import com.fixtech.entity.TokenRecuperacion;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.EstadoUsuario;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.ServiceUnavailableException;
import com.fixtech.exception.TooManyRequestsException;
import com.fixtech.repository.TokenRecuperacionRepository;
import com.fixtech.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecuperacionPasswordService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String MENSAJE_CODIGO_INVALIDO = "Código inválido o expirado";
    private static final String MENSAJE_TICKET_INVALIDO =
            "La verificación expiró o ya fue utilizada. Solicita un nuevo código.";

    private final UsuarioRepository usuarioRepository;
    private final TokenRecuperacionRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final CorreoRecuperacionService correoRecuperacionService;
    private final RecuperacionRateLimiter rateLimiter;
    private final RecuperacionProperties properties;
    private final AuditoriaService auditoriaService;
    private final AutenticacionService autenticacionService;

    @Transactional
    public void solicitar(String correo, String ip) {
        if (!correoRecuperacionService.estaHabilitado()) {
            throw new ServiceUnavailableException(
                    "El envío de correos no está configurado. Intente más tarde.");
        }

        validarLimiteIp(ip);

        String correoNormalizado = normalizarCorreo(correo);
        Usuario usuario = usuarioRepository.findByCorreo(correoNormalizado).orElse(null);
        if (usuario == null || usuario.getEstado() != EstadoUsuario.ACTIVO) {
            return;
        }

        LocalDateTime ahora = LocalDateTime.now();
        if (tokenRepository.existsByUsuarioIdAndCreadoEnAfter(
                usuario.getId(), ahora.minusSeconds(properties.getReenvioSegundos()))) {
            return;
        }

        long solicitudesRecientes = tokenRepository.countByUsuarioIdAndCreadoEnAfter(
                usuario.getId(), ahora.minusMinutes(properties.getVentanaMinutos()));
        if (solicitudesRecientes >= properties.getMaxSolicitudesPorCorreo()) {
            return;
        }

        tokenRepository.invalidarTodos(usuario.getId());

        String codigo = generarCodigo();
        TokenRecuperacion token = TokenRecuperacion.builder()
                .usuario(usuario)
                .codigoHash(passwordEncoder.encode(codigo))
                .expiraEn(ahora.plusMinutes(properties.getCodigoMinutos()))
                .intentos(0)
                .usado(false)
                .ticketUsado(false)
                .build();
        tokenRepository.save(token);

        correoRecuperacionService.enviarCodigo(
                usuario.getCorreo(), codigo, properties.getCodigoMinutos());

        auditoriaService.registrar(
                usuario.getId(),
                "RECUPERACION_SOLICITADA",
                "usuario",
                usuario.getId(),
                "Código OTP de recuperación enviado");
    }

    @Transactional(noRollbackFor = BadRequestException.class)
    public RecuperacionVerificadaResponse verificar(String correo, String codigo, String ip) {
        if (!correoRecuperacionService.estaHabilitado()) {
            throw new ServiceUnavailableException(
                    "El envío de correos no está configurado. Intente más tarde.");
        }
        validarLimiteVerificacion(ip);

        Usuario usuario = usuarioRepository.findByCorreo(normalizarCorreo(correo)).orElse(null);
        if (usuario == null || usuario.getEstado() != EstadoUsuario.ACTIVO) {
            throw new BadRequestException(MENSAJE_CODIGO_INVALIDO);
        }

        TokenRecuperacion token = tokenRepository
                .findTopByUsuarioIdAndUsadoFalseOrderByCreadoEnDescIdDesc(usuario.getId())
                .orElseThrow(() -> new BadRequestException(MENSAJE_CODIGO_INVALIDO));

        LocalDateTime ahora = LocalDateTime.now();
        if (token.getExpiraEn().isBefore(ahora)) {
            token.setUsado(true);
            tokenRepository.save(token);
            throw new BadRequestException(MENSAJE_CODIGO_INVALIDO);
        }

        if (token.getIntentos() >= properties.getMaxIntentos()) {
            token.setUsado(true);
            tokenRepository.save(token);
            throw new BadRequestException(MENSAJE_CODIGO_INVALIDO);
        }

        if (!passwordEncoder.matches(codigo, token.getCodigoHash())) {
            token.setIntentos(token.getIntentos() + 1);
            if (token.getIntentos() >= properties.getMaxIntentos()) {
                token.setUsado(true);
            }
            tokenRepository.save(token);
            throw new BadRequestException(MENSAJE_CODIGO_INVALIDO);
        }

        String ticket = generarTicket();
        token.setUsado(true);
        token.setTicketHash(sha256(ticket));
        token.setTicketExpiraEn(ahora.plusMinutes(properties.getTicketMinutos()));
        token.setTicketUsado(false);
        tokenRepository.save(token);

        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        usuarioRepository.save(usuario);

        auditoriaService.registrar(
                usuario.getId(),
                "RECUPERACION_VERIFICADA",
                "usuario",
                usuario.getId(),
                "Código OTP validado correctamente");

        return new RecuperacionVerificadaResponse(
                ticket, properties.getTicketMinutos() * 60L);
    }

    @Transactional(noRollbackFor = BadRequestException.class)
    public void restablecer(String ticket, String password) {
        TokenRecuperacion token = buscarTicket(ticket);
        Usuario usuario = token.getUsuario();
        validarCuentaActiva(usuario);

        usuario.setPasswordHash(passwordEncoder.encode(password));
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        usuarioRepository.save(usuario);

        tokenRepository.invalidarTodos(usuario.getId());

        auditoriaService.registrar(
                usuario.getId(),
                "PASSWORD_RESTABLECIDA",
                "usuario",
                usuario.getId(),
                "Contraseña restablecida mediante OTP");
    }

    @Transactional(noRollbackFor = BadRequestException.class)
    public TokenResponse acceder(String ticket) {
        TokenRecuperacion token = buscarTicket(ticket);
        Usuario usuario = token.getUsuario();
        validarCuentaActiva(usuario);

        TokenResponse tokens = autenticacionService.generarTokens(usuario);

        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        usuarioRepository.save(usuario);

        tokenRepository.invalidarTodos(usuario.getId());

        auditoriaService.registrar(
                usuario.getId(),
                "ACCESO_OTP",
                "usuario",
                usuario.getId(),
                "Inicio de sesión mediante código OTP");

        return tokens;
    }

    private TokenRecuperacion buscarTicket(String ticket) {
        TokenRecuperacion token = tokenRepository
                .findByTicketHashAndTicketUsadoFalse(sha256(ticket))
                .orElseThrow(() -> new BadRequestException(MENSAJE_TICKET_INVALIDO));

        if (token.getTicketExpiraEn() == null
                || token.getTicketExpiraEn().isBefore(LocalDateTime.now())) {
            token.setTicketUsado(true);
            tokenRepository.save(token);
            throw new BadRequestException(MENSAJE_TICKET_INVALIDO);
        }
        return token;
    }

    private void validarCuentaActiva(Usuario usuario) {
        if (usuario.getEstado() != EstadoUsuario.ACTIVO) {
            throw new ForbiddenException("La cuenta no está activa");
        }
    }

    private void validarLimiteIp(String ip) {
        if (!rateLimiter.permitir(
                "solicitar:" + ip,
                properties.getMaxSolicitudesPorIp(),
                Duration.ofMinutes(properties.getVentanaMinutos()))) {
            throw new TooManyRequestsException(
                    "Demasiadas solicitudes. Espere unos minutos e intente nuevamente.");
        }
    }

    private void validarLimiteVerificacion(String ip) {
        if (!rateLimiter.permitir(
                "verificar:" + ip,
                properties.getMaxSolicitudesPorIp() * 2,
                Duration.ofMinutes(properties.getVentanaMinutos()))) {
            throw new TooManyRequestsException(
                    "Demasiados intentos. Espere unos minutos e intente nuevamente.");
        }
    }

    private String normalizarCorreo(String correo) {
        return correo.trim().toLowerCase(Locale.ROOT);
    }

    private String generarCodigo() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String generarTicket() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("No se pudo generar el hash del ticket", ex);
        }
    }
}
