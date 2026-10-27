-- RotaLog - Migration: alertas de manutenção preventiva (api-frotas)
-- Cria a tabela 'frotas.alertas_manutencao'
-- Este script é idempotente (pode ser executado múltiplas vezes)
-- Executar após os scripts 01-08
-- Mantido em sincronia com rotalog-api-frotas/src/main/resources/db/migration/V3__Alertas_Manutencao.sql

SET search_path TO frotas;

-- Tabela de alertas de manutenção preventiva
CREATE TABLE IF NOT EXISTS alertas_manutencao (
    id BIGSERIAL PRIMARY KEY,
    veiculo_id BIGINT NOT NULL,
    motivo VARCHAR(20) NOT NULL,                -- KM, TEMPO, KM_E_TEMPO
    km_desde_ultima_preventiva BIGINT,
    data_referencia TIMESTAMP,                  -- última preventiva ou data de cadastro do veículo
    status VARCHAR(20) NOT NULL,                -- ENVIADA, FALHA, PENDENTE
    notificacao_id BIGINT,
    mensagem_erro TEXT,
    data_criacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Índices
CREATE INDEX IF NOT EXISTS idx_alertas_manutencao_status ON alertas_manutencao(status);
CREATE INDEX IF NOT EXISTS idx_alertas_manutencao_veiculo_id ON alertas_manutencao(veiculo_id);
