"use strict";

const MOCK_DELAY_KEY = "_mockDelayMs";
const MOCK_CONFIDENCE_KEY = "_mockConfidence";
const MOCK_COST_KEY = "_mockCost";
const MOCK_TOKENS_KEY = "_mockTokens";

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    let body = "";
    req.on("data", (chunk) => (body += chunk));
    req.on("end", () => {
      if (!body) {
        resolve({});
        return;
      }
      try {
        resolve(JSON.parse(body));
      } catch (err) {
        reject(err);
      }
    });
    req.on("error", reject);
  });
}

function envelope(data, explanation, metricsExtra = {}) {
  return {
    data,
    explanation,
    metrics: {
      model: metricsExtra.model || "mock-agent",
      tokensInput: metricsExtra.tokensInput ?? 10,
      tokensOutput: metricsExtra.tokensOutput ?? 8,
      confidence: metricsExtra.confidence ?? 0.95,
      estimatedCost: 0,
      ...metricsExtra,
    },
  };
}

function extractDirectInput(body) {
  if (!body || typeof body !== "object") {
    return {};
  }
  const copy = { ...body };
  delete copy[MOCK_DELAY_KEY];
  delete copy[MOCK_CONFIDENCE_KEY];
  delete copy[MOCK_COST_KEY];
  delete copy[MOCK_TOKENS_KEY];
  delete copy.messages;
  delete copy.model;
  delete copy.stream;
  return copy;
}

function extractChatUserPayload(body) {
  if (body?.messages && Array.isArray(body.messages)) {
    const user = [...body.messages].reverse().find((m) => m.role === "user");
    if (user?.content) {
      try {
        return JSON.parse(user.content);
      } catch {
        return { prompt: user.content };
      }
    }
  }
  return extractDirectInput(body);
}

async function applyMockDelay(body) {
  const delay = Number(body?.[MOCK_DELAY_KEY] ?? 0);
  if (delay > 0) {
    await sleep(delay);
  }
}

function handleComponentGenerative(body) {
  const input = extractChatUserPayload(body);
  const prompt = input.prompt || input.text || JSON.stringify(input);
  return envelope(
    {
      answer: `generative:mock-response`,
      promptEcho: prompt,
      format: "text",
    },
    "Mock GENERATIVE / COMPONENT agent (CHAT_MESSAGES).",
    { model: "mock-generative-v1", confidence: 0.91 }
  );
}

function metricsFromControls(body, defaults = {}) {
  const confidence = body?.[MOCK_CONFIDENCE_KEY] ?? defaults.confidence ?? 0.95;
  const estimatedCost = body?.[MOCK_COST_KEY] ?? defaults.estimatedCost ?? 0;
  const tokensInput = defaults.tokensInput ?? 10;
  const tokensOutput = defaults.tokensOutput ?? 8;
  const tokensOverride = body?.[MOCK_TOKENS_KEY];
  const tokensTotal = tokensOverride != null
    ? Number(tokensOverride)
    : tokensInput + tokensOutput;
  return {
    model: defaults.model || "mock-agent",
    tokensInput,
    tokensOutput,
    tokensTotal,
    confidence,
    estimatedCost,
  };
}

function handleComponentClassification(body) {
  const input = extractDirectInput(body);
  const feature = input.feature || input.text || "unknown";
  const label =
    String(feature).toLowerCase().includes("neg") ? "negative" : "positive";
  return envelope(
    {
      label,
      confidence: metricsFromControls(body).confidence,
      featureEcho: feature,
    },
    `Mock CLASSIFICATION / COMPONENT: label=${label}.`,
    metricsFromControls(body, { model: "mock-classifier-v1" })
  );
}

function handleComponentRegression(body) {
  const input = extractDirectInput(body);
  const raw = input.value ?? input.feature ?? input.x ?? 1.0;
  const x = Number(raw);
  const prediction = Number.isFinite(x) ? x * 1.25 + 0.5 : 1.75;
  return envelope(
    {
      prediction: Number(prediction.toFixed(4)),
      unit: "mock-score",
      inputEcho: input,
    },
    `Mock REGRESSION / COMPONENT: prediction=${prediction.toFixed(4)}.`,
    { model: "mock-regressor-v1", confidence: 0.93 }
  );
}

/**
 * COMPONENT analítico: recebe snapshot de execução + ALA e devolve diagnóstico estruturado.
 */
function handleComponentAlaEvaluator(body) {
  const input = extractDirectInput(body);
  const execution = input.execution || {};
  const ala = input.alaSettings || {};
  const recent = Array.isArray(input.recentInvocations)
    ? input.recentInvocations
    : [];

  const violations = [];
  const latencyMs = Number(execution.latencyMs ?? 0);
  const maxLatencyMs = Number(ala.maxLatencyMs ?? 0);
  if (maxLatencyMs > 0 && latencyMs > maxLatencyMs) {
    violations.push({
      dimension: "LATENCY",
      observed: latencyMs,
      limit: maxLatencyMs,
      severity: latencyMs > maxLatencyMs * 2 ? "HIGH" : "MEDIUM",
      message: `Latency ${latencyMs}ms exceeds ALA maxLatencyMs ${maxLatencyMs}ms.`,
    });
  }

  if (execution.alaCompliant === false && violations.length === 0) {
    violations.push({
      dimension: "ALA_FLAG",
      observed: execution.status || "NON_COMPLIANT",
      limit: "COMPLIANT",
      severity: "MEDIUM",
      message: "Dispatcher marked invocation as ALA non-compliant.",
    });
  }

  const httpStatus = Number(execution.httpStatusCode ?? 200);
  if (httpStatus >= 400) {
    violations.push({
      dimension: "HTTP_STATUS",
      observed: httpStatus,
      limit: 399,
      severity: "HIGH",
      message: `HTTP status ${httpStatus} indicates execution failure.`,
    });
  }

  const metrics = execution.metrics || {};
  const minConfidence = Number(ala.minConfidenceScore ?? 0);
  const observedConfidence = Number(metrics.confidenceScore ?? execution.confidenceScore ?? NaN);
  if (minConfidence > 0 && Number.isFinite(observedConfidence) && observedConfidence < minConfidence) {
    violations.push({
      dimension: "CONFIDENCE",
      observed: observedConfidence,
      limit: minConfidence,
      severity: "MEDIUM",
      message: `Confidence ${observedConfidence} below minConfidenceScore ${minConfidence}.`,
    });
  }

  const maxCost = Number(ala.maxEstimatedCost ?? 0);
  const observedCost = Number(metrics.estimatedCost ?? execution.estimatedCost ?? NaN);
  if (maxCost > 0 && Number.isFinite(observedCost) && observedCost > maxCost) {
    violations.push({
      dimension: "COST",
      observed: observedCost,
      limit: maxCost,
      severity: "MEDIUM",
      message: `Estimated cost ${observedCost} exceeds maxEstimatedCost ${maxCost}.`,
    });
  }

  const maxTokens = Number(ala.maxTokensPerInvocation ?? 0);
  const observedTokens = Number(metrics.tokensTotal ?? execution.tokensTotal ?? NaN);
  if (maxTokens > 0 && Number.isFinite(observedTokens) && observedTokens > maxTokens) {
    violations.push({
      dimension: "TOKENS",
      observed: observedTokens,
      limit: maxTokens,
      severity: "MEDIUM",
      message: `Token total ${observedTokens} exceeds maxTokensPerInvocation ${maxTokens}.`,
    });
  }

  const window = Number(ala.evaluationWindow ?? recent.length ?? 0);
  const maxErrorPct = Number(ala.maxErrorPercentage ?? 100);
  if (recent.length > 0 && window > 0) {
    const sample = recent.slice(0, window);
    const nonCompliant = sample.filter((r) => r.alaCompliant === false).length;
    const errorRate = (nonCompliant / sample.length) * 100;
    if (errorRate >= maxErrorPct) {
      violations.push({
        dimension: "ERROR_RATE",
        observed: Number(errorRate.toFixed(2)),
        limit: maxErrorPct,
        severity: "HIGH",
        message: `Error rate ${errorRate.toFixed(1)}% exceeds maxErrorPercentage ${maxErrorPct}% in window ${sample.length}.`,
      });
    }
  }

  const overallCompliant = violations.length === 0;
  const recommendations = [];
  if (violations.some((v) => v.dimension === "LATENCY")) {
    recommendations.push(
      "Increase maxLatencyMs, optimize the endpoint, or move inference closer to the dispatcher."
    );
  }
  if (violations.some((v) => v.dimension === "ERROR_RATE")) {
    recommendations.push(
      "Review recent failing invocations; consider deactivating the agent until the backend stabilizes."
    );
  }
  if (overallCompliant) {
    recommendations.push("No ALA infringement detected for the supplied execution snapshot.");
  }

  return envelope(
    {
      overallCompliant,
      violations,
      recommendations,
      evaluatedAgent: execution.agentName || execution.agentId || "unknown",
      evaluatedRole: execution.agentRole || null,
      evaluatedType: execution.agentType || null,
    },
    overallCompliant
      ? "ALA evaluator: execution within declared limits."
      : `ALA evaluator: ${violations.length} infringement(s) detected.`,
    { model: "mock-ala-evaluator-v1", confidence: overallCompliant ? 0.99 : 0.88 }
  );
}

function handleReferee(body) {
  const input = extractChatUserPayload(body);
  const objective = input.objective || input.taskTitle || input.prompt || "task";
  const approved = !String(objective).toLowerCase().includes("reject");
  return envelope(
    {
      approved,
      reasoning: approved
        ? `Mock REFEREE: objective "${objective}" satisfies declared constraints.`
        : `Mock REFEREE: objective "${objective}" was flagged for manual review.`,
      violations: approved ? [] : [{ code: "MOCK_POLICY", detail: "Simulated rejection." }],
    },
    "Mock REFEREE audit (GENERATIVE / CHAT_MESSAGES).",
    { model: "mock-referee-v1", confidence: approved ? 0.9 : 0.75 }
  );
}

function handleObserver(body) {
  const input = extractChatUserPayload(body);
  const context = input.execution || input.metrics || input;
  return envelope(
    {
      insights: [
        {
          type: "LATENCY",
          detail: `Observed latency: ${context.latencyMs ?? "n/a"} ms.`,
        },
        {
          type: "ALA",
          detail: `ALA compliant: ${context.alaCompliant ?? "unknown"}.`,
        },
        {
          type: "STATUS",
          detail: `Execution status: ${context.status ?? "unknown"}.`,
        },
      ],
      summary:
        "Mock OBSERVER explainability report for the supplied execution context.",
    },
    "Mock OBSERVER narrative for traceability in the paper experiment.",
    { model: "mock-observer-v1", confidence: 0.92 }
  );
}

const ROUTES = {
  "/mock/component/generative": handleComponentGenerative,
  "/mock/component/classification": handleComponentClassification,
  "/mock/provider-b/classification": handleComponentClassification,
  "/mock/component/regression": handleComponentRegression,
  "/mock/component/ala-evaluator": handleComponentAlaEvaluator,
  "/mock/referee": handleReferee,
  "/mock/observer": handleObserver,
};

async function handleRequest(req, res) {
  const url = new URL(req.url, "http://localhost");

  if (req.method === "GET" && url.pathname === "/health") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ status: "ok", routes: Object.keys(ROUTES) }));
    return;
  }

  if (req.method === "GET" && url.pathname === "/mock/catalog") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(
      JSON.stringify({
        agents: [
          { role: "COMPONENT", type: "GENERATIVE", path: "/mock/component/generative" },
          { role: "COMPONENT", type: "CLASSIFICATION", path: "/mock/component/classification" },
          { role: "COMPONENT", type: "REGRESSION", path: "/mock/component/regression" },
          { role: "COMPONENT", type: "CLASSIFICATION", capability: "ala-diagnosis", path: "/mock/component/ala-evaluator" },
          { role: "REFEREE", type: "GENERATIVE", path: "/mock/referee" },
          { role: "OBSERVER", type: "GENERATIVE", path: "/mock/observer" },
        ],
      })
    );
    return;
  }

  const handler = ROUTES[url.pathname];
  if (req.method !== "POST" || !handler) {
    res.writeHead(404, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: "not_found", path: url.pathname }));
    return;
  }

  try {
    const body = await readJsonBody(req);
    await applyMockDelay(body);
    const response = handler(body);
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify(response));
  } catch (err) {
    res.writeHead(400, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: "bad_request", message: String(err.message || err) }));
  }
}

module.exports = { handleRequest, MOCK_DELAY_KEY, ROUTES };
