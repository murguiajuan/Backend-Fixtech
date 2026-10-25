package com.fixtech.repository;

import com.fixtech.entity.Auditoria;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {

    @Query("""
            select a from Auditoria a
            where (:usuarioId is null or a.usuarioId = :usuarioId)
              and (:entidad is null or a.entidad = :entidad)
              and (:accion is null or a.accion = :accion)
              and (:desde is null or a.creadoEn >= :desde)
              and (:hasta is null or a.creadoEn < :hasta)
            order by a.creadoEn desc, a.id desc
            """)
    Page<Auditoria> buscar(
            @Param("usuarioId") Long usuarioId,
            @Param("entidad") String entidad,
            @Param("accion") String accion,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            Pageable pageable);
}
