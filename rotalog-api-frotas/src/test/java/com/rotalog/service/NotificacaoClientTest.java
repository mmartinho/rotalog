package com.rotalog.service;

import com.rotalog.domain.StatusAlerta;
import com.rotalog.dto.AlertaManutencaoPayload;
import com.rotalog.dto.ResultadoNotificacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NotificacaoClientTest {

    private static final String URL = "http://notificacoes.test/api/notificacoes/alertas-manutencao";

    private MockRestServiceServer server;
    private NotificacaoClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        client = new NotificacaoClient(restTemplate, "http://notificacoes.test");
    }

    private AlertaManutencaoPayload payload() {
        return new AlertaManutencaoPayload(1L, "ABC1D23", "KM", 60000L,
                LocalDateTime.of(2026, 8, 16, 10, 0), "gestor@rotalog.com");
    }

    @Test
    void enviarAlertaManutencao_respostaEnviada_devolveEnviada() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.veiculoId").value(1))
                .andExpect(jsonPath("$.placa").value("ABC1D23"))
                .andExpect(jsonPath("$.motivo").value("KM"))
                .andExpect(jsonPath("$.kmDesdeUltimaPreventiva").value(60000))
                .andExpect(jsonPath("$.emailGestor").value("gestor@rotalog.com"))
                .andRespond(withSuccess("{\"notificacaoId\":42,\"status\":\"ENVIADA\"}", MediaType.APPLICATION_JSON));

        ResultadoNotificacao resultado = client.enviarAlertaManutencao(payload());

        assertEquals(StatusAlerta.ENVIADA, resultado.getStatus());
        assertEquals(42L, resultado.getNotificacaoId());
        assertNull(resultado.getMensagemErro());
        server.verify();
    }

    @Test
    void enviarAlertaManutencao_respostaFalha_devolveFalhaComMensagem() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"notificacaoId\":43,\"status\":\"FALHA\",\"mensagemErro\":\"SMTP timeout\"}",
                        MediaType.APPLICATION_JSON));

        ResultadoNotificacao resultado = client.enviarAlertaManutencao(payload());

        assertEquals(StatusAlerta.FALHA, resultado.getStatus());
        assertEquals(43L, resultado.getNotificacaoId());
        assertEquals("SMTP timeout", resultado.getMensagemErro());
    }

    @Test
    void enviarAlertaManutencao_erro5xx_devolveFalha() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        ResultadoNotificacao resultado = client.enviarAlertaManutencao(payload());

        assertEquals(StatusAlerta.FALHA, resultado.getStatus());
        assertNull(resultado.getNotificacaoId());
        assertTrue(resultado.getMensagemErro().contains("500"));
    }

    @Test
    void enviarAlertaManutencao_erro4xx_devolveFalha() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        ResultadoNotificacao resultado = client.enviarAlertaManutencao(payload());

        assertEquals(StatusAlerta.FALHA, resultado.getStatus());
    }

    @Test
    void enviarAlertaManutencao_conexaoRecusada_devolvePendente() {
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new ConnectException("Connection refused");
        });

        ResultadoNotificacao resultado = client.enviarAlertaManutencao(payload());

        assertEquals(StatusAlerta.PENDENTE, resultado.getStatus());
        assertNull(resultado.getNotificacaoId());
    }

    @Test
    void enviarAlertaManutencao_timeout_devolvePendente() {
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new SocketTimeoutException("Read timed out");
        });

        ResultadoNotificacao resultado = client.enviarAlertaManutencao(payload());

        assertEquals(StatusAlerta.PENDENTE, resultado.getStatus());
    }

    @Test
    void enviarAlertaManutencao_corpoVazio_devolveFalha() throws IOException {
        server.expect(requestTo(URL)).andRespond(withSuccess());

        ResultadoNotificacao resultado = client.enviarAlertaManutencao(payload());

        assertEquals(StatusAlerta.FALHA, resultado.getStatus());
    }
}
