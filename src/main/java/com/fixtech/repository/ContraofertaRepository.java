package com.fixtech.repository;

import com.fixtech.entity.Contraoferta;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContraofertaRepository extends JpaRepository<Contraoferta, Long> {

    List<Contraoferta> findByOfertaIdOrderByRondaAscIdAsc(Long ofertaId);

    Optional<Contraoferta> findTopByOfertaIdOrderByRondaDescIdDesc(Long ofertaId);

    long countByOfertaId(Long ofertaId);
}
