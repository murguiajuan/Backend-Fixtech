package com.fixtech.controller;

import com.fixtech.dto.ModeracionResenaRequest;
import com.fixtech.dto.ResenaResponse;
import com.fixtech.entity.enums.EstadoResena;
import com.fixtech.exception.UnauthorizedException;
import com.fixtech.service.ResenaService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/resenas")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminResenaController {

    private final ResenaService resenaService;

    @GetMapping
    public List<ResenaResponse> listar(
            @RequestParam(required = false) EstadoResena estado) {
        return resenaService.listarAdmin(estado);
    }

    @PatchMapping("/{id}/moderacion")
    public ResenaResponse moderar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid ModeracionResenaRequest request) {
        return resenaService.moderar(usuarioId(jwt), id, request);
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
