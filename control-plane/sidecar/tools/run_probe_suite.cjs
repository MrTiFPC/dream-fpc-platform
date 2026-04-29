#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
const vm = require("vm");

const ROOT = path.resolve(__dirname, "..");
const DEFAULT_STUDIO_JS = path.join(ROOT, "studio", "assets", "studio.js");
const DEFAULT_REPORT_DIR = path.join(process.cwd(), "server", "game", "fpc_studio", "reports");
const DEFAULT_BASE_URL = "http://127.0.0.1:8000";

function parseArgs(argv) {
  const args = {
    baseUrl: DEFAULT_BASE_URL,
    studioJs: DEFAULT_STUDIO_JS,
    reportDir: DEFAULT_REPORT_DIR,
    timeoutMs: 30000,
    pollMs: 500,
    speaker: "marc",
    suite: "",
    steps: [],
    reportName: "",
  };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--base-url") {
      args.baseUrl = String(argv[++i] || "").trim();
    } else if (arg === "--studio-js") {
      args.studioJs = path.resolve(String(argv[++i] || "").trim());
    } else if (arg === "--report-dir") {
      args.reportDir = path.resolve(String(argv[++i] || "").trim());
    } else if (arg === "--timeout-ms") {
      args.timeoutMs = Number(argv[++i] || args.timeoutMs);
    } else if (arg === "--poll-ms") {
      args.pollMs = Number(argv[++i] || args.pollMs);
    } else if (arg === "--speaker") {
      args.speaker = String(argv[++i] || "").trim().toLowerCase();
    } else if (arg === "--suite") {
      args.suite = String(argv[++i] || "").trim();
    } else if (arg === "--steps") {
      args.steps = String(argv[++i] || "")
        .split(",")
        .map((value) => value.trim())
        .filter(Boolean);
    } else if (arg === "--report-name") {
      args.reportName = String(argv[++i] || "").trim();
    } else if (arg === "--help" || arg === "-h") {
      printHelp();
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  if (!args.suite && !args.steps.length) {
    throw new Error("Provide --suite <suiteId> or --steps <id,id,id>.");
  }
  return args;
}

function printHelp() {
  console.log(`Usage:
  node run_probe_suite.cjs --suite warg_acceptance_v1 [--speaker marc]
  node run_probe_suite.cjs --steps warg_farm_85,warg_unban_request [--speaker marc]

Options:
  --base-url    Sidecar base URL (default: ${DEFAULT_BASE_URL})
  --studio-js   Path to studio.js probe definitions
  --report-dir  Directory for JSON reports
  --timeout-ms  Poll timeout per probe
  --poll-ms     Poll interval
  --speaker     Probe library family (default: marc)
  --suite       Suite id from STUDIO_PROBE_SUITES
  --steps       Comma-separated explicit step ids
  --report-name Optional report file stem
`);
}

function extractLiteral(source, marker) {
  const markerIndex = source.indexOf(marker);
  if (markerIndex === -1) {
    throw new Error(`Could not find marker: ${marker}`);
  }
  const braceIndex = source.indexOf("{", markerIndex);
  if (braceIndex === -1) {
    throw new Error(`Could not find opening brace for marker: ${marker}`);
  }
  let depth = 0;
  let quote = "";
  let escape = false;
  for (let i = braceIndex; i < source.length; i += 1) {
    const char = source[i];
    if (quote) {
      if (escape) {
        escape = false;
      } else if (char === "\\") {
        escape = true;
      } else if (char === quote) {
        quote = "";
      }
      continue;
    }
    if (char === '"' || char === "'" || char === "`") {
      quote = char;
      continue;
    }
    if (char === "{") {
      depth += 1;
    } else if (char === "}") {
      depth -= 1;
      if (depth === 0) {
        return source.slice(braceIndex, i + 1);
      }
    }
  }
  throw new Error(`Unterminated object literal for marker: ${marker}`);
}

function loadStudioDefinitions(studioJsPath) {
  const source = fs.readFileSync(studioJsPath, "utf8");
  const libraryLiteral = extractLiteral(source, "const STUDIO_PROBE_PRESETS =");
  const suitesLiteral = extractLiteral(source, "const STUDIO_PROBE_SUITES =");
  const library = vm.runInNewContext(`(${libraryLiteral})`, {});
  const suites = vm.runInNewContext(`(${suitesLiteral})`, {});
  return { library, suites };
}

function flattenLibrary(library) {
  const steps = new Map();
  Object.entries(library || {}).forEach(([family, entries]) => {
    (entries || []).forEach((entry) => {
      if (entry && entry.id) {
        steps.set(entry.id, { ...entry, __family: family });
      }
    });
  });
  return steps;
}

function flattenSuites(suites) {
  const flattened = new Map();
  Object.entries(suites || {}).forEach(([family, entries]) => {
    (entries || []).forEach((entry) => {
      if (entry && entry.id) {
        flattened.set(entry.id, { ...entry, __family: family });
      }
    });
  });
  return flattened;
}

function normalizedSuiteTerms(values) {
  return Array.isArray(values)
    ? values.map((value) => String(value || "").trim().toLowerCase()).filter(Boolean)
    : [];
}

function readPathValue(target, pathExpression) {
  return String(pathExpression || "")
    .split(".")
    .map((part) => part.trim())
    .filter(Boolean)
    .reduce((current, part) => {
      if (current == null || typeof current !== "object") {
        return undefined;
      }
      return current[part];
    }, target);
}

function collectPathText(result, paths) {
  return paths
    .map((entry) => readPathValue(result, entry))
    .flatMap((value) => {
      if (Array.isArray(value)) {
        return value.map((item) => JSON.stringify(item));
      }
      if (value == null) {
        return [];
      }
      if (typeof value === "string") {
        return [value];
      }
      return [JSON.stringify(value)];
    })
    .join(" ")
    .toLowerCase();
}

function evaluateSuiteStep(step, result) {
  const replyLine = String(result?.reply?.line || "").trim();
  const replySource = String(result?.reply?.source || "").trim();
  const category = String(result?.classification?.messageCategory || "").trim();
  const focusIntent = String(result?.classification?.focusIntent || "").trim();
  const routeProfile = result?.classification?.routeProfile || {};
  const routeLane = String(routeProfile.lane || result?.classification?.routeLane || "").trim();
  const routeSpeechAct = String(routeProfile.speechAct || result?.classification?.routeSpeechAct || "").trim();
  const routeKnowledgeNeed = String(routeProfile.knowledgeNeed || result?.classification?.routeKnowledgeNeed || "").trim();
  const routePrimaryTopic = String(routeProfile.primaryTopic || result?.classification?.routePrimaryTopic || "").trim();
  const replyLower = replyLine.toLowerCase();
  const replyWords = replyLine ? replyLine.split(/\s+/).filter(Boolean).length : 0;
  const checks = [];
  const pushCheck = (label, ok, expected, actual) => {
    checks.push({ label, ok, expected, actual });
  };

  if (step.expectedCategory) {
    pushCheck("category", category === step.expectedCategory, step.expectedCategory, category || "missing");
  }
  if (step.expectedFocusIntent) {
    pushCheck("focusIntent", focusIntent === step.expectedFocusIntent, step.expectedFocusIntent, focusIntent || "missing");
  }
  if (step.expectedRouteLane) {
    pushCheck("routeLane", routeLane === step.expectedRouteLane, step.expectedRouteLane, routeLane || "missing");
  }
  if (step.expectedSpeechAct) {
    pushCheck("speechAct", routeSpeechAct === step.expectedSpeechAct, step.expectedSpeechAct, routeSpeechAct || "missing");
  }
  if (step.expectedKnowledgeNeed) {
    pushCheck("knowledgeNeed", routeKnowledgeNeed === step.expectedKnowledgeNeed, step.expectedKnowledgeNeed, routeKnowledgeNeed || "missing");
  }
  if (step.expectedPrimaryTopic) {
    pushCheck("primaryTopic", routePrimaryTopic === step.expectedPrimaryTopic, step.expectedPrimaryTopic, routePrimaryTopic || "missing");
  }
  if (typeof step.expectedSpeakNow === "boolean") {
    pushCheck("speakNow", Boolean(result?.reply?.speakNow) === step.expectedSpeakNow, String(step.expectedSpeakNow), String(Boolean(result?.reply?.speakNow)));
  }
  if (step.requiredSource) {
    pushCheck("source", replySource === step.requiredSource, step.requiredSource, replySource || "missing");
  }
  if (Number.isFinite(Number(step.maxReplyWords)) && Boolean(result?.reply?.speakNow)) {
    const maxReplyWords = Number(step.maxReplyWords);
    pushCheck("maxReplyWords", replyWords <= maxReplyWords, String(maxReplyWords), String(replyWords));
  }

  const requiredTerms = normalizedSuiteTerms(step.requiredTerms);
  if (requiredTerms.length) {
    const missing = requiredTerms.filter((term) => !replyLower.includes(term));
    pushCheck("mustContainAll", missing.length === 0, requiredTerms.join(", "), missing.length ? `missing: ${missing.join(", ")}` : "present");
  }

  const requiredAnyTerms = normalizedSuiteTerms(step.requiredAnyTerms);
  if (requiredAnyTerms.length) {
    const matched = requiredAnyTerms.filter((term) => replyLower.includes(term));
    pushCheck("mustContainAny", matched.length > 0, requiredAnyTerms.join(" | "), matched.length ? `matched: ${matched.join(", ")}` : "no required terms present");
  }

  const forbiddenTerms = normalizedSuiteTerms(step.forbiddenTerms);
  if (forbiddenTerms.length) {
    const found = forbiddenTerms.filter((term) => replyLower.includes(term));
    pushCheck("forbiddenTerms", found.length === 0, forbiddenTerms.join(", "), found.length ? `found: ${found.join(", ")}` : "clean");
  }

  const requiredNonEmptyPaths = Array.isArray(step.requiredNonEmptyPaths)
    ? step.requiredNonEmptyPaths.map((entry) => String(entry || "").trim()).filter(Boolean)
    : [];
  requiredNonEmptyPaths.forEach((pathExpression) => {
    const value = readPathValue(result, pathExpression);
    const normalized = typeof value === "string" ? value.trim() : (value == null ? "" : String(value).trim());
    pushCheck("nonEmptyPath", Boolean(normalized), pathExpression, normalized || "missing");
  });

  const requiredAnyTermsInPaths = Array.isArray(step.requiredAnyTermsInPaths) ? step.requiredAnyTermsInPaths : [];
  requiredAnyTermsInPaths.forEach((requirement) => {
    const paths = Array.isArray(requirement?.paths) ? requirement.paths.map((entry) => String(entry || "").trim()).filter(Boolean) : [];
    const terms = normalizedSuiteTerms(requirement?.terms);
    if (!paths.length || !terms.length) {
      return;
    }
    const pathText = collectPathText(result, paths);
    const matched = terms.filter((term) => pathText.includes(term));
    pushCheck("pathTermsAny", matched.length > 0, `${paths.join(" | ")} => ${terms.join(" | ")}`, matched.length ? `matched: ${matched.join(", ")}` : "no required terms present");
  });

  const passedChecks = checks.filter((check) => check.ok).length;
  const totalChecks = checks.length;
  return {
    checks,
    passedChecks,
    totalChecks,
    failed: totalChecks > 0 ? passedChecks !== totalChecks : false,
  };
}

async function postJson(url, payload) {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    throw new Error(`POST ${url} failed: ${response.status} ${response.statusText}`);
  }
  return response.json();
}

async function getJson(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`GET ${url} failed: ${response.status} ${response.statusText}`);
  }
  return response.json();
}

async function queueProbe(baseUrl, step, timeoutMs, pollMs) {
  const queued = await postJson(`${baseUrl}/studio/commands`, {
    action: "conversation_probe",
    fpcId: step.__family || "marc",
    playerName: step.playerName,
    channel: step.channel,
    text: step.text,
  });
  const commandId = String(queued.commandId || queued.id || "").trim();
  if (!commandId) {
    throw new Error(`Probe queue response did not include a command id for step ${step.id}`);
  }
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    await new Promise((resolve) => setTimeout(resolve, pollMs));
    const result = await getJson(`${baseUrl}/studio/results/${encodeURIComponent(commandId)}`);
    if (String(result.status || "").trim().toLowerCase() !== "pending") {
      return result;
    }
  }
  throw new Error(`Timed out waiting for probe result for step ${step.id}`);
}

function buildReportName(stem) {
  const timestamp = new Date().toISOString().replace(/[-:]/g, "").replace(/\.\d+Z$/, "Z").replace("T", "_");
  return `${stem}_${timestamp}.json`;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const { library, suites } = loadStudioDefinitions(args.studioJs);
  const flatLibrary = flattenLibrary(library);
  const flatSuites = flattenSuites(suites);

  let selectedSteps = [];
  let suiteMeta = null;
  if (args.suite) {
    suiteMeta = flatSuites.get(args.suite);
    if (!suiteMeta) {
      throw new Error(`Unknown suite id: ${args.suite}`);
    }
    selectedSteps = (suiteMeta.steps || []).map((stepId) => {
      const step = flatLibrary.get(stepId);
      if (!step) {
        throw new Error(`Suite ${args.suite} references unknown step: ${stepId}`);
      }
      return step;
    });
  } else {
    selectedSteps = args.steps.map((stepId) => {
      const step = flatLibrary.get(stepId);
      if (!step) {
        throw new Error(`Unknown step id: ${stepId}`);
      }
      return step;
    });
  }

  if (args.speaker) {
    selectedSteps = selectedSteps.map((step) => ({ ...step, __family: args.speaker }));
  }

  const report = {
    generatedAt: new Date().toISOString(),
    baseUrl: args.baseUrl,
    studioJs: args.studioJs,
    suiteId: args.suite || "",
    suiteLabel: suiteMeta?.label || "",
    suiteDescription: suiteMeta?.description || "",
    speaker: args.speaker || "",
    totals: {
      stepCount: selectedSteps.length,
      passedSteps: 0,
      failedSteps: 0,
      passedChecks: 0,
      totalChecks: 0,
    },
    steps: [],
  };

  for (const step of selectedSteps) {
    process.stdout.write(`Running ${step.id}... `);
    const startedAt = Date.now();
    try {
      const result = await queueProbe(args.baseUrl, step, args.timeoutMs, args.pollMs);
      if (String(result.status || "").trim().toLowerCase() !== "ok") {
        throw new Error(`Probe returned status=${result.status || "missing"}`);
      }
      const probe = result.probe || {};
      const evaluation = evaluateSuiteStep(step, probe);
      report.steps.push({
        id: step.id,
        label: step.label || step.id,
        text: step.text,
        channel: step.channel,
        expected: {
          routeLane: step.expectedRouteLane || "",
          speechAct: step.expectedSpeechAct || "",
          knowledgeNeed: step.expectedKnowledgeNeed || "",
          primaryTopic: step.expectedPrimaryTopic || "",
          speakNow: typeof step.expectedSpeakNow === "boolean" ? step.expectedSpeakNow : null,
          requiredSource: step.requiredSource || "",
          maxReplyWords: Number.isFinite(Number(step.maxReplyWords)) ? Number(step.maxReplyWords) : null,
          forbiddenTerms: Array.isArray(step.forbiddenTerms) ? step.forbiddenTerms : [],
        },
        startedAt,
        finishedAt: Date.now(),
        durationMs: Date.now() - startedAt,
        failed: evaluation.failed,
        passedChecks: evaluation.passedChecks,
        totalChecks: evaluation.totalChecks,
        checks: evaluation.checks,
        result,
      });
      report.totals.passedChecks += evaluation.passedChecks;
      report.totals.totalChecks += evaluation.totalChecks;
      if (evaluation.failed) {
        report.totals.failedSteps += 1;
        console.log(`FAIL (${evaluation.passedChecks}/${evaluation.totalChecks})`);
      } else {
        report.totals.passedSteps += 1;
        console.log(`PASS (${evaluation.passedChecks}/${evaluation.totalChecks})`);
      }
    } catch (error) {
      report.steps.push({
        id: step.id,
        label: step.label || step.id,
        text: step.text,
        channel: step.channel,
        startedAt,
        finishedAt: Date.now(),
        durationMs: Date.now() - startedAt,
        failed: true,
        passedChecks: 0,
        totalChecks: 0,
        checks: [],
        error: String(error && error.message ? error.message : error),
      });
      report.totals.failedSteps += 1;
      console.log(`ERROR (${String(error && error.message ? error.message : error)})`);
    }
  }

  fs.mkdirSync(args.reportDir, { recursive: true });
  const reportName = args.reportName
    ? (args.reportName.toLowerCase().endsWith(".json") ? args.reportName : `${args.reportName}.json`)
    : buildReportName(args.suite || (args.steps.length === 1 ? args.steps[0] : "probe_suite"));
  const reportPath = path.join(args.reportDir, reportName);
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2) + "\n", "utf8");

  console.log("");
  console.log(`Report: ${reportPath}`);
  console.log(`Steps: ${report.totals.passedSteps}/${report.totals.stepCount} passed`);
  console.log(`Checks: ${report.totals.passedChecks}/${report.totals.totalChecks} passed`);
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exit(1);
});
