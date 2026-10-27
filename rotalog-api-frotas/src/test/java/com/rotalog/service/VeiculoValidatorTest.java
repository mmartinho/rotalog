package com.rotalog.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VeiculoValidatorTest {

    private final VeiculoValidator validator = new VeiculoValidator();

    @ParameterizedTest
    @NullAndEmptySource
    void validarPlaca_ausente_lancaExcecao(String placa) {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> validator.validarPlaca(placa));

        assertEquals("Placa é obrigatória", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC123", "ABC12345"})
    void validarPlaca_tamanhoDiferenteDeSete_lancaExcecao(String placa) {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> validator.validarPlaca(placa));

        assertEquals("Placa deve ter 7 caracteres", ex.getMessage());
    }

    @Test
    void validarPlaca_comSeteCaracteres_aceita() {
        assertDoesNotThrow(() -> validator.validarPlaca("ABC1234"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void validarModelo_ausente_lancaExcecao(String modelo) {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> validator.validarModelo(modelo));

        assertEquals("Modelo é obrigatório", ex.getMessage());
    }

    @Test
    void validarModelo_preenchido_aceita() {
        assertDoesNotThrow(() -> validator.validarModelo("Fiorino"));
    }

    @Test
    void validarAno_nulo_lancaExcecao() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> validator.validarAno(null));

        assertEquals("Ano de fabricação inválido", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {1899, 2101})
    void validarAno_foraDoIntervalo_lancaExcecao(int ano) {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> validator.validarAno(ano));

        assertEquals("Ano de fabricação inválido", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {1900, 2100})
    void validarAno_nasBordas_aceita(int ano) {
        assertDoesNotThrow(() -> validator.validarAno(ano));
    }

    @Test
    void validarQuilometragem_negativa_lancaExcecao() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> validator.validarQuilometragem(-1L));

        assertEquals("Quilometragem não pode ser negativa", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1000L})
    void validarQuilometragem_naoNegativa_aceita(long km) {
        assertDoesNotThrow(() -> validator.validarQuilometragem(km));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ATIVO", "INATIVO", "MANUTENCAO"})
    void validarStatus_valido_aceita(String status) {
        assertDoesNotThrow(() -> validator.validarStatus(status));
    }

    @Test
    void validarStatus_invalido_lancaExcecao() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> validator.validarStatus("DESCONHECIDO"));

        assertEquals("Status inválido: DESCONHECIDO", ex.getMessage());
    }
}
