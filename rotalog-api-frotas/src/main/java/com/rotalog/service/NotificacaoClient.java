package com.rotalog.service;

import com.rotalog.domain.StatusAlerta;
import com.rotalog.dto.AlertaManutencaoPayload;
import com.rotalog.dto.AlertaNotificacaoResponse;
import com.rotalog.dto.ResultadoNotificacao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * NotificacaoClient - HTTP client for api-notificacoes
 * 
 * FIXME: Sem circuit breaker
 * FIXME: Sem retry logic
 */
@Slf4j
@Component
public class NotificacaoClient {

    private final RestTemplate restTemplate;
    private final String notificacaoApiUrl;

    public NotificacaoClient(RestTemplate restTemplate, @Value("${rotalog.notificacoes.url}") String notificacaoApiUrl) {
        this.restTemplate = restTemplate;
        this.notificacaoApiUrl = notificacaoApiUrl;
    }

    /**
     * Envia o alerta de manutenção preventiva e converte a resposta no status do alerta:
     * 200 ENVIADA -> ENVIADA; 200 FALHA ou resposta 4xx/5xx -> FALHA;
     * conexão recusada ou timeout (ResourceAccessException) -> PENDENTE.
     */
    public ResultadoNotificacao enviarAlertaManutencao(AlertaManutencaoPayload payload) {
        String url = notificacaoApiUrl + "/api/notificacoes/alertas-manutencao";
        try {
            log.info("Chamando api-notificacoes para alerta de manutenção: {}, {}, {}",
                    kv("veiculoId", payload.getVeiculoId()), kv("placa", payload.getPlaca()), kv("url", url));

            ResponseEntity<AlertaNotificacaoResponse> entity =
                    restTemplate.postForEntity(url, payload, AlertaNotificacaoResponse.class);
            AlertaNotificacaoResponse resposta = entity.getBody();
            log.info("Resposta da api-notificacoes: {}, {}, {}, {}",
                    kv("veiculoId", payload.getVeiculoId()),
                    kv("httpStatus", entity.getStatusCodeValue()),
                    kv("statusAlerta", resposta != null ? resposta.getStatus() : null),
                    kv("notificacaoId", resposta != null ? resposta.getNotificacaoId() : null));

            if (resposta != null && "ENVIADA".equals(resposta.getStatus())) {
                return new ResultadoNotificacao(StatusAlerta.ENVIADA, resposta.getNotificacaoId(), null);
            }
            if (resposta == null) {
                return new ResultadoNotificacao(StatusAlerta.FALHA, null, "Resposta vazia da api-notificacoes");
            }
            return new ResultadoNotificacao(StatusAlerta.FALHA, resposta.getNotificacaoId(), resposta.getMensagemErro());
        } catch (ResourceAccessException e) {
            log.warn("api-notificacoes indisponível, alerta ficará PENDENTE: {}, {}",
                    kv("veiculoId", payload.getVeiculoId()), kv("erro", e.getMessage()));
            return new ResultadoNotificacao(StatusAlerta.PENDENTE, null, e.getMessage());
        } catch (RestClientException e) {
            Integer httpStatus = e instanceof RestClientResponseException
                    ? ((RestClientResponseException) e).getRawStatusCode() : null;
            log.error("Erro ao enviar alerta de manutenção: {}, {}, {}",
                    kv("veiculoId", payload.getVeiculoId()), kv("httpStatus", httpStatus), kv("erro", e.getMessage()));
            return new ResultadoNotificacao(StatusAlerta.FALHA, null, e.getMessage());
        }
    }

    /**
     * Envia notificação para api-notificacoes
     * 
     * FIXME: Sem retry
     * FIXME: Sem fallback
     * FIXME: Sem validação de resposta
     * FIXME: Engolindo exceções no chamador
     */
    public void enviarNotificacao(String tipo, String destinatario, String mensagem) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = new HashMap<>();
            body.put("tipo", tipo);
            body.put("destinatario", destinatario);
            body.put("mensagem", mensagem);
            body.put("canal", "email"); // FIXME: canal hardcoded

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            String url = notificacaoApiUrl + "/api/notificacoes";
            log.info("Enviando notificação para {}: tipo={}, destinatario={}", url, tipo, destinatario);

            restTemplate.postForEntity(url, request, String.class);

            log.info("Notificação enviada com sucesso: tipo={}", tipo);
        } catch (Exception e) {
            // FIXME: Engolindo exceção - apenas logando
            log.error("Erro ao enviar notificação: tipo={}, erro={}", tipo, e.getMessage());
            // FIXME: Sem retry, sem fallback, sem dead letter queue
            throw e; // re-throw para o chamador decidir o que fazer
        }
    }

    /**
     * Envia notificação SMS
     * 
     * FIXME: Código duplicado com enviarNotificacao
     * FIXME: Deveria ser o mesmo método com canal diferente
     */
    public void enviarSms(String destinatario, String mensagem) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = new HashMap<>();
            body.put("tipo", "SMS");
            body.put("destinatario", destinatario);
            body.put("mensagem", mensagem);
            body.put("canal", "sms");

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            String url = notificacaoApiUrl + "/api/notificacoes";
            restTemplate.postForEntity(url, request, String.class);
        } catch (Exception e) {
            log.error("Erro ao enviar SMS: {}", e.getMessage());
        }
    }
}
