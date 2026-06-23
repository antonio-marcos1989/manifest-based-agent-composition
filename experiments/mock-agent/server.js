const http = require("http");
const { handleRequest } = require("./handlers");

const PORT = Number(process.env.MOCK_AGENT_PORT || 9090);

const server = http.createServer((req, res) => {
  handleRequest(req, res).catch((err) => {
    console.error(err);
    res.writeHead(500, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: "internal_error" }));
  });
});

server.listen(PORT, () => {
  console.log(`Mock agent farm listening on http://localhost:${PORT}`);
  console.log("Routes: /mock/component/{generative,classification,regression,ala-evaluator}");
  console.log("          /mock/referee  /mock/observer  GET /health  GET /mock/catalog");
});
