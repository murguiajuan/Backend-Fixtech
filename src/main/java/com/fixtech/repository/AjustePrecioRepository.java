package com.fixtech.repository;

import com.fixtech.entity.AjustePrecio;
import com.fixtech.entity.enums.EstadoAjuste;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AjustePrecioRepository extends JpaRepository<AjustePrecio, Long> {

    List<AjustePrecio> findByServicioIdOrderByCreadoEnDesc(Long servicioId);

    Optional<AjustePrecio> findTopByServicioIdAndEstadoOrderByCreadoEnDesc(
            Long servicioId, EstadoAjuste estado);

    boolean existsByServicioIdAndEstado(Long servicioId, EstadoAjuste estado);

    @Query(value = "SELECT * FROM ajuste_precio WHERE id = :ajusteId FOR UPDATE",
            nativeQuery = true)
    Optional<AjustePrecio> findByIdForUpdate(@Param("ajusteId") Long ajusteId);
}
