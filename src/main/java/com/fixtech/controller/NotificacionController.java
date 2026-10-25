package com.fixtech.controller;

import com.fixtech.dto.NotificacionResponse;
import com.fixtech.dto.NotificacionesResponse;
import com.fixtech.exception.UnauthorizedException;
import com.fixtech.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final NotificacionService notificacionService;

    @GetMapping
    public NotificacionesResponse listar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean soloNoLeidas) {
        return notificacionService.listar(usuarioId(jwt), soloNoLeidas);
    }

    @PatchMapping("/{id}/leer")
    public NotificacionResponse marcarLeida(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return notificacionService.marcarLeida(usuarioId(jwt), id);
    }

    @PatchMapping("/leer-todas")
    public ResponseEntity<Void> marcarTodasLeidas(
            @AuthenticationPrincipal Jwt jwt) {
        notificacionService.marcarTodasLeidas(usuarioId(jwt));
        return ResponseEntity.noContent().build();
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
