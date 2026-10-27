package com.rotalog.controller;

import com.rotalog.domain.StatusAlerta;
import com.rotalog.dto.AlertaManutencaoResponse;
import com.rotalog.dto.ResumoVerificacaoAlertas;
import com.rotalog.service.AlertaManutencaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AlertaManutencaoControllerTest {

    @Mock
    private AlertaManutencaoService alertaManutencaoService;

    @InjectMocks
    private AlertaManutencaoController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private AlertaManutencaoResponse alerta(String status) {
        AlertaManutencaoResponse response = new AlertaManutencaoResponse();
        response.setId(1L);
        response.setPlaca("ABC1D23");
        response.setMotivo("KM");
        response.setStatus(status);
        return response;
    }

    @Test
    void listar_semFiltro_devolveTodos() throws Exception {
        when(alertaManutencaoService.listarAlertas(null)).thenReturn(List.of(alerta("ENVIADA"), alerta("PENDENTE")));

        mockMvc.perform(get("/manutencoes/alertas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].placa").value("ABC1D23"));
    }

    @Test
    void listar_comStatus_filtraPorStatus() throws Exception {
        when(alertaManutencaoService.listarAlertas(StatusAlerta.PENDENTE)).thenReturn(List.of(alerta("PENDENTE")));

        mockMvc.perform(get("/manutencoes/alertas").param("status", "PENDENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDENTE"));
    }

    @Test
    void listar_statusEmMinuscula_ePermitido() throws Exception {
        when(alertaManutencaoService.listarAlertas(StatusAlerta.FALHA)).thenReturn(List.of(alerta("FALHA")));

        mockMvc.perform(get("/manutencoes/alertas").param("status", "falha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("FALHA"));
    }

    @Test
    void listar_statusInvalido_devolve400() throws Exception {
        mockMvc.perform(get("/manutencoes/alertas").param("status", "XPTO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());

        verify(alertaManutencaoService, never()).listarAlertas(any());
    }

    @Test
    void verificar_devolveResumo() throws Exception {
        ResumoVerificacaoAlertas resumo = new ResumoVerificacaoAlertas();
        resumo.setVerificados(8);
        resumo.setElegiveis(5);
        resumo.setNovosAlertas(4);
        resumo.setEnviados(3);
        resumo.setFalhas(0);
        resumo.setPendentes(1);
        when(alertaManutencaoService.verificarAlertas()).thenReturn(resumo);

        mockMvc.perform(post("/manutencoes/alertas/verificar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificados").value(8))
                .andExpect(jsonPath("$.elegiveis").value(5))
                .andExpect(jsonPath("$.novosAlertas").value(4))
                .andExpect(jsonPath("$.enviados").value(3))
                .andExpect(jsonPath("$.falhas").value(0))
                .andExpect(jsonPath("$.pendentes").value(1));
    }

    @Test
    void verificar_erroInesperado_devolve500() throws Exception {
        when(alertaManutencaoService.verificarAlertas()).thenThrow(new RuntimeException("banco indisponível"));

        mockMvc.perform(post("/manutencoes/alertas/verificar"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.erro").value("banco indisponível"));
    }
}
