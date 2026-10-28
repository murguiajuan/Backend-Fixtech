package com.fixtech.repository;

import com.fixtech.entity.TokenRecuperacion;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenRecuperacionRepository extends JpaRepository<TokenRecuperacion, Long> {

    Optional<TokenRecuperacion> findTopByUsuarioIdAndUsadoFalseOrderByCreadoEnDescIdDesc(
            Long usuarioId);

    Optional<TokenRecuperacion> findByTicketHashAndTicketUsadoFalse(String ticketHash);

    boolean existsByUsuarioIdAndCreadoEnAfter(Long usuarioId, LocalDateTime desde);

    long countByUsuarioIdAndCreadoEnAfter(Long usuarioId, LocalDateTime desde);

    @Modifying
    @Query("""
            update TokenRecuperacion t
               set t.usado = true, t.ticketUsado = true
             where t.usuario.id = :usuarioId
               and (t.usado = false or t.ticketUsado = false)
            """)
    int invalidarTodos(@Param("usuarioId") Long usuarioId);

    @Modifying
    @Query("delete from TokenRecuperacion t where t.creadoEn < :fecha")
    int eliminarAnteriores(@Param("fecha") LocalDateTime fecha);
}
