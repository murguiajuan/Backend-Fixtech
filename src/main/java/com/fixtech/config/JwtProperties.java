package com.fixtech.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    @NotBlank
    @Size(min = 32, message = "El secreto JWT debe tener al menos 32 caracteres")
    private String secret;

    @NotBlank
    private String issuer = "fixtech";

    @Positive
    private long accessTokenMinutos = 15;

    @Positive
    private long refreshTokenDias = 7;
}
