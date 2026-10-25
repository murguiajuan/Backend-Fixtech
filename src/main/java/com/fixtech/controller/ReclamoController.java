package com.fixtech.controller;

import com.fixtech.dto.ReclamoResponse;
import com.fixtech.dto.RespuestaReclamoRequest;
import com.fixtech.exception.UnauthorizedException;
import com.fixtech.service.ArchivoService;
import com.fixtech.service.ReclamoService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReclamoController {

    private final ReclamoService reclamoService;

    @PostMapping(value = "/servicios/{id}/reclamos",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<ReclamoResponse> registrar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestParam String descripcion,
            @RequestPart("fotos") List<MultipartFile> fotos) {
        ReclamoResponse response = reclamoService.registrar(
                usuarioId(jwt), id, descripcion, fotos);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/servicios/{id}/reclamos")
    @PreAuthorize("hasAnyRole('CLIENTE','TECNICO')")
    public List<ReclamoResponse> listar(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return reclamoService.listarPorServicio(usuarioId(jwt), id);
    }

    @GetMapping("/servicios/{id}/reclamos/{reclamoId}")
    @PreAuthorize("hasAnyRole('CLIENTE','TECNICO')")
    public ReclamoResponse obtener(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @PathVariable Long reclamoId) {
        return reclamoService.obtenerDetalle(
                usuarioId(jwt), id, reclamoId);
    }

    @GetMapping("/servicios/{id}/reclamos/{reclamoId}/fotos/{fotoId}")
    @PreAuthorize("hasAnyRole('CLIENTE','TECNICO')")
    public ResponseEntity<Resource> foto(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @PathVariable Long reclamoId,
            @PathVariable Long fotoId) {
        return imagen(reclamoService.obtenerFoto(
                usuarioId(jwt), id, reclamoId, fotoId));
    }

    @PatchMapping("/servicios/{id}/reclamos/{reclamoId}/respuesta")
    @PreAuthorize("hasRole('TECNICO')")
    public ReclamoResponse responder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @PathVariable Long reclamoId,
            @RequestBody @Valid RespuestaReclamoRequest request) {
        return reclamoService.responder(
                usuarioId(jwt), id, reclamoId, request);
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
