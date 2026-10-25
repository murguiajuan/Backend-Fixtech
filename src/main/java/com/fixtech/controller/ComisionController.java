package com.fixtech.controller;

import com.fixtech.dto.MiSaldoComisionesResponse;
import com.fixtech.dto.PagoComisionResponse;
import com.fixtech.entity.enums.EstadoPagoComision;
import com.fixtech.entity.enums.MetodoPago;
import com.fixtech.exception.UnauthorizedException;
import com.fixtech.service.ArchivoService;
import com.fixtech.service.ComisionConsultaService;
import com.fixtech.service.PagoComisionService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/comisiones")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TECNICO')")
public class ComisionController {

    private final ComisionConsultaService comisionConsultaService;
    private final PagoComisionService pagoComisionService;

    @GetMapping("/mi-saldo")
    public MiSaldoComisionesResponse miSaldo(@AuthenticationPrincipal Jwt jwt) {
        return comisionConsultaService.obtenerMiSaldo(usuarioId(jwt));
    }

    @GetMapping("/pagos")
    public List<PagoComisionResponse> listarPagos(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) EstadoPagoComision estado) {
        return pagoComisionService.listarMios(usuarioId(jwt), estado);
    }

    @PostMapping(value = "/pagos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PagoComisionResponse> registrarPago(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam List<Long> comisionIds,
            @RequestParam MetodoPago metodo,
            @RequestParam String numeroOperacion,
            @RequestPart("comprobante") MultipartFile comprobante) {
        PagoComisionResponse response = pagoComisionService.registrar(
                usuarioId(jwt), comisionIds, metodo, numeroOperacion, comprobante);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/pagos/{id}/comprobante")
    public ResponseEntity<Resource> comprobante(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return imagen(pagoComisionService.obtenerComprobanteTecnico(
                usuarioId(jwt), id));
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
