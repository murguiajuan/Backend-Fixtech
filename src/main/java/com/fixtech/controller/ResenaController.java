package com.fixtech.controller;

import com.fixtech.dto.ModeracionResenaRequest;
import com.fixtech.dto.ReporteResenaRequest;
import com.fixtech.dto.ResenaRequest;
import com.fixtech.dto.ResenaResponse;
import com.fixtech.dto.RespuestaResenaRequest;
import com.fixtech.entity.enums.EstadoResena;
import com.fixtech.exception.UnauthorizedException;
import com.fixtech.service.ResenaService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ResenaController {

    private final ResenaService resenaService;

    @PostMapping("/servicios/{id}/resenas")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<ResenaResponse> registrar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid ResenaRequest request) {
        ResenaResponse response = resenaService.registrar(
                usuarioId(jwt), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/servicios/{id}/resenas")
    @PreAuthorize("hasAnyRole('CLIENTE','TECNICO')")
    public ResenaResponse obtener(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return resenaService.obtenerPorServicio(usuarioId(jwt), id);
    }

    @PatchMapping("/servicios/{id}/resenas/{resenaId}/respuesta")
    @PreAuthorize("hasRole('TECNICO')")
    public ResenaResponse responder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @PathVariable Long resenaId,
            @RequestBody @Valid RespuestaResenaRequest request) {
        return resenaService.responder(
                usuarioId(jwt), id, resenaId, request);
    }

    @PostMapping("/servicios/{id}/resenas/{resenaId}/reporte")
    @PreAuthorize("hasRole('TECNICO')")
    public ResenaResponse reportar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @PathVariable Long resenaId,
            @RequestBody @Valid ReporteResenaRequest request) {
        return resenaService.reportar(
                usuarioId(jwt), id, resenaId, request);
    }

    @GetMapping("/tecnicos/{tecnicoId}/resenas")
    public List<ResenaResponse> listarPorTecnico(@PathVariable Long tecnicoId) {
        return resenaService.listarPorTecnico(tecnicoId);
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
