package com.rotalog.domain;

/**
 * Status do alerta de manutenção preventiva.
 * ENVIADA: api-notificacoes enviou o e-mail.
 * FALHA: api-notificacoes respondeu com falha ou erro.
 * PENDENTE: api-notificacoes fora do ar (conexão recusada ou timeout).
 */
public enum StatusAlerta {
    ENVIADA,
    FALHA,
    PENDENTE
}
