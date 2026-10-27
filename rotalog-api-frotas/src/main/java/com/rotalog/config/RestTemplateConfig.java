package com.rotalog.config;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate com timeouts, usado pelo NotificacaoClient.
 *
 * FIXME: EntregaClient e HealthCheckController ainda criam o RestTemplate manualmente
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(
            RestTemplateBuilder builder,
            @Value("${rotalog.notificacoes.connect-timeout-ms}") long connectTimeoutMs,
            @Value("${rotalog.notificacoes.read-timeout-ms}") long readTimeoutMs) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .additionalInterceptors(correlationIdInterceptor())
                .build();
    }

    /** Repassa o correlation-id da requisição corrente (MDC) no header X-Correlation-ID. */
    private ClientHttpRequestInterceptor correlationIdInterceptor() {
        return (request, body, execution) -> {
            String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (correlationId != null) {
                request.getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
            }
            return execution.execute(request, body);
        };
    }
}
