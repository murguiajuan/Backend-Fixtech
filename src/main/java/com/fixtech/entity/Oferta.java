package com.fixtech.entity;

import com.fixtech.entity.enums.EstadoOferta;
import com.fixtech.entity.enums.Modalidad;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "oferta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Oferta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitud_id", nullable = false)
    private Solicitud solicitud;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tecnico_id", nullable = false)
    private Tecnico tecnico;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Modalidad modalidad;

    @Column(name = "precio_servicio", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioServicio;

    @Column(name = "costo_movilidad", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal costoMovilidad = BigDecimal.ZERO;

    @Column(name = "costo_diagnostico", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal costoDiagnostico = BigDecimal.ZERO;

    @Column(name = "tiempo_estimado_horas", nullable = false)
    private int tiempoEstimadoHoras;

    @Column(name = "garantia_dias", nullable = false)
    @Builder.Default
    private int garantiaDias = 7;

    @Column(length = 500)
    private String mensaje;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 14)
    @Builder.Default
    private EstadoOferta estado = EstadoOferta.ENVIADA;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime actualizadoEn;

    @OneToMany(mappedBy = "oferta", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("ronda ASC, id ASC")
    @Builder.Default
    private List<Contraoferta> contraofertas = new ArrayList<>();

    @OneToOne(mappedBy = "oferta", fetch = FetchType.LAZY)
    private Servicio servicio;
}
