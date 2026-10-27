package com.rotalog.service;

import com.rotalog.config.AlertaProperties;
import com.rotalog.domain.AlertaManutencao;
import com.rotalog.domain.StatusAlerta;
import com.rotalog.domain.Veiculo;
import com.rotalog.dto.AlertaManutencaoPayload;
import com.rotalog.dto.AlertaManutencaoResponse;
import com.rotalog.dto.ElegibilidadeManutencao;
import com.rotalog.dto.ResultadoNotificacao;
import com.rotalog.dto.ResumoVerificacaoAlertas;
import com.rotalog.repository.AlertaManutencaoRepository;
import com.rotalog.repository.VeiculoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * AlertaManutencaoService - verifica veículos que precisam de manutenção preventiva,
 * pede o envio do e-mail à api-notificacoes e registra o alerta com o status da notificação.
 *
 * A verificação é idempotente: veículo com alerta ENVIADA ou PENDENTE posterior à sua
 * última preventiva (ou ao cadastro) não gera novo alerta. Alertas com FALHA são tentados de novo.
 *
 * FIXME: Chamadas HTTP síncronas em sequência, uma por veículo elegível
 * FIXME: Sem reenvio dos alertas PENDENTE
 */
@Slf4j
@Service
public class AlertaManutencaoService {

    private static final Set<StatusAlerta> STATUS_ABERTOS = Set.of(StatusAlerta.ENVIADA, StatusAlerta.PENDENTE);

    @Autowired
    private VeiculoRepository veiculoRepository;

    @Autowired
    private AlertaManutencaoRepository alertaManutencaoRepository;

    @Autowired
    private ManutencaoPreventivaService manutencaoPreventivaService;

    @Autowired
    private NotificacaoClient notificacaoClient;

    @Autowired
    private AlertaProperties alertaProperties;

    public ResumoVerificacaoAlertas verificarAlertas() {
        ResumoVerificacaoAlertas resumo = new ResumoVerificacaoAlertas();
        LocalDateTime agora = LocalDateTime.now();

        log.info("Verificação de alertas de manutenção iniciada");

        List<Veiculo> veiculos = veiculoRepository.findByStatus("ATIVO");
        log.info("Veículos ativos encontrados para verificação: {}", kv("totalVeiculos", veiculos.size()));
        for (Veiculo veiculo : veiculos) {
            resumo.setVerificados(resumo.getVerificados() + 1);
            try {
                processarVeiculo(veiculo, agora, resumo);
            } catch (Exception e) {
                // Falha em um veículo não interrompe os demais
                log.error("Erro ao verificar alerta do veículo {}: {}", veiculo.getPlaca(), e.getMessage());
            }
        }

        log.info("Verificação de alertas concluída: {}, {}, {}",
                kv("verificados", resumo.getVerificados()),
                kv("elegiveis", resumo.getElegiveis()),
                kv("novos", resumo.getNovosAlertas()));
        return resumo;
    }

    public List<AlertaManutencaoResponse> listarAlertas(StatusAlerta status) {
        List<AlertaManutencao> alertas = status == null
                ? alertaManutencaoRepository.findAllByOrderByDataCriacaoDesc()
                : alertaManutencaoRepository.findByStatusOrderByDataCriacaoDesc(status);

        List<Long> veiculoIds = alertas.stream().map(AlertaManutencao::getVeiculoId).distinct().collect(Collectors.toList());
        Map<Long, String> placas = veiculoRepository.findAllById(veiculoIds).stream()
                .collect(Collectors.toMap(Veiculo::getId, Veiculo::getPlaca));

        return alertas.stream()
                .map(alerta -> AlertaManutencaoResponse.de(alerta, placas.get(alerta.getVeiculoId())))
                .collect(Collectors.toList());
    }

    public static StatusAlerta converterStatus(String status) {
        try {
            return StatusAlerta.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Status inválido: " + status
                    + ". Valores aceitos: " + Arrays.toString(StatusAlerta.values()));
        }
    }

    private void processarVeiculo(Veiculo veiculo, LocalDateTime agora, ResumoVerificacaoAlertas resumo) {
        ElegibilidadeManutencao elegibilidade = manutencaoPreventivaService.avaliarElegibilidade(veiculo, agora);
        if (elegibilidade == null) {
            return;
        }
        resumo.setElegiveis(resumo.getElegiveis() + 1);

        boolean jaTemAlertaAberto = alertaManutencaoRepository.existsByVeiculoIdAndStatusInAndDataCriacaoAfter(
                veiculo.getId(), STATUS_ABERTOS, elegibilidade.getDataReferencia());
        if (jaTemAlertaAberto) {
            log.info("Veículo elegível ignorado: já possui alerta aberto (ENVIADA/PENDENTE): {}, {}",
                    kv("veiculoId", veiculo.getId()), kv("placa", veiculo.getPlaca()));
            return;
        }

        AlertaManutencaoPayload payload = new AlertaManutencaoPayload(
                veiculo.getId(),
                veiculo.getPlaca(),
                elegibilidade.getMotivo().name(),
                elegibilidade.getKmDesdeUltimaPreventiva(),
                elegibilidade.getDataReferencia(),
                alertaProperties.getEmailGestor());

        // Chama a api-notificacoes e só depois grava o alerta com o status resultante
        ResultadoNotificacao resultado = notificacaoClient.enviarAlertaManutencao(payload);

        AlertaManutencao alerta = new AlertaManutencao();
        alerta.setVeiculoId(veiculo.getId());
        alerta.setMotivo(elegibilidade.getMotivo());
        alerta.setKmDesdeUltimaPreventiva(elegibilidade.getKmDesdeUltimaPreventiva());
        alerta.setDataReferencia(elegibilidade.getDataReferencia());
        alerta.setStatus(resultado.getStatus());
        alerta.setNotificacaoId(resultado.getNotificacaoId());
        alerta.setMensagemErro(resultado.getMensagemErro());
        alerta.setDataCriacao(agora);
        alerta.setDataAtualizacao(agora);
        alertaManutencaoRepository.save(alerta);

        resumo.setNovosAlertas(resumo.getNovosAlertas() + 1);
        switch (resultado.getStatus()) {
            case ENVIADA:
                resumo.setEnviados(resumo.getEnviados() + 1);
                break;
            case PENDENTE:
                resumo.setPendentes(resumo.getPendentes() + 1);
                break;
            default:
                resumo.setFalhas(resumo.getFalhas() + 1);
        }
    }
}
