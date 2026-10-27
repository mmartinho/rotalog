***********************************************************************************************
AVA: cursos.alura.com.br
Formação: Carreira em 'AI-Native Software Engineering'
Curso: Engeharia de software na era da IA - Trabalhando com legado, escala e múltiplos serviços
Data Início: 12/10/2026
Data Final: 20/10/2026
***********************************************************************************************
Aula 01: Entendendo o projeto Legado 
  1.  Preparando o ambiente:
      > O projeto exige os seguintes pré-requisitos
        >> Docker e Docker Componse: já instalado de cursos anteriores
        >> Node.js (18+): já instalado, trabalho com a versão 22.13.0
        >> NX: Instalamos usando o comando:
            >>> npm install -g nx 
        >> Java 11 JDK: Instalamos baixando a versão 'Adoption'
            >>> @see https://adoptium.net/
            >>> JAVA_HOME automático
            >>> Instalamos em 
                >>>> D:\marcu\AppData\Local\Programs\Eclipse Adoptium\jdk-11.0.32.101-hotspot
        >> Maven: Instalamos baixando do site oficial
            >>> @see https://maven.apache.org/download.cgi
            >>> Seguindo as instruções em: @see https://maven.apache.org/install.html
        >> .NET 6 SDK: Instalamos baixando do site oficial
            >>> @see https://dotnet.microsoft.com/pt-br/download/dotnet/6.0
        >> Claude Code: já instalado de cursos anteriores 
      > Organizando o Workspace
        >> Criamos a pasta rotalog em:
            D:\wamp64\www\alura\Carreiras\IA\AI-Native Software Engineering\
            Engenharia de software na era da IA\Trabalhando com legado, escala e 
            múltiplos serviços\rotalog
        >> Clonamos os projetos nesta pasta nova:
            >>> git clone https://github.com/alura-cursos/rotalog-workspace.git
                git clone https://github.com/alura-cursos/rotalog-api-frotas.git
                git clone https://github.com/alura-cursos/rotalog-api-entregas.git
                git clone https://github.com/alura-cursos/rotalog-api-notificacoes.git
                git clone https://github.com/alura-cursos/rotalog-frontend.git
        >> O resultado ficou:
            >>> rotalog/
                ├── rotalog-api-entregas/
                ├── rotalog-api-frotas/
                ├── rotalog-api-notificacoes/
                ├── rotalog-frontend/
                ├── rotalog-workspace/
        >> Iniciamos o Docker Desktop
            >>> entramos em rotalog-workspace e executamos:
                >>>> docker-compose up -d postgres
      > Subimos cada API:
        >> API das Frotas (Java, porta 8080)
           >>> cd rotalog-api-frotas
           >>> mvn spring-boot:run
        >> API Entregas (Node, porta 3000):
           >>> cd rotalog-api-entregas
           >>> npm install
           >>> npm start
        >> API Notificações (.NET, porta 5000):
           >>> cd rotalog-api-notificacoes
           >>> dotnet nuget remove source nuget.org
           >>> dotnet nuget add source https://api.nuget.org/v3/index.json -n nuget.org
           >>> dotnet restore
           >>> dotnet run 
        >> Frontends
           >>> cd rotalog-frontend
           >>> npm install
           >>>> Rastreamento (3001)
                >>>>> npx nx serve rastreamento
           >>>> Painel ADMIN (4200)
                >>>>> npx nx serve painel-admin
  2.  O método: entender, ensinar, executar
      > Entender: Conhecer o sistema com os próprios olhos
      > Ensinar: Dar contexto para IA trabalhar com precisão
      > Executar: Refatorar, implementar e valida com IA
  3.  Rotalog: 5 Apss, 3 linguagens, zero docs
      > api-frotas (Java): God class 300+ linhas, naming pt/en, migrations com gaps 
      > api-entregas (Node): Callback hell, JWT hardcode, SQl injection
      > api-notifications (.NET): God Class, credenciais texto plano, SMS stub 
      > painel-admin (Angular): God Components, fetch() puro, sem HttpClient 
      > rastreamento (React): Class components, PropTypes, sem hooks
  4.  Rotalog: Arquitetura 4 camadas
      > 1. Usuários
            +-------------------------+     +-------------------------+
            |      Cliente Final      |     |     Gestor de Frota     |
            |     (Rastreamento)      |     |       (Back-office)     |
            +-------------------------+     +-------------------------+
                        |                               |
                        v                               v
        2. Micro Frontends
            +-------------------------+     +-------------------------+
            |       rastreamento      |     |       painel-admin      |
            |  React + Class Comps    |     |  Angular 18 + NgModule  |
            +-------------------------+     +-------------------------+
                        \                               /
                          +---------------+-------------+
                                          |
                                          v
        3. Microserviços
            +-----------------+     +-----------------+     +-----------------+
            |    api-frotas   |     |   api-entregas  |     | api-notificacoes|
            | Java + Spring   |     | Node + Express  |     |    .NET Core    |
            |    Boot 2.7     |     |       4.x       |     |        8        |
            +-----------------+     +-----------------+     +-----------------+
      
        4. Libs Compartilhadas
            +-----------------+     +-----------------+     +-----------------+
            |   shared-types  |     |  ui-components  |     |   api-contracts |
            |   TypeScript    |     |     Angular     |     |     OpenAPI     |
            +-----------------+     +-----------------+     +-----------------+
  5.  Conhecendo o Backend Rotalog 
      > Diversos 'FIXME', com falta de padrão, classes muito grandes, variáveis 'secret' dentro do código, 
        QUERY SQL em strings, práticas ruins 
  6.  Conhecendo o Frontend Rotalog (monorepo)
      > São duas apps: 
        >> App Angular 'painel-admin' 
            >>> Diversos ploblemas: Componentes Angular contendo estilos e layout tudo junto e muito grandes, 
                Injeção direta no constructor, sem abstrações, mensagens de erros hardcoded, componentes que 
                deveriam ser reutilizáveis estão se repetindo, etc.
        >> App React 'rastreamento'
            >>> Um arquivo só com tudo, chamadas de API sem variáveis de ambiente, etc.
  7.  Vamos executar toda a infraestrutura de software:
      > O Docker com o BD Postgress
      > cd rotalog-api-frotas
        >> mvn spring-boot:run
      > cd rotalog-api-entregas
        >> npm start
      > cd rotalog-api-notificacoes
        >> dotnet run
      > cd rotalog-frontend
        >> npx nx serve rastreamento
      > cd rotalog-frontend 
        >> npx nx serve painel-admin 
  8.  No Prompt do Chat do Claude, modelo Haiku 5.5, em Auto, executamos:
      > Liste os repositórios que você tem acesso nesse Workspace
        >> Encontrei **5 repositórios Git** clonados dentro do workspace `rotalog`. Todos apontam 
            para a organização `alura-cursos` no GitHub e estão na branch `main`:
              | ---------------------------|--------------------------------------------------------------|
              | Repositório                | Remote (origin)                                              |
              | ---------------------------|--------------------------------------------------------------|
              | `rotalog-api-entregas`     | https://github.com/alura-cursos/rotalog-api-entregas.git     |
              | `rotalog-api-frotas`       | https://github.com/alura-cursos/rotalog-api-frotas.git       |
              | `rotalog-api-notificacoes` | https://github.com/alura-cursos/rotalog-api-notificacoes.git |
              | `rotalog-frontend`         | https://github.com/alura-cursos/rotalog-frontend.git         |
              | `rotalog-workspace`        | https://github.com/alura-cursos/rotalog-workspace.git        |
              | ---------------------------|--------------------------------------------------------------|
            Não tenho acesso a uma API do GitHub nesta sessão. O CLI `gh` não está instalado, então 
            a lista acima vem só dos clones locais. Se você tiver outros repositórios na conta ou na 
            organização que não foram clonados, eles não aparecem aqui. Para listá-los, instale o `gh`
            e faça `gh auth login`, e eu rodo `gh repo list alura-cursos`.
  9.  No Prompt do Chat do Claude, modelo Haiku 5.5, em Auto, executamos: 
      > Agora que você identificou os repositórios locais do Workspace, identifique as 
        linguagens de programação que estão em cada um deles
        >> Contei os arquivos de código-fonte de cada repositório por extensão, excluindo 
            dependências (`node_modules`), caches e saídas de build (`bin`, `target`, `.angular`).
            É uma estimativa pela extensão dos arquivos, não uma análise completa.

              | Repositório                | Linguagens                                             |
              |----------------------------|--------------------------------------------------------|
              | `rotalog-api-entregas`     | **JavaScript**                                         | 
              | `rotalog-api-frotas`       | **Java**                                               | 
              | `rotalog-api-notificacoes` | **C#**                                                 | 
              | `rotalog-frontend`         | **TypeScript** (principal), **JavaScript**, HTML, CSS  | 
              | `rotalog-workspace`        | **SQL** (scripts), YAML, Markdown | 8 `.sql`, 1 `.yml` |  
              
              Os números de `.js` do `rotalog-api-entregas` somam os 12 arquivos encontrados 
              fora de `node_modules`. Não verifiquei manifestos como `package.json`, `pom.xml` e `.csproj`, 
              então as versões e os frameworks são só inferências a partir de arquivos de build e caches. 
              Se você quiser, posso ler esses manifestos para confirmar as versões.
  10. No Prompt do Chat do Claude, modelo Haiku 5.5, em Auto, executamos:
      > Analise cada projeto do Workspace e gere um mapa de dívidas técnicas em markdown:
        - Problemas de Arquitetura;
        - Inconsistências no código;
        - Vulnerabilidades de segurança;
        - Estado de pedendências;
        - Qualidade de infraestrutura
        Organize por projeto as dívidas técnicas encontradas.

Aula 02: Navegando no Workspace com a IA   
  1.  Criamos um novo arquivo na raiz do rotalog, iniciando as 'primeiras linhas' do markdown de orientação 
      ao Claude Code:
      @see CLAUDE.md
  2.  No Prompt do Chat do Claude, modelo Haiku 5.5, em Auto, executamos:
      > Criei um arquivo @CLAUDE.md na raiz do workspace, seguindo os padrões das primeiras linhas utilizadas 
        para mostrar a visão geral e o primeiro repositório rotalog-api-frotas, continue para os demais 
        repositórios disponibilizados neste workspace, salvando o arquivo
  3.  No Prompt do Chat do Claude, modelo Haiku 5.5, em Auto, executamos:
      > Acrescente, a cada respositório, os padrões de projeto utilizados, por exemplo:
        ...
        - Padrões: REST com 8 endpoints, MediatR configurado (não utilizado), Swagger habilitado ...
        ...
  4.  Criando a estrutura inicial para os 'Subagentes'
      > Criamos o diretório e '.claude/agents'
        @see .claude\agents
      > Criamos o arquivo java-frotas.md, com as sessões 'stack', 'estrutura de pastas' e 'convenções'
        @see .claude\agents\java-frotas.md
  5.  No Prompt do Chat do Claude, modelo Haiku 5.5, em Auto, executamos:
      > @.claude/agents/java-frotas.md  Quantos end-points existem?
        >> Existem 28 endpoints de negócio no rotalog-api-frotas, além de 1 de health check, totalizando 29. 
          Contei as anotações @GetMapping, @PostMapping, @PutMapping e @PatchMapping nos controllers:  
          Controller	         Base	         Endpoints
          VeiculoController	   /veiculos	   10
          ManutencaoController	/manutencoes	9
          MotoristaController	/motoristas	   9
          HealthCheckController	/health	      1 (fora da contagem de negócio)
          Isso bate com o CLAUDE.md do projeto. O agente java-frotas não lista endpoints, só descreve a stack 
          e as convenções.     
  6.  No Prompt do Chat do Claude, modelo Haiku 5.5, em Auto, executamos: 
      > Crie um especialista para cada repositório existente no Workspace. Para isso, utilize como exemplo 
        @.claude/agents/java-frotas.md e crie essa estrturura adequada para cada projeto aqui
        >> Criei quatro especialistas novos em .claude/agents/, seguindo a estrutura do java-frotas.md:
            - node-entregas.md: rotalog-api-entregas (Node/Express, Sequelize, proxy para frotas)
            - dotnet-notificacoes.md: rotalog-api-notificacoes (.NET 6, EF Core, envio simulado)
            - frontend-rotalog.md: rotalog-frontend (Nx, Angular 18 no painel-admin, React no rastreamento)
            - workspace-rotalog.md: rotalog-workspace (Docker Compose, PostgreSQL, scripts de init e seeds)
        @see .claude\agents
  7.  Um Subagente:
      > Mitiga a mistura de padrões: cada especialista só enxerga as regras do 'seu mundo'
      > Preservar o contexto: mantendo a exploração e a implementação fora da sua conversa principal
      > Aplicar restrições: limitando quais ferramentas um subagente pode usar
      > Reutilizar configurações: entre projetos com subagentes em nível de usuário
      > Especializar o comportamento: utilizando prompts de sistema focados em domínios específicos
      > Controlar custos: direcionando tarefas para modelos mais rápidos e de menor custo, como o Haiku
  8.  A Anthropic sugere: se a tarefa envolver explorar dez ou mais arquivos, ou se envolver três ou mais 
      frentes independentes de trabalho, vale a pena usar subagente. 
      E há cenários onde eles brilham ainda mais, como rodar análises em paralelo. 
  9.  No Prompt do Chat do Claude, modelo Haiku 5.5, em Auto, executamos: 
      > @.claude/agents/java-frotas.md Gere um endpoint GET api/veiculos/:id
  10. No Prompt do Chat do Claude, modelo Haiku 5.5, em Auto, executamos:     
      > Crie o teste, e melhore o lançamento de erro, pra quando o veículo não for encotrado. 
        Seria interessante que a classe de Exception, pudesse ser reaproveitada por outros métodos de outros 
        serviços.

Aula 03: Diagnóstico assistido por IA 
  1.  ADR: Arquitetural Decision Record é a memória do Projeto 
      > Título: Nome da decisão técnica 
      > Status: Proposto, Aceito ou Substituído
      > Contexto: Estado atual e problemas identificados
      > Decisão: O que foi decidido e por quê
      > Alternativas: O que foi considerado antes 
      > Consequências: O que melhora, o que pode dar errado 
  2.  No Prompt do Chat do Claude, modelo Haiku 5.5, em Auto, executamos:
      > @.claude/agents/java-frotas.md Preciso que você tome base o arquivo @MAPA_DIVIDA_TECNICA.md e 
        crie uma ADR apontando os possíveis problemas que a classe @rotalog-api-frotas/src/main/java/com/rotalog/
        service/VeiculoService.java pode ter. Além disso, vi alguns pontos de melhoria que precisam ser 
        considerados:
        1- A classe está muito grande, mas de 300 linhas, seria interessante dividir as responsabilidades 
           contidas nessa classe, criando outras classes auxiliares ou outros services para dividir essa 
           responsabilidade;
        2- A classe tem diversas Exceptions genérica, seria interessante criamos classes de Exceptions 
           específicas para cada tipo de erro que venha  a ocorrer;
        3- Preciso que a classe tenha cobertura de teste acima de 90%, utilize o teste de unidade;
        4- Remova o JAVADOC em excesso;
        5- Remova os logs com system.out.println;
        6- Crie enums para evitar strings de mudança de status;
        7- Remover as variávei que não estão sendo utilizadas.
           ATENÇÃO: Gere apenas a ADR. Não realize nenhuma modificação no projeto ainda. Aguarde a revisão.
        >> O claude gerou @see rotalog-api-frotas\docs\adr\0001-decomposicao-veiculo-service.md
  3.  No Prompt do Chat do Claude, modelo Haiku 5.5, em Auto, executamos:
      > @.claude/agents/node-entregas.md  Analise o arquivo @rotalog-api-entregas/src/middleware/auth.js  
        pois ele contém problemas de vulnerabilidades, como JWT secret hardcoded entre outros problemas. 
        Para isso consulte o arquivo @MAPA_DIVIDA_TECNICA.md , pois nele há outros problemas identificados 
        e preciso que você faça essa análise e identifique problemas de arquitetura, vulnerabilidade e 
        responsabilidade. Além disso, analise o arquivo @rotalog-api-entregas/src/routes/entregas.js  
        pois ele deveria ser apenas um arquivo de rotas, mas há nele consultas SQL, logs com console.log e 
        comentários em excesso. Não implemente nada ainda, gere apenas uma ADR semelhante ao que foi 
        gerada em @rotalog-api-frotas/docs/adr/0001-decomposicao-veiculo-service.md
        >> O claude gerou @see rotalog-api-entregas\docs\adr\0001-saneamento-auth-e-camadas-entregas.md
  4.  No Prompt do Chat do Claude, modelo Haiku 5.5, em Auto, executamos:
      > @.claude/agents/frontend-rotalog.md  Compare o painel-admin (Angular 18) com as boas práticas de 
        Angular na atualidade. Liste as modernizações mais impactantes, ordene-as por esforço x benefícios. 
        Considere todos os componentes do projeto (dashboard, entregas, layout, manutencoes, motoristas e 
        veiculos). Verifique se as chamadas HTTP estão utilizando o HttpClient. Verifique também a 
        possibilidade de utilizar o Pattern do Lazy Loading para os componentes do projeto. Não implemente 
        nada ainda, gere apenas uma ADR semelhante ao que foi gerada em 
        @rotalog-api-entregas/docs/adr/0001-saneamento-auth-e-camadas-entregas.md. 
        >> O claude gerou @see rotalog-frontend\docs\adr\0001-modernizacao-painel-admin.md
  5.  No Prompt do Chat do Claude, modelo Haiku 5.5, em Auto, executamos:
      > @.claude/agents/frontend-rotalog.md Analise o app ratreamento (React 18) e identifique os seguintes 
        problemas:
        - Qual o impacto para atualizar a versão 18 para a versão mais atual;
        - Class component vs functional components com hooks e o que ganharíamos gazendo essa migração;
        - PropTypes x Typescript: Mostre-me quais os riscos;
        - Ordener as modernizações por esforço x benefício.
        Não implemente nada ainda, gere apenas uma ADR semelhante ao que foi gerada em 
        @rotalog-api-entregas/docs/adr/0001-saneamento-auth-e-camadas-entregas.md.
        >> O claude gerou @see rotalog-frontend\docs\adr\0002-modernizacao-rastreamento-react.md
  6.  No Prompt do Chat do Claude, modelo Haiku 5.5, em Auto, executamos
      > @.claude/agents/frontend-rotalog.md Analise as libs shared-types e api-contracts no monorepo 
        frontend. Compare as interfaces do shared-types com os contratos reais dos 3 backends. Identifique 
        campos faltando, tipos divergentes e, por fim, endpoints não documentados. Não implemente nada 
        ainda, gere apenas uma ADR semelhante ao que foi gerada em 
        @rotalog-api-entregas/docs/adr/0001-saneamento-auth-e-camadas-entregas.md
        >> O claude gerou @see rotalog-frontend\docs\adr\0003-contratos-shared-types-e-openapi.md

Aula 04: Refatoração segura com IA 
  1.  Um mudança por vez
      > Não misturar refatoração de naming com extração de classes 
  2.  Teste antes, teste depois 
      > Se não tem tete, criar antes de mexer
  3.  Commit a cada passo estável
      > Se deu ruim, volta pro commit anterior 
  4.  A IA propõe, você valida 
      > Nunca aceita sem revisar 
  5.  No Prompt do Chat do Claude, modelo Haiku 5.5, em Auto, executamos:
      > @.claude/agents/java-frotas.md  Crie uma branch a partir de agora e chame-a de 'aula-4'
        >> O claude criou uma branch 'aula-4' em todos os projetos
  6.  No Prompt do Chat do Claude, modelo Haiku 5.5, em Auto, executamos:
      > Desculpe-me, você tem razão. Serei mais claro da próxima vez. Crie uma branch apenas para 
        o rotalog-api-frontas. Entretanto, troque o nome das outras para 'aula-3'.
        >> O claude renomeou as branches dos demais projetos para 'aula-3', e a do api-frontas 
           deixou 'aula-4'
  7.  No Prompt do Chat do Claude, modelo Haiku 5.5, em Auto, executamos:
      > @.claude/agents/java-frotas.md Crie um teste unitário com ao menos 90% de cobertura para a classe 
        @rotalog-api-frotas/src/main/java/com/rotalog/service/VeiculoService.java. Garanta que todos os testes 
        estejam passando.
  8.  No Prompt do Chat do Claude, modelo Haiku 5.5, em Auto, executamos:
      > @rotalog-api-frotas/docs/adr/0001-decomposicao-veiculo-service.md destaca 13 problemas. Vamos resolver 
        um por vez, testar, atualizar a ADR, comitar. Começamos com primeiro problema, implemente, teste, e se 
        tudo der ok, atualize a ADR e comite. Aguarde novas instruções.
  9.  No Prompt do Chat do Claude, modelo Haiku 5.5, em Auto, executamos:
      > @.claude/agents/node-entregas.md Verifique no projeto api-entregas  se existe algum teste que é capaz 
        de testar o arquivo @rotalog-api-entregas/src/middleware/auth.js
        >> Não existe, quer que eu crie?
      > Sim, crie o arquivo de teste, e garanta ao menos 90% de cobertura
        >> @see rotalog-api-entregas\test\middleware\auth.test.js
  10. No Prompt do Chat do Claude, modelo Haiku 5.5, em Auto, executamos:
      > @.claude/agents/node-entregas.md Faça a correção apontada na 
        @rotalog-api-entregas/docs/adr/0001-saneamento-auth-e-camadas-entregas.md 
        do projeto em questão para corrigir os problemas de vulnerabilidades no arquivo 
        @rotalog-api-entregas/src/middleware/auth.js. Garanta que os testes após as correção estão 
        funcionando 100%.
  11. No Prompt do Chat do Claude, modelo Haiku 5.5, em Auto, executamos:
      > @.claude/agents/node-entregas.md Faça os passos a seguir:
        1- Crie os testes para o arquivo @rotalog-api-entregas/src/routes/entregas.js 
        2- Garanta que os testes estejam passando
        3- Caso tudo ok, faça o commit 
        4- Execute a etapa 2 da @rotalog-api-entregas/docs/adr/0001-saneamento-auth-e-camadas-entregas.md  
        5- Altere os testes e garanta que estejam funcionando;
        6- Execute a etapa 3 da @rotalog-api-entregas/docs/adr/0001-saneamento-auth-e-camadas-entregas.md  
        7- Garanta que os testes cobrem os arquivos novos criados, se não, crie novos testes
        8- Se tudo estiver funcional, gere um commit
  12. Refatoração: Mudança na estrutura interna do código que não altera seu comportamento externo
  13. Código Legado: código sem testes, onde qualquer mudança é um risco. A idéia é:
      > Identificar o ponto de mudança 
      > Quebrar dependências
      > Cobrir com testes 
      > Finalmente: refatorar 
  14. Testes de Caracterização: Testa o que o código faz usando entradas conhecidas, observa as sáidas
      e escreve testes que capturam esse comportamento
  15. Test Doubles: objeto que substitui uma dependência real durante o teste
      > Tipos:
        >> Dummy: objeto que é passado mas nunca usado. Serve só pra preencher um parâmetro obrigatório.
        >> Stub: retorna respostas pré-programadas. O when(repository.findById(1L)).thenReturn(...) do 
           'Mockito' é um stub.
        >> Spy: grava as chamadas que recebeu pra você verificar depois. O verify(repository).findById(1L) é 
           uma verificação de spy.
        >> Mock: combina stub + spy com expectativas pré-definidas. No Jest, jest.fn() cria um mock que pode 
           ser configurado e verificado.
        >> Fake: implementação funcional simplificada, como um banco de dados em memória em vez do PostgreSQL 
           real.
  16. Testes unitários geralmente usam 'Stub' e 'Mocks' para isolar a unidade
  17. Teste de integração usam Fakes (como o banco H2 no lugar do PostgreSQL)
  18. Quando usar cada tipo de Teste:
      > Unitários: verificam uma unidade isolada do código, geralmente um método ou função, sem dependências 
        externas. São rápidos, baratos e dão feedback imediato
      > Integração: verificam se componentes diferentes funcionam juntos: service + banco, rota + middleware 
        + service. São mais lentos, mas pegam problemas que testes unitários não capturam.
      > E2E: verificam o fluxo completo do sistema, da requisição do usuário até a resposta final, passando 
        por todos os serviços. São os mais lentos e frágeis, mas validam que as peças se encaixam.
  19. Pirâmide de Testes: muitos testes unitários na base (rápidos e baratos), menos testes de integração 
      no meio, e poucos testes E2E no topo. Na refatoração de código legado, essa ordem se inverte no início: 
      como o código geralmente não tem testes, nós começamos com testes de integração ou testes de 
      caracterização que capturam o comportamento atual, e depois vamos adicionando unitários conforme 
      extraímos e isolamos responsabilidades.
  20. Métricas de Testes:
      > Line coverage: percentual de linhas executadas pelos testes. É a mais comum, mas também a mais 
        superficial. Uma linha pode ser executada sem que o teste valide nada sobre ela.
      > Branch coverage: percentual de ramificações lógicas cobertas (cada if/else, cada switch case). 
        É mais valiosa porque garante que os caminhos alternativos do código foram exercitados.
      > Mutation testing: altera o código fonte (inverte condições, troca operadores) e verifica se algum 
        teste falha. Se nenhum teste quebra quando você muda > pra >=, seus testes não estão realmente 
        validando aquela lógica. É a métrica mais rigorosa, mas também a mais cara de rodar.
       
Aula 05: Implementando feature cross-service 
  1.  Utilizando o DoD 
      > Defenition of Done: O que é o 'Pronto'
        >> Sem o DoD, o agente decide sozinho até onde ir 
        >> O 'pronto' dele pode ser diferente do seu 
        >> Critérios de aceite viram checklist da implementação 
        >> Cada serviço sabe o que precisa entregar
  2.  O que vamos implementar:   
      > Passo 1: API-FROTAS
        Verifica veículos com manutenção vencida. Chama o serviço de notificações via HTTP.
      > Passo 2: API-NOTIFICAÇÕES
        Recebe pedido via MediatR handler. Envia e-mail para o gestor. Retorna status.
      > Passo 3: PAINEL-ADMIN
        Exibe alertas com filtro por status. Consome endpoint da api-frotas.
        *se o serviço de notificações estiver fora, o alerta é registrado como 'pendente'*.   
  3.  Criamos o arquivo DoD.md com os requisitos do que queremos que a IA implemente 
      @see DoD.md
  4.  No Prompt do Chat do Claude, modelo Opus 5.5, em Manual, executamos:
      Avalie o arquivo @DoD.md e crie um TODO list com tudo o que precisa ser implementado nessa nova 
      feature. Não implemente nada ainda.
      > O Claude fez o TODO list na sessão 
  5.  No Prompt do Chat do Claude, modelo Opus 5.5, em Manual, executamos:
      > Salve o TODO list como um Plano de Execução na pasta 'plans' na raiz do Workspace. Não execute-o ainda.
        Quanto aos pontos a decidir:
        1. limite de km é 50000 e o número de meses 6;
        2. O email do gestor faz parte das variáveis do ambiente; 
        3. A verificação é disparada 'manualmente'. Provavelmente vamos decidir por algo do tipo: ao acessar uma determinada tela, ou ao logar no sistema;
        4. O reenvio fica pra depois;
        5. Km desde a última preventiva.
      > Claude salvou o @see plans\alerta-manutencao-preventiva.md
  6.  No Prompt do Chat do Claude, modelo Sonnet 5.5, em Auto, executamos:
      > Implemente o plano de execução @plans/alerta-manutencao-preventiva.md . Além disso, preciso que você 
        coloque os scripts do banco de dados do projeto Workspace na pasta tools, siga o padrão dos demais 
        scripts. Quero também que você implemente um script de seed que irá popular o banco de dados com 
        os carros elegíveis para manutenção.
        >> O Claude finalizou ao plano, e, no terminal do 'rotalog-workspace', destruímos e recriamos o 
           container:
           >>> docker-compose down -v
           >>> docker-compose up -d postgres
        >> E no terminal do 'rotalog-api-frotas':
           >>> mvn spring-boot:run -e
        >> Criamos uma nova collection 'rotalog' no Postman Workspace dos 'Projetos de Cursos da Alura'
           >>> Na Collection, criamos uma pasta 'api-frotas'
           >>> Criamos na pasta 'api-frontas' 2 requisições:
               >>>> Resumo de Alertas de Manutenção de Veículos
               >>>> Lista de alertas com a placa do veículo, filtro opcional   
  7.  No Prompt do Chat do Claude, modelo Sonnet 5.5, em Auto, executamos:
      > @.claude/agents/java-frotas.md Implemente os testes unitários ou/e integrados para a feature feita 
        dentro do api-frontas
  8.  No Prompt do Chat do Claude, modelo Sonnet 5.5, em Auto, executamos:
      > @.claude/agents/dotnet-notificacoes.md Faça a implementação da feature manutenção preventiva na 
        api-notificacoes. Lembre-se de consultar a feature no api-frotas e garantiar que essa comunicação 
        será feita com sucesso. 
        >> O que o agente alterou
           - Validação: motivo fora dos valores aceitos agora devolve 400, sem enviar nem gravar histórico, em 
             NotificacoesController.cs.
           - Testes: 9 testes novos em AlertaManutencaoTests.cs.
           - Exemplos: seção de alerta preventivo no requests.http.
           - Docs: o 400 de motivo inválido está documentado no README.md.
  9.  No Prompt do Chat do Claude, modelo Sonnet 5.5, em Auto, executamos:
      > @.claude/agents/frontend-rotalog.md 
        Crie a tela de alertas de manutenção no painel-admin. O endpoint já existe na api-frotas: 
        GET /api/manutencoes/alertas (aceita queary param status). Deixei a app api-frotas levantado no 
        localhost:8080 para você utilizar, caso precise. 
        Olhe o DTO AlertaManutencaoResponse no api-frotas, pois para montar a interface Typescript é preciso 
        utilizar as mesmas propriedades. 
        Siga o padrão simples, estilo o implementado na tela de Manutenção, na qual temos Tabela + Select 
        de filtro pos Status, sem busca por texto, cards ou painel de detalhe. Lembre-se de adicionar a 
        rota e o link na sidebar.
        >> A tela de alertas de manutenção já está no painel-admin, completa. Não alterei nada, porque cada 
           item que você pediu já existe e bate com o que descreveu. 
           >>> Ao solicitarmos a conversão do 'TODO List' em 'Plano de Ação', a IA também inseriu o 
               'painel-admin' no objetivo do plano @see plans\alerta-manutencao-preventiva.md, o que faz
               sentido, pois, não teria como testar a integração completa sem o item de 'Alertas' no
               'painel-admin'
  10. DoD: Definition of Done (o que é o 'pronto') 
      > Vem do Scrum: É o compromisso do artefato 'incremento', ao lado do 'Sprint Goal' 
        (compromisso do Sprint Backlog), e do 'Product Goal' (compromisso do Product Backlog);
      > É uma descrição formal do estado do 'Incremento' quando ele atende às medidas de qualidade requeridas 
        para o produto;
  11. DoD x Critérios de Aceite 
      > 'DoD' é a barra mínima de qualidade que qualquer entrega precisa atingir;
      > 'Critérios de Aceite' descrevem o que a funcionalidade precisa fazer para ser considerada correta;
      > Os 'Critérios de Aceite' dizem o que a feature faz. O DoD diz com qual nível de qualidade ela 
        foi feita;
      > Uma feature pode atender todos os critérios de aceite (funciona como esperado) e ainda assim não 
        estar "pronta" se não passou pelo 'DoD' (sem testes, sem revisão, com secret hardcoded).
  12. Estrutura de um DoD 
      > Código: revisão feita, segue as convenções do projeto, sem warnings ignorados
      > Testes: unitários passando, cobertura dos caminhos críticos, cenários de erro cobertos
      > Segurança: sem secrets hardcoded, inputs validados, sem vulnerabilidades conhecidas
      > Integração: comunicação entre serviços funcionando, contratos de API respeitados
      > Documentação: endpoints documentados, ADR criado se houve decisão arquitetural
      > Deploy: código no branch correto, build passando, pronto para ir pra produção

Aula 06: Debug, observabilidade e método
  1.  No Prompt do Chat do Claude, modelo Haiku 5.5, em Auto, executamos: 
      > Verifique o possível bug na implementação da feature de alerta de manutenção preventiva, pois o 
        gestor do rotalog não está recebendo os e-mails quando um veículo é cadastrado para a manutenção
        >> Confirmei o problema, mas a causa principal não é o disparo do alerta. Com o código atual, o gestor 
           não recebe e-mail porque nenhum e-mail real é enviado. Também há um segundo motivo: o alerta não 
           dispara no cadastro nem no agendamento de manutenção
  2.  No Prompt do Chat do Claude, modelo Sonnet 5.5, em Auto, executamos:
      > Adicione observabilidade básica ao fluxo de alerta de manutenção:
        1- Log estruturado (JSON) em cada serviço, com correlation-id que acompanha a requisição do 
           rotalog-api-frotas até o rotalog-api-notificacoes;
        2- No rotalog-api-frotas: logar quando inicia verificação, quantos veículos encontrou e para cada 
           um logar quando chamar a api-notificacoes, com status da resposta;
        3- No rotalog-api-notificacoes: logar quando recebe o pedido, quando tenta enviar e-mail e o 
           resultado do envio;
        4- O correlation-id deve ser gerado no rotalog-frontas e passado via header com a chave 
           X-correlation-ID na chamada HTTP. Siga as convenções da logging de cada serviço (java e dotnet)
  3.  No Windows Power Shell executamos:
      > docker exec -e PGPASSWORD=rotalog123 rotalog-db psql -U rotalog_admin -d rotalog -c 
        "UPDATE frotas.alertas_manutencao SET status = 'FALHA' WHERE status IN ('ENVIADA','PENDENTE');"
      > Isso faz com que a tabela alertas_manutencao seja 'reiniciada', deixando todos os alertas com falha.
        Ao utilizar a requisiçaõ POST api/manutencoes/alertas/verificar, os alertas com FALHA serão reenviados
        em uma nova 'tentativa'
      > Ao executar o POST, pudemos verificar que o api-notificacoes registrou o recebimento dos alertas
  4.  Habilidades que importam ao trabalhar com IA:
      > Pensamento Crítico: quando a IA gera um relatório de dívida técnica, você precisa saber se faltou algo 
        ou se algo ali não procede
      > Capacidade de dar contexto: a diferença entre "analise o projeto" e um prompt estruturado com critérios 
        específicos é a diferença entre um resultado genérico e um resultado útil
      > Domínio dos fundamentos: para revisar código gerado por IA em Java, Node e .NET, você precisa conhecer 
        as linguagens, os frameworks e os padrões. A IA não substitui esse conhecimento, ela depende dele
      > Julgamento de engenharia: quando o Claude Code sugeriu refatorar o VeiculoService, quem decidiu 
        dividir em QueryService e CommandService em vez de reescrever do zero fomos nós. A IA propôs, mas a 
        decisão arquitetural foi humana

Resumo Prático do Curso:
  1.  Criamos um novo arquivo na raiz do rotalog, iniciando as 'primeiras linhas' do markdown de orientação 
      ao Claude Code:
      @see CLAUDE.md
  2.  Solicitamos ao Claude completar o arquivo @CLAUDE.md
  3.  Criamos a estrutura inicial para os 'Subagentes'
      > Criamos o diretório e '.claude/agents'
        @see .claude\agents
      > Criamos o arquivo java-frotas.md, com as sessões 'stack', 'estrutura de pastas' e 'convenções'
        @see .claude\agents\java-frotas.md
  4.  Solicitamos ao Cluade criar os demais 'Subagentes', com base no 'Subagente' do java-frontas.md
  5.  Baseado no MAPA_DIVIDA_TECNICA.md, solicitamos ao 'Subagente' gerar uma ADR apontando os problemas 
      da app api-frotas
  6.  Solicitamos ao Claude criar testes para os projetos
  7.  Iniciamos a refatoração dos projetos 
  8.  Iniciamos uma nova feature