# ADR 0003: Alinhamento de `shared-types` e `api-contracts` com os contratos reais dos backends

- **Status:** Proposta
- **Data:** 2026-10-14
- **Escopo:** `rotalog-frontend/libs/shared-types/src/index.ts` e `rotalog-frontend/libs/api-contracts/src/openapi.yaml`, comparados com `rotalog-api-frotas`, `rotalog-api-entregas` e `rotalog-api-notificacoes`. Também a cópia paralela de tipos em `apps/painel-admin/src/app/models/index.ts`, que é o principal consumidor.
- **Origem:** análise estática em 2026-10-14: leitura de controllers, rotas, models, entidades, DTOs e serviços dos três backends, e grep de uso das libs no frontend. Nenhum código foi alterado e nenhuma requisição HTTP foi executada. Os formatos JSON são **inferidos** do código (Jackson, Sequelize com driver `pg` e System.Text.Json) e precisam ser confirmados com respostas reais (Q1).

## Contexto

As duas libs deveriam descrever o contrato da API, mas nenhuma delas é fonte confiável hoje.

- `libs/shared-types` **não é importado por nenhum app**. Confirmado com grep em `apps/` e `libs/`. Ou seja, os erros dela não aparecem em build nem em runtime. Eles aparecem quando alguém começar a usá-la.
- `libs/api-contracts` cobre 3 caminhos. Um deles (`/api/rastreamento/real-time`) não existe em nenhum backend.
- Os comentários `TODO` da lib estão, em parte, errados. Exemplo: `Veiculo.dataCadastro` tem TODO dizendo que o backend devolve `data_cadastro`. O Jackson serializa `dataCadastro` (camelCase é o padrão do Spring). `data_cadastro` é só o nome da coluna no banco. Por isso este ADR não usa os TODOs como fonte.

### Inventário real dos backends

| Serviço | Base | Endpoints de negócio | Health | Autenticação |
|---|---|---|---|---|
| `api-frotas` (Java/Spring) | `http://localhost:8080/api` (`server.servlet.context-path=/api`) | 28 | `/api/health` | nenhuma |
| `api-entregas` (Node/Express) | `http://localhost:3000/api` | 17 (9 de entregas, 4 de rastreamento, 4 de proxy para frotas) | `/api/health` | `authMiddleware` em `/api/entregas` e `/api/frotas`. `/api/rastreamento` sem auth. Bypass fora de produção (ADR 0001 de entregas, A5) |
| `api-notificacoes` (.NET 6) | `http://localhost:5000/api` | 7 | `/api/health` | nenhuma |

O `CLAUDE.md` diz que notificações tem 8 endpoints de negócio. O código tem 7, mais o health.

### Convenções reais de serialização

- **`api-frotas`:** Jackson, propriedades em camelCase (`anoFabricacao`, `dataCadastro`). Os controllers devolvem as **entidades JPA diretamente**, sem DTO de resposta.
- **`api-entregas`:** colunas do Sequelize, em snake_case (`numero_pedido`, `motorista_nome`). Listagem e detalhe incluem `rastreamentos`.
- **`api-notificacoes`:** System.Text.Json, em camelCase (`dataCriacao`, `erroMensagem`).
- **Datas** são strings ISO 8601 nos três. **Nulos** vêm como `null`.
- **`BIGINT` e `DECIMAL`** no Node: o driver `pg` devolve esses tipos como string por padrão. `database.js` não configura parser, então `id`, latitude, longitude, distância e peso chegam como string. O mesmo vale para `COUNT(*)` e `AVG(...)` em `/api/entregas/stats`. **Provável, a confirmar (Q1).**

## Análise

### 1. `shared-types` × contratos reais

#### 1.1 `Entrega` × `api-entregas` (`models/Entrega.js`, `routes/entregas.js`)

| `shared-types` | Backend real | Divergência |
|---|---|---|
| `id: number` | `id` BIGINT | Tipo provável: string em runtime (Q1) |
| `numero_pedido: string` | `numero_pedido` | Conforme |
| `status: 'pendente' \| 'em_transito' \| 'entregue' \| 'cancelada'` | `'PENDENTE' \| 'ATRIBUIDA' \| 'EM_TRANSITO' \| 'ENTREGUE' \| 'CANCELADA'` | Caixa diferente, e falta `ATRIBUIDA` |
| `driver_name` | `motorista_nome` | Nome diferente |
| `vehicle_plate` | `veiculo_placa` | Nome diferente |
| `origin_address` | `origem_endereco` | Nome diferente |
| `destination_address` | `destino_endereco` | Nome diferente |
| `origin_lat`, `origin_lng`, `destination_lat`, `destination_lng: number` | `origem_lat`, `origem_lng`, `destino_lat`, `destino_lng` DECIMAL(10,7), anuláveis | Nome diferente. O backend aceita `null`, o tipo não. Provável string |
| `distance_km: number` | `distancia_km` DECIMAL(10,2), anulável | Nome. Anulável. Provável string |
| `estimated_time_minutes: number` | `tempo_estimado_minutos` INTEGER, anulável | Nome. Anulável |
| `progress_percentage: number` | **Não existe.** O cálculo está no front (`TrackingDashboard.tsx:16-25`) | Campo inexistente |
| `events?: EntregaEvento[]` | `rastreamentos: Rastreamento[]` | Nome diferente |

Campos do backend ausentes no tipo: `motorista_id`, `veiculo_modelo`, `peso_kg`, `observacoes`, `data_criacao`, `data_atualizacao`, `data_coleta`, `data_entrega`.

`EntregaEvento` (`{ timestamp, description, status }`) não corresponde a `Rastreamento` (`{ id, entrega_id, latitude, longitude, evento, descricao, data_evento }`):

| `EntregaEvento` | `Rastreamento` | Divergência |
|---|---|---|
| `timestamp` | `data_evento` | Nome |
| `description` | `descricao` | Nome |
| `status` | `evento` | Nome e conteúdo. Não é status da entrega. Valores reais: `PEDIDO_CRIADO`, `STATUS_ALTERADO`, `ENTREGA_ATRIBUIDA`, `ENTREGA_CANCELADA`, `POSICAO_ATUALIZADA` |
| (ausentes) | `id`, `entrega_id`, `latitude`, `longitude` | Campo ausente |

#### 1.2 `Veiculo` × `api-frotas` (`domain/Veiculo.java`)

| `shared-types` | Backend real | Divergência |
|---|---|---|
| `id: number` | `id` Long | Conforme |
| `placa`, `modelo` | `placa`, `modelo` | Conforme |
| `ano: number` | `anoFabricacao` Integer | Nome |
| `dataCadastro: string` | `dataCadastro` | **Conforme.** O TODO está errado |
| `status: 'ativo' \| 'inativo' \| 'manutencao'` | `'ATIVO' \| 'INATIVO' \| 'MANUTENCAO'` | Caixa diferente |
| `quilometragem: number` | `quilometragem` Long | Conforme |
| `ultimaManutencao?: Date` | Não existe na entidade | Campo inexistente. `Date` vira string em runtime |
| (ausente) | `dataAtualizacao` | Campo ausente |

#### 1.3 `Motorista` × `api-frotas` (`domain/Motorista.java`)

| `shared-types` | Backend real | Divergência |
|---|---|---|
| `id`, `nome` | `id`, `nome` | Conforme |
| `email: string`, `telefone: string` | **Não existem** na entidade. O próprio FIXME da entidade diz que faltam | Campo inexistente |
| `numeroCnh: string` | `cnh` | Nome. O TODO diz `numero_cnh` e está errado |
| `dataVencimentoCnh: string` | `vencimentoCnh` (LocalDate, `yyyy-MM-dd`) | Nome |
| `veiculo_id: number` | **Não existe.** A entidade não tem vínculo com veículo | Campo inexistente |
| `ativo: boolean` | `status` `'ATIVO' \| 'INATIVO'` | Tipo diferente |
| (ausentes) | `categoriaCnh`, `status`, `dataCadastro`, `dataAtualizacao` | Campo ausente |

`FERIAS` aparece só num comentário da entidade. O service nunca grava esse valor.

#### 1.4 `Notificacao` × `api-notificacoes` (`DTOs/NotificacaoRequest.cs`, `NotificacaoResponse`)

| `shared-types` | Backend real | Divergência |
|---|---|---|
| `id: number` | `id` long | Conforme |
| `tipo: 'email' \| 'sms' \| 'webhook'` | `tipo` é o **evento de negócio** (ex.: `ENTREGA_ATRIBUIDA`). O canal fica em `canal` | Semântica trocada. `webhook` não existe |
| `destinatario`, `assunto?`, `mensagem` | `destinatario`, `assunto`, `mensagem` | Conforme |
| `enviado_em?: Date` | `dataEnvio` (anulável) | Nome, e tipo `Date` vira string |
| (ausentes) | `canal` (`'email' \| 'sms'`), `status` (`'PENDENTE' \| 'ENVIADO' \| 'FALHA'`), `tentativas`, `erroMensagem`, `servicoOrigem`, `referenciaId`, `dataCriacao` | Campo ausente |

#### 1.5 Itens do backend sem tipo, e tipos sem backend

- **`Manutencao`** existe no backend (`/api/manutencoes`) e não tem tipo na lib. Campos: `id`, `veiculoId`, `tipoManutencao`, `dataManutencao`, `quilometragemManutencao`, `custo` (number), `descricao`, `status` (`'PENDENTE' \| 'EM_ANDAMENTO' \| 'CONCLUIDA' \| 'CANCELADA'`), `dataCriacao`, `dataAtualizacao`.
- **`ApiResponse<T>`** (`{ data, message, status }`) **não é usado por nenhum backend**. Sucesso devolve a entidade pura, e erro devolve `{ erro }` ou `{ error }`.
- **`TemplateNotificacao`** e **`NotificacaoStats`** existem no backend e não têm tipo.
- **Resposta de `GET /api/rastreamento/pedido/{numeroPedido}`** tem forma própria, `{ pedido, status, origem, destino, data_criacao, data_entrega, eventos }`. Não tem tipo.

#### 1.6 `painel-admin` (cópia paralela de tipos, fora da lib)

`apps/painel-admin/src/app/models/index.ts` redefine `Veiculo`, `Motorista`, `Manutencao`, `Entrega`, `StatusVeiculo` e `ApiResponse`. Não importa `shared-types`. Os campos que os templates **realmente leem** divergem do backend:

| Uso no painel | Backend real | Efeito provável na tela |
|---|---|---|
| `entrega.codigo_rastreio` (`dashboard.component.ts:101`, `entregas.component.ts:71`) | `numero_pedido` | Coluna vazia |
| `veiculo.km_atual` | `quilometragem` | Campo vazio |
| `motorista.categoria_cnh`, `motorista.validade_cnh` (`motoristas.component.ts:54-56`) | `categoriaCnh`, `vencimentoCnh` | Campo vazio, e o alerta de CNH vencida nunca dispara |
| `motorista.cpf` | Não existe | Campo vazio |
| `veiculo.marca`, `veiculo.tipo`, `veiculo.ultima_manutencao` | Não existem | Campo vazio |
| `StatusVeiculo.MANUTENCAO = 'EM_MANUTENCAO'`, `BAIXADO` | `MANUTENCAO`. `BAIXADO` não existe | Filtro por status não casa |

Todos são **prováveis** pela leitura do código. Confirmar no navegador antes de corrigir.

### 2. `api-contracts` × backends (`openapi.yaml`)

| # | Achado | Evidência | Risco |
|---|---|---|---|
| C1 | `/api/rastreamento/real-time` não existe em nenhum backend. Está marcado como DESCONTINUADO | `openapi.yaml:61-69`. Grep nos quatro repositórios sem resultado | Documenta endpoint fantasma |
| C2 | Um único bloco `servers` com três hosts. Os caminhos não têm `servers` próprio, então `/api/entregas` herda o primeiro (8080, Java) | `openapi.yaml:8-14` | Cliente gerado aponta para a API errada |
| C3 | Enums de status em minúsculo (`ativo`, `pendente`, etc.) | `openapi.yaml:93, 119` | Mesmo problema da seção 1 |
| C4 | `VeiculoInput.ano` não existe. A API aceita `anoFabricacao` | `openapi.yaml:107`; `VeiculoRequest.java` | Campo enviado é ignorado em silêncio |
| C5 | `Entrega` tem `driver_name`, `destination_address`, `progress_percentage` (inexistentes). Não tem `origem_*`, `veiculo_placa`, `motorista_id`, `rastreamentos` | `openapi.yaml:110-130` | Mesmo problema da seção 1.1 |
| C6 | Nenhum schema de erro. Respostas 400, 401, 404 e 500 não estão documentadas | Todo o arquivo | O consumidor não sabe o formato do erro |
| C7 | Os TODOs citam `/api/manutencao` e `/api/motoristas/{id}/disponibilidade`. Os caminhos reais são `/api/manutencoes` e `/api/frotas/veiculos/{placa}/disponibilidade` (proxy do Node) | `openapi.yaml:71-74` | Leva a erro de caminho |
| C8 | Sem bloco `security`. A versão `1.0.0` está marcada no próprio comentário como defasada há 2 anos | `openapi.yaml:4-6` | Autenticação não documentada |

### 3. Endpoints não documentados

Legenda: **Doc.** = presente e correto no `openapi.yaml`. **Não doc.** = existe no backend e não está no `openapi.yaml`.

#### 3.1 `api-frotas` (28 de negócio + health)

Documentado corretamente: 2 (`GET /veiculos`, `POST /veiculos`). **Não documentados: 26 de negócio + health.**

- **Veículos (8 não documentados):** `GET /veiculos/{id}`, `GET /veiculos/placa/{placa}`, `GET /veiculos/status/{status}`, `PUT /veiculos/{id}`, `PATCH /veiculos/{id}/quilometragem`, `PATCH /veiculos/{id}/desativar`, `PATCH /veiculos/{id}/reativar`, `GET /veiculos/estatisticas` (devolve `String`, sem tipo).
- **Motoristas (9):** `GET /motoristas`, `GET /motoristas/{id}`, `GET /motoristas/cnh/{cnh}`, `GET /motoristas/status/{status}`, `GET /motoristas/cnh-vencida`, `POST /motoristas`, `PUT /motoristas/{id}`, `PATCH /motoristas/{id}/desativar`, `PATCH /motoristas/{id}/reativar`.
- **Manutenções (9):** `GET /manutencoes`, `GET /manutencoes/{id}`, `GET /manutencoes/veiculo/{veiculoId}`, `GET /manutencoes/pendentes`, `GET /manutencoes/veiculo/{veiculoId}/ultima`, `POST /manutencoes`, `PATCH /manutencoes/{id}/iniciar`, `PATCH /manutencoes/{id}/concluir`, `PATCH /manutencoes/{id}/cancelar`.
- **Health:** `GET /api/health` (dependências `api-entregas` e `api-notificacoes`).

#### 3.2 `api-entregas` (17 de negócio + health)

Documentado corretamente: 1 (`GET /api/entregas`). Além disso, há 1 fantasma (`/api/rastreamento/real-time`). **Não documentados: 16 de negócio + health.**

- **Entregas (8):** `GET /api/entregas/stats`, `GET /api/entregas/{id}`, `GET /api/entregas/pedido/{numeroPedido}`, `POST /api/entregas`, `PUT /api/entregas/{id}`, `PATCH /api/entregas/{id}/status`, `PATCH /api/entregas/{id}/atribuir`, `DELETE /api/entregas/{id}` (faz soft delete: muda o status para `CANCELADA`).
- **Rastreamento (4, sem auth):** `GET /api/rastreamento/{entregaId}`, `POST /api/rastreamento/{entregaId}`, `GET /api/rastreamento/{entregaId}/ultimo`, `GET /api/rastreamento/pedido/{numeroPedido}`.
- **Proxy para frotas (4):** `GET /api/frotas/veiculos/disponiveis`, `GET /api/frotas/veiculos/{placa}`, `GET /api/frotas/veiculos/{placa}/disponibilidade`, `GET /api/frotas/motoristas/{id}`.
- **Health:** `GET /api/health`. As dependências são sempre `UNKNOWN` (ADR 0001 de entregas, dívida 7).

#### 3.3 `api-notificacoes` (7 de negócio + health)

Nenhum documentado. **Não documentados: 7 de negócio + health.**

- `GET /api/notificacoes` (filtros `tipo`, `status`, `canal`, `servicoOrigem`), `GET /api/notificacoes/{id}`, `POST /api/notificacoes`, `POST /api/notificacoes/{id}/reenviar`, `GET /api/notificacoes/stats`, `GET /api/notificacoes/templates` (`apenasAtivos`), `POST /api/notificacoes/processar` (batch, sem auth).
- **Health:** `GET /api/health`. Expõe contagens de tabelas de outros schemas e `configuration.smtpServer` (ver dívida 6).

#### 3.4 Totais

- Endpoints de negócio reais: **52** (28 + 17 + 7).
- Documentados corretamente: **3**.
- Não documentados: **49** de negócio, mais **3** de health.
- Fantasmas no `openapi.yaml`: **1**.

## Decisão

### 1. Fonte da verdade

O contrato é o **JSON real de cada serviço**, confirmado por resposta real (Q1). A referência para os tipos é o código do controller, da rota ou do DTO de resposta. Os comentários `TODO` da lib não são fonte.

### 2. `shared-types` separado por serviço

Substituir o `index.ts` único por arquivos por serviço, com reexport no `index.ts`:

```
libs/shared-types/src/
  frotas.ts          Veiculo, Motorista, Manutencao
  entregas.ts        Entrega, Rastreamento, RastreamentoPedido
  notificacoes.ts    Notificacao, TemplateNotificacao, NotificacaoStats
  index.ts           reexporta os três
```

- Os **nomes dos campos são os do JSON real**, sem converter para camelCase. Não há camada de mapeamento que o compilador não verifica. Se a normalização for necessária, ela fica na borda do `fetch` (ADR 0002, seção 3), e não nos tipos.

### 3. Enums como uniões literais com os valores reais

```ts
export type StatusEntrega = 'PENDENTE' | 'ATRIBUIDA' | 'EM_TRANSITO' | 'ENTREGUE' | 'CANCELADA';
export type StatusVeiculo = 'ATIVO' | 'INATIVO' | 'MANUTENCAO';
export type StatusMotorista = 'ATIVO' | 'INATIVO';
export type StatusManutencao = 'PENDENTE' | 'EM_ANDAMENTO' | 'CONCLUIDA' | 'CANCELADA';
export type StatusNotificacao = 'PENDENTE' | 'ENVIADO' | 'FALHA';
export type CanalNotificacao = 'email' | 'sms';
```

- `CanalNotificacao` é minúsculo porque é o valor que o backend recebe e grava (`NotificacaoClient.java` envia `"email"`). Confirmar em Q1.

### 4. Tipos de dado

- **Datas:** `string` (ISO 8601). Não `Date`.
- **Anuláveis:** marcados com `| null` explícito, nunca como opcionais implícitos.
- **`id`, lat/lng, distância, peso e totais do Node:** `string` se Q1 confirmar. `number` nos três serviços Java e .NET, onde o JSON é número.

### 5. Remoções e correções

- Remover `ApiResponse<T>`, `Veiculo.ultimaManutencao`, `Motorista.veiculo_id`, `Entrega.progress_percentage` (o progresso vira cálculo local do app, fora do contrato) e `Notificacao.tipo` com `'webhook'`.
- Renomear `Notificacao.enviado_em` para `dataEnvio`, e `Notificacao.tipo` deixa de ser canal. O canal vai para `canal`.
- `Motorista.email` e `Motorista.telefone` saem do contrato até existir decisão de backend (Q4).
- Adicionar `Manutencao`, `TemplateNotificacao`, `NotificacaoStats` e `RastreamentoPedido`.

### 6. `api-contracts` em três arquivos

Substituir `openapi.yaml` por:

```
libs/api-contracts/src/
  openapi-frotas.yaml          servers: http://localhost:8080/api
  openapi-entregas.yaml        servers: http://localhost:3000/api
  openapi-notificacoes.yaml    servers: http://localhost:5000/api
```

Em cada arquivo:

- Todos os endpoints da seção 3, com `security` real (Bearer em `/entregas` e `/frotas` do Node, sem auth nos outros).
- Schemas de erro **por serviço**, com o formato real: `{ erro }` no `api-frotas`, `{ error }` nos outros dois (Q3).
- Remover `/api/rastreamento/real-time` (C1).
- Corrigir `/api/manutencao` para `/api/manutencoes`, e remover `/api/motoristas/{id}/disponibilidade` (C7).
- No health, documentar só `service`, `status` e `timestamp`. O restante (contagens, `smtpServer`, dependências) fica fora até decisão (dívida 6).

### 7. Backends não mudam nesta ADR

Nenhuma alteração em `rotalog-api-*`. As divergências do lado do backend são registradas em "Dívidas registradas".

### 8. `painel-admin`

Esta ADR **não altera** os models do `painel-admin`. A migração dele para `shared-types` depende de decisão própria (Q2), porque muda telas e services.

## Mudanças de comportamento

- **Runtime:** nenhuma. A lib não é importada por nenhum app hoje, e a mudança de tipo não altera nenhuma requisição.
- **Compilação:** nenhum build quebra, porque não há consumidor. Quando o `rastreamento` passar a usar a lib (ADR 0002, M9), o compilador passa a acusar divergências que hoje ficam escondidas em `any`.
- **Comparação de `id`:** se Q1 confirmar `id` como string, comparações com literal numérico (`id === 1`) deixam de casar. Comparações entre dois valores vindos da API continuam funcionando.

## Plano de migração

| Fase | Conteúdo | Depende de |
|---|---|---|
| 0 | Capturar uma resposta real por endpoint de listagem e detalhe dos três serviços (`curl`), guardadas como exemplos. Confirma Q1 e dá base para a fase 1 | Serviços no ar |
| 1 | `shared-types` por serviço, sem consumidor. Compilar a lib pelo target Nx que existir (verificar `project.json`, não verificado nesta análise) | Fase 0; Q4 |
| 2 | Três arquivos OpenAPI cobrindo o inventário da seção 3, com C1 a C8 corrigidos. Validar com um linter OpenAPI, escolhido na execução, sem adicionar dependência sem pedido | Fase 0; Q3; Q5 |
| 3 | Consumo: `rastreamento` (ADR 0002, M9) e `painel-admin` (Q2) passam a importar os tipos | Fase 1 |
| 4 | Fora desta ADR: padronizar erro, enums e datas nos backends, e deixar de expor entidades JPA no `api-frotas` | ADRs próprias |

## Alternativas consideradas

- **Gerar tipos e OpenAPI a partir dos backends.** Descartada nesta etapa. O `api-frotas` não tem Swagger habilitado, e o `api-entregas` não tem Swagger. Exigiria mudança nos backends. Fica como evolução futura.
- **Um tipo `Entrega` em camelCase, com mapper na borda.** Descartada. Esconde a divergência e cria uma camada de tradução que o compilador não verifica.
- **Um único OpenAPI com `servers` por caminho.** Descartada. Três arquivos são mais simples de manter e de gerar cliente.
- **Apagar `shared-types` e manter os models dentro de cada app.** Descartada. A fase 3 precisa de um lugar comum para o contrato, e é isso que a lib deve ser.

## Dívidas registradas (fora do escopo desta ADR)

1. **Envelope de erro divergente.** `api-frotas` devolve `{ erro }` em todas as rotas. `api-entregas` e `api-notificacoes` devolvem `{ error }`. `POST /api/entregas` devolve `detalhes` no 500, e o `api-notificacoes` devolve `detalhes` em todos os 500.
2. **`EntregaClient.veiculoTemEntregasAtivas` compara com minúsculo** (`rotalog-api-frotas/.../EntregaClient.java:64`: `"em_transito"`, `"pendente"`). O `api-entregas` grava maiúsculo. Resultado provável: sempre `false`. É um bug de contrato, não só de tipo.
3. **`api-frotas` expõe as entidades JPA diretamente.** Renomear um campo na entidade muda o contrato.
4. **`/api/entregas/stats` devolve linhas de SQL cruas** em `por_status`, com `total` provavelmente como string.
5. **Status, tipo e canal de notificação são strings sem enum** no backend (FIXME nos models).
6. **Health endpoints expõem detalhes internos.** O `api-notificacoes` devolve `configuration.smtpServer`, `smsApiUrl` e contagens de tabelas de outros schemas. Decidir antes de documentar.
7. **Contagem divergente.** O `CLAUDE.md` diz 8 endpoints de negócio em notificações. O código tem 7.

## Questões em aberto

- **Q1. Confirmar o formato real com respostas reais?** Especificamente: `id`, decimais e `COUNT` do Node vêm como string? `canal` chega como `"email"`? **Recomendação:** sim, antes da fase 1. Os comandos estão em "Como verificar".
- **Q2. A migração do `painel-admin` para `shared-types` entra nesta ADR ou em ADR própria?** **Recomendação:** ADR própria, porque muda telas e services. Esta ADR só corrige a lib.
- **Q3. O envelope de erro é documentado como está, ou padronizado agora?** **Recomendação:** documentar como está. Padronizar exige mudança nos backends, então vai para ADR de backend.
- **Q4. `Motorista.email`, `Motorista.telefone` e `Veiculo.ultimaManutencao` devem existir no backend, ou saem do contrato?** O próprio FIXME da entidade diz que faltam. **Recomendação:** sair do contrato até haver decisão de backend.
- **Q5. Remover `/api/rastreamento/real-time` do OpenAPI?** **Recomendação:** sim. Não existe em nenhum backend.

## Relação com outras ADRs

- **ADR 0002 (`rastreamento`):** esta ADR cumpre o M9 (alinhar `shared-types`) e responde a Q3 da ADR 0002 para `GET /api/entregas`, depois da confirmação de Q1.
- **ADR 0001 do `frontend`:** não foi lida nesta análise. Verificar se ela altera os models do `painel-admin` antes de aceitar esta ADR.
- **ADR 0001 de `entregas` (auth e camadas):** se aceita, muda `status`, `numero_pedido`, respostas de `PATCH /status` (passa a `409`) e respostas `401`. As seções 1 e 3 deste ADR precisam ser revisadas nesse caso.

## Como verificar

```bash
cd rotalog-frontend

# Nenhum app importa a lib hoje (resultado esperado: vazio)
grep -rn "shared-types" apps --include=*.ts --include=*.tsx

# O endpoint fantasma sai do OpenAPI (resultado esperado: vazio, depois da fase 2)
grep -rn "real-time" libs/api-contracts

# Caminhos documentados (hoje: 3, um deles fantasma)
grep -n "^  /" libs/api-contracts/src/*.yaml

# Chaves reais de uma resposta (exige o serviço no ar; jq ou equivalente)
curl -s http://localhost:8080/api/veiculos | jq '.[0] | keys'
curl -s http://localhost:3000/api/entregas | jq '.[0] | keys'
curl -s http://localhost:5000/api/notificacoes | jq '.[0] | keys'

# Tipo real de id e decimais (Q1)
curl -s http://localhost:3000/api/entregas | jq '.[0] | {id, origem_lat, distancia_km} | map_values(type)'
```

Critérios de aceite por fase:

- Fase 0: uma resposta real guardada por endpoint de listagem e detalhe, e Q1 respondida.
- Fase 1: cada campo de `shared-types` tem correspondência no JSON real, sem campo fantasma. Nenhum `any` nos tipos.
- Fase 2: cada caminho de `openapi-*.yaml` existe no backend, e o inventário da seção 3 está completo.

## Consequências

### Positivas

- Os tipos passam a bater com o JSON real, e o compilador acusa divergência quando um app começar a usar a lib.
- O OpenAPI deixa de apontar para endpoint inexistente e para o host errado.
- O inventário completo fica documentado, com 49 endpoints de negócio que hoje não aparecem em lugar nenhum.
- O bug de `EntregaClient` (dívida 2) fica visível como problema de contrato.

### Negativas

- A fase 0 não tem ganho visível para o usuário.
- O OpenAPI passa de um arquivo de ~130 linhas para três, com cobertura bem maior.
- O `painel-admin` continua com tipos próprios divergentes até a fase 3, e as telas com campos vazios continuam assim até lá.
- Se Q1 confirmar `id` como string, o tipo muda em todo consumidor, e isso precisa ser revisado app por app.
