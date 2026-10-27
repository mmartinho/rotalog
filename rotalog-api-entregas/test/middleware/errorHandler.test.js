const { describe, it, beforeEach, afterEach, mock } = require('node:test');
const assert = require('node:assert/strict');

const { errorHandler, notFoundHandler } = require('../../src/middleware/errorHandler');

function criarRes() {
    return {
        statusCode: null,
        body: null,
        status(code) {
            this.statusCode = code;
            return this;
        },
        json(payload) {
            this.body = payload;
            return this;
        },
    };
}

describe('errorHandler', () => {
    let envOriginal;

    beforeEach(() => {
        envOriginal = process.env.NODE_ENV;
        mock.method(console, 'error', () => {});
    });

    afterEach(() => {
        process.env.NODE_ENV = envOriginal;
        mock.restoreAll();
    });

    it('responde 500 com a mensagem do erro quando não há status', () => {
        const res = criarRes();
        errorHandler(new Error('falhou'), { method: 'GET', url: '/x', body: {} }, res, () => {});

        assert.equal(res.statusCode, 500);
        assert.deepEqual(res.body, { error: 'falhou' });
    });

    it('usa o status do erro quando definido', () => {
        const erro = Object.assign(new Error('não encontrado'), { status: 404 });
        const res = criarRes();
        errorHandler(erro, { method: 'GET', url: '/x', body: {} }, res, () => {});

        assert.equal(res.statusCode, 404);
    });

    it('usa mensagem genérica quando o erro não tem mensagem', () => {
        const res = criarRes();
        errorHandler({}, { method: 'GET', url: '/x', body: {} }, res, () => {});

        assert.equal(res.statusCode, 500);
        assert.deepEqual(res.body, { error: 'Erro interno do servidor' });
    });

    it('nunca devolve stack ao cliente, nem fora de produção', () => {
        process.env.NODE_ENV = 'development';
        const res = criarRes();
        errorHandler(new Error('falhou'), { method: 'GET', url: '/x', body: {} }, res, () => {});

        assert.equal('stack' in res.body, false);
    });

    it('registra a stack no log do servidor', () => {
        const erro = new Error('falhou');
        errorHandler(erro, { method: 'GET', url: '/x', body: {} }, criarRes(), () => {});

        const saidas = console.error.mock.calls.map((c) => c.arguments.join(' ')).join('\n');
        assert.ok(saidas.includes(erro.stack));
    });

    it('não registra o body da requisição no log', () => {
        const body = { senha: 'valor-sensivel-123' };
        errorHandler(new Error('falhou'), { method: 'POST', url: '/x', body }, criarRes(), () => {});

        const saidas = console.error.mock.calls.map((c) => c.arguments.join(' ')).join('\n');
        assert.ok(!saidas.includes('valor-sensivel-123'));
    });
});

describe('errorHandler com erros de domínio (ADR seção 3)', () => {
    const { EntradaInvalidaError, TransicaoStatusInvalidaError, EntregaNaoEncontradaError } = require('../../src/errors/errosDominio');

    beforeEach(() => {
        mock.method(console, 'error', () => {});
    });

    afterEach(() => {
        mock.restoreAll();
    });

    it('responde com o status do erro, a mensagem e os detalhes', () => {
        const res = criarRes();
        errorHandler(new EntradaInvalidaError('Status inválido', { statusValidos: ['A'] }), { method: 'PATCH', url: '/x', body: {} }, res, () => {});

        assert.equal(res.statusCode, 400);
        assert.deepEqual(res.body, { error: 'Status inválido', statusValidos: ['A'] });
    });

    it('responde 409 com de e para para transição inválida', () => {
        const res = criarRes();
        errorHandler(new TransicaoStatusInvalidaError('ENTREGUE', 'PENDENTE'), { method: 'PATCH', url: '/x', body: {} }, res, () => {});

        assert.equal(res.statusCode, 409);
        assert.deepEqual(res.body, {
            error: 'Transição de ENTREGUE para PENDENTE não é permitida',
            de: 'ENTREGUE',
            para: 'PENDENTE',
        });
    });

    it('responde 404 sem registrar erro interno no log', () => {
        const res = criarRes();
        errorHandler(new EntregaNaoEncontradaError(), { method: 'GET', url: '/x', body: {} }, res, () => {});

        assert.equal(res.statusCode, 404);
        assert.deepEqual(res.body, { error: 'Entrega não encontrada' });
        assert.equal(console.error.mock.callCount(), 0);
    });
});

describe('notFoundHandler', () => {
    it('responde 404 com path e method', () => {
        const res = criarRes();
        notFoundHandler({ path: '/nada', method: 'GET' }, res);

        assert.equal(res.statusCode, 404);
        assert.deepEqual(res.body, { error: 'Rota não encontrada', path: '/nada', method: 'GET' });
    });
});
