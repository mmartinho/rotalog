-- RotaLog - Seed: veículos elegíveis para alerta de manutenção preventiva
-- Este script é idempotente (pode ser executado múltiplas vezes)
-- Executar após os scripts 01-09
--
-- Regra de elegibilidade (api-frotas): veículo ATIVO com
--   km desde a última preventiva concluída > 50000  OU
--   última preventiva concluída há mais de 6 meses
-- Sem preventiva: km total e data_cadastro como referência.
--
-- As datas são relativas a NOW() para que o seed continue válido com o passar do tempo.
--
-- | Placa   | Cenário                          | Km desde preventiva | Última preventiva | Motivo esperado |
-- |---------|----------------------------------|---------------------|-------------------|-----------------|
-- | ALK1A11 | Elegível só por km               | 60000               | há 2 meses        | KM              |
-- | ALT2B22 | Elegível só por tempo            | 5000                | há 8 meses        | TEMPO           |
-- | ALX3C33 | Elegível por km e tempo          | 80000               | há 9 meses        | KM_E_TEMPO      |
-- | ALN4D44 | Não elegível                     | 12000               | há 1 mês          | -               |
-- | ALS5E55 | Sem preventiva, km total > 50000 | 65000 (total)       | nenhuma           | KM              |
-- | ALI6F66 | Inativo (ignorado)               | 90000               | há 10 meses       | -               |
--
-- Observação: os veículos do script 05 têm datas de 2024 e, por isso, também ficam
-- elegíveis por tempo. Para um cenário controlado, use apenas as placas AL*.

SET search_path TO frotas;

-- Veículos
INSERT INTO veiculos (placa, modelo, ano_fabricacao, quilometragem, status, data_cadastro, data_atualizacao) VALUES
('ALK1A11', 'Fiat Fiorino', 2021, 130000, 'ATIVO', NOW() - INTERVAL '2 years', NOW()),
('ALT2B22', 'Mercedes Sprinter', 2022, 30000, 'ATIVO', NOW() - INTERVAL '2 years', NOW()),
('ALX3C33', 'VW Delivery 9.170', 2019, 200000, 'ATIVO', NOW() - INTERVAL '3 years', NOW()),
('ALN4D44', 'Renault Master', 2023, 52000, 'ATIVO', NOW() - INTERVAL '2 years', NOW()),
('ALS5E55', 'Iveco Daily 35S14', 2022, 65000, 'ATIVO', NOW() - INTERVAL '2 months', NOW()),
('ALI6F66', 'Fiat Ducato', 2018, 190000, 'INATIVO', NOW() - INTERVAL '3 years', NOW())
ON CONFLICT (placa) DO NOTHING;

-- Manutenções (referenciadas pela placa; o id do veículo não é fixo)
INSERT INTO manutencoes (veiculo_id, tipo_manutencao, data_manutencao, quilometragem_manutencao, custo, descricao, status, data_criacao, data_atualizacao)
SELECT v.id, m.tipo_manutencao, m.data_manutencao, m.quilometragem_manutencao, m.custo, m.descricao, m.status, m.data_manutencao, m.data_manutencao
FROM (VALUES
    -- ALK1A11: 130000 - 70000 = 60000 km, preventiva recente (só km)
    ('ALK1A11', 'PREVENTIVA', NOW() - INTERVAL '2 months', 70000, 900.00, 'Seed alerta: preventiva concluída (só km)', 'CONCLUIDA'),
    -- ALT2B22: 30000 - 25000 = 5000 km, preventiva antiga (só tempo)
    ('ALT2B22', 'PREVENTIVA', NOW() - INTERVAL '8 months', 25000, 700.00, 'Seed alerta: preventiva concluída (só tempo)', 'CONCLUIDA'),
    -- ALT2B22: preventiva PENDENTE recente não conta como última preventiva
    ('ALT2B22', 'PREVENTIVA', NOW() - INTERVAL '1 week', NULL, NULL, 'Seed alerta: preventiva pendente (ignorada)', 'PENDENTE'),
    -- ALX3C33: 200000 - 120000 = 80000 km, preventiva antiga (km e tempo)
    ('ALX3C33', 'PREVENTIVA', NOW() - INTERVAL '9 months', 120000, 1500.00, 'Seed alerta: preventiva concluída (km e tempo)', 'CONCLUIDA'),
    -- ALN4D44: 52000 - 40000 = 12000 km, preventiva recente (não elegível)
    ('ALN4D44', 'PREVENTIVA', NOW() - INTERVAL '1 month', 40000, 800.00, 'Seed alerta: preventiva concluída (não elegível)', 'CONCLUIDA'),
    -- ALI6F66: elegível pelas regras, mas INATIVO
    ('ALI6F66', 'PREVENTIVA', NOW() - INTERVAL '10 months', 100000, 1200.00, 'Seed alerta: preventiva concluída (veículo inativo)', 'CONCLUIDA')
) AS m(placa, tipo_manutencao, data_manutencao, quilometragem_manutencao, custo, descricao, status)
JOIN veiculos v ON v.placa = m.placa
WHERE NOT EXISTS (
    SELECT 1 FROM manutencoes x WHERE x.veiculo_id = v.id AND x.descricao = m.descricao
);

-- Verificação
SELECT v.placa, v.status, v.quilometragem,
       (SELECT MAX(m.data_manutencao) FROM manutencoes m
         WHERE m.veiculo_id = v.id AND m.tipo_manutencao = 'PREVENTIVA' AND m.status = 'CONCLUIDA') AS ultima_preventiva
FROM veiculos v
WHERE v.placa LIKE 'AL%'
ORDER BY v.placa;
