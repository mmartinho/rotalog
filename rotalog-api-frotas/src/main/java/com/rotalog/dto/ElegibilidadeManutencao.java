package com.rotalog.dto;

import com.rotalog.domain.MotivoAlerta;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Resultado da avaliação de um veículo elegível para manutenção preventiva.
 */
@Getter
@AllArgsConstructor
public class ElegibilidadeManutencao {
    private final MotivoAlerta motivo;
    private final Long kmDesdeUltimaPreventiva;
    private final LocalDateTime dataReferencia; // última preventiva ou data de cadastro
}
