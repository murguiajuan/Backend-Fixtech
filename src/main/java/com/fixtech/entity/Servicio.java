package com.fixtech.entity;

import com.fixtech.entity.enums.CanceladoPor;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.MetodoPago;
import com.fixtech.entity.enums.Modalidad;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "servicio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Servicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitud_id", nullable = false, unique = true)
    private Solicitud solicitud;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "oferta_id", nullable = false, unique = true)
    private Oferta oferta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tecnico_id", nullable = false)
    private Tecnico tecnico;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Modalidad modalidad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoServicio estado = EstadoServicio.PROGRAMADO;

    @Column(name = "precio_servicio", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioServicio;

    @Column(name = "costo_movilidad", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal costoMovilidad = BigDecimal.ZERO;

    @Column(name = "costo_diagnostico", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal costoDiagnostico = BigDecimal.ZERO;

    @Column(name = "monto_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoTotal;

    @Column(name = "monto_final", precision = 10, scale = 2)
    private BigDecimal montoFinal;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", length = 13)
    private MetodoPago metodoPago;

    @Column(name = "pin_inicio", length = 4)
    private String pinInicio;

    @Column(name = "pin_validado", nullable = false)
    @Builder.Default
    private boolean pinValidado = false;

    @Column(name = "garantia_dias", nullable = false)
    @Builder.Default
    private int garantiaDias = 7;

    @Column(name = "fecha_programada")
    private LocalDateTime fechaProgramada;

    @Column(name = "fecha_inicio")
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_finalizacion")
    private LocalDateTime fechaFinalizacion;

    @Column(name = "fecha_limite_conformidad")
    private LocalDateTime fechaLimiteConformidad;

    @Column(name = "fecha_confirmacion")
    private LocalDateTime fechaConfirmacion;

    @Column(name = "confirmado_automaticamente", nullable = false)
    @Builder.Default
    private boolean confirmadoAutomaticamente = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelado_por", length = 7)
    private CanceladoPor canceladoPor;

    @Column(name = "motivo_cancelacion", length = 300)
    private String motivoCancelacion;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime actualizadoEn;
}
