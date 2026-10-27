# ADR 0002: Modernização do app `rastreamento` (React 18): atualização de versão, componentes com hooks e TypeScript

- **Status:** Proposta
- **Data:** 2026-10-14
- **Escopo:** `rotalog-frontend/apps/rastreamento/` (`App.tsx`, `main.tsx`, `components/DeliveryList.tsx`, `components/TrackingDashboard.tsx`, `components/MapView.tsx`, `tsconfig.app.json`), além de `libs/ui-components/` e `libs/shared-types/` quando relevantes.
- **Origem:** análise estática do código em 2026-10-14, `tsc --noEmit --strict` sobre o `tsconfig.app.json` (sem emitir arquivos) e leitura de `rotalog-api-entregas` (`routes/entregas.js`, `routes/rastreamento.js`, `index.js`). Nenhuma alteração de código foi feita nesta etapa. O app não tem testes, então nenhum comportamento foi executado. O build também não foi executado.

## Contexto

O `rastreamento` é um portal público com uma única tela: lista de entregas à esquerda, painel de detalhes e mapa à direita. Tem 4 componentes, todos **class components**, e nenhum hook. Não há arquivo de teste no app.

Fatos medidos:

- **React `18.3.1`** e **react-dom `18.3.1`** instalados (`package.json` com `~18.3.1`). `main.tsx` já usa `ReactDOM.createRoot` e `React.StrictMode`.
- **4 class components** (`App`, `DeliveryList`, `MapView`, `TrackingDashboard`) e **0 function components**.
- **TypeScript com checagem desligada no app:** `apps/rastreamento/tsconfig.app.json:7` tem `"strict": false`, enquanto `tsconfig.base.json:14` tem `true`. Os componentes usam `React.Component<any, any>` e `any` em props, estado e handlers.
- **PropTypes** em todos os componentes, alguns incompletos (ver R10).
- **Leaflet** é carregado por CDN (`unpkg`, com SRI) em `MapView.tsx:53-54`, mesmo com `leaflet` declarado em `dependencies`. `react-leaflet` também está em `dependencies`, mas não é importado em nenhum arquivo.
- O app não importa `libs/shared-types` nem `libs/ui-components`.
- Chamadas HTTP com `fetch` direto e URL fixa: `App.tsx:40` (`/api/entregas`) e `TrackingDashboard.tsx:56` (`/api/entregas/:id`).

### Respostas diretas às perguntas de escopo

- **Qual o impacto de atualizar do React 18 para a versão atual?** Baixo no código do app, médio no toolchain. O app usa só APIs que continuam existindo. O que muda é a validação de props e a compatibilidade das ferramentas. Detalhes na seção 1 abaixo.
- **Class components ou function components com hooks, e o que ganhamos?** Hooks reúnem o ciclo de vida de cada efeito num lugar só, permitem reaproveitar lógica entre componentes e deixam o código alinhado ao que o ecossistema e a documentação do React recomendam para código novo. Não há ganho de desempenho por si só. Detalhes na seção 2.
- **Quais os riscos de PropTypes em vez de TypeScript?** PropTypes checam só em desenvolvimento, não tipam o contrato com a API e somem no React 19. O TypeScript do app também está praticamente desligado. Detalhes na seção 3.
- **Qual a ordem das modernizações por esforço × benefício?** Ver o ranking na seção "Ranking".

## 1. Impacto da atualização do React 18 para a versão atual

A versão alvo é a última 19.x estável no momento da execução. Não fixamos número aqui porque a versão exata deve ser confirmada na documentação oficial antes de começar (pergunta Q4).

| Item | Situação no `rastreamento` | Impacto |
|---|---|---|
| `ReactDOM.createRoot` | Já usado em `main.tsx` | Nenhum |
| `propTypes` | Usado nos 4 componentes e em `libs/ui-components` | **Alto.** No React 19 os `propTypes` deixam de ser verificados, em silêncio. Sem TypeScript no lugar, o app perde a única checagem de props que existe hoje. Por isso o TypeScript (fase 3) vem antes da atualização (fase 5) |
| Class components | Os 4 componentes | Nenhum imediato. Continuam suportados no React 19. A atualização **não obriga** a migração |
| APIs removidas no 19 (`findDOMNode`, `ReactDOM.render`, `contextTypes`, `defaultProps` em função, string refs) | Grep em `apps/rastreamento/src` e `libs/ui-components/src` não encontrou uso | Nenhum |
| `React.StrictMode` | Já ativo em `main.tsx` | Nenhum. Em dev o app já monta, desmonta e remonta os componentes. Os `componentWillUnmount` atuais limpam o `setInterval` e o mapa, então isso já é seguro |
| `@types/react ~18.3` | `React.Component<any, any>` | Baixo. Mudanças de tipagem podem exigir ajustes pontuais |
| `@nx/react 19.8.4` e `@nx/webpack` | Ferramentas de build do monorepo | **Médio. A verificar.** A compatibilidade com React 19 não foi confirmada aqui. Pode exigir atualizar o Nx |
| `react-leaflet ^4.2.1` | Declarado, não usado | Nenhum no código. A versão 4 é para React 18. Remover antes (fase 1) elimina a dúvida |

Pontos de acoplamento:

- O Nx e o npm são **compartilhados com o `painel-admin` (Angular 18)**. Se a atualização do Nx for necessária, ela afeta o painel também. Isso precisa ser coordenado com a ADR 0001 do frontend (fase 4 dela).
- A atualização do React não resolve nenhum dos problemas de comportamento listados em R1 a R9. Eles são independentes da versão.

**Conclusão:** a atualização para React 19 é barata no código do app, mas só deve acontecer depois que o TypeScript substituir o PropTypes. Caso contrário, o app fica sem checagem de props.

## 2. Class components × function components com hooks

### O que muda

| Aspecto | Hoje (classes) | Com hooks |
|---|---|---|
| Efeitos colaterais | Dividido em `componentDidMount`, `componentDidUpdate` e `componentWillUnmount`. Em `TrackingDashboard`, o `setInterval` nasce em `componentDidMount` (`TrackingDashboard.tsx:38-44`) e é limpo em `componentWillUnmount` (`:46-50`), que lê o id do timer de `this.state` | Um `useEffect` com função de limpeza, no mesmo bloco |
| Valores que não geram render (id de timer, instância do mapa) | Guardados em `this.state` (`TrackingDashboard.tsx:33`) ou em campos da classe (`MapView.tsx:12-15`) | `useRef`. Guardar o id do timer em `state` provoca render sem necessidade |
| Reuso de lógica | Não há hoje. Em classes, o reuso exige HOC ou render prop | Custom hooks: `useEntregas`, `usePollingEntrega`, `useLeafletMap` |
| Tipagem | `React.Component<any, any>`, com `this` e binds | `function X(props: Props)`, sem `this` |
| Testes | Possíveis, mas mais verbosos | Testing Library trabalha bem com função e hook |
| Ecossistema | Bibliotecas novas assumem função | Documentação atual recomenda função para código novo |

### O que não ganhamos

- **Desempenho:** a migração não muda o número de renders por si só.
- **Correção dos bugs listados:** R1 (XSS nos popups), R2 (polling que não atualiza) e R3 (filtros que não funcionam) existem independentemente de classe ou função. Migrar sem corrigir esses itens leva os bugs para hooks.

### Recomendação

Migrar, mas **depois** de existirem testes (fase 0) e tipos (fase 3), e **um componente por vez**, começando pelo que tem menos efeitos colaterais. Ordem sugerida: `DeliveryList` (sem timer nem mapa), `TrackingDashboard` (timer), `MapView` (Leaflet, o mais delicado), `App` (carregamento e estado global da tela).

## 3. PropTypes × TypeScript: riscos

### Riscos de manter PropTypes

1. **Só valem em desenvolvimento.** A checagem de `prop-types` é desligada em build de produção. Um bug de contrato chega ao usuário sem aviso.
2. **Não tipam o contrato da API.** Os dados de `/api/entregas` entram no estado como `any`. PropTypes checa só a forma das props, não o que o `fetch` trouxe.
3. **Cobertura incompleta, e mentirosa em alguns pontos:**
   - `App` declara `propTypes = {}` vazio (`App.tsx:125-128`).
   - `TrackingDashboard` declara `delivery: PropTypes.object`, sem forma (`TrackingDashboard.tsx:163-165`).
   - `DeliveryList` declara `onFilterChange: PropTypes.func` (`DeliveryList.tsx:102`), mas nunca chama essa prop (ver R3).
4. **Somem no React 19** (seção 1). Sem TypeScript no lugar, a checagem se perde.

### Riscos do TypeScript atual (que não está ligado)

1. **Checagem desligada.** Com `strict: false`, `noImplicitAny: false` e `any` espalhado, o compilador quase não verifica nada no app.
2. **Medição:** com `--strict` ligado no `tsconfig.app.json`, `tsc` acusa **3 erros, todos em `MapView.tsx`** (linhas `222`, `226` e `258`). A causa é `const allPoints = []` (`MapView.tsx:221`), que o TypeScript infere como `never[]`. O restante do app compila sem erro, mas isso só vale porque o `any` já esconde a maior parte do código.
3. **Tipos errados dão falsa segurança.** `libs/shared-types` não bate com o backend:
   - `Entrega.status` é minúsculo (`'pendente' | 'em_transito' | ...`). O backend e o `STATUS_CONFIG` usam maiúsculo (`PENDENTE`, `EM_TRANSITO`).
   - `Entrega` usa `origin_lat`, `driver_name`, `vehicle_plate`. O backend retorna `origem_lat`, `motorista_nome`, `veiculo_placa`.
   - Usar esses tipos sem corrigi-los faria o compilador aceitar código que quebra em runtime.
4. **Runtime não é coberto pelo TypeScript.** Campos como latitude podem chegar como string ou como número, e `rastreamentos` pode vir ausente. Isso precisa de normalização na borda do `fetch`, num único lugar (pergunta Q3).

### Recomendação

Ligar `strict` no `tsconfig.app.json` (fase 3), corrigir os 3 erros de `MapView`, criar tipos a partir do contrato real do backend e normalizar os dados na entrada. Remover os `propTypes` de cada componente conforme ele for migrado para função (fase 4). Não migrar tipos antes de confirmar o contrato.

## Problemas identificados

Achados de comportamento e de segurança obtidos por leitura. Os números de linha são os do arquivo atual.

| # | Problema | Evidência | Risco |
|---|---|---|---|
| R1 | **HTML injetado nos popups do Leaflet.** Textos são concatenados em `bindPopup`, que grava como HTML | `MapView.tsx:141-145` (`origem_endereco`), `:154-158` (`destino_endereco`, `motorista_nome`), `:208-213` (`pos.descricao`). `descricao` vem de `POST /api/rastreamento/:entregaId` (`rotalog-api-entregas/src/routes/rastreamento.js:40-66`), que não tem autenticação (dívida 1 da ADR 0001 do `api-entregas`) | **Alto.** Evento forjado com HTML ou script é renderizado no mapa de todo visitante. Não foi executado, mas o caminho está no código |
| R2 | **Polling que não atualiza nada.** A cada 5 s o `refreshData` busca a entrega, mas descarta o resultado. Só grava `lastUpdate`, que nem é renderizado | `TrackingDashboard.tsx:38-44` (intervalo), `:52-64` (`refreshData`), `:59` (`lastUpdate`), `:60` (TODO "Atualizar Redux store") | **Médio.** O status mostrado não muda sem recarregar a página. Há requisições sem efeito |
| R3 | **Filtros não funcionam.** `handleFilterChange` existe, é passado para `DeliveryList`, mas nunca é chamado. O estado `filters` não é lido por ninguém | `App.tsx:17-21` (estado), `:67-74` (handler), `:102` (passagem), `DeliveryList.tsx:102` (prop declarada, nunca usada) | **Médio.** Funcionalidade aparente que não existe |
| R4 | **Carregamento e erro apagam a tela inteira.** `render` retorna só o texto de loading ou de erro, desmontando lista, detalhes e mapa. O erro não tem botão de tentar de novo | `App.tsx:81-87` | **Médio.** Uma falha da API deixa o usuário sem saída, e o mapa é recriado a cada recarga |
| R5 | **Efeito colateral em `render`.** O `sort` muta o array `rastreamentos` que vem do estado de `App` | `TrackingDashboard.tsx:140-141` | **Baixo.** Com `StrictMode` o `render` roda duas vezes em dev, então o efeito aparece em dobro |
| R6 | **Sem `ErrorBoundary`, e campos nulos quebram a renderização.** `a.status.localeCompare` e `event.evento.replace` lançam exceção se o campo for `null`. Sem limite de erro, uma exceção derruba a árvore inteira | `DeliveryList.tsx:37`, `TrackingDashboard.tsx:150` | **Baixo a médio** (depende de o backend devolver `null`). A confirmar |
| R7 | **Leaflet por CDN, com variável global.** `declare const L: any` e `<script>` dinâmico, apesar de `leaflet` estar em `dependencies` | `MapView.tsx:8`, `:45-64`; `package.json` (`leaflet`) | **Médio.** Tipagem perdida, carregamento assíncrono e dependência de rede para o JS e o CSS (`index.html:10`) |
| R8 | **Dependência declarada e não usada.** `react-leaflet` | `package.json` (dependencies); nenhum `import` | **Baixo.** Ruído, e uma incompatibilidade futura com React 19 |
| R9 | **Nenhum teste.** O projeto `rastreamento` não tem target `test` nem arquivo `.spec`. Os dois `.spec` do workspace são do `painel-admin` | `project.json` do app; `find` em `apps/` | **Alto para a migração.** Sem testes, qualquer refatoração é às cegas |
| R10 | **Tipagem e PropTypes incompletos.** `any` em todo lado, `strict` desligado, 3 erros sob `--strict` em `MapView.tsx`, `App` com `propTypes` vazio, `delivery: object` sem forma | `tsconfig.app.json:7`; `App.tsx:125-128`; `TrackingDashboard.tsx:163-165` | **Médio.** Ver seção 3 |
| R11 | **`shared-types` divergente do backend.** Nome de campo, caixa do status e formato não batem | `libs/shared-types/src/index.ts` (interface `Entrega`); `rotalog-api-entregas/src/routes/entregas.js` | **Médio.** Quem usar os tipos sem corrigir ganha falsa segurança. O app hoje nem os importa |
| R12 | **Redux citado e inexistente.** Comentários falam em Redux, mas não há Redux no `package.json` | `App.tsx:24`, `:38`; `main.tsx:136` | **Baixo.** Confunde quem lê. O `CLAUDE.md` já alerta para isso |
| R13 | **Duplicação de config de status.** `STATUS_CONFIG` aparece em `DeliveryList.tsx:4` e `TrackingDashboard.tsx:6`, com FIXME que pede lib compartilhada | `DeliveryList.tsx:1-10`, `TrackingDashboard.tsx:6-14` | **Baixo.** Mudança de status precisa ser feita em dois lugares |

Dívidas que o `CLAUDE.md` já registra e que **não** entram nesta ADR: URLs de API fixas em `localhost` (`App.tsx:40`, `TrackingDashboard.tsx:56`), CSS global (`styles.css`) e componentes grandes.

## Ranking das modernizações por esforço × benefício

A ordem combina esforço e benefício, e respeita dependências. Itens de esforço muito baixo com benefício médio ou alto vêm primeiro. Itens que dependem de testes ou tipos vêm depois.

| Ordem | Id | Modernização | Esforço | Benefício | Depende de |
|---|---|---|---|---|---|
| 1 | M1 | Remover `react-leaflet` não usado (R8) | Muito baixo | Baixo: limpa `dependencies` e tira um bloqueio de versão do React 19 | — |
| 2 | M2 | Escapar o texto dos popups do Leaflet, ou montar o conteúdo com `textContent` (R1) | Muito baixo | Alto: fecha XSS de dados que chegam sem autenticação | — |
| 3 | M3 | Remover o `sort` do `render` e tratar campos `null` (R5, R6) | Muito baixo | Baixo: evita crash e efeito duplicado | — |
| 4 | M4 | Fazer o polling atualizar a entrega selecionada, com o resultado sobrescrevendo o estado (R2) | Baixo | Médio-alto: status fica certo sem recarregar a página | — |
| 5 | M5 | Testes de caracterização de `App`, `DeliveryList`, `TrackingDashboard` e `MapView` (R9) | Médio | Alto: rede de segurança para toda a migração | Q1 |
| 6 | M6 | Trocar o Leaflet por CDN pelo pacote `leaflet` já declarado (R7) | Baixo | Médio: tipagem real, sem global e sem carregamento assíncrono | M5 |
| 7 | M7 | Decidir sobre filtros: implementar ou remover o código morto (R3) | Baixo | Médio: some uma funcionalidade falsa | Q2 |
| 8 | M8 | Ligar `strict` no `tsconfig.app.json`, corrigir os 3 erros de `MapView` e tipar o estado (R10) | Médio | Alto: o compilador passa a checar o app | M5 |
| 9 | M9 | Alinhar `libs/shared-types` ao contrato real e normalizar a entrada do `fetch` (R11, seção 3 item 4) | Médio | Alto: tipo que bate com o backend | Q3, M8 |
| 10 | M10 | `ErrorBoundary` e tela de erro com botão de tentar de novo, sem apagar a lista (R4, R6) | Baixo | Médio: a falha vira mensagem, não tela branca | — |
| 11 | M11 | Migrar `DeliveryList` para função com hooks, com tipos e sem `propTypes`. Extrair `STATUS_CONFIG` (R13) | Médio | Médio: primeiro componente, valida o padrão | M5, M8 |
| 12 | M12 | Migrar `TrackingDashboard` para função, com `useEffect` para o polling | Médio | Médio-alto: timer no lugar certo, sem `state` para o id | M4, M11 |
| 13 | M13 | Migrar `MapView` para função, com `useRef` e `useEffect` para o Leaflet | Médio-alto | Alto: o componente mais delicado, com ciclo de vida do mapa | M5, M6, M11 |
| 14 | M14 | Migrar `App` para função, com hooks de estado e carregamento | Médio | Médio: fecha a migração | M11, M12, M13 |
| 15 | M15 | Atualizar React 18 para a 19.x, com Nx e tipos no que for necessário (seção 1) | Médio-alto | Médio: suporte e ecossistema atual. Benefício de segurança só se R10 estiver feito | M8, M14, Q4 |

Observações:

- **M5 vem antes de tudo que muda comportamento ou estrutura.** Sem teste, a migração de `MapView` é a parte com maior risco de regressão.
- **M1 a M4 não dependem de nada** e podem ser feitos em PRs separados, cada um com seu teste de caracterização.
- **M15 vem por último.** Fazer a atualização depois da migração evita passar por ela com o código de classe, e depois que o TypeScript já cobre o que o PropTypes cobria.

## Decisão

Adotar a modernização em fases, na ordem abaixo. Cada fase é implantável sozinha, e cada item do ranking vira um commit ou PR próprio.

### 1. Ordem de execução

| Fase | Conteúdo | Depende de |
|---|---|---|
| 0 | **Rede de segurança.** M5: testes de caracterização com Jest (já no workspace) e Testing Library (Q1). Adicionar o target `test` no `project.json` do app. Nenhum comportamento muda. | Q1 |
| 1 | **Limpeza sem mudança visível.** M1, M3. | Fase 0 |
| 2 | **Segurança e comportamento.** M2 (XSS), M4 (polling), M7 (filtros, conforme Q2), M10 (erro). Cada um com teste novo. | Fase 0; Q2 |
| 3 | **TypeScript.** M6 (Leaflet via npm), M8 (strict) e M9 (contrato). PropTypes continuam até a migração de cada componente. | Fase 0; Q3 |
| 4 | **Hooks.** M11, M12, M13, M14, um PR por componente, na ordem indicada. Cada PR remove o `propTypes` do componente que migrou. | Fases 0 e 3 |
| 5 | **Framework.** M15: atualização para React 19.x, com a verificação de compatibilidade do Nx e dos tipos. Coordenada com o painel-admin. | Fase 4; Q4 |

### 2. Forma alvo de um componente (fase 4)

```tsx
type TrackingDashboardProps = {
  delivery: Entrega | null;
  onAtualizar: (entregaId: number) => Promise<Entrega>;
};

export function TrackingDashboard({ delivery, onAtualizar }: TrackingDashboardProps) {
  const [expanded, setExpanded] = useState(true);

  useEffect(() => {
    if (!delivery) return;
    const id = setInterval(() => onAtualizar(delivery.id), 5000);
    return () => clearInterval(id);
  }, [delivery?.id, onAtualizar]);

  if (!delivery) {
    return <div className="dashboard-empty">Selecione uma entrega para ver detalhes</div>;
  }
  // ...
}
```

- O `setInterval` vive num único `useEffect` com limpeza. O id do timer não vai para estado.
- `onAtualizar` é uma função que a `App` passa para atualizar a entrega selecionada (correção de R2). O componente não busca dados sozinho.
- Nomes de arquivo e de pasta não mudam, para não espalhar a mudança.

### 3. Tipos e entrada de dados (fase 3)

- `strict: true` em `apps/rastreamento/tsconfig.app.json`, herdado do `tsconfig.base.json`.
- Um módulo de entrada (`api.ts`) faz o `fetch`, normaliza latitude e longitude para número e garante `rastreamentos` como array. Os componentes recebem só o tipo normalizado.
- `libs/shared-types` é corrigido para o contrato real (Q3), ou o tipo fica local ao app, a decidir na execução.

### 4. Regras do projeto afetadas

Esta ADR muda pontos documentados, e a atualização desses documentos faz parte da decisão:

- `CLAUDE.md`, seção `rotalog-frontend`, "rastreamento com class components React e `PropTypes` no lugar de tipos TypeScript": passa a dizer "function components com hooks e TypeScript", depois da fase 4.
- `.claude/agents/frontend-rotalog.md`, seção "rastreamento (React)": "Class components com `PropTypes` (dívida conhecida)" deixa de existir na fase 4. A regra "Ao criar componente novo, seguir o padrão do arquivo vizinho, a menos que o pedido seja migrar" passa a apontar para function components.
- `.claude/agents/frontend-rotalog.md`, seção "Stack": "React ~18.3 com class components e `PropTypes`" passa a dizer a versão e o padrão reais depois da fase 5.
- `.claude/agents/frontend-rotalog.md`, seção "Regras de trabalho": "Não corrigir dívida técnica proativamente" continua valendo para tudo que não esteja nesta ADR. Os itens listados aqui são o pedido explícito.

Até a aprovação desta ADR, nenhuma dessas regras muda. A convenção atual diz que componente novo segue o vizinho, e nenhum arquivo do app é migrado sem pedido.

## Mudanças de comportamento

Visíveis para quem usa o portal:

- Texto de endereço, motorista e descrição de evento aparece como texto puro no mapa. HTML em `descricao` deixa de ser interpretado (M2).
- A entrega selecionada passa a refletir mudanças de status a cada 5 s, sem recarregar a página (M4).
- Se a API falhar, o portal mostra mensagem com botão de tentar de novo, e a lista continua visível quando possível (M10).
- Se os filtros forem removidos (Q2), a interface perde o que hoje não funciona. Se forem implementados, aparece filtro novo.
- Erros de renderização deixam de derrubar a página inteira (M10).

Visíveis para quem mantém o código:

- Após a fase 5, `propTypes` deixam de ser checados. A checagem passa a ser feita pelo TypeScript, que só vale depois da fase 3.

## Alternativas consideradas

- **Não migrar, só corrigir os bugs.** Viável, porque o app funciona. Descartada como solução final: a convenção do projeto, a documentação atual e o custo de manter `any` e `PropTypes` pesam mais do que o custo da migração, que é pequena (4 componentes).
- **Migrar tudo de uma vez.** Descartada. O `MapView` tem ciclo de vida imperativo, e um PR grande dificulta achar regressão.
- **Trocar Leaflet por `react-leaflet` agora.** Adiada. A versão 4 é para React 18, e a 5 exige React 19. Adotar agora obrigaria a atualização antes da hora. Reavaliar na fase 5.
- **Manter PropTypes e não usar TypeScript.** Descartada. PropTypes não checa produção, não tipa a API e some no React 19.
- **Redux Toolkit, como sugerem os TODOs.** Descartada. Há uma tela e um estado local. Não há Redux no `package.json`, e o `CLAUDE.md` pede não assumir store global.
- **TanStack Query para o `fetch`.** Adiada. Seria uma dependência nova para um app com uma tela. Reavaliar se o polling crescer.

## Dívidas registradas (fora do escopo desta ADR)

1. **`POST /api/rastreamento` sem autenticação** (`rotalog-api-entregas/src/routes/rastreamento.js`, `index.js:76`). Origem do R1. Corrigir o escape no front não resolve a origem dos dados. Precisa de ADR própria no `api-entregas`.
2. **CORS aberto com `*`** no `api-entregas` (`index.js:33`). Dívida 4 da ADR 0001 do `api-entregas`.
3. **URLs fixas em `localhost`** (`App.tsx:40`, `TrackingDashboard.tsx:56`). O `CLAUDE.md` proíbe mover sem pedido.
4. **Progresso fixo por status** (`TrackingDashboard.tsx:15-25`, FIXME "deveria usar dados reais de tracking GPS"). O backend já grava latitude e longitude nos eventos, então isso pode virar cálculo real numa ADR futura.
5. **Sem paginação** em `GET /api/entregas`. O app carrega tudo de uma vez.
6. **CSS global** em `styles.css` e componentes grandes. Dívida já listada no `CLAUDE.md`.

## Questões em aberto

- **Q1. Podemos adicionar `@testing-library/react` como devDependency?** O `jest-environment-jsdom` já está no workspace. **Recomendação:** sim. É a forma mais simples de testar componentes sem subir a API.
- **Q2. Filtros: implementar ou remover?** O `DeliveryList` tem ordenação funcionando, mas o filtro não existe. **Recomendação:** remover o código morto na fase 2 (`filters`, `handleFilterChange`, `onFilterChange`). Se o filtro for pedido de produto, abrir pedido próprio.
- **Q3. Qual é o contrato real de `GET /api/entregas`?** Antes da fase 3, confirmar pelo model Sequelize e pelo `include` de `rotalog-api-entregas/src/routes/entregas.js` (linhas 44-47 e 99-102): tipo de latitude e longitude (número ou string), nomes dos campos e se `rastreamentos` pode faltar. **Recomendação:** sim, confirmar com uma resposta real da API antes de escrever tipos.
- **Q4. Qual versão do React é o alvo?** A última 19.x estável no momento da execução, com a compatibilidade de `@nx/react` e `@types/react` verificada antes. **Recomendação:** a 19.x mais recente com suporte, confirmada na documentação oficial antes de começar.
- **Q5. Esta ADR autoriza a migração para function components e a remoção de `propTypes`?** Ambas são mudanças que a convenção atual proíbe sem pedido. **Recomendação:** sim, para os itens listados aqui. Nada fora desta lista muda.

## Como verificar

```bash
cd rotalog-frontend

# Build do app (saída em pasta temporária, depois removida)
npx nx build rastreamento

# TypeScript com strict, sem emitir arquivos (hoje: 3 erros em MapView.tsx)
npx tsc --noEmit -p apps/rastreamento/tsconfig.app.json --strict

# Class components restantes (hoje: 4)
grep -rln "extends React.Component" apps/rastreamento/src

# PropTypes restantes (hoje: em todos os componentes)
grep -rn "propTypes" apps/rastreamento/src

# Popups sem concatenação de HTML (após M2)
grep -n "bindPopup" apps/rastreamento/src/components/MapView.tsx

# Testes (hoje não existe target test; criado na fase 0)
npx nx test rastreamento
```

Critérios de aceite por fase:

- Fase 0: `npx nx test rastreamento` verde, com testes cobrindo lista, seleção, histórico, criação do mapa e polling.
- Fase 1: `propTypes` de `TrackingDashboard`, `DeliveryList` e `MapView` continuam ativos. Nenhum comportamento muda.
- Fase 2: um evento de rastreamento com `<img src=x onerror=...>` em `descricao` aparece como texto no popup. Polling atualiza o status de uma entrega em trânsito sem recarregar a página. Com a API derrubada, o portal mostra erro com botão de tentar de novo.
- Fase 3: `tsc --strict` sem erros. Nenhum `any` nos componentes migrados.
- Fase 4: `grep` de class components sem resultado. `propTypes` removidos de cada componente migrado.
- Fase 5: `npx nx build rastreamento` e `npx nx build painel-admin` sem erro após a atualização.

## Consequências

### Positivas

- Dados de evento deixam de ser interpretados como HTML no mapa, mesmo com `POST` aberto.
- O status mostrado passa a acompanhar a entrega, sem atualização manual.
- Erros de API e de renderização deixam de deixar a tela em branco.
- O compilador passa a checar o app, e os tipos passam a refletir o backend.
- O ciclo de vida do mapa e do timer fica num único lugar por componente.

### Negativas

- A convenção do projeto muda em pontos do `CLAUDE.md` e do agente `frontend-rotalog`, e isso precisa ser feito junto.
- A fase 0 é trabalho sem ganho visível ao usuário.
- Ligar `strict` pode revelar mais erros do que os 3 medidos, se os tipos forem escritos com precisão. Cada fase precisa de revisão separada.
- Atualizar para React 19 pode exigir atualizar o Nx, e isso afeta o `painel-admin` também.
- O XSS de R1 só é fechado no front. A origem (POST sem autenticação) continua aberta até a ADR do `api-entregas`.
