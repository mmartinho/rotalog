package com.rotalog.service;

import org.springframework.stereotype.Component;

/**
 * VeiculoValidator - regras de validação dos dados do veículo.
 * Extraído de VeiculoService (ADR 0001, problema 1).
 */
@Component
public class VeiculoValidator {

    public void validarPlaca(String placa) {
        if (placa == null || placa.isEmpty()) {
            throw new RuntimeException("Placa é obrigatória"); // FIXME: Use proper exception
        }

        if (placa.length() != 7) {
            throw new RuntimeException("Placa deve ter 7 caracteres"); // FIXME: Use proper exception
        }
    }

    public void validarModelo(String modelo) {
        if (modelo == null || modelo.isEmpty()) {
            throw new RuntimeException("Modelo é obrigatório"); // FIXME: Use proper exception
        }
    }

    public void validarAno(Integer anoFabricacao) {
        if (anoFabricacao == null || anoFabricacao < 1900 || anoFabricacao > 2100) {
            throw new RuntimeException("Ano de fabricação inválido"); // FIXME: Use proper exception
        }
    }

    public void validarQuilometragem(Long novaQuilometragem) {
        if (novaQuilometragem < 0) {
            throw new RuntimeException("Quilometragem não pode ser negativa"); // FIXME: Use proper exception
        }
    }

    public void validarStatus(String status) {
        if (!status.equals("ATIVO") && !status.equals("INATIVO") && !status.equals("MANUTENCAO")) {
            throw new RuntimeException("Status inválido: " + status); // FIXME: Use proper exception
        }
    }
}
