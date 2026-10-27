const { describe, it } = require('node:test');
const assert = require('node:assert/strict');

const validador = require('../../src/validators/entregaValidator');
const { EntradaInvalidaError } = require('../../src/errors/errosDominio');

function esperarErro(fn, mensagem) {
    assert.throws(fn, (erro) => {
        assert.ok(erro instanceof EntradaInvalidaError);
        assert.equal(erro.status, 400);
        if (mensagem) assert.match(erro.message, mensagem);
        return true;
    });
}

const criacaoValida = {
    origem_endereco: 'Rua A, 1',
    destino_endereco: 'Rua B, 2',
};

describe('validarCriacao', () => {
    it('aceita o mínimo obrigatório', () => {
        assert.doesNotThrow(() => validador.validarCriacao(criacaoValida));
    });

    it('exige origem e destino', () => {
        esperarErro(() => validador.validarCriacao({ origem_endereco: 'x' }), /obrigatórios/);
        esperarErro(() => validador.validarCriacao({ destino_endereco: 'x' }), /obrigatórios/);
    });

    it('rejeita endereço que não é texto ou passa do limite', () => {
        esperarErro(() => validador.validarCriacao({ ...criacaoValida, origem_endereco: 123 }), /origem_endereco/);
        esperarErro(() => validador.validarCriacao({ ...criacaoValida, destino_endereco: 'x'.repeat(256) }), /destino_endereco/);
    });

    it('rejeita peso negativo, texto ou infinito', () => {
        esperarErro(() => validador.validarCriacao({ ...criacaoValida, peso_kg: -1 }), /peso_kg/);
        esperarErro(() => validador.validarCriacao({ ...criacaoValida, peso_kg: 'abc' }), /peso_kg/);
        esperarErro(() => validador.validarCriacao({ ...criacaoValida, peso_kg: Infinity }), /peso_kg/);
    });

    it('aceita peso zero e peso nulo', () => {
        assert.doesNotThrow(() => validador.validarCriacao({ ...criacaoValida, peso_kg: 0 }));
        assert.doesNotThrow(() => validador.validarCriacao({ ...criacaoValida, peso_kg: null }));
    });

    it('rejeita latitude fora de -90..90 e longitude fora de -180..180', () => {
        esperarErro(() => validador.validarCriacao({ ...criacaoValida, origem_lat: 91 }), /origem_lat/);
        esperarErro(() => validador.validarCriacao({ ...criacaoValida, destino_lat: -91 }), /destino_lat/);
        esperarErro(() => validador.validarCriacao({ ...criacaoValida, origem_lng: 181 }), /origem_lng/);
        esperarErro(() => validador.validarCriacao({ ...criacaoValida, destino_lng: '-46' }), /destino_lng/);
    });

    it('aceita coordenadas 0 nos limites da faixa', () => {
        assert.doesNotThrow(() => validador.validarCriacao({
            ...criacaoValida,
            origem_lat: 0, origem_lng: 0, destino_lat: 90, destino_lng: -180,
        }));
    });

    it('rejeita observações que não são texto', () => {
        esperarErro(() => validador.validarCriacao({ ...criacaoValida, observacoes: 42 }), /observacoes/);
    });
});

describe('validarAtualizacao', () => {
    it('ignora campos ausentes e endereço vazio', () => {
        assert.doesNotThrow(() => validador.validarAtualizacao({}));
        assert.doesNotThrow(() => validador.validarAtualizacao({ origem_endereco: '' }));
    });

    it('valida tipo de endereço, peso e observações quando enviados', () => {
        esperarErro(() => validador.validarAtualizacao({ origem_endereco: 5 }), /origem_endereco/);
        esperarErro(() => validador.validarAtualizacao({ destino_endereco: 5 }), /destino_endereco/);
        esperarErro(() => validador.validarAtualizacao({ peso_kg: 'x' }), /peso_kg/);
        esperarErro(() => validador.validarAtualizacao({ observacoes: 9 }), /observacoes/);
    });

    it('aceita observações nulas para limpar o campo', () => {
        assert.doesNotThrow(() => validador.validarAtualizacao({ observacoes: null }));
    });
});

describe('validarStatus', () => {
    it('aceita os cinco status conhecidos', () => {
        for (const status of validador.STATUS_VALIDOS) {
            assert.doesNotThrow(() => validador.validarStatus(status));
        }
    });

    it('rejeita status desconhecido e informa os válidos', () => {
        assert.throws(() => validador.validarStatus('INVENTADO'), (erro) => {
            assert.equal(erro.status, 400);
            assert.equal(erro.message, 'Status inválido');
            assert.deepEqual(erro.detalhes, { statusValidos: validador.STATUS_VALIDOS });
            return true;
        });
    });
});

describe('validarAtribuicao', () => {
    const atribuicaoValida = { veiculo_placa: 'ABC1D23', motorista_id: 3 };

    it('aceita placa e motorista, com nome e modelo opcionais', () => {
        assert.doesNotThrow(() => validador.validarAtribuicao(atribuicaoValida));
        assert.doesNotThrow(() => validador.validarAtribuicao({
            ...atribuicaoValida, motorista_nome: 'Ana', veiculo_modelo: 'Van',
        }));
    });

    it('exige placa e motorista', () => {
        esperarErro(() => validador.validarAtribuicao({ motorista_id: 3 }), /obrigatórios/);
        esperarErro(() => validador.validarAtribuicao({ veiculo_placa: 'ABC1D23' }), /obrigatórios/);
    });

    it('rejeita motorista_id que não é inteiro positivo', () => {
        esperarErro(() => validador.validarAtribuicao({ ...atribuicaoValida, motorista_id: 'abc' }), /motorista_id/);
        esperarErro(() => validador.validarAtribuicao({ ...atribuicaoValida, motorista_id: '1.5' }), /motorista_id/);
        esperarErro(() => validador.validarAtribuicao({ ...atribuicaoValida, motorista_id: -2 }), /motorista_id/);
    });

    it('rejeita placa maior que 7 caracteres e nome maior que 100', () => {
        esperarErro(() => validador.validarAtribuicao({ ...atribuicaoValida, veiculo_placa: 'ABCDEFGH' }), /veiculo_placa/);
        esperarErro(() => validador.validarAtribuicao({ ...atribuicaoValida, motorista_nome: 'x'.repeat(101) }), /motorista_nome/);
        esperarErro(() => validador.validarAtribuicao({ ...atribuicaoValida, veiculo_modelo: 'x'.repeat(101) }), /veiculo_modelo/);
    });
});
