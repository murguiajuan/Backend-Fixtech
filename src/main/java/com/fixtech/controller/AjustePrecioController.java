package com.fixtech.controller;

import com.fixtech.dto.AjustePrecioResponse;
import com.fixtech.dto.RespuestaAjusteRequest;
import com.fixtech.exception.UnauthorizedException;
import com.fixtech.service.AjustePrecioService;
import com.fixtech.service.ArchivoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ajustes")
@RequiredArgsConstructor
public class AjustePrecioController {

    private final AjustePrecioService ajustePrecioService;

    @PatchMapping("/{id}/respuesta")
    @PreAuthorize("hasRole('CLIENTE')")
    public AjustePrecioResponse responder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid RespuestaAjusteRequest request) {
        return ajustePrecioService.responder(usuarioId(jwt), id, request);
    }

    @GetMapping("/{id}/evidencia")
    @PreAuthorize("hasAnyRole('CLIENTE','TECNICO')")
    public ResponseEntity<Resource> evidencia(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        ArchivoService.RecursoImagen imagen = ajustePrecioService.obtenerEvidencia(
                usuarioId(jwt), id);
        return ResponseEntity.ok()
                .contentType(imagen.tipoContenido())
                .body(imagen.recurso());
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
