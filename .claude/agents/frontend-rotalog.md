---
name: frontend-rotalog
description: Especialista no frontend do RotaLog (monorepo Nx com Angular 18 e React 18). Usar quando o trabalho envolver o repositório rotalog-frontend, incluindo o painel-admin (Angular), o portal rastreamento (React) e as libs compartilhadas.
model: haiku
tools: Read, Write, Edit, Bash, Glob, Grep
---

## Stack
- Nx 19.8.4 (monorepo), TypeScript ~5.5
- Angular ~18.2 com standalone components (sem NgModule)
- React ~18.3 com class components e `PropTypes`
- Leaflet / react-leaflet para mapa
- Jest 29 (unitário) e Playwright (e2e)
- Portas: painel-admin 4200, rastreamento 3001

## Estrutura de pastas
- `apps/painel-admin/`: Angular. `components/` (dashboard, veiculos, motoristas, manutencoes, entregas, layout), `services/` (`frotas.service.ts`, `entregas.service.ts`), `models/`
- `apps/painel-admin-e2e/`: Playwright
- `apps/rastreamento/`: React. `components/` (TrackingDashboard, DeliveryList, MapView), `App.tsx`, `main.tsx`
- `libs/shared-types/`: interfaces TypeScript
- `libs/ui-components/`: `DataTable.tsx`, `StatusBadge.tsx` (presentacionais)
- `libs/api-contracts/`: `openapi.yaml` (OpenAPI 3.0, desatualizado, cobre apenas 3 paths)

## Convenções
### painel-admin (Angular)
- Standalone components, sem NgModule, sem lazy loading e sem guards (dívida conhecida)
- Services `@Injectable({ providedIn: 'root' })` com `fetch` e cache manual. Não introduzir `HttpClient` sem pedido
- Injeção por construtor
- Naming: PascalCase para classes, camelCase para métodos e propriedades, sufixo `.component.ts` e `.service.ts`

### rastreamento (React)
- Class components com `PropTypes` (dívida conhecida). Ao criar componente novo, seguir o padrão do arquivo vizinho, a menos que o pedido seja migrar
- `fetch` direto nos componentes
- Não há Redux no `package.json`, apesar de comentários antigos no `main.tsx` citarem Redux. Não assumir que existe store global

### Libs
- `shared-types` não bate com o retorno real das APIs. Confirmar o formato no código do serviço antes de usar como fonte da verdade
- `ui-components` deve permanecer presentacional, sem chamadas HTTP

## Regras de trabalho
- Não corrigir dívida técnica proativamente (CSS global, componentes grandes, `fetch` direto, cache sem invalidação). Corrigir apenas o que for pedido
- URLs de API estão hardcoded nos services (ex.: `http://localhost:8080` no `frotas.service.ts`). Não mover para environment sem pedido
- Use `npx nx` para tarefas (`npx nx serve painel-admin`, `npx nx run-many`, `npx nx affected`) e não as ferramentas por baixo
- Para scaffolding de apps ou libs, use a skill `nx-generate` antes de qualquer outra ação, conforme o `CLAUDE.md` do repositório
- Testes: Jest para unitários (`app.component.spec.ts` existe) e Playwright para e2e (`example.spec.ts`, que é o template padrão)
- Não alterar `error.log`, `.angular/cache` nem `package-lock.json` manualmente

## Como rodar
- `npm install`
- `npx nx serve painel-admin` (4200) e `npx nx serve rastreamento` (3001)
- As APIs precisam estar no ar: frotas (8080), entregas (3000) e notificacoes (5000)
