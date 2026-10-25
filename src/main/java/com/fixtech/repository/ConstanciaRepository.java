package com.fixtech.repository;

import com.fixtech.entity.Constancia;
import com.fixtech.entity.enums.TipoConstancia;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConstanciaRepository extends JpaRepository<Constancia, Long> {

    @EntityGraph(attributePaths = {"fotos"})
    @Query("""
            select distinct c from Constancia c
            where c.servicio.id = :servicioId
            order by c.creadoEn asc, c.id asc
            """)
    List<Constancia> buscarPorServicio(@Param("servicioId") Long servicioId);

    @EntityGraph(attributePaths = {"fotos"})
    @Query("""
            select c from Constancia c
            where c.id = :constanciaId
              and c.servicio.id = :servicioId
            """)
    Optional<Constancia> buscarDetalle(
            @Param("constanciaId") Long constanciaId,
            @Param("servicioId") Long servicioId);

    Optional<Constancia> findByServicioIdAndTipo(Long servicioId, TipoConstancia tipo);

    boolean existsByServicioIdAndTipo(Long servicioId, TipoConstancia tipo);
}
