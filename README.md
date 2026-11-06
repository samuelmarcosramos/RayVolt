# RayVolt — E-Scooter Fleet-as-a-Service

> **Plataforma de Gestão de Frota de Trotinetes Elétricas Inteligentes (TQS 2026/27)**  
> *Mobilidade ligeira gerida por hubs, telemetria em tempo real e monitorização de regras urbanas.*

---

## Abstract do Projeto

O **RayVolt** é uma solução completa de *Fleet-as-a-Service* (Frota de Mobilidade Ligeira como Serviço) desenhada para ..... A plataforma permite que utilizadores finais encontrem, desbloqueiem e utilizem trotinetes elétrica, enquanto os operadores gerem o ciclo de vida dos veículos, SLAs de manutenção e telemetria remota.

A aplicação foi desenvolvida com foco prioritário em **Engenharia de Qualidade de Software (SQA)**, integração contínua (CI/CD), arquitetura hexagonal (Ports & Adapters) e testes determinísticos automatizados.

---

## 👥 Equipa de Desenvolvimento

| Fotografia / Nome | Número | Papel Principal (Role) | Contacto / GitHub |
| :--- | :---: | :--- | :--- |
| **[ Nome Aluno 1 ]** | `[ Nº Aluno ]` | **Team Coordinator** & Developer | `[ @github3 ]` |
| **[ Nome Aluno 2 ]** | `126255`  | **Product Owner** & Developer | `[ @github2 ]` |
| **Kelvin Loforte** | `121420` | **QA Engineer** & Developer | `[ @github3 ]` |
| **[ Nome Aluno 4 ]** | `[ Nº Aluno ]` | **DevOps Master** & Developer | `[ @github4 ]` |

---

## Project Bookmarks (Links Rápidos)

* **Jira Backlog & Board:** [Link para o Board do Jira]
* **Xray Test Management:** [Link para o Xray no Jira]
* **Dashboard SonarQube:** [http://vm-tqs.deti.ua.pt:9000/dashboard?id=rayvolt]
* **Aplicação Deployed (Production):** [http://deti-tqs-XX.ua.pt]
* **Documentação da API (Swagger / OpenAPI):** [http://deti-tqs-XX.ua.pt/swagger-ui.html]
* **Observabilidade & Métricas:** [http://deti-tqs-XX.ua.pt:3000]

---

## Arquitetura do Sistema

### Principais Decisões de Design para Qualidade:

1. **Controlo Temporal Determinístico:** ...

2. **Simulador de Telemetria (Container Separado):** ...

3. **Concorrência de Alocação:** ...

##  Casos de Uso & Atores

| Ator | Canal | Ações Principais | 
| ----- | ----- | ----- | 
| **End User (Utilizador)** | Web App | Encontrar trotinetes por Hub (UC1), reservar/iniciar viagem, terminar viagem em Hub e consultar pagamentos/histórico. | 
| **Operator (Operador)** | Backoffice | Registar veículos/hubs, gerir ciclo de vida (Available, In Use, Maintenance, Retired) e tratar tickets de reparação. | 
| **Institutional Client** | Dashboard | Definir regras de uso (velocidades por zona, lotação de hubs) e acompanhar KPIs de SLA e utilização. | 
| **Telemetria (MQTT)** | Service | Envio simulado do estado de bateria, coordenadas GPS e eventos de falha. | 

## Stack Tecnológica

* **Backend:**

* **Base de Dados:** 

* **Frontend:** 

* **Telemetria:**

* **Qualidade & CI/CD:** 

## Como Executar o Projeto Localmente

### Pré-requisitos

* Docker & Docker Compose

* JDK 21

* Maven 3.9+

### Passos para iniciar a aplicação e infraestrutura:


## Execução dos Testes Automatizados

| Categoria de Teste | Comando | Descrição / Ferramentas | 
| ----- | ----- | ----- | 
| **Unitários** | `./mvnw test` | Testa regras de negócio com JUnit 5 & Mockito. | 
| **Integração** | `./mvnw verify ...` | Testes contra PostgreSQL real via **Testcontainers**. | 
| **BDD / Aceitação** | `./mvnw verify ...` | Cenários Gherkin executados com **Cucumber**. | 
| **End-to-End (E2E)** | `./mvnw ...` | Testes de UI automatizados com **Playwright**. | 
| **Desempenho (SLO)** | `k6 run ...` | Testes de carga e validação de SLOs com **k6**. | 
| **Cobertura & Cobertura** | `./mvnw ...` | Relatório JaCoCo enviado para o SonarQube. | 

## Declaração de Uso de IA Generativa
* 

* 

* 

## Estrutura do Repositório

```
RayVolt/
├── README.md                 # Este documento
├── AGENTS.md                 # Diretivas e políticas para Agentes de IA
├── docker-compose.yml        # Configuração dos serviços (DB, MQTT, App)
├── pom.xml                   # Configuração Maven e dependências
├── .github/
│   └── workflows/            # CI/CD Pipelines (Build, Tests, Sonar, Deploy)
├── docs/                     # Entregáveis de documentação
│   ├── Product_Specification.pdf # Especificação de Requisitos e Arquitetura
│   └── QA_Manual.pdf             # Manual de SQA e Tabela de Conformidade
├── simulator/                # Container do Simulador de Telemetria de Frota
└── src/
    ├── main/                 # Código fonte da aplicação Spring Boot
    └── test/                 # Suíte de testes (unit, integration, acceptance, functional)

```
