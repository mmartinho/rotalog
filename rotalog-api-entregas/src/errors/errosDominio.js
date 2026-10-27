/**
 * Erros de domínio de entregas (ADR 0001, seção 3).
 *
 * Carregam o status HTTP. O errorHandler responde com a mensagem e com os
 * detalhes, sem stack trace.
 */

class ErroDominio extends Error {
    constructor(message, status, detalhes = {}) {
        super(message);
        this.name = this.constructor.name;
        this.status = status;
        this.detalhes = detalhes;
    }
}

class EntregaNaoEncontradaError extends ErroDominio {
    constructor(message = 'Entrega não encontrada') {
        super(message, 404);
    }
}

class EntradaInvalidaError extends ErroDominio {
    constructor(message, detalhes = {}) {
        super(message, 400, detalhes);
    }
}

class TransicaoStatusInvalidaError extends ErroDominio {
    constructor(de, para) {
        super('Transição de ' + de + ' para ' + para + ' não é permitida', 409, { de, para });
    }
}

module.exports = {
    ErroDominio,
    EntregaNaoEncontradaError,
    EntradaInvalidaError,
    TransicaoStatusInvalidaError
};
