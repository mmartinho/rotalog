package com.rotalog.dto;

import com.rotalog.domain.StatusAlerta;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Resultado do envio do alerta à api-notificacoes, já convertido para o status do alerta.
 */
@Getter
@AllArgsConstructor
public class ResultadoNotificacao {
    private final StatusAlerta status;
    private final Long notificacaoId;
    private final String mensagemErro;
}
