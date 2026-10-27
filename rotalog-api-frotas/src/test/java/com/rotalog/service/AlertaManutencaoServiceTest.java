package com.rotalog.service;

import com.rotalog.config.AlertaProperties;
import com.rotalog.domain.AlertaManutencao;
import com.rotalog.domain.MotivoAlerta;
import com.rotalog.domain.StatusAlerta;
import com.rotalog.domain.Veiculo;
import com.rotalog.dto.AlertaManutencaoPayload;
import com.rotalog.dto.AlertaManutencaoResponse;
import com.rotalog.dto.ElegibilidadeManutencao;
import com.rotalog.dto.ResultadoNotificacao;
import com.rotalog.dto.ResumoVerificacaoAlertas;
import com.rotalog.repository.AlertaManutencaoRepository;
import com.rotalog.repository.VeiculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertaManutencaoServiceTest {

    @Mock
    private VeiculoRepository veiculoRepository;

    @Mock
    private AlertaManutencaoRepository alertaManutencaoRepository;

    @Mock
    private ManutencaoPreventivaService manutencaoPreventivaService;

    @Mock
    private NotificacaoClient notificacaoClient;

    @Spy
    private AlertaProperties alertaProperties = new AlertaProperties();

    @InjectMocks
    private AlertaManutencaoService alertaManutencaoService;

    private final List<AlertaManutencao> alertasGravados = new ArrayList<>();

    @BeforeEach
    void setUp() {
        alertaProperties.setEmailGestor("gestor@rotalog.com");
    }

    private Veiculo veiculo(Long id, String placa) {
        Veiculo veiculo = new Veiculo();
        veiculo.setId(id);
        veiculo.setPlaca(placa);
        veiculo.setStatus("ATIVO");
        veiculo.setQuilometragem(100000L);
        return veiculo;
    }

    private ElegibilidadeManutencao elegivelPorKm() {
        return new ElegibilidadeManutencao(MotivoAlerta.KM, 60000L, LocalDateTime.now().minusMonths(2));
    }

    private void veiculoElegivel(Veiculo veiculo) {
        when(manutencaoPreventivaService.avaliarElegibilidade(org.mockito.ArgumentMatchers.eq(veiculo), any()))
                .thenReturn(elegivelPorKm());
    }

    // ---------- Orquestração ----------

    @Test
    void verificarAlertas_envioComSucesso_gravaAlertaEnviada() {
        Veiculo v = veiculo(1L, "ABC1D23");
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(v));
        veiculoElegivel(v);
        when(notificacaoClient.enviarAlertaManutencao(any()))
                .thenReturn(new ResultadoNotificacao(StatusAlerta.ENVIADA, 99L, null));

        ResumoVerificacaoAlertas resumo = alertaManutencaoService.verificarAlertas();

        ArgumentCaptor<AlertaManutencao> captor = ArgumentCaptor.forClass(AlertaManutencao.class);
        verify(alertaManutencaoRepository).save(captor.capture());
        AlertaManutencao alerta = captor.getValue();
        assertEquals(StatusAlerta.ENVIADA, alerta.getStatus());
        assertEquals(99L, alerta.getNotificacaoId());
        assertEquals(1L, alerta.getVeiculoId());
        assertEquals(MotivoAlerta.KM, alerta.getMotivo());
        assertEquals(60000L, alerta.getKmDesdeUltimaPreventiva());
        assertEquals(1, resumo.getVerificados());
        assertEquals(1, resumo.getElegiveis());
        assertEquals(1, resumo.getNovosAlertas());
        assertEquals(1, resumo.getEnviados());
        assertEquals(0, resumo.getFalhas());
        assertEquals(0, resumo.getPendentes());
    }

    @Test
    void verificarAlertas_enviaPayloadComEmailDoGestor() {
        Veiculo v = veiculo(1L, "ABC1D23");
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(v));
        veiculoElegivel(v);
        when(notificacaoClient.enviarAlertaManutencao(any()))
                .thenReturn(new ResultadoNotificacao(StatusAlerta.ENVIADA, 1L, null));

        alertaManutencaoService.verificarAlertas();

        ArgumentCaptor<AlertaManutencaoPayload> captor = ArgumentCaptor.forClass(AlertaManutencaoPayload.class);
        verify(notificacaoClient).enviarAlertaManutencao(captor.capture());
        AlertaManutencaoPayload payload = captor.getValue();
        assertEquals("gestor@rotalog.com", payload.getEmailGestor());
        assertEquals("ABC1D23", payload.getPlaca());
        assertEquals("KM", payload.getMotivo());
        assertEquals(1L, payload.getVeiculoId());
    }

    @Test
    void verificarAlertas_respostaFalha_gravaAlertaFalha() {
        Veiculo v = veiculo(1L, "ABC1D23");
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(v));
        veiculoElegivel(v);
        when(notificacaoClient.enviarAlertaManutencao(any()))
                .thenReturn(new ResultadoNotificacao(StatusAlerta.FALHA, 5L, "SMTP timeout"));

        ResumoVerificacaoAlertas resumo = alertaManutencaoService.verificarAlertas();

        ArgumentCaptor<AlertaManutencao> captor = ArgumentCaptor.forClass(AlertaManutencao.class);
        verify(alertaManutencaoRepository).save(captor.capture());
        assertEquals(StatusAlerta.FALHA, captor.getValue().getStatus());
        assertEquals("SMTP timeout", captor.getValue().getMensagemErro());
        assertEquals(1, resumo.getFalhas());
    }

    @Test
    void verificarAlertas_notificacoesForaDoAr_gravaAlertaPendente() {
        Veiculo v = veiculo(1L, "ABC1D23");
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(v));
        veiculoElegivel(v);
        when(notificacaoClient.enviarAlertaManutencao(any()))
                .thenReturn(new ResultadoNotificacao(StatusAlerta.PENDENTE, null, "Connection refused"));

        ResumoVerificacaoAlertas resumo = alertaManutencaoService.verificarAlertas();

        ArgumentCaptor<AlertaManutencao> captor = ArgumentCaptor.forClass(AlertaManutencao.class);
        verify(alertaManutencaoRepository).save(captor.capture());
        assertEquals(StatusAlerta.PENDENTE, captor.getValue().getStatus());
        assertEquals(1, resumo.getPendentes());
    }

    @Test
    void verificarAlertas_veiculoNaoElegivel_naoChamaNotificacoes() {
        Veiculo v = veiculo(1L, "ABC1D23");
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(v));
        when(manutencaoPreventivaService.avaliarElegibilidade(org.mockito.ArgumentMatchers.eq(v), any())).thenReturn(null);

        ResumoVerificacaoAlertas resumo = alertaManutencaoService.verificarAlertas();

        verify(notificacaoClient, never()).enviarAlertaManutencao(any());
        verify(alertaManutencaoRepository, never()).save(any());
        assertEquals(1, resumo.getVerificados());
        assertEquals(0, resumo.getElegiveis());
    }

    // ---------- Idempotência ----------

    @Test
    @SuppressWarnings("unchecked")
    void verificarAlertas_segundaVerificacao_naoDuplicaAlertas() {
        Veiculo v = veiculo(1L, "ABC1D23");
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(v));
        veiculoElegivel(v);
        when(notificacaoClient.enviarAlertaManutencao(any()))
                .thenReturn(new ResultadoNotificacao(StatusAlerta.ENVIADA, 1L, null));
        when(alertaManutencaoRepository.save(any(AlertaManutencao.class))).thenAnswer(invocation -> {
            alertasGravados.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        // "banco" em memória: existe alerta aberto criado depois da data de referência?
        when(alertaManutencaoRepository.existsByVeiculoIdAndStatusInAndDataCriacaoAfter(any(), any(), any()))
                .thenAnswer(invocation -> {
                    Long veiculoId = invocation.getArgument(0);
                    Collection<StatusAlerta> status = invocation.getArgument(1);
                    LocalDateTime depois = invocation.getArgument(2);
                    return alertasGravados.stream().anyMatch(a -> a.getVeiculoId().equals(veiculoId)
                            && status.contains(a.getStatus()) && a.getDataCriacao().isAfter(depois));
                });

        ResumoVerificacaoAlertas primeira = alertaManutencaoService.verificarAlertas();
        ResumoVerificacaoAlertas segunda = alertaManutencaoService.verificarAlertas();

        assertEquals(1, primeira.getNovosAlertas());
        assertEquals(1, segunda.getElegiveis());
        assertEquals(0, segunda.getNovosAlertas());
        assertEquals(1, alertasGravados.size());
        verify(notificacaoClient, times(1)).enviarAlertaManutencao(any());
    }

    @Test
    void verificarAlertas_veiculoComAlertaAberto_ignora() {
        Veiculo v = veiculo(1L, "ABC1D23");
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(v));
        veiculoElegivel(v);
        when(alertaManutencaoRepository.existsByVeiculoIdAndStatusInAndDataCriacaoAfter(any(), any(), any()))
                .thenReturn(true);

        ResumoVerificacaoAlertas resumo = alertaManutencaoService.verificarAlertas();

        verify(notificacaoClient, never()).enviarAlertaManutencao(any());
        assertEquals(1, resumo.getElegiveis());
        assertEquals(0, resumo.getNovosAlertas());
    }

    @Test
    void verificarAlertas_consultaAlertaAbertoApenasEnviadaEPendente() {
        Veiculo v = veiculo(1L, "ABC1D23");
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(v));
        veiculoElegivel(v);
        when(notificacaoClient.enviarAlertaManutencao(any()))
                .thenReturn(new ResultadoNotificacao(StatusAlerta.ENVIADA, 1L, null));

        alertaManutencaoService.verificarAlertas();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<StatusAlerta>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(alertaManutencaoRepository).existsByVeiculoIdAndStatusInAndDataCriacaoAfter(any(), captor.capture(), any());
        // alerta FALHA não bloqueia: é tentado de novo na próxima verificação
        assertEquals(2, captor.getValue().size());
        assertEquals(true, captor.getValue().contains(StatusAlerta.ENVIADA));
        assertEquals(true, captor.getValue().contains(StatusAlerta.PENDENTE));
    }

    // ---------- Falha isolada ----------

    @Test
    void verificarAlertas_falhaEmUmVeiculo_naoInterrompeOsDemais() {
        Veiculo v1 = veiculo(1L, "AAA1A11");
        Veiculo v2 = veiculo(2L, "BBB2B22");
        Veiculo v3 = veiculo(3L, "CCC3C33");
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(v1, v2, v3));
        when(manutencaoPreventivaService.avaliarElegibilidade(org.mockito.ArgumentMatchers.eq(v1), any()))
                .thenThrow(new RuntimeException("erro inesperado"));
        veiculoElegivel(v2);
        veiculoElegivel(v3);
        when(notificacaoClient.enviarAlertaManutencao(any()))
                .thenReturn(new ResultadoNotificacao(StatusAlerta.ENVIADA, 1L, null));

        ResumoVerificacaoAlertas resumo = alertaManutencaoService.verificarAlertas();

        assertEquals(3, resumo.getVerificados());
        assertEquals(2, resumo.getNovosAlertas());
        verify(alertaManutencaoRepository, times(2)).save(any(AlertaManutencao.class));
    }

    @Test
    void verificarAlertas_erroAoGravarUmAlerta_naoInterrompeOsDemais() {
        Veiculo v1 = veiculo(1L, "AAA1A11");
        Veiculo v2 = veiculo(2L, "BBB2B22");
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(v1, v2));
        veiculoElegivel(v1);
        veiculoElegivel(v2);
        when(notificacaoClient.enviarAlertaManutencao(any()))
                .thenReturn(new ResultadoNotificacao(StatusAlerta.ENVIADA, 1L, null));
        when(alertaManutencaoRepository.save(any(AlertaManutencao.class)))
                .thenThrow(new RuntimeException("banco indisponível"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResumoVerificacaoAlertas resumo = alertaManutencaoService.verificarAlertas();

        assertEquals(2, resumo.getVerificados());
        assertEquals(1, resumo.getNovosAlertas());
    }

    // ---------- Listagem ----------

    @Test
    void listarAlertas_semFiltro_devolveTodosComPlaca() {
        AlertaManutencao alerta = alerta(10L, 1L, StatusAlerta.ENVIADA);
        when(alertaManutencaoRepository.findAllByOrderByDataCriacaoDesc()).thenReturn(List.of(alerta));
        when(veiculoRepository.findAllById(List.of(1L))).thenReturn(List.of(veiculo(1L, "ABC1D23")));

        List<AlertaManutencaoResponse> resposta = alertaManutencaoService.listarAlertas(null);

        assertEquals(1, resposta.size());
        assertEquals("ABC1D23", resposta.get(0).getPlaca());
        assertEquals("ENVIADA", resposta.get(0).getStatus());
        assertEquals("KM", resposta.get(0).getMotivo());
        verify(alertaManutencaoRepository, never()).findByStatusOrderByDataCriacaoDesc(any());
    }

    @Test
    void listarAlertas_comFiltro_consultaPorStatus() {
        when(alertaManutencaoRepository.findByStatusOrderByDataCriacaoDesc(StatusAlerta.PENDENTE))
                .thenReturn(List.of(alerta(11L, 2L, StatusAlerta.PENDENTE)));
        when(veiculoRepository.findAllById(List.of(2L))).thenReturn(List.of(veiculo(2L, "DEF4G56")));

        List<AlertaManutencaoResponse> resposta = alertaManutencaoService.listarAlertas(StatusAlerta.PENDENTE);

        assertEquals(1, resposta.size());
        assertEquals("PENDENTE", resposta.get(0).getStatus());
        verify(alertaManutencaoRepository, never()).findAllByOrderByDataCriacaoDesc();
    }

    // ---------- Conversão de status ----------

    @Test
    void converterStatus_aceitaMinusculaEEspacos() {
        assertEquals(StatusAlerta.FALHA, AlertaManutencaoService.converterStatus(" falha "));
    }

    @Test
    void converterStatus_valorInvalido_lancaExcecao() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AlertaManutencaoService.converterStatus("XPTO"));

        assertEquals(true, ex.getMessage().contains("Status inválido: XPTO"));
    }

    private AlertaManutencao alerta(Long id, Long veiculoId, StatusAlerta status) {
        AlertaManutencao alerta = new AlertaManutencao();
        alerta.setId(id);
        alerta.setVeiculoId(veiculoId);
        alerta.setMotivo(MotivoAlerta.KM);
        alerta.setKmDesdeUltimaPreventiva(60000L);
        alerta.setDataReferencia(LocalDateTime.now().minusMonths(2));
        alerta.setStatus(status);
        alerta.setDataCriacao(LocalDateTime.now());
        return alerta;
    }
}
