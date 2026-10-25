package com.fixtech.controller;

import com.fixtech.dto.ParametroResponse;
import com.fixtech.dto.ParametrosUpdateRequest;
import com.fixtech.exception.UnauthorizedException;
import com.fixtech.service.AdministradorParametroService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/parametros")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminParametroController {

    private final AdministradorParametroService administradorParametroService;

    @GetMapping
    public List<ParametroResponse> listar() {
        return administradorParametroService.listar();
    }

    @PutMapping
    public List<ParametroResponse> actualizar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid ParametrosUpdateRequest request) {
        return administradorParametroService.actualizar(usuarioId(jwt), request);
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
