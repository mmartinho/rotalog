package com.rotalog.service;

import com.rotalog.config.AlertaProperties;
import com.rotalog.domain.Manutencao;
import com.rotalog.domain.MotivoAlerta;
import com.rotalog.domain.Veiculo;
import com.rotalog.dto.ElegibilidadeManutencao;
import com.rotalog.repository.ManutencaoRepository;
import com.rotalog.repository.VeiculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManutencaoPreventivaServiceTest {

    @Mock
    private VeiculoRepository veiculoRepository;

    @Mock
    private ManutencaoRepository manutencaoRepository;

    @Mock
    private VeiculoNotificador veiculoNotificador;

    @Spy
    private AlertaProperties alertaProperties = new AlertaProperties();

    @InjectMocks
    private ManutencaoPreventivaService manutencaoPreventivaService;

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 16, 12, 0);

    private Veiculo veiculo;

    @BeforeEach
    void setUp() {
        veiculo = new Veiculo();
        veiculo.setId(1L);
        veiculo.setPlaca("ABC1234");
        veiculo.setQuilometragem(1000L);
        veiculo.setStatus("ATIVO");
        veiculo.setDataCadastro(AGORA.minusMonths(1));

        // por padrão, o veículo não tem preventiva concluída
        lenient().when(manutencaoRepository
                .findFirstByVeiculoIdAndTipoManutencaoAndStatusOrderByDataManutencaoDesc(any(), eq("PREVENTIVA"), eq("CONCLUIDA")))
                .thenReturn(Optional.empty());
    }

    private void comUltimaPreventiva(Long quilometragem, LocalDateTime data) {
        Manutencao preventiva = new Manutencao();
        preventiva.setVeiculoId(1L);
        preventiva.setTipoManutencao("PREVENTIVA");
        preventiva.setStatus("CONCLUIDA");
        preventiva.setQuilometragemManutencao(quilometragem);
        preventiva.setDataManutencao(data);
        lenient().when(manutencaoRepository
                .findFirstByVeiculoIdAndTipoManutencaoAndStatusOrderByDataManutencaoDesc(1L, "PREVENTIVA", "CONCLUIDA"))
                .thenReturn(Optional.of(preventiva));
    }

    // ---------- avaliarElegibilidade ----------

    @Test
    void avaliarElegibilidade_soKm_motivoKm() {
        veiculo.setQuilometragem(70000L);
        comUltimaPreventiva(10000L, AGORA.minusMonths(2)); // 60000 km, 2 meses

        ElegibilidadeManutencao resultado = manutencaoPreventivaService.avaliarElegibilidade(veiculo, AGORA);

        assertNotNull(resultado);
        assertEquals(MotivoAlerta.KM, resultado.getMotivo());
        assertEquals(60000L, resultado.getKmDesdeUltimaPreventiva());
        assertEquals(AGORA.minusMonths(2), resultado.getDataReferencia());
    }

    @Test
    void avaliarElegibilidade_soTempo_motivoTempo() {
        veiculo.setQuilometragem(30000L);
        comUltimaPreventiva(25000L, AGORA.minusMonths(8)); // 5000 km, 8 meses

        ElegibilidadeManutencao resultado = manutencaoPreventivaService.avaliarElegibilidade(veiculo, AGORA);

        assertNotNull(resultado);
        assertEquals(MotivoAlerta.TEMPO, resultado.getMotivo());
        assertEquals(5000L, resultado.getKmDesdeUltimaPreventiva());
    }

    @Test
    void avaliarElegibilidade_kmETempo_motivoKmETempo() {
        veiculo.setQuilometragem(200000L);
        comUltimaPreventiva(100000L, AGORA.minusMonths(9));

        ElegibilidadeManutencao resultado = manutencaoPreventivaService.avaliarElegibilidade(veiculo, AGORA);

        assertNotNull(resultado);
        assertEquals(MotivoAlerta.KM_E_TEMPO, resultado.getMotivo());
    }

    @Test
    void avaliarElegibilidade_nenhum_devolveNull() {
        veiculo.setQuilometragem(52000L);
        comUltimaPreventiva(40000L, AGORA.minusMonths(1));

        assertNull(manutencaoPreventivaService.avaliarElegibilidade(veiculo, AGORA));
    }

    @Test
    void avaliarElegibilidade_semPreventiva_usaKmTotalEDataCadastro() {
        veiculo.setQuilometragem(65000L);
        veiculo.setDataCadastro(AGORA.minusMonths(2));

        ElegibilidadeManutencao resultado = manutencaoPreventivaService.avaliarElegibilidade(veiculo, AGORA);

        assertNotNull(resultado);
        assertEquals(MotivoAlerta.KM, resultado.getMotivo());
        assertEquals(65000L, resultado.getKmDesdeUltimaPreventiva());
        assertEquals(AGORA.minusMonths(2), resultado.getDataReferencia());
    }

    @Test
    void avaliarElegibilidade_semPreventivaCadastradoHaMaisDeSeisMeses_motivoTempo() {
        veiculo.setQuilometragem(1000L);
        veiculo.setDataCadastro(AGORA.minusMonths(7));

        ElegibilidadeManutencao resultado = manutencaoPreventivaService.avaliarElegibilidade(veiculo, AGORA);

        assertNotNull(resultado);
        assertEquals(MotivoAlerta.TEMPO, resultado.getMotivo());
    }

    @Test
    void avaliarElegibilidade_veiculoInativo_devolveNull() {
        veiculo.setStatus("INATIVO");
        veiculo.setQuilometragem(200000L);

        assertNull(manutencaoPreventivaService.avaliarElegibilidade(veiculo, AGORA));
    }

    @Test
    void avaliarElegibilidade_veiculoEmManutencao_devolveNull() {
        veiculo.setStatus("MANUTENCAO");
        veiculo.setQuilometragem(200000L);

        assertNull(manutencaoPreventivaService.avaliarElegibilidade(veiculo, AGORA));
    }

    @Test
    void avaliarElegibilidade_kmNoLimiteExato_devolveNull() {
        veiculo.setQuilometragem(60000L);
        comUltimaPreventiva(10000L, AGORA.minusMonths(1)); // exatamente 50000 km

        assertNull(manutencaoPreventivaService.avaliarElegibilidade(veiculo, AGORA));
    }

    @Test
    void avaliarElegibilidade_umKmAcimaDoLimite_motivoKm() {
        veiculo.setQuilometragem(60001L);
        comUltimaPreventiva(10000L, AGORA.minusMonths(1));

        ElegibilidadeManutencao resultado = manutencaoPreventivaService.avaliarElegibilidade(veiculo, AGORA);

        assertNotNull(resultado);
        assertEquals(MotivoAlerta.KM, resultado.getMotivo());
    }

    @Test
    void avaliarElegibilidade_seisMesesExatos_devolveNull() {
        comUltimaPreventiva(1000L, AGORA.minusMonths(6)); // exatamente 6 meses

        assertNull(manutencaoPreventivaService.avaliarElegibilidade(veiculo, AGORA));
    }

    @Test
    void avaliarElegibilidade_seisMesesEUmSegundo_motivoTempo() {
        comUltimaPreventiva(1000L, AGORA.minusMonths(6).minusSeconds(1));

        ElegibilidadeManutencao resultado = manutencaoPreventivaService.avaliarElegibilidade(veiculo, AGORA);

        assertNotNull(resultado);
        assertEquals(MotivoAlerta.TEMPO, resultado.getMotivo());
    }

    @Test
    void avaliarElegibilidade_respeitaLimitesConfigurados() {
        alertaProperties.setLimiteKm(5000L);
        alertaProperties.setIntervaloMeses(1);
        veiculo.setQuilometragem(8000L);
        comUltimaPreventiva(2000L, AGORA.minusDays(10)); // 6000 km

        ElegibilidadeManutencao resultado = manutencaoPreventivaService.avaliarElegibilidade(veiculo, AGORA);

        assertNotNull(resultado);
        assertEquals(MotivoAlerta.KM, resultado.getMotivo());
    }

    // ---------- precisaDeManutencao ----------

    @Test
    void precisaDeManutencao_abaixoDoLimite_devolveFalse() {
        veiculo.setQuilometragem(50000L);
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));

        assertFalse(manutencaoPreventivaService.precisaDeManutencao(1L));
    }

    @Test
    void precisaDeManutencao_acimaDoLimite_devolveTrue() {
        veiculo.setQuilometragem(50001L);
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));

        assertTrue(manutencaoPreventivaService.precisaDeManutencao(1L));
    }

    @Test
    void precisaDeManutencao_quilometragemNula_devolveFalse() {
        veiculo.setQuilometragem(null);
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));

        assertFalse(manutencaoPreventivaService.precisaDeManutencao(1L));
    }

    @Test
    void precisaDeManutencao_quandoVeiculoNaoExiste_lancaExcecao() {
        when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> manutencaoPreventivaService.precisaDeManutencao(99L));

        assertEquals("Veículo não encontrado: 99", ex.getMessage());
    }

    @Test
    void calcularCustoManutencao_somaCustoBaseAoCustoPorKm() {
        assertEquals(1000.0, manutencaoPreventivaService.calcularCustoManutencao("Fiorino", 10000L), 0.0001);
    }

    @Test
    void calcularCustoManutencao_quilometragemZero_devolveCustoBase() {
        assertEquals(500.0, manutencaoPreventivaService.calcularCustoManutencao("Fiorino", 0L), 0.0001);
    }

    @Test
    void agendarManutencaoPreventiva_notificaAgendamento() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));

        manutencaoPreventivaService.agendarManutencaoPreventiva(1L, 50000L);

        verify(veiculoNotificador).notificar("MANUTENCAO_AGENDADA",
                "Manutenção preventiva agendada para veículo ABC1234 em 50000 km");
    }

    @Test
    void agendarManutencaoPreventiva_quandoVeiculoNaoExiste_naoNotifica() {
        when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> manutencaoPreventivaService.agendarManutencaoPreventiva(99L, 50000L));

        verifyNoInteractions(veiculoNotificador);
    }
}
