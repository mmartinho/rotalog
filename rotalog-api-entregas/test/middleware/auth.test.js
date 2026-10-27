const { describe, it, beforeEach, afterEach, mock } = require('node:test');
const assert = require('node:assert/strict');
const jwt = require('jsonwebtoken');

const { authMiddleware, validarConfiguracaoAuth } = require('../../src/middleware/auth');

const SECRET = 'segredo-de-teste-com-tamanho-razoavel';
const ISSUER = 'rotalog';
const AUDIENCE = 'rotalog-api-entregas';
const VARIAVEIS = ['NODE_ENV', 'AUTH_DISABLED', 'JWT_SECRET', 'JWT_ISSUER', 'JWT_AUDIENCE'];

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

function assinar(payload, opcoes = {}) {
    return jwt.sign(payload, SECRET, {
        algorithm: 'HS256',
        issuer: ISSUER,
        audience: AUDIENCE,
        expiresIn: '1h',
        ...opcoes,
    });
}

function executar(req) {
    const res = criarRes();
    let chamouNext = false;
    authMiddleware(req, res, () => { chamouNext = true; });
    return { res, chamouNext };
}

describe('auth', () => {
    const envOriginal = {};

    beforeEach(() => {
        for (const nome of VARIAVEIS) {
            envOriginal[nome] = process.env[nome];
            delete process.env[nome];
        }
        process.env.NODE_ENV = 'production';
        process.env.JWT_SECRET = SECRET;
        mock.method(console, 'log', () => {});
        mock.method(console, 'error', () => {});
    });

    afterEach(() => {
        for (const nome of VARIAVEIS) {
            if (envOriginal[nome] === undefined) {
                delete process.env[nome];
            } else {
                process.env[nome] = envOriginal[nome];
            }
        }
        mock.restoreAll();
    });

    describe('validarConfiguracaoAuth', () => {
        it('aceita configuração com JWT_SECRET definido', () => {
            assert.doesNotThrow(() => validarConfiguracaoAuth());
        });

        it('falha quando a auth está ligada e JWT_SECRET não existe', () => {
            delete process.env.JWT_SECRET;
            assert.throws(() => validarConfiguracaoAuth(), /JWT_SECRET é obrigatório/);
        });

        it('falha quando NODE_ENV não está definido e JWT_SECRET não existe (auth ligada por padrão)', () => {
            delete process.env.NODE_ENV;
            delete process.env.JWT_SECRET;
            assert.throws(() => validarConfiguracaoAuth(), /JWT_SECRET é obrigatório/);
        });

        it('aceita bypass com AUTH_DISABLED=true fora de produção, sem exigir JWT_SECRET', () => {
            process.env.NODE_ENV = 'development';
            process.env.AUTH_DISABLED = 'true';
            delete process.env.JWT_SECRET;
            assert.doesNotThrow(() => validarConfiguracaoAuth());
        });

        it('registra um aviso no startup quando o bypass está ativo', () => {
            process.env.NODE_ENV = 'development';
            process.env.AUTH_DISABLED = 'true';
            validarConfiguracaoAuth();
            assert.equal(console.log.mock.callCount(), 1);
            assert.match(console.log.mock.calls[0].arguments[0], /AUTH_DISABLED=true/);
        });

        it('recusa AUTH_DISABLED=true com NODE_ENV=production', () => {
            process.env.AUTH_DISABLED = 'true';
            assert.throws(() => validarConfiguracaoAuth(), /não é permitido com NODE_ENV=production/);
        });

        it('ignora AUTH_DISABLED com valor diferente de "true"', () => {
            process.env.NODE_ENV = 'development';
            process.env.AUTH_DISABLED = 'false';
            delete process.env.JWT_SECRET;
            assert.throws(() => validarConfiguracaoAuth(), /JWT_SECRET é obrigatório/);
        });
    });

    describe('authMiddleware com bypass', () => {
        it('chama next() sem validar o header quando AUTH_DISABLED=true fora de produção', () => {
            process.env.NODE_ENV = 'development';
            process.env.AUTH_DISABLED = 'true';
            const req = { headers: {} };

            const { res, chamouNext } = executar(req);

            assert.equal(chamouNext, true);
            assert.equal(res.statusCode, null);
            assert.equal(req.user, undefined);
        });
    });

    describe('authMiddleware com autenticação ligada', () => {
        it('responde 401 quando o header Authorization não é enviado', () => {
            const { res, chamouNext } = executar({ headers: {} });

            assert.equal(chamouNext, false);
            assert.equal(res.statusCode, 401);
            assert.deepEqual(res.body, { error: 'Não autorizado' });
        });

        it('responde 401 quando NODE_ENV não está definido (fail-closed)', () => {
            delete process.env.NODE_ENV;
            const { res, chamouNext } = executar({ headers: {} });

            assert.equal(chamouNext, false);
            assert.equal(res.statusCode, 401);
        });

        it('responde 401 quando o header não tem o prefixo Bearer', () => {
            const { res, chamouNext } = executar({
                headers: { authorization: 'Basic ' + assinar({ sub: '1' }) },
            });

            assert.equal(chamouNext, false);
            assert.equal(res.statusCode, 401);
            assert.deepEqual(res.body, { error: 'Não autorizado' });
        });

        it('responde 401 quando o header tem partes a mais', () => {
            const { res } = executar({
                headers: { authorization: 'Bearer ' + assinar({ sub: '1' }) + ' extra' },
            });

            assert.equal(res.statusCode, 401);
            assert.deepEqual(res.body, { error: 'Não autorizado' });
        });

        it('responde 401 quando o header tem só o prefixo Bearer', () => {
            const { res } = executar({ headers: { authorization: 'Bearer' } });

            assert.equal(res.statusCode, 401);
        });

        it('responde 401 para a string de 10 caracteres que antes era aceita', () => {
            const { res, chamouNext } = executar({ headers: { authorization: 'Bearer 1234567890' } });

            assert.equal(chamouNext, false);
            assert.equal(res.statusCode, 401);
        });

        it('responde 401 quando a assinatura não confere', () => {
            const tokenForjado = jwt.sign({ sub: '1', role: 'admin' }, 'outro-segredo', {
                algorithm: 'HS256', issuer: ISSUER, audience: AUDIENCE, expiresIn: '1h',
            });
            const { res, chamouNext } = executar({ headers: { authorization: 'Bearer ' + tokenForjado } });

            assert.equal(chamouNext, false);
            assert.equal(res.statusCode, 401);
            assert.deepEqual(res.body, { error: 'Não autorizado' });
        });

        it('responde 401 quando o token está expirado', () => {
            const expirado = assinar({ sub: '1' }, { expiresIn: -10 });
            const { res, chamouNext } = executar({ headers: { authorization: 'Bearer ' + expirado } });

            assert.equal(chamouNext, false);
            assert.equal(res.statusCode, 401);
        });

        it('responde 401 quando o emissor (iss) não confere', () => {
            const token = assinar({ sub: '1' }, { issuer: 'outro-emissor' });
            const { res } = executar({ headers: { authorization: 'Bearer ' + token } });

            assert.equal(res.statusCode, 401);
        });

        it('responde 401 quando a audiência (aud) não confere', () => {
            const token = assinar({ sub: '1' }, { audience: 'outro-servico' });
            const { res } = executar({ headers: { authorization: 'Bearer ' + token } });

            assert.equal(res.statusCode, 401);
        });

        it('responde 401 para token sem assinatura (alg none)', () => {
            const semAssinatura = jwt.sign({ sub: '1', iss: ISSUER, aud: AUDIENCE }, '', { algorithm: 'none' });
            const { res } = executar({ headers: { authorization: 'Bearer ' + semAssinatura } });

            assert.equal(res.statusCode, 401);
        });

        it('responde 401 para token assinado com outro algoritmo HMAC (HS512)', () => {
            const hs512 = jwt.sign({ sub: '1', iss: ISSUER, aud: AUDIENCE }, SECRET, { algorithm: 'HS512' });
            const { res } = executar({ headers: { authorization: 'Bearer ' + hs512 } });

            assert.equal(res.statusCode, 401);
        });

        it('responde 401 quando JWT_SECRET não está configurado no momento da requisição', () => {
            delete process.env.JWT_SECRET;
            const { res, chamouNext } = executar({
                headers: { authorization: 'Bearer ' + assinar({ sub: '1' }) },
            });

            assert.equal(chamouNext, false);
            assert.equal(res.statusCode, 401);
        });

        it('aceita token válido e define req.user a partir dos claims', () => {
            const token = assinar({ sub: '42', role: 'gerente' });
            const req = { headers: { authorization: 'Bearer ' + token } };
            const { res, chamouNext } = executar(req);

            assert.equal(chamouNext, true);
            assert.equal(res.statusCode, null);
            assert.deepEqual(req.user, { id: '42', role: 'gerente' });
        });

        it('usa JWT_ISSUER e JWT_AUDIENCE quando definidos', () => {
            process.env.JWT_ISSUER = 'emissor-customizado';
            process.env.JWT_AUDIENCE = 'audiencia-customizada';
            const token = assinar({ sub: '7' }, { issuer: 'emissor-customizado', audience: 'audiencia-customizada' });
            const { chamouNext } = executar({ headers: { authorization: 'Bearer ' + token } });

            assert.equal(chamouNext, true);
        });

        it('não registra o token no log ao recusar', () => {
            const token = assinar({ sub: '1' }, { expiresIn: -10 });
            executar({ headers: { authorization: 'Bearer ' + token } });

            const saidas = console.error.mock.calls.map((c) => c.arguments.join(' ')).join('\n');
            assert.equal(console.error.mock.callCount(), 1);
            assert.ok(saidas.includes('TokenExpiredError'));
            assert.ok(!saidas.includes(token));
        });

        it('não registra log por requisição quando o token é aceito', () => {
            executar({ headers: { authorization: 'Bearer ' + assinar({ sub: '1' }) } });

            assert.equal(console.log.mock.callCount(), 0);
            assert.equal(console.error.mock.callCount(), 0);
        });
    });
});
