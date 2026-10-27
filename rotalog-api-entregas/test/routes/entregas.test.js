/**
 * Testes de caracterização das rotas de /api/entregas (ADR 0001, fase 0).
 *
 * Sobem as rotas num servidor Express no próprio processo e batem nele com
 * fetch, contra o Postgres do workspace. Os asserts fixam o comportamento atual. Quando a fase 3
 * corrigir algum ponto, o teste correspondente é alterado no mesmo commit, e o
 * comentário aponta o item da ADR.
 *
 * Os dados criados têm origem_endereco com o prefixo TESTE-CARAC e são removidos
 * no fim da suíte.
 */

const { describe, it, before, after, mock } = require('node:test');
const assert = require('node:assert/strict');
const express = require('express');
const jwt = require('jsonwebtoken');

const entregasRoutes = require('../../src/routes/entregas');
const { authMiddleware } = require('../../src/middleware/auth');
const { errorHandler, notFoundHandler } = require('../../src/middleware/errorHandler');

const PREFIXO = 'TESTE-CARAC';
const JWT_SECRET = 'segredo-de-teste-caracterizacao';

let servidor;
let base;
let token;

// Monta as rotas como o index.js (mesmo middleware e mesma ordem), sem subir o processo inteiro
function subirServidor() {
    process.env.NODE_ENV = 'production';
    process.env.JWT_SECRET = JWT_SECRET;
    delete process.env.AUTH_DISABLED;

    const app = express();
    app.use(express.json());
    app.use('/api/entregas', authMiddleware, entregasRoutes);
    app.use(notFoundHandler);
    app.use(errorHandler);

    return new Promise((resolve) => {
        servidor = app.listen(0, () => {
            base = `http://localhost:${servidor.address().port}`;
            resolve();
        });
    });
}

async function chamar(metodo, caminho, corpo, opcoes = {}) {
    const headers = { 'Content-Type': 'application/json' };
    if (!opcoes.semToken) {
        headers.Authorization = 'Bearer ' + token;
    }
    const res = await fetch(base + caminho, {
        method: metodo,
        headers,
        body: corpo === undefined ? undefined : JSON.stringify(corpo),
    });
    const texto = await res.text();
    return { status: res.status, body: texto ? JSON.parse(texto) : null };
}

async function criarEntrega(extra = {}) {
    const res = await chamar('POST', '/api/entregas', {
        origem_endereco: PREFIXO + ' origem',
        destino_endereco: 'Destino de teste',
        peso_kg: 10,
        ...extra,
    });
    assert.equal(res.status, 201, 'criação de entrega de teste falhou: ' + JSON.stringify(res.body));
    return res.body;
}

async function buscarEntrega(id) {
    const res = await chamar('GET', `/api/entregas/${id}`);
    assert.equal(res.status, 200);
    return res.body;
}

// Leva a entrega até ENTREGUE pelo caminho permitido pela máquina de estados
async function levarAteEntregue(id) {
    await chamar('PATCH', `/api/entregas/${id}/atribuir`, { veiculo_placa: 'TST7G89', motorista_id: 9007 });
    await chamar('PATCH', `/api/entregas/${id}/status`, { status: 'EM_TRANSITO' });
    const res = await chamar('PATCH', `/api/entregas/${id}/status`, { status: 'ENTREGUE' });
    assert.equal(res.status, 200);
}

async function limparDados() {
    const { sequelize } = require('../../src/config/database');
    const opcoes = { replacements: { p: PREFIXO + '%' }, logging: false };
    await sequelize.query(
        'DELETE FROM entregas.rastreamentos WHERE entrega_id IN (SELECT id FROM entregas.entregas WHERE origem_endereco LIKE :p)',
        opcoes
    );
    await sequelize.query('DELETE FROM entregas.entregas WHERE origem_endereco LIKE :p', opcoes);
    await sequelize.close();
}

before(async () => {
    // O SQL do Sequelize vai para console.log; silencia para não poluir a saída do node --test
    mock.method(console, 'log', () => {});
    await subirServidor();
    token = jwt.sign({ sub: '1', role: 'admin' }, JWT_SECRET, {
        algorithm: 'HS256',
        issuer: 'rotalog',
        audience: 'rotalog-api-entregas',
        expiresIn: '1h',
    });
});

after(async () => {
    await new Promise((resolve) => servidor.close(resolve));
    await limparDados();
    mock.restoreAll();
});

describe('erro inesperado no service vira 500 com a mensagem da rota', () => {
    const entregaService = require('../../src/services/entregaService');

    // Substitui um método do service só durante o teste, e restaura em seguida
    async function comFalha(metodo, requisicao) {
        const falha = mock.method(entregaService, metodo, async () => {
            throw new Error('falha simulada');
        });
        mock.method(console, 'error', () => {});
        try {
            return await requisicao();
        } finally {
            falha.mock.restore();
            console.error.mock.restore();
        }
    }

    it('GET /api/entregas', async () => {
        const res = await comFalha('listar', () => chamar('GET', '/api/entregas'));
        assert.equal(res.status, 500);
        assert.deepEqual(res.body, { error: 'Erro interno do servidor' });
    });

    it('GET /api/entregas/stats', async () => {
        const res = await comFalha('estatisticas', () => chamar('GET', '/api/entregas/stats'));
        assert.equal(res.status, 500);
        assert.deepEqual(res.body, { error: 'Erro ao buscar estatísticas' });
    });

    it('GET /api/entregas/:id', async () => {
        const res = await comFalha('buscarComRastreamentosOuFalhar', () => chamar('GET', '/api/entregas/1'));
        assert.equal(res.status, 500);
        assert.deepEqual(res.body, { error: 'Erro interno do servidor' });
    });

    it('GET /api/entregas/pedido/:numero', async () => {
        const res = await comFalha('buscarPorPedidoOuFalhar', () => chamar('GET', '/api/entregas/pedido/PED-X'));
        assert.equal(res.status, 500);
        assert.deepEqual(res.body, { error: 'Erro interno do servidor' });
    });

    it('POST /api/entregas', async () => {
        const res = await comFalha('criar', () => chamar('POST', '/api/entregas', {
            origem_endereco: PREFIXO + ' falha', destino_endereco: 'Destino',
        }));
        assert.equal(res.status, 500);
        assert.deepEqual(res.body, { error: 'Erro ao criar entrega' });
    });

    it('PUT /api/entregas/:id', async () => {
        const criada = await criarEntrega();
        const res = await comFalha('atualizarDados', () => chamar('PUT', `/api/entregas/${criada.id}`, { observacoes: 'x' }));
        assert.equal(res.status, 500);
        assert.deepEqual(res.body, { error: 'Erro ao atualizar entrega' });
    });

    it('PATCH /api/entregas/:id/status', async () => {
        const criada = await criarEntrega();
        const res = await comFalha('alterarStatus', () => chamar('PATCH', `/api/entregas/${criada.id}/status`, { status: 'CANCELADA' }));
        assert.equal(res.status, 500);
        assert.deepEqual(res.body, { error: 'Erro ao atualizar status' });
    });

    it('PATCH /api/entregas/:id/atribuir', async () => {
        const criada = await criarEntrega();
        const res = await comFalha('atribuir', () => chamar('PATCH', `/api/entregas/${criada.id}/atribuir`, {
            veiculo_placa: 'TSTAA11', motorista_id: 9100,
        }));
        assert.equal(res.status, 500);
        assert.deepEqual(res.body, { error: 'Erro ao atribuir entrega' });
    });

    it('DELETE /api/entregas/:id', async () => {
        const criada = await criarEntrega();
        const res = await comFalha('cancelar', () => chamar('DELETE', `/api/entregas/${criada.id}`));
        assert.equal(res.status, 500);
        assert.deepEqual(res.body, { error: 'Erro ao cancelar entrega' });
    });
});

describe('validação de entrada nas rotas (ADR E3)', () => {
    it('PATCH /:id/atribuir rejeita motorista_id inválido com 400', async () => {
        const criada = await criarEntrega();
        const res = await chamar('PATCH', `/api/entregas/${criada.id}/atribuir`, {
            veiculo_placa: 'TSTBB22', motorista_id: 'abc',
        });

        assert.equal(res.status, 400);
        assert.equal(res.body.error, 'motorista_id deve ser um inteiro positivo');
    });

    it('PUT /:id rejeita peso inválido com 400', async () => {
        const criada = await criarEntrega();
        const res = await chamar('PUT', `/api/entregas/${criada.id}`, { peso_kg: -5 });

        assert.equal(res.status, 400);
    });
});

describe('autenticação das rotas de entregas', () => {
    it('responde 401 sem token', async () => {
        const res = await chamar('GET', '/api/entregas', undefined, { semToken: true });
        assert.equal(res.status, 401);
        assert.deepEqual(res.body, { error: 'Não autorizado' });
    });
});

describe('GET /api/entregas', () => {
    it('lista as entregas como array e inclui a entrega criada', async () => {
        const criada = await criarEntrega();
        const res = await chamar('GET', '/api/entregas');

        assert.equal(res.status, 200);
        assert.ok(Array.isArray(res.body));
        assert.ok(res.body.some((e) => e.id === criada.id));
    });

    it('filtra por status, convertendo para maiúsculas', async () => {
        const criada = await criarEntrega();
        const res = await chamar('GET', '/api/entregas?status=pendente');

        assert.equal(res.status, 200);
        assert.ok(res.body.length > 0);
        assert.ok(res.body.every((e) => e.status === 'PENDENTE'));
        assert.ok(res.body.some((e) => e.id === criada.id));
    });

    it('filtra por motorista_id', async () => {
        const criada = await criarEntrega();
        await chamar('PATCH', `/api/entregas/${criada.id}/atribuir`, {
            veiculo_placa: 'TST1A23',
            motorista_id: 9001,
        });
        const res = await chamar('GET', '/api/entregas?motorista_id=9001');

        assert.equal(res.status, 200);
        assert.ok(res.body.some((e) => e.id === criada.id));
        assert.ok(res.body.every((e) => String(e.motorista_id) === '9001'));
    });

    it('filtra por veiculo (placa)', async () => {
        const criada = await criarEntrega();
        await chamar('PATCH', `/api/entregas/${criada.id}/atribuir`, {
            veiculo_placa: 'TST2B34',
            motorista_id: 9002,
        });
        const res = await chamar('GET', '/api/entregas?veiculo=TST2B34');

        assert.equal(res.status, 200);
        assert.ok(res.body.some((e) => e.id === criada.id));
        assert.ok(res.body.every((e) => e.veiculo_placa === 'TST2B34'));
    });
});

describe('GET /api/entregas/stats', () => {
    it('devolve total, por_status e gerado_em', async () => {
        await criarEntrega();
        const res = await chamar('GET', '/api/entregas/stats');

        assert.equal(res.status, 200);
        assert.equal(typeof res.body.total, 'number');
        assert.ok(Array.isArray(res.body.por_status));
        assert.ok(res.body.total > 0);
        assert.ok(!Number.isNaN(Date.parse(res.body.gerado_em)));

        for (const grupo of res.body.por_status) {
            assert.ok('status' in grupo);
            assert.ok('total' in grupo);
            assert.ok('distancia_media' in grupo);
            assert.ok('peso_medio' in grupo);
        }

        const soma = res.body.por_status.reduce((acc, g) => acc + parseInt(g.total, 10), 0);
        assert.equal(res.body.total, soma);
    });
});

describe('GET /api/entregas/:id', () => {
    it('devolve a entrega com rastreamentos, incluindo PEDIDO_CRIADO', async () => {
        const criada = await criarEntrega();
        const res = await chamar('GET', `/api/entregas/${criada.id}`);

        assert.equal(res.status, 200);
        assert.equal(res.body.id, criada.id);
        assert.ok(Array.isArray(res.body.rastreamentos));
        assert.ok(res.body.rastreamentos.some((r) => r.evento === 'PEDIDO_CRIADO'));
    });

    it('responde 404 para id inexistente', async () => {
        const res = await chamar('GET', '/api/entregas/999999999');

        assert.equal(res.status, 404);
        assert.deepEqual(res.body, { error: 'Entrega não encontrada' });
    });
});

describe('GET /api/entregas/pedido/:numeroPedido', () => {
    it('busca a entrega pelo número do pedido', async () => {
        const criada = await criarEntrega();
        const res = await chamar('GET', `/api/entregas/pedido/${criada.numero_pedido}`);

        assert.equal(res.status, 200);
        assert.equal(res.body.id, criada.id);
    });

    it('responde 404 quando o pedido não existe', async () => {
        const res = await chamar('GET', '/api/entregas/pedido/PED-NAO-EXISTE');

        assert.equal(res.status, 404);
        assert.deepEqual(res.body, { error: 'Pedido não encontrado' });
    });
});

describe('POST /api/entregas', () => {
    it('responde 400 quando faltam os endereços', async () => {
        const res = await chamar('POST', '/api/entregas', { origem_endereco: PREFIXO + ' so origem' });

        assert.equal(res.status, 400);
        assert.deepEqual(res.body, { error: 'Endereços de origem e destino são obrigatórios' });
    });

    it('cria a entrega como PENDENTE com número PED- e evento PEDIDO_CRIADO', async () => {
        const criada = await criarEntrega();

        assert.equal(criada.status, 'PENDENTE');
        assert.match(criada.numero_pedido, /^PED-/);

        const completa = await buscarEntrega(criada.id);
        const evento = completa.rastreamentos.find((r) => r.evento === 'PEDIDO_CRIADO');
        assert.ok(evento);
        assert.equal(evento.descricao, 'Pedido de entrega criado: ' + criada.numero_pedido);
    });

    it('calcula distância e tempo estimado a partir das coordenadas', async () => {
        const criada = await criarEntrega({
            origem_lat: -23.5,
            origem_lng: -46.6,
            destino_lat: -23.6,
            destino_lng: -46.7,
        });

        const distanciaEsperada = Math.round(Math.sqrt(0.1 * 0.1 + 0.1 * 0.1) * 111 * 100) / 100;
        assert.ok(Math.abs(Number(criada.distancia_km) - distanciaEsperada) < 0.01);
        assert.equal(criada.tempo_estimado_minutos, Math.ceil(distanciaEsperada * 2));
    });

    it('deixa distância e tempo nulos sem coordenadas', async () => {
        const criada = await criarEntrega();

        assert.equal(criada.distancia_km, null);
        assert.equal(criada.tempo_estimado_minutos, null);
    });

    it('responde 400 quando o peso não é numérico (ADR E3)', async () => {
        const res = await chamar('POST', '/api/entregas', {
            origem_endereco: PREFIXO + ' peso invalido',
            destino_endereco: 'Destino',
            peso_kg: 'abc',
        });

        assert.equal(res.status, 400);
        assert.deepEqual(res.body, { error: 'peso_kg deve ser um número maior ou igual a zero' });
    });

    it('responde 400 para coordenada fora da faixa ou com tipo errado (ADR E3)', async () => {
        const foraDaFaixa = await chamar('POST', '/api/entregas', {
            origem_endereco: PREFIXO + ' lat', destino_endereco: 'Destino', origem_lat: 91,
        });
        const tipoErrado = await chamar('POST', '/api/entregas', {
            origem_endereco: PREFIXO + ' lng', destino_endereco: 'Destino', origem_lng: '-46.6',
        });

        assert.equal(foraDaFaixa.status, 400);
        assert.equal(tipoErrado.status, 400);
    });

    it('grava latitude e longitude 0 e calcula distância com elas (ADR E7)', async () => {
        const criada = await criarEntrega({
            origem_lat: 0,
            origem_lng: 0,
            destino_lat: 0.5,
            destino_lng: 0.5,
        });

        assert.equal(Number(criada.origem_lat), 0);
        assert.equal(Number(criada.origem_lng), 0);
        assert.notEqual(criada.distancia_km, null);
        assert.notEqual(criada.tempo_estimado_minutos, null);
    });

    it('gera o sufixo do número do pedido em hexadecimal (ADR 5.4)', async () => {
        const criada = await criarEntrega();
        assert.match(criada.numero_pedido, /^PED-\d+-[0-9a-f]{8}$/);
    });
});

describe('PUT /api/entregas/:id', () => {
    it('atualiza endereço e observações', async () => {
        const criada = await criarEntrega();
        const res = await chamar('PUT', `/api/entregas/${criada.id}`, {
            origem_endereco: PREFIXO + ' editado',
            observacoes: 'observação de teste',
        });

        assert.equal(res.status, 200);
        assert.equal(res.body.origem_endereco, PREFIXO + ' editado');
        assert.equal(res.body.observacoes, 'observação de teste');
    });

    it('responde 404 para id inexistente', async () => {
        const res = await chamar('PUT', '/api/entregas/999999999', { observacoes: 'x' });

        assert.equal(res.status, 404);
        assert.deepEqual(res.body, { error: 'Entrega não encontrada' });
    });
});

describe('PATCH /api/entregas/:id/status', () => {
    it('EM_TRANSITO define data_coleta e grava STATUS_ALTERADO', async () => {
        const criada = await criarEntrega();
        await chamar('PATCH', `/api/entregas/${criada.id}/atribuir`, { veiculo_placa: 'TST6F78', motorista_id: 9006 });
        const res = await chamar('PATCH', `/api/entregas/${criada.id}/status`, { status: 'EM_TRANSITO' });

        assert.equal(res.status, 200);
        assert.equal(res.body.status, 'EM_TRANSITO');
        assert.notEqual(res.body.data_coleta, null);

        const completa = await buscarEntrega(criada.id);
        const evento = completa.rastreamentos.find((r) => r.evento === 'STATUS_ALTERADO');
        assert.equal(evento.descricao, 'Status alterado de ATRIBUIDA para EM_TRANSITO');
    });

    it('ENTREGUE define data_entrega', async () => {
        const criada = await criarEntrega();
        await chamar('PATCH', `/api/entregas/${criada.id}/atribuir`, { veiculo_placa: 'TST8H90', motorista_id: 9008 });
        await chamar('PATCH', `/api/entregas/${criada.id}/status`, { status: 'EM_TRANSITO' });
        const res = await chamar('PATCH', `/api/entregas/${criada.id}/status`, { status: 'ENTREGUE' });

        assert.equal(res.status, 200);
        assert.notEqual(res.body.data_entrega, null);
    });

    it('rejeita PENDENTE para EM_TRANSITO, que não está na máquina de estados (ADR seção 4)', async () => {
        const criada = await criarEntrega();
        const res = await chamar('PATCH', `/api/entregas/${criada.id}/status`, { status: 'EM_TRANSITO' });

        assert.equal(res.status, 409);
        assert.deepEqual(res.body, {
            error: 'Transição de PENDENTE para EM_TRANSITO não é permitida',
            de: 'PENDENTE',
            para: 'EM_TRANSITO',
        });
    });

    it('responde 400 com a lista de status válidos para status desconhecido', async () => {
        const criada = await criarEntrega();
        const res = await chamar('PATCH', `/api/entregas/${criada.id}/status`, { status: 'INVENTADO' });

        assert.equal(res.status, 400);
        assert.equal(res.body.error, 'Status inválido');
        assert.deepEqual(res.body.statusValidos, ['PENDENTE', 'ATRIBUIDA', 'EM_TRANSITO', 'ENTREGUE', 'CANCELADA']);
    });

    it('rejeita ENTREGUE voltando para PENDENTE (ADR E5)', async () => {
        const criada = await criarEntrega();
        await levarAteEntregue(criada.id);
        const res = await chamar('PATCH', `/api/entregas/${criada.id}/status`, { status: 'PENDENTE' });

        assert.equal(res.status, 409);
        assert.equal(res.body.de, 'ENTREGUE');
        assert.equal(res.body.para, 'PENDENTE');
    });

    it('rejeita ATRIBUIDA pelo PATCH de status, que só aceita atribuir (ADR E6)', async () => {
        const criada = await criarEntrega();
        const res = await chamar('PATCH', `/api/entregas/${criada.id}/status`, { status: 'ATRIBUIDA' });

        assert.equal(res.status, 409);
        assert.equal(res.body.para, 'ATRIBUIDA');

        const depois = await buscarEntrega(criada.id);
        assert.equal(depois.status, 'PENDENTE');
        assert.equal(depois.veiculo_placa, null);
    });

    it('permite desatribuir de ATRIBUIDA de volta para PENDENTE', async () => {
        const criada = await criarEntrega();
        await chamar('PATCH', `/api/entregas/${criada.id}/atribuir`, { veiculo_placa: 'TST9I01', motorista_id: 9009 });
        const res = await chamar('PATCH', `/api/entregas/${criada.id}/status`, { status: 'PENDENTE' });

        assert.equal(res.status, 200);
        assert.equal(res.body.status, 'PENDENTE');
    });

    it('responde 404 para id inexistente', async () => {
        const res = await chamar('PATCH', '/api/entregas/999999999/status', { status: 'ENTREGUE' });

        assert.equal(res.status, 404);
    });
});

describe('PATCH /api/entregas/:id/atribuir', () => {
    it('responde 400 quando falta placa ou motorista', async () => {
        const criada = await criarEntrega();
        const res = await chamar('PATCH', `/api/entregas/${criada.id}/atribuir`, { veiculo_placa: 'TST3C45' });

        assert.equal(res.status, 400);
        assert.deepEqual(res.body, { error: 'Placa do veículo e ID do motorista são obrigatórios' });
    });

    it('atribui veículo e motorista, muda para ATRIBUIDA e grava ENTREGA_ATRIBUIDA', async () => {
        const criada = await criarEntrega();
        const res = await chamar('PATCH', `/api/entregas/${criada.id}/atribuir`, {
            veiculo_placa: 'TST3C45',
            motorista_id: 9003,
            motorista_nome: 'Motorista de Teste',
            veiculo_modelo: 'Modelo de Teste',
        });

        assert.equal(res.status, 200);
        assert.equal(res.body.status, 'ATRIBUIDA');
        assert.equal(res.body.veiculo_placa, 'TST3C45');
        assert.equal(res.body.motorista_nome, 'Motorista de Teste');
        assert.equal(res.body.veiculo_modelo, 'Modelo de Teste');

        const completa = await buscarEntrega(criada.id);
        assert.ok(completa.rastreamentos.some((r) => r.evento === 'ENTREGA_ATRIBUIDA'));
    });

    it('deixa motorista_nome e veiculo_modelo nulos quando não enviados', async () => {
        const criada = await criarEntrega();
        const res = await chamar('PATCH', `/api/entregas/${criada.id}/atribuir`, {
            veiculo_placa: 'TST4D56',
            motorista_id: 9004,
        });

        assert.equal(res.status, 200);
        assert.equal(res.body.motorista_nome, null);
        assert.equal(res.body.veiculo_modelo, null);
    });

    it('responde 404 para id inexistente', async () => {
        const res = await chamar('PATCH', '/api/entregas/999999999/atribuir', {
            veiculo_placa: 'TST5E67',
            motorista_id: 9005,
        });

        assert.equal(res.status, 404);
    });
});

describe('DELETE /api/entregas/:id', () => {
    it('cancela a entrega e grava ENTREGA_CANCELADA', async () => {
        const criada = await criarEntrega();
        const res = await chamar('DELETE', `/api/entregas/${criada.id}`);

        assert.equal(res.status, 200);
        assert.equal(res.body.message, 'Entrega cancelada');
        assert.equal(res.body.entrega.status, 'CANCELADA');

        const completa = await buscarEntrega(criada.id);
        assert.ok(completa.rastreamentos.some((r) => r.evento === 'ENTREGA_CANCELADA'));
    });

    it('rejeita cancelar entrega já ENTREGUE (ADR seção 4)', async () => {
        const criada = await criarEntrega();
        await levarAteEntregue(criada.id);
        const res = await chamar('DELETE', `/api/entregas/${criada.id}`);

        assert.equal(res.status, 409);
        assert.equal(res.body.de, 'ENTREGUE');

        const depois = await buscarEntrega(criada.id);
        assert.equal(depois.status, 'ENTREGUE');
    });

    it('responde 404 para id inexistente', async () => {
        const res = await chamar('DELETE', '/api/entregas/999999999');

        assert.equal(res.status, 404);
        assert.deepEqual(res.body, { error: 'Entrega não encontrada' });
    });
});
