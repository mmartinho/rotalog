# Mapa de Dívidas Técnicas — RotaLog

> Escrito para: equipe de engenharia do RotaLog que vai priorizar a refatoração.

**Escopo:** 5 repositórios locais (`rotalog-api-entregas`, `rotalog-api-frotas`, `rotalog-api-notificacoes`, `rotalog-frontend`, `rotalog-workspace`).
**Data da análise:** 2026-10-12.
**Método:** leitura do código e da configuração, busca por padrões e verificação de cruzamentos entre serviços. Não há testes automatizados nem execução do sistema, então os achados são estáticos. Itens marcados como **verificado** foram confirmados por busca no código.

## Legenda de severidade

| Nível | Significado |
|---|---|
| 🔴 Crítica | Falha de segurança explorável ou bug que quebra fluxo de negócio |
| 🟠 Alta | Risco relevante de produção ou dívida que bloqueia evolução |
| 🟡 Média | Degrada manutenção, performance ou confiabilidade |
| 🔵 Baixa | Higiene, padronização ou documentação |

---

## Visão geral

| Projeto | Stack | Dívidas críticas | Dívidas altas | Testes automatizados |
|---|---|---|---|---|
| rotalog-workspace | Docker Compose + PostgreSQL 14 | 1 | 3 | — |
| rotalog-api-entregas | Node.js 18 / Express 4 / Sequelize | 3 | 8 | Nenhum |
| rotalog-api-frotas | Java 11 / Spring Boot 2.7.14 | 2 | 8 | Nenhum |
| rotalog-api-notificacoes | .NET 6 / EF Core 6 / MediatR | 2 | 7 | Nenhum |
| rotalog-frontend | Angular 18 + React 18 (Nx 19) | 1 | 5 | 2 arquivos `.spec.ts` |

## Dívidas transversais (sistema inteiro)

### Arquitetura
- 🟠 **Ciclo síncrono entre serviços.** `api-frotas` chama `api-entregas` (`EntregaClient`) e `api-entregas` chama `api-frotas` (`src/services/frotasService.js`). Uma indisponibilidade de um derruba o outro. **Verificado.**
- 🟠 **Acoplamento por banco.** Os três serviços compartilham a mesma base `rotalog`, separada apenas por schema, e `api-notificacoes` lê `frotas.veiculos` e `entregas.entregas` diretamente (`Controllers/HealthController.cs`).
- 🟡 **Esquema definido em cinco lugares sem fonte única de verdade:** `init-schemas.sql` + scripts `02` a `08` no workspace, `migration.sql` em cada repo, Flyway `V1`/`V2` no `api-frotas` (desabilitado), `EnsureCreated()` no `api-notificacoes` e `sequelize.sync()` no `api-entregas`.
- 🟡 **Seeds duplicados.** `api-entregas/src/config/seed.sql` e `populate.sql` repetem o que existe em `workspace/tools/scripts/06-seed-entregas.sql`.
- 🟡 **Scripts de init só rodam com volume novo.** Mudanças nos seeds não chegam a um banco existente sem `docker-compose down -v` (documentado no README, mas frágil).

### Segurança
- 🔴 **Nenhum serviço tem autenticação real.** Só existe o middleware do `api-entregas`, e ele está desligado (ver abaixo). Os demais não têm nenhum mecanismo de autenticação ou autorização.
- 🔴 **Credenciais repetidas em 5 arquivos versionados:** `docker-compose.yml`, `api-entregas/.env`, `api-entregas/src/config/database.js`, `api-frotas/src/main/resources/application.properties` e `api-notificacoes/appsettings.json`. A senha `rotalog123` aparece em todos eles. **Verificado.**
- 🟠 **CORS aberto em todos os serviços** (`*` no Express, `allowedOrigins("*")` no Spring, `AllowAnyOrigin()` no .NET).
- 🟠 **Frontends com URLs fixas em `localhost`.** `painel-admin/src/app/services/*.ts` e `rastreamento/src/App.tsx` apontam para `localhost:3000` e `localhost:8080` no código. **Verificado.**

### Estado de pendências
- 🟡 **Infraestrutura prometida e não implementada:** Redis, Kafka, Elasticsearch, Prometheus e Grafana aparecem como `TODO` no `docker-compose.yml`, mas não existem.
- 🟡 **Zero testes de integração ou de contrato** entre os serviços.
- 🔵 **Arquivos de ambiente de IA e lixo commitados:** `CLAUDE.md`, `AGENTS.md`, `opencode.json` e `error.log` (que contém a saída do git, não um log real) estão no repositório do frontend. **Verificado.**

### Qualidade de infraestrutura
- 🟠 **Sem CI/CD** em nenhum repositório e **sem Dockerfile** para os serviços. Só o Postgres sobe via compose.
- 🟠 **PostgreSQL 14** chega ao fim de vida em novembro de 2026, perto da data desta análise.
- 🟡 **Sem observabilidade.** Não há métricas, logs estruturados nem correlation ID entre serviços.

---

## rotalog-workspace (infraestrutura)

**Stack:** Docker Compose, PostgreSQL 14, scripts SQL em `tools/scripts/`.

### Problemas de arquitetura
- 🟡 Um único container de banco serve os três domínios. Não há isolamento de falha nem de escala por serviço.
- 🟡 Os serviços de aplicação não estão no compose. Cada um sobe à mão, em portas fixas (3000, 5000, 8080, 4200, 3001).

### Inconsistências no código
- 🟡 **A documentação não bate com o código.** O `CLAUDE_CONTEXT.md` diz que os repositórios ficam na organização `Charlinho`, mas os remotes apontam para `alura-cursos`. Também diz que o frontend usa React 17 (o `package.json` tem React 18.3.1) e que 70% dos componentes React são de classe (encontrei 3 em `rastreamento`). **Verificado.**
- 🔵 O `README.md` diz que `docker-compose up -d` executa os scripts automaticamente, mas isso só vale na primeira criação do volume.

### Vulnerabilidades de segurança
- 🔴 **Senha em texto plano no `docker-compose.yml`** (`POSTGRES_PASSWORD: rotalog123`), com a porta `5432` publicada no host.
- 🟠 **Um único usuário dono de tudo.** `rotalog_admin` recebe `ALL PRIVILEGES` nos três schemas (`init-schemas.sql`). Não há separação de papéis por serviço, e o próprio script marca isso como `TODO`.
- 🟡 Sem RLS, sem tabelas de auditoria (todos marcados como `TODO`).

### Estado de pendências
- 🟡 `docker-compose.yml` tem alteração não commitada.
- 🟡 Os `TODO` de health check, limites de recurso e estratégia de backup seguem sem implementação.

### Qualidade de infraestrutura
- 🟠 **Sem health check, sem limites de recurso e sem backup** no serviço de banco.
- 🟠 **Versão do Postgres** próxima do fim de suporte (ver transversal).
- 🔵 Os `FIXME` do compose, do tipo "No service containers" e "Password in compose file", descrevem problemas que continuam presentes.

---

## rotalog-api-entregas (Node.js / Express / Sequelize)

**Stack:** Node.js 18, Express 4.18, Sequelize 6, `pg`.

### Problemas de arquitetura
- 🟠 **Lógica de negócio dentro das rotas.** `src/routes/entregas.js` (365 linhas) faz validação, cálculo de distância, geração de número de pedido e persistência. Não há camada de serviço nem de domínio.
- 🟠 **Integração de notificações morta.** `src/services/notificacaoService.js` tem cinco funções de notificação, mas nenhuma rota as importa ou chama. **Verificado:** `grep` por `notificacaoService` e `notificar*` em `src/` só encontra a própria definição. Então `api-entregas` nunca envia notificação.
- 🟠 **Chamadas HTTP síncronas sem timeout nem retry.** `frotasService.js` e `notificacaoService.js` usam `http` nativo com callbacks, sem timeout e sem tratamento de falha.
- 🟡 Mistura de Sequelize com SQL bruto (`/stats` em `entregas.js`) e de callbacks com `async/await` (cerca de 60% das rotas usam callback, segundo o próprio código).
- 🟡 `sequelize.sync({ alter: false })` no startup, com comentário dizendo que é inseguro em produção.
- 🟡 Pool de conexões fixo em `max: 5`, sem configuração por ambiente.

### Inconsistências no código
- 🔴 **Bug de status: o `api-frotas` compara com minúsculas.** O `api-entregas` grava `PENDENTE` e `EM_TRANSITO` em maiúsculas (`statusValidos` em `entregas.js`). O `EntregaClient.java` do `api-frotas` compara com `"em_transito"` e `"pendente"`. Resultado: `veiculoTemEntregasAtivas` retorna sempre `false`. **Verificado.**
- 🟠 **Filtro de motorista não funciona.** O frontend chama `/api/entregas?motorista=`, mas a rota só lê `req.query.motorista_id`. O filtro é ignorado em silêncio. **Verificado.**
- 🟡 **Número do pedido com `Math.random()`** (`PED-<timestamp>-<aleatório de 0 a 999>`). Pode colidir sob carga.
- 🟡 **Distância por aproximação plana** (`sqrt(dLat² + dLng²) * 111`), e não por Haversine. O código tem FIXME dizendo isso.
- 🟡 Formato de erro inconsistente com os outros serviços (`error` aqui, `erro` no `api-frotas`).
- 🔵 `/api/frotas/veiculos/:placa` concatena a placa na URL sem `encodeURIComponent` (`frotasService.js`).

### Vulnerabilidades de segurança
- 🔴 **Autenticação desligada na configuração padrão.** `middleware/auth.js` faz bypass quando `NODE_ENV !== 'production'`, e o `.env` commitado define `NODE_ENV=development`. Nesse modo, todas as rotas `/api/entregas` e `/api/frotas` ficam abertas e todo request recebe `req.user = { role: 'admin' }`. **Verificado.**
- 🔴 **Mesmo com a autenticação ligada, o token não é validado.** O middleware só confere se o header existe e tem mais de 10 caracteres. Sem verificação de assinatura, expiração ou papel.
- 🔴 **`.env` com segredos commitado no git** (`JWT_SECRET`, `DB_PASSWORD`). **Verificado:** o arquivo está rastreado (`git ls-files .env`), apesar do próprio FIXME dizendo que não deveria estar.
- 🟠 **`/api/rastreamento` é público e aceita escrita sem auth** (`POST /api/rastreamento/:entregaId`), sem rate limit.
- 🟠 **Vazamento de detalhes internos.** `errorHandler.js` devolve `stack` fora de produção e registra `req.body` no log. O `POST /api/entregas` devolve `detalhes: error.message` ao cliente.
- 🟠 **`PATCH /:id/status` sem autorização e sem máquina de estados.** Permite voltar de `ENTREGUE` para `PENDENTE`.
- 🟡 CORS com `Access-Control-Allow-Origin: *`.

### Estado de pendências
- 🟠 **Zero testes.** O script `test` é `echo "Error: no test specified" && exit 1`.
- 🟡 **Integração de notificação não concluída** (ver arquitetura).
- 🟡 **`/api/health` com dependências hardcoded como `UNKNOWN`.** Não verifica banco nem serviços.
- 🔵 `package-lock.json` com alteração não commitada.

### Qualidade de infraestrutura
- 🟠 Sem graceful shutdown. O `uncaughtException` só loga e encerra.
- 🟡 Sem métricas, sem `morgan` (logging manual com `console.log` em cada request), sem rate limit.
- 🟡 `logging: console.log` no Sequelize imprime todo SQL em produção.
- 🔵 Sem linter nem formatador configurado.

---

## rotalog-api-frotas (Java 11 / Spring Boot 2.7.14)

**Stack:** Spring Boot 2.7.14, Spring Data JPA, Lombok, Flyway (desabilitado), PostgreSQL 42.5.1.

### Problemas de arquitetura
- 🟠 **Herança JPA com `TABLE_PER_CLASS`** em `domain/Veiculo.java`. O próprio código marca como estratégia ruim para este caso.
- 🟠 **Ciclo síncrono com `api-entregas`** (`EntregaClient.java`, que chama `GET /api/entregas?veiculo=`).
- 🟡 **`VeiculoService.java` com 339 linhas** misturando CRUD, estatísticas, sincronização e notificação. O `CLAUDE_CONTEXT.md` diz 400+; o arquivo tem 339.
- 🟡 **Migrations sem uso.** `V1__Initial_Schema.sql` e `V2__Seed_Data.sql` estão no repo, mas `spring.flyway.enabled=false`. O esquema real vem dos scripts do workspace.
- 🟡 **Clientes HTTP instanciados à mão** (`new RestTemplate()` em `EntregaClient` e `NotificacaoClient`), com URLs hardcoded (`http://localhost:3000`, `http://localhost:5000`).
- 🔵 Injeção por campo (`@Autowired` em atributo) em vez de construtor, marcada com FIXME.

### Inconsistências no código
- 🔴 **`DELETE /api/veiculos/{id}` não existe no backend, mas a interface chama.** `frotas.service.ts` do frontend tem `deletarVeiculo`, e `veiculos.component.ts:459` o usa. O `VeiculoController` não tem nenhum `@DeleteMapping` (**verificado**). O botão de exclusão vai falhar com 405.
- 🟠 **Clientes engolindo erros.** `EntregaClient.buscarEntregasPorVeiculo` devolve lista vazia em qualquer exceção, o que faz um veículo parecer sem entregas quando o serviço está fora. `NotificacaoClient.enviarSms` também engole a exceção, mas `enviarNotificacao` relança.
- 🟡 Typo no nome do método `obterEstatisticasFreita()` em `VeiculoService`, chamado do controller.
- 🟡 `GET /estatisticas` devolve JSON montado como `String`, não um objeto tipado.
- 🟡 Controllers devolvem a entidade JPA diretamente, sem DTO de resposta. Não há `@Valid` nos requests.
- 🟡 Logging misturado: `@Slf4j` e `System.out.println` (`VeiculoService.java:116, 164, 327`).
- 🔵 Canal `"email"` hardcoded no envio de notificação.

### Vulnerabilidades de segurança
- 🔴 **Nenhum endpoint tem autenticação ou autorização.** O `pom.xml` não tem Spring Security.
- 🟠 **CORS com `allowedOrigins("*")` e `allowedHeaders("*")`** (`config/CorsConfig.java`).
- 🟠 **Logs com dados sensíveis.** `application.properties` liga `show-sql=true`, `org.hibernate.SQL=DEBUG` e `BasicBinder=TRACE`, o que registra os valores dos parâmetros das consultas.
- 🟠 **Spring Boot 2.7.14 e Java 11 defasados.** O suporte open source do Spring Boot 2.7 terminou em 2023, e o Java 11 já tem LTS mais recentes. Flyway 8.5.1 também está defasado.
- 🟡 Senha do banco em `application.properties`, em texto plano.

### Estado de pendências
- 🟠 **Sem nenhum teste.** Não existe `src/test`, embora `spring-boot-starter-test` esteja declarado no `pom.xml`.
- 🟡 **O README manda rodar `./mvnw spring-boot:run`, mas não há `mvnw` no repositório.** **Verificado.** O comando documentado não funciona.
- 🔵 Os `TODO` de Swagger, HATEOAS, Actuator e métricas seguem em aberto.

### Qualidade de infraestrutura
- 🟠 **Sem Dockerfile e sem CI.** O serviço não tem como ser empacotado de forma reproduzível.
- 🟡 **Health check manual** em `HealthCheckController`, com `localhost:3000` e `localhost:5000` hardcoded. Deveria usar Spring Actuator.
- 🟡 `spring.jpa.hibernate.ddl-auto=validate` depende de um esquema que o Flyway não gerencia mais.

---

## rotalog-api-notificacoes (.NET 6 / EF Core 6 / MediatR)

**Stack:** .NET 6, EF Core 6, Npgsql 6, MediatR 11, Swashbuckle 6.5.

### Problemas de arquitetura
- 🟠 **Clean Architecture abandonada pela metade.** MediatR está registrado em `Program.cs` (`AddMediatR`), mas nenhum handler é usado. O `NotificacaoService` concentra CRUD, envio, template e retry.
- 🟠 **Health check viola o contexto de outro serviço.** `Controllers/HealthController.cs` abre conexões com o `searchPath` trocado para `frotas` e `entregas` e faz `SELECT COUNT(*)` nas tabelas deles. **Verificado.**
- 🟡 **Serviço concreto injetado** (`AddScoped<NotificacaoService>()`) sem interface, o que impede testes com mocks.
- 🟡 **Schema criado por `EnsureCreated()`** no startup, e o app continua rodando mesmo se o banco estiver fora (`Program.cs`, bloco `catch`).

### Inconsistências no código
- 🔴 **O envio de notificação é simulado.** `EnviarEmail` e `EnviarSms` só escrevem log (`[EMAIL FAKE]`, `[SMS FAKE]`), esperam 50 ou 100 ms e, com `new Random()`, lançam erro em 10% ou 20% das vezes. Nenhum email nem SMS é enviado de fato. **Verificado** em `Services/NotificacaoService.cs`.
- 🟡 Health check devolve `smtpServer` e `smsApiUrl` no corpo da resposta, expondo configuração interna.
- 🟡 `Program.cs` expõe Swagger sem checar ambiente.
- 🔵 A versão de `Microsoft.EntityFrameworkCore` (6.0.12) e a do MediatR estão fixadas com FIXME de "should be updated".

### Vulnerabilidades de segurança
- 🔴 **Credenciais em `appsettings.json` versionado:** `SenderPassword` do SMTP, `ApiKey` do provedor de SMS e a connection string com `rotalog123`. **Verificado.**
- 🟠 **Endpoints sem autenticação, incluindo o de processamento em lote.** `POST /api/notificacoes/processar` dispara o envio de todos os pendentes e não tem nenhuma proteção.
- 🟠 **CORS com `AllowAnyOrigin()` e `AllowedHosts: "*"`.**
- 🟠 **Erros internos devolvidos ao cliente.** Todos os endpoints retornam `detalhes = ex.Message`.
- 🟡 `UseHttpsRedirection` comentado.

### Estado de pendências
- 🟠 **Zero testes.** Não existe projeto de teste na solução.
- 🟡 Os `TODO` de autenticação, rate limit, FluentValidation, Polly e Serilog seguem sem implementação.
- 🟡 **Arquivos de build versionados.** `obj/project.assets.json` e outros cinco arquivos de `obj/` estão no git e aparecem como modificados. **Verificado.**

### Qualidade de infraestrutura
- 🟠 **.NET 6 fora de suporte** desde novembro de 2024.
- 🟡 Sem health check via `IHealthCheck`. O endpoint é um controller manual que faz consultas no banco.
- 🟡 Sem Dockerfile, sem CI.

---

## rotalog-frontend (Nx 19 — Angular 18 + React 18)

**Stack:** monorepo Nx com `apps/painel-admin` (Angular 18.2), `apps/rastreamento` (React 18.3), `libs/shared-types`, `libs/ui-components` e `libs/api-contracts`.

### Problemas de arquitetura
- 🟠 **Contrato de API não é usado.** `libs/api-contracts/src/openapi.yaml` existe, mas nenhum código o referencia. Os tipos em `libs/shared-types` são escritos à mão. **Verificado** por busca.
- 🟠 **Chamadas HTTP com `fetch()` direto no Angular.** `painel-admin/src/app/services/*.ts` tem 14 chamadas `fetch(` e nenhuma `HttpClient` efetiva, já que `app.config.ts` não registra `provideHttpClient()`. Os próprios comentários dizem `TODO: Deveria usar HttpClient`.
- 🟡 **Sem lazy loading.** `app.routes.ts` não usa `loadComponent` nem `loadChildren`. **Verificado.**
- 🟡 **Componentes grandes demais.** `veiculos.component.ts` tem 464 linhas e `dashboard.component.ts` tem 349, misturando lista, formulário e estado.
- 🟡 **Dois frameworks no mesmo monorepo** sem fronteira clara, e a lib `ui-components` é só React, então o Angular não pode reutilizá-la.
- 🔵 Cache manual em memória nos services (`veiculosCache`, `motoristasCache`) com invalidação manual (`limparCache`, marcado como `TODO`).

### Inconsistências no código
- 🔴 **Botão de exclusão de veículo quebra.** Ver `api-frotas`: a interface chama `DELETE /api/veiculos/{id}` e o backend não tem essa rota. **Verificado.**
- 🟠 **Filtro de entregas por motorista não funciona.** O frontend manda `?motorista=`, e o backend espera `motorista_id`. **Verificado.**
- 🟠 **Tipagem fraca apesar de `strict` ligado.** `tsconfig.base.json` tem `"strict": true`, mas `rastreamento` tem 23 ocorrências de `any`.
- 🟡 **Duplicação de método:** `getManutencoes` e `getManutencoesPorVeiculo` fazem coisas parecidas, com `TODO` de unificação.
- 🟡 **Erros silenciados.** Quase todos os services fazem `catch` e devolvem `null` ou `[]`, sem propagar o erro para a UI.
- 🔵 **Documentação desatualizada:** o `CLAUDE_CONTEXT.md` fala em React 17 e 70% de classes. O código tem React 18.3 e 3 componentes de classe.

### Vulnerabilidades de segurança
- 🟡 **Nenhum mecanismo de login.** O painel administrativo é aberto para quem acessar a URL. Isso se soma à ausência de auth no backend.
- 🟡 **URLs de API fixas em `localhost`** no código de produção (ver transversal).
- 🟡 Sem política de Content Security Policy configurada no `index.html`.
- 🔵 **Positivo verificado:** não há uso de `innerHTML`, `dangerouslySetInnerHTML` nem `localStorage`/`sessionStorage` nos apps.

### Estado de pendências
- 🟠 **Quase sem testes.** Existem dois `.spec.ts`: `painel-admin/src/app/app.component.spec.ts` e `painel-admin-e2e/src/example.spec.ts`, este último é o exemplo padrão do Playwright. **Verificado.**
- 🔵 **Resíduos do gerador Nx:** `apps/painel-admin/src/app/nx-welcome.component.ts` e `packages/.gitkeep`.
- 🔵 **Arquivos de alteração não commitados:** `package-lock.json` e `tsconfig.json`, este último só com a quebra de linha final alterada. **Verificado.**
- 🔵 **`error.log` commitado**, com saída do git no lugar de log. **Verificado.**

### Qualidade de infraestrutura
- 🟠 **Sem CI e sem pipeline de build/teste.** Não há Dockerfile nem configuração de deploy para os frontends.
- 🟡 Jest, Playwright e ESLint 9 estão configurados, mas sem uso real.
- 🟡 Os apps se comunicam direto com as APIs em `localhost`, sem proxy nem gateway.

---

## Plano de ação sugerido (ordem de prioridade)

1. **Segurança (agora):** tirar a autenticação do modo bypass e validar o JWT no `api-entregas`; remover credenciais de todos os arquivos versionados e rotacionar `rotalog123`; restringir CORS.
2. **Bugs de negócio:** corrigir a comparação de status em minúsculas no `api-frotas`; corrigir o filtro `motorista` vs `motorista_id`; implementar ou remover o `DELETE` de veículo no frontend ou no backend; decidir se as notificações do `api-entregas` devem ser chamadas.
3. **Notificações reais:** substituir a simulação em `api-notificacoes` por um provedor de email e SMS de verdade, e fazer o envio sair de endpoint público.
4. **Contrato e fronteiras:** gerar tipos a partir do `openapi.yaml`; remover a leitura de schemas alheios em `HealthController.cs` e no seed; eliminar o ciclo síncrono entre `frotas` e `entregas`.
5. **Testes e CI:** cobrir pelo menos os fluxos de criação e atribuição de entrega com testes de integração; montar pipeline de build em cada repo.
6. **Infraestrutura:** Dockerfile para cada serviço e entrada no compose; health checks; usuários de banco separados por serviço; planejar a migração do PostgreSQL 14, do .NET 6 e do Spring Boot 2.7.
7. **Higiene:** tirar `.env`, `obj/`, `error.log` e arquivos de agente do git; remover `nx-welcome` e os resíduos; atualizar o `CLAUDE_CONTEXT.md`.

---

## Limitações desta análise

- A análise é estática. Não rodei os serviços, então bugs de runtime podem existir além destes.
- Os itens marcados como **verificado** foram confirmados por busca no código. Os demais são inferidos a partir da leitura e dos comentários `FIXME`/`TODO` do próprio repositório.
- A versão de suporte de PostgreSQL, .NET e Spring Boot foi comparada com o calendário público de cada produto, na data desta análise.
