const { describe, it } = require('node:test');
const assert = require('node:assert/strict');

const asyncHandler = require('../../src/middleware/asyncHandler');

describe('asyncHandler', () => {
    it('repassa req, res e next ao handler', async () => {
        const req = { id: 1 };
        const res = { id: 2 };
        let recebidos;

        const handler = asyncHandler(async (r, s, n) => {
            recebidos = [r, s, n];
        });
        await handler(req, res, () => {});
        await new Promise((resolve) => setImmediate(resolve));

        assert.equal(recebidos[0], req);
        assert.equal(recebidos[1], res);
        assert.equal(typeof recebidos[2], 'function');
    });

    it('encaminha a rejeição do handler para next(erro)', async () => {
        const erro = new Error('falhou');
        let recebido;

        const handler = asyncHandler(async () => { throw erro; });
        handler({}, {}, (e) => { recebido = e; });
        await new Promise((resolve) => setImmediate(resolve));

        assert.equal(recebido, erro);
    });

    it('não chama next quando o handler conclui', async () => {
        let chamadoComErro = false;

        const handler = asyncHandler(async (req, res) => { res.ok = true; });
        const res = {};
        handler({}, res, (e) => { chamadoComErro = e !== undefined; });
        await new Promise((resolve) => setImmediate(resolve));

        assert.equal(res.ok, true);
        assert.equal(chamadoComErro, false);
    });
});
