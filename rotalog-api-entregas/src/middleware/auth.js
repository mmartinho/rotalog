/**
 * Auth Middleware
 *
 * Valida o JWT (HS256) enviado em "Authorization: Bearer <token>".
 * Regras na ADR 0001 (docs/adr/0001-saneamento-auth-e-camadas-entregas.md), seção 1.
 *
 * A autenticação fica ligada por padrão. O bypass exige AUTH_DISABLED=true
 * e só é aceito fora de NODE_ENV=production.
 */

const jwt = require('jsonwebtoken');

const ALGORITMO = 'HS256';
const MENSAGEM_NAO_AUTORIZADO = 'Não autorizado';

function getJwtSecret() {
    return process.env.JWT_SECRET;
}

function getIssuer() {
    return process.env.JWT_ISSUER || 'rotalog';
}

function getAudience() {
    return process.env.JWT_AUDIENCE || 'rotalog-api-entregas';
}

function bypassAtivo() {
    return process.env.AUTH_DISABLED === 'true' && process.env.NODE_ENV !== 'production';
}

// Chamada uma vez no startup, antes de o servidor subir
function validarConfiguracaoAuth() {
    if (process.env.AUTH_DISABLED === 'true' && process.env.NODE_ENV === 'production') {
        throw new Error('AUTH_DISABLED=true não é permitido com NODE_ENV=production');
    }

    if (bypassAtivo()) {
        console.log('[AUTH] ATENÇÃO: autenticação desligada (AUTH_DISABLED=true)');
        return;
    }

    if (!getJwtSecret()) {
        throw new Error('JWT_SECRET é obrigatório com a autenticação ligada');
    }
}

// O motivo vai para o log, sem o token. O cliente recebe sempre a mesma resposta
function recusar(res, motivo) {
    console.error('[AUTH] Recusado: ' + motivo);
    return res.status(401).json({ error: MENSAGEM_NAO_AUTORIZADO });
}

function authMiddleware(req, res, next) {
    if (bypassAtivo()) {
        return next();
    }

    const header = req.headers.authorization;
    if (!header) {
        return recusar(res, 'cabeçalho Authorization ausente');
    }

    const partes = header.split(' ');
    if (partes.length !== 2 || partes[0] !== 'Bearer') {
        return recusar(res, 'cabeçalho Authorization mal formatado');
    }

    const secret = getJwtSecret();
    if (!secret) {
        return recusar(res, 'JWT_SECRET não configurado');
    }

    let claims;
    try {
        claims = jwt.verify(partes[1], secret, {
            algorithms: [ALGORITMO],
            issuer: getIssuer(),
            audience: getAudience()
        });
    } catch (error) {
        return recusar(res, error.name);
    }

    req.user = { id: claims.sub, role: claims.role };
    next();
}

module.exports = { authMiddleware, validarConfiguracaoAuth };
