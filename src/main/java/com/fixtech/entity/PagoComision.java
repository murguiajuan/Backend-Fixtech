package com.fixtech.entity;

import com.fixtech.entity.enums.EstadoPagoComision;
import com.fixtech.entity.enums.MetodoPago;
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
@Table(name = "pago_comision")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagoComision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tecnico_id", nullable = false)
    private Tecnico tecnico;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 13)
    private MetodoPago metodo;

    @Column(name = "numero_operacion", nullable = false, length = 40)
    private String numeroOperacion;

    @Column(name = "ruta_comprobante", nullable = false, length = 255)
    private String rutaComprobante;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private EstadoPagoComision estado = EstadoPagoComision.PENDIENTE;

    @Column(name = "validado_por")
    private Long validadoPorId;

    @Column(name = "fecha_validacion")
    private LocalDateTime fechaValidacion;

    @Column(length = 300)
    private String observacion;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @OneToMany(mappedBy = "pago", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PagoComisionDetalle> detalles = new ArrayList<>();

    public void agregarComision(Comision comision) {
        PagoComisionDetalle detalle = new PagoComisionDetalle();
        detalle.setPago(this);
        detalle.setComision(comision);
        detalles.add(detalle);
    }
}
