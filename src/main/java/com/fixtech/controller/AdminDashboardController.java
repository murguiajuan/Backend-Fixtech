package com.fixtech.controller;

import com.fixtech.dto.DashboardResponse;
import com.fixtech.dto.ReporteResponse;
import com.fixtech.exception.UnauthorizedException;
import com.fixtech.service.AdministradorDashboardService;
import com.fixtech.service.AdministradorReporteService;
import com.fixtech.service.ReporteExcelService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdministradorDashboardService administradorDashboardService;
    private final AdministradorReporteService administradorReporteService;
    private final ReporteExcelService reporteExcelService;

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@AuthenticationPrincipal Jwt jwt) {
        return administradorDashboardService.obtener(usuarioId(jwt));
    }

    @GetMapping("/reportes")
    public ReporteResponse reporte(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return administradorReporteService.obtener(usuarioId(jwt), desde, hasta);
    }

    @GetMapping("/reportes/excel")
    public ResponseEntity<byte[]> exportar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        ReporteResponse reporte = administradorReporteService.obtener(
                usuarioId(jwt), desde, hasta);
        byte[] archivo = reporteExcelService.generar(reporte);
        String nombre = "fixtech-reporte-%s-%s.xlsx".formatted(desde, hasta);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        ReporteExcelService.TIPO_CONTENIDO))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nombre).build().toString())
                .body(archivo);
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
