package com.rotalog.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CorrelationIdTest {

    @AfterEach
    void limparMdc() {
        MDC.clear();
    }

    @Test
    void filtro_geraCorrelationIdNoMdcEDevolveNoResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] visto = new String[1];

        new CorrelationIdFilter().doFilter(new MockHttpServletRequest(), response,
                (req, res) -> visto[0] = MDC.get(CorrelationIdFilter.MDC_KEY));

        assertNotNull(visto[0]);
        assertEquals(visto[0], response.getHeader(CorrelationIdFilter.HEADER));
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    @Test
    void filtro_headerInvalido_ehSubstituido() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "valor com espaço\nmalicioso");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CorrelationIdFilter().doFilter(request, response, new MockFilterChain());

        assertNotNull(response.getHeader(CorrelationIdFilter.HEADER));
        assertEquals(36, response.getHeader(CorrelationIdFilter.HEADER).length());
    }

    @Test
    void restTemplate_repassaCorrelationIdDoMdcNoHeader() {
        RestTemplate restTemplate = new RestTemplateConfig().restTemplate(new RestTemplateBuilder(), 2000L, 5000L);
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo("http://notificacoes.test/x"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(CorrelationIdFilter.HEADER, "abc-123"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        MDC.put(CorrelationIdFilter.MDC_KEY, "abc-123");
        restTemplate.postForObject("http://notificacoes.test/x", "{}", String.class);

        server.verify();
    }
}
