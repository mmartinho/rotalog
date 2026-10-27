# RotaLog Workspace

Configuração de infraestrutura do RotaLog — sistema de gestão de frotas e entregas.

## Serviços

| Serviço            | Stack                | Porta | Repositório                  |
|--------------------|----------------------|-------|------------------------------|
| API Frotas         | Java / Spring Boot   | 8080  | rotalog-api-frotas           |
| API Entregas       | Node.js / Express    | 3000  | rotalog-api-entregas         |
| API Notificações   | .NET Core 6          | 5000  | rotalog-api-notificacoes     |
| Painel Admin       | Angular (NX)         | 4200  | rotalog-frontend             |
| Rastreamento       | React (NX)           | 3001  | rotalog-frontend             |

## Banco de Dados

PostgreSQL 14 com três schemas:

- `frotas` — veículos, motoristas, manutenções
- `entregas` — pedidos, rotas, rastreamento
- `notificacoes` — templates, histórico de envio

Os scripts em `tools/scripts/` criam os schemas e populam as tabelas automaticamente
na primeira execução do container.

### Scripts de `tools/scripts/`

| Script | Conteúdo |
|--------|----------|
| `init-schemas.sql` (01) | Schemas `frotas`, `entregas` e `notificacoes` |
| `02` a `04` | Tabelas de cada serviço |
| `05` a `07` | Seeds de frotas, entregas e notificações |
| `08-add-motorista-veiculo-info.sql` | Colunas `motorista_nome` e `veiculo_modelo` em entregas |
| `09-alertas-manutencao.sql` | Tabela `frotas.alertas_manutencao` (alerta de manutenção preventiva) |
| `10-seed-alertas-manutencao.sql` | Veículos elegíveis para o alerta de manutenção preventiva |

Os scripts `09` e `10` são idempotentes. O seed `10` cria os veículos `ALK1A11` (elegível por km),
`ALT2B22` (por tempo), `ALX3C33` (km e tempo), `ALS5E55` (sem preventiva, km total alto),
`ALN4D44` (não elegível) e `ALI6F66` (inativo), com datas relativas a `NOW()`. Os veículos do script `05`
têm datas de 2024 e também ficam elegíveis por tempo.

**Atenção:** os scripts rodam apenas quando o volume é criado. Quem já tem o ambiente precisa rodar
`docker-compose down -v && docker-compose up -d postgres` (apaga os dados) ou executar os scripts novos na mão:

```bash
docker exec -i rotalog-db psql -U rotalog_admin -d rotalog < tools/scripts/09-alertas-manutencao.sql
docker exec -i rotalog-db psql -U rotalog_admin -d rotalog < tools/scripts/10-seed-alertas-manutencao.sql
```

Para repetir o E2E é preciso limpar os alertas:
`docker exec rotalog-db psql -U rotalog_admin -d rotalog -c "TRUNCATE frotas.alertas_manutencao RESTART IDENTITY"`.

## Como subir

```bash
docker-compose up -d
```

Isso sobe o PostgreSQL e executa os scripts de inicialização. As APIs e frontends
precisam ser iniciados separadamente em seus respectivos repositórios.

## Estrutura

```
rotalog-workspace/
├── docker-compose.yml
├── tools/
│   └── scripts/       # SQL de inicialização (schemas, migrations, seeds)
└── README.md
```

## Observações

- Se precisar resetar o banco, apague o volume do Docker antes de subir novamente:
  `docker-compose down -v && docker-compose up -d`
- Redis e Kafka estão comentados no compose — nunca foram implementados.
