package com.fixtech.controller;

import com.fixtech.dto.SolicitudDisponibleResponse;
import com.fixtech.dto.SolicitudRequest;
import com.fixtech.dto.SolicitudResponse;
import com.fixtech.dto.SolicitudResumenResponse;
import com.fixtech.entity.enums.EstadoSolicitud;
import com.fixtech.exception.UnauthorizedException;
import com.fixtech.service.ArchivoService;
import com.fixtech.service.SolicitudService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/solicitudes")
@RequiredArgsConstructor
public class SolicitudController {

    private final SolicitudService solicitudService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<SolicitudResponse> crear(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("datos") @Valid SolicitudRequest datos,
            @RequestPart("fotos") List<MultipartFile> fotos) {
        SolicitudResponse response = solicitudService.crear(usuarioId(jwt), datos, fotos);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mias")
    @PreAuthorize("hasRole('CLIENTE')")
    public List<SolicitudResumenResponse> listarMias(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) EstadoSolicitud estado) {
        return solicitudService.listarMias(usuarioId(jwt), estado);
    }

    @GetMapping("/disponibles")
    @PreAuthorize("hasRole('TECNICO')")
    public List<SolicitudDisponibleResponse> listarDisponibles(
            @AuthenticationPrincipal Jwt jwt) {
        return solicitudService.listarDisponibles(usuarioId(jwt));
    }

    @GetMapping("/disponibles/{id}")
    @PreAuthorize("hasRole('TECNICO')")
    public SolicitudDisponibleResponse obtenerDisponible(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return solicitudService.obtenerDisponible(usuarioId(jwt), id);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CLIENTE')")
    public SolicitudResponse obtenerMia(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return solicitudService.obtenerMia(usuarioId(jwt), id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CLIENTE')")
    public SolicitudResponse actualizar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid SolicitudRequest datos) {
        return solicitudService.actualizar(usuarioId(jwt), id, datos);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CLIENTE')")
    public SolicitudResponse cancelar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestParam(required = false) String motivo) {
        return solicitudService.cancelar(usuarioId(jwt), id, motivo);
    }

    @GetMapping("/{id}/imagenes/{imagenId}")
    @PreAuthorize("hasAnyRole('CLIENTE','TECNICO')")
    public ResponseEntity<Resource> obtenerImagen(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @PathVariable Long imagenId) {
        ArchivoService.RecursoImagen imagen =
                solicitudService.obtenerImagen(usuarioId(jwt), id, imagenId);
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
