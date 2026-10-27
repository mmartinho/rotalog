package com.rotalog.service;

import com.rotalog.domain.Veiculo;
import com.rotalog.repository.VeiculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VeiculoServiceTest {

    @Mock
    private VeiculoRepository veiculoRepository;

    @Mock
    private VeiculoValidator veiculoValidator;

    @Mock
    private VeiculoNotificador veiculoNotificador;

    @Mock
    private ManutencaoPreventivaService manutencaoPreventivaService;

    @InjectMocks
    private VeiculoService veiculoService;

    private Veiculo veiculo;

    @BeforeEach
    void setUp() {
        veiculo = new Veiculo();
        veiculo.setId(1L);
        veiculo.setPlaca("ABC1234");
        veiculo.setModelo("Fiorino");
        veiculo.setAnoFabricacao(2020);
        veiculo.setQuilometragem(1000L);
        veiculo.setStatus("ATIVO");
    }

    @Test
    void listarTodos_devolveTodosOsVeiculos() {
        when(veiculoRepository.findAll()).thenReturn(List.of(veiculo));

        assertEquals(List.of(veiculo), veiculoService.listarTodos());
    }

    @Test
    void buscarPorId_quandoExiste_devolveVeiculo() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));

        assertSame(veiculo, veiculoService.buscarPorId(1L));
    }

    @Test
    void buscarPorId_quandoNaoExiste_lancaExcecao() {
        when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> veiculoService.buscarPorId(99L));

        assertEquals("Veículo não encontrado: 99", ex.getMessage());
    }

    @Test
    void buscarPorPlaca_quandoExiste_devolveVeiculo() {
        when(veiculoRepository.findByPlaca("ABC1234")).thenReturn(Optional.of(veiculo));

        assertSame(veiculo, veiculoService.buscarPorPlaca("ABC1234"));
    }

    @Test
    void buscarPorPlaca_quandoNaoExiste_lancaExcecao() {
        when(veiculoRepository.findByPlaca("XYZ9999")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> veiculoService.buscarPorPlaca("XYZ9999"));

        assertEquals("Veículo não encontrado com placa: XYZ9999", ex.getMessage());
    }

    @Test
    void registrarVeiculo_comDadosValidos_salvaENotificaNovoVeiculo() {
        when(veiculoRepository.findByPlaca(anyString())).thenReturn(Optional.empty());
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        Veiculo salvo = veiculoService.registrarVeiculo("abc1234", "Fiorino", 2020);

        ArgumentCaptor<Veiculo> captor = ArgumentCaptor.forClass(Veiculo.class);
        verify(veiculoRepository).save(captor.capture());
        Veiculo persistido = captor.getValue();
        assertEquals("ABC1234", persistido.getPlaca());
        assertEquals("Fiorino", persistido.getModelo());
        assertEquals(2020, persistido.getAnoFabricacao());
        assertEquals("ATIVO", persistido.getStatus());
        assertEquals(0L, persistido.getQuilometragem());
        assertNotNull(persistido.getDataCadastro());
        assertNotNull(persistido.getDataAtualizacao());
        assertSame(persistido, salvo);
        verify(veiculoNotificador).notificar("NOVO_VEICULO", "Novo veículo cadastrado: ABC1234 - Fiorino");
    }

    @Test
    void registrarVeiculo_placaInvalida_propagaErroDoValidadorSemSalvar() {
        doThrow(new RuntimeException("Placa deve ter 7 caracteres"))
                .when(veiculoValidator).validarPlaca("ABC123");

        assertThrows(RuntimeException.class, () -> veiculoService.registrarVeiculo("ABC123", "Fiorino", 2020));

        verifyNoInteractions(veiculoRepository, veiculoNotificador);
    }

    @Test
    void registrarVeiculo_placaJaCadastrada_lancaExcecaoSemSalvar() {
        when(veiculoRepository.findByPlaca("ABC1234")).thenReturn(Optional.of(veiculo));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> veiculoService.registrarVeiculo("ABC1234", "Fiorino", 2020));

        assertEquals("Veículo com placa ABC1234 já existe", ex.getMessage());
        verify(veiculoRepository, never()).save(any(Veiculo.class));
        verifyNoInteractions(veiculoNotificador);
    }

    @Test
    void registrarVeiculo_modeloOuAnoInvalidos_propagaErroDoValidadorSemSalvar() {
        when(veiculoRepository.findByPlaca(anyString())).thenReturn(Optional.empty());
        doThrow(new RuntimeException("Modelo é obrigatório"))
                .when(veiculoValidator).validarModelo("");

        assertThrows(RuntimeException.class, () -> veiculoService.registrarVeiculo("ABC1234", "", 2020));

        verify(veiculoRepository, never()).save(any(Veiculo.class));
    }

    @Test
    void atualizarVeiculo_atualizaCamposInformados() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        Veiculo atualizado = veiculoService.atualizarVeiculo(1L, "Ducato", 2022, 5000L);

        assertEquals("Ducato", veiculo.getModelo());
        assertEquals(2022, veiculo.getAnoFabricacao());
        assertEquals(5000L, veiculo.getQuilometragem());
        assertNotNull(veiculo.getDataAtualizacao());
        assertSame(veiculo, atualizado);
    }

    @Test
    void atualizarVeiculo_comCamposNulosOuVazios_mantemValoresAtuais() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        veiculoService.atualizarVeiculo(1L, "", null, null);

        assertEquals("Fiorino", veiculo.getModelo());
        assertEquals(2020, veiculo.getAnoFabricacao());
        assertEquals(1000L, veiculo.getQuilometragem());
    }

    @Test
    void atualizarVeiculo_quilometragemMenorQueAtual_aindaAtualiza() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        veiculoService.atualizarVeiculo(1L, null, null, 500L);

        assertEquals(500L, veiculo.getQuilometragem());
    }

    @Test
    void atualizarVeiculo_quandoNaoExiste_lancaExcecao() {
        when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> veiculoService.atualizarVeiculo(99L, "Ducato", 2022, 5000L));

        verify(veiculoRepository, never()).save(any(Veiculo.class));
    }

    @Test
    void atualizarQuilometragem_acimaDoLimite_notificaAlertaDeManutencao() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));
        when(manutencaoPreventivaService.precisaDeManutencao(1L)).thenReturn(true);

        Veiculo atualizado = veiculoService.atualizarQuilometragem(1L, 60000L);

        assertEquals(60000L, atualizado.getQuilometragem());
        verify(veiculoNotificador).notificar("ALERTA_MANUTENCAO",
                "Veículo ABC1234 atingiu 60000 km. Agendar manutenção preventiva.");
    }

    @Test
    void atualizarQuilometragem_abaixoDoLimite_naoNotifica() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));
        when(manutencaoPreventivaService.precisaDeManutencao(1L)).thenReturn(false);

        Veiculo atualizado = veiculoService.atualizarQuilometragem(1L, 2000L);

        assertEquals(2000L, atualizado.getQuilometragem());
        verifyNoInteractions(veiculoNotificador);
    }

    @Test
    void atualizarQuilometragem_menorQueAtual_atualizaSemNotificar() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));
        when(manutencaoPreventivaService.precisaDeManutencao(1L)).thenReturn(false);

        Veiculo atualizado = veiculoService.atualizarQuilometragem(1L, 500L);

        assertEquals(500L, atualizado.getQuilometragem());
        verifyNoInteractions(veiculoNotificador);
    }

    @Test
    void atualizarQuilometragem_quilometragemInvalida_propagaErroSemSalvar() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
        doThrow(new RuntimeException("Quilometragem não pode ser negativa"))
                .when(veiculoValidator).validarQuilometragem(-1L);

        assertThrows(RuntimeException.class, () -> veiculoService.atualizarQuilometragem(1L, -1L));

        verify(veiculoRepository, never()).save(any(Veiculo.class));
    }

    @Test
    void atualizarQuilometragem_quandoNaoExiste_lancaExcecao() {
        when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> veiculoService.atualizarQuilometragem(99L, 1000L));

        verify(veiculoRepository, never()).save(any(Veiculo.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ATIVO", "INATIVO", "MANUTENCAO"})
    void obterVeiculosPorStatus_statusValido_devolveVeiculos(String status) {
        when(veiculoRepository.findByStatus(status)).thenReturn(List.of(veiculo));

        assertEquals(List.of(veiculo), veiculoService.obterVeiculosPorStatus(status));
    }

    @Test
    void obterVeiculosPorStatus_statusInvalido_propagaErroSemConsultar() {
        doThrow(new RuntimeException("Status inválido: DESCONHECIDO"))
                .when(veiculoValidator).validarStatus("DESCONHECIDO");

        assertThrows(RuntimeException.class, () -> veiculoService.obterVeiculosPorStatus("DESCONHECIDO"));

        verifyNoInteractions(veiculoRepository);
    }

    @Test
    void desativarVeiculo_marcaComoInativoENotifica() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        Veiculo desativado = veiculoService.desativarVeiculo(1L);

        assertEquals("INATIVO", desativado.getStatus());
        assertNotNull(desativado.getDataAtualizacao());
        verify(veiculoNotificador).notificar("VEICULO_DESATIVADO", "Veículo ABC1234 foi desativado");
    }

    @Test
    void desativarVeiculo_quandoNaoExiste_lancaExcecao() {
        when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> veiculoService.desativarVeiculo(99L));

        verify(veiculoRepository, never()).save(any(Veiculo.class));
    }

    @Test
    void reativarVeiculo_marcaComoAtivoSemNotificar() {
        veiculo.setStatus("INATIVO");
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        Veiculo reativado = veiculoService.reativarVeiculo(1L);

        assertEquals("ATIVO", reativado.getStatus());
        assertNotNull(reativado.getDataAtualizacao());
        verifyNoInteractions(veiculoNotificador);
    }

    @Test
    void reativarVeiculo_quandoNaoExiste_lancaExcecao() {
        when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> veiculoService.reativarVeiculo(99L));

        verify(veiculoRepository, never()).save(any(Veiculo.class));
    }

    @Test
    void obterEstatisticasFreita_montaJsonComContagensPorStatus() {
        when(veiculoRepository.count()).thenReturn(10L);
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(veiculo, veiculo, veiculo));
        when(veiculoRepository.findByStatus("INATIVO")).thenReturn(List.of(veiculo));
        when(veiculoRepository.findByStatus("MANUTENCAO")).thenReturn(List.of());

        String estatisticas = veiculoService.obterEstatisticasFreita();

        assertEquals("{\"total\": 10, \"ativos\": 3, \"inativos\": 1, \"em_manutencao\": 0}", estatisticas);
    }

    @Test
    void sincronizarComSistemaExterno_naoInteragemComDependencias() {
        veiculoService.sincronizarComSistemaExterno();

        verifyNoInteractions(veiculoRepository, veiculoValidator, veiculoNotificador, manutencaoPreventivaService);
    }
}
