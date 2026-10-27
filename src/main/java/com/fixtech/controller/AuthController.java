package com.fixtech.controller;

import com.fixtech.dto.LoginRequest;
import com.fixtech.dto.PerfilUsuarioResponse;
import com.fixtech.dto.RefreshTokenRequest;
import com.fixtech.dto.RegistroClienteRequest;
import com.fixtech.dto.RegistroTecnicoRequest;
import com.fixtech.dto.TokenResponse;
import com.fixtech.dto.UsuarioRegistradoResponse;
import com.fixtech.exception.UnauthorizedException;
import com.fixtech.service.AutenticacionService;
import com.fixtech.service.PerfilUsuarioService;
import com.fixtech.service.RegistroService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RegistroService registroService;
    private final AutenticacionService autenticacionService;
    private final PerfilUsuarioService perfilUsuarioService;

    @PostMapping("/register/cliente")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioRegistradoResponse registrarCliente(
            @Valid @RequestBody RegistroClienteRequest request) {
        return registroService.registrarCliente(request);
    }

    @PostMapping("/register/tecnico")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioRegistradoResponse registrarTecnico(
            @Valid @RequestBody RegistroTecnicoRequest request) {
        return registroService.registrarTecnico(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return autenticacionService.login(request);
    }

    @PostMapping("/refresh")
    public TokenResponse refrescar(@Valid @RequestBody RefreshTokenRequest request) {
        return autenticacionService.refrescar(request);
    }

    @GetMapping("/me")
    public PerfilUsuarioResponse perfil(@AuthenticationPrincipal Jwt jwt) {
        return perfilUsuarioService.obtener(usuarioId(jwt));
    }

    private Long usuarioId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new UnauthorizedException("Token inválido o expirado");
        }
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException ex) {
            throw new UnauthorizedException("Token inválido o expirado");
        }
    }
}
