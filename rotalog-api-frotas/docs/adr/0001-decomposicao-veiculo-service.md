# ADR 0001: Decomposição do VeiculoService e tratamento de erros específico

- **Status:** Proposta
- **Data:** 2026-10-14
- **Escopo:** `rotalog-api-frotas`, classe `com.rotalog.service.VeiculoService`
- **Origem:** item de dívida "VeiculoService.java com 339 linhas" do `MAPA_DIVIDA_TECNICA.md`, e os problemas listados abaixo.

## Contexto

O `VeiculoService` concentra, em uma única classe de 339 linhas, cinco responsabilidades: CRUD e consultas de veículo, validação de dados, notificação ao gestor, regras de manutenção preventiva (limite de quilometragem, custo, agendamento) e estatísticas da frota. A classe não tem testes de cobertura relevante e carrega 56 comentários `FIXME`/`TODO`.

A análise do código original (`HEAD`) identificou os seguintes problemas:

| # | Problema | Evidência (HEAD) | Risco |
|---|---|---|---|
| 1 | Múltiplas responsabilidades em uma classe só | Classe inteira, 339 linhas | Dificulta testes, leitura e alteração isolada |
| 2 | Exceções genéricas `RuntimeException` para erros de negócio | Linhas 49, 57, 70, 74, 80, 85, 90, 160, 196 | Controllers não distinguem tipos de erro; `catch (RuntimeException)` captura falhas de programação também |
| 3 | Status escritos como string solta (`"ATIVO"`, `"INATIVO"`, `"MANUTENCAO"`) | Linhas 98, 195-196, 266, 291, 307-309 | Erro de digitação passa em silêncio; lista de valores válidos duplicada |
| 4 | Tipos de notificação e e-mail do gestor como string | Linhas 110, 222, 276 (`"gestor@rotalog.com"`) | Mudança de destinatário exige editar quatro pontos |
| 5 | Bloco `try/catch` de notificação copiado quatro vezes, engolindo exceção | Linhas 113-116, 180-183, 225-228, 279-282 | Duplicação; a falha de notificação some do fluxo |
| 6 | `System.out.println` no lugar de logger | Linhas 116, 164, 327 | Saída fora do SLF4j, sem nível, sem controle em produção |
| 7 | Injeção por campo com `@Autowired` | Linhas 27, 30 | Dependências ocultas, difícil de testar sem Spring |
| 8 | Variáveis não utilizadas | Linhas 212-213 (`intervaloQuilometragem`, `intervaloMeses`) e parâmetro `modelo` em `calcularCustoManutencao` (linha 236) | Ruído e intenção enganosa |
| 9 | Código morto: método vazio com `println` | `sincronizarComSistemaExterno()`, linhas 324-333 | Sem chamadores; só registra log e não faz nada |
| 10 | **Bug:** checagem de placa duplicada sem normalizar | Linha 78 consulta `placa` original; linha 95 grava `placa.toUpperCase()` | Uma placa em minúsculas passa pela checagem e colide com o `UNIQUE` do banco |
| 11 | `status.equals(...)` sem proteção contra nulo | Linha 195 | `NullPointerException` quando o status é nulo |
| 12 | Limites e custos fixos no código | Linhas 238-239 (custos), 253 (limite de 50.000 km) | Mudança de regra exige recompilar |
| 13 | Javadoc e comentários de FIXME em excesso | 56 ocorrências de `FIXME`/`TODO` na classe | Ruído que obscurece o código |

Observação sobre o mapa de dívidas: o `MAPA_DIVIDA_TECNICA.md` afirma que o módulo não tem nenhum teste. Os demais achados do mapa para este serviço (`System.out`, tamanho da classe, typo em `obterEstatisticasFreita`) foram confirmados.

## Decisão

Propõe-se dividir a classe em quatro componentes, cada um com uma responsabilidade:

### 1. Divisão de responsabilidades

| Classe | Responsabilidade |
|---|---|
| `VeiculoService` | Orquestrará CRUD, consultas, ativação/desativação e estatísticas. Não conterá regra de validação nem de notificação. |
| `VeiculoValidator` | Validará placa, modelo, ano de fabricação, quilometragem e status. Devolverá a placa normalizada e o `StatusVeiculo`. |
| `VeiculoNotificador` | Enviará notificações ao gestor. Concentrará o destinatário e o tratamento de falha (log, sem propagar). |
| `ManutencaoPreventivaService` | Conterá a regra de manutenção preventiva: limite de quilometragem, agendamento e cálculo de custo. |

Nesta etapa (problema 1), `VeiculoService` passa de 339 para 239 linhas. A meta de cerca de 145 linhas depende da remoção de comentários, stubs e código morto (problemas 5, 9 e 13), que ficam para as próximas etapas.

### 2. Exceções específicas

| Exceção | Base | Quando será lançada |
|---|---|---|
| `VeiculoNaoEncontradoException` | `RecursoNaoEncontradoException` | Veículo não existir (por id ou por placa) |
| `VeiculoInvalidoException` | `RuntimeException` | Base das violações de validação e regra de negócio |
| `PlacaInvalidaException` | `VeiculoInvalidoException` | Placa vazia ou diferente de 7 caracteres |
| `PlacaDuplicadaException` | `VeiculoInvalidoException` | Placa já cadastrada |
| `ModeloInvalidoException` | `VeiculoInvalidoException` | Modelo ausente ou vazio |
| `AnoFabricacaoInvalidoException` | `VeiculoInvalidoException` | Ano nulo ou fora de 1900 a 2100 |
| `QuilometragemInvalidaException` | `VeiculoInvalidoException` | Quilometragem nula ou negativa |
| `StatusVeiculoInvalidoException` | `VeiculoInvalidoException` | Status que não existir no enum |

`VeiculoNaoEncontradoException` estenderá a `RecursoNaoEncontradoException`, que já existe no projeto. Assim, o `VeiculoController` continuará respondendo 404 para esse caso, sem alteração.

### 3. Enums

- `StatusVeiculo` (`ATIVO`, `INATIVO`, `MANUTENCAO`): toda transição e consulta de status passará a usar o enum. A string só aparecerá na fronteira com o banco (`.name()`), e a entrada do usuário será convertida por `VeiculoValidator.validarStatus`.
- `TipoNotificacao` (`NOVO_VEICULO`, `ALERTA_MANUTENCAO`, `MANUTENCAO_AGENDADA`, `VEICULO_DESATIVADO`): os nomes serão os mesmos que a API de notificações já recebe, então o contrato não muda.

### 4. Notificação centralizada

`VeiculoNotificador.notificar(TipoNotificacao, mensagem)` será o único ponto que chama `NotificacaoClient`. Ele definirá o destinatário e registrará a falha em log, sem interromper a operação de veículo. Os quatro blocos `try/catch` duplicados serão removidos.

### 5. Logs e comentários

- `System.out.println` será substituído por `log.warn` ou removido.
- Javadoc e FIXME/TODO serão removidos do código. As dívidas que continuarem abertas ficarão registradas nesta ADR, na seção "Fora do escopo".

### 6. Injeção por construtor

`VeiculoService`, `VeiculoNotificador` e `ManutencaoPreventivaService` usarão `@RequiredArgsConstructor` (Lombok), com campos `final`.

### 7. Mudanças de comportamento

Estas mudanças alterarão comportamento e precisarão ser conhecidas por quem consome a API:

- **Placa normalizada antes da checagem de duplicidade** (problema 10). Hoje, `abc1d23` passa pela checagem e falha só no banco. Com a mudança, `PlacaDuplicadaException` será lançada.
- **Status nulo** em `obterVeiculosPorStatus` passará a lançar `StatusVeiculoInvalidoException` em vez de `NullPointerException`.
- **Quilometragem nula** em `atualizarQuilometragem` passará a lançar `QuilometragemInvalidaException` em vez de `NullPointerException`.
- **`sincronizarComSistemaExterno()` será removido** (problema 9). Não há chamadores e o método é um stub vazio.
- **`precisaDeManutencao` e `calcularCustoManutencao` sairão de `VeiculoService`** e irão para `ManutencaoPreventivaService`. `precisaDeManutencao` passará a receber o `Veiculo` em vez do id, e `calcularCustoManutencao` perderá o parâmetro `modelo`, que não é usado. Nenhum código do repositório chama esses métodos fora do próprio service.

### 8. Testes de unidade

Cada classe terá testes de unidade com JUnit 5 e Mockito. Em `VeiculoServiceTest`, os colaboradores serão mockados; os demais componentes serão testados isoladamente.

Os testes de fronteira cobrirão os limites de ano (1900 e 2100 válidos; 1899 e 2101 inválidos) e a quilometragem de 50.000 km.

## Consequências

### Positivas

- Cada classe terá uma responsabilidade e poderá ser testada sem subir o Spring.
- Erros de negócio passarão a ser distinguíveis por tipo. O controller e um futuro `@ControllerAdvice` poderão mapear cada um para um status HTTP específico.
- Typo em status deixará de ser um bug silencioso: o compilador acusará o uso errado do enum.
- Falhas de notificação aparecerão no log com nível `ERROR`, e não mais via `System.out`.
- Meta de cobertura, medida com JaCoCo 0.8.12: **`VeiculoService` com 100% de cobertura de linha e de branch.**

### Negativas

- Quatro classes no lugar de uma: mais arquivos para navegar.
- `precisaDeManutencao` e `calcularCustoManutencao` mudarão de classe e de assinatura. Código externo ao repositório que dependa deles precisará ser atualizado.
- O `VeiculoController` continuará capturando `RuntimeException` (ver "Fora do escopo"). Os tipos novos ainda não serão usados para escolher o status HTTP.

## Alternativas consideradas

- **Manter a classe única e só limpar comentários e logs.** Descartada: resolve o ruído, mas não separa responsabilidades nem permite testar regras isoladamente.
- **Tornar `Veiculo.status` um enum na entidade JPA já nesta etapa.** Descartada: a mudança atinge `ManutencaoService` (que grava `"MANUTENCAO"` como string) e o repositório. Fica como próximo passo.
- **Criar `@ControllerAdvice` agora.** Descartada: mudaria o formato de erro de todos os controllers, e isso precisa ser decidido junto com os outros serviços.

## Fora do escopo (dívidas que permanecem)

1. **Controller ainda captura `RuntimeException`.** Mapear `VeiculoInvalidoException` para 400, `VeiculoNaoEncontradoException` para 404 e `PlacaDuplicadaException` para 409 via `@ControllerAdvice`.
2. **`Veiculo.status` continua `String` na entidade.** Migrar para `@Enumerated(EnumType.STRING)` quando `ManutencaoService` também usar o enum.
3. **Typo `obterEstatisticasFreita`** e estatísticas devolvidas como `String` JSON. O ideal é um DTO tipado. O nome será mantido para não quebrar o controller.
4. **Limites e custos fixos** (`LIMITE_QUILOMETRAGEM`, `CUSTO_BASE`, `CUSTO_POR_KM`) em `ManutencaoPreventivaService`. Devem vir de configuração.
5. **Quilometragem menor que a atual** só gera aviso em log. Falta decidir se deve ser rejeitada.
6. **`NotificacaoClient`** usa `RestTemplate` criado à mão, URL fixa em `localhost:5000` e sem timeout.
7. **`ManutencaoService` altera o status do veículo diretamente**, sem transação compartilhada.
8. **`Veiculo` usa `TABLE_PER_CLASS`**, estratégia que o próprio código marca como inadequada.
9. **Consultas sem paginação** em `listarTodos`.

## Acompanhamento dos problemas

Os 13 problemas da tabela de contexto são resolvidos um por vez, cada um com teste, atualização desta ADR e commit.

| # | Problema | Situação |
|---|---|---|
| 1 | Múltiplas responsabilidades em uma classe só | **Resolvido** (seção 1): validação, notificação e manutenção preventiva extraídas; `VeiculoService` delega para elas. Sem mudança de comportamento. |
| 2 | Exceções genéricas `RuntimeException` | Pendente |
| 3 | Status como string solta | Pendente |
| 4 | Tipos de notificação e e-mail do gestor como string | Pendente (e-mail já centralizado em `VeiculoNotificador`; tipos ainda são string) |
| 5 | `try/catch` de notificação copiado quatro vezes | Pendente (bloco já centralizado em `VeiculoNotificador`) |
| 6 | `System.out.println` no lugar de logger | Pendente |
| 7 | Injeção por campo com `@Autowired` | Pendente (as classes novas também usam `@Autowired`, para manter o padrão do arquivo) |
| 8 | Variáveis não utilizadas | Pendente |
| 9 | Código morto em `sincronizarComSistemaExterno()` | Pendente |
| 10 | Bug: checagem de placa duplicada sem normalizar | Pendente |
| 11 | `status.equals(...)` sem proteção contra nulo | Pendente |
| 12 | Limites e custos fixos no código | Pendente |
| 13 | Javadoc e FIXME/TODO em excesso | Pendente |

## Como verificar

```bash
cd rotalog-api-frotas
mvn -o test
mvn org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.12:report
# relatório: target/site/jacoco/index.html
```

Critério de aceite: todos os testes passando e `VeiculoService` com 100% de cobertura de linha e de branch. Testes de integração com o banco não serão executados nesta etapa.
