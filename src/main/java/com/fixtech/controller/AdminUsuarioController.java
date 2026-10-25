package com.fixtech.controller;

import com.fixtech.dto.CambioEstadoUsuarioRequest;
import com.fixtech.dto.UsuarioAdminResponse;
import com.fixtech.entity.enums.EstadoUsuario;
import com.fixtech.entity.enums.Rol;
import com.fixtech.exception.UnauthorizedException;
import com.fixtech.service.AdministradorUsuarioService;
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
@RequestMapping("/api/admin/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUsuarioController {

    private final AdministradorUsuarioService administradorUsuarioService;

    @GetMapping
    public List<UsuarioAdminResponse> listar(
            @RequestParam(required = false) Rol rol,
            @RequestParam(required = false) EstadoUsuario estado,
            @RequestParam(required = false) String busqueda) {
        return administradorUsuarioService.listar(rol, estado, busqueda);
    }

    @PatchMapping("/{id}/estado")
    public UsuarioAdminResponse cambiarEstado(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid CambioEstadoUsuarioRequest request) {
        return administradorUsuarioService.cambiarEstado(usuarioId(jwt), id, request);
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
