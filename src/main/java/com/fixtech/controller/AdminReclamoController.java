package com.fixtech.controller;

import com.fixtech.dto.CambioEstadoReclamoRequest;
import com.fixtech.dto.ReclamoResponse;
import com.fixtech.entity.enums.EstadoReclamo;
import com.fixtech.exception.UnauthorizedException;
import com.fixtech.service.ArchivoService;
import com.fixtech.service.ReclamoService;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reclamos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReclamoController {

    private final ReclamoService reclamoService;

    @GetMapping
    public List<ReclamoResponse> listar(
            @RequestParam(required = false) EstadoReclamo estado) {
        return reclamoService.listarAdmin(estado);
    }

    @GetMapping("/{id}")
    public ReclamoResponse obtener(@PathVariable Long id) {
        return reclamoService.obtenerDetalleAdmin(id);
    }

    @GetMapping("/{id}/fotos/{fotoId}")
    public ResponseEntity<Resource> foto(
            @PathVariable Long id, @PathVariable Long fotoId) {
        return imagen(reclamoService.obtenerFotoAdmin(id, fotoId));
    }

    @PatchMapping("/{id}/estado")
    public ReclamoResponse cambiarEstado(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid CambioEstadoReclamoRequest request) {
        return reclamoService.cambiarEstado(usuarioId(jwt), id, request);
    }

    private ResponseEntity<Resource> imagen(ArchivoService.RecursoImagen imagen) {
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
