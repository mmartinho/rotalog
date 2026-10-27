package com.rotalog.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resposta da api-notificacoes ao alerta de manutenção (status ENVIADA ou FALHA)
 */
@Getter
@Setter
@NoArgsConstructor
public class AlertaNotificacaoResponse {
    private Long notificacaoId;
    private String status;
    private String mensagemErro;
}
