# Rotalog

## Visão Geral

Sistema de gestão de frotas e entregas. Gerencia veículos, motoristas, entregas e notificações. Composto por 3 APIs de backend e 2 frontends (monorepo nx).

## Repositórios

### rotalog-api-frotas (Java/Sprint boot 2.7)

- Reponsabilidade: veículos, motoristas e manutenções
- Banco: PostgreSQL (schema `frotas`, incluindo `alertas_manutencao`)
- Variáveis de ambiente: `ROTALOG_EMAIL_GESTOR` (e-mail do gestor dos alertas de manutenção preventiva; padrão `gestor@rotalog.com`, não pode ficar vazia) e `ROTALOG_NOTIFICACOES_URL` (padrão `http://localhost:5000`)
- Migrations: Flyway (`V1`, `V2`, `V3`), mas desabilitado (`spring.flyway.enabled=false`)
- Estrutura: controller -> service -> repository
- Entidades JPA em domain (o README cita model)
- Padrões: REST com 30 endpoints de negócio e 1 de health (`/health`), Spring Data JPA (`JpaRepository`), DTOs de request separados das entidades, clientes HTTP para api-entregas (`EntregaClient`, com `RestTemplate` criado manualmente) e api-notificacoes (`NotificacaoClient`, com `RestTemplate` `@Bean` com timeouts), injeção por campo com `@Autowired`, tratamento de erro manual com `ResponseEntity` (sem `@ControllerAdvice`), Lombok nas entidades, `ddl-auto=validate`, Swagger não habilitado

### rotalog-api-entregas (Node.js/Express 4)

- Reponsabilidade: pedidos, rotas e rastreamento de entregas
- Banco: PostgreSQL (schema `entregas`)
- ORM: Sequelize
- Migrations: scripts SQL em `src/config/migration.sql` (executados via `npm run db:migrate`)
- Estrutura: routes (acessam os models diretamente) e services (apenas integração com api-frotas e api-notificacoes)
- Middlewares de autenticação, tratamento de erro e 404 em `src/middleware/`
- Padrões: REST com 17 endpoints e 1 de health (`/api/health`), Active Record (models Sequelize), proxy para a api-frotas em `/api/frotas` com `http` nativo em estilo callback, cadeia de middlewares do Express, autenticação em bypass fora de produção, `notificacaoService` existente mas não usado, Swagger não habilitado, `requests.http` para testes manuais

### rotalog-api-notificacoes (.NET Core 6)

- Reponsabilidade: envio de notificações transacionais por email e SMS, e histórico de envios
- Banco: PostgreSQL (schema `notificacoes`) via Entity Framework Core
- Migrations: `Data/migration.sql` existe, mas o esquema é criado por `EnsureCreated()` na inicialização
- Estrutura: Controllers -> Services -> Data (`NotificacoesDbContext`), com DTOs e Models (as pastas Domain/Application/Infrastructure citadas no README não existem)
- Testes: xUnit em `tests/Notificacoes.Tests` (`dotnet test rotalog-api-notificacoes.sln`)
- Porta 5000, health check em `/health`
- Padrões: REST com 9 endpoints e 1 de health (`/api/health`), Swagger habilitado em `/swagger`, MediatR registrado mas não utilizado, `NotificacaoService` como god class concreta registrada sem interface, controller acessando o `DbContext` via service (sem repository), DTOs de request e response, templates com substituição de variáveis, seleção de canal por `if/else` (email/sms), envio de e-mail simulado (`Random` e `Task.Delay`) atrás de `IEnvioEmail`, try/catch genérico em cada endpoint

### rotalog-frontend (Nx monorepo: Angular 18 + React 18)

- Reponsabilidade: painel administrativo de gestão (`painel-admin`) e portal público de rastreamento de entregas (`rastreamento`)
- Apps:
  - `apps/painel-admin/`: Angular 18 com standalone components (porta 4200)
  - `apps/rastreamento/`: React 18 (porta 3001)
- Libs compartilhadas:
  - `libs/shared-types/`: interfaces TypeScript
  - `libs/ui-components/`: componentes Angular reutilizáveis
  - `libs/api-contracts/`: especificações OpenAPI
- Subir um app: `npx nx serve <app>`
- Padrões: Nx monorepo com libs compartilhadas; painel-admin com standalone components sem NgModule, services `@Injectable({ providedIn: 'root' })` que usam `fetch` e cache manual (sem `HttpClient`), injeção por construtor, sem lazy loading e sem guards; rastreamento com class components React e `PropTypes` no lugar de tipos TypeScript, com `fetch` direto; `ui-components` com `DataTable` e `StatusBadge` (presentational); `shared-types` com interfaces que não batem com o backend; `api-contracts` com OpenAPI 3.0 de 3 paths, desatualizado; Jest e Playwright configurados, com 2 arquivos `.spec.ts`

### rotalog-workspace (Docker Compose)

- Reponsabilidade: infraestrutura local, com PostgreSQL 14 (porta 5432) e banco `rotalog`
- Banco: schemas `frotas`, `entregas` e `notificacoes`, criados pelos scripts em `tools/scripts/`
- Migrations: scripts SQL numerados (`02` a `10`) montados como `docker-entrypoint-initdb.d`, executados apenas na criação do volume
- Subir o banco: `docker-compose up -d postgres`
- Resetar o banco: `docker-compose down -v && docker-compose up -d postgres`
- Padrões: schema-per-service em um banco único compartilhado, scripts de init em `docker-entrypoint-initdb.d`, um único usuário (`rotalog_admin`) com todos os privilégios, serviços de aplicação fora do compose, sem health check nem limites de recurso
