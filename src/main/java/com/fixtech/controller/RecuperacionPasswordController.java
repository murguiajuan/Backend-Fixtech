package com.fixtech.controller;

import com.fixtech.dto.AccesoRecuperacionRequest;
import com.fixtech.dto.MensajeGenericoResponse;
import com.fixtech.dto.RecuperacionVerificadaResponse;
import com.fixtech.dto.RestablecerPasswordRequest;
import com.fixtech.dto.SolicitudRecuperacionRequest;
import com.fixtech.dto.TokenResponse;
import com.fixtech.dto.VerificacionRecuperacionRequest;
import com.fixtech.service.RecuperacionPasswordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/recuperacion")
@RequiredArgsConstructor
public class RecuperacionPasswordController {

    private static final String MENSAJE_SOLICITUD =
            "Si el correo está registrado, recibirás un código de recuperación.";

    private final RecuperacionPasswordService recuperacionPasswordService;

    @PostMapping("/solicitar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MensajeGenericoResponse solicitar(
            @Valid @RequestBody SolicitudRecuperacionRequest request,
            HttpServletRequest httpRequest) {
        recuperacionPasswordService.solicitar(request.correo(), ip(httpRequest));
        return new MensajeGenericoResponse(MENSAJE_SOLICITUD);
    }

    @PostMapping("/verificar")
    public RecuperacionVerificadaResponse verificar(
            @Valid @RequestBody VerificacionRecuperacionRequest request,
            HttpServletRequest httpRequest) {
        return recuperacionPasswordService.verificar(
                request.correo(), request.codigo(), ip(httpRequest));
    }

    @PostMapping("/restablecer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restablecer(@Valid @RequestBody RestablecerPasswordRequest request) {
        recuperacionPasswordService.restablecer(request.ticket(), request.password());
    }

    @PostMapping("/acceder")
    public TokenResponse acceder(@Valid @RequestBody AccesoRecuperacionRequest request) {
        return recuperacionPasswordService.acceder(request.ticket());
    }

    private String ip(HttpServletRequest request) {
        String reenviada = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(reenviada)) {
            return reenviada.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
