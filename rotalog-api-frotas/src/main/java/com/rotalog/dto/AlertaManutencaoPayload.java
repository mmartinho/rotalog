package com.rotalog.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Corpo do POST /api/notificacoes/alertas-manutencao (api-notificacoes)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AlertaManutencaoPayload {
    private Long veiculoId;
    private String placa;
    private String motivo;
    private Long kmDesdeUltimaPreventiva;
    private LocalDateTime dataReferencia;
    private String emailGestor;
}
