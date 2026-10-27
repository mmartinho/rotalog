package com.rotalog.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resumo devolvido por POST /manutencoes/alertas/verificar
 */
@Getter
@Setter
@NoArgsConstructor
public class ResumoVerificacaoAlertas {
    private int verificados;
    private int elegiveis;
    private int novosAlertas;
    private int enviados;
    private int falhas;
    private int pendentes;
}
