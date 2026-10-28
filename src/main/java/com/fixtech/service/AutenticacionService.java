package com.fixtech.service;

import com.fixtech.dto.LoginRequest;
import com.fixtech.dto.RefreshTokenRequest;
import com.fixtech.dto.TokenResponse;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.EstadoUsuario;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.UnauthorizedException;
import com.fixtech.repository.UsuarioRepository;
import com.fixtech.security.JwtService;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AutenticacionService {

    private static final int MAX_INTENTOS_FALLIDOS = 5;
    private static final long MINUTOS_BLOQUEO = 15;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public TokenResponse login(LoginRequest request) {
        String correo = request.correo().trim().toLowerCase(Locale.ROOT);
        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new UnauthorizedException("Credenciales inválidas"));

        validarBloqueo(usuario);

        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            registrarIntentoFallido(usuario);
            throw new UnauthorizedException("Credenciales inválidas");
        }

        if (usuario.getEstado() != EstadoUsuario.ACTIVO) {
            throw new ForbiddenException("La cuenta no está activa");
        }

        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        return generarTokens(usuario);
    }

    @Transactional(readOnly = true)
    public TokenResponse refrescar(RefreshTokenRequest request) {
        Jwt jwt = jwtService.decodificarRefresh(request.refreshToken());
        Long usuarioId = idDeToken(jwt);

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UnauthorizedException("Token inválido o expirado"));

        if (usuario.getEstado() != EstadoUsuario.ACTIVO) {
            throw new ForbiddenException("La cuenta no está activa");
        }
        if (usuario.getBloqueadoHasta() != null
                && usuario.getBloqueadoHasta().isAfter(LocalDateTime.now())) {
            throw new ForbiddenException("La cuenta está bloqueada temporalmente. Intente más tarde.");
        }

        return generarTokens(usuario);
    }

    private void validarBloqueo(Usuario usuario) {
        if (usuario.getBloqueadoHasta() == null) {
            return;
        }
        if (usuario.getBloqueadoHasta().isAfter(LocalDateTime.now())) {
            throw new ForbiddenException("La cuenta está bloqueada temporalmente. Intente más tarde.");
        }
        usuario.setBloqueadoHasta(null);
        usuario.setIntentosFallidos(0);
    }

    private void registrarIntentoFallido(Usuario usuario) {
        int intentos = usuario.getIntentosFallidos() + 1;
        usuario.setIntentosFallidos(intentos);
        if (intentos >= MAX_INTENTOS_FALLIDOS) {
            usuario.setBloqueadoHasta(LocalDateTime.now().plusMinutes(MINUTOS_BLOQUEO));
        }
        usuarioRepository.save(usuario);
    }

    public TokenResponse generarTokens(Usuario usuario) {
        return TokenResponse.bearer(
                jwtService.generarAccessToken(usuario),
                jwtService.generarRefreshToken(usuario),
                jwtService.expiracionAccessSegundos());
    }

    private Long idDeToken(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException ex) {
            throw new UnauthorizedException("Token inválido o expirado");
        }
    }
}
