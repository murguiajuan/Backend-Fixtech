package com.fixtech.controller;

import com.fixtech.dto.ContraofertaRequest;
import com.fixtech.dto.OfertaOrden;
import com.fixtech.dto.OfertaRequest;
import com.fixtech.dto.OfertaResponse;
import com.fixtech.dto.RespuestaNegociacionResponse;
import com.fixtech.dto.ServicioResponse;
import com.fixtech.exception.UnauthorizedException;
import com.fixtech.service.NegociacionService;
import com.fixtech.service.OfertaService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OfertaController {

    private final OfertaService ofertaService;
    private final NegociacionService negociacionService;

    @PostMapping("/solicitudes/{solicitudId}/ofertas")
    @PreAuthorize("hasRole('TECNICO')")
    public ResponseEntity<OfertaResponse> crear(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long solicitudId,
            @RequestBody @Valid OfertaRequest request) {
        OfertaResponse response = ofertaService.crear(
                usuarioId(jwt), solicitudId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/solicitudes/{solicitudId}/ofertas")
    @PreAuthorize("hasRole('CLIENTE')")
    public List<OfertaResponse> listarDelCliente(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long solicitudId,
            @RequestParam(defaultValue = "PRECIO") OfertaOrden orden) {
        return ofertaService.listarDelCliente(usuarioId(jwt), solicitudId, orden);
    }

    @GetMapping("/solicitudes/{solicitudId}/ofertas/comparar")
    @PreAuthorize("hasRole('CLIENTE')")
    public List<OfertaResponse> comparar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long solicitudId,
            @RequestParam(required = false) List<Long> ids) {
        return ofertaService.comparar(usuarioId(jwt), solicitudId, ids);
    }

    @GetMapping("/ofertas/mias")
    @PreAuthorize("hasRole('TECNICO')")
    public List<OfertaResponse> listarMias(@AuthenticationPrincipal Jwt jwt) {
        return ofertaService.listarMias(usuarioId(jwt));
    }

    @PutMapping("/ofertas/{id}")
    @PreAuthorize("hasRole('TECNICO')")
    public OfertaResponse actualizar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid OfertaRequest request) {
        return ofertaService.actualizar(usuarioId(jwt), id, request);
    }

    @DeleteMapping("/ofertas/{id}")
    @PreAuthorize("hasRole('TECNICO')")
    public OfertaResponse retirar(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ofertaService.retirar(usuarioId(jwt), id);
    }

    @PostMapping("/ofertas/{id}/contraoferta")
    @PreAuthorize("hasAnyRole('CLIENTE','TECNICO')")
    public RespuestaNegociacionResponse responder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid ContraofertaRequest request) {
        return negociacionService.responder(usuarioId(jwt), id, request);
    }

    @PostMapping("/ofertas/{id}/aceptar")
    @PreAuthorize("hasRole('CLIENTE')")
    public ServicioResponse aceptar(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return negociacionService.aceptar(usuarioId(jwt), id);
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
