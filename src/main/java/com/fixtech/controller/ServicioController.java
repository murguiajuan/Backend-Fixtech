package com.fixtech.controller;

import com.fixtech.dto.AvanceRequest;
import com.fixtech.dto.AvanceResponse;
import com.fixtech.dto.AjustePrecioResponse;
import com.fixtech.dto.CambioEstadoServicioRequest;
import com.fixtech.dto.ConstanciaRequest;
import com.fixtech.dto.ConstanciaResponse;
import com.fixtech.dto.FinalizarServicioRequest;
import com.fixtech.dto.MensajeRequest;
import com.fixtech.dto.MensajeResponse;
import com.fixtech.dto.ServicioDetalleResponse;
import com.fixtech.dto.ServicioResumenResponse;
import com.fixtech.dto.ServicioResponse;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.TipoAvance;
import com.fixtech.entity.enums.TipoConstancia;
import com.fixtech.exception.UnauthorizedException;
import com.fixtech.service.AjustePrecioService;
import com.fixtech.service.ArchivoService;
import com.fixtech.service.AvanceService;
import com.fixtech.service.ConformidadService;
import com.fixtech.service.ConstanciaService;
import com.fixtech.service.MensajeService;
import com.fixtech.service.ServicioConsultaService;
import com.fixtech.service.ServicioEjecucionService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
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
public class ServicioController {

    private final ServicioConsultaService servicioConsultaService;
    private final ServicioEjecucionService servicioEjecucionService;
    private final ConstanciaService constanciaService;
    private final AvanceService avanceService;
    private final AjustePrecioService ajustePrecioService;
    private final MensajeService mensajeService;
    private final ConformidadService conformidadService;

    @GetMapping("/servicios/mios")
    @PreAuthorize("hasAnyRole('CLIENTE','TECNICO')")
    public List<ServicioResumenResponse> listarMios(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) EstadoServicio estado) {
        return servicioConsultaService.listarMios(usuarioId(jwt), estado);
    }

    @GetMapping("/servicios/{id}")
    @PreAuthorize("hasAnyRole('CLIENTE','TECNICO')")
    public ServicioDetalleResponse detalle(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return servicioConsultaService.obtenerDetalle(usuarioId(jwt), id);
    }

    @PatchMapping("/servicios/{id}/estado")
    @PreAuthorize("hasAnyRole('CLIENTE','TECNICO')")
    public ServicioResponse cambiarEstado(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid CambioEstadoServicioRequest request) {
        return servicioEjecucionService.cambiarEstado(usuarioId(jwt), id, request);
    }

    @PostMapping("/servicios/{id}/finalizar")
    @PreAuthorize("hasRole('TECNICO')")
    public ServicioResponse finalizar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid FinalizarServicioRequest request) {
        return servicioEjecucionService.finalizar(usuarioId(jwt), id, request);
    }

    @PostMapping("/servicios/{id}/conformidad")
    @PreAuthorize("hasRole('CLIENTE')")
    public ServicioResponse confirmar(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return conformidadService.confirmar(usuarioId(jwt), id);
    }

    @PostMapping(value = "/servicios/{id}/constancias",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('CLIENTE','TECNICO')")
    public ResponseEntity<ConstanciaResponse> registrarConstancia(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestParam TipoConstancia tipo,
            @RequestParam(required = false) String observaciones,
            @RequestParam(required = false) String accesorios,
            @RequestPart("fotos") List<MultipartFile> fotos) {
        ConstanciaResponse response = constanciaService.registrar(
                usuarioId(jwt),
                id,
                new ConstanciaRequest(tipo, observaciones, accesorios),
                fotos);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/servicios/{id}/constancias/{constanciaId}/confirmar")
    @PreAuthorize("hasRole('CLIENTE')")
    public ConstanciaResponse confirmarConstancia(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @PathVariable Long constanciaId) {
        return constanciaService.confirmar(usuarioId(jwt), id, constanciaId);
    }

    @GetMapping("/servicios/{id}/constancias/{constanciaId}/fotos/{fotoId}")
    @PreAuthorize("hasAnyRole('CLIENTE','TECNICO')")
    public ResponseEntity<Resource> fotoConstancia(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @PathVariable Long constanciaId,
            @PathVariable Long fotoId) {
        return imagen(constanciaService.obtenerFoto(
                usuarioId(jwt), id, constanciaId, fotoId));
    }

    @GetMapping("/servicios/{id}/avances")
    @PreAuthorize("hasAnyRole('CLIENTE','TECNICO')")
    public List<AvanceResponse> listarAvances(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return avanceService.listar(usuarioId(jwt), id);
    }

    @PostMapping(value = "/servicios/{id}/avances",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TECNICO')")
    public ResponseEntity<AvanceResponse> registrarAvance(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestParam TipoAvance tipo,
            @RequestParam String descripcion,
            @RequestPart(value = "foto", required = false) MultipartFile foto) {
        AvanceResponse response = avanceService.registrar(
                usuarioId(jwt), id, new AvanceRequest(tipo, descripcion), foto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/servicios/{id}/avances/{avanceId}/foto")
    @PreAuthorize("hasAnyRole('CLIENTE','TECNICO')")
    public ResponseEntity<Resource> fotoAvance(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @PathVariable Long avanceId) {
        return imagen(avanceService.obtenerFoto(usuarioId(jwt), id, avanceId));
    }

    @GetMapping("/servicios/{id}/mensajes")
    @PreAuthorize("hasAnyRole('CLIENTE','TECNICO')")
    public List<MensajeResponse> listarMensajes(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return mensajeService.listar(usuarioId(jwt), id);
    }

    @PostMapping("/servicios/{id}/mensajes")
    @PreAuthorize("hasAnyRole('CLIENTE','TECNICO')")
    public ResponseEntity<MensajeResponse> enviarMensaje(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid MensajeRequest request) {
        MensajeResponse response = mensajeService.enviar(usuarioId(jwt), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/servicios/{id}/ajuste-precio",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TECNICO')")
    public ResponseEntity<AjustePrecioResponse> proponerAjuste(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestParam BigDecimal montoNuevo,
            @RequestParam String justificacion,
            @RequestPart(value = "evidencia", required = false) MultipartFile evidencia) {
        AjustePrecioResponse response = ajustePrecioService.proponer(
                usuarioId(jwt), id, montoNuevo, justificacion, evidencia);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
