package com.fixtech.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.archivos")
public class ArchivoProperties {

    @NotBlank
    private String directorio = "uploads";

    @Positive
    private long maxImagenBytes = 5L * 1024 * 1024;

    @Positive
    private int maxDimension = 1600;

    @DecimalMin("0.1")
    @DecimalMax("1.0")
    private double calidadJpeg = 0.8;
}
