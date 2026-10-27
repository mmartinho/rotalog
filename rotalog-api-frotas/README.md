# RotaLog API Frotas

Microsserviço de gestão de frotas — veículos, motoristas e manutenções.

## Stack

- Java 11
- Spring Boot 2.7
- Spring Data JPA / Hibernate
- Flyway (migrations)
- PostgreSQL (schema `frotas`)

## Como rodar

Pré-requisito: PostgreSQL rodando via `rotalog-workspace`.

```bash
./mvnw spring-boot:run
```

A API sobe na porta **8080**.

### Variáveis de ambiente

| Variável                 | Obrigatória | Padrão                  | Descrição                                                        |
|--------------------------|-------------|-------------------------|------------------------------------------------------------------|
| `ROTALOG_EMAIL_GESTOR`   | Não         | `gestor@rotalog.com`    | E-mail do gestor que recebe os alertas de manutenção preventiva. A aplicação não sobe se o valor ficar vazio. |
| `ROTALOG_NOTIFICACOES_URL` | Não       | `http://localhost:5000` | URL da api-notificacoes                                          |

Para usar outro e-mail:

```bash
ROTALOG_EMAIL_GESTOR=outro@rotalog.com ./mvnw spring-boot:run
```

Outras propriedades (`application.properties`): `rotalog.alertas.limite-km` (50000),
`rotalog.alertas.intervalo-meses` (6), `rotalog.notificacoes.connect-timeout-ms` (2000) e
`rotalog.notificacoes.read-timeout-ms` (5000).

## Estrutura do projeto

```
src/main/java/com/rotalog/frotas/
├── controller/       # Endpoints REST
├── service/          # Regras de negócio
├── repository/       # Acesso a dados (Spring Data)
├── model/            # Entidades JPA
└── config/           # Configurações
```

## Endpoints principais

| Método | Rota                       | Descrição                    |
|--------|----------------------------|------------------------------|
| GET    | /api/veiculos              | Listar veículos              |
| GET    | /api/veiculos/{id}         | Buscar veículo por ID        |
| POST   | /api/veiculos              | Cadastrar veículo            |
| GET    | /api/motoristas            | Listar motoristas            |
| GET    | /api/manutencoes           | Listar manutenções           |
| POST   | /api/manutencoes/alertas/verificar | Verificar veículos que precisam de preventiva e enviar os alertas |
| GET    | /api/manutencoes/alertas?status=   | Listar alertas (filtro opcional: ENVIADA, FALHA, PENDENTE) |

### Alerta de manutenção preventiva

`POST /api/manutencoes/alertas/verificar` avalia os veículos `ATIVO`. É elegível o veículo com
**km desde a última preventiva concluída > 50000** ou **última preventiva concluída há mais de 6 meses**
(sem preventiva: km total e `dataCadastro`). Para cada elegível, chama a api-notificacoes e grava o
alerta em `frotas.alertas_manutencao` com o status: `ENVIADA`, `FALHA` ou `PENDENTE` (api-notificacoes
fora do ar). A chamada é idempotente: veículo com alerta `ENVIADA` ou `PENDENTE` posterior à sua
última preventiva não gera novo alerta. Alertas `PENDENTE` não são reenviados (fora do escopo).

## Testes

```bash
./mvnw test
```

Unitários (JUnit 5 + Mockito) e integração em `src/test/java/com/rotalog/integration/`, que usa
Testcontainers com PostgreSQL 14 (precisa de Docker; sem Docker a classe é ignorada). O schema vem das
migrations `V1` e `V3` e é validado pelo Hibernate (`ddl-auto=validate`).

## Migrations

Flyway em `src/main/resources/db/migration/`. Seguir a sequência existente ao
adicionar novas migrations.

O Flyway está desabilitado e o schema vem dos scripts do `rotalog-workspace`
(`tools/scripts/09-alertas-manutencao.sql` cria `frotas.alertas_manutencao`; o `V3__Alertas_Manutencao.sql`
é o espelho dele). Com `ddl-auto=validate`, a tabela precisa existir antes de a API subir.
Os scripts só rodam na criação do volume: em ambiente existente, rode
`docker-compose down -v && docker-compose up -d postgres` ou execute o script `09` (e o `10`, de seed) na mão.
