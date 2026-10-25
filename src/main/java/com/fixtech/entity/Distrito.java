package com.fixtech.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "distrito")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Distrito {

    private static final String PROVINCIA_POR_DEFECTO = "Piura";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String nombre;

    @Column(nullable = false, length = 60)
    @Builder.Default
    private String provincia = PROVINCIA_POR_DEFECTO;

    @Column(name = "tope_movilidad", nullable = false, precision = 8, scale = 2)
    private BigDecimal topeMovilidad;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}
