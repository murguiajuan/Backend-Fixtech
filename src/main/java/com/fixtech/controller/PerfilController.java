package com.fixtech.controller;

import com.fixtech.dto.PerfilTecnicoRequest;
import com.fixtech.dto.PerfilUsuarioResponse;
import com.fixtech.exception.UnauthorizedException;
import com.fixtech.service.ArchivoService;
import com.fixtech.service.PerfilTecnicoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PerfilController {

    private final PerfilTecnicoService perfilTecnicoService;

    @PutMapping("/perfil/tecnico")
    @PreAuthorize("hasRole('TECNICO')")
    public PerfilUsuarioResponse actualizarPerfilTecnico(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid PerfilTecnicoRequest request) {
        return perfilTecnicoService.actualizar(usuarioId(jwt), request);
    }

    @PostMapping(value = "/perfil/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TECNICO')")
    public PerfilUsuarioResponse actualizarFoto(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("foto") MultipartFile foto) {
        return perfilTecnicoService.actualizarFoto(usuarioId(jwt), foto);
    }

    @GetMapping("/tecnicos/{id}/foto")
    @PreAuthorize("hasAnyRole('CLIENTE','TECNICO','ADMIN')")
    public ResponseEntity<Resource> obtenerFoto(@PathVariable Long id) {
        ArchivoService.RecursoImagen foto = perfilTecnicoService.obtenerFoto(id);
        return ResponseEntity.ok()
                .contentType(foto.tipoContenido())
                .body(foto.recurso());
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
