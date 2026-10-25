package com.fixtech.controller;

import com.fixtech.dto.TecnicoVerificacionResponse;
import com.fixtech.dto.VerificacionTecnicoRequest;
import com.fixtech.exception.UnauthorizedException;
import com.fixtech.service.AdministradorTecnicoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tecnicos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminTecnicoController {

    private final AdministradorTecnicoService administradorTecnicoService;

    @PatchMapping("/{id}/verificacion")
    public TecnicoVerificacionResponse verificar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid VerificacionTecnicoRequest request) {
        return administradorTecnicoService.verificar(
                usuarioId(jwt), id, request);
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
