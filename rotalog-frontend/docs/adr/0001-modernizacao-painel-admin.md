# ADR 0001: Modernização do painel-admin (Angular 18) e lazy loading por rota

- **Status:** Proposta
- **Data:** 2026-10-14
- **Escopo:** `rotalog-frontend/apps/painel-admin/` (todos os componentes: `dashboard`, `entregas`, `layout`, `manutencoes`, `motoristas`, `veiculos`; services `frotas.service.ts` e `entregas.service.ts`; `app.routes.ts`, `app.config.ts`, `app.component.spec.ts`)
- **Origem:** análise estática do código em 2026-10-14 e um build de produção (`npx nx build painel-admin`) com a saída em pasta temporária, depois removida. Nenhuma alteração de código foi feita nesta etapa. Os testes não foram executados, porque o spec atual está quebrado (ver D14).

## Contexto

O painel-admin usa Angular `18.2.14` com standalone components e `provideZoneChangeDetection` com `eventCoalescing`. A base já está parcialmente moderna (sem NgModule, bootstrap por `bootstrapApplication`), mas a maior parte das práticas atuais do Angular ainda não foi adotada. Os 79 `TODO` do código documentam quase todas as lacunas, e alguns deles descrevem problemas reais de comportamento, não só de estilo.

### Respostas diretas às perguntas de escopo

- **As chamadas HTTP usam `HttpClient`?** Não. Os dois services fazem 14 chamadas com `fetch` (`frotas.service.ts`, `entregas.service.ts`). A única ocorrência de `HttpClient` no painel é um comentário `TODO`. Não há interceptors.
- **Lazy loading é possível?** Sim. O Angular 18 já suporta `loadComponent` nas rotas standalone. Hoje nenhuma rota usa, e os cinco componentes de tela são importados de forma estática em `app.routes.ts:13-17`. O ganho de tamanho inicial é modesto (ver M6), mas o padrão é barato e prepara o projeto para crescer.

### Problemas identificados

| # | Problema | Evidência | Risco |
|---|---|---|---|
| D1 | HTTP com `fetch`, sem `HttpClient`, sem interceptors | `frotas.service.ts:25-185`, `entregas.service.ts:11-47`; `app.component.ts` (TODO de interceptors) | Sem ponto único para auth, erro, log ou header. Difícil de testar com `HttpTestingController` |
| D2 | Erros engolidos pelos services: retornam `[]` ou `null` | `frotas.service.ts:42, 53, 69, 85, 117, 128, 144, 160, 173, 185`; `entregas.service.ts:23, 34, 46` | A UI não distingue "lista vazia" de "API fora do ar". O banner de erro do dashboard (`dashboard.component.ts:338`) **nunca dispara**, porque o `try/catch` do componente nunca recebe exceção |
| D3 | Falha de escrita não aparece para o usuário | `veiculos.component.ts:446-453` (fecha o formulário e recarrega independentemente do resultado); `veiculos.component.ts:459-461` (exclusão ignora o `boolean`) | Usuário acredita que salvou ou excluiu algo que não foi persistido |
| D4 | Cache manual sem invalidação consistente | `frotas.service.ts:16-17` e `:25-33`; `limparCache()` chamado manualmente em `veiculos.component.ts:452, 460` e `motoristas.component.ts` (após cada escrita); `getManutencoes` e `entregas` sem cache | Dados obsoletos entre telas e lógica de invalidação espalhada pelos componentes |
| D5 | Navegação por `window.location.href` | `dashboard.component.ts:346-347` (`navigateTo`) | Cada clique recarrega a aplicação inteira e zera todos os caches em memória. O `routerLink` já é usado no `sidebar.component.ts` |
| D6 | Carregamento sequencial no dashboard | `dashboard.component.ts:314-316` (três `await` em série) | Latência somada das três chamadas |
| D7 | Dados fictícios em tela de gestão | `dashboard.component.ts:290-295` (alertas com datas de março de 2026 e textos fixos), `dashboard.component.ts:332-333` (`totalManutencoes = 6`, `manutencoesPendentes = 2`) | O painel mostra contagens que não vêm de nenhuma API |
| D8 | Sem lazy loading | `app.routes.ts:8` (TODO), `app.routes.ts:13-17` | Todo o código de telas entra no bundle inicial. Build atual: `main` com 328 kB brutos (79 kB transferidos) |
| D9 | Change detection padrão, sem `OnPush` | Nenhum componente com `ChangeDetectionStrategy.OnPush` (a única ocorrência de `OnPush` é um TODO em `dashboard.component.ts`) | Todos os componentes são verificados a cada evento, mesmo com estado simples |
| D10 | Control flow legado (`*ngIf`, `*ngFor`) e `CommonModule` | 5 arquivos de componente usam `*ngIf`/`*ngFor` | Sintaxe antiga. O Angular recomenda `@if`/`@for` desde a v17 |
| D11 | Formulários com `ngModel` e validação só por `alert()` | `veiculos.component.ts:82-115` e `:438-443`; `motoristas.component.ts:83-106` e checagem de campos obrigatórios com `alert` | Sem `Validators`, sem erro por campo, `Partial<Veiculo>` como tipo do formulário |
| D12 | Componentes grandes com estado público e lógica de negócio | `veiculos.component.ts` (464 linhas), `dashboard.component.ts` (349 linhas, todos os campos públicos, cálculos no componente); `motoristas.component.ts` é cópia de `veiculos` (`motoristas.component.ts:10`) | Difícil de testar e de reutilizar. CRUD duplicado entre veículos e motoristas |
| D13 | Estilos com orçamento estourado | Build: 4 componentes excedem o `anyComponentStyle` de 2 kB (`dashboard` 2.29 kB, `veiculos` 2.78 kB, `motoristas` 2.25 kB, `entregas` 2.26 kB); CSS duplicado entre `veiculos`, `motoristas` e `manutencoes` | Warnings no build e CSS repetido. Há também `styles.css` global (CLAUDE.md lista como dívida conhecida) |
| D14 | Código morto e teste quebrado | `nx-welcome.component.ts` (808 linhas, usado só pelo spec); `app.component.spec.ts:18` espera o texto `Welcome painel-admin`, que não existe mais no `AppComponent` | Suite de testes vermelha desde a geração do projeto. Não há teste útil do painel |
| D15 | Bibliotecas compartilhadas não usadas | Nenhum arquivo do painel importa `libs/ui-components` nem `libs/shared-types`; `tsconfig.base.json` não tem `paths`; `models/index.ts` duplica as interfaces | `DataTable` e `StatusBadge` existem mas não são usados. As interfaces locais não batem com o backend (CLAUDE.md alerta para isso) |
| D16 | URLs de API hardcoded | `frotas.service.ts:8` (`http://localhost:8080`), `entregas.service.ts:6` (`http://localhost:3000`) | Troca de ambiente exige editar código. CLAUDE.md proíbe mover sem pedido (ver Q4) |
| D17 | Versão do framework sem suporte | `package.json`: `@angular/core ~18.2`, instalado `18.2.14` | O Angular 18 já saiu da janela de suporte oficial (confirmar as datas na documentação no momento da execução). Signals estáveis, control flow e zoneless evoluíram depois |

## Ranking das modernizações por esforço × benefício

A ordem combina esforço (muito baixo a alto) e benefício (baixo a alto). Itens com esforço muito baixo e benefício médio ou alto vêm primeiro. Os itens de esforço alto ficam por último, mesmo quando o benefício é alto, porque dependem de outros.

| Ordem | Id | Modernização | Esforço | Benefício | Depende de |
|---|---|---|---|---|---|
| 1 | M1 | Trocar `window.location.href` por `routerLink`/`Router.navigate` (D5) | Muito baixo | Alto: navegação de SPA real, sem perder cache nem estado | — |
| 2 | M2 | Carregar as três listas do dashboard em paralelo com `Promise.all` (D6) | Muito baixo | Médio: latência do dashboard cai para a da chamada mais lenta | — |
| 3 | M3 | Remover `nx-welcome.component.ts` e corrigir `app.component.spec.ts` (D14) | Muito baixo | Médio: suite volta a ser verde e serve de base para as demais mudanças | — |
| 4 | M4 | Substituir os dados fictícios do dashboard por dados da API (D7). Manutenções podem sair de `getManutencoes()`, que já existe. Alertas saem ou ficam marcados como não implementados | Baixo | Alto: tela de gestão deixa de mostrar número inventado | Q3 |
| 5 | M5 | Converter `*ngIf`/`*ngFor` para `@if`/`@for` com o schematic oficial `ng generate @angular/core:control-flow` (D10) | Baixo | Médio: sintaxe atual, `track` obrigatório no `@for`, menos `CommonModule` | — |
| 6 | M6 | Lazy loading por rota com `loadComponent` (D8) | Baixo | Médio: cada tela vira um chunk; o ganho no bundle inicial é limitado ao código dos cinco componentes, porque o framework continua no `main` | M3 |
| 7 | M7 | `environment.ts` com `fileReplacements` no `project.json` (D16) | Baixo | Médio: deploy sem editar código | Q4 |
| 8 | M8 | `HttpClient` com `provideHttpClient` e um interceptor de erro. Services passam a propagar erro tipado. Componentes mostram a falha (D1, D2, D3) | Médio | Alto: ponto único para auth, erro e log; testável; a UI passa a refletir falhas reais | Q1 |
| 9 | M9 | Estado reativo com invalidação automática (signals ou `shareReplay` com `BehaviorSubject`) no lugar do cache manual (D4) | Médio | Médio-alto: some a lógica de `limparCache()` espalhada pelos componentes | M8 |
| 10 | M10 | Reactive Forms tipados, com `Validators` e mensagem por campo (D11) | Médio | Médio: validação real de placa, CPF e CNH | — |
| 11 | M11 | `ChangeDetectionStrategy.OnPush` e signals nos componentes (D9) | Médio | Médio: menos verificações; base para zoneless | M12 para zoneless |
| 12 | M12 | Atualizar Angular 18.2 para uma versão com suporte (D17), em etapas de `ng update` | Médio-alto | Alto: suporte, correções e recursos atuais | Fase 0 |
| 13 | M13 | Quebrar os componentes grandes e criar uma tabela/CRUD genérico que use `ui-components` (D12, D13, D15) | Alto | Alto: manutenção e reuso. Remove a cópia entre veículos e motoristas | M5, M9, M10 |

Observações sobre a ordem:

- **M6 tem benefício menor do que parece.** O `main` atual já inclui Angular core, router e forms (328 kB brutos). Os cinco componentes de tela estão no mesmo chunk, e o tamanho exato deles só será conhecido depois da conversão. A medição deve ser feita antes e depois. O valor de longo prazo é isolar cada tela, e não reduzir o bundle agora.
- **M12 vem depois de M1 a M6, mas antes de M9 a M11.** Signals estáveis e control flow ficam mais simples na versão atual, e fazer M9 a M11 no Angular 18 seria trabalho que depois precisaria ser refeito.
- **M8 é o item de maior benefício real, mas exige aprovação**, porque a convenção atual do projeto proíbe `HttpClient` sem pedido (ver seção "Regras do projeto").

## Decisão

Adotar a modernização em fases, na ordem abaixo. Cada fase é implantável sozinha.

### 1. Ordem de execução

| Fase | Conteúdo | Depende de |
|---|---|---|
| 0 | **Rede de segurança.** M3 (remover `nx-welcome` e corrigir o spec). Fazer `npx nx test painel-admin` passar. Adicionar, no `painel-admin-e2e`, um smoke test que abre as cinco rotas e verifica o título de cada tela. Nenhum comportamento muda. | Nada |
| 1 | **Quick wins sem mudança de contrato com a API.** M1, M2, M4, M5, M6. | Fase 0; Q3 para M4 |
| 2 | **Configuração.** M7. | Q4 |
| 3 | **HTTP e erros.** M8 e M9. Os services passam a usar `HttpClient`, e a convenção do projeto é atualizada antes de começar. | Q1 |
| 4 | **Framework.** M12, uma etapa de `ng update` por versão maior, com o smoke test da fase 0 verde em cada passo. | Fase 0 |
| 5 | **Forms, detecção de mudanças e componentes.** M10, M11, M13. | Fases 3 e 4 |

### 2. Forma alvo dos services (fase 3)

```ts
@Injectable({ providedIn: 'root' })
export class FrotasService {
  private http = inject(HttpClient);

  veiculos(): Observable<Veiculo[]> {
    return this.http.get<Veiculo[]>(`${API_URL}/api/veiculos`);
  }
}
```

- Um interceptor (`errorInterceptor`) registra o erro e o repassa. Cada componente decide como mostrar a falha, com mensagem e botão de tentar novamente.
- `HttpClient` entra com `provideHttpClient(withInterceptors([...]))` em `app.config.ts`.
- O cache passa a ser responsabilidade de um store por entidade (M9), e não de um `if (cache)` dentro do service.
- Os services continuam em `services/`, com o mesmo nome de arquivo, para não espalhar a mudança.

### 3. Lazy loading (fase 1, M6)

```ts
export const appRoutes: Route[] = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: 'dashboard', loadComponent: () => import('./components/dashboard/dashboard.component').then(m => m.DashboardComponent) },
  // demais rotas no mesmo padrão
  { path: '**', redirectTo: 'dashboard' }
];
```

- Sem guards nem resolvers nesta etapa. Os TODOs de guard continuam registrados (ver dívidas).
- Remover também a importação de `SidebarComponent` do `AppComponent` apenas se o layout deixar de depender dele. Nesta ADR, o layout continua como está.

### 4. Regras do projeto afetadas

Esta ADR muda quatro pontos documentados, e a atualização desses documentos faz parte da decisão:

- `CLAUDE.md`, seção `rotalog-frontend`: "sem lazy loading e sem guards" passa a dizer "lazy loading por rota (fase 1); guards pendentes".
- `CLAUDE.md`, seção `rotalog-frontend`: "services ... com `fetch` e cache manual (sem `HttpClient`)" passa a dizer "services com `HttpClient` e interceptor de erro", quando a fase 3 for concluída.
- `.claude/agents/frontend-rotalog.md`, seção "painel-admin (Angular)": a linha "Não introduzir `HttpClient` sem pedido" deixa de existir depois da aprovação de Q1. A linha "sem lazy loading" também deixa de existir na fase 1.
- `.claude/agents/frontend-rotalog.md`, seção "Regras de trabalho": "Não corrigir dívida técnica proativamente" continua valendo para tudo que não esteja listado nesta ADR. Os itens listados aqui são o pedido explícito.

Até a aprovação desta ADR, nenhuma dessas regras muda.

## Mudanças de comportamento

Estas mudanças são visíveis para quem usa o painel:

- O dashboard navega entre telas sem recarregar a página (M1).
- Se a API estiver fora do ar, o dashboard mostra o banner de erro e o botão de tentar novamente (M8). Hoje ele mostra zeros.
- Se criar, editar ou excluir falhar, o formulário continua aberto e aparece uma mensagem de erro (M8). Hoje o formulário fecha como se tivesse dado certo.
- Os totais de manutenção deixam de ser fixos (M4). Os alertas fictícios saem da tela ou ficam marcados como não implementados.
- Cada tela carrega seu código na primeira visita (M6). Pode haver um pequeno atraso na primeira navegação para cada tela.
- URLs de API não mudam por padrão em desenvolvimento (M7).

## Alternativas consideradas

- **Angular Material ou PrimeNG agora.** Adiada. O projeto já tem `ui-components`, mesmo que não use. Reavaliar depois da fase 5, porque biblioteca de componentes é uma decisão de design separada.
- **NgRx ou store global.** Descartada. São quatro entidades com CRUD simples. Signals ou `shareReplay` bastam.
- **Zoneless já na fase 3.** Adiada. Depende de M11 e da versão do Angular (M12). Avaliar depois da fase 5.
- **Manter `fetch` e corrigir só os erros (D2, D3).** Descartada como solução final. Resolve a falha silenciosa, mas não resolve D1 nem dá um ponto para o interceptor de auth quando houver login.
- **Reescrever o painel de uma vez.** Descartada. Não há teste para proteger o comportamento atual, e o risco de regressão é alto. A fase 0 vem antes.
- **Não usar lazy loading.** Descartada. O ganho inicial é modesto, mas o custo é só uma linha por rota, e a alternativa deixa o padrão ausente para futuras telas pesadas.

## Dívidas registradas (fora do escopo desta ADR)

1. **Painel sem autenticação e sem guards** (`app.component.ts`, `app.routes.ts`). Depende de decisão de emissão de JWT, que é a Q1 da ADR do `api-entregas`. Precisa de ADR própria.
2. **CSS global em `styles.css`** (`styles.css:1-3`). Já listado no CLAUDE.md como dívida conhecida. Só será tratado junto com M13.
3. **Sidebar sem logout e sem usuário logado** (`sidebar.component.ts`, rodapé com TODOs).
4. **Ordenação e paginação no front** (`veiculos.component.ts`, `ordenar()`). Sem paginação no backend, a lista carrega tudo de uma vez.
5. **Inconsistência de status entre telas e models.** `veiculos` filtra `EM_MANUTENCAO`; `models/index.ts` define `MANUTENCAO = 'EM_MANUTENCAO'` e `BAIXADO`, que não aparecem nas telas. A lista de status deve ser confirmada com o `api-frotas`.
6. **`EntregasService.getEntregasPorMotorista` nunca usado** (`entregas.service.ts:40-48`).
7. **Interfaces com nomes mistos snake_case e camelCase** (`models/index.ts`). Confirmar o formato real das respostas antes de mudar.
8. **Sem atualização em tempo real para entregas** (`entregas.component.ts`, TODO de WebSocket).
9. **Exclusão com `confirm()` nativo** (`veiculos.component.ts`, TODO). Substituir por componente de diálogo faz parte de M13.
10. **Comentários `TODO` que descrevem comportamento já resolvido.** Depois de cada item de M1 a M13, remover o TODO correspondente. A remoção só ocorre junto com o item, nunca em lote.

## Questões em aberto

- **Q1. Podemos adotar `HttpClient` e interceptors?** A convenção atual do projeto proíbe sem pedido, e esta ADR é o pedido. **Recomendação:** sim, na fase 3, com a atualização de `CLAUDE.md` e do agente.
- **Q2. Qual versão do Angular é o alvo de M12?** A última versão com suporte no momento da execução, ou a LTS? **Recomendação:** a LTS mais recente com suporte, confirmada na documentação oficial antes de começar.
- **Q3. De onde devem vir os dados do dashboard?** Manutenções podem sair de `GET /api/manutencoes`. Os alertas precisam de endpoint próprio, que não existe. **Recomendação:** M4 usa a API para manutenções e remove os alertas até existir endpoint.
- **Q4. Podemos mover as URLs para `environment.ts`?** O CLAUDE.md diz que não sem pedido. **Recomendação:** sim, na fase 2, porque o pedido desta ADR cobre M7.
- **Q5. A fase 0 deve incluir o smoke test de e2e?** Hoje o `painel-admin-e2e` tem só o template `example.spec.ts`. **Recomendação:** sim, com um teste por rota, sem depender de dados específicos da API.

## Como verificar

```bash
cd rotalog-frontend

# Build de produção: orçamentos de estilo (hoje 4 avisos)
npx nx build painel-admin

# Testes unitários (hoje falham por D14, esperado até a fase 0)
npx nx test painel-admin

# Smoke test de e2e (a ser criado na fase 0)
npx nx e2e painel-admin-e2e

# Após M8: nenhuma chamada fetch nos services
grep -rn "fetch(" apps/painel-admin/src --include=*.ts

# Após M1: nenhuma navegação por window.location
grep -rn "window.location" apps/painel-admin/src --include=*.ts

# Após M5: nenhuma diretiva estrutural antiga
grep -rn "\*ngIf\|\*ngFor" apps/painel-admin/src --include=*.ts

# Após M6: rotas com loadComponent
grep -n "loadComponent" apps/painel-admin/src/app/app.routes.ts
```

Critérios de aceite por fase:

- Fase 0: `npx nx test painel-admin` verde; smoke test de e2e cobrindo as cinco rotas.
- Fase 1: navegação do dashboard sem recarregar a página (verificável no smoke test); dashboard sem número fixo; `main` do build medido antes e depois de M6 e registrado nesta ADR.
- Fase 3: API derrubada → dashboard mostra erro; criação com falha → formulário permanece aberto com mensagem.
- Fase 4: `npx nx build painel-admin` sem erro depois de cada `ng update`.

## Consequências

### Positivas

- Falhas da API deixam de ser invisíveis. Hoje o usuário não sabe quando nada foi salvo.
- Navegação por rota preserva o estado da aplicação e os caches.
- O dashboard passa a mostrar dados reais, ou a indicar que ainda não há dados.
- `HttpClient` permite testes com `HttpTestingController`, sem subir a API.
- O atraso de atualização do framework fica registrado e tem um caminho definido.

### Negativas

- A convenção do projeto muda em quatro pontos (`CLAUDE.md` e o agente `frontend-rotalog`), e isso precisa ser feito junto.
- A fase 0 é trabalho sem ganho visível para o usuário final.
- Converter todo o painel para `@if`/`@for`, `HttpClient` e Reactive Forms é um volume de mudança grande. Cada fase precisa ser revisada separadamente.
- Migrar o Angular 18 para uma versão atual pode exigir mudanças em dependências do Nx e do Jest. A compatibilidade precisa ser verificada no `package.json` antes de começar.
- O ganho de bundle do lazy loading é pequeno neste tamanho de app. O benefício real aparece quando novas telas forem adicionadas.
