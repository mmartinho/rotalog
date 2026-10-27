# E2E: alerta de manutenção preventiva

Teste Playwright em `apps/painel-admin-e2e/src/alertas-manutencao.spec.ts`. Cobre o fluxo
painel-admin → api-frotas → api-notificacoes → banco.

## Cenários

| Cenário | Pré-condição | O que verifica |
|---------|--------------|----------------|
| Caminho feliz | api-notificacoes no ar | "Verificar agora" gera alertas `ENVIADA` para os veículos elegíveis do seed, ignora o não elegível e o inativo, e a notificação existe em `GET /api/notificacoes?tipo=ALERTA_MANUTENCAO` com o e-mail do gestor |
| Idempotência | após o caminho feliz | Uma segunda verificação não cria novos alertas |
| Filtro por status | após o caminho feliz | Todos / Enviada / Pendente na tela |
| Falha | api-notificacoes **desligada** e `E2E_NOTIFICACOES_DOWN=true` | Os alertas são registrados como `PENDENTE` |

Os testes rodam em série e só no chromium, porque compartilham o estado do banco. Cada grupo
limpa `frotas.alertas_manutencao` antes de começar (via `docker exec`).

## Passo a passo

1. **Postgres com seed** (scripts `09` e `10` do `rotalog-workspace`):

   ```bash
   cd rotalog-workspace
   docker-compose down -v && docker-compose up -d postgres   # apaga os dados
   ```

   Em um ambiente que já existe, sem apagar nada, execute os scripts novos na mão:

   ```bash
   docker exec -i rotalog-db psql -U rotalog_admin -d rotalog < tools/scripts/09-alertas-manutencao.sql
   docker exec -i rotalog-db psql -U rotalog_admin -d rotalog < tools/scripts/10-seed-alertas-manutencao.sql
   ```

2. **api-frotas** (porta 8080). O e-mail do gestor tem padrão `gestor@rotalog.com`; para trocar, defina `ROTALOG_EMAIL_GESTOR` (e use o mesmo valor no teste):

   ```bash
   cd rotalog-api-frotas
   ROTALOG_EMAIL_GESTOR=gestor@rotalog.com ./mvnw spring-boot:run
   ```

3. **api-notificacoes** (porta 5000). O envio simulado falha em 10% das vezes; para o E2E, desligue a falha aleatória:

   ```bash
   cd rotalog-api-notificacoes
   EmailSettings__SimulatedFailureRate=0 dotnet run
   ```

4. **Playwright** (o `playwright.config.ts` sobe o painel-admin na porta 4200 sozinho; na primeira vez, `npx playwright install chromium`):

   ```bash
   cd rotalog-frontend
   npx nx e2e painel-admin-e2e -- --project=chromium
   ```

5. **Cenário de falha**: desligue a api-notificacoes e rode com a variável:

   ```bash
   E2E_NOTIFICACOES_DOWN=true npx nx e2e painel-admin-e2e -- --project=chromium
   ```

## Variáveis do teste

| Variável | Padrão | Uso |
|----------|--------|-----|
| `E2E_DB_CONTAINER` | `rotalog-db` | Container do Postgres usado para limpar os alertas |
| `E2E_NOTIFICACOES_DOWN` | - | `true` roda só o cenário de falha |
| `ROTALOG_EMAIL_GESTOR` | `gestor@rotalog.com` | E-mail esperado na notificação (o mesmo da api-frotas) |
| `FROTAS_API_URL` | `http://localhost:8080/api` | |
| `NOTIFICACOES_API_URL` | `http://localhost:5000/api` | |

## Limpar os alertas na mão

```bash
docker exec rotalog-db psql -U rotalog_admin -d rotalog -c "TRUNCATE frotas.alertas_manutencao RESTART IDENTITY"
```
