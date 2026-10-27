# ADR 0001: Saneamento do middleware de autenticação e separação de camadas em `entregas`

- **Status:** Proposta
- **Data:** 2026-10-14
- **Escopo:** `rotalog-api-entregas`: `src/middleware/auth.js`, `src/middleware/errorHandler.js`, `src/routes/entregas.js` e a montagem das rotas em `src/index.js`
- **Origem:** seção `rotalog-api-entregas` do `MAPA_DIVIDA_TECNICA.md` (itens de segurança e arquitetura) e análise estática do código em 2026-10-14. Nenhuma alteração de código foi feita nesta etapa.

## Contexto

Duas peças concentram os riscos mais altos do serviço.

O `auth.js` foi copiado de um exemplo da internet (o próprio cabeçalho diz "Copied from StackOverflow (2020)"). Ele contém um segredo literal de fallback, não valida token e, por padrão, deixa todas as rotas abertas.

O `routes/entregas.js` tem 365 linhas e mistura quatro camadas num arquivo só: HTTP, validação, SQL e regra de negócio. Há ainda 12 chamadas de `console`, 37 linhas com `FIXME`/`TODO` e um `require` não usado.

A análise abaixo é estática. O projeto não tem testes automatizados (`npm test` falha de propósito), então nenhum dos comportamentos citados foi executado.

### Problemas do middleware de autenticação

| # | Problema | Evidência | Risco |
|---|---|---|---|
| A1 | Segredo literal como fallback de `JWT_SECRET` | `auth.js:12` | Segredo previsível e versionado no repositório |
| A2 | `JWT_SECRET` é declarado mas nunca usado | `auth.js:12`; não há `jwt.verify`, e `jsonwebtoken` não está no `package.json` | A intenção de validar assinatura existe só no nome da variável |
| A3 | Token não é validado | `auth.js:36`: só checa presença e tamanho ≥ 10 caracteres | Qualquer string com 10 caracteres após `Bearer ` passa |
| A4 | Sem expiração, emissor nem audiência | `auth.js:40-42` (FIXMEs) | Token roubado vale para sempre |
| A5 | Fail-open por padrão | `auth.js:16`: bypass quando `NODE_ENV !== 'production'`, inclusive quando a variável não existe | Um deploy sem `NODE_ENV` fica aberto |
| A6 | Identidade fixa de administrador | `auth.js:46`: `req.user = { id: 1, role: 'admin' }` para todo request | Nenhuma identidade real. Nenhuma rota lê `req.user` hoje, mas qualquer controle futuro veria todos como admin |
| A7 | Log a cada request de autenticação | `auth.js:17` e `auth.js:43` | Ruído em produção, sem identificar quem chamou |
| A8 | Mensagens de erro distintas por causa da falha | `auth.js:24, 30, 37` | Facilita enumerar o motivo da recusa (menor risco, mas fácil de corrigir) |
| A9 | Procedência não verificada | Cabeçalho do arquivo | Código copiado sem revisão de licença nem de segurança |

### Problemas em `routes/entregas.js`

| # | Problema | Evidência | Risco |
|---|---|---|---|
| E1 | SQL bruto na rota | `entregas.js:67-77` (`/stats`); `sequelize` importado em `entregas.js:14` só para isso | Regra de dados na camada HTTP, schema `entregas` fixo no texto da consulta |
| E2 | Regra de negócio na rota | Número do pedido (`:153`), distância (`:159-161`), tempo estimado (`:165`), transição de status (`:245-260`), atribuição (`:304-309`), cancelamento (`:345`) | Impossível testar sem subir o Express e o banco |
| E3 | Validação inline e incompleta | `:148` (só dois campos), `:212-218` (sem tipo), `:237-248`, `:289-298` | Entrada inválida chega ao banco. Ex.: `peso_kg` como texto |
| E4 | Trilha de rastreamento repetida e sem transação | `:185`, `:265`, `:314`, `:349`; a entrega é salva antes do evento (`:182`, `:262`, `:311`) | Se o evento falhar, a entrega fica gravada sem histórico |
| E5 | Transição de status sem regra | `:250` (FIXME); `:252` aceita qualquer valor de `statusValidos`, inclusive `ENTREGUE` → `PENDENTE` | Histórico de entrega pode ser reescrito |
| E6 | Status `ATRIBUIDA` via `PATCH /status` sem veículo | `:245` aceita `ATRIBUIDA` sem passar por `/atribuir` | Entrega marcada como atribuída sem veículo nem motorista |
| E7 | Latitude e longitude `0` tratadas como ausentes | `:157` (`origem_lat && ...`) e `:171-174` (`origem_lat \|\| null`) | Coordenada válida (equador, meridiano de Greenwich) vira `null` ou deixa de gerar distância |
| E8 | Número do pedido com `Math.random` | `:153` | Colisão só é barrada pelo índice `UNIQUE` (`migration.sql:11`), e o resultado seria erro 500 |
| E9 | Estilos misturados | Callback em `:65`, `:120`, `:336`; `async/await` nas demais | Duas formas de tratar erro no mesmo arquivo |
| E10 | Erro inconsistente para o cliente | `:198` devolve `detalhes: error.message`; as demais rotas devolvem mensagem genérica | Vazamento de detalhe interno só no `POST` |
| E11 | Logs de sucesso com `console.log` | `:192`, `:272`, `:321` | Ruído em produção, e o `console.error` de cada rota repete o que `errorHandler` já registra |
| E12 | Erro é logado em cada rota e de novo no `errorHandler` | 9 `console.error` em `entregas.js`; `errorHandler.js:9-15` loga URL, body e stack | Mesmo erro aparece várias vezes e o body pode conter dados sensíveis |
| E13 | Comentários em excesso | 37 linhas com `FIXME`/`TODO`; JSDoc de quatro linhas repetido em cada rota; cabeçalho com 4 `FIXME` | Ruído que esconde o código real |
| E14 | Import não usado | `entregas.js:13` (`Op`) | Ruído |
| E15 | Formato de resposta sem padrão | `/stats` devolve `{ total, por_status, gerado_em }`; as demais devolvem a entidade pura | Contrato de resposta difícil de prever |

### Desalinhamento com a regra atual do projeto

A regra documentada em `CLAUDE.md` e em `.claude/agents/node-entregas.md` diz que "rotas acessam os models diretamente" e que "services fazem apenas integração com api-frotas e api-notificacoes". A separação proposta nesta ADR **altera essa regra**. Sem a alteração, não existe onde colocar a regra de negócio. Por isso a atualização dos dois documentos faz parte da decisão (ver seção 6).

## Decisão

### 1. Autenticação (`auth.js`)

1.1. **Remover o segredo literal.** `JWT_SECRET` passa a ser obrigatório quando a autenticação está ligada. Se estiver ausente, a aplicação não sobe (falha no startup, antes de `startServer()` em `index.js`).

1.2. **Validar o token de verdade.** Verificação de assinatura, expiração, `iss` e `aud`, com o algoritmo fixado (`algorithms: ['HS256']`) para impedir troca de algoritmo. Biblioteca: `jsonwebtoken`. **Nova dependência, a confirmar** (pergunta Q4).

1.3. **Trocar o bypass implícito por flag explícita.** A autenticação fica **ligada por padrão**. O bypass exige `AUTH_DISABLED=true` e só é aceito quando `NODE_ENV !== 'production'`. Ao subir com bypass, um aviso é registrado uma vez, no startup, e não por request.

1.4. **`req.user` vem dos claims do token** (`sub`, `role`). O usuário fixo é removido. Esta etapa só estabelece identidade. Controle de papéis (RBAC) fica para outra ADR.

1.5. **Mensagem única para qualquer falha de autenticação** (ausente, mal formatada, inválida ou expirada): `401 { error: 'Não autorizado' }`. O motivo real vai para o log, sem o token.

1.6. **Remover os logs por request** de `auth.js` (`:17` e `:43`). Só falhas de autenticação são registradas.

1.7. **`errorHandler.js`** deixa de devolver `stack` em qualquer ambiente e deixa de logar `req.body`. A stack continua no log do servidor, sem vazar ao cliente.

1.8. **Ação operacional (não automatizável por esta ADR):** rotacionar o `JWT_SECRET` que está no `.env` versionado e remover o `.env` do índice do git (`git rm --cached .env`). O `.env` aparece como rastreado (`git ls-files .env`).

> **Pré-requisito crítico:** não existe emissor de JWT em nenhum repositório do workspace (o painel-admin também não tem login, conforme o MAPA). Ligar a validação real em produção sem emissor bloqueia todos os clientes. Ver pergunta Q1.

### 2. Estrutura alvo de `entregas`

```
src/
  routes/entregas.js                  só mapeia método + caminho para o controller (e para a validação)
  controllers/entregasController.js   lê req, chama o service, escolhe o status HTTP
  services/entregaService.js          regras: número do pedido, distância, transições, trilha de rastreamento
  repositories/estatisticasRepository.js  SQL de /stats, em uma função nomeada
  validators/entregaValidator.js      validação de entrada, devolve EntradaInvalidaError
  utils/distancia.js                  cálculo puro (Haversine), sem dependência de banco
```

Uma rota passa a ter a forma:

```js
router.patch('/:id/status', validar(entregaValidator.status), asyncHandler(entregasController.atualizarStatus));
```

- Os models Sequelize continuam sendo usados diretamente pelo service. Não há repositório genérico, porque o projeto usa Active Record e a base é pequena.
- `asyncHandler` é um helper de 5 linhas em `middleware/`. Assim não é preciso adicionar `express-async-errors`.

### 3. Erros de domínio

Mesmo padrão adotado na ADR 0001 do `api-frotas` (exceções específicas com base comum), adaptado ao Node:

| Classe | Status HTTP | Quando |
|---|---|---|
| `EntregaNaoEncontradaError` | 404 | Entrega não existe |
| `EntradaInvalidaError` | 400 | Campo ausente, tipo errado ou fora da faixa |
| `TransicaoStatusInvalidaError` | 409 | Transição não permitida pela máquina de estados |

O `errorHandler` mapeia essas classes para o status. Qualquer outro erro vira 500 com mensagem genérica.

### 4. Máquina de estados de status (proposta a validar com o negócio)

| De | Para |
|---|---|
| `PENDENTE` | `ATRIBUIDA`, `CANCELADA` |
| `ATRIBUIDA` | `EM_TRANSITO`, `PENDENTE` (desatribuir), `CANCELADA` |
| `EM_TRANSITO` | `ENTREGUE`, `CANCELADA` |
| `ENTREGUE` | nenhuma |
| `CANCELADA` | nenhuma |

- `ATRIBUIDA` só é alcançada por `PATCH /:id/atribuir`, que já grava veículo e motorista.
- As transições ficam em uma constante no service, testada de forma isolada.

### 5. Trilha de rastreamento, transação e cálculos

5.1. Cada alteração de entrega e o evento de rastreamento correspondente são gravados na **mesma transação** (`sequelize.transaction`). O evento passa a ser uma função do service, `registrarEvento(transaction, entregaId, evento, descricao)`.

5.2. **Latitude e longitude:** verificação por `!== undefined && !== null`, nunca por valor truthy.

5.3. **Distância:** Haversine em `utils/distancia.js`. O tempo estimado (`2 min/km`) vira constante nomeada.

5.4. **Número do pedido:** o prefixo `PED-` e o formato são preservados, porque o rastreamento público busca por esse valor. O sufixo passa a usar `crypto.randomBytes`. A unicidade continua garantida pelo índice `UNIQUE` (`migration.sql:11`), e a colisão rara passa a ser repetida pelo service.

### 6. Regras do projeto

Esta ADR altera dois pontos documentados e exige atualização simultânea:

- `CLAUDE.md`, seção `rotalog-api-entregas`: "routes (acessam os models diretamente) e services (apenas integração...)" passa a dizer que a camada de negócio fica em `services/` e as rotas só mapeiam.
- `.claude/agents/node-entregas.md`: a mesma regra, e também a instrução de `NODE_ENV=production` para testar auth, que deixa de ser necessária porque a auth passa a estar ligada por padrão.

As integrações (`frotasService.js`, `notificacaoService.js`) continuam em `services/`. A alternativa de movê-las para `integrations/` foi descartada por ser churn sem ganho imediato.

### 7. Comentários e logs

7.1. Remover do `routes/entregas.js` o JSDoc repetido, o cabeçalho com FIXME e os FIXME inline. Cada dívida que continuar válida vai para a tabela de "Dívidas registradas" desta ADR, para que nada se perca. **Condição:** o `CLAUDE.md` diz que FIXME/TODO são material do curso e não devem ser corrigidos proativamente. A remoção só ocorre com confirmação (pergunta Q2).

7.2. Remover os três `console.log` de sucesso (`:192`, `:272`, `:321`). A rota não loga. Quem loga erro é o `errorHandler`, uma única vez.

7.3. Não introduzir logger novo nesta etapa. O `CLAUDE.md` pede `console` até que haja pedido explícito. Um logger estruturado fica para outra ADR.

7.4. Remover o import `Op` não usado (`:13`).

## Mudanças de comportamento

Estas mudanças alteram o contrato e precisam ser comunicadas a quem consome a API:

- Token inválido ou expirado passa a dar `401`. Hoje é aceito.
- Sem `NODE_ENV` definido, a autenticação passa a estar ligada. Hoje o bypass fica ativo.
- `req.user` passa a ter a forma dos claims do token, e não mais `{ id: 1, role: 'admin' }`.
- `PATCH /:id/status` não aceita mais `ATRIBUIDA` nem transições fora da tabela. Passa a dar `409`.
- Campos com tipo ou faixa inválidos passam a dar `400`. Hoje entram no banco.
- Latitude e longitude `0` passam a ser gravadas e usadas no cálculo de distância.
- `POST /api/entregas` deixa de devolver `detalhes` no `500`.
- `numero_pedido` mantém o formato, mas com sufixo diferente. Consumidores que parsearem o sufixo devem ser verificados.

## Plano de migração

Cada fase é implantável sozinha e deve manter os testes verdes.

| Fase | Conteúdo | Depende de |
|---|---|---|
| 0 | **Testes de caracterização** dos 18 endpoints, com `node --test` e `fetch` nativo (sem dependência nova), contra o Postgres do workspace. Fixam o comportamento atual antes de qualquer mudança. | Nada |
| 1 | **Autenticação e erros**: itens 1.1 a 1.8 | Q1, Q4 |
| 2 | **Extração de camadas sem mudar comportamento**: controllers, service, repositório de estatísticas, `asyncHandler`. Callbacks viram `async/await`. | Fase 0 |
| 3 | **Correções de comportamento**: máquina de estados, validação, `lat/lng = 0`, transação, número do pedido. Um commit por correção, com as mudanças listadas acima. | Q3 |
| 4 | **Limpeza de comentários e logs** (seção 7) | Q2 |
| 5 | Fora desta ADR: logger, RS256, RBAC, Swagger | ADRs próprias |

## Alternativas consideradas

- **Manter o arquivo único e só limpar comentários e logs.** Descartada: resolve o ruído, mas não tira SQL nem regra da rota, e não permite testar a regra isoladamente.
- **Adotar `express-validator` ou `Joi` agora.** Adiada: adiciona dependências. A validação com funções simples atende ao tamanho atual. Reavaliar quando houver mais de um serviço validando entrada.
- **Adotar `express-async-errors`.** Descartada por ora, pelo mesmo motivo. O `asyncHandler` de 5 linhas resolve.
- **RS256 com chave pública já na fase 1.** Adiada. Com um único validador, HS256 com segredo de ambiente é suficiente. Passa a ser recomendado quando mais de um serviço validar token, porque aí a chave pública evita compartilhar o segredo.
- **Remover a autenticação do `api-entregas` e delegar tudo ao gateway.** Descartada nesta ADR. Não há gateway no workspace.
- **Manter o bypass e apenas trocar o segredo.** Descartada: o fail-open (A5) é o problema de maior risco do arquivo.

## Dívidas registradas (fora do escopo desta ADR)

1. **`/api/rastreamento` sem autenticação**, incluindo `POST` que grava eventos (`index.js:76`, `rastreamento.js`).
2. **Filtro `motorista` do frontend não casa com `motorista_id` da rota** (`entregas.js:37-39`). É contrato entre frontend e API, não é bug desta camada.
3. **Proxy `/api/frotas` não propaga identidade**, e o `api-frotas` não tem autenticação. A proteção do proxy não protege o acesso direto à porta 8080.
4. **CORS aberto com `*`** em `index.js`, inclusive com `Authorization` liberado.
5. **Sem paginação** em `GET /api/entregas` (`entregas.js:41`) e `limit: 5` fixo em `:47`.
6. **`sequelize.sync({ alter: false })` no startup** em `index.js`, com comentário dizendo que é inseguro em produção.
7. **`/api/health` com dependências fixas em `UNKNOWN`** em `index.js`.
8. **Integração de notificação não usada** (`notificacaoService.js` sem chamadas).
9. **`PATCH /:id/atribuir` não verifica** se veículo e motorista existem no `api-frotas`.

## Questões em aberto

- **Q1. Quem emite o JWT?** Sem emissor, a fase 1 bloqueia todos os clientes em produção. Opções: (a) criar login em um serviço (qual?), (b) aceitar token de um provedor externo, (c) manter a validação atrás de flag até existir emissor. **Recomendação:** (c) como etapa intermediária, com ADR própria para a emissão.
- **Q2. Podemos remover FIXME/TODO do código?** O `CLAUDE.md` os trata como material do curso. **Recomendação:** remover de `routes/entregas.js` só após confirmação, mantendo a lista nesta ADR.
- **Q3. As transições da seção 4 estão corretas?** Precisam de aprovação do negócio antes da fase 3.
- **Q4. Podemos adicionar `jsonwebtoken` como dependência?** A alternativa sem dependência é implementar HMAC com `crypto`, o que não é recomendado para validação de token.
- **Q5. O sufixo do número do pedido é consumido em algum lugar?** Verificar o frontend e os testes do rastreamento público antes da fase 3.

## Como verificar

```bash
cd rotalog-api-entregas

# Hoje: o script test falha de propósito (esperado até a fase 0)
npm test

# Após a fase 0
node --test

# Achados de log e de SQL que devem sumir da rota
grep -n "console\." src/routes/entregas.js
grep -n "sequelize.query" src/routes/entregas.js

# Segredo literal removido
grep -rn "super-secret" src

# Bypass só com flag explícita
NODE_ENV=production AUTH_DISABLED=true node src/index.js   # deve recusar o bypass
```

Critérios de aceite por fase:

- Fase 0: 18 endpoints cobertos por teste de caracterização, todos verdes antes de qualquer mudança.
- Fase 1: `Authorization: Bearer <token forjado>` retorna `401`; sem `JWT_SECRET`, o processo não sobe com a auth ligada.
- Fase 2: `src/routes/entregas.js` sem `require` de `models`, `sequelize` nem `console`.
- Fase 3: `PATCH /status` de `ENTREGUE` para `PENDENTE` retorna `409`; `lat = 0` é gravado.

## Consequências

### Positivas

- Autenticação deixa de ser nominal: token forjado ou expirado é recusado.
- Ausência de configuração deixa de abrir o serviço.
- Regra de negócio fica testável sem Express nem banco.
- Histórico de entrega não pode mais ser reescrito por transição inválida.
- Evento de rastreamento e alteração da entrega ficam atômicos.

### Negativas

- Mais arquivos para navegar: cerca de cinco novos.
- A regra de camadas do projeto muda, e isso precisa ser refletido em `CLAUDE.md` e no agente `node-entregas`.
- Clientes que hoje batem sem token passam a receber `401` em produção. Sem emissor (Q1), isso é um corte.
- Sem testes hoje, a fase 0 é trabalho antes do primeiro ganho visível.
- O sufixo do número do pedido muda. Consumidores que dependem do formato exato precisam ser checados (Q5).
