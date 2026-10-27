const { describe, it, before, after, beforeEach, afterEach, mock } = require('node:test');
const assert = require('node:assert/strict');
const { UniqueConstraintError } = require('sequelize');

const service = require('../../src/services/entregaService');
const { Entrega, Rastreamento } = require('../../src/models');
const { sequelize } = require('../../src/config/database');
const { TransicaoStatusInvalidaError } = require('../../src/errors/errosDominio');

const PREFIXO = 'TESTE-UNIT-SVC';

// Transições permitidas pela ADR 0001, seção 4
const PERMITIDAS = [
    ['PENDENTE', 'ATRIBUIDA'], ['PENDENTE', 'CANCELADA'],
    ['ATRIBUIDA', 'EM_TRANSITO'], ['ATRIBUIDA', 'PENDENTE'], ['ATRIBUIDA', 'CANCELADA'],
    ['EM_TRANSITO', 'ENTREGUE'], ['EM_TRANSITO', 'CANCELADA'],
];

describe('máquina de estados (ADR seção 4)', () => {
    it('aceita exatamente as transições da tabela', () => {
        const todos = ['PENDENTE', 'ATRIBUIDA', 'EM_TRANSITO', 'ENTREGUE', 'CANCELADA'];
        for (const de of todos) {
            for (const para of todos) {
                const esperado = PERMITIDAS.some(([a, b]) => a === de && b === para);
                assert.equal(service.podeTransicionar(de, para), esperado, de + ' -> ' + para);
            }
        }
    });

    it('não permite sair de ENTREGUE nem de CANCELADA', () => {
        assert.deepEqual(service.TRANSICOES.ENTREGUE, []);
        assert.deepEqual(service.TRANSICOES.CANCELADA, []);
    });

    it('ignora chaves herdadas do objeto, como constructor', () => {
        assert.equal(service.podeTransicionar('constructor', 'PENDENTE'), false);
        assert.equal(service.podeTransicionar('__proto__', 'PENDENTE'), false);
    });
});

describe('gerarNumeroPedido e calcularDistanciaKm', () => {
    it('gera PED- com timestamp e 8 caracteres hexadecimais', () => {
        const numero = service.gerarNumeroPedido();
        assert.match(numero, /^PED-\d+-[0-9a-f]{8}$/);
    });

    it('gera números diferentes em chamadas seguidas', () => {
        const numeros = new Set(Array.from({ length: 50 }, () => service.gerarNumeroPedido()));
        assert.equal(numeros.size, 50);
    });

    it('calcula a distância aproximada e zero para o mesmo ponto', () => {
        assert.equal(service.calcularDistanciaKm(0, 0, 0, 0), 0);
        assert.equal(service.calcularDistanciaKm(0, 0, 3, 4), 5 * 111);
        assert.equal(service.calcularDistanciaKm(-3, -4, 0, 0), 5 * 111);
    });
});

describe('criar com retry do número do pedido (ADR 5.4)', () => {
    let transacoes;

    beforeEach(() => {
        transacoes = 0;
        mock.method(sequelize, 'transaction', async (fn) => {
            transacoes += 1;
            return fn({});
        });
        mock.method(Rastreamento, 'create', async () => ({}));
        mock.method(console, 'log', () => {});
    });

    afterEach(() => {
        mock.restoreAll();
    });

    it('repete com outro número após colisão do índice UNIQUE', async () => {
        let chamadas = 0;
        mock.method(Entrega, 'create', async (campos) => {
            chamadas += 1;
            if (chamadas === 1) throw new UniqueConstraintError({ message: 'duplicado' });
            return { id: 10, numero_pedido: campos.numero_pedido };
        });

        const entrega = await service.criar({ origem_endereco: 'A', destino_endereco: 'B' });

        assert.equal(chamadas, 2);
        assert.equal(transacoes, 2);
        assert.match(entrega.numero_pedido, /^PED-/);
    });

    it('desiste após 3 colisões seguidas e relança o erro', async () => {
        mock.method(Entrega, 'create', async () => {
            throw new UniqueConstraintError({ message: 'duplicado' });
        });

        await assert.rejects(
            service.criar({ origem_endereco: 'A', destino_endereco: 'B' }),
            UniqueConstraintError
        );
        assert.equal(transacoes, 3);
    });

    it('não repete para erro que não é de unicidade', async () => {
        mock.method(Entrega, 'create', async () => {
            throw new Error('falha de conexão');
        });

        await assert.rejects(service.criar({ origem_endereco: 'A', destino_endereco: 'B' }), /falha de conexão/);
        assert.equal(transacoes, 1);
    });

    it('grava lat/lng 0 e calcula a distância com elas (ADR E7)', async () => {
        let campos;
        mock.method(Entrega, 'create', async (c) => {
            campos = c;
            return { id: 1, numero_pedido: c.numero_pedido };
        });

        await service.criar({
            origem_endereco: 'A', destino_endereco: 'B',
            origem_lat: 0, origem_lng: 0, destino_lat: 3, destino_lng: 4,
        });

        assert.equal(campos.origem_lat, 0);
        assert.equal(campos.origem_lng, 0);
        assert.equal(campos.distancia_km, 5 * 111);
        assert.equal(campos.tempo_estimado_minutos, Math.ceil(5 * 111 * 2));
    });

    it('deixa distância nula sem as quatro coordenadas', async () => {
        let campos;
        mock.method(Entrega, 'create', async (c) => {
            campos = c;
            return { id: 1, numero_pedido: c.numero_pedido };
        });

        await service.criar({ origem_endereco: 'A', destino_endereco: 'B', origem_lat: 1 });

        assert.equal(campos.distancia_km, null);
        assert.equal(campos.tempo_estimado_minutos, null);
    });
});

describe('transições com transação no banco (ADR 5.1)', () => {
    let entrega;

    before(async () => {
        entrega = await Entrega.create({
            numero_pedido: 'PED-TESTE-SVC-' + Date.now(),
            origem_endereco: PREFIXO + ' rollback',
            destino_endereco: 'Destino',
            status: 'PENDENTE',
        });
    });

    after(async () => {
        const opcoes = { replacements: { p: PREFIXO + '%' }, logging: false };
        await sequelize.query(
            'DELETE FROM entregas.rastreamentos WHERE entrega_id IN (SELECT id FROM entregas.entregas WHERE origem_endereco LIKE :p)',
            opcoes
        );
        await sequelize.query('DELETE FROM entregas.entregas WHERE origem_endereco LIKE :p', opcoes);
        await sequelize.close();
    });

    it('desfaz a alteração da entrega quando o evento de rastreamento falha', async () => {
        mock.method(Rastreamento, 'create', async () => {
            throw new Error('falha simulada no rastreamento');
        });
        mock.method(console, 'log', () => {});

        try {
            await assert.rejects(service.cancelar(entrega), /falha simulada/);
        } finally {
            mock.restoreAll();
        }

        const noBanco = await Entrega.findByPk(entrega.id);
        assert.equal(noBanco.status, 'PENDENTE');
    });

    it('rejeita cancelar entrega ENTREGUE sem tocar no banco', async () => {
        const entregue = { id: 1, status: 'ENTREGUE', save: async () => assert.fail('não deve salvar') };

        await assert.rejects(service.cancelar(entregue), (erro) => {
            assert.ok(erro instanceof TransicaoStatusInvalidaError);
            assert.equal(erro.status, 409);
            return true;
        });
    });
});
