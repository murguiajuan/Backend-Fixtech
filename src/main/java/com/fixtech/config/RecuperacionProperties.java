package com.fixtech.config;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.recuperacion")
public class RecuperacionProperties {

    @Positive
    private int codigoMinutos = 10;

    @Positive
    private int ticketMinutos = 10;

    @Positive
    private int maxIntentos = 5;

    @Positive
    private int reenvioSegundos = 60;

    @Positive
    private int maxSolicitudesPorCorreo = 3;

    @Positive
    private int maxSolicitudesPorIp = 10;

    @Positive
    private int ventanaMinutos = 15;

    @Positive
    private long limpiezaIntervaloMs = 3_600_000L;
}
