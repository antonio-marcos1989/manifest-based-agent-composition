# Manifest-Based Agent Composition — Artifact Repository

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

## Functional experiment (offline mock agent)

Terminal 1 — mock classification/regression HTTP agent:

```bash
cd experiments
npm install
npm run mock-agent
```

Terminal 2 — API (if not already running):

```bash
./mvnw spring-boot:run
```

Terminal 3 — automated smoke flow:

```powershell
cd experiments
.\run-functional-experiment.ps1
```

The script:

1. registers a `CLASSIFICATION` manifest pointing to `http://localhost:9090`
2. calls `POST /api/v1/execute/{agentId}`
3. fetches `/metrics` and `/invocations`
4. prints a short summary (latency, ALA flag, log count)

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
