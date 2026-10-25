package com.fixtech.entity;

import com.fixtech.entity.enums.EmisorContraoferta;
import com.fixtech.entity.enums.EstadoContraoferta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "contraoferta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contraoferta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "oferta_id", nullable = false)
    private Oferta oferta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 7)
    private EmisorContraoferta emisor;

    @Column(nullable = false)
    private int ronda;

    @Column(name = "monto_propuesto", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoPropuesto;

    @Column(length = 300)
    private String mensaje;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private EstadoContraoferta estado = EstadoContraoferta.PENDIENTE;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;
}
