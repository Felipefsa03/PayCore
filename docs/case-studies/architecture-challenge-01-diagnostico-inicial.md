# Architecture Challenge #01 — Diagnóstico Inicial

## 1. Qual problema de negócio a PayCore está tentando resolver?

A PayCore busca oferecer uma infraestrutura financeira B2B que permita a empresas realizar e acompanhar transferências de valores entre contas de maneira segura, confiável e rastreável, sem precisar desenvolver toda essa estrutura internamente.

Na minha visão, o principal valor entregue não é simplesmente permitir uma transferência de saldo, mas garantir que as empresas possam confiar na execução e no resultado dessas operações.

Por exemplo, se um cliente corporativo solicitar uma transferência e ocorrer uma falha de comunicação, ele precisa conseguir descobrir se a operação foi efetivada, rejeitada ou continua em processamento. A ausência dessa informação poderia gerar novas tentativas, movimentações duplicadas e inconsistências financeiras.

Portanto, considero que o problema central envolve três necessidades: **integridade das movimentações, rastreabilidade das operações e simplicidade de integração para os clientes corporativos**.

Como estamos trabalhando inicialmente com saldos fictícios, o objetivo do MVP seria validar essas capacidades em um ambiente controlado, criando uma base que possa evoluir sem comprometer as regras fundamentais do domínio financeiro.

## 2. Quais cinco perguntas você faria ao CTO antes de escolher uma arquitetura?

**1. Qual é exatamente o escopo das movimentações financeiras do MVP?**

Precisamos permitir apenas transferências entre contas internas da plataforma ou também simular integrações com instituições externas?

Essa definição influencia diretamente a complexidade do processamento, os estados das operações e a necessidade de lidar com falhas distribuídas.

**2. Qual volume de transações esperamos processar inicialmente e qual crescimento projetamos?**

Gostaria de entender a quantidade estimada de operações por segundo, os horários de pico e o tempo de resposta esperado.

Não considero adequado escolher uma arquitetura distribuída complexa apenas pela possibilidade de crescimento. Primeiro precisamos conhecer os requisitos reais de escala e identificar quais decisões poderão ser revistas futuramente.

**3. Quais garantias de consistência financeira são obrigatórias?**

Por exemplo, uma transferência precisa atualizar o débito e o crédito de forma atômica? O sistema pode apresentar temporariamente informações desatualizadas em consultas? Como devemos tratar operações cujo resultado ainda é desconhecido?

Considero importante distinguir consistência dos registros financeiros de consistência das informações apresentadas ao usuário.

**4. Como devemos tratar falhas, duplicidades e indisponibilidade dos serviços?**

Precisamos definir o comportamento esperado quando uma requisição é enviada duas vezes, quando ocorre um timeout ou quando uma operação é confirmada internamente, mas a notificação não chega ao cliente.

Também gostaria de entender quais expectativas de disponibilidade, recuperação e continuidade operacional precisamos atender.

**5. Quais são as restrições reais de segurança, infraestrutura e capacidade da equipe?**

Como teremos três engenheiros e quatro meses para entregar o MVP, gostaria de entender o orçamento disponível, a experiência da equipe com as tecnologias propostas, os requisitos de isolamento entre clientes corporativos e as exigências de autenticação, autorização e auditoria.

Essas informações ajudariam a escolher uma arquitetura que a equipe consiga não apenas desenvolver, mas também manter e operar com segurança.

## 3. Qual é o maior risco técnico ou de negócio?

Considero que o maior risco é **comprometer a integridade financeira durante o processamento de transferências, principalmente em situações de concorrência, falhas parciais ou repetição de requisições**.

Imagine uma conta com R$ 1.000 de saldo disponível recebendo duas solicitações simultâneas de transferência de R$ 800.

Se ambas consultarem o mesmo saldo antes de realizar a movimentação, uma implementação sem controle adequado de concorrência poderá autorizar as duas operações, mesmo que o valor disponível não seja suficiente.

Outro cenário crítico seria uma transferência confirmada no banco de dados, mas cuja resposta não chegasse ao cliente. Ao reenviar a requisição, o cliente poderia provocar uma segunda movimentação caso não existisse um mecanismo adequado de idempotência.

Esses problemas são particularmente graves porque afetam a confiabilidade do produto. Uma plataforma financeira que processa operações incorretamente perde seu valor, independentemente de sua velocidade ou escalabilidade.

Por isso, minha prioridade inicial seria estabelecer invariantes financeiras e mecanismos capazes de garantir que:

- Uma mesma operação lógica não produza movimentações duplicadas.
- Débitos e créditos respeitem as regras de conservação de valores.
- Transferências concorrentes não comprometam o saldo disponível.
- Operações confirmadas permaneçam registradas e auditáveis.
- Falhas de comunicação não sejam confundidas automaticamente com falhas na execução financeira.

Ainda não definiria uma tecnologia específica para resolver esses problemas. Primeiro, documentaria os cenários críticos e as garantias necessárias. Depois, compararia alternativas de persistência, controle de concorrência e processamento, considerando suas limitações e custos.

## 4. Como você saberia que o MVP foi bem-sucedido?

Eu avaliaria o sucesso do MVP em duas dimensões: **correção funcional e confiabilidade técnica**.

Do ponto de vista funcional, o sistema deve permitir que clientes corporativos autenticados criem e consultem contas fictícias, solicitem transferências, acompanhem seus estados e recebam notificações sobre o resultado das operações.

Do ponto de vista técnico, estabeleceria os seguintes critérios de aceite:

**1. Integridade financeira:** 100% dos cenários de teste de transferências devem preservar as invariantes financeiras definidas, sem lançamentos desbalanceados ou movimentações inconsistentes.

**2. Idempotência:** ao executar 100 requisições simultâneas com a mesma chave de idempotência e o mesmo conteúdo, apenas uma transferência financeira deve ser efetivada.

**3. Concorrência:** testes com transferências simultâneas devem comprovar que o saldo disponível não se torna negativo quando a regra de negócio proíbe descoberto.

**4. Rastreabilidade:** todas as operações devem possuir identificadores únicos, histórico de estados e registros suficientes para investigar seu processamento.

**5. Recuperação de falhas:** operações confirmadas devem permanecer recuperáveis e consultáveis após reinícios dos serviços ou falhas simuladas de comunicação.

**6. Desempenho:** gostaria de estabelecer inicialmente uma meta experimental de latência p95 inferior a 300 ms para transferências internas, validada sob uma carga de teste definida e documentada. Esse valor deverá ser revisto após os primeiros benchmarks.

**7. Reprodutibilidade:** a equipe deve conseguir configurar e executar o ambiente de desenvolvimento de forma documentada e automatizada, com testes integrados ao pipeline de CI.

Além dos critérios técnicos, consideraria importante validar se uma aplicação cliente consegue integrar-se à plataforma utilizando apenas a documentação disponibilizada.

Para mim, o MVP será bem-sucedido quando conseguirmos demonstrar que as funcionalidades essenciais operam corretamente, que os cenários críticos de falha foram testados e que a arquitetura escolhida atende às necessidades atuais sem introduzir complexidade desnecessária.

**Minha principal premissa arquitetural é que a confiabilidade das operações financeiras deve ser estabelecida antes de investirmos em mecanismos avançados de escalabilidade.** Uma plataforma que processa milhares de operações incorretamente não é melhor do que uma que processa centenas corretamente.