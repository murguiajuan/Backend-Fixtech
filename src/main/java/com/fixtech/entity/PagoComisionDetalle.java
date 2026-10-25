package com.fixtech.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pago_comision_detalle")
@Getter
@Setter
@NoArgsConstructor
public class PagoComisionDetalle {

    @EmbeddedId
    private PagoComisionDetalleId id = new PagoComisionDetalleId();

    @MapsId("pagoId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pago_id", nullable = false)
    private PagoComision pago;

    @MapsId("comisionId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comision_id", nullable = false)
    private Comision comision;
}
