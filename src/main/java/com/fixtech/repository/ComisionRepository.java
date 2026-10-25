package com.fixtech.repository;

import com.fixtech.entity.Comision;
import com.fixtech.entity.enums.EstadoComision;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComisionRepository extends JpaRepository<Comision, Long> {

    Optional<Comision> findByServicioId(Long servicioId);

    boolean existsByServicioId(Long servicioId);

    @Query(value = "SELECT * FROM comision WHERE id IN (:ids) ORDER BY id FOR UPDATE",
            nativeQuery = true)
    List<Comision> findByIdsForUpdate(@Param("ids") Collection<Long> ids);

    @EntityGraph(attributePaths = {"servicio"})
    List<Comision> findByTecnicoIdAndEstadoOrderByFechaLimitePagoAscIdAsc(
            Long tecnicoId, EstadoComision estado);

    @Query("""
            select coalesce(sum(c.montoComision), 0)
            from Comision c
            where c.tecnico.id = :tecnicoId and c.estado = :estado
            """)
    BigDecimal sumarMontoPorTecnicoYEstado(
            @Param("tecnicoId") Long tecnicoId,
            @Param("estado") EstadoComision estado);

    long countByTecnicoIdAndEstadoAndFechaLimitePagoBefore(
            Long tecnicoId, EstadoComision estado, LocalDate fecha);

    @Query("select distinct c.tecnico.id from Comision c where c.estado = :estado")
    List<Long> buscarTecnicoIdsPorEstado(@Param("estado") EstadoComision estado);

    @EntityGraph(attributePaths = {"tecnico", "tecnico.usuario"})
    List<Comision> findByEstadoOrderByFechaLimitePagoAscIdAsc(EstadoComision estado);

    long countByEstado(EstadoComision estado);

    @Query("select sum(c.montoComision) from Comision c where c.estado = :estado")
    BigDecimal sumarMontoPorEstado(@Param("estado") EstadoComision estado);

    @Query("""
            select sum(c.montoComision) from Comision c
            where c.estado <> :estadoExcluido
            """)
    BigDecimal sumarComisionTotal(@Param("estadoExcluido") EstadoComision estadoExcluido);

    @Query("""
            select sum(c.montoBase) from Comision c
            where c.estado <> :estadoExcluido
            """)
    BigDecimal sumarVolumenTotal(@Param("estadoExcluido") EstadoComision estadoExcluido);

    @Query("""
            select sum(c.montoComision) from Comision c
            where c.estado <> :estadoExcluido
              and c.servicio.fechaConfirmacion >= :desde
              and c.servicio.fechaConfirmacion < :hasta
            """)
    BigDecimal sumarComisionEntre(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            @Param("estadoExcluido") EstadoComision estadoExcluido);

    @Query("""
            select sum(c.montoBase) from Comision c
            where c.estado <> :estadoExcluido
              and c.servicio.fechaConfirmacion >= :desde
              and c.servicio.fechaConfirmacion < :hasta
            """)
    BigDecimal sumarVolumenEntre(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            @Param("estadoExcluido") EstadoComision estadoExcluido);

    @Query(value = """
            SELECT DATE_FORMAT(s.fecha_confirmacion, '%Y-%m') AS mes,
                   COUNT(*) AS servicios,
                   COALESCE(SUM(c.monto_base), 0) AS volumen_total,
                   COALESCE(SUM(c.monto_comision), 0) AS ingreso_fixtech
            FROM comision c
            JOIN servicio s ON s.id = c.servicio_id
            WHERE c.estado <> 'ANULADA'
              AND s.fecha_confirmacion >= :desde
              AND s.fecha_confirmacion < :hasta
            GROUP BY DATE_FORMAT(s.fecha_confirmacion, '%Y-%m')
            ORDER BY mes
            """, nativeQuery = true)
    List<Object[]> ingresosPorMes(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);
}
