---

name: java-frotas

description: Especialista no serviço Java/Sprint Boot de Frotas. Usar quando o trabalho envolver o repositório rotalog-api-frotas, incluindo veículos, motoristas, e manutenção.

model: haiku

tools: Read, Write, Edit, Bash, Glob, Grep

---

## Stack
- Java 11, Sprint boot 2.7, Spring Data JPA, Hibernate
- Banco: PostgreSQL
- Migrations: Flyway

## Estrutura de pastas 
- controller -> service -> repository

## Convenções
- Naming: PascalCase para classes, camelCase para métodos e variáveis
- Logging: SLF4j Logger (nunca utilizar System.out.println)
- Testes: JUnit5 + mockito

## Endpoints (28 de negócio + 1 de health)

### Veículos (`/veiculos`) — VeiculoController
- GET `/veiculos`
- GET `/veiculos/{id}`
- GET `/veiculos/placa/{placa}`
- GET `/veiculos/status/{status}`
- POST `/veiculos`
- PUT `/veiculos/{id}`
- PATCH `/veiculos/{id}/quilometragem`
- PATCH `/veiculos/{id}/desativar`
- PATCH `/veiculos/{id}/reativar`
- GET `/veiculos/estatisticas`

### Manutenções (`/manutencoes`) — ManutencaoController
- GET `/manutencoes`
- GET `/manutencoes/{id}`
- GET `/manutencoes/veiculo/{veiculoId}`
- GET `/manutencoes/pendentes`
- GET `/manutencoes/veiculo/{veiculoId}/ultima`
- POST `/manutencoes`
- PATCH `/manutencoes/{id}/iniciar`
- PATCH `/manutencoes/{id}/concluir`
- PATCH `/manutencoes/{id}/cancelar`

### Motoristas (`/motoristas`) — MotoristaController
- GET `/motoristas`
- GET `/motoristas/{id}`
- GET `/motoristas/cnh/{cnh}`
- GET `/motoristas/status/{status}`
- GET `/motoristas/cnh-vencida`
- POST `/motoristas`
- PUT `/motoristas/{id}`
- PATCH `/motoristas/{id}/desativar`
- PATCH `/motoristas/{id}/reativar`

### Health
- GET `/health` — HealthCheckController