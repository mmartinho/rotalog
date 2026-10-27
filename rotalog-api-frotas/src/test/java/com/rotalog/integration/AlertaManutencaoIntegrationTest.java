package com.rotalog.integration;

import com.rotalog.domain.AlertaManutencao;
import com.rotalog.domain.Manutencao;
import com.rotalog.domain.MotivoAlerta;
import com.rotalog.domain.StatusAlerta;
import com.rotalog.domain.Veiculo;
import com.rotalog.repository.AlertaManutencaoRepository;
import com.rotalog.repository.ManutencaoRepository;
import com.rotalog.repository.VeiculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integração do alerta de manutenção preventiva com PostgreSQL real (Testcontainers).
 * O schema vem das migrations V1 e V3 e é validado pelo Hibernate (ddl-auto=validate).
 * A api-notificacoes é simulada com MockRestServiceServer no RestTemplate real da aplicação.
 * Sem Docker, a classe é ignorada.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class AlertaManutencaoIntegrationTest {

    private static final String URL_ALERTA = "http://notificacoes.test/api/notificacoes/alertas-manutencao";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:14");

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) throws Exception {
        criarSchema();
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=frotas");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("rotalog.alertas.email-gestor", () -> "gestor@teste.com");
        registry.add("rotalog.notificacoes.url", () -> "http://notificacoes.test");
    }

    // Mesmo schema do ambiente: V1 (tabelas) e V3 (alertas) dentro do schema frotas
    private static void criarSchema() throws SQLException, IOException {
        try (Connection conexao = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = conexao.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS frotas");
            statement.execute("SET search_path TO frotas");
            for (String migration : List.of("V1__Initial_Schema.sql", "V3__Alertas_Manutencao.sql")) {
                String sql = new String(Files.readAllBytes(
                        Paths.get("src/main/resources/db/migration", migration)), StandardCharsets.UTF_8);
                statement.execute(sql);
            }
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private VeiculoRepository veiculoRepository;

    @Autowired
    private ManutencaoRepository manutencaoRepository;

    @Autowired
    private AlertaManutencaoRepository alertaManutencaoRepository;

    private MockRestServiceServer notificacoes;

    @BeforeEach
    void setUp() {
        alertaManutencaoRepository.deleteAll();
        manutencaoRepository.deleteAll();
        veiculoRepository.deleteAll();
        notificacoes = MockRestServiceServer.bindTo(restTemplate).build();
    }

    private Veiculo veiculo(String placa, String status, long km, LocalDateTime cadastro) {
        Veiculo veiculo = new Veiculo();
        veiculo.setPlaca(placa);
        veiculo.setModelo("Modelo");
        veiculo.setStatus(status);
        veiculo.setQuilometragem(km);
        veiculo.setDataCadastro(cadastro);
        veiculo.setDataAtualizacao(cadastro);
        return veiculoRepository.save(veiculo);
    }

    private Manutencao manutencao(Long veiculoId, String tipo, String status, Long km, LocalDateTime data) {
        Manutencao manutencao = new Manutencao();
        manutencao.setVeiculoId(veiculoId);
        manutencao.setTipoManutencao(tipo);
        manutencao.setStatus(status);
        manutencao.setQuilometragemManutencao(km);
        manutencao.setDataManutencao(data);
        manutencao.setDataCriacao(LocalDateTime.now());
        manutencao.setDataAtualizacao(LocalDateTime.now());
        return manutencaoRepository.save(manutencao);
    }

    private AlertaManutencao alerta(Long veiculoId, StatusAlerta status, LocalDateTime criacao) {
        AlertaManutencao alerta = new AlertaManutencao();
        alerta.setVeiculoId(veiculoId);
        alerta.setMotivo(MotivoAlerta.KM);
        alerta.setKmDesdeUltimaPreventiva(60000L);
        alerta.setDataReferencia(criacao.minusMonths(2));
        alerta.setStatus(status);
        alerta.setDataCriacao(criacao);
        alerta.setDataAtualizacao(criacao);
        return alertaManutencaoRepository.save(alerta);
    }

    private void notificacoesResponde(String corpo) {
        notificacoes.expect(ExpectedCount.manyTimes(), requestTo(URL_ALERTA))
                .andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));
    }

    // ---------- Queries dos repositories ----------

    @Test
    void ultimaPreventivaConcluida_ignoraOutrosTiposEStatusEPegaAMaisRecente() {
        Veiculo v = veiculo("ABC1D23", "ATIVO", 100000L, LocalDateTime.now().minusYears(2));
        LocalDateTime agora = LocalDateTime.now();
        manutencao(v.getId(), "PREVENTIVA", "CONCLUIDA", 20000L, agora.minusMonths(10));
        Manutencao maisRecente = manutencao(v.getId(), "PREVENTIVA", "CONCLUIDA", 50000L, agora.minusMonths(4));
        manutencao(v.getId(), "PREVENTIVA", "PENDENTE", null, agora.minusDays(1));
        manutencao(v.getId(), "CORRETIVA", "CONCLUIDA", 90000L, agora.minusDays(2));

        Manutencao resultado = manutencaoRepository
                .findFirstByVeiculoIdAndTipoManutencaoAndStatusOrderByDataManutencaoDesc(v.getId(), "PREVENTIVA", "CONCLUIDA")
                .orElseThrow();

        assertEquals(maisRecente.getId(), resultado.getId());
    }

    @Test
    void ultimaPreventivaConcluida_semPreventiva_devolveVazio() {
        Veiculo v = veiculo("ABC1D23", "ATIVO", 1000L, LocalDateTime.now());
        manutencao(v.getId(), "CORRETIVA", "CONCLUIDA", 900L, LocalDateTime.now());

        assertTrue(manutencaoRepository
                .findFirstByVeiculoIdAndTipoManutencaoAndStatusOrderByDataManutencaoDesc(v.getId(), "PREVENTIVA", "CONCLUIDA")
                .isEmpty());
    }

    @Test
    void alertaAberto_consideraStatusEDataDeCriacao() {
        Veiculo v = veiculo("ABC1D23", "ATIVO", 1000L, LocalDateTime.now());
        LocalDateTime referencia = LocalDateTime.now().minusMonths(1);
        Set<StatusAlerta> abertos = Set.of(StatusAlerta.ENVIADA, StatusAlerta.PENDENTE);

        alerta(v.getId(), StatusAlerta.FALHA, LocalDateTime.now());
        assertFalse(alertaManutencaoRepository
                .existsByVeiculoIdAndStatusInAndDataCriacaoAfter(v.getId(), abertos, referencia));

        alerta(v.getId(), StatusAlerta.ENVIADA, LocalDateTime.now().minusMonths(2)); // anterior à referência
        assertFalse(alertaManutencaoRepository
                .existsByVeiculoIdAndStatusInAndDataCriacaoAfter(v.getId(), abertos, referencia));

        alerta(v.getId(), StatusAlerta.PENDENTE, LocalDateTime.now().minusDays(1));
        assertTrue(alertaManutencaoRepository
                .existsByVeiculoIdAndStatusInAndDataCriacaoAfter(v.getId(), abertos, referencia));
        assertFalse(alertaManutencaoRepository
                .existsByVeiculoIdAndStatusInAndDataCriacaoAfter(v.getId() + 1, abertos, referencia));
    }

    @Test
    void listagemDosAlertas_ordenadaDoMaisRecenteEFiltradaPorStatus() {
        Veiculo v = veiculo("ABC1D23", "ATIVO", 1000L, LocalDateTime.now());
        AlertaManutencao antigo = alerta(v.getId(), StatusAlerta.ENVIADA, LocalDateTime.now().minusDays(3));
        AlertaManutencao pendente = alerta(v.getId(), StatusAlerta.PENDENTE, LocalDateTime.now().minusDays(2));
        AlertaManutencao recente = alerta(v.getId(), StatusAlerta.ENVIADA, LocalDateTime.now().minusDays(1));

        List<AlertaManutencao> todos = alertaManutencaoRepository.findAllByOrderByDataCriacaoDesc();
        List<AlertaManutencao> enviados = alertaManutencaoRepository.findByStatusOrderByDataCriacaoDesc(StatusAlerta.ENVIADA);

        assertEquals(List.of(recente.getId(), pendente.getId(), antigo.getId()),
                todos.stream().map(AlertaManutencao::getId).collect(java.util.stream.Collectors.toList()));
        assertEquals(List.of(recente.getId(), antigo.getId()),
                enviados.stream().map(AlertaManutencao::getId).collect(java.util.stream.Collectors.toList()));
    }

    // ---------- Fluxo completo (controller -> service -> banco -> client HTTP) ----------

    @Test
    void verificar_gravaAlertaEnviadaComDadosDoVeiculoEListaPorStatus() throws Exception {
        Veiculo elegivel = veiculo("ABC1D23", "ATIVO", 130000L, LocalDateTime.now().minusYears(2));
        manutencao(elegivel.getId(), "PREVENTIVA", "CONCLUIDA", 70000L, LocalDateTime.now().minusMonths(2));
        Veiculo naoElegivel = veiculo("DEF4G56", "ATIVO", 52000L, LocalDateTime.now().minusYears(2));
        manutencao(naoElegivel.getId(), "PREVENTIVA", "CONCLUIDA", 40000L, LocalDateTime.now().minusMonths(1));
        veiculo("GHI7J89", "INATIVO", 200000L, LocalDateTime.now().minusYears(3));

        notificacoes.expect(ExpectedCount.once(), requestTo(URL_ALERTA))
                .andExpect(MockRestRequestMatchers.jsonPath("$.veiculoId").value(elegivel.getId()))
                .andExpect(MockRestRequestMatchers.jsonPath("$.placa").value("ABC1D23"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.motivo").value("KM"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.kmDesdeUltimaPreventiva").value(60000))
                .andExpect(MockRestRequestMatchers.jsonPath("$.emailGestor").value("gestor@teste.com"))
                .andRespond(withSuccess("{\"notificacaoId\":77,\"status\":\"ENVIADA\"}", MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/manutencoes/alertas/verificar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificados").value(2))
                .andExpect(jsonPath("$.elegiveis").value(1))
                .andExpect(jsonPath("$.novosAlertas").value(1))
                .andExpect(jsonPath("$.enviados").value(1))
                .andExpect(jsonPath("$.pendentes").value(0))
                .andExpect(jsonPath("$.falhas").value(0));

        mockMvc.perform(get("/manutencoes/alertas").param("status", "ENVIADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].placa").value("ABC1D23"))
                .andExpect(jsonPath("$[0].motivo").value("KM"))
                .andExpect(jsonPath("$[0].kmDesdeUltimaPreventiva").value(60000))
                .andExpect(jsonPath("$[0].status").value("ENVIADA"))
                .andExpect(jsonPath("$[0].notificacaoId").value(77));
        mockMvc.perform(get("/manutencoes/alertas").param("status", "PENDENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        notificacoes.verify();
    }

    @Test
    void verificar_segundaChamada_naoDuplicaAlertas() throws Exception {
        Veiculo v = veiculo("ABC1D23", "ATIVO", 130000L, LocalDateTime.now().minusYears(2));
        manutencao(v.getId(), "PREVENTIVA", "CONCLUIDA", 70000L, LocalDateTime.now().minusMonths(2));
        notificacoesResponde("{\"notificacaoId\":1,\"status\":\"ENVIADA\"}");

        mockMvc.perform(post("/manutencoes/alertas/verificar")).andExpect(jsonPath("$.novosAlertas").value(1));
        mockMvc.perform(post("/manutencoes/alertas/verificar"))
                .andExpect(jsonPath("$.elegiveis").value(1))
                .andExpect(jsonPath("$.novosAlertas").value(0));

        assertEquals(1, alertaManutencaoRepository.count());
    }

    @Test
    void verificar_notificacoesForaDoAr_gravaAlertaPendente() throws Exception {
        Veiculo v = veiculo("ABC1D23", "ATIVO", 130000L, LocalDateTime.now().minusYears(2));
        manutencao(v.getId(), "PREVENTIVA", "CONCLUIDA", 70000L, LocalDateTime.now().minusMonths(2));
        notificacoes.expect(ExpectedCount.manyTimes(), requestTo(URL_ALERTA)).andRespond(request -> {
            throw new ConnectException("Connection refused");
        });

        mockMvc.perform(post("/manutencoes/alertas/verificar"))
                .andExpect(jsonPath("$.pendentes").value(1));

        mockMvc.perform(get("/manutencoes/alertas").param("status", "PENDENTE"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDENTE"));
    }

    @Test
    void verificar_notificacoesComErro_gravaAlertaFalhaETentaDeNovoNaProximaVerificacao() throws Exception {
        Veiculo v = veiculo("ABC1D23", "ATIVO", 130000L, LocalDateTime.now().minusYears(2));
        manutencao(v.getId(), "PREVENTIVA", "CONCLUIDA", 70000L, LocalDateTime.now().minusMonths(2));
        notificacoes.expect(ExpectedCount.manyTimes(), requestTo(URL_ALERTA)).andRespond(withServerError());

        mockMvc.perform(post("/manutencoes/alertas/verificar")).andExpect(jsonPath("$.falhas").value(1));
        mockMvc.perform(post("/manutencoes/alertas/verificar")).andExpect(jsonPath("$.falhas").value(1));

        // FALHA não bloqueia: cada verificação gera um novo alerta
        assertEquals(2, alertaManutencaoRepository.findByStatusOrderByDataCriacaoDesc(StatusAlerta.FALHA).size());
    }

    @Test
    void listar_statusInvalido_devolve400() throws Exception {
        mockMvc.perform(get("/manutencoes/alertas").param("status", "XPTO"))
                .andExpect(status().isBadRequest());
    }
}
