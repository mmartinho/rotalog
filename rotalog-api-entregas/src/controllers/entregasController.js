/**
 * Entregas Controller
 *
 * Lê a requisição, valida a entrada, chama o service e escolhe o status HTTP
 * (ADR 0001, seção 2). Sem acesso direto aos models.
 *
 * Erros de domínio (ErroDominio) seguem para o errorHandler. Qualquer outro
 * erro vira 500 com a mensagem da rota.
 *
 * FIXME: Tratamento de erro inconsistente entre as rotas
 */

const entregaService = require('../services/entregaService');
const entregaValidator = require('../validators/entregaValidator');
const { ErroDominio } = require('../errors/errosDominio');

async function listar(req, res, next) {
    try {
        const entregas = await entregaService.listar(req.query);
        res.json(entregas);
    } catch (error) {
        console.error('Erro ao listar entregas:', error.message); // FIXME: usar logger
        res.status(500).json({ error: 'Erro interno do servidor' });
    }
}

async function estatisticas(req, res, next) {
    try {
        const resultado = await entregaService.estatisticas();
        res.json(resultado);
    } catch (error) {
        console.error('Erro ao buscar estatísticas:', error.message);
        res.status(500).json({ error: 'Erro ao buscar estatísticas' });
    }
}

async function buscarPorId(req, res, next) {
    try {
        const entrega = await entregaService.buscarComRastreamentosOuFalhar(req.params.id);
        res.json(entrega);
    } catch (error) {
        if (error instanceof ErroDominio) return next(error);
        console.error('Erro ao buscar entrega:', error.message);
        res.status(500).json({ error: 'Erro interno do servidor' });
    }
}

async function buscarPorPedido(req, res, next) {
    try {
        const entrega = await entregaService.buscarPorPedidoOuFalhar(req.params.numeroPedido);
        res.json(entrega);
    } catch (error) {
        if (error instanceof ErroDominio) return next(error);
        console.error('Erro ao buscar pedido:', error.message);
        res.status(500).json({ error: 'Erro interno do servidor' });
    }
}

async function criar(req, res, next) {
    try {
        const dados = req.body || {};
        entregaValidator.validarCriacao(dados);

        const entrega = await entregaService.criar(dados);
        res.status(201).json(entrega);
    } catch (error) {
        if (error instanceof ErroDominio) return next(error);
        console.error('Erro ao criar entrega:', error.message);
        res.status(500).json({ error: 'Erro ao criar entrega' });
    }
}

async function atualizar(req, res, next) {
    try {
        const dados = req.body || {};
        const entrega = await entregaService.buscarEntregaOuFalhar(req.params.id);
        entregaValidator.validarAtualizacao(dados);

        const atualizada = await entregaService.atualizarDados(entrega, dados);
        res.json(atualizada);
    } catch (error) {
        if (error instanceof ErroDominio) return next(error);
        console.error('Erro ao atualizar entrega:', error.message);
        res.status(500).json({ error: 'Erro ao atualizar entrega' });
    }
}

async function alterarStatus(req, res, next) {
    try {
        const { status } = req.body || {};
        const entrega = await entregaService.buscarEntregaOuFalhar(req.params.id);
        entregaValidator.validarStatus(status);

        const atualizada = await entregaService.alterarStatus(entrega, status);
        res.json(atualizada);
    } catch (error) {
        if (error instanceof ErroDominio) return next(error);
        console.error('Erro ao atualizar status:', error.message);
        res.status(500).json({ error: 'Erro ao atualizar status' });
    }
}

async function atribuir(req, res, next) {
    try {
        const dados = req.body || {};
        const entrega = await entregaService.buscarEntregaOuFalhar(req.params.id);
        entregaValidator.validarAtribuicao(dados);

        const atualizada = await entregaService.atribuir(entrega, dados);
        res.json(atualizada);
    } catch (error) {
        if (error instanceof ErroDominio) return next(error);
        console.error('Erro ao atribuir entrega:', error.message);
        res.status(500).json({ error: 'Erro ao atribuir entrega' });
    }
}

async function cancelar(req, res, next) {
    try {
        const entrega = await entregaService.buscarEntregaOuFalhar(req.params.id);
        const cancelada = await entregaService.cancelar(entrega);
        res.json({ message: 'Entrega cancelada', entrega: cancelada });
    } catch (error) {
        if (error instanceof ErroDominio) return next(error);
        console.error('Erro ao cancelar entrega:', error.message);
        res.status(500).json({ error: 'Erro ao cancelar entrega' });
    }
}

module.exports = {
    listar,
    estatisticas,
    buscarPorId,
    buscarPorPedido,
    criar,
    atualizar,
    alterarStatus,
    atribuir,
    cancelar
};
