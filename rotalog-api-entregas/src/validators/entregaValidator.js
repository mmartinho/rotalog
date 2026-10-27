/**
 * Validação de entrada de /api/entregas (ADR 0001, seções 3 e E3).
 *
 * Cada função lança EntradaInvalidaError (400) na primeira falha. Campos
 * opcionais ausentes (undefined ou null) não são validados.
 */

const { EntradaInvalidaError } = require('../errors/errosDominio');

const STATUS_VALIDOS = ['PENDENTE', 'ATRIBUIDA', 'EM_TRANSITO', 'ENTREGUE', 'CANCELADA'];

function ausente(valor) {
    return valor === undefined || valor === null;
}

function validarTexto(nome, valor, tamanhoMaximo) {
    if (ausente(valor)) return;
    if (typeof valor !== 'string' || valor.length > tamanhoMaximo) {
        throw new EntradaInvalidaError(nome + ' deve ser um texto de até ' + tamanhoMaximo + ' caracteres');
    }
}

function validarPeso(peso) {
    if (ausente(peso)) return;
    if (typeof peso !== 'number' || !Number.isFinite(peso) || peso < 0) {
        throw new EntradaInvalidaError('peso_kg deve ser um número maior ou igual a zero');
    }
}

function validarCoordenada(nome, valor, limite) {
    if (ausente(valor)) return;
    if (typeof valor !== 'number' || !Number.isFinite(valor) || Math.abs(valor) > limite) {
        throw new EntradaInvalidaError(nome + ' deve ser um número entre -' + limite + ' e ' + limite);
    }
}

function validarCriacao(dados) {
    if (!dados.origem_endereco || !dados.destino_endereco) {
        throw new EntradaInvalidaError('Endereços de origem e destino são obrigatórios');
    }

    validarTexto('origem_endereco', dados.origem_endereco, 255);
    validarTexto('destino_endereco', dados.destino_endereco, 255);
    validarPeso(dados.peso_kg);
    validarCoordenada('origem_lat', dados.origem_lat, 90);
    validarCoordenada('origem_lng', dados.origem_lng, 180);
    validarCoordenada('destino_lat', dados.destino_lat, 90);
    validarCoordenada('destino_lng', dados.destino_lng, 180);
    validarTexto('observacoes', dados.observacoes, Infinity);
}

// Endereço vazio é ignorado na atualização, como antes da fase 3
function validarAtualizacao(dados) {
    if (dados.origem_endereco) validarTexto('origem_endereco', dados.origem_endereco, 255);
    if (dados.destino_endereco) validarTexto('destino_endereco', dados.destino_endereco, 255);
    validarPeso(dados.peso_kg);
    if (dados.observacoes !== undefined) validarTexto('observacoes', dados.observacoes, Infinity);
}

function validarStatus(status) {
    if (!STATUS_VALIDOS.includes(status)) {
        throw new EntradaInvalidaError('Status inválido', { statusValidos: STATUS_VALIDOS });
    }
}

function validarAtribuicao(dados) {
    if (!dados.veiculo_placa || !dados.motorista_id) {
        throw new EntradaInvalidaError('Placa do veículo e ID do motorista são obrigatórios');
    }

    validarTexto('veiculo_placa', dados.veiculo_placa, 7);
    if (!/^\d+$/.test(String(dados.motorista_id)) || Number(dados.motorista_id) <= 0) {
        throw new EntradaInvalidaError('motorista_id deve ser um inteiro positivo');
    }
    validarTexto('motorista_nome', dados.motorista_nome, 100);
    validarTexto('veiculo_modelo', dados.veiculo_modelo, 100);
}

module.exports = {
    STATUS_VALIDOS,
    validarCriacao,
    validarAtualizacao,
    validarStatus,
    validarAtribuicao
};
