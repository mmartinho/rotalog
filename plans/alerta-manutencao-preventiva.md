# Plano de Execução: Alerta de manutenção preventiva

- **Origem:** [DoD.md](../DoD.md)
- **Status:** implementado (pendência: ponto de disparo da verificação no front, decisão 3)
- **Serviços afetados:** rotalog-api-frotas, rotalog-api-notificacoes, rotalog-frontend (painel-admin), rotalog-workspace

## Objetivo

A api-frotas identifica veículos que precisam de manutenção preventiva, pede à api-notificacoes que envie um e-mail ao gestor e registra o alerta com o status da notificação. O painel-admin lista os alertas e permite filtrar por status.

## Decisões tomadas

| # | Tema | Decisão |
|---|------|---------|
| 1 | Limites | Elegível com **km rodada desde a última preventiva > 50000** **ou** **última preventiva há mais de 6 meses**. Os dois valores ficam configuráveis. |
| 2 | E-mail do gestor | Vem de **variável de ambiente** (`ROTALOG_EMAIL_GESTOR`), lida pela api-frotas e enviada no request à api-notificacoes. |
| 3 | Disparo | **Manual**, via endpoint. O ponto exato de disparo no front (ao abrir uma tela ou ao logar) ainda não foi definido; por isso o endpoint precisa ser idempotente e barato de chamar várias vezes. |
| 4 | Reenvio de PENDENTE | **Fora do escopo** desta entrega. |
| 5 | Base da quilometragem | **Km desde a última preventiva** = `veiculo.quilometragem - ultimaPreventiva.quilometragemManutencao`. |
| — | Comunicação | HTTP síncrona entre os serviços. |
| — | Falha | api-notificacoes fora do ar (conexão recusada ou timeout) → alerta registrado como **PENDENTE**. |

### Regras derivadas das decisões

- "Última preventiva" é a `Manutencao` mais recente com `tipoManutencao = PREVENTIVA` e `status = CONCLUIDA`.
- Veículo **sem nenhuma preventiva**: a km é a quilometragem total e a data de referência é `dataCadastro`.
- Só entram na verificação veículos com `status = ATIVO`.
- **Idempotência:** não se cria um novo alerta para um veículo que já tenha alerta `ENVIADA` ou `PENDENTE` posterior à sua última preventiva. Isso permite chamar a verificação a cada acesso a uma tela ou login.

## Estado atual (ponto de partida)

- `ManutencaoPreventivaService.precisaDeManutencao` usa a **km total ≥ 50000** com valor fixo no código e não tem regra por data. Precisa ser alterado para a regra da decisão 5.
- `NotificacaoClient` chama `POST /api/notificacoes` e retorna `void`. A URL e o `RestTemplate` estão fixos no código e não há timeout.
- `Manutencao` já tem `dataManutencao`, `quilometragemManutencao`, `tipoManutencao` e `status`.
- A api-frotas já tem testes unitários (`src/test/java/com/rotalog/service/`). A api-notificacoes **não tem projeto de testes**. O `painel-admin-e2e` tem Playwright, com apenas `example.spec.ts`.
- `ddl-auto=validate`: a tabela nova precisa existir no banco antes de a api-frotas subir.

---

## Etapa 1: Banco de dados (rotalog-workspace + api-frotas)

- [x] 1.1 Criar `rotalog-workspace/tools/scripts/09-alertas-manutencao.sql` com a tabela `frotas.alertas_manutencao`:
  - `id` BIGSERIAL PK
  - `veiculo_id` BIGINT NOT NULL
  - `motivo` VARCHAR (`KM`, `TEMPO`, `KM_E_TEMPO`)
  - `km_desde_ultima_preventiva` BIGINT
  - `data_referencia` TIMESTAMP (última preventiva ou cadastro)
  - `status` VARCHAR (`ENVIADA`, `FALHA`, `PENDENTE`)
  - `notificacao_id` BIGINT NULL
  - `mensagem_erro` TEXT NULL
  - `data_criacao`, `data_atualizacao` TIMESTAMP
  - índices em `status` e `veiculo_id`
- [x] 1.2 Criar `rotalog-api-frotas/src/main/resources/db/migration/V3__Alertas_Manutencao.sql` com o mesmo conteúdo, para manter a consistência (o Flyway segue desabilitado).
- [x] 1.3 Conferir se o seed de frotas (`05-seed-frotas.sql`) tem pelo menos: um veículo elegível por km, um elegível por tempo, um não elegível e um sem preventiva. Se faltar algum caso, criar um script de seed complementar, necessário para o E2E.
  - Feito: o `05` tem datas de 2024 e não cobre os casos; criado `10-seed-alertas-manutencao.sql` (idempotente, datas relativas a `NOW()`) com km, tempo, km e tempo, não elegível, sem preventiva e inativo.
- [x] 1.4 (Opcional) Adicionar o template `ALERTA_MANUTENCAO_PREVENTIVA` em `07-seed-notificacoes.sql` e em `rotalog-api-notificacoes/Data/migration.sql`.
- [x] 1.5 Documentar que os scripts só rodam quando o volume é criado: quem já tem o ambiente precisa rodar `docker-compose down -v && docker-compose up -d postgres` ou executar o script na mão.

## Etapa 2: rotalog-api-notificacoes (.NET)

Vem antes da api-frotas para que o contrato fique definido primeiro.

- [x] 2.1 Criar o DTO `AlertaManutencaoRequest` com: `veiculoId`, `placa`, `motivo`, `kmDesdeUltimaPreventiva`, `dataReferencia`, `emailGestor`.
- [x] 2.2 Criar o DTO `AlertaManutencaoResponse` com: `notificacaoId`, `status` (`ENVIADA` | `FALHA`) e `mensagemErro` (opcional).
- [x] 2.3 Criar o endpoint `POST /api/notificacoes/alertas-manutencao` no `NotificacoesController`:
  - valida os campos obrigatórios (400 se faltar algum)
  - monta a mensagem pelo template (ou por um texto padrão, se o template não existir)
  - envia pelo canal **email** para `emailGestor`
  - grava no histórico com `tipo = ALERTA_MANUTENCAO` e `servicoOrigem = api-frotas`
  - responde **200** com o status, inclusive quando o envio falha; 5xx fica só para erro inesperado
- [x] 2.4 Converter o status interno da notificação (ex.: `ENVIADO`/`FALHOU`) para o contrato (`ENVIADA`/`FALHA`).
- [x] 2.5 Isolar o envio simulado (`Random` + `Task.Delay`) atrás de uma interface (ex.: `IEnvioEmail`) registrada no DI, para os testes terem resultado previsível.
- [x] 2.6 Conferir se o endpoint aparece no Swagger (`/swagger`). *(descoberta automática do controller, com `ProducesResponseType`; não conferido no navegador)*

## Etapa 3: rotalog-api-frotas (Java)

### 3.1 Configuração
- [x] 3.1.1 Adicionar ao `application.properties`:
  ```properties
  rotalog.alertas.limite-km=50000
  rotalog.alertas.intervalo-meses=6
  rotalog.alertas.email-gestor=${ROTALOG_EMAIL_GESTOR}
  rotalog.notificacoes.url=${ROTALOG_NOTIFICACOES_URL:http://localhost:5000}
  rotalog.notificacoes.connect-timeout-ms=2000
  rotalog.notificacoes.read-timeout-ms=5000
  ```
- [x] 3.1.2 Definir o comportamento quando `ROTALOG_EMAIL_GESTOR` não estiver definida. Sugestão: a aplicação falha ao subir, com uma mensagem clara.
- [x] 3.1.3 Criar um `@Bean RestTemplate` com os timeouts e injetá-lo no `NotificacaoClient` no lugar do `new RestTemplate()`. Os métodos existentes continuam funcionando.

### 3.2 Domínio e persistência
- [x] 3.2.1 Criar a entidade `domain/AlertaManutencao` (Lombok, no padrão das entidades existentes).
- [x] 3.2.2 Criar o `repository/AlertaManutencaoRepository` com `findByStatusOrderByDataCriacaoDesc`, `findAllByOrderByDataCriacaoDesc` e uma consulta que diga se o veículo já tem alerta aberto (`ENVIADA`/`PENDENTE`) depois de uma data.
- [x] 3.2.3 Criar no `ManutencaoRepository` a consulta da última preventiva concluída de um veículo.

### 3.3 Regra de elegibilidade
- [x] 3.3.1 Alterar o `ManutencaoPreventivaService` (ou criar um `AlertaManutencaoService`) para:
  - calcular `kmDesdeUltimaPreventiva` e `dataReferencia` conforme as decisões 1 e 5 e as regras derivadas
  - determinar o `motivo` (`KM`, `TEMPO`, `KM_E_TEMPO`)
  - ler os limites da configuração no lugar da constante `LIMITE_QUILOMETRAGEM`
- [x] 3.3.2 Ajustar `precisaDeManutencao` para usar a mesma regra e atualizar o `ManutencaoPreventivaServiceTest`.

### 3.4 Integração com api-notificacoes
- [x] 3.4.1 Criar no `NotificacaoClient` o método `enviarAlertaManutencao(AlertaManutencaoPayload)`, que retorna um resultado (`status`, `notificacaoId`, `mensagemErro`).
- [x] 3.4.2 Converter a resposta em status do alerta:
  - resposta 200 com `ENVIADA` → `ENVIADA`
  - resposta 200 com `FALHA` ou resposta 4xx/5xx → `FALHA`
  - `ResourceAccessException` (conexão recusada ou timeout) → `PENDENTE`

### 3.5 Orquestração e endpoints
- [x] 3.5.1 Criar o fluxo `verificarAlertas()`: busca os veículos ativos → filtra os elegíveis → ignora os que já têm alerta aberto → para cada um, chama a api-notificacoes **e depois** grava o alerta com o status resultante. Uma falha em um veículo não interrompe os demais.
- [x] 3.5.2 Criar o `controller/AlertaManutencaoController` (tratamento de erro via `ResponseEntity`, como no restante do projeto):
  - `POST /api/manutencoes/alertas/verificar` → resumo `{ verificados, elegiveis, novosAlertas, enviados, falhas, pendentes }`
  - `GET /api/manutencoes/alertas?status=` → lista de alertas com a placa do veículo, filtro opcional e status inválido → 400
- [x] 3.5.3 Confirmar se o `CorsConfig` libera `http://localhost:4200` para os novos paths. *(já libera `/**` para qualquer origem; sem alteração)*

## Etapa 4: painel-admin (Angular)

- [x] 4.1 Criar a interface `AlertaManutencao` e o tipo `StatusAlerta = 'ENVIADA' | 'FALHA' | 'PENDENTE'` em `models/index.ts` (e em `libs/shared-types`, se fizer sentido). *(`shared-types` não alterada: já está dessincronizada do backend)*
- [x] 4.2 Adicionar ao `services/frotas.service.ts` os métodos `listarAlertas(status?)` e `verificarAlertas()`, usando `fetch` como no restante do service.
- [x] 4.3 Criar o componente standalone `components/alertas-manutencao/`:
  - tabela (`DataTable` e `StatusBadge` de `libs/ui-components` são React e não servem no Angular; tabela e badge feitos como nas demais telas) (placa, motivo, km desde a preventiva, data de referência, status, data do alerta)
  - `StatusBadge` com cores para ENVIADA, FALHA e PENDENTE
  - filtro por status (Todos / Enviada / Falha / Pendente)
  - estados de carregando, lista vazia e erro
- [x] 4.4 Registrar a rota em `app.routes.ts` e o item no `sidebar.component.ts`.
- [x] 4.5 **Ponto de disparo (decisão 3 em aberto):** deixar `verificarAlertas()` disponível no service, chamado de um único lugar e fácil de mover. Por enquanto, colocar um botão "Verificar agora" na tela de alertas. Quando a decisão sair, a chamada vai para o `ngOnInit` da tela escolhida ou para o fluxo de login.

## Etapa 5: Testes

### 5.1 api-frotas (JUnit 5 + Mockito)
- [x] Elegibilidade: só km, só tempo, km e tempo, nenhum, sem preventiva (km total + `dataCadastro`), veículo inativo, valores no limite exato (50000 km e 6 meses).
- [x] Orquestração: alerta gravado como ENVIADA, FALHA e PENDENTE (com o `NotificacaoClient` mockado).
- [x] Idempotência: uma segunda verificação não duplica os alertas.
- [x] Falha em um veículo não interrompe os demais.
- [x] `NotificacaoClient` com `MockRestServiceServer`: sucesso, falha, 5xx e timeout/conexão recusada.
- [x] Controller: filtro por status e status inválido → 400.

### 5.2 api-notificacoes (xUnit)
- [x] Criar o projeto de testes (ex.: `rotalog-api-notificacoes.Tests`) e adicioná-lo à solution. *(`tests/Notificacoes.Tests`, nome curto por causa do limite de 260 caracteres de caminho no Windows; criada `rotalog-api-notificacoes.sln`)*
- [x] Validação do request (campos obrigatórios).
- [x] Envio com sucesso → `ENVIADA`; envio com falha → `FALHA` (com o `IEnvioEmail` mockado).
- [x] Gravação no histórico com tipo e origem corretos (EF Core InMemory).

### 5.3 painel-admin (Jest)
- [x] `frotas.service`: `listarAlertas` com e sem filtro, e `verificarAlertas`.
- [x] Componente: renderização da lista, filtro e status no badge.

### 5.4 E2E do fluxo completo (Playwright em `painel-admin-e2e`)
- [x] Caminho feliz: banco com seed → disparar a verificação → abrir a tela de alertas → o alerta aparece como **ENVIADA** → a notificação existe em `GET /api/notificacoes?tipo=ALERTA_MANUTENCAO`.
- [x] Falha: com a api-notificacoes desligada, disparar a verificação → o alerta aparece como **PENDENTE**.
- [x] Filtro por status na tela.
- [x] Documentar o passo a passo para subir o ambiente do E2E (Postgres, as 3 APIs com `ROTALOG_EMAIL_GESTOR` definida e o painel).

## Etapa 6: Documentação e contratos

- [x] 6.1 Atualizar `libs/api-contracts` (OpenAPI) com os novos endpoints de frotas e de notificações.
- [x] 6.2 Atualizar os READMEs dos serviços e o `CLAUDE.md` (contagem de endpoints, nova tabela, novas variáveis de ambiente).
- [x] 6.3 Documentar as variáveis `ROTALOG_EMAIL_GESTOR` e `ROTALOG_NOTIFICACOES_URL`.

---

## Ordem sugerida e dependências

1. Etapa 1 (banco), necessária para a api-frotas subir com `ddl-auto=validate`
2. Etapa 2 (api-notificacoes), que define o contrato
3. Etapa 3 (api-frotas), que consome o contrato
4. Etapa 4 (painel-admin)
5. Etapa 5: os testes unitários são escritos junto com cada etapa; o E2E vem por último
6. Etapa 6 (documentação)

## Fora do escopo

- Reenvio automático ou manual de alertas PENDENTE (decisão 4)
- Agendamento automático da verificação (`@Scheduled`)
- Circuit breaker e retry no `NotificacaoClient`
- Comunicação assíncrona (filas)

## Pendências

- [ ] Definir o ponto de disparo da verificação no front: ao acessar uma tela (qual?) ou ao logar (decisão 3).
