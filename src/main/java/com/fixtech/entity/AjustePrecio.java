package com.fixtech.entity;

import com.fixtech.entity.enums.EstadoAjuste;
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
@Table(name = "ajuste_precio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AjustePrecio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servicio_id", nullable = false)
    private Servicio servicio;

    @Column(name = "monto_anterior", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoAnterior;

    @Column(name = "monto_nuevo", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoNuevo;

    @Column(nullable = false, length = 500)
    private String justificacion;

    @Column(name = "ruta_evidencia", length = 255)
    private String rutaEvidencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private EstadoAjuste estado = EstadoAjuste.PENDIENTE;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "fecha_respuesta")
    private LocalDateTime fechaRespuesta;
}
