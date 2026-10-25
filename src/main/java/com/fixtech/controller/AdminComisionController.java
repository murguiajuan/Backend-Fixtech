package com.fixtech.controller;

import com.fixtech.dto.DeudaTecnicoResponse;
import com.fixtech.dto.PagoComisionResponse;
import com.fixtech.dto.ValidacionPagoComisionRequest;
import com.fixtech.entity.enums.EstadoPagoComision;
import com.fixtech.exception.UnauthorizedException;
import com.fixtech.service.ArchivoService;
import com.fixtech.service.DeudaTecnicoService;
import com.fixtech.service.PagoComisionService;
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
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminComisionController {

    private final PagoComisionService pagoComisionService;
    private final DeudaTecnicoService deudaTecnicoService;

    @GetMapping("/pagos")
    public List<PagoComisionResponse> listarPagos(
            @RequestParam(required = false) EstadoPagoComision estado) {
        return pagoComisionService.listarAdmin(estado);
    }

    @GetMapping("/pagos/{id}")
    public PagoComisionResponse detallePago(@PathVariable Long id) {
        return pagoComisionService.obtenerDetalleAdmin(id);
    }

    @GetMapping("/pagos/{id}/comprobante")
    public ResponseEntity<Resource> comprobante(@PathVariable Long id) {
        return imagen(pagoComisionService.obtenerComprobanteAdmin(id));
    }

    @PatchMapping("/pagos/{id}/validacion")
    public PagoComisionResponse validar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid ValidacionPagoComisionRequest request) {
        return pagoComisionService.validar(usuarioId(jwt), id, request);
    }

    @GetMapping("/comisiones/deudores")
    public List<DeudaTecnicoResponse> listarDeudores() {
        return deudaTecnicoService.listarDeudores();
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
