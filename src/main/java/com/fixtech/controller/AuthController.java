package com.fixtech.controller;

import com.fixtech.dto.LoginRequest;
import com.fixtech.dto.RefreshTokenRequest;
import com.fixtech.dto.RegistroClienteRequest;
import com.fixtech.dto.RegistroTecnicoRequest;
import com.fixtech.dto.TokenResponse;
import com.fixtech.dto.UsuarioRegistradoResponse;
import com.fixtech.service.AutenticacionService;
import com.fixtech.service.RegistroService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
}
