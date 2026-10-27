# RotaLog API Notificações

Microsserviço de notificações — envio de emails e SMS transacionais.

## Stack

- .NET Core 6
- Entity Framework Core
- MediatR
- PostgreSQL (schema `notificacoes`)

## Como rodar

Pré-requisito: PostgreSQL rodando via `rotalog-workspace`.

```bash
dotnet restore
dotnet run
```

A API sobe na porta **5000**.

## Estrutura do projeto

O projeto segue uma estrutura inspirada em Clean Architecture:

```
├── Domain/            # Entidades e interfaces
├── Application/       # Handlers e lógica de aplicação
├── Infrastructure/    # Acesso a dados, serviços externos
├── Controllers/       # Endpoints REST
└── Program.cs
```

## Endpoints principais

| Método | Rota                        | Descrição                      |
|--------|-----------------------------|--------------------------------|
| POST   | /api/notificacoes/email     | Enviar notificação por email   |
| POST   | /api/notificacoes/sms       | Enviar notificação por SMS     |
| GET    | /api/notificacoes/historico | Histórico de envios            |
| POST   | /api/notificacoes/alertas-manutencao | Enviar o alerta de manutenção preventiva por e-mail (retorna `ENVIADA` ou `FALHA`) |
| GET    | /health                     | Health check do serviço        |

### Alerta de manutenção preventiva

`POST /api/notificacoes/alertas-manutencao` (chamado pela api-frotas):

```json
{ "veiculoId": 1, "placa": "ABC1D23", "motivo": "KM", "kmDesdeUltimaPreventiva": 60000,
  "dataReferencia": "2026-08-16T10:00:00", "emailGestor": "gestor@rotalog.com" }
```

Responde `200` com `{ "notificacaoId": 1, "status": "ENVIADA" | "FALHA", "mensagemErro": "..." }`, inclusive
quando o envio falha; `400` se faltar `veiculoId`, `placa`, `motivo` ou `emailGestor`, ou se `motivo` não for `KM`, `TEMPO` ou `KM_E_TEMPO`. A mensagem usa o
template `ALERTA_MANUTENCAO_PREVENTIVA` (e-mail) ou um texto padrão. O histórico fica com
`tipo = ALERTA_MANUTENCAO` e `servicoOrigem = api-frotas`.

O envio de e-mail é simulado (`IEnvioEmail`) e falha em 10% das vezes; para desligar a falha
aleatória (E2E), use `EmailSettings__SimulatedFailureRate=0`.

## Testes

```bash
dotnet test rotalog-api-notificacoes.sln
```

Projeto de testes (xUnit): `tests/Notificacoes.Tests`.
