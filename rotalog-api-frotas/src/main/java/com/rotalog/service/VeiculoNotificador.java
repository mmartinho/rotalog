package com.rotalog.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * VeiculoNotificador - envia notificações de veículo ao gestor.
 * Extraído de VeiculoService (ADR 0001, problema 1).
 * A falha de envio é registrada em log e não é propagada.
 */
@Slf4j
@Component
public class VeiculoNotificador {

    private static final String DESTINATARIO_GESTOR = "gestor@rotalog.com";

    @Autowired
    private NotificacaoClient notificacaoClient;

    public void notificar(String tipo, String mensagem) {
        try {
            notificacaoClient.enviarNotificacao(tipo, DESTINATARIO_GESTOR, mensagem);
        } catch (Exception e) {
            log.error("Falha ao enviar notificação {}: {}", tipo, e.getMessage());
        }
    }
}
