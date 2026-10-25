package com.fixtech.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.notificaciones.correo")
public class CorreoProperties {

    private boolean habilitado = false;

    @NotBlank
    private String remitente = "no-reply@fixtech.pe";
}
