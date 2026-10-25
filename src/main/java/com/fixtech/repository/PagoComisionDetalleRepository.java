package com.fixtech.repository;

import com.fixtech.entity.PagoComisionDetalle;
import com.fixtech.entity.PagoComisionDetalleId;
import com.fixtech.entity.enums.EstadoPagoComision;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PagoComisionDetalleRepository
        extends JpaRepository<PagoComisionDetalle, PagoComisionDetalleId> {

    @Query("""
            select d.comision.id
            from PagoComisionDetalle d
            where d.comision.id in :comisionIds
              and d.pago.estado in :estados
            """)
    List<Long> buscarComisionesConPagoActivo(
            @Param("comisionIds") Collection<Long> comisionIds,
            @Param("estados") Collection<EstadoPagoComision> estados);
}
