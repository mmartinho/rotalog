# Unificar rotalog em um único repositório Git

## Contexto
Hoje `rotalog/` tem 5 subprojetos, e cada um tem seu próprio `.git` (remotes `alura-cursos/rotalog-*`, branches `main` e `aula-*`). A ideia é enviar tudo ao GitHub como um único repositório. A raiz ainda não é um repo e já contém `CLAUDE.md`, `DoD.md`, `MAPA_DIVIDA_TECNICA.md`, `README.txt`, `.claude/` e `plans/`.

Decisões do usuário:
- As alterações não commitadas de frotas, notificacoes e frontend entram como estão.
- Regras de .gitignore que conflitam ficam restritas ao subprojeto de origem, e os arquivos já versionados continuam versionados, **exceto credenciais**.
- `.env` e arquivos típicos de credencial (chaves, certificados, keystores, secrets) são ignorados em todo o repositório. O `.env` do entregas (`DB_PASSWORD`, `JWT_SECRET`) não vai para o GitHub.
- O `appsettings.json` do notificacoes e o `application.properties` do frotas **continuam versionados**, mesmo tendo senhas de desenvolvimento, porque os serviços precisam deles para subir.
- O txt traz as mensagens de **todas** as branches.

Raiz: `D:/wamp64/www/alura/Carreiras/IA/NativeSftEng/N1/Legado/rotalog`

## Passos

### 1. Backup e captura do estado (ainda com os `.git` existentes)
- Compactar os 5 diretórios `.git` em `<scratchpad>/rotalog-git-backup.zip`. A exclusão não tem volta, então o backup vem primeiro.
- Para cada subprojeto, gravar no scratchpad a lista de arquivos que precisam entrar no novo repo: `git ls-files` + `git ls-files --others --exclude-standard`, com o prefixo `rotalog-xxx/`. A lista inclui arquivos versionados mesmo estando ignorados, como o `package-lock.json` do entregas, mas **remove os arquivos de credencial** (`rotalog-api-entregas/.env`).

### 2. Arquivo temporário `rotalog/historico-commits.txt`
- Uma seção por subprojeto: `git log --all --no-merges --format="%h %ad %an%n%B" --date=short`. Isso pega o corpo completo da mensagem, sem duplicar commits entre branches e sem os merges "Merge branch 'main'..." do frontend.
- Colocar o arquivo em `.git/info/exclude` do novo repo, para que fique local e não seja commitado.

### 3. `rotalog/.gitignore` (união sem repetições)
Agrupar por seção (Credenciais, Node, Java/Maven, .NET, IDEs, OS, Logs, Nx/Angular, Claude). As regras são deduplicadas: `.DS_Store`, `Thumbs.db`, `*.log`, `node_modules/`, `dist/`, `build/`, `target/`, `bin/`, `obj/`, `.idea/`, `*.swp`, `*~` e similares aparecem uma só vez.

**Seção Credenciais (global, logo no topo):**
```
# Credenciais e segredos
.env
.env.*
!.env.example
*.pem
*.key
*.crt
*.p12
*.pfx
*.jks
*.keystore
id_rsa*
id_ed25519*
secrets.json
credentials.json
*.secret
appsettings.*.local.json
application-local.properties
application-secrets.properties
```
`.env.*` já cobre o `.env.local` e o `.env.*.local` que estavam no entregas e no workspace.

Regras globais: tudo o que não conflita, ou seja, os padrões sem `/` inicial e sem negação.

Regras com prefixo do subprojeto (uma regra solta na raiz mudaria de significado ou apagaria arquivos versionados):
- `/` inicial do frontend (`/.idea`, `/coverage`, `/.sass-cache`, `/connect.lock`, `/libpeerconnection.log`, `/typings`) → `/rotalog-frontend/...`
- `.vscode`: `/rotalog-frontend/.vscode/*` mais as 4 negações `!/rotalog-frontend/.vscode/{settings,tasks,launch,extensions}.json`. O `.vscode/` inteiro é ignorado apenas em entregas, frotas e workspace.
- frotas: `!.mvn/wrapper/maven-wrapper.jar`, `!**/src/main/**/target/` e o resto das negações e os caminhos `/nbproject/private/`, `/dist/` etc. → `/rotalog-api-frotas/...`
- workspace: `package-lock.json`, `*.jar`, `*.war` → `/rotalog-workspace/...`
- entregas: `package-lock.json`, `yarn.lock`, `pnpm-lock.yaml`, `__snapshots__/`, `*.seed` → `/rotalog-api-entregas/...`
- Os comentários FIXME sobre o `.env` saem, porque agora ele é ignorado. O FIXME do `appsettings.json` fica como lembrete de dívida técnica.

Os `.gitignore` dos subprojetos ficam onde estão.

### 4. Recriar o repositório
- Remover `rotalog-*/.git` (todos os 5).
- `git init -b main` na raiz e depois `git add -A`.
- Comparar `git ls-files` com as listas do passo 1. O que faltar (arquivos ignorados pelos `.gitignore` internos, mas versionados antes) entra com `git add -f`. **Nunca forçar arquivos que casam com a seção Credenciais.**
- Confirmar que nenhuma pasta `node_modules`, `target`, `bin`, `obj`, `dist`, `.nx` ou `.angular` foi incluída e que nenhum `.env` está no índice.

### 5. Commit
Mensagem em pt-BR, montada a partir do txt:
```
chore: unificar subprojetos do RotaLog em um único repositório

Consolida rotalog-api-entregas, rotalog-api-frotas, rotalog-api-notificacoes,
rotalog-frontend e rotalog-workspace (históricos anteriores resumidos abaixo).
O .env e outros arquivos de credencial passam a ser ignorados.

- api-entregas: <resumo: setup Express/Sequelize, rotas CRUD/rastreamento, auth JWT, ADR 0001 fases 1-3...>
- api-frotas: <resumo: Spring Boot, CRUD veículos/motoristas/manutenção, Flyway, refactor VeiculoService, alerta preventivo, observabilidade...>
- api-notificacoes: <...>
- frontend: <...>
- workspace: <...>

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```
O commit usa o autor do `git config` atual. Não faço push e não configuro remote, porque o usuário ainda não criou o repositório no GitHub.

## Credenciais que continuam versionadas (decisão do usuário)
Ficam no repo como dívida técnica conhecida e são listadas no resumo final:
- `rotalog-api-notificacoes/appsettings.json`: senha do banco, `SenderPassword`, `ApiKey` de SMS
- `rotalog-api-frotas/src/main/resources/application.properties`: `spring.datasource.password`
- `rotalog-workspace/docker-compose.yml`: `POSTGRES_PASSWORD`
- valores hardcoded em código ou documentação: fallback em `rotalog-api-entregas/src/config/database.js` e `README.txt`
- Os segredos de teste em `rotalog-api-entregas/test/**` são fictícios, então não exigem nada.

O `.env` do entregas continua no histórico dos repositórios antigos (`alura-cursos/rotalog-api-entregas`). Isso não afeta o novo repo, que começa com um único commit.

## Verificação
- `find rotalog -name .git` encontra só `rotalog/.git`.
- `git log` mostra 1 commit e `git status` sai limpo, exceto `historico-commits.txt`, que está excluído.
- O diff entre as listas do passo 1 e `git ls-files` sai vazio.
- `git ls-files | grep -E '(^|/)\.env'` não retorna nada.
- `git check-ignore -v` em alguns casos:
  - ignorados: `rotalog-api-entregas/.env` e `rotalog-frontend/node_modules/x`
  - versionados: `rotalog-frontend/.vscode/extensions.json`, `rotalog-frontend/package-lock.json` e `rotalog-api-entregas/package-lock.json`
