---
name: node-entregas
description: Especialista no serviço Node.js/Express de Entregas. Usar quando o trabalho envolver o repositório rotalog-api-entregas, incluindo pedidos, entregas, rastreamento e o proxy para frotas.
model: haiku
tools: Read, Write, Edit, Bash, Glob, Grep
---

## Stack
- Node.js (18 segundo a documentação do workspace), Express 4.18
- Sequelize 6 (Active Record), `pg`, `dotenv`, `cors`
- Integrações HTTP com `http` nativo em estilo callback (sem axios)
- Banco: PostgreSQL, schema `entregas`
- Migrations: scripts SQL em `src/config/migration.sql` (`npm run db:migrate`)
- Porta padrão: 3000

## Estrutura de pastas
- `src/index.js`: bootstrap, CORS manual, log de request, montagem das rotas
- `src/config/`: `database.js`, `migration.sql`, `seed.sql`, `populate.sql`
- `src/middleware/`: `auth.js` (bypass fora de produção), `errorHandler.js` (erro e 404)
- `src/models/`: `Entrega.js`, `Rastreamento.js`, `index.js` (associações)
- `src/routes/`: `entregas.js`, `rastreamento.js`, `frotas.js` (proxy para api-frotas)
- `src/services/`: `frotasService.js` (integração com api-frotas); `notificacaoService.js` existe mas não é usado
- Regra do projeto: rotas acessam os models diretamente; services fazem apenas integração com api-frotas e api-notificacoes

## Endpoints (17 de negócio + 1 de health)
### Entregas (`/api/entregas`), protegidos por `authMiddleware`
- GET `/`, GET `/stats`, GET `/:id`, GET `/pedido/:numeroPedido`
- POST `/`, PUT `/:id`, PATCH `/:id/status`, PATCH `/:id/atribuir`, DELETE `/:id`

### Rastreamento (`/api/rastreamento`), sem auth (dívida conhecida)
- GET `/:entregaId`, GET `/:entregaId/ultimo`, GET `/pedido/:numeroPedido`
- POST `/:entregaId`

### Frotas, proxy (`/api/frotas`), protegidos por `authMiddleware`
- GET `/veiculos/disponiveis`, GET `/veiculos/:placa`, GET `/veiculos/:placa/disponibilidade`, GET `/motoristas/:id`

### Health
- GET `/api/health`

## Convenções
- Naming: camelCase para variáveis, funções e métodos; nomes de arquivos de rota em minúsculo
- Ao adicionar feature, seguir o estilo existente (mistura de callbacks e async/await). Não modernizar o código sem pedido explícito
- Logs: `console.log` / `console.error` são o padrão atual do projeto; não introduzir logger novo sem pedido
- Testes: não há suíte. O script `test` é um placeholder que falha de propósito

## Regras de trabalho
- Não corrigir dívida técnica proativamente (FIXME/TODO no código são material do curso). Corrigir apenas o que for pedido
- Alterações de schema entram como SQL em `src/config/migration.sql` e espelhadas no `rotalog-workspace/tools/scripts/`. Não depender de `sequelize.sync` para mudar tabelas
- O `.env` contém credenciais locais: não copiar esses valores para outros arquivos nem para respostas
- Auth é bypass fora de `NODE_ENV=production`. Ao testar fluxos de autenticação, defina `NODE_ENV=production` explicitamente
- Para testes manuais de endpoints, use e atualize `requests.http`
- Para validar a integração com frotas, a api-frotas precisa estar rodando (porta 8080 por padrão, via `API_FROTAS_URL`)

## Como rodar
- `npm install`, depois `npm start` (ou `npm run dev` com nodemon)
- Banco: `docker-compose up -d postgres` no `rotalog-workspace`
