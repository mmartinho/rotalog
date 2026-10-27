/**
 * Estatisticas Repository
 *
 * Consultas agregadas sobre a tabela de entregas (ADR 0001, seção 2).
 */

const { sequelize } = require('../config/database');

// FIXME: Query raw em vez de usar Sequelize aggregation
async function buscarResumoPorStatus() {
    const query = `
        SELECT
            status,
            COUNT(*) as total,
            COALESCE(AVG(distancia_km), 0) as distancia_media,
            COALESCE(AVG(peso_kg), 0) as peso_medio
        FROM entregas.entregas
        GROUP BY status
    `;

    return sequelize.query(query, { type: sequelize.constructor.QueryTypes.SELECT });
}

module.exports = { buscarResumoPorStatus };
