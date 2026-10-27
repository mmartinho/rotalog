package com.rotalog.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VeiculoNotificadorTest {

    @Mock
    private NotificacaoClient notificacaoClient;

    @InjectMocks
    private VeiculoNotificador veiculoNotificador;

    @Test
    void notificar_enviaParaOGestor() {
        veiculoNotificador.notificar("NOVO_VEICULO", "Novo veículo cadastrado: ABC1234 - Fiorino");

        verify(notificacaoClient).enviarNotificacao("NOVO_VEICULO", "gestor@rotalog.com",
                "Novo veículo cadastrado: ABC1234 - Fiorino");
    }

    @Test
    void notificar_quandoEnvioFalha_naoPropagaExcecao() {
        doThrow(new RuntimeException("api indisponível"))
                .when(notificacaoClient).enviarNotificacao(anyString(), anyString(), anyString());

        assertDoesNotThrow(() -> veiculoNotificador.notificar("VEICULO_DESATIVADO", "Veículo ABC1234 foi desativado"));
    }
}
