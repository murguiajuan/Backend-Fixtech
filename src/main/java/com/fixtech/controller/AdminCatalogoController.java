package com.fixtech.controller;

import com.fixtech.dto.CatalogoSimpleRequest;
import com.fixtech.dto.CatalogoSimpleResponse;
import com.fixtech.dto.DistritoRequest;
import com.fixtech.dto.DistritoResponse;
import com.fixtech.dto.EspecialidadRequest;
import com.fixtech.dto.EspecialidadResponse;
import com.fixtech.dto.EstadoCatalogoRequest;
import com.fixtech.exception.UnauthorizedException;
import com.fixtech.service.CatalogoService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/catalogos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogoController {

    private final CatalogoService catalogoService;

    @GetMapping("/tipos-equipo")
    public List<CatalogoSimpleResponse> listarTiposEquipo(
            @RequestParam(defaultValue = "true") boolean incluirInactivos) {
        return catalogoService.listarTiposEquipo(!incluirInactivos);
    }

    @PostMapping("/tipos-equipo")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogoSimpleResponse crearTipoEquipo(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid CatalogoSimpleRequest request) {
        return catalogoService.crearTipoEquipo(usuarioId(jwt), request);
    }

    @PutMapping("/tipos-equipo/{id}")
    public CatalogoSimpleResponse actualizarTipoEquipo(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid CatalogoSimpleRequest request) {
        return catalogoService.actualizarTipoEquipo(usuarioId(jwt), id, request);
    }

    @PatchMapping("/tipos-equipo/{id}/estado")
    public CatalogoSimpleResponse cambiarEstadoTipoEquipo(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid EstadoCatalogoRequest request) {
        return catalogoService.cambiarEstadoTipoEquipo(
                usuarioId(jwt), id, request.activo());
    }

    @GetMapping("/tipos-servicio")
    public List<CatalogoSimpleResponse> listarTiposServicio(
            @RequestParam(defaultValue = "true") boolean incluirInactivos) {
        return catalogoService.listarTiposServicio(!incluirInactivos);
    }

    @PostMapping("/tipos-servicio")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogoSimpleResponse crearTipoServicio(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid CatalogoSimpleRequest request) {
        return catalogoService.crearTipoServicio(usuarioId(jwt), request);
    }

    @PutMapping("/tipos-servicio/{id}")
    public CatalogoSimpleResponse actualizarTipoServicio(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid CatalogoSimpleRequest request) {
        return catalogoService.actualizarTipoServicio(usuarioId(jwt), id, request);
    }

    @PatchMapping("/tipos-servicio/{id}/estado")
    public CatalogoSimpleResponse cambiarEstadoTipoServicio(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid EstadoCatalogoRequest request) {
        return catalogoService.cambiarEstadoTipoServicio(
                usuarioId(jwt), id, request.activo());
    }

    @GetMapping("/especialidades")
    public List<EspecialidadResponse> listarEspecialidades(
            @RequestParam(defaultValue = "true") boolean incluirInactivos) {
        return catalogoService.listarEspecialidades(!incluirInactivos);
    }

    @PostMapping("/especialidades")
    @ResponseStatus(HttpStatus.CREATED)
    public EspecialidadResponse crearEspecialidad(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid EspecialidadRequest request) {
        return catalogoService.crearEspecialidad(usuarioId(jwt), request);
    }

    @PutMapping("/especialidades/{id}")
    public EspecialidadResponse actualizarEspecialidad(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid EspecialidadRequest request) {
        return catalogoService.actualizarEspecialidad(usuarioId(jwt), id, request);
    }

    @PatchMapping("/especialidades/{id}/estado")
    public EspecialidadResponse cambiarEstadoEspecialidad(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid EstadoCatalogoRequest request) {
        return catalogoService.cambiarEstadoEspecialidad(
                usuarioId(jwt), id, request.activo());
    }

    @GetMapping("/distritos")
    public List<DistritoResponse> listarDistritos(
            @RequestParam(defaultValue = "true") boolean incluirInactivos) {
        return catalogoService.listarDistritos(!incluirInactivos);
    }

    @PostMapping("/distritos")
    @ResponseStatus(HttpStatus.CREATED)
    public DistritoResponse crearDistrito(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid DistritoRequest request) {
        return catalogoService.crearDistrito(usuarioId(jwt), request);
    }

    @PutMapping("/distritos/{id}")
    public DistritoResponse actualizarDistrito(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid DistritoRequest request) {
        return catalogoService.actualizarDistrito(usuarioId(jwt), id, request);
    }

    @PatchMapping("/distritos/{id}/estado")
    public DistritoResponse cambiarEstadoDistrito(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid EstadoCatalogoRequest request) {
        return catalogoService.cambiarEstadoDistrito(
                usuarioId(jwt), id, request.activo());
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
