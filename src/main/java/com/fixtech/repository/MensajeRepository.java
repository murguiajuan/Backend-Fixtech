package com.fixtech.repository;

import com.fixtech.entity.Mensaje;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MensajeRepository extends JpaRepository<Mensaje, Long> {

    @Query("""
            select m from Mensaje m
            where m.servicio.id = :servicioId
            order by m.creadoEn asc, m.id asc
            """)
    List<Mensaje> buscarPorServicio(@Param("servicioId") Long servicioId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Mensaje m
            set m.leido = true
            where m.servicio.id = :servicioId
              and m.emisor.id <> :usuarioId
              and m.leido = false
            """)
    int marcarLeidos(
            @Param("servicioId") Long servicioId,
            @Param("usuarioId") Long usuarioId);
}
