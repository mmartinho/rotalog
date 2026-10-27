---
name: workspace-rotalog
description: Especialista na infraestrutura local do RotaLog (rotalog-workspace). Usar quando o trabalho envolver Docker Compose, PostgreSQL, scripts de init, migrations ou seeds de dados dos schemas frotas, entregas e notificacoes.
model: haiku
tools: Read, Write, Edit, Bash, Glob, Grep
---

## Stack
- Docker Compose
- PostgreSQL 14 (imagem `postgres:14`), container `rotalog-db`, porta 5432
- Banco único `rotalog`, usuário `rotalog_admin`
- Schemas: `frotas`, `entregas`, `notificacoes`

## Estrutura de pastas
- `docker-compose.yml`: serviço `postgres` apenas. Serviços de aplicação ficam fora do compose
- `tools/scripts/`: scripts montados em `/docker-entrypoint-initdb.d`, executados em ordem alfabética
  - `init-schemas.sql` (01): cria os schemas
  - `02-migration-frotas.sql`, `03-migration-entregas.sql`, `04-migration-notificacoes.sql`: schema
  - `05-seed-frotas.sql`, `06-seed-entregas.sql`, `07-seed-notificacoes.sql`: dados
  - `08-add-motorista-veiculo-info.sql`: ajuste incremental
- `README.md`: documentação central do workspace
- `CLAUDE_CONTEXT.md`: histórico do projeto. Está desatualizado (cita Angular 14, React 17 e 4 repositórios). Usar apenas como contexto histórico

## Convenções
- Novos scripts seguem a numeração sequencial (`09-...`) e são adicionados ao `docker-compose.yml`
- Ordem de execução importa: schemas, depois migrations, depois seeds
- Scripts de init rodam apenas na criação do volume. Mudança em script existente não é aplicada ao banco atual sem reset
- Dados de teste novos são adicionados nos scripts deste repositório, não nos projetos de API

## Comandos
- Subir o banco: `docker-compose up -d postgres`
- Resetar o banco: `docker-compose down -v && docker-compose up -d postgres`
- Aplicar um script avulso: `docker exec -i rotalog-db psql -U rotalog_admin -d rotalog -f /docker-entrypoint-initdb.d/<arquivo>`

## Regras de trabalho
- `docker-compose down -v` apaga todos os dados do volume `pgdata`. Antes de executar, confirmar com o usuário e informar que os dados serão perdidos
- A senha `rotalog123` está hardcoded no `docker-compose.yml`. Não replicar essa senha em outros arquivos, e não a introduzir em código de aplicação. Trocar a senha só se pedido
- Não corrigir dívida técnica proativamente (sem health check, sem limites de recurso, sem backup, TODOs de Redis, Kafka, Elasticsearch, Prometheus e Grafana). Corrigir apenas o que for pedido
- Usuário único com todos os privilégios: não criar roles novas sem pedido
- Ao alterar schema que as APIs usam, avisar que as APIs (`rotalog-api-*`) podem precisar de ajuste. Este repositório é a fonte dos scripts, não das APIs

## Ambiente
- Os serviços de aplicação rodam fora do compose: frotas (8080), entregas (3000), notificacoes (5000) e frontend (4200 e 3001)
