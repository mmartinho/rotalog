/**
 * Entrega Service
 *
 * Regras de negócio de entregas: número do pedido, distância, máquina de
 * estados e trilha de rastreamento (ADR 0001, seções 4 e 5).
 *
 * As rotas não acessam os models diretamente: passam pelos controllers, que
 * chamam este service.
 *
 * FIXME: Cálculo de distância aproximado (ver ADR 0001, seção 5.3)
 */

const crypto = require('crypto');
const { UniqueConstraintError } = require('sequelize');
const { Entrega, Rastreamento } = require('../models');
const { sequelize } = require('../config/database');
const estatisticasRepository = require('../repositories/estatisticasRepository');
const {
    EntregaNaoEncontradaError,
    TransicaoStatusInvalidaError
} = require('../errors/errosDominio');

// ADR 0001, seção 4. ATRIBUIDA só é alcançada por atribuir (ver alterarStatus)
const TRANSICOES = {
    PENDENTE: ['ATRIBUIDA', 'CANCELADA'],
    ATRIBUIDA: ['EM_TRANSITO', 'PENDENTE', 'CANCELADA'],
    EM_TRANSITO: ['ENTREGUE', 'CANCELADA'],
    ENTREGUE: [],
    CANCELADA: []
};

const TENTATIVAS_NUMERO_PEDIDO = 3;

function podeTransicionar(de, para) {
    return Object.prototype.hasOwnProperty.call(TRANSICOES, de) && TRANSICOES[de].includes(para);
}

// Sufixo com 8 bytes aleatórios (ADR 0001, seção 5.4). Colisão é barrada pelo índice UNIQUE
function gerarNumeroPedido() {
    return 'PED-' + Date.now() + '-' + crypto.randomBytes(4).toString('hex');
}

function temCoordenadas(...valores) {
    return valores.every((valor) => valor !== undefined && valor !== null);
}

// FIXME: Fórmula simplificada - deveria usar Haversine ou API de rotas
function calcularDistanciaKm(origemLat, origemLng, destinoLat, destinoLng) {
    const dLat = Math.abs(destinoLat - origemLat);
    const dLng = Math.abs(destinoLng - origemLng);
    return Math.sqrt(dLat * dLat + dLng * dLng) * 111; // FIXME: aproximação grosseira
}

function montarFiltros(query) {
    const where = {};

    // Filtro por veículo (usado pelo api-frotas)
    if (query.veiculo) {
        where.veiculo_placa = query.veiculo;
    }

    // Filtro por status
    if (query.status) {
        where.status = query.status.toUpperCase();
    }

    // Filtro por motorista
    if (query.motorista_id) {
        where.motorista_id = query.motorista_id;
    }

    return where;
}

// FIXME: Sem paginação
async function listar(query) {
    return Entrega.findAll({
        where: montarFiltros(query),
        order: [['data_criacao', 'DESC']],
        include: [{
            model: Rastreamento,
            as: 'rastreamentos',
            limit: 5, // FIXME: limit hardcoded
            order: [['data_evento', 'DESC']]
        }]
    });
}

async function buscarComRastreamentosOuFalhar(id) {
    const entrega = await Entrega.findByPk(id, {
        include: [{
            model: Rastreamento,
            as: 'rastreamentos',
            order: [['data_evento', 'DESC']]
        }]
    });

    if (!entrega) throw new EntregaNaoEncontradaError();
    return entrega;
}

async function buscarEntregaOuFalhar(id) {
    const entrega = await Entrega.findByPk(id);

    if (!entrega) throw new EntregaNaoEncontradaError();
    return entrega;
}

async function buscarPorPedidoOuFalhar(numeroPedido) {
    const entrega = await Entrega.findOne({ where: { numero_pedido: numeroPedido } });

    if (!entrega) throw new EntregaNaoEncontradaError('Pedido não encontrado');
    return entrega;
}

async function estatisticas() {
    const stats = await estatisticasRepository.buscarResumoPorStatus();
    const totalEntregas = stats.reduce(function(acc, s) { return acc + parseInt(s.total); }, 0);

    return {
        total: totalEntregas,
        por_status: stats,
        gerado_em: new Date().toISOString()
    };
}

function registrarEvento(transaction, entregaId, evento, descricao) {
    return Rastreamento.create({
        entrega_id: entregaId,
        evento,
        descricao,
        data_evento: new Date()
    }, { transaction });
}

// Repete com outro número em caso de colisão do índice UNIQUE (ADR 0001, seção 5.4)
async function criarComNumeroUnico(campos) {
    for (let tentativa = 1; ; tentativa++) {
        const numeroPedido = gerarNumeroPedido();

        try {
            return await sequelize.transaction(async (transaction) => {
                const entrega = await Entrega.create({
                    ...campos,
                    numero_pedido: numeroPedido
                }, { transaction });

                await registrarEvento(transaction, entrega.id, 'PEDIDO_CRIADO', 'Pedido de entrega criado: ' + numeroPedido);
                return entrega;
            });
        } catch (error) {
            const colisao = error instanceof UniqueConstraintError;
            if (!colisao || tentativa >= TENTATIVAS_NUMERO_PEDIDO) throw error;
        }
    }
}

async function criar(dados) {
    const { origem_endereco, destino_endereco, peso_kg, observacoes,
            origem_lat, origem_lng, destino_lat, destino_lng } = dados;

    let distanciaKm = null;
    if (temCoordenadas(origem_lat, origem_lng, destino_lat, destino_lng)) {
        distanciaKm = calcularDistanciaKm(origem_lat, origem_lng, destino_lat, destino_lng);
    }

    // FIXME: Tempo estimado hardcoded
    const tempoEstimado = distanciaKm !== null ? Math.ceil(distanciaKm * 2) : null; // 2 min/km

    const entrega = await criarComNumeroUnico({
        origem_endereco,
        destino_endereco,
        origem_lat: origem_lat ?? null,
        origem_lng: origem_lng ?? null,
        destino_lat: destino_lat ?? null,
        destino_lng: destino_lng ?? null,
        peso_kg: peso_kg || null,
        distancia_km: distanciaKm,
        tempo_estimado_minutos: tempoEstimado,
        status: 'PENDENTE',
        observacoes: observacoes || null,
        data_criacao: new Date(),
        data_atualizacao: new Date()
    });

    console.log('Entrega criada:', entrega.numero_pedido); // FIXME: usar logger

    return entrega;
}

async function atualizarDados(entrega, dados) {
    const { origem_endereco, destino_endereco, peso_kg, observacoes } = dados;

    if (origem_endereco) entrega.origem_endereco = origem_endereco;
    if (destino_endereco) entrega.destino_endereco = destino_endereco;
    if (peso_kg !== undefined) entrega.peso_kg = peso_kg;
    if (observacoes !== undefined) entrega.observacoes = observacoes;
    entrega.data_atualizacao = new Date();

    await entrega.save();
    return entrega;
}

async function alterarStatus(entrega, status) {
    const statusAnterior = entrega.status;

    if (status === 'ATRIBUIDA' || !podeTransicionar(statusAnterior, status)) {
        throw new TransicaoStatusInvalidaError(statusAnterior, status);
    }

    await sequelize.transaction(async (transaction) => {
        entrega.status = status;
        entrega.data_atualizacao = new Date();

        if (status === 'EM_TRANSITO') {
            entrega.data_coleta = new Date();
        }
        if (status === 'ENTREGUE') {
            entrega.data_entrega = new Date();
        }

        await entrega.save({ transaction });
        await registrarEvento(transaction, entrega.id, 'STATUS_ALTERADO',
            'Status alterado de ' + statusAnterior + ' para ' + status);
    });

    console.log('Status atualizado:', entrega.numero_pedido, statusAnterior, '->', status);

    return entrega;
}

// FIXME: Sem verificação se veículo/motorista existem no api-frotas
// FIXME: Sem verificação de disponibilidade
async function atribuir(entrega, dados) {
    const { veiculo_placa, motorista_id, motorista_nome, veiculo_modelo } = dados;

    await sequelize.transaction(async (transaction) => {
        entrega.veiculo_placa = veiculo_placa;
        entrega.motorista_id = motorista_id;
        entrega.motorista_nome = motorista_nome || null;
        entrega.veiculo_modelo = veiculo_modelo || null;
        entrega.status = 'ATRIBUIDA';
        entrega.data_atualizacao = new Date();

        await entrega.save({ transaction });
        await registrarEvento(transaction, entrega.id, 'ENTREGA_ATRIBUIDA',
            'Atribuída ao veículo ' + veiculo_placa + ' e motorista #' + motorista_id);
    });

    console.log('Entrega atribuída:', entrega.numero_pedido, 'veículo:', veiculo_placa);

    return entrega;
}

// FIXME: Não deleta de verdade, apenas muda status (soft delete)
async function cancelar(entrega) {
    if (!podeTransicionar(entrega.status, 'CANCELADA')) {
        throw new TransicaoStatusInvalidaError(entrega.status, 'CANCELADA');
    }

    await sequelize.transaction(async (transaction) => {
        entrega.status = 'CANCELADA';
        entrega.data_atualizacao = new Date();

        await entrega.save({ transaction });
        await registrarEvento(transaction, entrega.id, 'ENTREGA_CANCELADA', 'Entrega cancelada');
    });

    return entrega;
}

module.exports = {
    TRANSICOES,
    podeTransicionar,
    gerarNumeroPedido,
    calcularDistanciaKm,
    listar,
    buscarComRastreamentosOuFalhar,
    buscarEntregaOuFalhar,
    buscarPorPedidoOuFalhar,
    estatisticas,
    criar,
    atualizarDados,
    alterarStatus,
    atribuir,
    cancelar
};
