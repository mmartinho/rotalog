package com.rotalog.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguracaoAlertaTest {

    @Test
    void alertaProperties_valoresPadrao() {
        AlertaProperties properties = new AlertaProperties();

        assertEquals(50000L, properties.getLimiteKm());
        assertEquals(6, properties.getIntervaloMeses());
    }

    @Test
    void alertaProperties_emailDefinido_naoLancaExcecao() {
        AlertaProperties properties = new AlertaProperties();
        properties.setEmailGestor("gestor@rotalog.com");

        assertDoesNotThrow(properties::validar);
    }

    @Test
    void alertaProperties_emailAusente_falhaComMensagemClara() {
        AlertaProperties properties = new AlertaProperties();

        IllegalStateException ex = assertThrows(IllegalStateException.class, properties::validar);

        assertTrue(ex.getMessage().contains("ROTALOG_EMAIL_GESTOR"));
    }

    @Test
    void alertaProperties_emailEmBranco_falha() {
        AlertaProperties properties = new AlertaProperties();
        properties.setEmailGestor("   ");

        assertThrows(IllegalStateException.class, properties::validar);
    }

    @Test
    void restTemplate_aplicaOsTimeoutsConfigurados() {
        RestTemplate restTemplate = new RestTemplateConfig().restTemplate(new RestTemplateBuilder(), 2000L, 5000L);

        // O interceptor de correlation-id embrulha a factory que carrega os timeouts
        ClientHttpRequestFactory factory = (ClientHttpRequestFactory)
                ReflectionTestUtils.getField(restTemplate.getRequestFactory(), "requestFactory");
        assertEquals(2000, ReflectionTestUtils.getField(factory, "connectTimeout"));
        assertEquals(5000, ReflectionTestUtils.getField(factory, "readTimeout"));
    }
}
