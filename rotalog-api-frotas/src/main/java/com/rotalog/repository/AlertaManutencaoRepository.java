package com.rotalog.repository;

import com.rotalog.domain.AlertaManutencao;
import com.rotalog.domain.StatusAlerta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * AlertaManutencaoRepository
 *
 * FIXME: Sem paginação
 */
@Repository
public interface AlertaManutencaoRepository extends JpaRepository<AlertaManutencao, Long> {

    List<AlertaManutencao> findAllByOrderByDataCriacaoDesc();

    List<AlertaManutencao> findByStatusOrderByDataCriacaoDesc(StatusAlerta status);

    // Veículo já tem alerta aberto (ENVIADA/PENDENTE) criado depois da data de referência?
    boolean existsByVeiculoIdAndStatusInAndDataCriacaoAfter(Long veiculoId, Collection<StatusAlerta> status, LocalDateTime data);
}
