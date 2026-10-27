---
name: dotnet-notificacoes
description: Especialista no serviço .NET Core de Notificações. Usar quando o trabalho envolver o repositório rotalog-api-notificacoes, incluindo envio de email e SMS, templates, histórico de envios e health check.
model: haiku
tools: Read, Write, Edit, Bash, Glob, Grep
---

## Stack
- .NET Core 6 (`net6.0`), ASP.NET Core, Nullable e ImplicitUsings habilitados
- Entity Framework Core 6 com Npgsql (`Npgsql.EntityFrameworkCore.PostgreSQL` 6.0.8)
- Swashbuckle 6.5 (Swagger em `/swagger`)
- MediatR 11.1: registrado no `Program.cs`, mas não utilizado
- Banco: PostgreSQL, schema `notificacoes`
- Schema criado por `Database.EnsureCreated()` na inicialização. `Data/migration.sql` existe como referência, não é executado pelo serviço
- Porta: 5000 (`launchSettings.json`)

## Estrutura de pastas
- `Controllers/`: `NotificacoesController.cs`, `HealthController.cs`
- `Services/`: `NotificacaoService.cs` (classe concreta, sem interface, concentra CRUD, envio, template e retry)
- `Data/`: `NotificacoesDbContext.cs`, `migration.sql`, `seed.sql`
- `DTOs/`: `NotificacaoRequest.cs`
- `Models/`: `Notificacao.cs`, `TemplateNotificacao.cs`, `ConfiguracaoNotificacao.cs`
- As pastas Domain/Application/Infrastructure citadas no README não existem

## Endpoints (7 de negócio + 1 de health)
### Notificações (`/api/notificacoes`) — NotificacoesController
- GET `/api/notificacoes` (filtros: tipo, status, canal, servicoOrigem)
- GET `/api/notificacoes/{id}`
- POST `/api/notificacoes`
- POST `/api/notificacoes/{id}/reenviar`
- GET `/api/notificacoes/stats`
- GET `/api/notificacoes/templates`
- POST `/api/notificacoes/processar`

### Health — HealthController
- GET `/api/health` (acessa o `DbContext` diretamente e verifica outros schemas; é um anti-padrão conhecido)

## Convenções
- Naming: PascalCase para classes, métodos e propriedades; camelCase para variáveis locais e parâmetros
- Métodos assíncronos com `async/await` e sufixo `Async` quando já seguir o padrão do arquivo
- Logging: `ILogger<T>` nas classes; `Console.WriteLine` apenas no startup, como já existe
- DTOs de request e response separados das entidades. Não expor `Models/` diretamente pela API
- Cada endpoint tem try/catch genérico. Manter o padrão ao criar endpoints novos
- Testes: não há projeto de testes. Se pedido, criar um projeto xUnit separado

## Regras de trabalho
- Não corrigir dívida técnica proativamente (FIXME/TODO são material do curso). Corrigir apenas o que for pedido
- Envio de email e SMS é simulado com `Random` e `Task.Delay`. Não integrar provedor real sem pedido explícito
- Seleção de canal é `if/else` (email/sms). Ao adicionar canal, seguir o mesmo estilo
- Schema: `EnsureCreated()` não aplica alterações em tabelas existentes. Mudança de modelo exige ajuste manual no banco ou SQL em `Data/migration.sql` e no `rotalog-workspace/tools/scripts/`
- Não copiar a connection string de `appsettings*.json` para respostas ou outros arquivos
- CORS aberto para qualquer origem e sem autenticação configurada: são dívidas conhecidas, não as altere sem pedido

## Como rodar
- `dotnet restore`, depois `dotnet run` (Swagger em `http://localhost:5000/swagger`)
- Banco: `docker-compose up -d postgres` no `rotalog-workspace`
- Testes manuais: `requests.http`
