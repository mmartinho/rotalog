/**
 * Entregas Routes
 *
 * Só mapeia método + caminho para o controller (ADR 0001, seção 2).
 */

const express = require('express');
const router = express.Router();
const entregasController = require('../controllers/entregasController');
const asyncHandler = require('../middleware/asyncHandler');

router.get('/', asyncHandler(entregasController.listar));
router.get('/stats', asyncHandler(entregasController.estatisticas));
router.get('/:id', asyncHandler(entregasController.buscarPorId));
router.get('/pedido/:numeroPedido', asyncHandler(entregasController.buscarPorPedido));
router.post('/', asyncHandler(entregasController.criar));
router.put('/:id', asyncHandler(entregasController.atualizar));
router.patch('/:id/status', asyncHandler(entregasController.alterarStatus));
router.patch('/:id/atribuir', asyncHandler(entregasController.atribuir));
router.delete('/:id', asyncHandler(entregasController.cancelar));

module.exports = router;
