const http = require("http");

const PORT = process.env.MOCK_AGENT_PORT || 9090;

const server = http.createServer((req, res) => {
  if (req.method === "POST") {
    let body = "";
    req.on("data", (chunk) => (body += chunk));
    req.on("end", () => {
      let input = {};
      try {
        input = body ? JSON.parse(body) : {};
      } catch {
        input = { raw: body };
      }

      const response = {
        data: {
          answer: "classification:positive",
          echo: input,
        },
        explanation: "Mock agent response for functional experiment.",
        metrics: {
          tokensInput: 12,
          tokensOutput: 8,
          model: "mock-classifier-v1",
          confidence: 0.97,
          estimatedCost: 0.0,
        },
      };

      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify(response));
    });
    return;
  }

  if (req.method === "GET" && req.url === "/health") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ status: "ok" }));
    return;
  }

  res.writeHead(404);
  res.end();
});

server.listen(PORT, () => {
  console.log(`Mock ML agent listening on http://localhost:${PORT}`);
});
