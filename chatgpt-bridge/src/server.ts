import { createServer, IncomingMessage, ServerResponse } from "node:http";
import { mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { z } from "zod";

type Json = Record<string, unknown>;
type ContextPack = {
  schemaVersion: number;
  deviceId: string;
  generatedAt: number;
  architecture: Record<string, unknown>;
  activeSituations: Array<Record<string, unknown>>;
  situations: Array<Record<string, unknown>>;
  personalModel: { interests?: Array<Record<string, unknown>>; [key: string]: unknown };
  priorityCandidates: Array<Record<string, unknown>>;
  uncertainCases: Array<Record<string, unknown>>;
  recentOutcomes: Array<Record<string, unknown>>;
  system: Record<string, unknown>;
};
type PolicyBoost = { match: string; weight: number };
type PolicyPack = {
  version: string;
  generatedAt: number;
  ttlMs: number;
  attentionThreshold: number;
  maxNowItems: number;
  interruptionPenaltyScale: number;
  featureWeights: Record<string, number>;
  boosts: PolicyBoost[];
  teacherNotes: string;
};
type BridgeState = {
  latestContext: ContextPack | null;
  contextHistory: ContextPack[];
  policy: PolicyPack;
};

const PORT = Number(process.env.PORT ?? 8787);
const MCP_PATH = "/mcp";
const STATE_FILE = resolve(process.env.CORTEX_STATE_FILE ?? ".data/cortex-bridge-state.json");
const DEVICE_TOKEN = process.env.CORTEX_DEVICE_TOKEN ?? "";

const defaultPolicy = (): PolicyPack => ({
  version: "bootstrap-1",
  generatedAt: Date.now(),
  ttlMs: 7 * 24 * 60 * 60 * 1000,
  attentionThreshold: 0.72,
  maxNowItems: 7,
  interruptionPenaltyScale: 0.24,
  featureWeights: {},
  boosts: [],
  teacherNotes: "Bootstrap policy. Prefer explicit requests, security, deadlines, active situations and meaningful state changes."
});

function emptyState(): BridgeState {
  return { latestContext: null, contextHistory: [], policy: defaultPolicy() };
}
function loadState(): BridgeState {
  try {
    return JSON.parse(readFileSync(STATE_FILE, "utf8")) as BridgeState;
  } catch {
    return emptyState();
  }
}
function saveState(state: BridgeState) {
  mkdirSync(dirname(STATE_FILE), { recursive: true });
  const tmp = `${STATE_FILE}.tmp`;
  writeFileSync(tmp, JSON.stringify(state, null, 2));
  renameSync(tmp, STATE_FILE);
}
function text(v: unknown): string {
  return typeof v === "string" ? v : v == null ? "" : String(v);
}
function flattenRecords(pack: ContextPack | null): Array<Record<string, unknown>> {
  if (!pack) return [];
  const interests = Array.isArray(pack.personalModel?.interests) ? pack.personalModel.interests : [];
  return [
    ...pack.priorityCandidates.map((x) => ({ ...x, _kind: "priority_candidate" })),
    ...interests.map((x) => ({ ...x, _kind: "interest" })),
    ...pack.activeSituations.map((x) => ({ ...x, _kind: "situation" })),
    ...pack.uncertainCases.map((x) => ({ ...x, _kind: "uncertain_case" }))
  ];
}
function stableId(row: Record<string, unknown>, index: number): string {
  const candidates = [row.id, row.derivedId, row.situationId, row.key];
  const found = candidates.find((x) => typeof x === "string" || typeof x === "number");
  return found == null ? `${text(row._kind) || "record"}:${index}` : `${text(row._kind) || "record"}:${String(found)}`;
}
function searchable(row: Record<string, unknown>): string {
  return Object.values(row).map(text).join(" ").toLowerCase();
}
async function readJson(req: IncomingMessage, maxBytes = 1_000_000): Promise<unknown> {
  let bytes = 0;
  const chunks: Buffer[] = [];
  for await (const chunk of req) {
    const b = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    bytes += b.length;
    if (bytes > maxBytes) throw new Error("Payload too large");
    chunks.push(b);
  }
  const raw = Buffer.concat(chunks).toString("utf8").trim();
  return raw ? JSON.parse(raw) : {};
}
function json(res: ServerResponse, status: number, body: unknown) {
  res.writeHead(status, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" });
  res.end(JSON.stringify(body));
}
function deviceAuthorized(req: IncomingMessage): boolean {
  if (!DEVICE_TOKEN) return true;
  const header = text(req.headers.authorization);
  return header === `Bearer ${DEVICE_TOKEN}`;
}

const boostSchema = z.object({
  match: z.string().min(1).max(120),
  weight: z.number().min(-1).max(1)
});
const policyInput = {
  version: z.string().min(1).max(80),
  ttlMs: z.number().int().min(60_000).max(30 * 24 * 60 * 60 * 1000).default(7 * 24 * 60 * 60 * 1000),
  attentionThreshold: z.number().min(0).max(1).default(0.72),
  maxNowItems: z.number().int().min(1).max(12).default(7),
  interruptionPenaltyScale: z.number().min(0).max(0.55).default(0.24),
  featureWeights: z.record(z.string(), z.number().min(0).max(0.45)).default({}),
  boosts: z.array(boostSchema).max(100).default([]),
  teacherNotes: z.string().max(4000).default("")
};

function createCortexServer() {
  const server = new McpServer(
    { name: "cortex-personal-intelligence", version: "0.1.0" },
    {
      instructions:
        "Cortex is Karim's private personal-intelligence store. Cortex owns evidence, knowledge, world state and execution. Read grounded situations, personal model, candidates and outcomes before teaching policy. You may tune bounded FINAL JUDGMENT parameters only. Never mutate evidence, create canonical facts, directly suppress UI items, or execute actions."
    }
  );

  server.registerTool(
    "search",
    {
      title: "Search Cortex context",
      description: "Use this when you need to find current Cortex situations, priority candidates, or learned interests relevant to the user's request.",
      inputSchema: {
        query: z.string().min(1).max(300),
        kinds: z.array(z.enum(["priority_candidate", "interest", "situation"])).optional(),
        limit: z.number().int().min(1).max(50).default(20)
      },
      outputSchema: {
        results: z.array(z.object({ id: z.string(), kind: z.string(), record: z.record(z.string(), z.unknown()) }))
      },
      annotations: { readOnlyHint: true, destructiveHint: false, openWorldHint: false }
    },
    async ({ query, kinds, limit }) => {
      const state = loadState();
      const q = query.trim().toLowerCase();
      const rows = flattenRecords(state.latestContext)
        .map((record, index) => ({ id: stableId(record, index), kind: text(record._kind), record }))
        .filter((x) => (!kinds?.length || kinds.includes(x.kind as "priority_candidate" | "interest" | "situation")) && searchable(x.record).includes(q))
        .slice(0, limit);
      return {
        structuredContent: { results: rows },
        content: [{ type: "text", text: `Found ${rows.length} Cortex context record(s).` }]
      };
    }
  );

  server.registerTool(
    "fetch",
    {
      title: "Fetch Cortex record",
      description: "Use this after search when you need the complete stored Cortex record for one stable id.",
      inputSchema: { id: z.string().min(1).max(200) },
      outputSchema: { found: z.boolean(), record: z.record(z.string(), z.unknown()).nullable() },
      annotations: { readOnlyHint: true, destructiveHint: false, openWorldHint: false }
    },
    async ({ id }) => {
      const rows = flattenRecords(loadState().latestContext);
      const found = rows.find((record, index) => stableId(record, index) === id) ?? null;
      return {
        structuredContent: { found: Boolean(found), record: found },
        content: [{ type: "text", text: found ? "Cortex record loaded." : "Cortex record not found." }]
      };
    }
  );

  server.registerTool(
    "get_priority_review",
    {
      title: "Review Cortex priorities",
      description: "Use this when judging what should or should not interrupt the user. Returns the current candidate set, learned interests, situations, feedback summary, and active policy.",
      inputSchema: {},
      outputSchema: {
        generatedAt: z.number(),
        priorityCandidates: z.array(z.record(z.string(), z.unknown())),
        personalModel: z.record(z.string(), z.unknown()),
        activeSituations: z.array(z.record(z.string(), z.unknown())),
        uncertainCases: z.array(z.record(z.string(), z.unknown())),
        recentOutcomes: z.array(z.record(z.string(), z.unknown())),
        policy: z.record(z.string(), z.unknown())
      },
      annotations: { readOnlyHint: true, destructiveHint: false, openWorldHint: false }
    },
    async () => {
      const state = loadState();
      const c = state.latestContext;
      const out = {
        generatedAt: c?.generatedAt ?? 0,
        priorityCandidates: c?.priorityCandidates ?? [],
        personalModel: c?.personalModel ?? {},
        activeSituations: c?.activeSituations ?? c?.situations ?? [],
        uncertainCases: c?.uncertainCases ?? [],
        recentOutcomes: c?.recentOutcomes ?? [],
        policy: state.policy as unknown as Record<string, unknown>
      };
      return {
        structuredContent: out,
        content: [{ type: "text", text: c ? "Loaded the latest Cortex priority review pack." : "No Cortex context pack has arrived yet." }]
      };
    }
  );

  server.registerTool(
    "publish_policy_pack",
    {
      title: "Teach Cortex priority policy",
      description: "Use this when you have enough grounded evidence to improve how Cortex decides what deserves the user's attention. Writes a compact policy that Cortex can apply locally; do not encode one-off guesses as permanent rules.",
      inputSchema: policyInput,
      outputSchema: { accepted: z.boolean(), version: z.string(), generatedAt: z.number() },
      annotations: { readOnlyHint: false, destructiveHint: false, openWorldHint: false, idempotentHint: true }
    },
    async (input) => {
      const state = loadState();
      const policy: PolicyPack = {
        version: input.version,
        generatedAt: Date.now(),
        ttlMs: input.ttlMs,
        attentionThreshold: input.attentionThreshold,
        maxNowItems: input.maxNowItems,
        interruptionPenaltyScale: input.interruptionPenaltyScale,
        featureWeights: { ...input.featureWeights },
        boosts: input.boosts.map((x) => ({ match: x.match.trim(), weight: x.weight })).filter((x) => x.match),
        teacherNotes: input.teacherNotes.trim()
      };
      state.policy = policy;
      saveState(state);
      return {
        structuredContent: { accepted: true, version: policy.version, generatedAt: policy.generatedAt },
        content: [{ type: "text", text: `Cortex priority policy ${policy.version} is ready for the device to pull.` }]
      };
    }
  );

  return server;
}

const httpServer = createServer(async (req, res) => {
  if (!req.url) return json(res, 400, { error: "missing_url" });
  const url = new URL(req.url, `http://${req.headers.host ?? "localhost"}`);

  if (req.method === "GET" && url.pathname === "/") {
    return json(res, 200, { service: "Cortex ChatGPT Policy Bridge", mcp: MCP_PATH, device: "/device/context" });
  }
  if (req.method === "GET" && url.pathname === "/health") {
    const state = loadState();
    return json(res, 200, {
      ok: true,
      contextGeneratedAt: state.latestContext?.generatedAt ?? 0,
      policyVersion: state.policy.version,
      policyGeneratedAt: state.policy.generatedAt
    });
  }
  if (url.pathname.startsWith("/device/") && !deviceAuthorized(req)) {
    return json(res, 401, { error: "unauthorized" });
  }
  if (req.method === "POST" && url.pathname === "/device/context") {
    try {
      const body = await readJson(req) as ContextPack;
      if (!body || typeof body !== "object" || body.schemaVersion !== 2 ||
          !Array.isArray(body.priorityCandidates) || !Array.isArray(body.activeSituations) ||
          !body.personalModel || !Array.isArray(body.recentOutcomes)) {
        return json(res, 400, { error: "invalid_context_pack" });
      }
      const state = loadState();
      state.latestContext = body;
      state.contextHistory = [...state.contextHistory, body].slice(-24);
      saveState(state);
      return json(res, 200, { accepted: true, policyVersion: state.policy.version });
    } catch (error) {
      return json(res, 400, { error: "bad_json", detail: error instanceof Error ? error.message : String(error) });
    }
  }
  if (req.method === "GET" && url.pathname === "/device/policy") {
    const state = loadState();
    return json(res, 200, { policy: state.policy });
  }

  if (req.method === "OPTIONS" && url.pathname === MCP_PATH) {
    res.writeHead(204, {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "POST, GET, DELETE, OPTIONS",
      "Access-Control-Allow-Headers": "content-type, mcp-session-id",
      "Access-Control-Expose-Headers": "Mcp-Session-Id"
    });
    return res.end();
  }
  if (url.pathname === MCP_PATH && req.method && new Set(["POST", "GET", "DELETE"]).has(req.method)) {
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Access-Control-Expose-Headers", "Mcp-Session-Id");
    const server = createCortexServer();
    const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined, enableJsonResponse: true });
    res.on("close", () => { transport.close(); server.close(); });
    try {
      await server.connect(transport);
      await transport.handleRequest(req, res);
    } catch (error) {
      console.error("MCP request failed", error);
      if (!res.headersSent) res.writeHead(500).end("Internal server error");
    }
    return;
  }

  return json(res, 404, { error: "not_found" });
});

httpServer.listen(PORT, () => {
  console.log(`Cortex ChatGPT Policy Bridge listening on http://localhost:${PORT}${MCP_PATH}`);
});
