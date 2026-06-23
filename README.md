# Manifest-Based Agent Composition — Artifact Repository

**Repository:** https://github.com/antonio-marcos1989/manifest-based-agent-composition

Open-source reference implementation for the paper *A Manifest-Based Declarative Approach to AI Agent Composition*.

This repository is a **minimal artifact** focused on the manifest-centric path:

- manifest catalog (`/api/v1/manifests`)
- agnostic dispatch (`/api/v1/execute/{agentId}`)
- role governance and default ALA
- JSON Schema / legacy contract validators
- per-invocation telemetry (`execution_logs`) and metrics API

Goal/task orchestration, authentication, and UI are **out of scope**.

## Requirements

- JDK **21+**
- Maven 3.9+ (or `./mvnw`)
- MongoDB 6+ (local Docker or Atlas)
- Optional: Node.js 18+ for mock agent experiments

## Quick start

```bash
# 1. MongoDB
docker compose up -d

# 2. Run API (port 8081)
./mvnw spring-boot:run

# 3. Unit tests (no external agents)
./mvnw test -Dtest=AgentManifestServiceDefaultsTest,AgentManifestRoleTest,AgentResponseParserTest,JsonSchemaContractValidatorTest,AgentCredentialSupportTest,AgentPersonaSupportTest
```

Environment variables:

| Variable | Default |
|----------|---------|
| `MONGODB_URI` | `mongodb://localhost:27017/manifest-composition` |

## Functional experiment (mock agent farm)

The experiment covers **every role and type** used in the paper metamodel, plus an ALA diagnostic component:

| Role | Type | Mock route | Purpose |
|------|------|------------|---------|
| COMPONENT | GENERATIVE | `/mock/component/generative` | `CHAT_MESSAGES` dispatch |
| COMPONENT | CLASSIFICATION | `/mock/component/classification` | `DIRECT_JSON` ML-style API |
| COMPONENT | REGRESSION | `/mock/component/regression` | numeric prediction |
| COMPONENT | CLASSIFICATION | `/mock/component/ala-evaluator` | **ALA diagnosis** from execution snapshots |
| REFEREE | GENERATIVE | `/mock/referee` | audit (`approved`, `violations`) |
| OBSERVER | GENERATIVE | `/mock/observer` | explainability narrative |

An extra manifest (`mock-component-classification-slow`) uses `_mockDelayMs` with a tight `maxLatencyMs` to **trigger a latency ALA violation**; `mock-component-classification-low-confidence` stresses **confidence ALA**; `mock-component-classification-provider-b` demonstrates **endpoint portability** (alternate mock URL, same manifest schema). The ALA evaluator diagnoses infringements from execution snapshots.

The eight-phase script also verifies **contract rejection** (invalid input → HTTP 400) and ships a minimal **imperative baseline** in `experiments/baseline-imperative/` for integration-cost comparison.

Normative manifest schema: `experiments/manifest-schema.json`.

Terminal 1 — mock farm (port 9090):

```bash
cd experiments
npm install
npm run mock-agent
```

Terminal 2 — API (port 8081):

```bash
./mvnw spring-boot:run
```

Terminal 3 — full experiment:

```powershell
cd experiments
.\run-functional-experiment.ps1
# skip slow ALA demo:
.\run-functional-experiment.ps1 -SkipSlowAlaDemo
```

The script (`paper-agents.json` drives registration):

1. registers all manifests
2. executes GENERATIVE / CLASSIFICATION / REGRESSION components
3. runs the slow classifier to violate ALA latency (unless `-SkipSlowAlaDemo`)
4. invokes the **ALA evaluator** with execution + recent logs
5. invokes REFEREE and OBSERVER on the experiment context
6. prints a summary table (status, `alaCompliant`, latency)

Reset MongoDB collections between runs:

```bash
./mvnw test -Dtest=MongoCollectionCleanerTest
```

## Postman

Import `docs/postman/Manifest-Catalog.postman_collection.json`.

Collection variables:

| Variable | Example |
|----------|---------|
| `baseUrl` | `http://localhost:8081` |
| `ollamaApiKey` | your token (only for remote LLM steps) |

## REST surface (paper Table 3)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/v1/manifests` | List catalog |
| POST | `/api/v1/manifests` | Register manifest |
| POST | `/api/v1/manifests/test-connection` | Probe endpoint |
| POST | `/api/v1/execute/{agentId}` | Dispatch |
| GET | `/api/v1/manifests/{id}/metrics` | ALA telemetry summary |
| GET | `/api/v1/manifests/{id}/invocations` | Invocation logs |

## Project layout

```
src/main/java/...     Spring Boot reference implementation
src/test/java/...     Unit tests + Mongo cleaner utility
docs/postman/         Manifest-only Postman collection
experiments/          Mock agent + functional smoke script
docker-compose.yml    Local MongoDB
```

## Citation

If you use this artifact, please cite the accompanying paper (manifest-based declarative agent composition).

## License

Academic/research artifact — align license with your institution before public release.
