/**
 * asyncHandler: encaminha erros de handlers async para o errorHandler
 * sem precisar de try/catch em cada rota.
 */

function asyncHandler(fn) {
    return function(req, res, next) {
        Promise.resolve(fn(req, res, next)).catch(next);
    };
}

module.exports = asyncHandler;
