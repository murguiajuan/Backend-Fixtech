package com.fixtech.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PagoComisionDetalleId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "pago_id", nullable = false)
    private Long pagoId;

    @Column(name = "comision_id", nullable = false)
    private Long comisionId;
}
