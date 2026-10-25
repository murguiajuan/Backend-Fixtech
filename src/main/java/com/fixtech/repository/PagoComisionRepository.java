package com.fixtech.repository;

import com.fixtech.entity.PagoComision;
import com.fixtech.entity.enums.EstadoPagoComision;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PagoComisionRepository extends JpaRepository<PagoComision, Long> {

    @EntityGraph(attributePaths = {
        "tecnico", "tecnico.usuario", "detalles", "detalles.comision",
        "detalles.comision.servicio"
    })
    List<PagoComision> findByTecnicoIdOrderByCreadoEnDescIdDesc(Long tecnicoId);

    @EntityGraph(attributePaths = {
        "tecnico", "tecnico.usuario", "detalles", "detalles.comision",
        "detalles.comision.servicio"
    })
    List<PagoComision> findByTecnicoIdAndEstadoOrderByCreadoEnDescIdDesc(
            Long tecnicoId, EstadoPagoComision estado);

    @EntityGraph(attributePaths = {
        "tecnico", "tecnico.usuario", "detalles", "detalles.comision",
        "detalles.comision.servicio"
    })
    List<PagoComision> findByEstadoOrderByCreadoEnAscIdAsc(EstadoPagoComision estado);

    @EntityGraph(attributePaths = {
        "tecnico", "tecnico.usuario", "detalles", "detalles.comision",
        "detalles.comision.servicio"
    })
    List<PagoComision> findAllByOrderByCreadoEnDescIdDesc();

    @EntityGraph(attributePaths = {
        "tecnico", "tecnico.usuario", "detalles", "detalles.comision",
        "detalles.comision.servicio"
    })
    @Query("select p from PagoComision p where p.id = :id")
    Optional<PagoComision> buscarDetallePorId(@Param("id") Long id);

    @Query(value = "SELECT * FROM pago_comision WHERE id = :id FOR UPDATE",
            nativeQuery = true)
    Optional<PagoComision> findByIdForUpdate(@Param("id") Long id);

    boolean existsByTecnicoIdAndNumeroOperacionAndEstadoIn(
            Long tecnicoId, String numeroOperacion, Collection<EstadoPagoComision> estados);

    Optional<PagoComision> findByIdAndTecnicoId(Long id, Long tecnicoId);

    long countByEstado(EstadoPagoComision estado);
}
