/**
 * Error Handler Middleware
 *
 * Registra o erro no log do servidor (URL, mensagem e stack) e responde
 * ao cliente sem stack trace e sem o body da requisição.
 */

const { ErroDominio } = require('../errors/errosDominio');

function errorHandler(err, req, res, next) {
    // Erro de domínio é esperado: responde com o status dele, sem log de erro interno
    if (err instanceof ErroDominio) {
        return res.status(err.status).json({ error: err.message, ...err.detalhes });
    }

    console.error('=== ERRO NÃO TRATADO ===');
    console.error('URL:', req.method, req.url);
    console.error('Erro:', err.message);
    console.error('Stack:', err.stack);
    console.error('========================');

    res.status(err.status || 500).json({
        error: err.message || 'Erro interno do servidor'
    });
}

function notFoundHandler(req, res) {
    res.status(404).json({
        error: 'Rota não encontrada',
        path: req.path,
        method: req.method
    });
}

module.exports = { errorHandler, notFoundHandler };
