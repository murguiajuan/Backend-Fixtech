package com.fixtech.repository;

import com.fixtech.entity.Resena;
import com.fixtech.entity.enums.EstadoResena;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResenaRepository extends JpaRepository<Resena, Long> {

    boolean existsByServicioId(Long servicioId);

    @EntityGraph(attributePaths = {"cliente", "tecnico", "servicio"})
    Optional<Resena> findByServicioId(Long servicioId);

    @EntityGraph(attributePaths = {"cliente", "tecnico", "servicio"})
    @Query("select r from Resena r where r.id = :resenaId")
    Optional<Resena> buscarDetallePorId(@Param("resenaId") Long resenaId);

    @Query(value = "SELECT * FROM resena WHERE id = :resenaId FOR UPDATE",
            nativeQuery = true)
    Optional<Resena> findByIdForUpdate(@Param("resenaId") Long resenaId);

    @EntityGraph(attributePaths = {"cliente", "tecnico", "servicio"})
    List<Resena> findByTecnicoIdAndEstadoOrderByCreadoEnDescIdDesc(
            Long tecnicoId, EstadoResena estado);

    @EntityGraph(attributePaths = {"cliente", "tecnico", "servicio"})
    List<Resena> findAllByOrderByCreadoEnDescIdDesc();

    @EntityGraph(attributePaths = {"cliente", "tecnico", "servicio"})
    List<Resena> findByEstadoOrderByCreadoEnDescIdDesc(EstadoResena estado);

    long countByTecnicoIdAndEstado(Long tecnicoId, EstadoResena estado);

    @Query("""
            select avg(r.estrellas) from Resena r
            where r.tecnico.id = :tecnicoId
              and r.estado = :estado
            """)
    Double promedioEstrellas(
            @Param("tecnicoId") Long tecnicoId,
            @Param("estado") EstadoResena estado);

    @Query("select avg(r.estrellas) from Resena r where r.estado = :estado")
    Double promedioGlobal(@Param("estado") EstadoResena estado);

    long countByEstado(EstadoResena estado);

    @Query("""
            select avg(r.estrellas) from Resena r
            where r.estado = :estado
              and r.creadoEn >= :desde and r.creadoEn < :hasta
            """)
    Double promedioEntre(
            @Param("estado") EstadoResena estado,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    @Query("""
            select count(r) from Resena r
            where r.estado = :estado
              and r.creadoEn >= :desde and r.creadoEn < :hasta
            """)
    long contarEntre(
            @Param("estado") EstadoResena estado,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);
}
