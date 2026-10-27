package com.rotalog.service;

import com.rotalog.config.AlertaProperties;
import com.rotalog.domain.Manutencao;
import com.rotalog.domain.MotivoAlerta;
import com.rotalog.domain.Veiculo;
import com.rotalog.dto.ElegibilidadeManutencao;
import com.rotalog.repository.ManutencaoRepository;
import com.rotalog.repository.VeiculoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * ManutencaoPreventivaService - regras de manutenção preventiva do veículo.
 * Extraído de VeiculoService (ADR 0001, problema 1).
 *
 * FIXME: Limites e custos fixos no código (ver ADR 0001, "Fora do escopo")
 */
@Slf4j
@Service
public class ManutencaoPreventivaService {

    @Autowired
    private VeiculoRepository veiculoRepository;

    @Autowired
    private ManutencaoRepository manutencaoRepository;

    @Autowired
    private AlertaProperties alertaProperties;

    @Autowired
    private VeiculoNotificador veiculoNotificador;

    public void agendarManutencaoPreventiva(Long veiculoId, Long quilometragemLimite) {
        Veiculo veiculo = buscarVeiculo(veiculoId);

        // FIXME: Hardcoded intervals
        Long intervaloQuilometragem = 10000L;
        Integer intervaloMeses = 3;

        log.info("Manutenção preventiva agendada para veículo {} em {} km",
            veiculo.getPlaca(), quilometragemLimite);

        veiculoNotificador.notificar("MANUTENCAO_AGENDADA",
            "Manutenção preventiva agendada para veículo " + veiculo.getPlaca() + " em " + quilometragemLimite + " km");
    }

    public Double calcularCustoManutencao(String modelo, Long quilometragem) {
        // FIXME: Hardcoded costs
        Double custoPorKm = 0.05;
        Double custoBase = 500.0;

        return custoBase + (quilometragem * custoPorKm);
    }

    public Boolean precisaDeManutencao(Long veiculoId) {
        Veiculo veiculo = buscarVeiculo(veiculoId);

        return avaliarElegibilidade(veiculo, LocalDateTime.now()) != null;
    }

    /**
     * Avalia se o veículo precisa de manutenção preventiva.
     * Elegível: veículo ATIVO com km desde a última preventiva concluída maior que o limite
     * OU última preventiva concluída há mais de N meses.
     * Sem preventiva concluída: km total e data de cadastro como referência.
     *
     * @return o motivo e a base do cálculo, ou null se o veículo não é elegível
     */
    public ElegibilidadeManutencao avaliarElegibilidade(Veiculo veiculo, LocalDateTime agora) {
        if (!"ATIVO".equals(veiculo.getStatus())) {
            return null;
        }

        Optional<Manutencao> ultimaPreventiva = manutencaoRepository
                .findFirstByVeiculoIdAndTipoManutencaoAndStatusOrderByDataManutencaoDesc(
                        veiculo.getId(), "PREVENTIVA", "CONCLUIDA");

        long quilometragem = veiculo.getQuilometragem() != null ? veiculo.getQuilometragem() : 0L;
        long kmDesdeUltimaPreventiva = quilometragem;
        LocalDateTime dataReferencia = veiculo.getDataCadastro();

        if (ultimaPreventiva.isPresent()) {
            Manutencao preventiva = ultimaPreventiva.get();
            long kmNaPreventiva = preventiva.getQuilometragemManutencao() != null ? preventiva.getQuilometragemManutencao() : 0L;
            kmDesdeUltimaPreventiva = Math.max(0L, quilometragem - kmNaPreventiva);
            if (preventiva.getDataManutencao() != null) {
                dataReferencia = preventiva.getDataManutencao();
            }
        }

        boolean porKm = kmDesdeUltimaPreventiva > alertaProperties.getLimiteKm();
        boolean porTempo = dataReferencia != null
                && dataReferencia.plusMonths(alertaProperties.getIntervaloMeses()).isBefore(agora);

        if (!porKm && !porTempo) {
            return null;
        }

        MotivoAlerta motivo = porKm && porTempo ? MotivoAlerta.KM_E_TEMPO
                : porKm ? MotivoAlerta.KM : MotivoAlerta.TEMPO;
        return new ElegibilidadeManutencao(motivo, kmDesdeUltimaPreventiva, dataReferencia);
    }

    private Veiculo buscarVeiculo(Long veiculoId) {
        return veiculoRepository.findById(veiculoId)
                .orElseThrow(() -> new RuntimeException("Veículo não encontrado: " + veiculoId));
    }
}
