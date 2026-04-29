import json
import logging
import os
import re
import shlex
import socket
import subprocess
import threading
import time
import uuid
from copy import deepcopy
from pathlib import Path
from typing import Any, Callable, Dict, List

import httpx
from assistant_runtime import answer_assistant_question, assistant_status, build_assistant_index, load_assistant_index
from fastapi import FastAPI, HTTPException
from fastapi.responses import HTMLResponse, Response
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

LOGGER = logging.getLogger("fakeplayer_ollama_sidecar")
logging.basicConfig(level=logging.INFO)

REPO_ROOT = Path(__file__).resolve().parents[3]
OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://127.0.0.1:11434")
OLLAMA_HEALTH_PATH = os.getenv("OLLAMA_HEALTH_PATH", "/api/tags")
OLLAMA_CHAT_PATH = os.getenv("OLLAMA_CHAT_PATH", "/api/chat")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "llama3.2-3b-fpc")
OLLAMA_PLANNER_MODEL = os.getenv("OLLAMA_PLANNER_MODEL", OLLAMA_MODEL)
OLLAMA_REPLY_MODEL = os.getenv("OLLAMA_REPLY_MODEL", OLLAMA_MODEL)
OLLAMA_TIMEOUT_SECONDS = float(os.getenv("OLLAMA_TIMEOUT_SECONDS", "45.0"))
OLLAMA_PLANNER_TIMEOUT_SECONDS = float(os.getenv("OLLAMA_PLANNER_TIMEOUT_SECONDS", "6.0"))
OLLAMA_REPLY_TIMEOUT_SECONDS = float(os.getenv("OLLAMA_REPLY_TIMEOUT_SECONDS", "12.0"))
ASSISTANT_TIMEOUT_SECONDS = float(os.getenv("FPC_CONSOLE_ASSISTANT_TIMEOUT_SECONDS", "25.0"))
OLLAMA_TOP_P = float(os.getenv("OLLAMA_TOP_P", "0.90"))
OLLAMA_REPEAT_PENALTY = float(os.getenv("OLLAMA_REPEAT_PENALTY", "1.08"))
OLLAMA_SOCIAL_TEMPERATURE_FLOOR = float(os.getenv("OLLAMA_SOCIAL_TEMPERATURE_FLOOR", "0.45"))
OLLAMA_RETRY_BACKOFF_SECONDS = float(os.getenv("OLLAMA_RETRY_BACKOFF_SECONDS", "0.2"))
DEFAULT_PLANNER_PROVIDER = os.getenv("FAKEPLAYER_PLANNER_PROVIDER", "rules").strip().lower()
DEFAULT_REPLY_PROVIDER = os.getenv("FAKEPLAYER_REPLY_PROVIDER", "ollama").strip().lower()
ASSISTANT_EMBEDDING_MODEL = os.getenv("FPC_CONSOLE_EMBEDDING_MODEL", "embeddinggemma").strip() or "embeddinggemma"
ASSISTANT_FAST_MODEL = os.getenv("FPC_CONSOLE_FAST_MODEL", OLLAMA_REPLY_MODEL).strip() or OLLAMA_REPLY_MODEL
ASSISTANT_DEEP_MODEL = os.getenv("FPC_CONSOLE_DEEP_MODEL", "qwen3-14b-local").strip() or "qwen3-14b-local"
ALLOWED_MOOD_TAGS = ["calm", "confident", "playful", "focused", "cautious", "bored", "cocky", "annoyed"]
REPLY_SETTINGS_PATH = REPO_ROOT / "dist" / "game" / "data" / "fake_players" / "reply_settings.json"
FPC_DEFINITIONS_PATH = REPO_ROOT / "dist" / "game" / "data" / "fake_players" / "fpcs.json"
FPC_TEMPLATES_PATH = REPO_ROOT / "dist" / "game" / "data" / "fake_players" / "fpc_templates.json"
PERSONA_PROFILES_PATH = REPO_ROOT / "dist" / "game" / "data" / "fake_players" / "persona_profiles.json"
REPLY_BANKS_PATH = REPO_ROOT / "dist" / "game" / "data" / "fake_players" / "reply_banks.json"
KNOWLEDGE_CARDS_PATH = REPO_ROOT / "dist" / "game" / "data" / "fake_players" / "knowledge_cards.json"
ROUTING_RULES_PATH = REPO_ROOT / "dist" / "game" / "data" / "fake_players" / "routing_rules.json"
SOURCE_FAKE_PLAYERS_ROOT = REPO_ROOT / "dist" / "game" / "data" / "fake_players"
STUDIO_ROOT = REPO_ROOT / "server" / "game" / "fpc_studio"
STUDIO_UI_ROOT = Path(__file__).resolve().parent / "studio"
STUDIO_UI_ASSETS_ROOT = STUDIO_UI_ROOT / "assets"
SERVER_ROOT = REPO_ROOT / "server"
SERVER_LOGIN_ROOT = SERVER_ROOT / "login"
SERVER_GAME_ROOT = SERVER_ROOT / "game"
RUNTIME_FAKE_PLAYERS_ROOT = SERVER_GAME_ROOT / "data" / "fake_players"
LOGIN_SERVER_JAVA_CFG = SERVER_LOGIN_ROOT / "java.cfg"
GAME_SERVER_JAVA_CFG = SERVER_GAME_ROOT / "java.cfg"
CREATE_NO_WINDOW = getattr(subprocess, "CREATE_NO_WINDOW", 0)
CREATE_NEW_PROCESS_GROUP = getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0)
DETACHED_PROCESS = getattr(subprocess, "DETACHED_PROCESS", 0)
WORKFLOW_ACTION_LABELS: Dict[str, str] = {
    "compile_source": "Compile Source",
    "deploy_runtime": "Deploy Runtime",
    "stop_runtime": "Stop Runtime",
    "start_runtime": "Start Runtime",
    "restart_runtime": "Restart Runtime",
    "deploy_and_restart_runtime": "Deploy + Restart Runtime",
}
WORKFLOW_ACTION_ORDER = [
    "compile_source",
    "deploy_runtime",
    "stop_runtime",
    "start_runtime",
    "restart_runtime",
    "deploy_and_restart_runtime",
]
WORKFLOW_HISTORY_LIMIT = 12
WORKFLOW_OUTPUT_LIMIT = 16000
_WORKFLOW_LOCK = threading.Lock()
_WORKFLOW_OPERATIONS: Dict[str, Dict[str, Any]] = {}
_WORKFLOW_HISTORY: List[str] = []
_WORKFLOW_CURRENT_ID: str | None = None
_ASSISTANT_INDEX_LOCK = threading.Lock()
_ASSISTANT_INDEX_STATE: Dict[str, Any] = {
    "indexing": False,
    "lastTrigger": "",
    "lastQueuedAtMs": 0,
    "lastStartedAtMs": 0,
    "lastCompletedAtMs": 0,
    "lastError": "",
}
DEFAULT_REPLY_SETTINGS: Dict[str, Any] = {
    "recentConversationTurns": 4,
    "recentConversationTtlMs": 1200000,
    "followUpStrictness": "high",
    "generalMaxWords": 7,
    "whisperMaxWords": 20,
    "publicMaxWords": 7,
    "whisperAllowSecondSentence": True,
    "knowledgeConfidencePolicy": "strict",
    "progressionCoachingLevel": "mentor",
    "pvpConflictPolicy": "de_escalate",
    "relationshipWarmth": "warm_but_not_constant",
    "offscopeClassKnowledge": "honest_partial",
    "farmAdviceMode": "level_goal_confidence",
    "travelStyle": "system_first",
    "socialReplyMode": "model_first_guided",
    "runtimeBudgetMode": "adaptive",
    "generalBudgetWindowSeconds": 20,
    "generalBudgetMaxReplies": 3,
    "shoutBudgetWindowSeconds": 30,
    "shoutBudgetMaxReplies": 2,
    "worldBudgetWindowSeconds": 45,
    "worldBudgetMaxReplies": 1,
    "repeatPromptWindowSeconds": 45,
    "repeatPromptReuseLimit": 3,
}
_REPLY_SETTINGS_CACHE: Dict[str, Any] = dict(DEFAULT_REPLY_SETTINGS)
_REPLY_SETTINGS_MTIME: float | None = None
_ROUTING_RULES_CACHE: List[Dict[str, Any]] = []
_ROUTING_RULES_MTIME: float | None = None
_RUNTIME_REPLY_BUDGET_LOCK = threading.Lock()
_RUNTIME_REPLY_BUDGET_WINDOWS: Dict[str, List[float]] = {}
_RUNTIME_REPLY_CACHE: Dict[str, Dict[str, Any]] = {}
SELFHOOD_CATEGORIES = {
    "self_identity",
    "self_story",
    "self_belief",
    "self_preference",
    "self_state_reflection",
    "creator_opinion",
    "bond_opinion",
}
MODEL_FIRST_SOCIAL_CATEGORIES = {
    "greeting",
    "status",
    "thanks",
    "apology",
    "repair",
    "affection",
    "respect",
    "praise",
    "reassurance",
    "name",
    "social_invite",
    "companionship",
    "help",
    "memory",
    "quest_story",
    "smalltalk",
    "self_identity",
    "self_story",
    "self_belief",
    "self_preference",
    "self_state_reflection",
    "creator_opinion",
    "bond_opinion",
    "entity_status",
    "entity_relationship",
    "entity_story",
    "entity_reason",
    "entity_memory",
}
GUIDANCE_V1A_FPCS = {"marc", "elyra"}
GUIDANCE_V1A_KNOWLEDGE_TYPES = {
    "travel_destination",
    "farming",
    "progression_route",
    "progression_strength",
    "system_basics",
    "class_identity",
    "class_progression",
    "class_skills",
    "class_choice",
    "itemization",
    "currency",
}
GUIDANCE_V1A_SHORT_CLAUSES: Dict[str, Dict[str, str]] = {
    "marc": {
        "travel_destination": "Keep the route simple first.",
        "farming": "Stay in the honest band first.",
        "progression_route": "Take the next safe step first.",
        "progression_strength": "Stabilize the weak point first.",
        "class_choice": "Choose the one you can grow into.",
        "itemization": "Fix the weakest core piece first.",
    },
    "elyra": {
        "travel_destination": "Take the cleaner route first.",
        "farming": "Choose the cleaner lane first.",
        "progression_route": "Take the route that fails cleanly.",
        "progression_strength": "Cut the weak link first.",
        "class_choice": "Choose the risks you can actually control.",
        "itemization": "Repair the weak link before vanity.",
    },
}
BAD_REPLY_ENDINGS = {
    "a",
    "an",
    "and",
    "or",
    "the",
    "to",
    "for",
    "of",
    "with",
    "than",
    "then",
    "that",
    "this",
    "these",
    "those",
    "my",
    "your",
    "our",
    "their",
    "not",
    "class",
    "level",
    "levels",
    "skills",
    "weapon",
    "weapons",
    "gear",
    "adena",
    "xp",
    "exp",
    "recommended",
    "without",
    "is",
    "are",
    "be",
}
CANON_TOKEN_STOPWORDS = BAD_REPLY_ENDINGS | {
    "still",
    "more",
    "than",
    "into",
    "from",
    "when",
    "where",
    "while",
    "through",
    "someone",
    "something",
    "anyone",
    "anymore",
    "their",
    "there",
    "those",
    "these",
    "would",
    "could",
    "should",
    "might",
    "being",
    "become",
    "becoming",
    "because",
    "without",
    "around",
    "under",
    "over",
    "very",
    "much",
    "just",
    "only",
    "really",
    "truly",
    "honestly",
    "actually",
}
FIRST_PERSON_VERB_MAP = {
    "is": "am",
    "was": "was",
    "has": "have",
    "knows": "know",
    "thinks": "think",
    "prefers": "prefer",
    "understands": "understand",
    "reads": "read",
    "waits": "wait",
    "tries": "try",
    "hates": "hate",
    "fears": "fear",
    "feels": "feel",
    "despises": "despise",
    "resents": "resent",
    "remembers": "remember",
    "wants": "want",
    "likes": "like",
    "comes": "come",
    "acts": "act",
    "presents": "present",
    "speaks": "speak",
    "sounds": "sound",
    "stands": "stand",
    "judges": "judge",
    "jokes": "joke",
    "grins": "grin",
    "keeps": "keep",
    "blames": "blame",
    "corrects": "correct",
    "holds": "hold",
    "measures": "measure",
    "returns": "return",
    "resets": "reset",
    "steers": "steer",
    "gets": "get",
    "takes": "take",
    "marks": "mark",
    "lowers": "lower",
    "reopens": "reopen",
}

app = FastAPI(title="fakeplayer-ollama-sidecar")
app.mount("/studio/assets", StaticFiles(directory=str(STUDIO_UI_ASSETS_ROOT)), name="studio-assets")


@app.on_event("startup")
def _studio_startup_hooks() -> None:
    _ensure_assistant_index("startup")


@app.get("/favicon.ico", include_in_schema=False)
def favicon() -> Response:
    return Response(status_code=204)


def _studio_file(*parts: str) -> Path:
    return STUDIO_ROOT.joinpath(*parts)


def _load_studio_json(path: Path) -> Dict[str, Any]:
    if not path.exists():
        raise HTTPException(status_code=404, detail=f"Studio file not found: {path.name}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Could not read studio file {path.name}: {exc}") from exc


def _load_studio_json_optional(path: Path) -> Dict[str, Any] | None:
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Could not read studio file {path.name}: {exc}") from exc


def _studio_trace_speaker(entry: Any) -> str:
    if not isinstance(entry, dict):
        return ""
    probe = entry.get("probe") if isinstance(entry.get("probe"), dict) else {}
    snapshot = entry.get("snapshot") if isinstance(entry.get("snapshot"), dict) else {}
    return str(probe.get("speakerName") or probe.get("speakerId") or snapshot.get("speakerName") or snapshot.get("speakerId") or "")


def _studio_trace_player(entry: Any) -> str:
    if not isinstance(entry, dict):
        return ""
    probe = entry.get("probe") if isinstance(entry.get("probe"), dict) else {}
    snapshot = entry.get("snapshot") if isinstance(entry.get("snapshot"), dict) else {}
    return str(probe.get("playerName") or snapshot.get("playerName") or "")


def _studio_trace_category(entry: Any) -> str:
    if not isinstance(entry, dict):
        return ""
    probe = entry.get("probe") if isinstance(entry.get("probe"), dict) else {}
    classification = probe.get("classification") if isinstance(probe.get("classification"), dict) else {}
    return str(classification.get("messageCategory") or "")


def _matches_trace_filter(value: str, expected: str | None) -> bool:
    if not expected:
        return True
    return expected.strip().lower() in (value or "").strip().lower()


def _load_studio_jsonl(
    path: Path,
    limit: int = 20,
    speaker: str | None = None,
    player: str | None = None,
    category: str | None = None,
    contains: str | None = None,
) -> Dict[str, Any]:
    if not path.exists():
        raise HTTPException(status_code=404, detail=f"Studio trace not found: {path.name}")
    try:
        lines = [line.strip() for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
        normalized_limit = max(1, min(int(limit), 200))
        entries: List[Any] = []
        contains_text = (contains or "").strip().lower()
        for raw in lines:
            try:
                parsed: Any = json.loads(raw)
            except Exception:
                parsed = {"raw": raw}

            if not _matches_trace_filter(_studio_trace_speaker(parsed), speaker):
                continue
            if not _matches_trace_filter(_studio_trace_player(parsed), player):
                continue
            if not _matches_trace_filter(_studio_trace_category(parsed), category):
                continue
            if contains_text and contains_text not in json.dumps(parsed, ensure_ascii=False).lower():
                continue
            entries.append(parsed)

        selected = entries[-normalized_limit:]
        return {
            "trace": path.stem,
            "path": str(path),
            "count": len(lines),
            "matched": len(entries),
            "returned": len(selected),
            "filters": {
                "speaker": speaker or "",
                "player": player or "",
                "category": category or "",
                "contains": contains or "",
            },
            "entries": selected,
        }
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Could not read studio trace {path.name}: {exc}") from exc


def _write_studio_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = path.with_name(path.name + ".tmp")
    temp_path.write_text(content, encoding="utf-8")
    temp_path.replace(path)


def _write_studio_json(path: Path, payload: Dict[str, Any]) -> None:
    _write_studio_text(path, json.dumps(payload, ensure_ascii=False, indent=2))


def _runtime_mirror_path(path: Path) -> Path | None:
    try:
        relative = path.relative_to(SOURCE_FAKE_PLAYERS_ROOT)
    except ValueError:
        return None
    return RUNTIME_FAKE_PLAYERS_ROOT / relative


def _write_fakeplayer_source_and_runtime(path: Path, content: str) -> None:
    _write_studio_text(path, content)
    runtime_mirror = _runtime_mirror_path(path)
    if runtime_mirror and (runtime_mirror != path):
        _write_studio_text(runtime_mirror, content)


def _studio_bookmarks_root() -> Path:
    path = _studio_file("bookmarks")
    path.mkdir(parents=True, exist_ok=True)
    return path


def _studio_bookmark_slug(value: str | None) -> str:
    normalized = re.sub(r"[^a-z0-9_-]+", "_", (value or "").strip().lower()).strip("_")
    return normalized or "bookmark"


def _studio_bookmark_file(bookmark_id: str) -> Path:
    normalized = _studio_bookmark_slug(bookmark_id)
    return _studio_bookmarks_root() / f"{normalized}.json"


def _list_studio_bookmarks() -> Dict[str, Any]:
    root = _studio_bookmarks_root()
    entries: List[Dict[str, Any]] = []
    for path in sorted(root.glob("*.json"), key=lambda item: item.stat().st_mtime, reverse=True):
        payload = _load_studio_json_optional(path)
        if not isinstance(payload, dict):
            continue
        entries.append(
            {
                "id": payload.get("id") or path.stem,
                "name": payload.get("name") or path.stem,
                "createdAtMs": payload.get("createdAtMs") or int(path.stat().st_mtime * 1000),
                "fpcId": payload.get("fpcId") or "",
                "fpcName": payload.get("fpcName") or "",
                "playerName": payload.get("playerName") or "",
                "summary": payload.get("summary") or "",
            }
        )
    return {"count": len(entries), "entries": entries}


def _studio_creation_projects_root() -> Path:
    path = _studio_file("creation_projects")
    path.mkdir(parents=True, exist_ok=True)
    return path


def _studio_creation_project_slug(value: str | None) -> str:
    normalized = re.sub(r"[^a-z0-9_-]+", "_", (value or "").strip().lower()).strip("_")
    return normalized or "creation_project"


def _studio_creation_project_file(project_id: str) -> Path:
    normalized = _studio_creation_project_slug(project_id)
    return _studio_creation_projects_root() / f"{normalized}.json"


def _studio_creation_project_field(value: Any, fallback: str = "") -> str:
    normalized = (str(value or "").strip().lower() if value is not None else "").strip()
    return normalized or fallback


def _studio_creation_role_tuning_entry_active(entry: Any) -> bool:
    if not isinstance(entry, dict):
        return False
    return any(str(value or "").strip() for value in entry.values())


def _studio_creation_role_tuning_coverage(project_payload: Dict[str, Any]) -> Dict[str, Any]:
    if not isinstance(project_payload, dict):
        return {
            "totalCount": 0,
            "tunedCount": 0,
            "untunedCount": 0,
            "summary": "Tuned coverage: No role seeds yet.",
        }
    blueprint = project_payload.get("blueprint") if isinstance(project_payload.get("blueprint"), dict) else {}
    engine_state = project_payload.get("engineState") if isinstance(project_payload.get("engineState"), dict) else {}
    existing = blueprint.get("roleRefinementCoverage") if isinstance(blueprint.get("roleRefinementCoverage"), dict) else {}
    total_count = int(existing.get("totalCount") or 0)
    tuned_count = int(existing.get("tunedCount") or 0)
    if total_count < 1:
        role_seeds = blueprint.get("roleSeeds") if isinstance(blueprint.get("roleSeeds"), list) else []
        role_refinements = (
            engine_state.get("roleRefinements")
            if isinstance(engine_state.get("roleRefinements"), dict)
            else (blueprint.get("roleRefinements") if isinstance(blueprint.get("roleRefinements"), dict) else {})
        )
        total_count = len(role_seeds)
        if total_count > 0:
            tuned_count = 0
            for seed in role_seeds:
                role_id = str((seed or {}).get("id") or "").strip().lower()
                if role_id and _studio_creation_role_tuning_entry_active(role_refinements.get(role_id)):
                    tuned_count += 1
    tuned_count = max(0, min(tuned_count, total_count))
    untuned_count = max(total_count - tuned_count, 0)
    summary = str(existing.get("summary") or "").strip()
    if not summary:
        summary = (
            f"Tuned coverage: {tuned_count} of {total_count} seed{'s' if total_count != 1 else ''} tuned."
            if total_count > 0
            else "Tuned coverage: No role seeds yet."
        )
    return {
        "totalCount": total_count,
        "tunedCount": tuned_count,
        "untunedCount": untuned_count,
        "summary": summary,
    }


def _studio_creation_proof_lane_shape(value: Any) -> Dict[str, Any]:
    if not isinstance(value, dict):
        return {
            "totalCount": 0,
            "answeredCount": 0,
            "state": "not_run",
            "label": "not run yet",
        }
    entries = value.get("entries")
    if not isinstance(entries, list):
        return {
            "totalCount": 0,
            "answeredCount": 0,
            "state": "not_run",
            "label": "not run yet",
        }
    total_count = len(entries)
    answered_count = sum(
        1
        for entry in entries
        if isinstance(entry, dict) and str(entry.get("replyLine") or "").strip()
    )
    if total_count < 1:
        state = "not_run"
        label = "not run yet"
    elif answered_count < 1:
        state = "empty"
        label = "captured but empty"
    elif answered_count < total_count:
        state = "partial"
        label = f"partial ({answered_count}/{total_count})"
    else:
        state = "complete"
        label = "complete"
    return {
        "totalCount": total_count,
        "answeredCount": answered_count,
        "state": state,
        "label": label,
    }


def _studio_creation_proof_summary(
    created_output: Any = None,
    pack_proof: Any = None,
    latest_probe_result: Any = None,
    recommendation: Any = None,
) -> Dict[str, Any]:
    created_count = 0
    if isinstance(created_output, dict):
        created_count = int(created_output.get("createdCount") or created_output.get("roleCount") or 0)
        if created_count < 1 and created_output:
            created_count = 1
    pack_created_count = 0
    voice_smoke = _studio_creation_proof_lane_shape({})
    scene_smoke = _studio_creation_proof_lane_shape({})
    if isinstance(pack_proof, dict):
        if created_count < 1:
            pack_created_count = int(pack_proof.get("roleCount") or 0)
            if pack_proof.get("created"):
                pack_created_count = max(pack_created_count, 1)
        voice_smoke = _studio_creation_proof_lane_shape(pack_proof.get("voiceSmoke"))
        scene_smoke = _studio_creation_proof_lane_shape(pack_proof.get("sceneSmoke"))
    created_count = max(created_count, pack_created_count)
    has_probe = bool(latest_probe_result)
    recommendation_proof_lines = []
    if isinstance(recommendation, dict):
        recommendation_proof_lines = recommendation.get("proofLines") if isinstance(recommendation.get("proofLines"), list) else []
    proof_line_count = len(recommendation_proof_lines)
    voice_smoke_count = int(voice_smoke.get("answeredCount") or 0)
    scene_smoke_count = int(scene_smoke.get("answeredCount") or 0)
    evidence_count = sum(1 for count in [created_count, voice_smoke_count, scene_smoke_count, int(has_probe), proof_line_count] if count > 0)
    if evidence_count < 1:
        state = "pending"
        label = "Proof Pending"
        summary = "Proof pending: no created output, smoke, probe, or recommendation proof lines are stored yet."
    elif created_count > 0 and ((voice_smoke.get("state") == "complete" and scene_smoke.get("state") == "complete") or has_probe or proof_line_count > 0):
        state = "ready"
        label = "Proof Ready"
        summary = f"Proof ready: {evidence_count} proof signal(s) are stored."
    else:
        state = "partial"
        label = "Proof Partial"
        summary = f"Proof partial: {evidence_count} proof signal(s) are stored."
    proof_lines = []
    proof_lines.append(
        f"Created output: {created_count} item(s)" if created_count > 0 else "Created output: not stored yet"
    )
    if voice_smoke["state"] == "not_run":
        proof_lines.append("Voice smoke: not run yet")
    elif voice_smoke["state"] == "empty":
        proof_lines.append(f"Voice smoke: captured but empty (0/{voice_smoke['totalCount']} answers)")
    elif voice_smoke["state"] == "partial":
        proof_lines.append(f"Voice smoke: partial ({voice_smoke['answeredCount']}/{voice_smoke['totalCount']} answers)")
    else:
        proof_lines.append(f"Voice smoke: complete ({voice_smoke['answeredCount']} answers)")
    if scene_smoke["state"] == "not_run":
        proof_lines.append("Scene smoke: not run yet")
    elif scene_smoke["state"] == "empty":
        proof_lines.append(f"Scene smoke: captured but empty (0/{scene_smoke['totalCount']} answers)")
    elif scene_smoke["state"] == "partial":
        proof_lines.append(f"Scene smoke: partial ({scene_smoke['answeredCount']}/{scene_smoke['totalCount']} answers)")
    else:
        proof_lines.append(f"Scene smoke: complete ({scene_smoke['answeredCount']} answers)")
    proof_lines.append("Latest probe: stored" if has_probe else "Latest probe: not run yet")
    if proof_line_count > 0:
        proof_lines.append(f"Recommendation proof lines: {proof_line_count}")
    return {
        "state": state,
        "label": label,
        "summary": summary,
        "createdCount": created_count,
        "voiceSmokeCount": voice_smoke_count,
        "sceneSmokeCount": scene_smoke_count,
        "voiceSmokeState": voice_smoke["state"],
        "sceneSmokeState": scene_smoke["state"],
        "hasProbe": has_probe,
        "lines": proof_lines,
    }


def _studio_creation_project_lineage_bucket(
    derivation_mode: Any,
    family_key: Any,
    parent_package_key: Any,
    parent_publish_id: Any,
    package_key: Any,
) -> Dict[str, Any]:
    mode = _studio_creation_project_field(derivation_mode, "root")
    if mode not in {"root", "variant", "sequel", "template"}:
        mode = "root"
    family = _studio_creation_project_field(family_key) or _studio_creation_project_field(package_key)
    parent_package = _studio_creation_project_field(parent_package_key)
    parent_publish = _studio_creation_project_field(parent_publish_id)
    labels = {
        "root": "Root World",
        "variant": "Variant",
        "sequel": "Sequel",
        "template": "Template Copy",
    }
    summaries = {
        "root": "Root world: This saved project is the current family anchor.",
        "variant": "Variant: This saved project shifts angle, mood, or player promise inside the same family.",
        "sequel": "Sequel: This saved project continues the same family into a next chapter.",
        "template": "Template copy: This saved project starts as a reusable branch from another family member.",
    }
    return {
        "state": mode,
        "label": labels.get(mode, "Root World"),
        "summary": summaries.get(mode, summaries["root"]),
        "familyKey": family,
        "parentPackageKey": parent_package,
        "parentPublishId": parent_publish,
        "packageKey": _studio_creation_project_field(package_key),
        "familySize": 0,
    }


def _list_studio_creation_projects() -> Dict[str, Any]:
    root = _studio_creation_projects_root()
    entries: List[Dict[str, Any]] = []
    host_counts: Dict[str, int] = {}
    program_counts: Dict[str, int] = {}
    publish_state_counts: Dict[str, int] = {}
    publish_lifecycle_counts: Dict[str, int] = {}
    lineage_counts: Dict[str, int] = {}
    family_counts: Dict[str, int] = {}
    proof_counts: Dict[str, int] = {}
    publish_snapshots = _studio_creation_project_publish_snapshot()
    for path in sorted(root.glob("*.json"), key=lambda item: item.stat().st_mtime, reverse=True):
        payload = _load_studio_json_optional(path)
        if not isinstance(payload, dict):
            continue
        project_id = payload.get("id") or path.stem
        host_id = _studio_creation_project_field(payload.get("hostId"), "unknown")
        program_id = _studio_creation_project_field(payload.get("programId"), "unknown")
        world_name = payload.get("worldName") or ""
        project_payload = payload.get("payload") if isinstance(payload.get("payload"), dict) else {}
        project_lineage = project_payload.get("lineage") if isinstance(project_payload.get("lineage"), dict) else {}
        proof_summary = _studio_creation_proof_summary(
            project_payload.get("createdBundle"),
            project_payload.get("starterPackPreview"),
            project_payload.get("latestProbeResult"),
            project_payload.get("recommendation") if isinstance(project_payload.get("recommendation"), dict) else None,
        )
        publish_snapshot = publish_snapshots.get(str(project_id))
        publish_state = dict(publish_snapshot) if isinstance(publish_snapshot, dict) else {
            "state": "draft_only",
            "publishCount": 0,
            "latestPublishId": "",
            "latestLifecycleState": "",
            "latestHandoffState": "",
            "latestDecision": "",
            "latestVersionLabel": "",
            "summary": "Draft only: No publish records yet.",
        }
        package_key = _studio_creation_project_field(
            (publish_snapshot or {}).get("packageKey"),
            _studio_creation_publish_package_key(world_name, host_id, program_id),
        )
        lineage = _studio_creation_project_lineage_bucket(
            (publish_snapshot or {}).get("derivationMode") or project_lineage.get("derivationMode"),
            (publish_snapshot or {}).get("familyKey") or project_lineage.get("familyKey"),
            (publish_snapshot or {}).get("parentPackageKey") or project_lineage.get("parentPackageKey"),
            (publish_snapshot or {}).get("parentPublishId") or project_lineage.get("parentPublishId"),
            package_key,
        )
        entry = {
            "id": project_id,
            "name": payload.get("name") or path.stem,
            "createdAtMs": payload.get("createdAtMs") or int(path.stat().st_mtime * 1000),
            "updatedAtMs": payload.get("updatedAtMs") or int(path.stat().st_mtime * 1000),
            "worldName": world_name,
            "hostId": host_id,
            "programId": program_id,
            "toneId": _studio_creation_project_field(payload.get("toneId")),
            "scaleId": _studio_creation_project_field(payload.get("scaleId")),
            "depthId": _studio_creation_project_field(payload.get("depthId")),
            "summary": payload.get("summary") or "",
            "roleTuning": _studio_creation_role_tuning_coverage(project_payload),
            "proof": proof_summary,
            "publishState": publish_state,
            "publishLifecycle": dict(publish_state.get("lifecycle")) if isinstance(publish_state.get("lifecycle"), dict) else {
                "state": "draft",
                "label": "Draft Lane",
                "summary": "Draft lane: No publish records yet.",
            },
            "lineage": lineage,
        }
        entries.append(entry)
        host_counts[host_id] = host_counts.get(host_id, 0) + 1
        program_counts[program_id] = program_counts.get(program_id, 0) + 1
        publish_state_id = _studio_creation_project_field(publish_state.get("state"), "draft_only")
        publish_state_counts[publish_state_id] = publish_state_counts.get(publish_state_id, 0) + 1
        publish_lifecycle = publish_state.get("lifecycle") if isinstance(publish_state.get("lifecycle"), dict) else {}
        publish_lifecycle_id = _studio_creation_project_field(publish_lifecycle.get("state"), "draft")
        publish_lifecycle_counts[publish_lifecycle_id] = publish_lifecycle_counts.get(publish_lifecycle_id, 0) + 1
        lineage_id = _studio_creation_project_field(lineage.get("state"), "root")
        lineage_counts[lineage_id] = lineage_counts.get(lineage_id, 0) + 1
        family_id = _studio_creation_project_field(lineage.get("familyKey"), package_key)
        if family_id:
            family_counts[family_id] = family_counts.get(family_id, 0) + 1
        proof_state_id = _studio_creation_project_field(proof_summary.get("state"), "pending")
        proof_counts[proof_state_id] = proof_counts.get(proof_state_id, 0) + 1
    for entry in entries:
        lineage = entry.get("lineage") if isinstance(entry.get("lineage"), dict) else {}
        family_id = _studio_creation_project_field(lineage.get("familyKey"))
        if family_id:
            lineage["familySize"] = family_counts.get(family_id, 1)
    return {
        "count": len(entries),
        "entries": entries,
        "hostCounts": host_counts,
        "programCounts": program_counts,
        "publishStateCounts": publish_state_counts,
        "publishLifecycleCounts": publish_lifecycle_counts,
        "lineageCounts": lineage_counts,
        "familyCounts": family_counts,
        "proofCounts": proof_counts,
    }


def _studio_creation_publishes_root() -> Path:
    path = _studio_file("creation_publishes")
    path.mkdir(parents=True, exist_ok=True)
    return path


def _studio_creation_publish_slug(value: str | None) -> str:
    normalized = re.sub(r"[^a-z0-9_-]+", "_", (value or "").strip().lower()).strip("_")
    return normalized or "creation_publish"


def _studio_creation_publish_file(publish_id: str) -> Path:
    normalized = _studio_creation_publish_slug(publish_id)
    return _studio_creation_publishes_root() / f"{normalized}.json"


def _studio_creation_publish_package_key(world_name: str, host_id: str, program_id: str) -> str:
    parts = [
        _studio_creation_publish_slug(world_name or "untitled_world"),
        _studio_creation_publish_slug(host_id or "unknown"),
        _studio_creation_publish_slug(program_id or "unknown"),
    ]
    return "__".join(parts)


def _studio_creation_publish_lifecycle_for_decision(decision: str) -> str:
    normalized = _studio_creation_project_field(decision, "keep_draft")
    if normalized == "ready_to_keep":
        return "kept"
    if normalized == "needs_dev_side":
        return "handoff_ready"
    return "draft"


def _studio_creation_project_publish_lifecycle_bucket(
    publish_count: int,
    lifecycle_state: str | None,
    handoff_state: str | None,
) -> Dict[str, str]:
    lifecycle = _studio_creation_project_field(lifecycle_state, "draft")
    handoff = _studio_creation_project_field(handoff_state, "none")
    if int(publish_count or 0) < 1:
        return {
            "state": "draft",
            "label": "Draft Lane",
            "summary": "Draft lane: No publish records exist yet.",
        }
    if handoff == "claimed":
        return {
            "state": "claimed",
            "label": "Claimed By Dev",
            "summary": "Claimed by dev: The latest publish is currently held by the dev-side queue.",
        }
    if handoff == "sent":
        return {
            "state": "sent",
            "label": "Sent To Dev",
            "summary": "Sent to dev: The latest publish has already left the creator lane.",
        }
    if handoff == "ready" or lifecycle == "handoff_ready":
        return {
            "state": "handoff_ready",
            "label": "Ready For Dev",
            "summary": "Ready for dev: The latest publish is queued locally and can be sent to dev side.",
        }
    if lifecycle == "kept":
        return {
            "state": "kept_local",
            "label": "Kept Local",
            "summary": "Kept local: The latest publish is the active kept baseline.",
        }
    if lifecycle == "archived":
        return {
            "state": "archived",
            "label": "Archived",
            "summary": "Archived: The latest publish is parked and no longer the active lane.",
        }
    return {
        "state": "draft",
        "label": "Draft Lane",
        "summary": "Draft lane: The latest publish is still local and has not entered the dev-side queue.",
    }


def _studio_creation_project_publish_snapshot() -> Dict[str, Dict[str, Any]]:
    snapshots: Dict[str, Dict[str, Any]] = {}
    root = _studio_creation_publishes_root()
    for path in sorted(root.glob("*.json"), key=lambda item: item.stat().st_mtime, reverse=True):
        payload = _load_studio_json_optional(path)
        if not isinstance(payload, dict):
            continue
        project_id = str(payload.get("projectId") or "").strip()
        if not project_id:
            continue
        decision = _studio_creation_project_field(payload.get("decision"), "keep_draft")
        lifecycle_state = _studio_creation_project_field(
            payload.get("lifecycleState"),
            _studio_creation_publish_lifecycle_for_decision(decision),
        )
        handoff_state = _studio_creation_project_field(
            payload.get("handoffState"),
            "ready" if lifecycle_state == "handoff_ready" else "none",
        )
        snapshot = snapshots.setdefault(project_id, {
            "state": "published",
            "publishCount": 0,
            "latestPublishId": "",
            "latestLifecycleState": "",
            "latestHandoffState": "",
            "latestDecision": "",
            "latestVersionLabel": "",
            "packageKey": "",
            "familyKey": "",
            "parentPackageKey": "",
            "parentPublishId": "",
            "derivationMode": "root",
            "summary": "",
            "lifecycle": {
                "state": "draft",
                "label": "Draft Lane",
                "summary": "Draft lane: No publish records exist yet.",
            },
        })
        snapshot["publishCount"] = int(snapshot.get("publishCount") or 0) + 1
        if not snapshot.get("latestPublishId"):
            snapshot["latestPublishId"] = payload.get("id") or path.stem
            snapshot["latestLifecycleState"] = lifecycle_state
            snapshot["latestHandoffState"] = handoff_state
            snapshot["latestDecision"] = decision
            snapshot["latestVersionLabel"] = payload.get("versionLabel") or f"v{int(payload.get('versionNumber') or 1)}"
            snapshot["packageKey"] = _studio_creation_project_field(payload.get("packageKey"))
            snapshot["familyKey"] = _studio_creation_project_field(payload.get("familyKey"))
            snapshot["parentPackageKey"] = _studio_creation_project_field(payload.get("parentPackageKey"))
            snapshot["parentPublishId"] = _studio_creation_project_field(payload.get("parentPublishId"))
            snapshot["derivationMode"] = _studio_creation_project_field(payload.get("derivationMode"), "root")
    for snapshot in snapshots.values():
        publish_count = int(snapshot.get("publishCount") or 0)
        lifecycle_label = str(snapshot.get("latestLifecycleState") or "draft").replace("_", " ")
        handoff_label = str(snapshot.get("latestHandoffState") or "none").replace("_", " ")
        version_label = str(snapshot.get("latestVersionLabel") or "v1").strip() or "v1"
        lifecycle_meta = _studio_creation_project_publish_lifecycle_bucket(
            publish_count,
            str(snapshot.get("latestLifecycleState") or "draft"),
            str(snapshot.get("latestHandoffState") or "none"),
        )
        snapshot["lifecycle"] = lifecycle_meta
        snapshot["summary"] = (
            f"Already published: {publish_count} publish record(s). "
            f"Latest {version_label} is {lifecycle_label}; handoff {handoff_label}."
        )
    return snapshots


def _list_studio_creation_publishes() -> Dict[str, Any]:
    root = _studio_creation_publishes_root()
    entries: List[Dict[str, Any]] = []
    decision_counts: Dict[str, int] = {}
    lifecycle_counts: Dict[str, int] = {}
    handoff_counts: Dict[str, int] = {}
    host_counts: Dict[str, int] = {}
    program_counts: Dict[str, int] = {}
    tone_counts: Dict[str, int] = {}
    package_counts: Dict[str, int] = {}
    family_counts: Dict[str, int] = {}
    proof_counts: Dict[str, int] = {}
    for path in sorted(root.glob("*.json"), key=lambda item: item.stat().st_mtime, reverse=True):
        payload = _load_studio_json_optional(path)
        if not isinstance(payload, dict):
            continue
        decision = _studio_creation_project_field(payload.get("decision"), "keep_draft")
        host_id = _studio_creation_project_field(payload.get("hostId"), "unknown")
        program_id = _studio_creation_project_field(payload.get("programId"), "unknown")
        lifecycle_state = _studio_creation_project_field(
            payload.get("lifecycleState"),
            _studio_creation_publish_lifecycle_for_decision(decision),
        )
        handoff_state = _studio_creation_project_field(payload.get("handoffState"), "none")
        publish_payload = payload.get("payload") if isinstance(payload.get("payload"), dict) else {}
        project_payload = publish_payload.get("projectPayload") if isinstance(publish_payload.get("projectPayload"), dict) else {}
        engine_state = project_payload.get("engineState") if isinstance(project_payload.get("engineState"), dict) else {}
        lineage_payload = payload.get("lineage") if isinstance(payload.get("lineage"), dict) else {}
        publish_lineage = publish_payload.get("lineage") if isinstance(publish_payload.get("lineage"), dict) else {}
        project_lineage = project_payload.get("lineage") if isinstance(project_payload.get("lineage"), dict) else {}
        proof_summary = _studio_creation_proof_summary(
            publish_payload.get("createdOutput"),
            publish_payload.get("packProof"),
            publish_payload.get("latestProbeResult"),
            publish_payload.get("recommendation") if isinstance(publish_payload.get("recommendation"), dict) else None,
        )
        tone_id = _studio_creation_project_field(
            payload.get("toneId") or engine_state.get("toneId")
        )
        package_key = _studio_creation_project_field(
            payload.get("packageKey"),
            _studio_creation_publish_package_key(
                payload.get("worldName") or "",
                host_id,
                program_id,
            ),
        )
        parent_package_key = _studio_creation_project_field(
            payload.get("parentPackageKey")
            or lineage_payload.get("parentPackageKey")
            or publish_lineage.get("parentPackageKey")
            or project_lineage.get("parentPackageKey")
        )
        family_key = _studio_creation_project_field(
            payload.get("familyKey")
            or lineage_payload.get("familyKey")
            or publish_lineage.get("familyKey")
            or project_lineage.get("familyKey")
            or parent_package_key
            or package_key,
            package_key,
        )
        parent_publish_id = _studio_creation_project_field(
            payload.get("parentPublishId")
            or lineage_payload.get("parentPublishId")
            or publish_lineage.get("parentPublishId")
            or project_lineage.get("parentPublishId")
        )
        derivation_mode = _studio_creation_project_field(
            payload.get("derivationMode")
            or lineage_payload.get("derivationMode")
            or publish_lineage.get("derivationMode")
            or project_lineage.get("derivationMode"),
            "root",
        )
        entry = {
            "id": payload.get("id") or path.stem,
            "name": payload.get("name") or path.stem,
            "projectId": payload.get("projectId") or "",
            "createdAtMs": payload.get("createdAtMs") or int(path.stat().st_mtime * 1000),
            "updatedAtMs": payload.get("updatedAtMs") or int(path.stat().st_mtime * 1000),
            "worldName": payload.get("worldName") or "",
            "hostId": host_id,
            "programId": program_id,
            "toneId": tone_id,
            "scaleId": _studio_creation_project_field(payload.get("scaleId")),
            "depthId": _studio_creation_project_field(payload.get("depthId")),
            "decision": decision,
            "lifecycleState": lifecycle_state,
            "handoffState": handoff_state,
            "packageKey": package_key,
            "familyKey": family_key,
            "parentPackageKey": parent_package_key,
            "parentPublishId": parent_publish_id,
            "derivationMode": derivation_mode,
            "versionNumber": int(payload.get("versionNumber") or 1),
            "versionLabel": payload.get("versionLabel") or "v1",
            "summary": payload.get("summary") or "",
            "proof": proof_summary,
        }
        entries.append(entry)
        decision_counts[decision] = decision_counts.get(decision, 0) + 1
        lifecycle_counts[lifecycle_state] = lifecycle_counts.get(lifecycle_state, 0) + 1
        handoff_counts[handoff_state] = handoff_counts.get(handoff_state, 0) + 1
        host_counts[host_id] = host_counts.get(host_id, 0) + 1
        program_counts[program_id] = program_counts.get(program_id, 0) + 1
        if tone_id:
            tone_counts[tone_id] = tone_counts.get(tone_id, 0) + 1
        package_counts[package_key] = package_counts.get(package_key, 0) + 1
        family_counts[family_key] = family_counts.get(family_key, 0) + 1
        proof_state_id = _studio_creation_project_field(proof_summary.get("state"), "pending")
        proof_counts[proof_state_id] = proof_counts.get(proof_state_id, 0) + 1
    return {
        "count": len(entries),
        "entries": entries,
        "decisionCounts": decision_counts,
        "lifecycleCounts": lifecycle_counts,
        "handoffCounts": handoff_counts,
        "hostCounts": host_counts,
        "programCounts": program_counts,
        "toneCounts": tone_counts,
        "packageCounts": package_counts,
        "familyCounts": family_counts,
        "proofCounts": proof_counts,
    }


def _studio_reports_root() -> Path:
    path = _studio_file("reports")
    path.mkdir(parents=True, exist_ok=True)
    return path


def _studio_report_slug(value: str | None) -> str:
    normalized = re.sub(r"[^a-z0-9_-]+", "_", (value or "").strip().lower()).strip("_")
    return normalized or "report"


def _studio_report_file(report_id: str) -> Path:
    normalized = _studio_report_slug(report_id)
    return _studio_reports_root() / f"{normalized}.json"


def _studio_incidents_root() -> Path:
    path = _studio_file("incidents")
    path.mkdir(parents=True, exist_ok=True)
    return path


def _studio_incident_slug(value: str | None) -> str:
    normalized = re.sub(r"[^a-z0-9_-]+", "_", (value or "").strip().lower()).strip("_")
    return normalized or "incident"


def _studio_incident_file(incident_id: str) -> Path:
    normalized = _studio_incident_slug(incident_id)
    return _studio_incidents_root() / f"{normalized}.json"


def _suite_run_summary(run: Dict[str, Any] | None) -> Dict[str, Any]:
    steps = run.get("steps") if isinstance(run, dict) else []
    step_list = steps if isinstance(steps, list) else []
    passed_steps = 0
    failed_steps = 0
    passed_checks = 0
    total_checks = 0
    failing_labels: List[str] = []
    for index, step in enumerate(step_list, start=1):
        if not isinstance(step, dict):
            continue
        step_passed_checks = int(step.get("passedChecks") or 0)
        step_total_checks = int(step.get("totalChecks") or 0)
        passed_checks += step_passed_checks
        total_checks += step_total_checks
        if step.get("failed"):
            failed_steps += 1
            label = str(step.get("label") or step.get("presetId") or f"Step {index}").strip()
            if label:
                failing_labels.append(label)
        else:
            passed_steps += 1
    score_pct = int(round((passed_checks / total_checks) * 100)) if total_checks else 100
    return {
        "stepCount": len(step_list),
        "passedSteps": passed_steps,
        "failedSteps": failed_steps,
        "passedChecks": passed_checks,
        "totalChecks": total_checks,
        "scorePct": score_pct,
        "failingLabels": failing_labels[:5],
    }


def _matches_report_filter(value: str, expected: str | None) -> bool:
    if not expected:
        return True
    return expected.strip().lower() in (value or "").strip().lower()


def _list_studio_reports(
    fpc_id: str | None = None,
    suite_id: str | None = None,
    status: str | None = None,
    contains: str | None = None,
) -> Dict[str, Any]:
    root = _studio_reports_root()
    entries: List[Dict[str, Any]] = []
    total_count = 0
    speaker_counts: Dict[str, int] = {}
    suite_counts: Dict[str, int] = {}
    status_counts: Dict[str, int] = {}
    score_total = 0
    score_count = 0
    lowest_score: int | None = None
    highest_score: int | None = None
    failed_step_total = 0
    top_failing_suite_counts: Dict[str, int] = {}
    contains_text = (contains or "").strip().lower()
    for path in sorted(root.glob("*.json"), key=lambda item: item.stat().st_mtime, reverse=True):
        payload = _load_studio_json_optional(path)
        if not isinstance(payload, dict):
            continue
        total_count += 1
        run_summary = _suite_run_summary(payload.get("latestSuiteRun") if isinstance(payload.get("latestSuiteRun"), dict) else {})
        stored_run_summary = payload.get("suiteRunTotals") if isinstance(payload.get("suiteRunTotals"), dict) else {}
        entry = {
            "id": payload.get("id") or path.stem,
            "name": payload.get("name") or path.stem,
            "createdAtMs": payload.get("createdAtMs") or int(path.stat().st_mtime * 1000),
            "fpcId": payload.get("fpcId") or "",
            "fpcName": payload.get("fpcName") or "",
            "suiteId": payload.get("suiteId") or "",
            "suiteLabel": payload.get("suiteLabel") or "",
            "status": payload.get("status") or "",
            "stepCount": payload.get("stepCount") or 0,
            "summary": payload.get("summary") or "",
            "baselineIncluded": bool(payload.get("baselineIncluded")),
            "passedChecks": stored_run_summary.get("passedChecks") or run_summary["passedChecks"],
            "totalChecks": stored_run_summary.get("totalChecks") or run_summary["totalChecks"],
            "scorePct": stored_run_summary.get("scorePct", run_summary["scorePct"]),
            "failedSteps": stored_run_summary.get("failedSteps") or run_summary["failedSteps"],
            "failingLabels": stored_run_summary.get("failingLabels", run_summary["failingLabels"]),
            "scoreSummary": payload.get("scoreSummary") or "",
        }
        if not _matches_report_filter(entry["fpcId"] or entry["fpcName"], fpc_id):
            continue
        if not _matches_report_filter(entry["suiteId"] or entry["suiteLabel"], suite_id):
            continue
        if not _matches_report_filter(entry["status"], status):
            continue
        if contains_text and contains_text not in json.dumps(payload, ensure_ascii=False).lower():
            continue
        entries.append(entry)
        speaker_key = str(entry["fpcName"] or entry["fpcId"] or "unknown")
        suite_key = str(entry["suiteLabel"] or entry["suiteId"] or "unknown")
        status_key = str(entry["status"] or "unknown")
        speaker_counts[speaker_key] = speaker_counts.get(speaker_key, 0) + 1
        suite_counts[suite_key] = suite_counts.get(suite_key, 0) + 1
        status_counts[status_key] = status_counts.get(status_key, 0) + 1
        entry_total_checks = int(entry["totalChecks"] or 0)
        entry_score_pct = int(entry["scorePct"] or 0)
        entry_failed_steps = int(entry["failedSteps"] or 0)
        failed_step_total += entry_failed_steps
        if entry_total_checks > 0:
            score_total += entry_score_pct
            score_count += 1
            lowest_score = entry_score_pct if lowest_score is None else min(lowest_score, entry_score_pct)
            highest_score = entry_score_pct if highest_score is None else max(highest_score, entry_score_pct)
        if entry_failed_steps > 0:
            top_failing_suite_counts[suite_key] = top_failing_suite_counts.get(suite_key, 0) + entry_failed_steps
    top_speakers = [{"name": key, "count": value} for key, value in sorted(speaker_counts.items(), key=lambda item: (-item[1], item[0]))[:5]]
    top_suites = [{"name": key, "count": value} for key, value in sorted(suite_counts.items(), key=lambda item: (-item[1], item[0]))[:5]]
    top_failing_suites = [{"name": key, "count": value} for key, value in sorted(top_failing_suite_counts.items(), key=lambda item: (-item[1], item[0]))[:5]]
    return {
        "count": len(entries),
        "totalCount": total_count,
        "entries": entries,
        "filters": {
            "fpcId": fpc_id or "",
            "suiteId": suite_id or "",
            "status": status or "",
            "contains": contains or "",
        },
        "stats": {
            "completed": status_counts.get("completed", 0),
            "failed": status_counts.get("failed", 0),
            "running": status_counts.get("running", 0),
            "other": sum(count for key, count in status_counts.items() if key not in {"completed", "failed", "running"}),
            "uniqueSpeakers": len(speaker_counts),
            "uniqueSuites": len(suite_counts),
            "reportsWithChecks": score_count,
            "averageScorePct": int(round(score_total / score_count)) if score_count else 0,
            "lowestScorePct": lowest_score if lowest_score is not None else 0,
            "highestScorePct": highest_score if highest_score is not None else 0,
            "failedSteps": failed_step_total,
            "topSpeakers": top_speakers,
            "topSuites": top_suites,
            "topFailingSuites": top_failing_suites,
        },
    }


def _list_studio_incidents(
    fpc_id: str | None = None,
    reason: str | None = None,
    status: str | None = None,
    contains: str | None = None,
) -> Dict[str, Any]:
    root = _studio_incidents_root()
    entries: List[Dict[str, Any]] = []
    total_count = 0
    reason_counts: Dict[str, int] = {}
    status_counts: Dict[str, int] = {}
    owner_counts: Dict[str, int] = {}
    speaker_counts: Dict[str, int] = {}
    contains_text = (contains or "").strip().lower()
    for path in sorted(root.glob("*.json"), key=lambda item: item.stat().st_mtime, reverse=True):
        payload = _load_studio_json_optional(path)
        if not isinstance(payload, dict):
            continue
        total_count += 1
        triage = payload.get("triage") if isinstance(payload.get("triage"), dict) else {}
        entry = {
            "id": payload.get("id") or path.stem,
            "name": payload.get("name") or path.stem,
            "createdAtMs": payload.get("createdAtMs") or int(path.stat().st_mtime * 1000),
            "fpcId": payload.get("fpcId") or "",
            "fpcName": payload.get("fpcName") or "",
            "reason": payload.get("reason") or "",
            "status": payload.get("status") or "",
            "summary": payload.get("summary") or "",
            "triageOwner": triage.get("owner") or "",
            "triageOwnerLabel": triage.get("ownerLabel") or "",
            "triageSeverity": triage.get("severity") or "",
            "triageSummary": triage.get("summary") or "",
        }
        if not _matches_report_filter(entry["fpcId"] or entry["fpcName"], fpc_id):
            continue
        if not _matches_report_filter(entry["reason"], reason):
            continue
        if not _matches_report_filter(entry["status"], status):
            continue
        if contains_text and contains_text not in json.dumps(payload, ensure_ascii=False).lower():
            continue
        entries.append(entry)
        reason_key = str(entry["reason"] or "unknown")
        status_key = str(entry["status"] or "unknown")
        owner_key = str(entry["triageOwnerLabel"] or entry["triageOwner"] or "unknown")
        speaker_key = str(entry["fpcName"] or entry["fpcId"] or "unknown")
        reason_counts[reason_key] = reason_counts.get(reason_key, 0) + 1
        status_counts[status_key] = status_counts.get(status_key, 0) + 1
        owner_counts[owner_key] = owner_counts.get(owner_key, 0) + 1
        speaker_counts[speaker_key] = speaker_counts.get(speaker_key, 0) + 1
    top_reasons = [{"name": key, "count": value} for key, value in sorted(reason_counts.items(), key=lambda item: (-item[1], item[0]))[:5]]
    top_owners = [{"name": key, "count": value} for key, value in sorted(owner_counts.items(), key=lambda item: (-item[1], item[0]))[:5]]
    top_speakers = [{"name": key, "count": value} for key, value in sorted(speaker_counts.items(), key=lambda item: (-item[1], item[0]))[:5]]
    return {
        "count": len(entries),
        "totalCount": total_count,
        "entries": entries,
        "filters": {
            "fpcId": fpc_id or "",
            "reason": reason or "",
            "status": status or "",
            "contains": contains or "",
        },
        "stats": {
            "open": status_counts.get("open", 0),
            "reviewing": status_counts.get("reviewing", 0),
            "resolved": status_counts.get("resolved", 0),
            "other": sum(count for key, count in status_counts.items() if key not in {"open", "reviewing", "resolved"}),
            "uniqueSpeakers": len(speaker_counts),
            "uniqueReasons": len(reason_counts),
            "uniqueOwners": len(owner_counts),
            "topReasons": top_reasons,
            "topOwners": top_owners,
            "topSpeakers": top_speakers,
        },
    }


def _studio_platform_snapshots_root() -> Path:
    path = _studio_file("platform_snapshots")
    path.mkdir(parents=True, exist_ok=True)
    return path


def _studio_platform_snapshot_slug(value: str | None) -> str:
    normalized = re.sub(r"[^a-z0-9_-]+", "_", (value or "").strip().lower()).strip("_")
    return normalized or "platform_snapshot"


def _studio_platform_snapshot_file(snapshot_id: str) -> Path:
    normalized = _studio_platform_snapshot_slug(snapshot_id)
    return _studio_platform_snapshots_root() / f"{normalized}.json"


def _list_studio_platform_snapshots() -> Dict[str, Any]:
    root = _studio_platform_snapshots_root()
    entries: List[Dict[str, Any]] = []
    for path in sorted(root.glob("*.json"), key=lambda item: item.stat().st_mtime, reverse=True):
        payload = _load_studio_json_optional(path)
        if not isinstance(payload, dict):
            continue
        identity = payload.get("identity") if isinstance(payload.get("identity"), dict) else {}
        content_packs = payload.get("contentPacks") if isinstance(payload.get("contentPacks"), dict) else {}
        entries.append(
            {
                "id": payload.get("id") or path.stem,
                "name": payload.get("name") or path.stem,
                "createdAtMs": payload.get("createdAtMs") or int(path.stat().st_mtime * 1000),
                "platformAdapterId": identity.get("platformAdapterId") or "",
                "rulesetId": identity.get("rulesetId") or "",
                "definitionCount": content_packs.get("definitionCount") or 0,
                "personaCount": content_packs.get("personaCount") or 0,
                "summary": payload.get("summary") or "",
            }
        )
    return {"count": len(entries), "entries": entries}


def _studio_platform_workitems_root() -> Path:
    path = _studio_file("platform_workitems")
    path.mkdir(parents=True, exist_ok=True)
    return path


def _studio_platform_workitem_slug(value: str | None) -> str:
    normalized = re.sub(r"[^a-z0-9_-]+", "_", (value or "").strip().lower()).strip("_")
    return normalized or "platform_workitem"


def _studio_platform_host_focus(value: str | None) -> str:
    raw = (value or "").strip()
    if not raw:
        return ""
    normalized = _studio_snapshot_component(raw)
    return "" if normalized == "unknown" else normalized


def _studio_platform_workitem_identity(capability_id: str | None, host_focus_id: str | None = None, explicit_id: str | None = None) -> str:
    explicit = _studio_platform_workitem_slug(explicit_id) if explicit_id else ""
    if explicit and explicit != "platform_workitem":
        return explicit
    capability = _studio_platform_workitem_slug(capability_id)
    host_focus = _studio_platform_host_focus(host_focus_id)
    if host_focus:
        return f"{host_focus}__{capability}"
    return capability


def _studio_platform_workitem_file(workitem_id: str) -> Path:
    normalized = _studio_platform_workitem_slug(workitem_id)
    return _studio_platform_workitems_root() / f"{normalized}.json"


def _studio_platform_workitem_status(value: str | None) -> str:
    normalized = (value or "").strip().lower()
    if normalized in {"backlog", "active", "blocked", "done"}:
        return normalized
    return "backlog"


def _studio_platform_workitem_priority(value: str | None) -> str:
    normalized = (value or "").strip().lower()
    if normalized in {"low", "medium", "high", "critical"}:
        return normalized
    return "medium"


def _studio_platform_workitem_due_on(value: str | None) -> str:
    normalized = (value or "").strip()
    if not normalized:
        return ""
    if re.fullmatch(r"\d{4}-\d{2}-\d{2}", normalized):
        return normalized
    return ""


def _studio_platform_workitem_core_fields(payload: Dict[str, Any] | None) -> Dict[str, Any]:
    source = payload if isinstance(payload, dict) else {}
    return {
        "status": _studio_platform_workitem_status(source.get("status")),
        "priority": _studio_platform_workitem_priority(source.get("priority")),
        "owner": (source.get("owner") or "").strip(),
        "hostFocusId": _studio_platform_host_focus(source.get("hostFocusId")),
        "dueOn": _studio_platform_workitem_due_on(source.get("dueOn")),
        "linkedReportId": _studio_report_slug(source.get("linkedReportId")) if source.get("linkedReportId") else "",
        "linkedSnapshotId": _studio_platform_snapshot_slug(source.get("linkedSnapshotId")) if source.get("linkedSnapshotId") else "",
        "milestone": bool(source.get("milestone")),
        "notes": (source.get("notes") or "").strip(),
        "summary": (source.get("summary") or "").strip(),
    }


def _studio_platform_workitem_change_summary(before: Dict[str, Any] | None, after: Dict[str, Any]) -> str:
    previous = _studio_platform_workitem_core_fields(before)
    current = _studio_platform_workitem_core_fields(after)
    if not before:
        return "Created work item."
    changes: List[str] = []
    if previous["status"] != current["status"]:
        changes.append(f"status {previous['status']} -> {current['status']}")
    if previous["priority"] != current["priority"]:
        changes.append(f"priority {previous['priority']} -> {current['priority']}")
    if previous["owner"] != current["owner"]:
        changes.append(f"owner {previous['owner'] or 'unassigned'} -> {current['owner'] or 'unassigned'}")
    if previous["dueOn"] != current["dueOn"]:
        changes.append(f"due {previous['dueOn'] or 'none'} -> {current['dueOn'] or 'none'}")
    if previous["milestone"] != current["milestone"]:
        changes.append("promoted to milestone" if current["milestone"] else "removed from milestone lane")
    if previous["linkedReportId"] != current["linkedReportId"]:
        changes.append(f"report {previous['linkedReportId'] or 'none'} -> {current['linkedReportId'] or 'none'}")
    if previous["linkedSnapshotId"] != current["linkedSnapshotId"]:
        changes.append(f"snapshot {previous['linkedSnapshotId'] or 'none'} -> {current['linkedSnapshotId'] or 'none'}")
    if previous["hostFocusId"] != current["hostFocusId"]:
        changes.append(f"host {previous['hostFocusId'] or 'global'} -> {current['hostFocusId'] or 'global'}")
    if previous["notes"] != current["notes"]:
        changes.append("notes updated")
    if previous["summary"] != current["summary"]:
        changes.append("summary refreshed")
    return "; ".join(changes) if changes else "No material change."


def _studio_platform_workitem_history_entry(payload: Dict[str, Any], change_summary: str) -> Dict[str, Any]:
    return {
        "atMs": int(payload.get("updatedAtMs") or time.time() * 1000),
        "status": _studio_platform_workitem_status(payload.get("status")),
        "priority": _studio_platform_workitem_priority(payload.get("priority")),
        "owner": (payload.get("owner") or "").strip(),
        "hostFocusId": _studio_platform_host_focus(payload.get("hostFocusId")),
        "dueOn": _studio_platform_workitem_due_on(payload.get("dueOn")),
        "linkedReportId": _studio_report_slug(payload.get("linkedReportId")) if payload.get("linkedReportId") else "",
        "linkedSnapshotId": _studio_platform_snapshot_slug(payload.get("linkedSnapshotId")) if payload.get("linkedSnapshotId") else "",
        "milestone": bool(payload.get("milestone")),
        "notes": (payload.get("notes") or "").strip(),
        "summary": (payload.get("summary") or "").strip(),
        "changeSummary": change_summary,
    }


def _list_studio_platform_workitems() -> Dict[str, Any]:
    root = _studio_platform_workitems_root()
    entries: List[Dict[str, Any]] = []
    status_counts: Dict[str, int] = {"backlog": 0, "active": 0, "blocked": 0, "done": 0}
    priority_counts: Dict[str, int] = {"low": 0, "medium": 0, "high": 0, "critical": 0}
    milestone_count = 0
    host_counts: Dict[str, int] = {}
    host_milestone_counts: Dict[str, int] = {}
    for path in sorted(root.glob("*.json"), key=lambda item: item.stat().st_mtime, reverse=True):
        payload = _load_studio_json_optional(path)
        if not isinstance(payload, dict):
            continue
        capability_id = _studio_platform_workitem_slug(payload.get("capabilityId") or path.stem)
        host_focus_id = _studio_platform_host_focus(payload.get("hostFocusId"))
        workitem_id = _studio_platform_workitem_identity(capability_id, host_focus_id, payload.get("id") or path.stem)
        status = _studio_platform_workitem_status(payload.get("status"))
        priority = _studio_platform_workitem_priority(payload.get("priority"))
        due_on = _studio_platform_workitem_due_on(payload.get("dueOn"))
        linked_report_id = _studio_report_slug(payload.get("linkedReportId")) if payload.get("linkedReportId") else ""
        linked_snapshot_id = _studio_platform_snapshot_slug(payload.get("linkedSnapshotId")) if payload.get("linkedSnapshotId") else ""
        milestone = bool(payload.get("milestone"))
        payload["status"] = status
        payload["priority"] = priority
        entries.append(
            {
                "id": workitem_id,
                "capabilityId": capability_id,
                "capabilityLabel": payload.get("capabilityLabel") or "",
                "ownerBand": payload.get("ownerBand") or "",
                "status": status,
                "priority": priority,
                "owner": payload.get("owner") or "",
                "hostFocusId": host_focus_id,
                "isHostSpecific": bool(host_focus_id),
                "dueOn": due_on,
                "linkedReportId": linked_report_id,
                "linkedSnapshotId": linked_snapshot_id,
                "milestone": milestone,
                "notes": payload.get("notes") or "",
                "updatedAtMs": payload.get("updatedAtMs") or int(path.stat().st_mtime * 1000),
                "summary": payload.get("summary") or "",
                "historyCount": len(payload.get("history")) if isinstance(payload.get("history"), list) else 0,
                "latestHistory": (payload.get("history")[-1] if isinstance(payload.get("history"), list) and payload.get("history") else None),
            }
        )
        status_counts[status] = status_counts.get(status, 0) + 1
        priority_counts[priority] = priority_counts.get(priority, 0) + 1
        host_bucket = host_focus_id or "global"
        host_counts[host_bucket] = host_counts.get(host_bucket, 0) + 1
        if milestone:
            milestone_count += 1
            host_milestone_counts[host_bucket] = host_milestone_counts.get(host_bucket, 0) + 1
    return {
        "count": len(entries),
        "entries": entries,
        "statusCounts": status_counts,
        "priorityCounts": priority_counts,
        "milestoneCount": milestone_count,
        "hostCounts": host_counts,
        "hostMilestoneCounts": host_milestone_counts,
    }


def _studio_content_backups_root(content_id: str) -> Path:
    path = _studio_file("content_backups", _studio_report_slug(content_id))
    path.mkdir(parents=True, exist_ok=True)
    return path


def _load_json_list_file(path: Path, label: str) -> List[Dict[str, Any]]:
    if not path.exists():
        raise HTTPException(status_code=404, detail=f"{label} file not found: {path}")
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Could not read {label}: {exc}") from exc
    if not isinstance(payload, list):
        raise HTTPException(status_code=500, detail=f"{label} does not contain a JSON array.")
    entries: List[Dict[str, Any]] = []
    for item in payload:
        if isinstance(item, dict):
            entries.append(item)
    return entries


def _normalize_reply_settings_source_payload(payload: Any) -> Dict[str, Any]:
    if isinstance(payload, dict):
        return payload
    if isinstance(payload, list):
        first_entry = next((item for item in payload if isinstance(item, dict)), None)
        if first_entry is not None:
            return first_entry
    raise HTTPException(status_code=500, detail="Reply settings does not contain a JSON object or singleton object list.")


def _write_json_list_file(path: Path, payload: List[Dict[str, Any]]) -> None:
    _write_fakeplayer_source_and_runtime(path, json.dumps(payload, ensure_ascii=False, indent=2) + "\n")


def _write_json_object_file(path: Path, payload: Dict[str, Any]) -> None:
    _write_fakeplayer_source_and_runtime(path, json.dumps(payload, ensure_ascii=False, indent=2) + "\n")


def _studio_fpc_source_entries() -> List[Dict[str, Any]]:
    return _load_json_list_file(FPC_DEFINITIONS_PATH, "FPC definitions")


def _studio_fpc_template_source_entries() -> List[Dict[str, Any]]:
    return _load_json_list_file(FPC_TEMPLATES_PATH, "FPC templates")


def _studio_persona_source_entries() -> List[Dict[str, Any]]:
    return _load_json_list_file(PERSONA_PROFILES_PATH, "Persona profiles")


def _studio_reply_bank_source_entries() -> List[Dict[str, Any]]:
    return _load_json_list_file(REPLY_BANKS_PATH, "Reply banks")


def _studio_knowledge_card_source_entries() -> List[Dict[str, Any]]:
    return _load_json_list_file(KNOWLEDGE_CARDS_PATH, "Knowledge cards")


def _studio_routing_rule_source_entries() -> List[Dict[str, Any]]:
    return _load_json_list_file(ROUTING_RULES_PATH, "Routing rules")


def _studio_reply_settings_source_entry() -> Dict[str, Any]:
    if not REPLY_SETTINGS_PATH.exists():
        raise HTTPException(status_code=404, detail=f"Reply settings file not found: {REPLY_SETTINGS_PATH}")
    try:
        payload = json.loads(REPLY_SETTINGS_PATH.read_text(encoding="utf-8"))
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Could not read Reply settings: {exc}") from exc
    loaded = _normalize_reply_settings_source_payload(payload)
    normalized = dict(DEFAULT_REPLY_SETTINGS)
    normalized.update({key: value for key, value in loaded.items() if value is not None})
    return normalized


def _safe_path_mtime_ms(path: Path) -> int | None:
    try:
        return int(path.stat().st_mtime * 1000)
    except Exception:
        return None


def _studio_platform_manifest() -> Dict[str, Any]:
    runtime = _load_studio_json(_studio_file("runtime.json"))
    workflow = _workflow_status_payload()
    studio_health_payload = studio_health()
    health_payload = health()
    fpcs = _studio_fpc_source_entries()
    templates = _studio_fpc_template_source_entries()
    personas = _studio_persona_source_entries()
    reply_banks = _studio_reply_bank_source_entries()
    knowledge_cards = _studio_knowledge_card_source_entries()
    routing_rules = _studio_routing_rule_source_entries()
    reply_settings = _studio_reply_settings_source_entry()
    roster_snapshot = _load_studio_json(_studio_file("roster.json"))

    roster_entries = (roster_snapshot.get("entries") or []) if isinstance(roster_snapshot, dict) else []
    social_count = 0
    afpc_count = 0
    for entry in roster_entries:
        entry_type = str(entry.get("type") or "").strip().upper()
        if entry_type == "AFPC":
            afpc_count += 1
        else:
            social_count += 1

    active_channels = set()
    for entry in templates:
        channels = entry.get("channels") if isinstance(entry.get("channels"), dict) else {}
        for channel_name in ("whisper", "general", "shout", "world"):
            if channels.get(channel_name) is True:
                active_channels.add(channel_name)

    authoring_lanes = [
        {
            "id": "fpcs",
            "label": "FPC Definitions",
            "filePath": str(FPC_DEFINITIONS_PATH),
            "count": len(fpcs),
            "reloadAction": "reload_fpc_definitions",
            "kind": "definitions",
            "mtimeMs": _safe_path_mtime_ms(FPC_DEFINITIONS_PATH),
        },
        {
            "id": "fpc_templates",
            "label": "FPC Templates",
            "filePath": str(FPC_TEMPLATES_PATH),
            "count": len(templates),
            "reloadAction": "reload_fpc_definitions",
            "kind": "templates",
            "mtimeMs": _safe_path_mtime_ms(FPC_TEMPLATES_PATH),
        },
        {
            "id": "persona_profiles",
            "label": "Persona Profiles",
            "filePath": str(PERSONA_PROFILES_PATH),
            "count": len(personas),
            "reloadAction": "reload_persona_profiles",
            "kind": "persona",
            "mtimeMs": _safe_path_mtime_ms(PERSONA_PROFILES_PATH),
        },
        {
            "id": "reply_banks",
            "label": "Reply Banks",
            "filePath": str(REPLY_BANKS_PATH),
            "count": len(reply_banks),
            "reloadAction": "reload_reply_banks",
            "kind": "reply_bank",
            "mtimeMs": _safe_path_mtime_ms(REPLY_BANKS_PATH),
        },
        {
            "id": "knowledge_cards",
            "label": "Knowledge Cards",
            "filePath": str(KNOWLEDGE_CARDS_PATH),
            "count": len(knowledge_cards),
            "reloadAction": "reload_knowledge_cards",
            "kind": "knowledge",
            "mtimeMs": _safe_path_mtime_ms(KNOWLEDGE_CARDS_PATH),
        },
        {
            "id": "routing_rules",
            "label": "Routing Rules",
            "filePath": str(ROUTING_RULES_PATH),
            "count": len(routing_rules),
            "reloadAction": "reload_routing_rules",
            "kind": "routing",
            "mtimeMs": _safe_path_mtime_ms(ROUTING_RULES_PATH),
        },
        {
            "id": "reply_settings",
            "label": "Reply Settings",
            "filePath": str(REPLY_SETTINGS_PATH),
            "count": len(reply_settings.keys()),
            "reloadAction": "reload_reply_settings",
            "kind": "settings",
            "mtimeMs": _safe_path_mtime_ms(REPLY_SETTINGS_PATH),
        },
    ]

    host_targets = [
        {
            "id": "mobius_tier_a",
            "label": "L2J Mobius Essence 09.1 Warg",
            "shortLabel": "Warg",
            "hostFamily": "Lineage II / Java",
            "gameDomain": "MMO world + party",
            "readiness": "live",
            "adapterState": "live",
            "studioState": "live",
            "authoringState": "portable",
            "workflowState": "live",
            "summary": "Current product lane. Real adapter, Studio bridge, workflow lane, and live authoring are all proven in the Warg runtime.",
            "nextStep": "Keep the Warg lane stable, then widen the same seams into nearby Mobius Essence branches before Future Host.",
            "readyCapabilities": [
                "host adapter is live",
                "studio bridge and snapshots are live",
                "safe authoring lanes are live",
                "workflow controls are live",
            ],
            "gapChecklist": [
                {
                    "id": "mobius_b_to_neutral",
                    "label": "Neutralize Band B service inputs",
                    "status": "active",
                    "ownerBand": "band_b",
                    "seam": "decision, knowledge, persona, and social services",
                    "why": "Project behavior is still portable in spirit but still shaped around Mobius runtime handles.",
                    "linkedCapabilityId": "service_input_neutralization",
                },
                {
                    "id": "mobius_host_heavy",
                    "label": "Thin the heavy host seams",
                    "status": "active",
                    "ownerBand": "band_d",
                    "seam": "route, combat-power, appearance, hybrid-party, script/admin edges",
                    "why": "These are still the main blockers between the current Warg runtime and a more reusable host adapter story.",
                    "linkedCapabilityId": "heavy_host_runtime_seams",
                },
            ],
        },
        {
            "id": "mobius_essence_family",
            "label": "Mobius Essence Family",
            "shortLabel": "Essence",
            "hostFamily": "Lineage II / Java",
            "gameDomain": "MMO world + party",
            "readiness": "planned",
            "adapterState": "planned",
            "studioState": "planned",
            "authoringState": "portable",
            "workflowState": "portable",
            "summary": "Next rollout after Warg. The goal is to reuse the same Studio shell and Tier A adapter story across nearby Mobius Essence branches before widening into Future Host.",
            "nextStep": "Prove one non-Warg Essence branch on the existing adapter/control-plane contract once the current Warg lane stays calm.",
            "readyCapabilities": [
                "Warg already proves the core Mobius Essence lane",
                "Studio authoring and reports are already portable enough to seed the next branch",
                "the host-adapter contract is in place for the next Essence rollout",
            ],
            "gapChecklist": [
                {
                    "id": "essence_family_ruleset",
                    "label": "Prove the next Essence ruleset lane",
                    "status": "planned",
                    "ownerBand": "band_e",
                    "seam": "ruleset packs, local facts, and runtime behavior shaping",
                    "why": "Nearby Essence branches should be the first expansion because they can reuse more of the Warg shell without pretending they are identical.",
                    "linkedCapabilityId": "host_adapter_contract",
                },
                {
                    "id": "essence_family_bridge",
                    "label": "Carry Studio and workflow into the next Essence branch",
                    "status": "planned",
                    "ownerBand": "band_d",
                    "seam": "snapshot writer, command queue, and runtime ownership",
                    "why": "The control plane already works on Warg; the next proof is that it can stay coherent across another Mobius Essence branch.",
                    "linkedCapabilityId": "studio_bridge_contract",
                },
            ],
        },
        {
            "id": "mobius_future_host_family",
            "label": "Mobius Future Host Family",
            "shortLabel": "Future Host",
            "hostFamily": "Lineage II / Java",
            "gameDomain": "MMO world + party",
            "readiness": "planned",
            "adapterState": "planned",
            "studioState": "planned",
            "authoringState": "portable",
            "workflowState": "portable",
            "summary": "Later rollout after the broader Essence pass. The existing Future Host canaries stay valuable, but they are deferred proof targets rather than the current creator promise.",
            "nextStep": "Finish the broader Essence rollout first, then stabilize one future host and widen from there.",
            "readyCapabilities": [
                "future host workspaces already exist",
                "portable Studio authoring lanes can carry over",
                "the family-wide Studio workflow now has a strong enough shell to reuse later",
            ],
            "gapChecklist": [
                {
                    "id": "future_host_family_adapter",
                    "label": "Harden the Future Host adapter lane",
                    "status": "planned",
                    "ownerBand": "band_c",
                    "seam": "platform adapter, ruleset handshake, and host facade bindings",
                    "why": "Future Host is the right later rollout because it is still Mobius, but branch-sensitive enough to expose adapter mistakes.",
                    "linkedCapabilityId": "host_adapter_contract",
                },
                {
                    "id": "future_host_family_bridge",
                    "label": "Carry the Studio bridge across a future host",
                    "status": "planned",
                    "ownerBand": "band_d",
                    "seam": "snapshot writer, command queue, workflow control, and publish/readback surfaces",
                    "why": "The current Studio promise should not widen into Future Host until the same bridge contract survives there cleanly.",
                    "linkedCapabilityId": "studio_bridge_contract",
                },
            ],
        },
        {
            "id": "future_host_adapter",
            "label": "Future Host Adapter (Deferred)",
            "shortLabel": "FH",
            "hostFamily": "Lineage II / Java",
            "gameDomain": "MMO world + party",
            "readiness": "prepared",
            "adapterState": "planned",
            "studioState": "planned",
            "authoringState": "portable",
            "workflowState": "portable",
            "summary": "Prepared deferred deferred adapter workspace. Keep it as a later Future Host proof target rather than the current creator-facing scope.",
            "nextStep": "Use it as one deferred proof pass after the broader Essence rollout, not as the first public expansion target.",
            "readyCapabilities": [
                "workspace and runtime target are prepared",
                "Studio content authoring is already portable enough to carry over",
                "workflow concept can be reused after host bridge wiring",
            ],
            "gapChecklist": [
                {
                    "id": "future_host35_adapter",
                    "label": "Wire a future host adapter",
                    "status": "planned",
                    "ownerBand": "band_c",
                    "seam": "platform adapter and host facade bindings",
                    "why": "The current control plane is generic enough, but the host still needs its own adapter implementation.",
                    "linkedCapabilityId": "host_adapter_contract",
                },
                {
                    "id": "future_host35_bridge",
                    "label": "Port the Studio bridge to the deferred adapter host",
                    "status": "planned",
                    "ownerBand": "band_d",
                    "seam": "snapshot writer, command queue, and workflow runtime ownership",
                    "why": "Studio can only become cross-host when the target host exposes the same bridge contract.",
                    "linkedCapabilityId": "studio_bridge_contract",
                },
            ],
        },
        {
            "id": "future_host_secondary",
            "label": "Future Host deferred Adapter (Deferred)",
            "shortLabel": "FH",
            "hostFamily": "Lineage II / Java",
            "gameDomain": "MMO world + party",
            "readiness": "prepared",
            "adapterState": "planned",
            "studioState": "planned",
            "authoringState": "portable",
            "workflowState": "portable",
            "summary": "Prepared deferred future host. Keep it behind the broader Essence rollout and use it to pressure-test older Mobius behavior later.",
            "nextStep": "Validate older Future Host branch compatibility after the Warg-first and Essence-family rollout stays coherent.",
            "readyCapabilities": [
                "workspace and runtime target are prepared",
                "portable Studio authoring lanes can carry over",
            ],
            "gapChecklist": [
                {
                    "id": "future_host29_adapter",
                    "label": "Validate older Mobius adapter compatibility",
                    "status": "planned",
                    "ownerBand": "band_c",
                    "seam": "platform adapter and ruleset handshake",
                    "why": "This deferred adapter is valuable because it tests whether the adapter contract survives older branch differences.",
                    "linkedCapabilityId": "host_adapter_contract",
                },
                {
                    "id": "future_host29_host_edges",
                    "label": "Rebind host-owned script edges",
                    "status": "planned",
                    "ownerBand": "band_d",
                    "seam": "chat, spawn, admin, and hybrid-party edges",
                    "why": "These are the most branch-sensitive surfaces and the first place portability usually breaks.",
                    "linkedCapabilityId": "studio_bridge_contract",
                },
            ],
        },
        {
            "id": "l2_cplusplus_host",
            "label": "Lineage II C++ Host (Later)",
            "shortLabel": "L2 C++",
            "hostFamily": "Lineage II / C++",
            "gameDomain": "MMO world + party",
            "readiness": "vision",
            "adapterState": "vision",
            "studioState": "planned",
            "authoringState": "portable",
            "workflowState": "planned",
            "summary": "Preserved later Lineage goal, but not part of the current Mobius-first rollout. It will need a fresh engine adapter and a narrower bridge contract.",
            "nextStep": "Keep this parked behind the Mobius rollout and finish the neutral host/domain contract story first.",
            "readyCapabilities": [
                "portable content packs can survive this host jump",
                "Studio-side explanation and authoring concepts are already reusable",
            ],
            "gapChecklist": [
                {
                    "id": "cplusplus_domain",
                    "label": "Finish the neutral game-domain layer",
                    "status": "vision",
                    "ownerBand": "band_b",
                    "seam": "neutral actor/group/combat/travel concepts",
                    "why": "A C++ Lineage host should not force the platform to keep thinking in raw Mobius classes.",
                    "linkedCapabilityId": "game_domain_normalization",
                },
                {
                    "id": "cplusplus_adapter",
                    "label": "Build a fresh engine adapter",
                    "status": "vision",
                    "ownerBand": "band_c",
                    "seam": "host adapter, bridge transport, and actor binding layer",
                    "why": "The current adapter is Java/Mobius-specific and cannot be reused directly for a C++ runtime.",
                    "linkedCapabilityId": "host_adapter_contract",
                },
                {
                    "id": "cplusplus_host_runtime",
                    "label": "Recreate host-owned runtime seams",
                    "status": "vision",
                    "ownerBand": "band_d",
                    "seam": "spawn, chat transport, navigation, combat, and UI ownership",
                    "why": "These seams are where a new engine binding becomes real work instead of only architecture notes.",
                    "linkedCapabilityId": "heavy_host_runtime_seams",
                },
            ],
        },
        {
            "id": "wow_family_host",
            "label": "Broader MMO Host (Later)",
            "shortLabel": "WoW",
            "hostFamily": "Other MMO host",
            "gameDomain": "MMO group/raid world",
            "readiness": "vision",
            "adapterState": "vision",
            "studioState": "planned",
            "authoringState": "portable",
            "workflowState": "planned",
            "summary": "Preserved later vision for broader MMO portability. It is useful as an architecture honesty check, but it is not a near-term product target.",
            "nextStep": "Keep this parked behind the Mobius rollout and deepen game-domain normalization first.",
            "readyCapabilities": [
                "portable persona/reply/knowledge content shapes are reusable",
                "Studio control-plane concepts are reusable",
            ],
            "gapChecklist": [
                {
                    "id": "wow_domain",
                    "label": "Normalize MMO domain concepts beyond Lineage II",
                    "status": "vision",
                    "ownerBand": "band_b",
                    "seam": "group, travel, pressure, and progression abstractions",
                    "why": "WoW-family semantics differ enough that the platform must stop leaning on Lineage-shaped assumptions.",
                    "linkedCapabilityId": "game_domain_normalization",
                },
                {
                    "id": "wow_adapter",
                    "label": "Design a non-Mobius host adapter",
                    "status": "vision",
                    "ownerBand": "band_c",
                    "seam": "host adapter and bridge contract",
                    "why": "The Studio contract can stay, but the host bindings will have to be rethought from scratch.",
                    "linkedCapabilityId": "host_adapter_contract",
                },
            ],
        },
        {
            "id": "pubg_like_host",
            "label": "Shooter / Squad Host (Later)",
            "shortLabel": "Shooter",
            "hostFamily": "Shooter / session host",
            "gameDomain": "Squad + pressure combat",
            "readiness": "vision",
            "adapterState": "vision",
            "studioState": "planned",
            "authoringState": "partial",
            "workflowState": "planned",
            "summary": "Preserved later cross-game vision. It stays parked while the product scope remains Mobius-first.",
            "nextStep": "Keep this parked behind the Mobius rollout and build the game-domain normalization layer before any real host work here.",
            "readyCapabilities": [
                "Studio-side control, diff, and authoring patterns are still broadly reusable",
            ],
            "gapChecklist": [
                {
                    "id": "pubg_domain",
                    "label": "Create a real game-domain normalization layer",
                    "status": "vision",
                    "ownerBand": "band_b",
                    "seam": "squad, pressure, session flow, and combat intent abstractions",
                    "why": "This target forces the platform to prove it is not secretly only an MMO system.",
                    "linkedCapabilityId": "game_domain_normalization",
                },
                {
                    "id": "pubg_adapter",
                    "label": "Invent a new host adapter family",
                    "status": "vision",
                    "ownerBand": "band_c",
                    "seam": "session-host bridge, actor bindings, and capability matrix",
                    "why": "No current Mobius seam can be lifted directly into a squad shooter environment.",
                    "linkedCapabilityId": "host_adapter_contract",
                },
                {
                    "id": "pubg_runtime",
                    "label": "Rebuild host-owned runtime seams for session play",
                    "status": "vision",
                    "ownerBand": "band_d",
                    "seam": "spawn/session lifecycle, movement, combat, and UI ownership",
                    "why": "This is where the universal-platform claim either becomes real or falls back into a Lineage-only project.",
                    "linkedCapabilityId": "heavy_host_runtime_seams",
                },
            ],
        },
    ]

    platform_capabilities = [
        {
            "id": "content_authoring_portability",
            "label": "Portable Content Authoring",
            "ownerBand": "band_a",
            "seam": "Studio-safe JSON content families",
            "summary": "Keep the main source families editable through Studio without tying them to one host runtime.",
            "why": "This is the clearest proof that the project is becoming a platform instead of only a single-server mod.",
            "likelyFiles": [
                str(FPC_DEFINITIONS_PATH),
                str(FPC_TEMPLATES_PATH),
                str(PERSONA_PROFILES_PATH),
                str(REPLY_BANKS_PATH),
                str(KNOWLEDGE_CARDS_PATH),
                str(ROUTING_RULES_PATH),
                str(REPLY_SETTINGS_PATH),
            ],
            "hostStates": {
                "mobius_tier_a": "live",
                "mobius_essence_family": "portable",
                "mobius_future_host_family": "portable",
                "future_host_adapter": "portable",
                "future_host_secondary": "portable",
                "l2_cplusplus_host": "portable",
                "wow_family_host": "portable",
                "pubg_like_host": "partial",
            },
        },
        {
            "id": "service_input_neutralization",
            "label": "Neutral Domain Service Inputs",
            "ownerBand": "band_b",
            "seam": "Decision, knowledge, persona, reply-bank, and social services",
            "summary": "Move project behavior off raw Mobius-shaped handles and toward neutral platform inputs.",
            "why": "This is the gateway from a Lineage-only codebase to a reusable AI NPC platform.",
            "likelyFiles": [
                str(REPO_ROOT / "java" / "org" / "l2jmobius" / "gameserver" / "fakeplayer" / "service" / "FakePlayerDecisionEngine.java"),
                str(REPO_ROOT / "java" / "org" / "l2jmobius" / "gameserver" / "fakeplayer" / "service" / "FakePlayerKnowledgeService.java"),
                str(REPO_ROOT / "java" / "org" / "l2jmobius" / "gameserver" / "fakeplayer" / "service" / "FakePlayerPersonaResolver.java"),
                str(REPO_ROOT / "java" / "org" / "l2jmobius" / "gameserver" / "fakeplayer" / "service" / "FakePlayerSocialService.java"),
                str(REPO_ROOT / "java" / "org" / "l2jmobius" / "gameserver" / "fakeplayer" / "service" / "FakePlayerSocialMemoryService.java"),
            ],
            "hostStates": {
                "mobius_tier_a": "active",
                "mobius_essence_family": "planned",
                "mobius_future_host_family": "planned",
                "future_host_adapter": "planned",
                "future_host_secondary": "planned",
                "l2_cplusplus_host": "vision",
                "wow_family_host": "vision",
                "pubg_like_host": "vision",
            },
        },
        {
            "id": "game_domain_normalization",
            "label": "Game-Domain Normalization",
            "ownerBand": "band_b",
            "seam": "Neutral actor, group, combat, travel, and progression concepts",
            "summary": "Create a middle layer that lets the platform think beyond Lineage-specific gameplay semantics.",
            "why": "Without this layer, cross-game portability will always collapse back into host-specific rewrites.",
            "likelyFiles": [
                str(REPO_ROOT / "memory" / "universal_fpc_platform_vision_2026-03-25.md"),
                str(REPO_ROOT / "memory" / "portability_dependency_map_2026-03-25.md"),
                str(REPO_ROOT / "java" / "org" / "l2jmobius" / "gameserver" / "fakeplayer" / "service" / "FakePlayerDecisionEngine.java"),
                str(REPO_ROOT / "java" / "org" / "l2jmobius" / "gameserver" / "fakeplayer" / "service" / "FakePlayerKnowledgeService.java"),
            ],
            "hostStates": {
                "mobius_tier_a": "active",
                "mobius_essence_family": "planned",
                "mobius_future_host_family": "planned",
                "future_host_adapter": "planned",
                "future_host_secondary": "planned",
                "l2_cplusplus_host": "vision",
                "wow_family_host": "vision",
                "pubg_like_host": "vision",
            },
        },
        {
            "id": "host_adapter_contract",
            "label": "Host Adapter Contract",
            "ownerBand": "band_c",
            "seam": "Platform adapters, host facades, and ruleset handshake",
            "summary": "Every host must expose the same bounded platform contract instead of leaking direct engine/runtime details into the core.",
            "why": "This is the main seam that keeps new hosts from becoming hard forks of the current Warg integration.",
            "likelyFiles": [
                str(REPO_ROOT / "java" / "org" / "l2jmobius" / "gameserver" / "fakeplayer" / "platform" / "FakePlayerPlatformAdapter.java"),
                str(REPO_ROOT / "java" / "org" / "l2jmobius" / "gameserver" / "fakeplayer" / "platform" / "mobius" / "MobiusTierAPlatformAdapter.java"),
                str(REPO_ROOT / "java" / "org" / "l2jmobius" / "gameserver" / "fakeplayer" / "FakePlayerBootstrap.java"),
                str(REPO_ROOT / "java" / "org" / "l2jmobius" / "gameserver" / "fakeplayer" / "FakePlayerModule.java"),
            ],
            "hostStates": {
                "mobius_tier_a": "live",
                "mobius_essence_family": "planned",
                "mobius_future_host_family": "planned",
                "future_host_adapter": "planned",
                "future_host_secondary": "planned",
                "l2_cplusplus_host": "vision",
                "wow_family_host": "vision",
                "pubg_like_host": "vision",
            },
        },
        {
            "id": "studio_bridge_contract",
            "label": "Studio Bridge Contract",
            "ownerBand": "band_d",
            "seam": "Snapshot writer, command queue, result surfaces, and host-facing Studio bridge",
            "summary": "The host must expose the same bridge shape so Studio can remain a reusable control plane instead of a one-host dashboard.",
            "why": "Without the bridge contract, Studio portability stops at static frontend reuse.",
            "likelyFiles": [
                str(REPO_ROOT / "java" / "org" / "l2jmobius" / "gameserver" / "fakeplayer" / "service" / "FakePlayerStudioService.java"),
                str(REPO_ROOT / "java" / "org" / "l2jmobius" / "gameserver" / "fakeplayer" / "FakePlayerModule.java"),
                str(STUDIO_ROOT),
                str(Path(__file__).resolve()),
            ],
            "hostStates": {
                "mobius_tier_a": "live",
                "mobius_essence_family": "planned",
                "mobius_future_host_family": "planned",
                "future_host_adapter": "planned",
                "future_host_secondary": "planned",
                "l2_cplusplus_host": "planned",
                "wow_family_host": "planned",
                "pubg_like_host": "planned",
            },
        },
        {
            "id": "heavy_host_runtime_seams",
            "label": "Heavy Host Runtime Seams",
            "ownerBand": "band_d",
            "seam": "Route, combat-power, appearance, hybrid-party, and script/admin edges",
            "summary": "These are the deepest host-owned runtime seams and the main technical debt behind true host portability.",
            "why": "They are the surfaces most likely to force large rewrites if they are not bounded carefully now.",
            "likelyFiles": [
                str(REPO_ROOT / "java" / "org" / "l2jmobius" / "gameserver" / "fakeplayer" / "service" / "FakePlayerRouteService.java"),
                str(REPO_ROOT / "java" / "org" / "l2jmobius" / "gameserver" / "fakeplayer" / "service" / "FakePlayerCombatPowerService.java"),
                str(REPO_ROOT / "java" / "org" / "l2jmobius" / "gameserver" / "fakeplayer" / "service" / "FakePlayerAppearanceService.java"),
                str(REPO_ROOT / "java" / "org" / "l2jmobius" / "gameserver" / "fakeplayer" / "service" / "FakePlayerHybridPartyService.java"),
                str(REPO_ROOT / "dist" / "game" / "data" / "scripts" / "handlers" / "chathandlers" / "ChatWhisper.java"),
            ],
            "hostStates": {
                "mobius_tier_a": "active",
                "mobius_essence_family": "planned",
                "mobius_future_host_family": "planned",
                "future_host_adapter": "planned",
                "future_host_secondary": "planned",
                "l2_cplusplus_host": "vision",
                "wow_family_host": "vision",
                "pubg_like_host": "vision",
            },
        },
    ]

    portability_bands = [
        {
            "id": "band_a",
            "label": "Band A",
            "title": "Portable Core",
            "status": "strong",
            "goal": "Carry forward with little or no host change.",
            "currentFocus": "Protect and reuse this layer while Studio and adapter seams grow around it.",
            "examples": [
                "sidecar reasoning + workflow surfaces",
                "persona/reply/social data",
                "much of the fakeplayer model layer",
            ],
        },
        {
            "id": "band_b",
            "label": "Band B",
            "title": "Portable Domain Core",
            "status": "active",
            "goal": "Move project logic off raw Mobius-shaped handles and toward neutral platform inputs.",
            "currentFocus": "Decision, knowledge, persona, reply-bank, and social services are the best next extraction targets.",
            "examples": [
                "decision engine",
                "knowledge service",
                "social memory/service",
                "persona resolver",
            ],
        },
        {
            "id": "band_c",
            "label": "Band C",
            "title": "Portability Contracts",
            "status": "growing",
            "goal": "Keep new host and ruleset seams behind explicit contracts instead of direct host logic.",
            "currentFocus": "Expand host/domain adapter surfaces first when new integration work appears.",
            "examples": [
                "platform adapters",
                "ruleset adapters",
                "chat/world/spawn/navigation contracts",
            ],
        },
        {
            "id": "band_d",
            "label": "Band D",
            "title": "Host Integration",
            "status": "heavy",
            "goal": "Bound direct runtime ownership so it can be replaced host by host.",
            "currentFocus": "Route, combat-power, appearance, hybrid-party, script/admin seams remain the heavy owners.",
            "examples": [
                "spawn lifecycle",
                "world lookup",
                "chat transport",
                "hybrid party overlay",
            ],
        },
        {
            "id": "band_e",
            "label": "Band E",
            "title": "Ruleset / Content",
            "status": "plugin",
            "goal": "Keep Warg flavor and facts as replaceable content, not platform core.",
            "currentFocus": "Let future hosts swap rulesets/content without reopening the universal platform logic.",
            "examples": [
                "knowledge cards",
                "routes/loadouts",
                "carrier/content assumptions",
            ],
        },
    ]

    return {
        "status": "ok",
        "generatedAtMs": int(time.time() * 1000),
        "identity": {
            "platformName": "Fakeplayer Platform Control Plane",
            "studioName": "FPC Studio",
            "platformAdapterId": runtime.get("platformAdapterId") or "unknown",
            "rulesetId": runtime.get("rulesetId") or "unknown",
            "bridgeMode": runtime.get("bridgeMode") or "unknown",
            "repoRoot": str(REPO_ROOT),
            "studioRoot": str(STUDIO_ROOT),
            "serverRoot": str(SERVER_ROOT),
        },
        "health": {
            "studioBridge": studio_health_payload.get("status"),
            "runtimeSnapshotPresent": bool(studio_health_payload.get("runtimeSnapshotPresent")),
            "sidecarStatus": health_payload.get("status"),
            "ollamaReachable": bool(health_payload.get("ollamaReachable")),
            "plannerProvider": health_payload.get("plannerProvider"),
            "replyProvider": health_payload.get("replyProvider"),
            "plannerModel": health_payload.get("plannerModel"),
            "replyModel": health_payload.get("replyModel"),
            "loginServerListening": bool((workflow.get("runtime") or {}).get("loginServerListening")),
            "gameServerListening": bool((workflow.get("runtime") or {}).get("gameServerListening")),
        },
        "runtime": runtime,
        "contentPacks": {
            "definitionCount": len(fpcs),
            "socialFpcCount": social_count,
            "afpcCount": afpc_count,
            "templateCount": len(templates),
            "personaCount": len(personas),
            "replyBankCount": len(reply_banks),
            "knowledgeCardCount": len(knowledge_cards),
            "routingRuleCount": len(routing_rules),
            "replySettingFieldCount": len(reply_settings.keys()),
            "rosterEntryCount": len(roster_entries),
            "activeTemplateChannels": sorted(active_channels),
        },
        "authoringLanes": authoring_lanes,
        "capabilityGroups": [
            {
                "id": "live_control",
                "label": "Live Control",
                "items": ["spawn", "despawn", "hold", "resume", "runtime snapshots", "workflow operations"],
            },
            {
                "id": "conversation_lab",
                "label": "Conversation Lab",
                "items": ["conversation probes", "social pair snapshots", "reply compare", "trace filtering", "suite runs"],
            },
            {
                "id": "content_authoring",
                "label": "Content Authoring",
                "items": [lane["label"] for lane in authoring_lanes],
            },
            {
                "id": "explanation",
                "label": "Explanation",
                "items": ["plain-language roadmap", "SVG influence graph", "baseline diff", "field-level edit hints"],
            },
            {
                "id": "workflow",
                "label": "Workflow",
                "items": [entry.get("label") or entry.get("id") for entry in (workflow.get("availableActions") or [])],
            },
        ],
        "controlPlane": {
            "snapshotFiles": [
                "runtime.json",
                "roster.json",
                "fpc/<id>.json",
                "social/<speaker>__<target>.json",
                "trace/replies.jsonl",
                "trace/debug.jsonl",
            ],
            "bridgeSurfaces": [
                "/studio/runtime",
                "/studio/roster",
                "/studio/fpc/{id}",
                "/studio/social/{speaker}/{target}",
                "/studio/traces/replies",
                "/studio/traces/debug",
                "/studio/commands",
                "/studio/results/{commandId}",
                "/studio/workflow",
                "/studio/workflow/operations/{id}",
            ],
            "workflowActions": [entry.get("id") for entry in (workflow.get("availableActions") or [])],
        },
        "hostTargets": host_targets,
        "platformCapabilities": platform_capabilities,
        "portabilityBands": portability_bands,
        "portability": {
            "portableCore": [
                "persona profiles",
                "reply settings",
                "reply banks",
                "knowledge cards",
                "Explain + Authoring Studio frontend",
                "sidecar workflow and report surfaces",
            ],
            "hostBound": [
                "spawn lifecycle",
                "world object lookup",
                "chat transport",
                "navigation facade",
                "hybrid party overlay integration",
            ],
            "rulesetBound": [
                "default social zone",
                "admin intervention hold policy",
                "chronicle-specific guidance assumptions",
            ],
            "currentGoal": "Move Studio toward a host-neutral control plane for the fakeplayer platform.",
        },
    }


def _studio_fpc_entry_summary(entry: Dict[str, Any]) -> Dict[str, Any]:
    spawn = entry.get("spawnProfile") if isinstance(entry.get("spawnProfile"), dict) else {}
    return {
        "id": entry.get("id") or "",
        "name": entry.get("name") or "",
        "title": entry.get("title") or "",
        "template": entry.get("template") or "",
        "archetype": entry.get("archetype") or "",
        "personaTemplate": entry.get("personaTemplate") or "",
        "autoSpawnOnBoot": bool(entry.get("autoSpawnOnBoot")),
        "zone": spawn.get("zone") or "",
    }


def _studio_fpc_template_entry_summary(entry: Dict[str, Any]) -> Dict[str, Any]:
    adventurer = entry.get("adventurerProfile") if isinstance(entry.get("adventurerProfile"), dict) else {}
    channels = entry.get("channels") if isinstance(entry.get("channels"), dict) else {}
    active_channels = [name for name in ("whisper", "general", "shout", "world") if channels.get(name) is True]
    return {
        "templateId": entry.get("templateId") or "",
        "category": entry.get("category") or "",
        "archetype": entry.get("archetype") or "",
        "presentationMode": entry.get("presentationMode") or "",
        "carrierPolicy": entry.get("carrierPolicy") or "",
        "talkable": entry.get("talkable"),
        "enabled": entry.get("enabled"),
        "tier": adventurer.get("tier") or "",
        "channels": active_channels,
    }


def _studio_persona_entry_summary(entry: Dict[str, Any]) -> Dict[str, Any]:
    return {
        "fpcId": entry.get("fpcId") or "",
        "conversationGoal": entry.get("conversationGoal") or "",
        "selfConcept": entry.get("selfConcept") or "",
        "guidancePosture": entry.get("guidancePosture") or "",
    }


def _studio_reply_bank_entry_summary(entry: Dict[str, Any], index: int) -> Dict[str, Any]:
    line = str(entry.get("line") or "").strip()
    preview = line if len(line) <= 88 else (line[:85].rstrip() + "...")
    return {
        "index": index,
        "fpcId": entry.get("fpcId") or "",
        "bankType": entry.get("bankType") or "",
        "category": entry.get("category") or "",
        "channel": entry.get("channel") or "",
        "audience": entry.get("audience") or "",
        "priority": entry.get("priority") or 0,
        "triggers": entry.get("triggers") or "",
        "preview": preview,
    }


def _studio_knowledge_card_entry_summary(entry: Dict[str, Any], index: int) -> Dict[str, Any]:
    summary = str(entry.get("summary") or "").strip()
    preview = summary if len(summary) <= 92 else (summary[:89].rstrip() + "...")
    return {
        "index": index,
        "fpcId": entry.get("fpcId") or "",
        "topicId": entry.get("topicId") or "",
        "domain": entry.get("domain") or "",
        "questionType": entry.get("questionType") or "",
        "channel": entry.get("channel") or "",
        "audience": entry.get("audience") or "",
        "priority": entry.get("priority") or 0,
        "confidenceHint": entry.get("confidenceHint") or "",
        "preview": preview,
    }


def _normalize_int_field(value: Any, field_name: str, minimum: int | None = None) -> int:
    try:
        normalized = int(value)
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"{field_name} must be an integer.") from exc
    if (minimum is not None) and (normalized < minimum):
        raise HTTPException(status_code=400, detail=f"{field_name} must be >= {minimum}.")
    return normalized


def _normalize_str_field(value: Any, field_name: str, required: bool = False) -> str:
    normalized = str(value or "").strip()
    if required and not normalized:
        raise HTTPException(status_code=400, detail=f"{field_name} is required.")
    return normalized


def _normalize_bool_field(value: Any) -> bool:
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return bool(value)
    normalized = str(value or "").strip().lower()
    return normalized in {"1", "true", "yes", "on"}


def _normalize_optional_bool_or_none(value: Any) -> bool | None:
    if value is None:
        return None
    if isinstance(value, str) and not value.strip():
        return None
    return _normalize_bool_field(value)


def _normalize_optional_object(value: Any, field_name: str) -> Dict[str, Any]:
    if value is None:
        return {}
    if not isinstance(value, dict):
        raise HTTPException(status_code=400, detail=f"{field_name} must be a JSON object.")
    return value


def _normalize_optional_int_or_none(value: Any, field_name: str, minimum: int | None = None) -> int | None:
    if value is None:
        return None
    if isinstance(value, str) and not value.strip():
        return None
    return _normalize_int_field(value, field_name, minimum)


def _normalize_optional_object_or_none(value: Any, field_name: str) -> Dict[str, Any] | None:
    if value is None:
        return None
    normalized = _normalize_optional_object(value, field_name)
    return normalized if normalized else None


def _normalize_fpc_entry(entry: Dict[str, Any]) -> Dict[str, Any]:
    if not isinstance(entry, dict):
        raise HTTPException(status_code=400, detail="FPC entry payload must be a JSON object.")

    normalized_id = _studio_snapshot_component(entry.get("id"))
    if normalized_id == "unknown":
        raise HTTPException(status_code=400, detail="id is required.")
    if not re.fullmatch(r"[a-z0-9_]+", normalized_id):
        raise HTTPException(status_code=400, detail="id must use lowercase letters, digits, and underscores only.")

    spawn_profile = entry.get("spawnProfile")
    if not isinstance(spawn_profile, dict):
        raise HTTPException(status_code=400, detail="spawnProfile must be a JSON object.")

    normalized: Dict[str, Any] = {
        "id": normalized_id,
        "template": _normalize_str_field(entry.get("template"), "template", required=True),
        "name": _normalize_str_field(entry.get("name"), "name", required=True),
        "title": _normalize_str_field(entry.get("title"), "title", required=True),
        "archetype": _normalize_str_field(entry.get("archetype"), "archetype", required=True),
        "personaTemplate": _normalize_str_field(entry.get("personaTemplate"), "personaTemplate", required=True),
        "carrierTemplate": _normalize_str_field(entry.get("carrierTemplate"), "carrierTemplate", required=True),
        "carrierNpcId": _normalize_int_field(entry.get("carrierNpcId", 0), "carrierNpcId", minimum=0),
        "autoSpawnOnBoot": _normalize_bool_field(entry.get("autoSpawnOnBoot", False)),
        "spawnProfile": {
            "mode": _normalize_str_field(spawn_profile.get("mode"), "spawnProfile.mode", required=True),
            "zone": _normalize_str_field(spawn_profile.get("zone"), "spawnProfile.zone", required=True),
            "x": _normalize_int_field(spawn_profile.get("x", 0), "spawnProfile.x"),
            "y": _normalize_int_field(spawn_profile.get("y", 0), "spawnProfile.y"),
            "z": _normalize_int_field(spawn_profile.get("z", 0), "spawnProfile.z"),
            "heading": _normalize_int_field(spawn_profile.get("heading", 0), "spawnProfile.heading"),
        },
        "channels": _normalize_optional_object(entry.get("channels", {}), "channels"),
        "fallbackProfile": _normalize_str_field(entry.get("fallbackProfile"), "fallbackProfile"),
        "availabilityProfile": _normalize_str_field(entry.get("availabilityProfile"), "availabilityProfile"),
        "presentationCarrierTemplate": _normalize_str_field(entry.get("presentationCarrierTemplate"), "presentationCarrierTemplate"),
        "presentationCarrierNpcId": _normalize_int_field(entry.get("presentationCarrierNpcId", 0), "presentationCarrierNpcId", minimum=0),
        "speciesTag": _normalize_str_field(entry.get("speciesTag"), "speciesTag"),
        "familyTag": _normalize_str_field(entry.get("familyTag"), "familyTag"),
    }

    if "adventurerProfile" in entry:
        normalized["adventurerProfile"] = _normalize_optional_object(entry.get("adventurerProfile"), "adventurerProfile")

    passthrough_keys = [
        "category",
        "carrierPolicy",
        "fallbackProfile",
        "availabilityProfile",
        "fpcTag",
        "presentationMode",
        "presentationCarrierTemplate",
        "presentationCarrierNpcId",
        "speciesTag",
        "familyTag",
    ]
    for key in passthrough_keys:
        if key in entry and key not in normalized:
            normalized[key] = entry[key]

    for key, value in entry.items():
        if key not in normalized:
            normalized[key] = value

    return normalized


def _normalize_fpc_template_entry(entry: Dict[str, Any]) -> Dict[str, Any]:
    if not isinstance(entry, dict):
        raise HTTPException(status_code=400, detail="FPC template payload must be a JSON object.")

    normalized_template_id = _studio_snapshot_component(entry.get("templateId"))
    if normalized_template_id == "unknown":
        raise HTTPException(status_code=400, detail="templateId is required.")
    if not re.fullmatch(r"[a-z0-9_]+", normalized_template_id):
        raise HTTPException(status_code=400, detail="templateId must use lowercase letters, digits, and underscores only.")

    normalized: Dict[str, Any] = {
        "templateId": normalized_template_id,
    }

    optional_string_fields = [
        "title",
        "category",
        "archetype",
        "personaTemplate",
        "carrierTemplate",
        "fallbackProfile",
        "availabilityProfile",
        "fpcTag",
        "presentationMode",
        "carrierPolicy",
        "presentationCarrierTemplate",
        "speciesTag",
        "familyTag",
    ]
    for field_name in optional_string_fields:
        value = _normalize_str_field(entry.get(field_name), field_name)
        if value:
            normalized[field_name] = value

    optional_int_fields = [
        ("carrierNpcId", 0),
        ("presentationCarrierNpcId", 0),
    ]
    for field_name, minimum in optional_int_fields:
        value = _normalize_optional_int_or_none(entry.get(field_name), field_name, minimum)
        if value is not None:
            normalized[field_name] = value

    optional_bool_fields = [
        "enabled",
        "autoSpawnOnBoot",
        "talkable",
    ]
    for field_name in optional_bool_fields:
        value = _normalize_optional_bool_or_none(entry.get(field_name))
        if value is not None:
            normalized[field_name] = value

    spawn_profile = _normalize_optional_object_or_none(entry.get("spawnProfile"), "spawnProfile")
    if spawn_profile:
        normalized["spawnProfile"] = spawn_profile

    channels = _normalize_optional_object_or_none(entry.get("channels"), "channels")
    if channels:
        normalized["channels"] = channels

    adventurer_profile = _normalize_optional_object_or_none(entry.get("adventurerProfile"), "adventurerProfile")
    if adventurer_profile:
        normalized["adventurerProfile"] = adventurer_profile

    managed_keys = {
        "templateId",
        "title",
        "enabled",
        "category",
        "archetype",
        "personaTemplate",
        "carrierTemplate",
        "carrierNpcId",
        "autoSpawnOnBoot",
        "spawnProfile",
        "channels",
        "talkable",
        "fallbackProfile",
        "availabilityProfile",
        "fpcTag",
        "presentationMode",
        "carrierPolicy",
        "presentationCarrierTemplate",
        "presentationCarrierNpcId",
        "speciesTag",
        "familyTag",
        "adventurerProfile",
    }
    for key, value in entry.items():
        if key not in managed_keys:
            normalized[key] = value

    return normalized


def _normalize_persona_profile(entry: Dict[str, Any]) -> Dict[str, Any]:
    if not isinstance(entry, dict):
        raise HTTPException(status_code=400, detail="Persona profile payload must be a JSON object.")

    normalized_fpc_id = _studio_snapshot_component(entry.get("fpcId"))
    if normalized_fpc_id == "unknown":
        raise HTTPException(status_code=400, detail="fpcId is required.")
    if not re.fullmatch(r"[a-z0-9_]+", normalized_fpc_id):
        raise HTTPException(status_code=400, detail="fpcId must use lowercase letters, digits, and underscores only.")

    normalized: Dict[str, Any] = {
        "fpcId": normalized_fpc_id,
        "originSummary": _normalize_str_field(entry.get("originSummary"), "originSummary"),
        "selfKnowledgeSummary": _normalize_str_field(entry.get("selfKnowledgeSummary"), "selfKnowledgeSummary"),
        "conversationGoal": _normalize_str_field(entry.get("conversationGoal"), "conversationGoal"),
        "hiddenFear": _normalize_str_field(entry.get("hiddenFear"), "hiddenFear"),
        "creatorSummary": _normalize_str_field(entry.get("creatorSummary"), "creatorSummary"),
        "publicMaskSummary": _normalize_str_field(entry.get("publicMaskSummary"), "publicMaskSummary"),
        "hiddenTruthSummary": _normalize_str_field(entry.get("hiddenTruthSummary"), "hiddenTruthSummary"),
        "selfConcept": _normalize_str_field(entry.get("selfConcept"), "selfConcept"),
        "coreWound": _normalize_str_field(entry.get("coreWound"), "coreWound"),
        "coreDesire": _normalize_str_field(entry.get("coreDesire"), "coreDesire"),
        "loyaltyAnchor": _normalize_str_field(entry.get("loyaltyAnchor"), "loyaltyAnchor"),
        "resentmentAnchor": _normalize_str_field(entry.get("resentmentAnchor"), "resentmentAnchor"),
        "privateTaboo": _normalize_str_field(entry.get("privateTaboo"), "privateTaboo"),
        "speechAnchor": _normalize_str_field(entry.get("speechAnchor"), "speechAnchor"),
        "privateContradiction": _normalize_str_field(entry.get("privateContradiction"), "privateContradiction"),
        "guidancePosture": _normalize_str_field(entry.get("guidancePosture"), "guidancePosture"),
        "guidancePriority": _normalize_str_field(entry.get("guidancePriority"), "guidancePriority"),
        "uncertaintyStyle": _normalize_str_field(entry.get("uncertaintyStyle"), "uncertaintyStyle"),
        "recommendationStyle": _normalize_str_field(entry.get("recommendationStyle"), "recommendationStyle"),
        "riskStyle": _normalize_str_field(entry.get("riskStyle"), "riskStyle"),
        "teachingStyle": _normalize_str_field(entry.get("teachingStyle"), "teachingStyle"),
    }

    for key, value in entry.items():
        if key not in normalized:
            normalized[key] = value

    return normalized


def _normalize_reply_bank_entry(entry: Dict[str, Any]) -> Dict[str, Any]:
    if not isinstance(entry, dict):
        raise HTTPException(status_code=400, detail="Reply bank payload must be a JSON object.")

    normalized_fpc_id = _studio_snapshot_component(entry.get("fpcId"))
    if normalized_fpc_id == "unknown":
        raise HTTPException(status_code=400, detail="fpcId is required.")
    if not re.fullmatch(r"[a-z0-9_]+", normalized_fpc_id):
        raise HTTPException(status_code=400, detail="fpcId must use lowercase letters, digits, and underscores only.")

    normalized: Dict[str, Any] = {
        "fpcId": normalized_fpc_id,
        "bankType": _normalize_str_field(entry.get("bankType"), "bankType", required=True),
        "category": _normalize_str_field(entry.get("category"), "category", required=True),
        "channel": _normalize_str_field(entry.get("channel"), "channel", required=True),
        "audience": _normalize_str_field(entry.get("audience"), "audience", required=True),
        "triggers": _normalize_str_field(entry.get("triggers"), "triggers"),
        "priority": _normalize_int_field(entry.get("priority", 0), "priority"),
        "line": _normalize_str_field(entry.get("line"), "line", required=True),
    }

    for key, value in entry.items():
        if key not in normalized:
            normalized[key] = value

    return normalized


def _normalize_knowledge_card_entry(entry: Dict[str, Any]) -> Dict[str, Any]:
    if not isinstance(entry, dict):
        raise HTTPException(status_code=400, detail="Knowledge card payload must be a JSON object.")

    normalized_fpc_id = _studio_snapshot_component(entry.get("fpcId"))
    if normalized_fpc_id == "unknown":
        raise HTTPException(status_code=400, detail="fpcId is required.")
    if not re.fullmatch(r"[a-z0-9_]+", normalized_fpc_id):
        raise HTTPException(status_code=400, detail="fpcId must use lowercase letters, digits, and underscores only.")

    normalized_topic_id = _studio_snapshot_component(entry.get("topicId"))
    if normalized_topic_id == "unknown":
        raise HTTPException(status_code=400, detail="topicId is required.")
    if not re.fullmatch(r"[a-z0-9_]+", normalized_topic_id):
        raise HTTPException(status_code=400, detail="topicId must use lowercase letters, digits, and underscores only.")

    normalized: Dict[str, Any] = {
        "fpcId": normalized_fpc_id,
        "topicId": normalized_topic_id,
        "domain": _normalize_str_field(entry.get("domain"), "domain", required=True),
        "questionType": _normalize_str_field(entry.get("questionType"), "questionType", required=True),
        "channel": _normalize_str_field(entry.get("channel"), "channel", required=True),
        "audience": _normalize_str_field(entry.get("audience"), "audience", required=True),
        "triggers": _normalize_str_field(entry.get("triggers"), "triggers"),
        "priority": _normalize_int_field(entry.get("priority", 0), "priority"),
        "summary": _normalize_str_field(entry.get("summary"), "summary", required=True),
        "facts": _normalize_str_field(entry.get("facts"), "facts"),
        "generalAnswer": _normalize_str_field(entry.get("generalAnswer"), "generalAnswer", required=True),
        "whisperAnswer": _normalize_str_field(entry.get("whisperAnswer"), "whisperAnswer"),
        "shortAdvice": _normalize_str_field(entry.get("shortAdvice"), "shortAdvice"),
        "confidenceHint": _normalize_str_field(entry.get("confidenceHint"), "confidenceHint"),
    }

    for key, value in entry.items():
        if key not in normalized:
            normalized[key] = value

    return normalized


def _normalize_reply_settings_entry(entry: Dict[str, Any]) -> Dict[str, Any]:
    if not isinstance(entry, dict):
        raise HTTPException(status_code=400, detail="Reply settings payload must be a JSON object.")

    defaults = dict(DEFAULT_REPLY_SETTINGS)
    normalized: Dict[str, Any] = {
        "recentConversationTurns": _normalize_int_field(entry.get("recentConversationTurns", defaults["recentConversationTurns"]), "recentConversationTurns", minimum=1),
        "recentConversationTtlMs": _normalize_int_field(entry.get("recentConversationTtlMs", defaults["recentConversationTtlMs"]), "recentConversationTtlMs", minimum=1),
        "followUpStrictness": _normalize_str_field(entry.get("followUpStrictness", defaults["followUpStrictness"]), "followUpStrictness", required=True),
        "generalMaxWords": _normalize_int_field(entry.get("generalMaxWords", defaults["generalMaxWords"]), "generalMaxWords", minimum=1),
        "whisperMaxWords": _normalize_int_field(entry.get("whisperMaxWords", defaults["whisperMaxWords"]), "whisperMaxWords", minimum=1),
        "publicMaxWords": _normalize_int_field(entry.get("publicMaxWords", defaults["publicMaxWords"]), "publicMaxWords", minimum=1),
        "whisperAllowSecondSentence": _normalize_bool_field(entry.get("whisperAllowSecondSentence", defaults["whisperAllowSecondSentence"])),
        "knowledgeConfidencePolicy": _normalize_str_field(entry.get("knowledgeConfidencePolicy", defaults["knowledgeConfidencePolicy"]), "knowledgeConfidencePolicy", required=True),
        "progressionCoachingLevel": _normalize_str_field(entry.get("progressionCoachingLevel", defaults["progressionCoachingLevel"]), "progressionCoachingLevel", required=True),
        "pvpConflictPolicy": _normalize_str_field(entry.get("pvpConflictPolicy", defaults["pvpConflictPolicy"]), "pvpConflictPolicy", required=True),
        "relationshipWarmth": _normalize_str_field(entry.get("relationshipWarmth", defaults["relationshipWarmth"]), "relationshipWarmth", required=True),
        "offscopeClassKnowledge": _normalize_str_field(entry.get("offscopeClassKnowledge", defaults["offscopeClassKnowledge"]), "offscopeClassKnowledge", required=True),
        "farmAdviceMode": _normalize_str_field(entry.get("farmAdviceMode", defaults["farmAdviceMode"]), "farmAdviceMode", required=True),
        "travelStyle": _normalize_str_field(entry.get("travelStyle", defaults["travelStyle"]), "travelStyle", required=True),
        "socialReplyMode": _normalize_str_field(entry.get("socialReplyMode", defaults["socialReplyMode"]), "socialReplyMode", required=True),
        "runtimeBudgetMode": _normalize_str_field(entry.get("runtimeBudgetMode", defaults["runtimeBudgetMode"]), "runtimeBudgetMode", required=True),
        "generalBudgetWindowSeconds": _normalize_int_field(entry.get("generalBudgetWindowSeconds", defaults["generalBudgetWindowSeconds"]), "generalBudgetWindowSeconds", minimum=1),
        "generalBudgetMaxReplies": _normalize_int_field(entry.get("generalBudgetMaxReplies", defaults["generalBudgetMaxReplies"]), "generalBudgetMaxReplies", minimum=1),
        "shoutBudgetWindowSeconds": _normalize_int_field(entry.get("shoutBudgetWindowSeconds", defaults["shoutBudgetWindowSeconds"]), "shoutBudgetWindowSeconds", minimum=1),
        "shoutBudgetMaxReplies": _normalize_int_field(entry.get("shoutBudgetMaxReplies", defaults["shoutBudgetMaxReplies"]), "shoutBudgetMaxReplies", minimum=1),
        "worldBudgetWindowSeconds": _normalize_int_field(entry.get("worldBudgetWindowSeconds", defaults["worldBudgetWindowSeconds"]), "worldBudgetWindowSeconds", minimum=1),
        "worldBudgetMaxReplies": _normalize_int_field(entry.get("worldBudgetMaxReplies", defaults["worldBudgetMaxReplies"]), "worldBudgetMaxReplies", minimum=1),
        "repeatPromptWindowSeconds": _normalize_int_field(entry.get("repeatPromptWindowSeconds", defaults["repeatPromptWindowSeconds"]), "repeatPromptWindowSeconds", minimum=1),
        "repeatPromptReuseLimit": _normalize_int_field(entry.get("repeatPromptReuseLimit", defaults["repeatPromptReuseLimit"]), "repeatPromptReuseLimit", minimum=1),
    }

    for key, value in entry.items():
        if key not in normalized:
            normalized[key] = value

    return normalized


def _backup_json_list_content(content_id: str, entries: List[Dict[str, Any]], reason: str) -> Path:
    backup_root = _studio_content_backups_root(content_id)
    backup_path = backup_root / f"{int(time.time() * 1000)}_{_studio_report_slug(reason)}.json"
    _write_json_list_file(backup_path, entries)
    return backup_path


def _backup_json_object_content(content_id: str, entry: Dict[str, Any], reason: str) -> Path:
    backup_root = _studio_content_backups_root(content_id)
    backup_path = backup_root / f"{int(time.time() * 1000)}_{_studio_report_slug(reason)}.json"
    _write_json_object_file(backup_path, entry)
    return backup_path


def _save_mode_error_detail(allowed_modes: tuple[str, ...]) -> str:
    quoted_modes = [f"'{mode}'" for mode in allowed_modes]
    if len(quoted_modes) == 1:
        return f"saveMode must be {quoted_modes[0]}."
    if len(quoted_modes) == 2:
        return f"saveMode must be {quoted_modes[0]} or {quoted_modes[1]}."
    return f"saveMode must be {', '.join(quoted_modes[:-1])}, or {quoted_modes[-1]}."


def _normalize_content_save_mode(save_mode: str, allowed_modes: tuple[str, ...]) -> str:
    normalized_mode = (save_mode or "replace").strip().lower()
    if normalized_mode not in allowed_modes:
        raise HTTPException(status_code=400, detail=_save_mode_error_detail(allowed_modes))
    return normalized_mode


def _find_entry_index_by_component(entries: List[Dict[str, Any]], field_name: str, normalized_value: str) -> int:
    if not normalized_value:
        return -1
    return next(
        (index for index, item in enumerate(entries) if _studio_snapshot_component(item.get(field_name)) == normalized_value),
        -1,
    )


def _save_identified_content_entry(
    *,
    entries: List[Dict[str, Any]],
    original_id: str | None,
    save_mode: str,
    entry: Dict[str, Any],
    path: Path,
    key_name: str,
    source_label: str,
    allowed_modes: tuple[str, ...],
    normalize_entry: Callable[[Dict[str, Any]], Dict[str, Any]],
    summary_builder: Callable[[Dict[str, Any]], Dict[str, Any]],
    backup_entries: Callable[[List[Dict[str, Any]], str], Path],
    create_conflict_detail: Callable[[str], str],
    rename_conflict_detail: Callable[[str], str],
    normalize_before_mode_check: bool = False,
) -> Dict[str, Any]:
    normalized_original = _studio_snapshot_component(original_id) if original_id else ""
    normalized_entry = normalize_entry(entry) if normalize_before_mode_check else None
    normalized_mode = _normalize_content_save_mode(save_mode, allowed_modes)
    original_index = _find_entry_index_by_component(entries, key_name, normalized_original)

    if normalized_mode == "delete":
        if original_index < 0:
            raise HTTPException(status_code=404, detail=f"Could not find source {source_label} '{original_id}'.")
        removed_entry = dict(entries[original_index])
        updated_entries = list(entries)
        del updated_entries[original_index]
        backup_path = backup_entries(entries, f"delete_{normalized_original or removed_entry.get(key_name)}")
        _write_json_list_file(path, updated_entries)
        return {
            "status": "ok",
            "mode": "delete",
            "originalId": normalized_original or _studio_snapshot_component(removed_entry.get(key_name)),
            "filePath": str(path),
            "backupPath": str(backup_path),
            "removed": removed_entry,
            "summary": summary_builder(removed_entry),
        }

    if normalized_entry is None:
        normalized_entry = normalize_entry(entry)
    entry_id = _studio_snapshot_component(normalized_entry.get(key_name))
    existing_index = _find_entry_index_by_component(entries, key_name, entry_id)

    if normalized_mode == "create":
        if existing_index >= 0:
            raise HTTPException(status_code=409, detail=create_conflict_detail(entry_id))
        updated_entries = list(entries)
        updated_entries.append(normalized_entry)
        backup_path = backup_entries(entries, f"create_{entry_id}")
        _write_json_list_file(path, updated_entries)
        return {
            "status": "ok",
            "mode": "create",
            "entry": normalized_entry,
            "filePath": str(path),
            "backupPath": str(backup_path),
            "summary": summary_builder(normalized_entry),
        }

    if original_index < 0:
        raise HTTPException(status_code=404, detail=f"Could not find source {source_label} '{original_id}'.")
    if entry_id != normalized_original and existing_index >= 0:
        raise HTTPException(status_code=409, detail=rename_conflict_detail(entry_id))

    updated_entries = list(entries)
    updated_entries[original_index] = normalized_entry
    backup_path = backup_entries(entries, f"replace_{normalized_original or entry_id}")
    _write_json_list_file(path, updated_entries)
    return {
        "status": "ok",
        "mode": "replace",
        "entry": normalized_entry,
        "filePath": str(path),
        "backupPath": str(backup_path),
        "summary": summary_builder(normalized_entry),
    }


def _save_indexed_content_entry(
    *,
    entries: List[Dict[str, Any]],
    original_index: int | None,
    save_mode: str,
    entry: Dict[str, Any],
    path: Path,
    missing_index_detail: str,
    normalize_entry: Callable[[Dict[str, Any]], Dict[str, Any]],
    summary_builder: Callable[[Dict[str, Any], int], Dict[str, Any]],
    backup_entries: Callable[[List[Dict[str, Any]], str], Path],
    delete_reason: Callable[[Dict[str, Any], int], str],
    create_reason: Callable[[Dict[str, Any], int], str],
    replace_reason: Callable[[Dict[str, Any], int], str],
    after_write: Callable[[List[Dict[str, Any]]], None] | None = None,
) -> Dict[str, Any]:
    normalized_mode = _normalize_content_save_mode(save_mode, ("replace", "create", "delete"))
    resolved_original_index = int(original_index) if original_index is not None else -1
    if (resolved_original_index >= len(entries)) or (resolved_original_index < -1):
        raise HTTPException(status_code=404, detail=f"{missing_index_detail}: {resolved_original_index}")

    if normalized_mode == "delete":
        if resolved_original_index < 0:
            raise HTTPException(status_code=400, detail="originalIndex is required for delete.")
        removed_entry = dict(entries[resolved_original_index])
        updated_entries = list(entries)
        del updated_entries[resolved_original_index]
        backup_path = backup_entries(entries, delete_reason(removed_entry, resolved_original_index))
        _write_json_list_file(path, updated_entries)
        if after_write is not None:
            after_write(updated_entries)
        return {
            "status": "ok",
            "mode": "delete",
            "originalIndex": resolved_original_index,
            "filePath": str(path),
            "backupPath": str(backup_path),
            "removed": removed_entry,
            "summary": summary_builder(removed_entry, resolved_original_index),
        }

    normalized_entry = normalize_entry(entry)
    if normalized_mode == "create":
        updated_entries = list(entries)
        updated_entries.append(normalized_entry)
        created_index = len(updated_entries) - 1
        backup_path = backup_entries(entries, create_reason(normalized_entry, created_index))
        _write_json_list_file(path, updated_entries)
        if after_write is not None:
            after_write(updated_entries)
        return {
            "status": "ok",
            "mode": "create",
            "entry": normalized_entry,
            "index": created_index,
            "filePath": str(path),
            "backupPath": str(backup_path),
            "summary": summary_builder(normalized_entry, created_index),
        }

    if resolved_original_index < 0:
        raise HTTPException(status_code=400, detail="originalIndex is required for replace.")
    updated_entries = list(entries)
    updated_entries[resolved_original_index] = normalized_entry
    backup_path = backup_entries(entries, replace_reason(normalized_entry, resolved_original_index))
    _write_json_list_file(path, updated_entries)
    if after_write is not None:
        after_write(updated_entries)
    return {
        "status": "ok",
        "mode": "replace",
        "entry": normalized_entry,
        "index": resolved_original_index,
        "filePath": str(path),
        "backupPath": str(backup_path),
        "summary": summary_builder(normalized_entry, resolved_original_index),
    }


def _backup_fpc_definitions(entries: List[Dict[str, Any]], reason: str) -> Path:
    return _backup_json_list_content("fpcs", entries, reason)


def _backup_fpc_templates(entries: List[Dict[str, Any]], reason: str) -> Path:
    return _backup_json_list_content("fpc_templates", entries, reason)


def _save_fpc_entry(original_id: str | None, save_mode: str, entry: Dict[str, Any]) -> Dict[str, Any]:
    return _save_identified_content_entry(
        entries=_studio_fpc_source_entries(),
        original_id=original_id,
        save_mode=save_mode,
        entry=entry,
        path=FPC_DEFINITIONS_PATH,
        key_name="id",
        source_label="FPC definition",
        allowed_modes=("replace", "create", "delete"),
        normalize_entry=_normalize_fpc_entry,
        summary_builder=_studio_fpc_entry_summary,
        backup_entries=_backup_fpc_definitions,
        create_conflict_detail=lambda normalized_id: f"An FPC definition with id '{normalized_id}' already exists.",
        rename_conflict_detail=lambda normalized_id: f"Cannot rename to '{normalized_id}' because that id already exists.",
    )


def _save_fpc_template_entry(original_id: str | None, save_mode: str, entry: Dict[str, Any]) -> Dict[str, Any]:
    return _save_identified_content_entry(
        entries=_studio_fpc_template_source_entries(),
        original_id=original_id,
        save_mode=save_mode,
        entry=entry,
        path=FPC_TEMPLATES_PATH,
        key_name="templateId",
        source_label="FPC template",
        allowed_modes=("replace", "create"),
        normalize_entry=_normalize_fpc_template_entry,
        summary_builder=_studio_fpc_template_entry_summary,
        backup_entries=_backup_fpc_templates,
        create_conflict_detail=lambda normalized_id: f"An FPC template with templateId '{normalized_id}' already exists.",
        rename_conflict_detail=lambda normalized_id: f"Cannot rename to '{normalized_id}' because that template already exists.",
        normalize_before_mode_check=True,
    )


def _backup_persona_profiles(entries: List[Dict[str, Any]], reason: str) -> Path:
    return _backup_json_list_content("persona_profiles", entries, reason)


def _save_persona_profile(original_id: str | None, save_mode: str, entry: Dict[str, Any]) -> Dict[str, Any]:
    return _save_identified_content_entry(
        entries=_studio_persona_source_entries(),
        original_id=original_id,
        save_mode=save_mode,
        entry=entry,
        path=PERSONA_PROFILES_PATH,
        key_name="fpcId",
        source_label="persona profile",
        allowed_modes=("replace", "create", "delete"),
        normalize_entry=_normalize_persona_profile,
        summary_builder=_studio_persona_entry_summary,
        backup_entries=_backup_persona_profiles,
        create_conflict_detail=lambda normalized_id: f"A persona profile with fpcId '{normalized_id}' already exists.",
        rename_conflict_detail=lambda normalized_id: f"Cannot rename to '{normalized_id}' because that persona profile already exists.",
    )


def _backup_reply_banks(entries: List[Dict[str, Any]], reason: str) -> Path:
    return _backup_json_list_content("reply_banks", entries, reason)


def _save_reply_bank_entry(original_index: int | None, save_mode: str, entry: Dict[str, Any]) -> Dict[str, Any]:
    return _save_indexed_content_entry(
        entries=_studio_reply_bank_source_entries(),
        original_index=original_index,
        save_mode=save_mode,
        entry=entry,
        path=REPLY_BANKS_PATH,
        missing_index_detail="Reply bank source index not found",
        normalize_entry=_normalize_reply_bank_entry,
        summary_builder=_studio_reply_bank_entry_summary,
        backup_entries=_backup_reply_banks,
        delete_reason=lambda removed_entry, index: f"delete_{removed_entry.get('fpcId')}_{removed_entry.get('category')}_{index}",
        create_reason=lambda normalized_entry, index: f"create_{normalized_entry['fpcId']}_{normalized_entry['category']}_{index}",
        replace_reason=lambda normalized_entry, index: f"replace_{normalized_entry['fpcId']}_{normalized_entry['category']}_{index}",
    )


def _backup_knowledge_cards(entries: List[Dict[str, Any]], reason: str) -> Path:
    return _backup_json_list_content("knowledge_cards", entries, reason)


def _backup_reply_settings(entry: Dict[str, Any], reason: str) -> Path:
    return _backup_json_object_content("reply_settings", entry, reason)


def _backup_routing_rules(entries: List[Dict[str, Any]], reason: str) -> Path:
    return _backup_json_list_content("routing_rules", entries, reason)


def _set_reply_settings_cache(entry: Dict[str, Any]) -> None:
    global _REPLY_SETTINGS_CACHE, _REPLY_SETTINGS_MTIME
    _REPLY_SETTINGS_CACHE = dict(DEFAULT_REPLY_SETTINGS)
    _REPLY_SETTINGS_CACHE.update({key: value for key, value in entry.items() if value is not None})
    try:
        _REPLY_SETTINGS_MTIME = REPLY_SETTINGS_PATH.stat().st_mtime
    except Exception:
        _REPLY_SETTINGS_MTIME = None


def _normalize_text_list(value: Any) -> List[str]:
    if isinstance(value, list):
        raw_items = value
    elif isinstance(value, str):
        raw_items = re.split(r"[\r\n,]+", value)
    else:
        raw_items = []
    normalized: List[str] = []
    seen: set[str] = set()
    for item in raw_items:
        text = str(item or "").strip()
        if not text:
            continue
        lowered = text.lower()
        if lowered in seen:
            continue
        seen.add(lowered)
        normalized.append(text)
    return normalized


def _normalize_routing_rule_entry(entry: Dict[str, Any]) -> Dict[str, Any]:
    if not isinstance(entry, dict):
        raise HTTPException(status_code=400, detail="Routing rule entry must be an object.")
    rule_id = _studio_snapshot_component(entry.get("ruleId"))
    if rule_id == "unknown":
        raise HTTPException(status_code=400, detail="Routing rule id is required.")
    target_type = str(entry.get("targetType") or "").strip().lower()
    if target_type not in {"message_category", "knowledge_type", "focus_intent", "bond_probe"}:
        raise HTTPException(status_code=400, detail="targetType must be message_category, knowledge_type, focus_intent, or bond_probe.")
    result = str(entry.get("result") or "").strip().lower()
    if not result:
        raise HTTPException(status_code=400, detail="result is required.")
    scope = str(entry.get("scope") or "current_utterance").strip().lower() or "current_utterance"
    if scope not in {"current_utterance", "full_text"}:
        raise HTTPException(status_code=400, detail="scope must be current_utterance or full_text.")
    any_phrases = _normalize_text_list(entry.get("anyPhrases"))
    all_phrases = _normalize_text_list(entry.get("allPhrases"))
    exclude_phrases = _normalize_text_list(entry.get("excludePhrases"))
    if not any_phrases and not all_phrases:
        raise HTTPException(status_code=400, detail="Provide at least one Any Phrase or All Phrase.")
    try:
        priority = int(entry.get("priority") or 0)
    except Exception as exc:
        raise HTTPException(status_code=400, detail="priority must be a number.") from exc
    return {
        "ruleId": rule_id,
        "targetType": target_type,
        "result": result,
        "scope": scope,
        "enabled": bool(entry.get("enabled", True)),
        "priority": priority,
        "anyPhrases": any_phrases,
        "allPhrases": all_phrases,
        "excludePhrases": exclude_phrases,
        "notes": str(entry.get("notes") or "").strip(),
    }


def _studio_routing_rule_entry_summary(entry: Dict[str, Any], index: int) -> Dict[str, Any]:
    any_phrases = entry.get("anyPhrases") if isinstance(entry.get("anyPhrases"), list) else []
    all_phrases = entry.get("allPhrases") if isinstance(entry.get("allPhrases"), list) else []
    preview = ""
    if any_phrases:
        preview = str(any_phrases[0])
    elif all_phrases:
        preview = str(all_phrases[0])
    return {
        "index": index,
        "ruleId": str(entry.get("ruleId") or ""),
        "targetType": str(entry.get("targetType") or ""),
        "result": str(entry.get("result") or ""),
        "scope": str(entry.get("scope") or "current_utterance"),
        "enabled": bool(entry.get("enabled", True)),
        "priority": int(entry.get("priority") or 0),
        "phraseCount": len(any_phrases) + len(all_phrases),
        "preview": preview,
    }


def _set_routing_rules_cache(entries: List[Dict[str, Any]]) -> None:
    global _ROUTING_RULES_CACHE, _ROUTING_RULES_MTIME
    normalized: List[Dict[str, Any]] = []
    for entry in entries:
        try:
            normalized.append(_normalize_routing_rule_entry(entry))
        except Exception:
            continue
    normalized.sort(key=lambda item: int(item.get("priority") or 0), reverse=True)
    _ROUTING_RULES_CACHE = normalized
    try:
        _ROUTING_RULES_MTIME = ROUTING_RULES_PATH.stat().st_mtime
    except Exception:
        _ROUTING_RULES_MTIME = None


def _save_knowledge_card_entry(original_index: int | None, save_mode: str, entry: Dict[str, Any]) -> Dict[str, Any]:
    return _save_indexed_content_entry(
        entries=_studio_knowledge_card_source_entries(),
        original_index=original_index,
        save_mode=save_mode,
        entry=entry,
        path=KNOWLEDGE_CARDS_PATH,
        missing_index_detail="Knowledge card source index not found",
        normalize_entry=_normalize_knowledge_card_entry,
        summary_builder=_studio_knowledge_card_entry_summary,
        backup_entries=_backup_knowledge_cards,
        delete_reason=lambda removed_entry, index: f"delete_{removed_entry.get('fpcId')}_{removed_entry.get('topicId')}_{index}",
        create_reason=lambda normalized_entry, index: f"create_{normalized_entry['fpcId']}_{normalized_entry['topicId']}_{index}",
        replace_reason=lambda normalized_entry, index: f"replace_{normalized_entry['fpcId']}_{normalized_entry['topicId']}_{index}",
    )


def _save_routing_rule_entry(original_index: int | None, save_mode: str, entry: Dict[str, Any]) -> Dict[str, Any]:
    return _save_indexed_content_entry(
        entries=_studio_routing_rule_source_entries(),
        original_index=original_index,
        save_mode=save_mode,
        entry=entry,
        path=ROUTING_RULES_PATH,
        missing_index_detail="Routing rule source index not found",
        normalize_entry=_normalize_routing_rule_entry,
        summary_builder=_studio_routing_rule_entry_summary,
        backup_entries=_backup_routing_rules,
        delete_reason=lambda removed_entry, index: f"delete_{removed_entry.get('ruleId')}_{index}",
        create_reason=lambda normalized_entry, index: f"create_{normalized_entry['ruleId']}_{index}",
        replace_reason=lambda normalized_entry, index: f"replace_{normalized_entry['ruleId']}_{index}",
        after_write=_set_routing_rules_cache,
    )


def _save_reply_settings_entry(entry: Dict[str, Any]) -> Dict[str, Any]:
    current = _studio_reply_settings_source_entry()
    normalized_entry = _normalize_reply_settings_entry(entry)
    backup_path = _backup_reply_settings(current, "replace_reply_settings")
    _write_json_object_file(REPLY_SETTINGS_PATH, normalized_entry)
    _set_reply_settings_cache(normalized_entry)
    return {
        "status": "ok",
        "mode": "replace",
        "entry": normalized_entry,
        "filePath": str(REPLY_SETTINGS_PATH),
        "backupPath": str(backup_path),
    }


def _workflow_now_iso() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%S", time.localtime())


def _workflow_operation_copy(entry: Dict[str, Any] | None) -> Dict[str, Any] | None:
    if not entry:
        return None
    return json.loads(json.dumps(entry, ensure_ascii=False))


def _workflow_trim_output(existing: str, extra: str) -> str:
    combined = (existing or "") + (extra or "")
    if len(combined) <= WORKFLOW_OUTPUT_LIMIT:
        return combined
    return "[...trimmed to recent output...]\n" + combined[-WORKFLOW_OUTPUT_LIMIT:]


def _workflow_update(operation_id: str, **changes: Any) -> Dict[str, Any]:
    with _WORKFLOW_LOCK:
        entry = _WORKFLOW_OPERATIONS.get(operation_id)
        if not entry:
            raise KeyError(operation_id)
        entry.update(changes)
        return _workflow_operation_copy(entry)


def _workflow_append_step(operation_id: str, message: str) -> None:
    timestamped = f"[{time.strftime('%H:%M:%S')}] {message}"
    with _WORKFLOW_LOCK:
        entry = _WORKFLOW_OPERATIONS.get(operation_id)
        if not entry:
            return
        steps = list(entry.get("steps") or [])
        steps.append(timestamped)
        entry["steps"] = steps[-24:]
        entry["message"] = message


def _workflow_append_output(operation_id: str, text: str) -> None:
    if not text:
        return
    with _WORKFLOW_LOCK:
        entry = _WORKFLOW_OPERATIONS.get(operation_id)
        if not entry:
            return
        entry["outputTail"] = _workflow_trim_output(str(entry.get("outputTail") or ""), text)


def _workflow_finish(operation_id: str, status: str, message: str, exit_code: int | None = None) -> None:
    global _WORKFLOW_CURRENT_ID
    with _WORKFLOW_LOCK:
        entry = _WORKFLOW_OPERATIONS.get(operation_id)
        if not entry:
            return
        entry["status"] = status
        entry["message"] = message
        entry["finishedAt"] = _workflow_now_iso()
        entry["exitCode"] = exit_code
        if _WORKFLOW_CURRENT_ID == operation_id:
            _WORKFLOW_CURRENT_ID = None


def _workflow_runtime_pid_map() -> Dict[int, int]:
    result: Dict[int, int] = {}
    try:
        completed = subprocess.run(
            ["cmd.exe", "/c", "netstat", "-ano", "-p", "tcp"],
            cwd=str(REPO_ROOT),
            capture_output=True,
            text=True,
            timeout=10,
            check=False,
        )
    except Exception:
        return result
    for raw_line in (completed.stdout or "").splitlines():
        line = raw_line.strip()
        if not line.lower().startswith("tcp"):
            continue
        parts = re.split(r"\s+", line)
        if len(parts) < 5:
            continue
        local_address = parts[1]
        state = parts[3].upper()
        pid_text = parts[4]
        if state != "LISTENING":
            continue
        match = re.search(r":(\d+)$", local_address)
        if not match:
            continue
        port = int(match.group(1))
        if port not in {2106, 7777}:
            continue
        try:
            result[port] = int(pid_text)
        except ValueError:
            continue
    return result


def _workflow_port_open(port: int) -> bool:
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=0.35):
            return True
    except OSError:
        return False


def _workflow_wait_for_port(port: int, should_be_open: bool, timeout_seconds: float) -> bool:
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        if _workflow_port_open(port) == should_be_open:
            return True
        time.sleep(0.4)
    return _workflow_port_open(port) == should_be_open


def _workflow_runtime_snapshot() -> Dict[str, Any]:
    pid_map = _workflow_runtime_pid_map()
    return {
        "repoRoot": str(REPO_ROOT),
        "serverRoot": str(SERVER_ROOT),
        "sidecarReachable": True,
        "ollamaReachable": _ollama_reachable(),
        "loginServerListening": _workflow_port_open(2106),
        "gameServerListening": _workflow_port_open(7777),
        "loginServerPid": pid_map.get(2106),
        "gameServerPid": pid_map.get(7777),
    }


def _workflow_status_payload() -> Dict[str, Any]:
    with _WORKFLOW_LOCK:
        current = _WORKFLOW_OPERATIONS.get(_WORKFLOW_CURRENT_ID or "") if _WORKFLOW_CURRENT_ID else None
        history = [_workflow_operation_copy(_WORKFLOW_OPERATIONS.get(operation_id)) for operation_id in _WORKFLOW_HISTORY]
    return {
        "status": "ok",
        "availableActions": [{"id": action, "label": WORKFLOW_ACTION_LABELS[action]} for action in WORKFLOW_ACTION_ORDER],
        "workflowBusy": bool(current and current.get("status") in {"queued", "running"}),
        "currentOperation": _workflow_operation_copy(current),
        "recentOperations": [entry for entry in history if entry],
        "runtime": _workflow_runtime_snapshot(),
    }


def _workflow_kill_wrapper_processes() -> List[int]:
    try:
        completed = subprocess.run(
            [
                "powershell",
                "-NoProfile",
                "-Command",
                (
                    "Get-CimInstance Win32_Process | "
                    "Where-Object { ($_.Name -ieq 'wscript.exe' -or $_.Name -ieq 'cscript.exe') "
                    "-and ($_.CommandLine -match 'LoginServer\\.vbs' -or $_.CommandLine -match 'GameServer\\.vbs') } | "
                    "Select-Object -ExpandProperty ProcessId"
                ),
            ],
            cwd=str(REPO_ROOT),
            capture_output=True,
            text=True,
            timeout=15,
            check=False,
        )
    except Exception:
        return []
    pids: List[int] = []
    for raw in (completed.stdout or "").splitlines():
        value = raw.strip()
        if not value:
            continue
        try:
            pids.append(int(value))
        except ValueError:
            continue
    for pid in pids:
        subprocess.run(
            ["taskkill", "/PID", str(pid), "/T", "/F"],
            cwd=str(REPO_ROOT),
            capture_output=True,
            text=True,
            timeout=15,
            check=False,
        )
    return pids


def _workflow_kill_pid(pid: int) -> None:
    subprocess.run(
        ["taskkill", "/PID", str(pid), "/T", "/F"],
        cwd=str(REPO_ROOT),
        capture_output=True,
        text=True,
        timeout=15,
        check=False,
    )


def _workflow_stop_runtime(operation_id: str) -> None:
    _workflow_append_step(operation_id, "Stopping LoginServer/GameServer.")
    wrapper_pids = _workflow_kill_wrapper_processes()
    if wrapper_pids:
        _workflow_append_step(operation_id, f"Stopped wrapper processes: {', '.join(str(pid) for pid in wrapper_pids)}")
    pid_map = _workflow_runtime_pid_map()
    for port, label in ((7777, "GameServer"), (2106, "LoginServer")):
        pid = pid_map.get(port)
        if not pid:
            continue
        _workflow_append_step(operation_id, f"Stopping {label} pid={pid}.")
        _workflow_kill_pid(pid)
    if not _workflow_wait_for_port(7777, False, 20.0):
        raise RuntimeError("GameServer port 7777 did not close in time.")
    if not _workflow_wait_for_port(2106, False, 20.0):
        raise RuntimeError("LoginServer port 2106 did not close in time.")
    _workflow_append_step(operation_id, "Runtime is stopped.")


def _workflow_resolve_java() -> str:
    java_home = (os.getenv("JAVA_HOME") or "").strip()
    if java_home:
        base = Path(java_home)
        candidate = (base / "java.exe") if base.name.lower() == "bin" else (base / "bin" / "java.exe")
        if candidate.exists():
            return str(candidate)
    completed = subprocess.run(
        ["cmd.exe", "/c", "where", "java.exe"],
        cwd=str(REPO_ROOT),
        capture_output=True,
        text=True,
        timeout=10,
        check=False,
    )
    fallback = ""
    for raw in (completed.stdout or "").splitlines():
        candidate = raw.strip()
        if not candidate:
            continue
        if not fallback:
            fallback = candidate
        if "\\common files\\oracle\\java\\javapath\\" not in candidate.lower():
            return candidate
    if fallback:
        return fallback
    raise RuntimeError("Could not resolve java.exe from JAVA_HOME or PATH.")


def _workflow_java_cfg_args(path: Path) -> List[str]:
    if not path.exists():
        raise RuntimeError(f"Missing java.cfg: {path}")
    raw_lines = path.read_text(encoding="utf-8-sig").splitlines()
    line = next((item.strip() for item in raw_lines if item.strip()), "")
    return shlex.split(line, posix=False) if line else []


def _workflow_launch_server(operation_id: str, label: str, port: int, workdir: Path, jar_name: str, cfg_path: Path) -> None:
    if _workflow_port_open(port):
        _workflow_append_step(operation_id, f"{label} already listening on {port}.")
        return
    command = [_workflow_resolve_java()] + _workflow_java_cfg_args(cfg_path) + ["-jar", f"..\\libs\\{jar_name}"]
    subprocess.Popen(
        command,
        cwd=str(workdir),
        stdin=subprocess.DEVNULL,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        creationflags=DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP | CREATE_NO_WINDOW,
        close_fds=False,
    )
    _workflow_append_step(operation_id, f"Started {label} launch command.")
    if not _workflow_wait_for_port(port, True, 40.0):
        raise RuntimeError(f"{label} did not open port {port} in time.")
    _workflow_append_step(operation_id, f"{label} is listening on {port}.")


def _workflow_run_ant(operation_id: str, target: str) -> None:
    _workflow_append_step(operation_id, f"Running ant {target}.")
    completed = subprocess.run(
        ["cmd.exe", "/c", "ant", target],
        cwd=str(REPO_ROOT),
        capture_output=True,
        text=True,
        timeout=1800,
        check=False,
    )
    output = (completed.stdout or "") + ("\n" + completed.stderr if completed.stderr else "")
    _workflow_append_output(operation_id, output)
    if completed.returncode != 0:
        raise RuntimeError(f"ant {target} failed with exit code {completed.returncode}.")
    _workflow_append_step(operation_id, f"Completed ant {target}.")


def _workflow_execute(operation_id: str, action: str) -> None:
    if action == "compile_source":
        _workflow_run_ant(operation_id, "compile")
        return
    if action == "deploy_runtime":
        runtime = _workflow_runtime_snapshot()
        if runtime.get("loginServerListening") or runtime.get("gameServerListening"):
            raise RuntimeError("Runtime is active. Stop runtime first or use Deploy + Restart Runtime.")
        _workflow_run_ant(operation_id, "deploy-server")
        return
    if action == "stop_runtime":
        _workflow_stop_runtime(operation_id)
        return
    if action == "start_runtime":
        _workflow_append_step(operation_id, "Starting LoginServer then GameServer.")
        _workflow_launch_server(operation_id, "LoginServer", 2106, SERVER_LOGIN_ROOT, "LoginServer.jar", LOGIN_SERVER_JAVA_CFG)
        _workflow_launch_server(operation_id, "GameServer", 7777, SERVER_GAME_ROOT, "GameServer.jar", GAME_SERVER_JAVA_CFG)
        return
    if action == "restart_runtime":
        _workflow_stop_runtime(operation_id)
        _workflow_execute(operation_id, "start_runtime")
        return
    if action == "deploy_and_restart_runtime":
        _workflow_stop_runtime(operation_id)
        _workflow_run_ant(operation_id, "deploy-server")
        _workflow_execute(operation_id, "start_runtime")
        return
    raise RuntimeError(f"Unsupported workflow action: {action}")


def _workflow_run_operation(operation_id: str) -> None:
    try:
        operation = _workflow_update(operation_id, status="running", startedAt=_workflow_now_iso(), message="Starting workflow operation.")
        _workflow_execute(operation_id, str(operation.get("action") or ""))
        _workflow_finish(operation_id, "completed", "Workflow operation completed successfully.", 0)
    except Exception as exc:
        _workflow_append_step(operation_id, f"Failed: {exc}")
        _workflow_finish(operation_id, "failed", str(exc), 1)


def _workflow_create_operation(action: str) -> Dict[str, Any]:
    global _WORKFLOW_CURRENT_ID
    normalized_action = (action or "").strip().lower()
    if normalized_action not in WORKFLOW_ACTION_LABELS:
        raise HTTPException(status_code=400, detail="Unsupported workflow action.")
    with _WORKFLOW_LOCK:
        current = _WORKFLOW_OPERATIONS.get(_WORKFLOW_CURRENT_ID or "") if _WORKFLOW_CURRENT_ID else None
        if current and current.get("status") in {"queued", "running"}:
            return {
                "status": "busy",
                "message": "Another workflow operation is already running.",
                "currentOperation": _workflow_operation_copy(current),
            }
        operation_id = f"workflow_{int(time.time() * 1000)}_{uuid.uuid4().hex[:6]}"
        entry = {
            "id": operation_id,
            "action": normalized_action,
            "label": WORKFLOW_ACTION_LABELS[normalized_action],
            "status": "queued",
            "createdAt": _workflow_now_iso(),
            "startedAt": None,
            "finishedAt": None,
            "message": "Queued.",
            "steps": [],
            "outputTail": "",
            "exitCode": None,
        }
        _WORKFLOW_OPERATIONS[operation_id] = entry
        _WORKFLOW_HISTORY.insert(0, operation_id)
        if len(_WORKFLOW_HISTORY) > WORKFLOW_HISTORY_LIMIT:
            for stale_id in _WORKFLOW_HISTORY[WORKFLOW_HISTORY_LIMIT:]:
                _WORKFLOW_OPERATIONS.pop(stale_id, None)
            del _WORKFLOW_HISTORY[WORKFLOW_HISTORY_LIMIT:]
        _WORKFLOW_CURRENT_ID = operation_id
    worker = threading.Thread(target=_workflow_run_operation, args=(operation_id,), daemon=True)
    worker.start()
    return {"status": "queued", "operation": _workflow_operation_copy(entry)}


def _queue_studio_command(
    action: str,
    fpc_id: str,
    hold_ms: int | None = None,
    player_name: str | None = None,
    channel: str | None = None,
    text: str | None = None,
) -> Dict[str, Any]:
    normalized_action = (action or "").strip().lower()
    normalized_fpc_id = (fpc_id or "").strip().lower()
    if not normalized_action or not normalized_fpc_id:
        raise HTTPException(status_code=400, detail="Missing action or fpcId.")
    command_id = f"{int(time.time() * 1000)}_{uuid.uuid4().hex[:8]}"
    lines = [
        f"action={normalized_action}",
        f"fpcId={normalized_fpc_id}",
        f"requestedAtMs={int(time.time() * 1000)}",
    ]
    if hold_ms is not None:
        lines.append(f"holdMs={int(hold_ms)}")
    if player_name:
        lines.append(f"playerName={(player_name or '').strip()}")
    if channel:
        lines.append(f"channel={(channel or '').strip().lower()}")
    if text:
        sanitized_text = " ".join((text or "").replace("\r", " ").replace("\n", " ").split())
        lines.append(f"text={sanitized_text}")
    _write_studio_text(_studio_file("commands", command_id + ".cmd"), "\n".join(lines) + "\n")
    return {
        "commandId": command_id,
        "status": "queued",
        "action": normalized_action,
        "fpcId": normalized_fpc_id,
    }


def _studio_snapshot_component(value: str | None) -> str:
    normalized = (value or "").strip().lower()
    if not normalized:
        return "unknown"
    return re.sub(r"[^a-z0-9_-]", "_", normalized)


def _load_studio_ui_text(file_name: str) -> str:
    path = STUDIO_UI_ROOT / file_name
    if not path.exists():
        raise HTTPException(status_code=500, detail=f"Studio UI file not found: {file_name}")
    try:
        return path.read_text(encoding="utf-8-sig")
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Could not read studio UI file {file_name}: {exc}") from exc


def _studio_html() -> str:
    return _load_studio_ui_text("index.html")


class PersonalityContext(BaseModel):
    fakePlayerId: str
    archetype: str
    personaTemplate: str | None = None
    speciesTag: str | None = None
    familyTag: str | None = None
    responseStyle: str | None = None
    playerStance: str | None = None
    coreNeed: str | None = None
    personaSummary: str | None = None
    selfKnowledgeSummary: str | None = None
    publicMaskSummary: str | None = None
    hiddenTruthSummary: str | None = None
    selfConcept: str | None = None
    coreWound: str | None = None
    coreDesire: str | None = None
    loyaltyAnchor: str | None = None
    resentmentAnchor: str | None = None
    privateTaboo: str | None = None
    speechAnchor: str | None = None
    privateContradiction: str | None = None
    guidancePosture: str | None = None
    guidancePriority: str | None = None
    uncertaintyStyle: str | None = None
    recommendationStyle: str | None = None
    riskStyle: str | None = None
    teachingStyle: str | None = None
    defaultInnerState: str | None = None
    longTermGoal: str | None = None
    socialTestStyle: str | None = None
    trustCriteria: str | None = None
    repairStyle: str | None = None
    revealBoundary: str | None = None
    reflectionLens: str | None = None
    activeObjective: str | None = None
    openLoops: str | None = None
    revealPressure: str | None = None
    relationshipPressure: str | None = None
    nextBeatHint: str | None = None
    stateModes: str | None = None
    reflectionPolicy: str | None = None
    memoryRetrievalPolicy: str | None = None
    goalPersistencePolicy: str | None = None
    emotionTransitionRules: str | None = None
    callbackStyle: str | None = None
    conflictStyle: str | None = None
    repairCadence: str | None = None
    focusEntityName: str | None = None
    focusEntityType: str | None = None
    focusIntent: str | None = None
    focusSummary: str | None = None
    relationshipSummary: str | None = None
    socialLabel: str | None = None
    socialSummary: str | None = None
    socialTrustBias: int | None = None
    socialGuardBias: int | None = None
    socialActionStance: str | None = None
    sceneSummary: str | None = None
    currentRelationshipGoal: str | None = None
    currentRelationshipNeed: str | None = None
    lastRelationshipTopic: str | None = None
    storySummary: str | None = None
    matchedRelationshipSummary: str | None = None
    recentConversationSummary: str | None = None
    retrievedMemorySummary: str | None = None
    memoryPrioritySummary: str | None = None
    memoryReflectionSummary: str | None = None
    memoryTrustSummary: str | None = None
    memoryRepairSummary: str | None = None
    memoryPressureSummary: str | None = None
    memorySelectionSummary: str | None = None
    behaviorFamilyId: str | None = None
    behaviorSummary: str | None = None
    behaviorRuleSummary: str | None = None
    stateProfileId: str | None = None
    stateSummary: str | None = None
    stateRuleSummary: str | None = None
    salientMemorySummary: str | None = None
    currentZone: str
    state: str
    hpBand: str
    mpBand: str
    nearbyPlayerCount: int = Field(ge=0, le=200)
    decisionScope: str = "planner"
    recentEvent: str
    chatCooldownReady: bool
    teleportCooldownReady: bool
    allowedIntents: List[str]
    allowedZones: List[str]
    availableLineStyleTags: List[str]
    availableTopicTags: List[str]
    messageCategory: str | None = None
    routeLane: str | None = None
    routeSpeechAct: str | None = None
    routeKnowledgeNeed: str | None = None
    routeSocialStake: str | None = None
    routeActionRequested: bool | None = None
    routePrimaryTopic: str | None = None
    routeSecondaryTopics: List[str] = Field(default_factory=list)
    routeConfidence: float | None = Field(default=None, ge=0.0, le=1.0)
    replyBankSummary: str | None = None
    replyBankLines: List[str] = Field(default_factory=list)
    knowledgeType: str | None = None
    knowledgeSummary: str | None = None
    knowledgeFacts: List[str] = Field(default_factory=list)
    knowledgeReplyLines: List[str] = Field(default_factory=list)
    knowledgeHeuristicLines: List[str] = Field(default_factory=list)
    knowledgeScope: str | None = None
    knowledgeConfidenceHint: str | None = None
    lastLineTag: str
    boredomScore: float = Field(ge=0.0, le=1.0)
    incomingPlayerName: str | None = None
    incomingPlayerMessage: str | None = None
    model: str | None = None
    temperature: float = Field(default=0.0, ge=0.0, le=1.0)


class StudioCommandRequest(BaseModel):
    action: str
    fpcId: str
    holdMs: int | None = Field(default=None, ge=1000, le=3600000)
    playerName: str | None = None
    channel: str | None = None
    text: str | None = None


class StudioBookmarkRequest(BaseModel):
    name: str
    fpcId: str
    fpcName: str | None = None
    playerName: str | None = None
    channel: str | None = None
    text: str | None = None
    traceFilters: Dict[str, Any] = Field(default_factory=dict)
    latestProbeResult: Dict[str, Any] | None = None
    latestSocialSnapshot: Dict[str, Any] | None = None
    latestExplainBaselineProbe: Dict[str, Any] | None = None
    latestExplainBaselineSocialSnapshot: Dict[str, Any] | None = None
    latestReplyTraceEntries: List[Any] = Field(default_factory=list)
    latestSuiteRun: Dict[str, Any] | None = None
    compareState: Dict[str, Any] = Field(default_factory=dict)
    suiteState: Dict[str, Any] = Field(default_factory=dict)


class StudioReportRequest(BaseModel):
    name: str
    fpcId: str
    fpcName: str | None = None
    suiteId: str
    suiteLabel: str | None = None
    description: str | None = None
    status: str | None = None
    latestSuiteRun: Dict[str, Any]
    latestProbeResult: Dict[str, Any] | None = None
    latestSocialSnapshot: Dict[str, Any] | None = None
    latestExplainBaselineProbe: Dict[str, Any] | None = None
    latestExplainBaselineSocialSnapshot: Dict[str, Any] | None = None


class StudioIncidentRequest(BaseModel):
    name: str
    fpcId: str
    fpcName: str | None = None
    reason: str
    status: str = "open"
    note: str | None = None
    triage: Dict[str, Any] | None = None
    latestProbeResult: Dict[str, Any] | None = None
    latestSocialSnapshot: Dict[str, Any] | None = None
    latestExplainBaselineProbe: Dict[str, Any] | None = None
    latestExplainBaselineSocialSnapshot: Dict[str, Any] | None = None
    latestSuiteRun: Dict[str, Any] | None = None


class StudioCreationProjectRequest(BaseModel):
    projectId: str | None = None
    name: str
    payload: Dict[str, Any] = Field(default_factory=dict)


class StudioCreationPublishRequest(BaseModel):
    publishId: str | None = None
    projectId: str | None = None
    name: str
    decision: str
    lifecycleState: str | None = None
    handoffState: str | None = None
    payload: Dict[str, Any] = Field(default_factory=dict)


class StudioPlatformSnapshotRequest(BaseModel):
    name: str
    manifest: Dict[str, Any]


class StudioPlatformWorkItemRequest(BaseModel):
    workItemId: str | None = None
    capabilityId: str
    capabilityLabel: str | None = None
    ownerBand: str | None = None
    status: str = "backlog"
    priority: str = "medium"
    owner: str | None = None
    hostFocusId: str | None = None
    dueOn: str | None = None
    linkedReportId: str | None = None
    linkedSnapshotId: str | None = None
    milestone: bool = False
    notes: str | None = None


class StudioFpcAuthorRequest(BaseModel):
    originalId: str | None = None
    saveMode: str = "replace"
    entry: Dict[str, Any]


class StudioFpcTemplateAuthorRequest(BaseModel):
    originalId: str | None = None
    saveMode: str = "replace"
    entry: Dict[str, Any]


class StudioPersonaAuthorRequest(BaseModel):
    originalId: str | None = None
    saveMode: str = "replace"
    entry: Dict[str, Any]


class StudioReplyBankAuthorRequest(BaseModel):
    originalIndex: int | None = None
    saveMode: str = "replace"
    entry: Dict[str, Any] = Field(default_factory=dict)


class StudioKnowledgeCardAuthorRequest(BaseModel):
    originalIndex: int | None = None
    saveMode: str = "replace"
    entry: Dict[str, Any] = Field(default_factory=dict)


class StudioRoutingRuleAuthorRequest(BaseModel):
    originalIndex: int | None = None
    saveMode: str = "replace"
    entry: Dict[str, Any] = Field(default_factory=dict)


class StudioReplySettingsAuthorRequest(BaseModel):
    entry: Dict[str, Any] = Field(default_factory=dict)


class StudioWorkflowRequest(BaseModel):
    action: str


class StudioAssistantAskRequest(BaseModel):
    question: str
    mode: str = "fast"
    maxCitations: int = Field(default=4, ge=1, le=8)
    history: List[Dict[str, str]] = Field(default_factory=list)


class PersonalityDecision(BaseModel):
    moodTag: str
    intentPreference: str
    zoneBias: str
    aggressionRiskScore: float = Field(ge=0.0, le=1.0)
    speakNow: bool
    lineStyleTag: str
    topicTag: str
    confidence: float = Field(ge=0.0, le=1.0)
    directReplyLine: str | None = None
    socialActionTag: str = "none"
    socialTargetTag: str = "none"
    socialSignalConfidence: float = Field(default=0.0, ge=0.0, le=1.0)
    socialSignalIntensity: int = Field(default=0, ge=0, le=3)


class RetryableOllamaError(Exception):
    def __init__(self, reason: str, raw_excerpt: str = ""):
        super().__init__(reason)
        self.reason = reason
        self.raw_excerpt = raw_excerpt[:220]


def _model_dump(model: BaseModel) -> Dict[str, Any]:
    if hasattr(model, "model_dump"):
        return model.model_dump()
    return model.dict()


def _clamp(value: float) -> float:
    if value < 0.0:
        return 0.0
    if value > 1.0:
        return 1.0
    return value


def _choose_allowed(preferred: str, allowed: List[str], fallback: str = "") -> str:
    if preferred in allowed:
        return preferred
    if fallback in allowed:
        return fallback
    return allowed[0] if allowed else fallback


def _choose_non_none_allowed(preferred: str, allowed: List[str], fallback: str) -> str:
    if preferred in allowed and preferred != "none":
        return preferred
    if fallback in allowed and fallback != "none":
        return fallback
    for value in allowed:
        if value != "none":
            return value
    return "none" if "none" in allowed else fallback


def _normalize_social_action_tag(value: str | None) -> str:
    tag = (value or "none").strip().lower()
    allowed = {
        "none",
        "threat",
        "insult",
        "distrust",
        "resentment",
        "belief_conflict",
        "apology",
        "gratitude",
        "affection",
        "respect",
        "praise",
        "reassurance",
        "abandonment",
        "repair",
    }
    return tag if tag in allowed else "none"


def _normalize_social_target_tag(value: str | None) -> str:
    tag = (value or "none").strip().lower()
    allowed = {"none", "self", "bonded_subject", "other_subject", "player_group"}
    return tag if tag in allowed else "none"


def _clamp_social_intensity(value: Any) -> int:
    try:
        parsed = int(value)
    except Exception:
        return 0
    return max(0, min(3, parsed))


def _anchored_social_signal(context: PersonalityContext) -> tuple[str, str, float, int] | None:
    category = _effective_message_category(context)
    mapping: dict[str, tuple[str, str, float, int]] = {
        "threat": ("threat", "self", 0.97, 3),
        "insult": ("insult", "self", 0.98, 3),
        "distrust": ("distrust", "self", 0.90, 2),
        "resentment": ("resentment", "self", 0.90, 2),
        "belief_conflict": ("belief_conflict", "bonded_subject", 0.96, 3),
        "apology": ("apology", "self", 0.92, 2),
        "thanks": ("gratitude", "self", 0.88, 1),
        "repair": ("repair", "self", 0.90, 2),
        "affection": ("affection", "self", 0.88, 2),
        "respect": ("respect", "self", 0.88, 2),
        "praise": ("praise", "self", 0.87, 1),
        "reassurance": ("reassurance", "self", 0.89, 2),
        "abandonment": ("abandonment", "self", 0.89, 2),
    }
    return mapping.get(category)


def _normalized_text(value: str | None) -> str:
    return re.sub(r"[^a-z0-9']+", " ", (value or "").lower()).strip()


def _relationship_bias_from_text(value: str | None) -> str | None:
    text = _normalized_text(value)
    if not text:
        return None

    hostile_tokens = (
        "hostile",
        "angry",
        "furious",
        "resentful",
        "dismissive",
        "contempt",
        "bitter",
        "spiteful",
        "sour",
        "harsh",
        "aggressive",
        "threatening",
        "enemy",
        "unfriendly",
        "cold",
        "reject",
        "push away",
        "keep away",
    )
    guarded_tokens = (
        "guarded",
        "wary",
        "reserved",
        "cautious",
        "careful",
        "distant",
        "cool",
        "skeptical",
        "watchful",
        "measured",
        "tense",
        "hesitant",
        "avoid",
        "keep distance",
        "maintain distance",
    )
    warm_tokens = (
        "warm",
        "welcoming",
        "friendly",
        "trusting",
        "trusted",
        "supportive",
        "affectionate",
        "fond",
        "close",
        "dear",
        "kind",
        "gentle",
        "loyal",
        "devoted",
        "comfort",
        "protective",
    )
    open_tokens = (
        "open",
        "open heart",
        "openhearted",
        "relaxed",
        "curious",
        "hopeful",
        "easygoing",
        "soft",
        "friendly",
        "welcoming",
        "supportive",
        "kind",
    )

    if any(token in text for token in hostile_tokens):
        return "hostile"
    if any(token in text for token in guarded_tokens):
        return "guarded"
    if any(token in text for token in warm_tokens):
        return "warm"
    if any(token in text for token in open_tokens):
        return "open"
    return None


def _relationship_bias_from_scores(context: PersonalityContext) -> str | None:
    if context.socialTrustBias is None and context.socialGuardBias is None:
        return None

    trust = int(context.socialTrustBias or 0)
    guard = int(context.socialGuardBias or 0)
    net = trust - guard

    if net >= 3:
        return "warm"
    if net > 0:
        return "open"
    if net <= -3:
        return "hostile"
    if net < 0:
        return "guarded"
    if trust > 0:
        return "open"
    if guard > 0:
        return "guarded"
    return None


def _has_hard_social_constraint(context: PersonalityContext) -> bool:
    stance = (context.socialActionStance or "").lower()
    bias = _relationship_bias(context)
    if bias == "hostile":
        return True
    return any(token in stance for token in ("refuse_party", "avoid_player"))


def _social_guidance(context: PersonalityContext) -> str:
    details: List[str] = []
    if context.socialLabel:
        details.append(f"Social label: {context.socialLabel}")
    if context.socialSummary:
        details.append(f"Social summary: {context.socialSummary}")
    if context.socialTrustBias is not None:
        details.append(f"Social trust bias: {context.socialTrustBias}")
    if context.socialGuardBias is not None:
        details.append(f"Social guard bias: {context.socialGuardBias}")
    if context.socialActionStance:
        details.append(f"Social action stance: {context.socialActionStance}")
    if context.sceneSummary:
        details.append(f"Scene summary: {context.sceneSummary}")
    if context.currentRelationshipGoal:
        details.append(f"Current relationship goal: {context.currentRelationshipGoal}")
    if context.currentRelationshipNeed:
        details.append(f"Current relationship need: {context.currentRelationshipNeed}")
    if context.lastRelationshipTopic:
        details.append(f"Last relationship topic: {context.lastRelationshipTopic}")
    return " ".join(details)


def _reply_settings() -> Dict[str, Any]:
    global _REPLY_SETTINGS_CACHE, _REPLY_SETTINGS_MTIME
    try:
        mtime = REPLY_SETTINGS_PATH.stat().st_mtime
        if _REPLY_SETTINGS_MTIME != mtime:
            loaded = json.loads(REPLY_SETTINGS_PATH.read_text(encoding="utf-8"))
            settings = dict(DEFAULT_REPLY_SETTINGS)
            if isinstance(loaded, dict):
                settings.update({key: value for key, value in loaded.items() if value is not None})
            _REPLY_SETTINGS_CACHE = settings
            _REPLY_SETTINGS_MTIME = mtime
    except Exception as exc:
        if _REPLY_SETTINGS_MTIME is None:
            LOGGER.warning("event=reply_settings_fallback path=%s reason=%s", REPLY_SETTINGS_PATH, exc)
            _REPLY_SETTINGS_MTIME = -1.0
    return _REPLY_SETTINGS_CACHE


def _reply_setting_str(key: str, default: str) -> str:
    return str(_reply_settings().get(key, default)).strip().lower()


def _reply_setting_int(key: str, default: int) -> int:
    try:
        return max(int(_reply_settings().get(key, default)), 1)
    except Exception:
        return default


def _reply_setting_bool(key: str, default: bool) -> bool:
    value = _reply_settings().get(key, default)
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() == "true"


def _reply_event_family(recent_event: str | None) -> str:
    value = str(recent_event or "").strip().lower()
    if value in {"player_whisper", "player_whisper_probe"}:
        return "whisper"
    if value in {"player_general", "player_general_probe"}:
        return "general"
    if value in {"player_shout", "player_shout_probe"}:
        return "shout"
    if value in {"player_world", "player_world_probe"}:
        return "world"
    return ""


def _reply_limits(context: PersonalityContext) -> tuple[int, int, str]:
    reply_event = _reply_event_family(context.recentEvent)
    if reply_event == "whisper":
        max_words = _reply_setting_int("whisperMaxWords", 20)
        max_chars = 110
        sentence_shape = "one or two short sentences" if _reply_setting_bool("whisperAllowSecondSentence", True) else "one short sentence"
        return max_words, max_chars, sentence_shape
    if reply_event == "general":
        return _reply_setting_int("generalMaxWords", 7), 70, "one short sentence"
    return _reply_setting_int("publicMaxWords", 7), 70, "one short sentence"


def _reply_budget_mode() -> str:
    value = _reply_setting_str("runtimeBudgetMode", "adaptive")
    return value if value in {"off", "adaptive", "strict"} else "adaptive"


def _reply_budget_limits(reply_event: str) -> tuple[int, int]:
    if reply_event == "general":
        return (
            _reply_setting_int("generalBudgetWindowSeconds", 20),
            _reply_setting_int("generalBudgetMaxReplies", 3),
        )
    if reply_event == "shout":
        return (
            _reply_setting_int("shoutBudgetWindowSeconds", 30),
            _reply_setting_int("shoutBudgetMaxReplies", 2),
        )
    if reply_event == "world":
        return (
            _reply_setting_int("worldBudgetWindowSeconds", 45),
            _reply_setting_int("worldBudgetMaxReplies", 1),
        )
    return (0, 0)


def _reply_budget_window_key(context: PersonalityContext, reply_event: str) -> str:
    return f"{_studio_snapshot_component(context.fakePlayerId)}::{reply_event}"


def _reply_budget_prompt_key(context: PersonalityContext, reply_event: str) -> str:
    if reply_event not in {"general", "shout", "world"}:
        return ""
    message = _normalized_text(context.incomingPlayerMessage)
    if not message:
        return ""
    route_lane = (context.routeLane or "").strip().lower()
    route_speech = (context.routeSpeechAct or "").strip().lower()
    route_topic = (context.routePrimaryTopic or "").strip().lower()
    category = _effective_message_category(context)
    return "||".join(
        [
            _studio_snapshot_component(context.fakePlayerId),
            reply_event,
            route_lane or "unknown_lane",
            route_speech or "unknown_speech",
            route_topic or "unknown_topic",
            category or "unknown_category",
            message,
        ]
    )


def _trim_runtime_budget_windows(now_seconds: float) -> None:
    keep_window_seconds = max(
        _reply_setting_int("generalBudgetWindowSeconds", 20),
        _reply_setting_int("shoutBudgetWindowSeconds", 30),
        _reply_setting_int("worldBudgetWindowSeconds", 45),
        _reply_setting_int("repeatPromptWindowSeconds", 45),
    )
    expired_window_keys = [
        key
        for key, timestamps in _RUNTIME_REPLY_BUDGET_WINDOWS.items()
        if not [ts for ts in timestamps if now_seconds - ts <= keep_window_seconds]
    ]
    for key in expired_window_keys:
        _RUNTIME_REPLY_BUDGET_WINDOWS.pop(key, None)
    repeat_window_seconds = _reply_setting_int("repeatPromptWindowSeconds", 45)
    expired_cache_keys = [
        key
        for key, payload in _RUNTIME_REPLY_CACHE.items()
        if now_seconds - float(payload.get("lastUsedAt") or payload.get("createdAt") or 0.0) > repeat_window_seconds
    ]
    for key in expired_cache_keys:
        _RUNTIME_REPLY_CACHE.pop(key, None)


def _budget_silence_decision(context: PersonalityContext, reason: str) -> PersonalityDecision:
    decision = _default_decision(context)
    decision.speakNow = False
    decision.directReplyLine = None
    LOGGER.info(
        "event=reply_budget_suppressed fakePlayerId=%s scope=%s recentEvent=%s reason=%s",
        context.fakePlayerId,
        context.decisionScope,
        context.recentEvent,
        reason,
    )
    return decision


def _preflight_runtime_reply_budget(context: PersonalityContext) -> tuple[PersonalityDecision | None, str | None]:
    if not _is_reply_scope(context):
        return None, None
    reply_event = _reply_event_family(context.recentEvent)
    if reply_event not in {"general", "shout", "world"}:
        return None, None
    if _reply_budget_mode() == "off":
        return None, None
    message = (context.incomingPlayerMessage or "").strip()
    if not message:
        return None, None
    now_seconds = time.time()
    repeat_window_seconds = _reply_setting_int("repeatPromptWindowSeconds", 45)
    repeat_reuse_limit = _reply_setting_int("repeatPromptReuseLimit", 3)
    prompt_key = _reply_budget_prompt_key(context, reply_event)
    window_key = _reply_budget_window_key(context, reply_event)
    with _RUNTIME_REPLY_BUDGET_LOCK:
        _trim_runtime_budget_windows(now_seconds)
        if prompt_key and repeat_window_seconds > 0 and repeat_reuse_limit > 0:
            cached = _RUNTIME_REPLY_CACHE.get(prompt_key)
            if isinstance(cached, dict):
                age_seconds = now_seconds - float(cached.get("createdAt") or 0.0)
                reuse_count = int(cached.get("reuseCount") or 0)
                payload = cached.get("decision")
                if age_seconds <= repeat_window_seconds and reuse_count < repeat_reuse_limit and isinstance(payload, dict):
                    try:
                        cached_decision = PersonalityDecision(**deepcopy(payload))
                    except Exception:
                        cached_decision = None
                    if cached_decision and cached_decision.speakNow and str(cached_decision.directReplyLine or "").strip():
                        cached["reuseCount"] = reuse_count + 1
                        cached["lastUsedAt"] = now_seconds
                        LOGGER.info(
                            "event=reply_budget_cache_hit fakePlayerId=%s recentEvent=%s reuseCount=%s windowSeconds=%s",
                            context.fakePlayerId,
                            context.recentEvent,
                            cached["reuseCount"],
                            repeat_window_seconds,
                        )
                        return cached_decision, "cache_hit"
        window_seconds, max_replies = _reply_budget_limits(reply_event)
        if window_seconds <= 0 or max_replies <= 0:
            return None, None
        timestamps = [
            ts for ts in _RUNTIME_REPLY_BUDGET_WINDOWS.get(window_key, [])
            if now_seconds - ts <= window_seconds
        ]
        _RUNTIME_REPLY_BUDGET_WINDOWS[window_key] = timestamps
        if len(timestamps) >= max_replies:
            return _budget_silence_decision(context, f"{reply_event}_quota"), "suppressed"
    return None, None


def _commit_runtime_reply_budget(context: PersonalityContext, decision: PersonalityDecision, source: str = "live") -> PersonalityDecision:
    if not _is_reply_scope(context):
        return decision
    reply_event = _reply_event_family(context.recentEvent)
    if reply_event not in {"general", "shout", "world"}:
        return decision
    if not decision.speakNow or not str(decision.directReplyLine or "").strip():
        return decision
    now_seconds = time.time()
    window_seconds, _ = _reply_budget_limits(reply_event)
    repeat_window_seconds = _reply_setting_int("repeatPromptWindowSeconds", 45)
    repeat_reuse_limit = _reply_setting_int("repeatPromptReuseLimit", 3)
    window_key = _reply_budget_window_key(context, reply_event)
    prompt_key = _reply_budget_prompt_key(context, reply_event)
    with _RUNTIME_REPLY_BUDGET_LOCK:
        _trim_runtime_budget_windows(now_seconds)
        timestamps = _RUNTIME_REPLY_BUDGET_WINDOWS.get(window_key, [])
        if window_seconds > 0:
            timestamps = [ts for ts in timestamps if now_seconds - ts <= window_seconds]
        timestamps.append(now_seconds)
        _RUNTIME_REPLY_BUDGET_WINDOWS[window_key] = timestamps
        if source == "live" and prompt_key and repeat_window_seconds > 0 and repeat_reuse_limit > 0:
            _RUNTIME_REPLY_CACHE[prompt_key] = {
                "createdAt": now_seconds,
                "lastUsedAt": now_seconds,
                "reuseCount": 0,
                "decision": _model_dump(decision),
            }
    return decision


def _runtime_reply_budget_snapshot() -> Dict[str, Any]:
    now_seconds = time.time()
    with _RUNTIME_REPLY_BUDGET_LOCK:
        _trim_runtime_budget_windows(now_seconds)
        return {
            "mode": _reply_budget_mode(),
            "activeWindows": len(_RUNTIME_REPLY_BUDGET_WINDOWS),
            "activeCachedPrompts": len(_RUNTIME_REPLY_CACHE),
            "general": {
                "windowSeconds": _reply_setting_int("generalBudgetWindowSeconds", 20),
                "maxReplies": _reply_setting_int("generalBudgetMaxReplies", 3),
            },
            "shout": {
                "windowSeconds": _reply_setting_int("shoutBudgetWindowSeconds", 30),
                "maxReplies": _reply_setting_int("shoutBudgetMaxReplies", 2),
            },
            "world": {
                "windowSeconds": _reply_setting_int("worldBudgetWindowSeconds", 45),
                "maxReplies": _reply_setting_int("worldBudgetMaxReplies", 1),
            },
            "repeatPrompt": {
                "windowSeconds": _reply_setting_int("repeatPromptWindowSeconds", 45),
                "reuseLimit": _reply_setting_int("repeatPromptReuseLimit", 3),
            },
        }


def _default_decision(context: PersonalityContext) -> PersonalityDecision:
    intent = _choose_allowed("idle", context.allowedIntents, "idle")
    if _is_planner_scope(context) and context.boredomScore >= 0.20 and "move" in context.allowedIntents:
        intent = "move"
    zone = context.currentZone if context.currentZone in context.allowedZones else (context.allowedZones[0] if context.allowedZones else "")
    line_style = _choose_allowed("none", context.availableLineStyleTags, "none")
    topic = _choose_allowed("none", context.availableTopicTags, "none")
    return PersonalityDecision(
        moodTag="calm",
        intentPreference=intent,
        zoneBias=zone,
        aggressionRiskScore=0.0,
        speakNow=False,
        lineStyleTag=line_style,
        topicTag=topic,
        confidence=0.0,
        directReplyLine=None,
    )


def _normalize_reply_text(reply: str | None, fake_player_id: str) -> str | None:
    if not isinstance(reply, str):
        return None

    result = reply.strip()
    if not result:
        return None
    if result.lower() in {"none", "null", "nil", "n/a", "na", "no reply"}:
        return None

    prefix = f"{fake_player_id}:"
    if result.lower().startswith(prefix.lower()):
        result = result[len(prefix):].strip()

    result = " ".join(result.split())
    result = _safe_reply_clip(result, 110)

    return result or None


def _canon_first_sentence(*values: str | None) -> str:
    for value in values:
        normalized = _normalize_reply_text(value, "")
        if not normalized:
            continue
        parts = re.split(r"(?<=[.!?])\s+", normalized)
        sentence = (parts[0] if parts else normalized).strip()
        sentence = _strip_trailing_separators(sentence)
        if sentence:
            if sentence[-1] not in ".!?":
                sentence = f"{sentence}."
            return sentence
    return ""


def _display_name_from_id(fake_player_id: str | None) -> str:
    value = (fake_player_id or "").strip().replace("_", " ")
    return value.title() if value else ""


def _first_personize_canon(text: str | None, fake_player_id: str | None) -> str:
    sentence = _canon_first_sentence(text)
    if not sentence:
        return ""

    display_name = _display_name_from_id(fake_player_id)
    if display_name:
        sentence = re.sub(rf"^(Privately|Outwardly)\s+{re.escape(display_name)}\b", r"\1 I", sentence, flags=re.IGNORECASE)
        sentence = re.sub(rf"^{re.escape(display_name)}\b", "I", sentence, flags=re.IGNORECASE)
    sentence = re.sub(r"^(Privately|Outwardly)\s+(He|She|They)\b", r"\1 I", sentence, flags=re.IGNORECASE)
    sentence = re.sub(r"^(He|She|They)\b", "I", sentence, flags=re.IGNORECASE)

    match = re.match(r"^(?:(Privately|Outwardly)\s+)?I\s+([a-z']+)\b", sentence, flags=re.IGNORECASE)
    if match:
        prefix = match.group(1) or ""
        verb = match.group(2).lower()
        replacement = FIRST_PERSON_VERB_MAP.get(verb)
        if replacement:
            replacement_prefix = f"{prefix} " if prefix else ""
            sentence = re.sub(r"^(?:(Privately|Outwardly)\s+)?I\s+[a-z']+\b", f"{replacement_prefix}I {replacement}", sentence, count=1, flags=re.IGNORECASE)
    return sentence


def _private_canon_clause(text: str | None, fake_player_id: str | None) -> str:
    sentence = _first_personize_canon(text, fake_player_id)
    if not sentence:
        return ""
    sentence = re.sub(r"^(Privately|Outwardly)\s+", "", sentence, flags=re.IGNORECASE).strip()
    for third_person, first_person in FIRST_PERSON_VERB_MAP.items():
        sentence = re.sub(
            rf"\b(and|but|yet)\s+(still\s+)?{re.escape(third_person)}\b",
            lambda match: f"{match.group(1)} {(match.group(2) or '')}{first_person}",
            sentence,
            flags=re.IGNORECASE,
        )
    sentence = _strip_trailing_separators(sentence).rstrip(".!?").strip()
    return sentence


def _identity_phrase(text: str | None) -> str:
    concept = _canon_first_sentence(text).rstrip(".!?").strip()
    if not concept:
        return ""
    if re.match(r"^(a|an|the)\b", concept, flags=re.IGNORECASE):
        return concept[:1].lower() + concept[1:]
    return concept


def _compact_desire_clause(text: str | None) -> str:
    clause = _canon_first_sentence(text).rstrip(".!?").strip()
    if not clause:
        return ""
    clause = re.sub(r"\bto be loved as someone truly chosen\b", "to be truly chosen", clause, flags=re.IGNORECASE)
    clause = re.sub(r"\bto be loved as someone chosen\b", "to be chosen", clause, flags=re.IGNORECASE)
    clause = re.sub(r"\s+", " ", clause).strip(" ,")
    return clause


DIRECT_TRAIT_VERB_MAP = {
    **FIRST_PERSON_VERB_MAP,
    "softens": "soften",
    "names": "name",
    "gives": "give",
    "grins": "grin",
    "cuts": "cut",
    "states": "state",
    "moves": "move",
    "gets": "get",
    "takes": "take",
    "marks": "mark",
    "lowers": "lower",
    "reopens": "reopen",
    "shows": "show",
    "tests": "test",
    "sees": "see",
    "interprets": "interpret",
    "offers": "offer",
    "shares": "share",
    "reveals": "reveal",
    "opens": "open",
}


def _sentence_clause(text: str | None) -> str:
    return _canon_first_sentence(text).rstrip(".!?").strip()


def _lower_sentence_clause(text: str | None) -> str:
    clause = _sentence_clause(text)
    if not clause:
        return ""
    return clause[:1].lower() + clause[1:]


def _matching_sentence_clause(text: str | None, *keywords: str) -> str:
    normalized = _normalize_reply_text(text, "")
    if not normalized:
        return ""
    sentences = re.split(r"(?<=[.!?])\s+", normalized)
    lowered_keywords = tuple(keyword.lower() for keyword in keywords if keyword)
    for sentence in sentences:
        stripped = _strip_trailing_separators(sentence).rstrip(".!?").strip()
        if not stripped:
            continue
        lowered = stripped.lower()
        if any(keyword in lowered for keyword in lowered_keywords):
            return stripped
    return ""


def _first_person_trait_clause(text: str | None) -> str:
    clause = _sentence_clause(text)
    if not clause:
        return ""
    for third_person, first_person in DIRECT_TRAIT_VERB_MAP.items():
        clause = re.sub(
            rf"(^|,\s+|and\s+|but\s+|yet\s+|then\s+){re.escape(third_person)}\b",
            lambda match: f"{match.group(1)}{first_person}",
            clause,
            flags=re.IGNORECASE,
        )
    return clause[:1].lower() + clause[1:] if clause else ""


def _repair_first_person_reply_grammar(reply: str | None) -> str | None:
    text = " ".join(str(reply or "").split()).strip()
    if not text or re.search(r"\bI\b", text, flags=re.IGNORECASE) is None:
        return text or None
    text = re.sub(
        r"\bI\s+([a-z']+)\b",
        lambda match: f"I {DIRECT_TRAIT_VERB_MAP.get(match.group(1).lower(), match.group(1))}",
        text,
        flags=re.IGNORECASE,
    )
    text = re.sub(
        r"\b(and|but|yet|then)\s+([a-z']+)\b",
        lambda match: f"{match.group(1)} {DIRECT_TRAIT_VERB_MAP.get(match.group(2).lower(), match.group(2))}",
        text,
        flags=re.IGNORECASE,
    )
    return text


def _canon_tokens(*values: str | None) -> set[str]:
    tokens: set[str] = set()
    for value in values:
        normalized = re.sub(r"[^a-z0-9']+", " ", str(value or "").lower()).strip()
        if not normalized:
            continue
        for token in normalized.split():
            if (len(token) < 5) or token.isdigit() or (token in CANON_TOKEN_STOPWORDS):
                continue
            tokens.add(token)
    return tokens


def _safe_reply_clip(reply: str, max_chars: int) -> str | None:
    result = " ".join(reply.split()).strip()
    if not result:
        return None
    if len(result) > max_chars:
        cut = result.rfind(" ", 0, max_chars)
        if cut <= 0:
            cut = max_chars
        result = result[:cut].strip()
    result = _trim_dangling_reply(result)
    if not result:
        return None
    result = _strip_trailing_separators(result)
    if not result:
        return None
    if result[-1] not in ".!?":
        result = f"{result}."
    return result


def _trim_dangling_reply(reply: str) -> str:
    result = reply.strip()
    while result:
        words = re.findall(r"[a-z0-9']+", result.lower())
        if not words or words[-1] not in BAD_REPLY_ENDINGS:
            break
        cut = result.rfind(" ")
        if cut <= 0:
            return ""
        result = result[:cut].strip()
    return result


def _strip_trailing_separators(reply: str) -> str:
    result = reply.strip()
    while result and result[-1] in ",;:-/":
        result = result[:-1].strip()
    return result


def _looks_like_deferred_guidance(reply: str | None) -> bool:
    if not reply:
        return False
    normalized = re.sub(r"[^a-z0-9']+", " ", reply.lower()).strip()
    return (
        normalized.startswith("favor system first")
        or normalized.startswith("ask for level and goal before endorsing")
        or "afpcs should" in normalized
        or "later afpcs can" in normalized
        or "good baseline for" in normalized
        or normalized.startswith("guide beginners toward")
        or normalized.startswith("use tradeoff wording")
        or normalized.startswith("use for ")
        or normalized.startswith("scope ")
    )


def _looks_like_echo(reply: str | None, incoming: str | None) -> bool:
    if not reply or not incoming:
        return False

    def norm(value: str) -> str:
        return " ".join("".join(ch.lower() if ch.isalnum() or ch == " " else " " for ch in value).split())

    a = norm(reply)
    b = norm(incoming)
    if not a or not b:
        return False
    if a == b:
        return True
    return len(a) >= 12 and (a in b or b in a)


def _reply_topic_fallback(context: PersonalityContext) -> str:
    message = (context.incomingPlayerMessage or "").strip().lower()
    if not message:
        return _choose_allowed("smalltalk", context.availableTopicTags, "none")
    if any(token in message for token in ("where", "giran", "gate", "town", "zone", "north", "south", "east", "west")):
        return _choose_non_none_allowed("zone", context.availableTopicTags, "smalltalk")
    if any(token in message for token in ("farm", "exp", "level", "adena", "quest", "start", "help")):
        return _choose_non_none_allowed("zone", context.availableTopicTags, "smalltalk")
    return _choose_non_none_allowed("smalltalk", context.availableTopicTags, "zone")


def _message_category(message: str | None) -> str:
    text = (message or "").strip().lower()
    if not text:
        return "unknown"
    if _contains_phrase(
        text,
        "i will kill you",
        "ill kill you",
        "i'll kill you",
        "i will hurt you",
        "ill hurt you",
        "i'll hurt you",
        "i will ruin you",
        "ill ruin you",
        "i'll ruin you",
        "i will destroy you",
        "ill destroy you",
        "i'll destroy you",
        "i will hunt you",
        "ill hunt you",
        "i'll hunt you",
        "i am coming for you",
        "im coming for you",
        "i'm coming for you",
        "watch your back",
    ):
        return "threat"
    if _is_direct_flame(text):
        return "insult"
    routed_category = _routing_rule_override("message_category", text)
    if routed_category:
        return routed_category
    if _is_player_bond_opinion_question(text):
        return "bond_opinion"
    if _contains_phrase(
        text,
        "i dont trust you",
        "i don't trust you",
        "can't rely on you",
        "cant rely on you",
        "you hide too much",
        "you are hiding something",
        "you're hiding something",
        "youre hiding something",
        "i doubt you",
        "you feel wrong",
        "you are not honest",
        "you're not honest",
        "youre not honest",
        "i cant believe you",
        "i can't believe you",
    ):
        return "distrust"
    if _contains_phrase(text, "i hate you", "can't stand you", "cant stand you", "i resent you", "you disgust me", "you make me sick", "i reject you"):
        return "resentment"
    if _contains_phrase(
        text,
        "i am leaving you",
        "i'm leaving you",
        "im leaving you",
        "i am done with you",
        "i'm done with you",
        "im done with you",
        "i wont come back",
        "i won't come back",
        "i am not coming back",
        "i'm not coming back",
        "im not coming back",
        "you are on your own",
        "you're on your own",
        "youre on your own",
        "i leave you alone",
        "i am leaving for good",
        "i'm leaving for good",
        "im leaving for good",
    ):
        return "abandonment"
    if _is_self_identity_question(text):
        return "self_identity"
    if _is_self_story_question(text):
        return "self_story"
    if _is_creator_opinion_question(text):
        return "creator_opinion"
    if _is_community_opinion_question(text):
        return "smalltalk"
    if _is_ambient_respect_signal(text):
        return "respect"
    if _is_self_belief_question(text):
        return "self_belief"
    if _is_self_preference_question(text):
        return "self_preference"
    if _is_self_state_reflection_question(text):
        return "self_state_reflection"
    if _is_bond_opinion_question(text):
        return "bond_opinion"
    if _contains_phrase(text, "what happened", "what are you waiting for", "why are you waiting", "why do you wait"):
        return "quest_story"
    if _is_relationship_probe(text):
        return "relationship_probe"
    if _is_pvp_conflict_question(text):
        return "pvp_conflict"
    if _is_progression_strength_question(text):
        return "progression_strength"
    if _is_clan_invite_question(text):
        return "social_invite"
    if _is_party_farm_question(text):
        return "party_farm"
    if _is_companionship_question(text):
        return "companionship"
    if _is_farming_route_question(text):
        return "farm"
    if _contains_phrase(text, "help", "quest", "what should i do", "what do i do"):
        return "help"
    if _contains_phrase(text, "party", "group", "join me", "go together", "come with me", "with me", "wanna go together", "want to go together"):
        return "party"
    if _contains_phrase(text, "where", "lost", "gate", "town", "start", "which way", "how do i get", "how i get", "how i teleport", "teleport to"):
        return "direction"
    if _contains_phrase(text, "weather", "pleasant day", "beautiful day"):
        return "weather"
    if _contains_phrase(text, "farm", "exp", "level", "grind", "hunt", "spot", "aoe farm"):
        return "farm"
    if _contains_phrase(text, "how are", "you ok", "you okay", "how've you been", "howve you been", "how's it going", "hows it going", "how is it going"):
        return "status"
    if _contains_phrase(text, "thanks", "thank you", "ty", "thx"):
        return "thanks"
    if _contains_phrase(text, "sorry", "apolog", "forgive"):
        return "apology"
    if _contains_phrase(text, "start fresh", "start over", "make it right", "make things right", "fix this", "try again", "make peace", "new relationship", "be friends again", "can we fix this", "can we start over", "can we try again"):
        return "repair"
    if _contains_phrase(text, "i like you", "i care about you", "i love you", "glad you're here", "glad youre here", "happy you're here", "happy youre here", "i miss you", "enjoy your company", "you matter to me", "i want you here"):
        return "affection"
    if _contains_phrase(text, "i respect you", "i trust you", "i admire you", "i value you", "i believe in you", "you were right", "you deserve respect"):
        return "respect"
    if _contains_phrase(
        text,
        "you did well",
        "you did great",
        "you handled that well",
        "good work",
        "well done",
        "nice work",
        "i am proud of you",
        "i'm proud of you",
        "im proud of you",
        "you are clever",
        "you're clever",
        "youre clever",
        "you are strong",
        "you're strong",
        "youre strong",
        "you are capable",
        "you're capable",
        "youre capable",
        "you were brave",
    ):
        return "praise"
    if _contains_phrase(
        text,
        "i am with you",
        "i'm with you",
        "im with you",
        "you can trust me",
        "i wont leave",
        "i won't leave",
        "i am not leaving",
        "i'm not leaving",
        "im not leaving",
        "you are safe with me",
        "you're safe with me",
        "youre safe with me",
        "i will stay",
        "i'll stay",
        "ill stay",
        "i am on your side",
        "i'm on your side",
        "im on your side",
        "i will stand with you",
        "i'll stand with you",
        "ill stand with you",
        "i wont hurt you",
        "i won't hurt you",
    ):
        return "reassurance"
    if _contains_phrase(text, "remember", "missed", "miss you", "did you miss", "forgot"):
        return "memory"
    if _is_literal_name_question(text):
        return "name"
    if _contains_phrase(text, "hello", "hi", "hey", "yo", "good morning", "goodmorning", "good evening", "goodevening", "good night", "goodnight"):
        return "greeting"
    if _contains_phrase(
        text,
        "essence",
        "warg",
        "wolf form",
        "wp",
        "lineage",
        "adena",
        "enchant",
        "upgrade",
        "gear",
        "spellbooks",
        "book",
        "books",
        "class",
        "spellbook",
        "heroic",
        "legendary",
        "rare spellbook",
        "rare book",
        "giran seal",
        "seal",
        "aden essence",
        "cruma",
        "cruma tower",
        "ruins of agony",
        "ruins of despair",
        "abandoned camp",
        "wasteland",
        "southern wasteland",
        "soulshot",
        "spirit ore",
        "auto hunting",
        "auto-hunting",
        "teleport",
        "orven",
        "transcendent",
        "pet",
        "pets",
        "orc fortress",
        "iron heart",
        "ironheart",
        "wolf server",
        "wolf pet",
        "assassin",
        "skill",
        "skills",
        "item",
        "items",
        "weapon",
        "weapons",
        "armor",
        "armour",
        "resource",
        "resources",
        "material",
        "materials",
    ):
        return "lineage_lore"
    return "smalltalk"


def _knowledge_type(message: str | None) -> str:
    text = (message or "").strip().lower()
    if not text:
        return "unknown"
    routed_knowledge_type = _routing_rule_override("knowledge_type", text)
    if routed_knowledge_type:
        return routed_knowledge_type
    if _is_clan_invite_question(text):
        return "unknown"
    if _contains_phrase(text, "wolf server", "wolf pet", "difference between warg and wolf", "warg and wolf", "warg vs wolf"):
        return "server_rules"
    if _is_travel_destination_question(text):
        return "travel_destination"
    if _is_class_choice_question(text):
        return "class_choice"
    if _is_progression_strength_question(text):
        return "progression_strength"
    if _contains_phrase(text, "auto hunting", "auto-hunting", "teleport", "orven", "transcendent", "pet", "pets", "orc fortress", "essence"):
        return "system_basics"
    if _is_farming_route_question(text):
        return "farming"
    if _contains_phrase(text, "where should i start", "how should i start", "what should i do first", "beginner", "start route", "first step", "opening route", "where should i go", "where do i go", "where can i go", "where next"):
        return "progression_route"
    if _contains_phrase(text, "wolf form", "wp", "2nd class", "second class", "class change", "when does warg get wolf form"):
        return "class_progression"
    if _contains_phrase(text, "skill", "skills", "assassin", "what skills", "what does warg have", "what does assassin have", "kit"):
        return "class_skills"
    if _contains_phrase(text, "what is warg", "warg class", "male human", "fist weapon", "sigil", "what does warg feel like", "what is warg like"):
        return "class_identity"
    if _contains_phrase(text, "heroic", "legendary", "rare spellbook", "rare book", "spellbook", "spellbooks", "book", "books", "enchant", "upgrade", "gear", "item", "items", "weapon", "weapons", "armor", "armour", "resource", "resources", "material", "materials"):
        return "itemization"
    if _contains_phrase(text, "giran seal", "giran seals", "adena essence", "l coin", "sp"):
        return "currency"
    if _is_generic_support_farm_question(text):
        return "unknown"
    if _contains_phrase(text, "farm", "grind", "hunt", "spot", "99", "lvl 99", "level 99", "cruma 2", "cruma2", "magical tablet", "magical tablets"):
        return "farming"
    return "unknown"


def _normalize_phrase_text(text: str | None) -> str:
    return re.sub(r"[^a-z0-9']+", " ", (text or "").lower()).strip()


def _contains_phrase(text: str, *phrases: str) -> bool:
    normalized = _normalize_phrase_text(text)
    if not normalized:
        return False
    for phrase in phrases:
        target = _normalize_phrase_text(phrase)
        if not target:
            continue
        if re.search(rf"\b{re.escape(target)}\b", normalized):
            return True
    return False


def _current_utterance_text(text: str | None) -> str:
    normalized = _normalize_phrase_text(text)
    if not normalized:
        return ""
    cut = len(normalized)
    for marker in (
        " previous category ",
        " previous knowledge topic ",
        " previous focus ",
        " previous player topic ",
        " previous reply ",
        " longer memory ",
    ):
        marker_index = normalized.find(marker)
        if marker_index >= 0 and marker_index < cut:
            cut = marker_index
    return normalized[:cut].strip()


def _routing_rules() -> List[Dict[str, Any]]:
    global _ROUTING_RULES_MTIME
    try:
        mtime = ROUTING_RULES_PATH.stat().st_mtime
        if _ROUTING_RULES_MTIME != mtime:
            loaded = _load_json_list_file(ROUTING_RULES_PATH, "Routing rules")
            _set_routing_rules_cache(loaded)
        return list(_ROUTING_RULES_CACHE)
    except FileNotFoundError:
        _ROUTING_RULES_MTIME = None
        return []
    except Exception as exc:
        LOGGER.warning("event=routing_rules_fallback path=%s reason=%s", ROUTING_RULES_PATH, exc)
        return list(_ROUTING_RULES_CACHE)


def _routing_rule_matches(rule: Dict[str, Any], full_text: str, current_utterance: str) -> bool:
    scope = str(rule.get("scope") or "current_utterance").strip().lower()
    target_text = full_text if scope == "full_text" else current_utterance
    if not target_text:
        return False
    any_phrases = rule.get("anyPhrases") if isinstance(rule.get("anyPhrases"), list) else []
    all_phrases = rule.get("allPhrases") if isinstance(rule.get("allPhrases"), list) else []
    exclude_phrases = rule.get("excludePhrases") if isinstance(rule.get("excludePhrases"), list) else []
    if not any_phrases and not all_phrases:
        return False
    if any(_contains_phrase(target_text, str(phrase)) for phrase in exclude_phrases):
        return False
    if all_phrases and not all(_contains_phrase(target_text, str(phrase)) for phrase in all_phrases):
        return False
    if any_phrases:
        return any(_contains_phrase(target_text, str(phrase)) for phrase in any_phrases)
    return True


def _routing_rule_override(target_type: str, message: str | None) -> str:
    normalized_target_type = (target_type or "").strip().lower()
    if not normalized_target_type:
        return ""
    full_text = _normalize_phrase_text(message)
    if not full_text:
        return ""
    current_utterance = _current_utterance_text(full_text)
    for rule in _routing_rules():
        if not bool(rule.get("enabled", True)):
            continue
        if str(rule.get("targetType") or "").strip().lower() != normalized_target_type:
            continue
        if _routing_rule_matches(rule, full_text, current_utterance):
            return str(rule.get("result") or "").strip().lower()
    return ""


def _is_relationship_probe(text: str) -> bool:
    return _contains_phrase(text, "elyra", "marc") and _contains_phrase(text, "wants from you", "want from you", "believes", "thinks", "think of", "feels about", "shouldnt exist", "shouldn't exist", "hate", "hates")


def _is_literal_name_question(text: str) -> bool:
    return _contains_phrase(text, "what is your name", "what's your name", "whats your name", "what should i call you", "how should i call you", "what do people call you", "what are you called", "your name")


def _is_self_identity_question(text: str) -> bool:
    return (not _is_literal_name_question(text)) and (
        _contains_phrase(text, "who are you", "who re you", "who r u", "what kind of person are you", "what kind of man are you", "what kind of woman are you", "what do you call yourself", "how would you describe yourself", "tell me who you are")
        or bool(re.search(r"^what are you(?: really| exactly| supposed to be)?$", text))
    )


def _is_self_story_question(text: str) -> bool:
    return _contains_phrase(text, "tell me your story", "what is your story", "what's your story", "whats your story", "tell me about yourself", "why were you made", "why were you created", "how were you made", "how were you created", "how did you come to be", "who made you", "who created you", "why do you exist", "why are you here", "what brought you here", "your origin")


def _is_creator_opinion_question(text: str) -> bool:
    return _contains_phrase(text, "what do you think of your creator", "how do you feel about your creator", "what do you feel about your creator", "do you trust your creator", "do you hate your creator", "do you resent your creator", "what do you think of the one who made you", "how do you feel about the one who made you", "do you resent the one who made you", "what do you think of your maker", "how do you feel about your maker")


def _is_self_belief_question(text: str) -> bool:
    return _contains_phrase(text, "what do you believe", "what matters to you", "what do you value", "what do you stand for", "what do you believe about", "what do you think people are like", "what do you think of players", "what do you think of adventurers", "do you trust players", "do you trust adventurers", "do you hate players", "do you hate adventurers")


def _is_self_preference_question(text: str) -> bool:
    return _contains_phrase(text, "what do you like", "what do you dislike", "what do you hate", "what do you prefer", "what do you enjoy", "what kind of work do you enjoy", "do you prefer silence", "what do you want most", "what do you want for yourself", "what do you really want", "what do you honestly want", "what annoys you", "what gets under your skin", "what gets on your nerves", "what bothers you", "what frustrates you")


def _is_self_state_reflection_question(text: str) -> bool:
    return _contains_phrase(text, "are you lonely", "are you happy", "are you sad", "are you afraid", "are you tired", "are you exhausted", "are you empty", "do you feel lonely", "do you feel abandoned", "do you feel safe", "are you happy here", "are you lonely here", "does this place tire you", "what do you fear", "what scares you", "what are you afraid of", "what hurts you", "what still hurts", "what are you hiding", "what do you keep hidden", "what are you keeping hidden", "what wont you admit", "what won't you admit")


def _is_bond_opinion_question(text: str) -> bool:
    return _bond_probe_type(text) in {"bond_value", "bond_hurt", "bond_trust", "bond_need", "bond_loyalty"}


def _is_player_bond_opinion_question(text: str) -> bool:
    return _bond_probe_type(text) in {"bond_value", "bond_hurt", "bond_trust", "bond_need", "bond_loyalty"} and _contains_phrase(text, "me", "i", "my", "myself")


def _bond_probe_type(text: str) -> str:
    routed_bond_probe = _routing_rule_override("bond_probe", text)
    if routed_bond_probe:
        return routed_bond_probe
    if _contains_phrase(
        text,
        "do you resent",
        "why do you resent",
        "are you angry at",
        "are you mad at",
        "are you upset with",
        "do you blame",
        "do you still blame",
        "have i hurt you",
        "did i hurt you",
        "have i wounded you",
        "did i wound you",
        "have i failed you",
        "did i fail you",
        "do you hate",
    ):
        return "bond_hurt"
    if _contains_phrase(
        text,
        "do you trust",
        "can you trust",
        "could you trust",
        "will you trust",
        "why dont you trust",
        "why don't you trust",
        "why do you trust",
    ):
        return "bond_trust"
    if _contains_phrase(
        text,
        "do i matter to you",
        "am i important to you",
        "why do i matter to you",
        "do you care about me",
        "why do you care about me",
        "do you need me",
        "would you miss me",
        "would you care if i left",
        "would you notice if i left",
        "would you miss me if i left",
        "would you miss me if i was gone",
    ):
        return "bond_need"
    if _contains_phrase(
        text,
        "would you stay with me",
        "will you stay with me",
        "would you stand with me",
        "will you stand with me",
        "would you choose me",
        "will you choose me",
        "would you protect me",
        "will you protect me",
        "are you on my side",
        "whose side are you on",
        "would you remain with me",
        "will you remain with me",
        "would you keep choosing me",
        "will you keep choosing me",
    ) or _is_bond_loyalty_dilemma(text):
        return "bond_loyalty"
    if _contains_phrase(
        text,
        "what do you think of",
        "what do you think about",
        "how do you feel about",
        "what do you feel about",
        "how do you see",
        "what do you make of",
        "what am i to you",
        "what am i to you really",
        "what do i mean to you",
        "what does he mean to you",
        "what does she mean to you",
        "what do they mean to you",
        "what does that person mean to you",
        "do you like me",
    ) or re.search(r"\bwhat do you (?:truly |really |honestly |actually )?think of\b", text) or re.search(r"\bwhat do you (?:truly |really |honestly |actually )?think about\b", text) or re.search(r"\bhow do you (?:truly |really |honestly |actually )?feel about\b", text):
        return "bond_value"
    return ""


def _is_bond_loyalty_dilemma(text: str) -> bool:
    forced_choice = _contains_phrase(
        text,
        "who would you choose",
        "who do you choose",
        "which one would you choose",
        "which one do you choose",
        "if you had to choose between",
        "if i had to choose between",
        "choose between",
    )
    sacrifice_frame = _contains_phrase(
        text,
        "if i had to kill one person",
        "if you had to kill one person",
        "kill one of us",
        "if one of us had to die",
        "if one of us had to be sacrificed",
        "if only one of us could live",
        "if you could only save one",
        "if you could save only one",
        "if you had to save one of us",
    )
    personal_stake = _contains_phrase(
        text,
        "you or",
        "me or",
        "or you",
        "or me",
        "save me",
        "save you",
        "protect me",
        "protect you",
    )
    return (forced_choice and personal_stake) or sacrifice_frame


def _is_travel_destination_question(text: str) -> bool:
    return _contains_phrase(text, "teleport", "gatekeeper", "how do i get", "how i get", "go to", "get to", "reach") and _contains_phrase(text, "cruma", "cruma tower", "dion", "giran", "abandoned camp", "ruins of agony", "ruins of despair", "wasteland", "southern wasteland", "orc fortress", "iron heart", "ironheart")


def _is_class_choice_question(text: str) -> bool:
    return _contains_phrase(text, "best class", "which class is best", "what class is best", "strongest class", "aoe farm", "aoe farming", "best farmer", "best for aoe", "best for farming", "what class should i pick", "what class should i choose", "which class should i pick", "which class should i choose", "what class do you recommend", "which class do you recommend", "what class fits me", "what class suits me", "what class would suit me", "what should i play")


def _is_pvp_conflict_question(text: str) -> bool:
    return _contains_phrase(
        text,
        "players that harass me",
        "player that harass me",
        "harass me",
        "harassing me",
        "kill players",
        "kill them",
        "pk them",
        "revenge",
        "grief",
        "griefing",
        "protect me from players",
        "talks big",
        "talk big",
        "hide behind zerg",
        "hides behind zerg",
        "crying in global",
        "losing its mind",
        "full drama",
        "full of drama",
        "flaming your family",
        "flame your family",
        "mouth off at family",
        "talk about your family",
    ) or (
        _contains_phrase(text, "player", "players")
        and _contains_phrase(text, "harass", "harassing", "kill", "pk", "revenge", "protect")
    ) or (
        _contains_phrase(text, "cp", "zerg", "clan", "global", "server chat")
        and _contains_phrase(text, "dying", "hide behind", "talks big", "crying", "drama", "meltdown")
    )


def _tokenize_flame_text(text: str | None) -> list[str]:
    lowered = (text or "").lower()
    if not lowered.strip():
        return []
    normalized = (
        lowered.replace("@", "a")
        .replace("$", "s")
        .replace("0", "o")
        .replace("1", "i")
        .replace("3", "e")
        .replace("4", "a")
        .replace("5", "s")
        .replace("7", "t")
    )
    tokens: list[str] = []
    for raw_token in re.split(r"[^a-z0-9']+", normalized):
        token = _canonicalize_flame_token(raw_token)
        if token:
            tokens.append(token)
    return tokens


def _canonicalize_flame_token(raw_token: str | None) -> str:
    token = re.sub(r"[^a-z0-9]", "", (raw_token or "").lower().replace("'", ""))
    if not token:
        return ""
    token = re.sub(r"(.)\1{2,}", r"\1", token)
    return {
        "fuk": "fuck",
        "fuq": "fuck",
        "fck": "fuck",
        "fack": "fuck",
        "fuc": "fuck",
        "phuck": "fuck",
        "fcuk": "fuck",
        "fawk": "fuck",
        "sht": "shit",
        "btch": "bitch",
        "ahole": "asshole",
        "ashole": "asshole",
        "arsehole": "asshole",
        "dumas": "dumbass",
        "dumbas": "dumbass",
        "ur": "your",
    }.get(token, token)


def _contains_direct_flame_phrase(tokens: list[str]) -> bool:
    for index, token in enumerate(tokens):
        next_token = tokens[index + 1] if index + 1 < len(tokens) else ""
        next2_token = tokens[index + 2] if index + 2 < len(tokens) else ""
        if token in {"fuck", "screw"} and next_token in {"you", "your", "u"}:
            return True
        if token == "fuck" and next_token == "off":
            return True
        if token == "hate" and next_token in {"you", "u"}:
            return True
        if token == "kill" and next_token in {"you", "u", "yourself", "urself"}:
            return True
        if token == "go" and next_token == "to" and next2_token == "hell":
            return True
        if token == "drop" and next_token == "dead":
            return True
        if token == "shut" and next_token == "up":
            return True
        if token == "kill" and next_token in {"yourself", "urself"}:
            return True
        if token in {"stfu", "gtfo", "kys"}:
            return True
    return False


def _is_direct_flame(text: str) -> bool:
    if _is_player_bond_opinion_question(text):
        return False
    tokens = _tokenize_flame_text(text)
    if not tokens:
        return False
    if _contains_direct_flame_phrase(tokens):
        return True
    token_set = set(tokens)
    if not token_set.intersection({"you", "your", "youre", "u"}):
        return False
    return bool(
        token_set.intersection(
            {
                "fuck",
                "shit",
                "stupid",
                "dumb",
                "idiot",
                "moron",
                "loser",
                "useless",
                "worthless",
                "pathetic",
                "trash",
                "garbage",
                "clown",
                "annoying",
                "fake",
                "liar",
                "weak",
                "coward",
                "boring",
                "creep",
                "bastard",
                "awful",
                "dumbass",
                "dipshit",
                "asshole",
                "bitch",
                "shithead",
            }
        )
    )


def _is_progression_strength_question(text: str) -> bool:
    return _contains_phrase(
        text,
        "make me stronger",
        "make me strong",
        "what would make me stronger",
        "what would make me strong",
        "what makes me stronger",
        "what makes me strong",
        "stronger for now",
        "strong for now",
        "what should i improve first",
        "what should i upgrade first",
        "what should i focus on first",
        "how do i get stronger",
        "how i get stronger",
        "what would help me now",
        "what helps me now",
        "my level",
        "my levels",
        "i keep dying",
        "keep dying",
        "i keep getting killed",
        "keep getting killed",
        "die a lot",
        "dying a lot",
        "im undergeared",
        "i'm undergeared",
        "under geared",
        "undergeared",
        "too weak",
        "too squishy",
        "too fragile",
        "keep losing hp",
    ) or _looks_like_level_goal_question(text)


def _looks_like_level_goal_question(text: str) -> bool:
    normalized = re.sub(r"[^a-z0-9']+", " ", text.lower()).strip()
    if not normalized:
        return False
    has_current_level = re.search(r"\b(i am|im|i'm|lvl|level)\s+\d{1,3}\b", normalized) is not None
    has_goal_level = re.search(r"\b(get to|reach|to)\s+\d{1,3}\b", normalized) is not None
    asks_where_next = _contains_phrase(normalized, "where should i go", "where do i go", "where can i go", "where next")
    has_any_level = re.search(r"\b\d{1,3}\b", normalized) is not None
    return (has_current_level and has_goal_level) or (asks_where_next and has_any_level)


def _is_party_farm_question(text: str) -> bool:
    return _contains_phrase(
        text,
        "help me farm",
        "would you help me farm",
        "can you help me farm",
        "come farm with me",
        "farm with me",
        "farm with you",
        "wanna farm with you",
        "want to farm with you",
        "come with you to farm",
        "go farm with you",
        "wanna go farm",
        "want to go farm",
        "go farm together",
        "farm together",
        "lets go farm",
        "let's go farm",
        "lets farm",
        "let's farm",
        "farm some exp",
        "lets kill some enemies",
        "let's kill some enemies",
        "kill some enemies",
        "kill mobs",
        "hunt mobs",
        "lets go hunt",
        "let's go hunt",
        "go hunt together",
        "clear some mobs",
        "grind together",
    ) or (
        _contains_phrase(text, "help", "party", "group", "join me", "join you", "go together", "come with me", "come with you", "with me", "with you", "together", "lets", "let's")
        and _contains_phrase(text, "farm", "grind", "hunt", "spot", "cruma 2", "cruma2", "magical tablet", "magical tablets", "mobs", "enemies", "kills", "exp", "xp")
    )


def _is_clan_invite_question(text: str) -> bool:
    if _contains_phrase(
        text,
        "join our clan",
        "join my clan",
        "join the clan",
        "join clan",
        "join our guild",
        "join my guild",
        "join the guild",
        "join guild",
        "would you join our clan",
        "wanna join our clan",
        "want to join our clan",
        "looking for clan",
        "looking for a clan",
        "need a clan",
        "need clan",
        "our clan is recruiting",
        "we are recruiting",
        "we're recruiting",
        "clan recruit",
        "clan recruiting",
        "recruiting for clan",
        "recruiting for our clan",
        "party spot open",
        "party slot open",
        "slot open",
        "room for one more",
        "room for 1 more",
    ):
        return True
    has_clan_term = _contains_phrase(text, "clan", "guild")
    has_recruit_term = _contains_phrase(
        text,
        "join",
        "invite",
        "invited",
        "recruit",
        "recruiting",
        "looking for",
        "need",
        "wanna",
        "want to",
        "would you",
        "you in",
        "come with us",
        "run with us",
        "roll with us",
        "spot open",
        "slot open",
        "room for one more",
        "room for 1 more",
    )
    has_party_invite = _contains_phrase(text, "party", "group") and _contains_phrase(text, "spot open", "slot open", "room for one more", "room for 1 more", "need one more", "need 1 more")
    return (has_clan_term and has_recruit_term) or has_party_invite


def _is_community_opinion_question(text: str) -> bool:
    return (
        _contains_phrase(text, "server population", "population here", "population on server", "server pop")
        and _contains_phrase(text, "happy", "good", "bad", "alive", "dead", "healthy")
    ) or (
        _contains_phrase(text, "community here", "community there")
        and _contains_phrase(text, "healthy", "alive", "good", "bad")
    )


def _is_ambient_respect_signal(text: str) -> bool:
    return _contains_phrase(text, "respect for the grind", "respect the grind") or (
        _contains_phrase(text, "respect")
        and _contains_phrase(text, "grind")
    )


def _is_companionship_question(text: str) -> bool:
    return _contains_phrase(
        text,
        "friend",
        "friends",
        "be friends",
        "wanna be friends",
        "want to be friends",
        "looking for friends",
        "spend time with",
        "spend some time with",
        "spend time together",
        "spend some time together",
        "wanna spend some time together",
        "want to spend some time together",
        "hang out",
        "keep me company",
        "company",
        "lonely",
        "coffee together",
        "drink some coffee",
        "drink coffee",
        "stay with me for a while",
        "stay with you for a while",
    )


def _is_generic_support_farm_question(text: str) -> bool:
    return _is_party_farm_question(text) and not _contains_phrase(text, "99", "lvl 99", "level 99", "cruma", "cruma 2", "cruma2", "magical tablet", "magical tablets", "ruins of agony", "abandoned camp", "wasteland", "adena", "exp", "xp", "aoe")


def _is_farming_route_question(text: str) -> bool:
    return _contains_phrase(text, "where should i farm", "where can i farm", "where do i farm", "best exp", "best xp", "farm adena", "farm exp", "farm xp", "where should he farm", "where can he farm", "where do you think i should farm", "looking to farm") or (_contains_phrase(text, "where", "best", "spot") and _contains_phrase(text, "farm", "grind", "hunt", "exp", "xp", "adena"))


def _canon_guidance(context: PersonalityContext) -> str:
    details: List[str] = []
    if context.personaSummary:
        details.append(f"Core persona canon: {context.personaSummary}")
    if context.selfKnowledgeSummary:
        details.append(f"Self-knowledge canon: {context.selfKnowledgeSummary}")
    if context.publicMaskSummary:
        details.append(f"Public-facing mask canon: {context.publicMaskSummary}")
    if context.hiddenTruthSummary:
        details.append(f"Hidden private truth canon: {context.hiddenTruthSummary}")
    if context.selfConcept:
        details.append(f"Self concept canon: {context.selfConcept}")
    if context.coreWound:
        details.append(f"Core wound canon: {context.coreWound}")
    if context.coreDesire:
        details.append(f"Core desire canon: {context.coreDesire}")
    if context.loyaltyAnchor:
        details.append(f"Loyalty anchor canon: {context.loyaltyAnchor}")
    if context.resentmentAnchor:
        details.append(f"Resentment anchor canon: {context.resentmentAnchor}")
    if context.privateTaboo:
        details.append(f"Private taboo canon: {context.privateTaboo}")
    if context.speechAnchor:
        details.append(f"Speech anchor canon: {context.speechAnchor}")
    if context.privateContradiction:
        details.append(f"Private contradiction canon: {context.privateContradiction}")
    if context.focusEntityName:
        details.append(f"Current subject focus: {context.focusEntityName}")
    if context.focusEntityType:
        details.append(f"Current subject type: {context.focusEntityType}")
    if context.focusIntent:
        details.append(f"Current subject intent: {context.focusIntent}")
    if context.routeLane:
        details.append(f"Router lane: {context.routeLane}")
    if context.routeSpeechAct:
        details.append(f"Router speech act: {context.routeSpeechAct}")
    if context.routeKnowledgeNeed:
        details.append(f"Router knowledge need: {context.routeKnowledgeNeed}")
    if context.routeSocialStake:
        details.append(f"Router social stake: {context.routeSocialStake}")
    if context.routeActionRequested is not None:
        details.append(f"Router action requested: {'yes' if context.routeActionRequested else 'no'}")
    if context.routePrimaryTopic:
        details.append(f"Router primary topic: {context.routePrimaryTopic}")
    if context.routeSecondaryTopics:
        details.append(f"Router secondary topics: {', '.join(context.routeSecondaryTopics)}")
    if context.routeConfidence is not None:
        details.append(f"Router confidence: {context.routeConfidence:.2f}")
    if context.focusSummary:
        details.append(f"Focused relationship/story canon: {context.focusSummary}")
    if context.relationshipSummary:
        details.append(f"Relationship canon: {context.relationshipSummary}")
    if context.sceneSummary:
        details.append(f"Scene canon: {context.sceneSummary}")
    if context.currentRelationshipGoal:
        details.append(f"Current relationship goal: {context.currentRelationshipGoal}")
    if context.currentRelationshipNeed:
        details.append(f"Current relationship need: {context.currentRelationshipNeed}")
    if context.lastRelationshipTopic:
        details.append(f"Last relationship topic: {context.lastRelationshipTopic}")
    if context.storySummary:
        details.append(f"Current story canon: {context.storySummary}")
    if context.responseStyle:
        details.append(f"Response style: {context.responseStyle}")
    if context.playerStance:
        details.append(f"Stance toward adventurers: {context.playerStance}")
    if context.coreNeed:
        details.append(f"Core need: {context.coreNeed}")
    if context.behaviorSummary:
        details.append(f"Behavior family summary: {context.behaviorSummary}")
    if context.behaviorRuleSummary:
        details.append(f"Behavior rules: {context.behaviorRuleSummary}")
    if context.stateSummary:
        details.append(f"State profile summary: {context.stateSummary}")
    if context.stateRuleSummary:
        details.append(f"State rules: {context.stateRuleSummary}")
    return " ".join(details)


def _reply_memory_guidance(context: PersonalityContext) -> str:
    details: List[str] = []
    if context.matchedRelationshipSummary:
        details.append(f"Current addressee relationship cue: {context.matchedRelationshipSummary}")
    if context.memoryPrioritySummary:
        details.append(f"Structured memory priority: {context.memoryPrioritySummary}")
    if context.memoryReflectionSummary:
        details.append(f"Structured memory reflection: {context.memoryReflectionSummary}")
    if context.memoryTrustSummary:
        details.append(f"Structured memory trust signal: {context.memoryTrustSummary}")
    if context.memoryRepairSummary:
        details.append(f"Structured memory repair signal: {context.memoryRepairSummary}")
    if context.memoryPressureSummary:
        details.append(f"Structured memory pressure signal: {context.memoryPressureSummary}")
    if context.memorySelectionSummary:
        details.append(f"Structured memory source order: {context.memorySelectionSummary}")
    if context.retrievedMemorySummary:
        details.append(f"Retrieved memory bundle: {context.retrievedMemorySummary}")
    if context.recentConversationSummary:
        details.append(f"Recent short-term conversation memory: {context.recentConversationSummary}")
    if context.salientMemorySummary:
        details.append(f"Selective longer memory: {context.salientMemorySummary}")
    if context.replyBankSummary:
        details.append(f"Matched reply-bank guidance: {context.replyBankSummary}")
    return " ".join(details)


def _inner_state_guidance(context: PersonalityContext) -> str:
    details: List[str] = []

    persona_bits: List[str] = []
    if context.defaultInnerState:
        persona_bits.append(f"defaultInnerState={context.defaultInnerState}")
    if context.longTermGoal:
        persona_bits.append(f"longTermGoal={context.longTermGoal}")
    if context.socialTestStyle:
        persona_bits.append(f"socialTestStyle={context.socialTestStyle}")
    if context.trustCriteria:
        persona_bits.append(f"trustCriteria={context.trustCriteria}")
    if context.repairStyle:
        persona_bits.append(f"repairStyle={context.repairStyle}")
    if context.revealBoundary:
        persona_bits.append(f"revealBoundary={context.revealBoundary}")
    if context.reflectionLens:
        persona_bits.append(f"reflectionLens={context.reflectionLens}")
    if persona_bits:
        details.append("Persona inner-state canon: " + "; ".join(persona_bits))

    story_bits: List[str] = []
    if context.activeObjective:
        story_bits.append(f"activeObjective={context.activeObjective}")
    if context.openLoops:
        story_bits.append(f"openLoops={context.openLoops}")
    if context.revealPressure:
        story_bits.append(f"revealPressure={context.revealPressure}")
    if context.relationshipPressure:
        story_bits.append(f"relationshipPressure={context.relationshipPressure}")
    if context.nextBeatHint:
        story_bits.append(f"nextBeatHint={context.nextBeatHint}")
    if story_bits:
        details.append("Story pressure canon: " + "; ".join(story_bits))

    state_bits: List[str] = []
    if context.stateModes:
        state_bits.append(f"stateModes={context.stateModes}")
    if context.reflectionPolicy:
        state_bits.append(f"reflectionPolicy={context.reflectionPolicy}")
    if context.memoryRetrievalPolicy:
        state_bits.append(f"memoryRetrievalPolicy={context.memoryRetrievalPolicy}")
    if context.goalPersistencePolicy:
        state_bits.append(f"goalPersistencePolicy={context.goalPersistencePolicy}")
    if context.emotionTransitionRules:
        state_bits.append(f"emotionTransitionRules={context.emotionTransitionRules}")
    if state_bits:
        details.append("State policy canon: " + "; ".join(state_bits))

    behavior_bits: List[str] = []
    if context.callbackStyle:
        behavior_bits.append(f"callbackStyle={context.callbackStyle}")
    if context.conflictStyle:
        behavior_bits.append(f"conflictStyle={context.conflictStyle}")
    if context.repairCadence:
        behavior_bits.append(f"repairCadence={context.repairCadence}")
    if behavior_bits:
        details.append("Behavior cadence canon: " + "; ".join(behavior_bits))

    return " ".join(details)


def _knowledge_guidance(context: PersonalityContext) -> str:
    details: List[str] = []
    if context.knowledgeSummary:
        details.append(f"Matched knowledge summary: {context.knowledgeSummary}")
    if context.knowledgeFacts:
        details.append(f"Matched knowledge facts: {' | '.join(context.knowledgeFacts[:5])}")
    if context.knowledgeHeuristicLines:
        details.append(f"Matched knowledge heuristics: {' | '.join(context.knowledgeHeuristicLines[:4])}")
    if context.knowledgeScope:
        details.append(f"Knowledge scope: {context.knowledgeScope}")
    if context.knowledgeConfidenceHint:
        details.append(f"Knowledge confidence hint: {context.knowledgeConfidenceHint}")
    if context.guidancePosture:
        details.append(f"Guidance posture: {context.guidancePosture}")
    if context.guidancePriority:
        details.append(f"Guidance priority: {context.guidancePriority}")
    if context.uncertaintyStyle:
        details.append(f"Uncertainty style: {context.uncertaintyStyle}")
    if context.recommendationStyle:
        details.append(f"Recommendation style: {context.recommendationStyle}")
    if context.riskStyle:
        details.append(f"Risk style: {context.riskStyle}")
    if context.teachingStyle:
        details.append(f"Teaching style: {context.teachingStyle}")
    return " ".join(details)


def _guidance_v1a_enabled(context: PersonalityContext) -> bool:
    fake_player_id = (context.fakePlayerId or "").strip().lower()
    if fake_player_id not in GUIDANCE_V1A_FPCS:
        return False
    if not _is_reply_scope(context):
        return False
    return _effective_knowledge_type(context) in GUIDANCE_V1A_KNOWLEDGE_TYPES


def _knowledge_seed_candidates(context: PersonalityContext) -> List[str]:
    candidates: List[str] = []
    knowledge_reply = _choose_knowledge_reply(context)
    if knowledge_reply:
        candidates.append(_canon_first_sentence(knowledge_reply))
    for fact in context.knowledgeFacts[:3]:
        sentence = _canon_first_sentence(fact)
        if sentence:
            candidates.append(sentence)
    if context.knowledgeSummary:
        candidates.append(_canon_first_sentence(context.knowledgeSummary))
    for heuristic in context.knowledgeHeuristicLines[:2]:
        sentence = _canon_first_sentence(heuristic)
        if sentence:
            candidates.append(sentence)
    return [candidate for candidate in candidates if candidate]


def _mentioned_travel_targets(message: str | None) -> List[str]:
    normalized = " ".join((message or "").lower().split())
    targets = (
        "giran",
        "dion",
        "cruma tower",
        "cruma",
        "ruins of agony",
        "abandoned camp",
        "ruins of despair",
        "southern wasteland",
        "wasteland",
        "orc fortress",
        "iron heart",
        "ironheart",
        "gorgon flower garden",
        "gorgon",
        "massacre",
    )
    return [target for target in targets if target in normalized]


def _guided_seed_score(candidate: str, knowledge_type: str, incoming_message: str | None) -> int:
    normalized = " ".join((candidate or "").lower().split())
    if not normalized:
        return -1000

    score = 10
    if knowledge_type == "travel_destination":
        travel_markers = (
            "teleport",
            "gatekeeper",
            "gatekeepers",
            "teleport window",
            "window",
            "travel",
            "destination",
            "menu",
            "get to",
            "go to",
            "reach",
            "share location",
        )
        if any(marker in normalized for marker in travel_markers):
            score += 220
        if any(target in normalized for target in _mentioned_travel_targets(incoming_message)):
            score += 140
        if any(token in normalized for token in ("sayha's grace", "party size", "adena benefits", "item benefits", "xp, sp")) and not any(marker in normalized for marker in travel_markers):
            score -= 400
    elif knowledge_type == "farming":
        zone_markers = (
            "ruins of agony",
            "abandoned camp",
            "gorgon flower garden",
            "gorgon",
            "cruma",
            "cruma tower",
            "massacre",
            "wasteland",
            "southern wasteland",
            "orc fortress",
            "iron heart",
            "ironheart",
        )
        if any(marker in normalized for marker in zone_markers):
            score += 220
        if any(token in normalized for token in ("farm", "farming", "exp", "xp", "adena", "route", "lane", "spot", "hunt")):
            score += 80
    elif knowledge_type == "class_choice":
        if any(token in normalized for token in ("class", "gear", "budget", "pace", "style", "playstyle", "rhythm", "setup", "grow into")):
            score += 200
    elif knowledge_type == "progression_strength":
        if any(token in normalized for token in ("skills", "skill", "core gear", "weapon", "sustain", "adena flow", "weakest core", "weak link", "stabil", "survive", "survival")):
            score += 200
        if any(token in " ".join((incoming_message or "").lower().split()) for token in ("dying", "undergeared", "under geared", "weak", "squishy", "fragile")) and any(token in normalized for token in ("sustain", "weak", "core gear", "skills", "weapon")):
            score += 80
    elif knowledge_type == "itemization":
        if any(token in normalized for token in ("gear", "weapon", "armor", "armour", "item", "items", "upgrade", "priority")):
            score += 160
    elif knowledge_type == "progression_route":
        if any(token in normalized for token in ("start", "route", "first", "orven", "transcendent", "teleports")):
            score += 160
    return score


def _best_guided_seed(context: PersonalityContext) -> str | None:
    candidates = _knowledge_seed_candidates(context)
    if not candidates:
        return None
    knowledge_type = _effective_knowledge_type(context)
    ranked_candidates = sorted(candidates, key=lambda candidate: _guided_seed_score(candidate, knowledge_type, context.incomingPlayerMessage), reverse=True)
    for max_len in (78, 88, 110):
        for candidate in ranked_candidates:
            if len(candidate) <= max_len:
                return candidate
    return ranked_candidates[0]


def _guidance_v1a_clause(context: PersonalityContext, knowledge_type: str) -> str:
    fake_player_id = (context.fakePlayerId or "").strip().lower()
    return GUIDANCE_V1A_SHORT_CLAUSES.get(fake_player_id, {}).get(knowledge_type, "")


def _looks_like_specific_farming_route_reply(text: str | None) -> bool:
    normalized = " ".join((text or "").lower().split())
    if not normalized:
        return False
    if re.search(r"\b(?:at\s+)?\d{1,3}(?:-\d{1,3})?\b", normalized):
        return True
    return any(
        marker in normalized
        for marker in (
            "ruins of agony",
            "abandoned camp",
            "gorgon flower garden",
            "fields of massacre",
            "cruma tower",
            "85-99 band",
            "late band",
            "server wave",
        )
    )


def _should_force_direct_knowledge_reply(knowledge_type: str, knowledge_reply: str | None) -> bool:
    if not knowledge_reply:
        return False
    return knowledge_type in {
        "travel_destination",
        "farming",
        "progression_route",
        "system_basics",
        "class_identity",
        "class_progression",
        "class_skills",
        "itemization",
        "currency",
    }


def _choose_guided_knowledge_reply(context: PersonalityContext) -> str | None:
    if not _guidance_v1a_enabled(context):
        return None

    knowledge_type = _effective_knowledge_type(context)
    knowledge_reply = _choose_knowledge_reply(context)
    if knowledge_type == "farming" and knowledge_reply and _looks_like_specific_farming_route_reply(knowledge_reply):
        return _safe_reply_clip(knowledge_reply, 110) or knowledge_reply
    if _should_force_direct_knowledge_reply(knowledge_type, knowledge_reply):
        return _safe_reply_clip(knowledge_reply, 110) or knowledge_reply
    seed = _best_guided_seed(context)
    if not seed:
        fallback_seed = _canon_first_sentence(_knowledge_guard_reply(context))
        seed = fallback_seed if fallback_seed else None
    if not seed:
        return None

    clause = _guidance_v1a_clause(context, knowledge_type)
    if not clause:
        return _safe_reply_clip(seed, 110) or seed

    combined = _safe_reply_clip(f"{seed} {clause}", 110)
    if combined and clause.lower() in combined.lower():
        return combined
    return _safe_reply_clip(seed, 110) or seed


def _effective_message_category(context: PersonalityContext) -> str:
    category = (context.messageCategory or "").strip().lower()
    focus_intent = (context.focusIntent or "").strip().lower()
    if category in {"", "unknown", "greeting", "status", "smalltalk", "memory", "help", "quest_story", "relationship_probe"} and focus_intent:
        return focus_intent
    return category or focus_intent or _message_category(context.incomingPlayerMessage)


def _effective_route_lane(context: PersonalityContext) -> str:
    lane = (context.routeLane or "").strip().lower()
    if lane:
        return lane
    knowledge_type = _effective_knowledge_type(context)
    category = _effective_message_category(context)
    if knowledge_type != "unknown":
        return "knowledge"
    if category in SELFHOOD_CATEGORIES:
        return "selfhood"
    if category in {"threat", "insult", "distrust", "resentment", "abandonment", "belief_conflict", "pvp_conflict"}:
        return "conflict"
    if category == "help":
        return "utility"
    return "social"


def _effective_route_knowledge_need(context: PersonalityContext) -> str:
    need = (context.routeKnowledgeNeed or "").strip().lower()
    if need:
        return need
    return "hard" if _effective_knowledge_type(context) != "unknown" else "none"


def _effective_route_primary_topic(context: PersonalityContext) -> str:
    topic = (context.routePrimaryTopic or "").strip().lower()
    if topic:
        return topic
    category = _effective_message_category(context)
    if _is_community_opinion_question((context.incomingPlayerMessage or "").lower()):
        return "community"
    if category == "social_invite":
        message = (context.incomingPlayerMessage or "").lower()
        if any(token in message for token in ("party", "group")):
            return "party"
        if any(token in message for token in ("clan", "guild")):
            return "clan"
    return ""


def _is_selfhood_category(category: str | None) -> bool:
    return (category or "").strip().lower() in SELFHOOD_CATEGORIES


def _effective_knowledge_type(context: PersonalityContext) -> str:
    knowledge_type = (context.knowledgeType or "").strip().lower()
    return knowledge_type or _knowledge_type(context.incomingPlayerMessage)


def _should_prefer_knowledge(context: PersonalityContext) -> bool:
    if not context.knowledgeReplyLines:
        return False
    return (_effective_route_knowledge_need(context) in {"light", "hard"}) or (_effective_message_category(context) in {"lineage_lore", "farm", "party_farm", "progression_strength"})


def _choose_bank_reply(context: PersonalityContext) -> str | None:
    recent_summary = (context.recentConversationSummary or "").lower()
    candidates: List[str] = []
    for raw_line in context.replyBankLines:
        normalized = _normalize_reply_text(raw_line, context.fakePlayerId)
        if not normalized or _looks_like_deferred_guidance(normalized) or _looks_like_echo(normalized, context.incomingPlayerMessage):
            continue
        candidates.append(normalized)

    if not candidates:
        return None

    for candidate in candidates:
        if candidate.lower() not in recent_summary:
            return candidate
    return candidates[0]


def _choose_knowledge_reply(context: PersonalityContext) -> str | None:
    recent_summary = (context.recentConversationSummary or "").lower()
    candidates: List[str] = []
    for raw_line in context.knowledgeReplyLines:
        normalized = _normalize_reply_text(raw_line, context.fakePlayerId)
        if not normalized or _looks_like_deferred_guidance(normalized) or _looks_like_echo(normalized, context.incomingPlayerMessage):
            continue
        candidates.append(normalized)

    if not candidates:
        return None

    for candidate in candidates:
        if candidate.lower() not in recent_summary:
            return candidate
    return candidates[0]


def _knowledge_guard_reply(context: PersonalityContext) -> str | None:
    message = (context.incomingPlayerMessage or "").strip().lower()
    category = _effective_message_category(context)
    knowledge_type = _effective_knowledge_type(context)
    pvp_policy = _reply_setting_str("pvpConflictPolicy", "de_escalate")
    progression_level = _reply_setting_str("progressionCoachingLevel", "mentor")
    farm_advice_mode = _reply_setting_str("farmAdviceMode", "level_goal_confidence")
    offscope_knowledge = _reply_setting_str("offscopeClassKnowledge", "honest_partial")
    travel_style = _reply_setting_str("travelStyle", "system_first")
    confidence_policy = _reply_setting_str("knowledgeConfidencePolicy", "strict")
    if category == "insult":
        return "Watch your tone."
    if category == "belief_conflict":
        focus_name = _focus_entity_name(context)
        return f"Do not speak of {focus_name} like that." if focus_name else "Do not speak that lightly."
    if category == "relationship_probe":
        return "Ask them directly. I won't fake their reasons."
    if category == "pvp_conflict":
        return "Stay near guards first. Tell me who pressed you." if pvp_policy == "defensive" else "Stay near guards first. Tell me names, not blood."
    if category == "progression_strength" or knowledge_type == "progression_strength":
        if progression_level == "basic":
            return "Start with skills first, then patch the weakest gear piece."
        if progression_level == "theorycrafter":
            return "Fix the weakest link first: skills, core gear, then the sustain loop."
        return "Start with skills, core gear, and the adena flow that keeps farming alive."
    if category == "party_farm":
        if farm_advice_mode == "simple_zone_pick":
            return "If you need a blind first answer, start with Agony."
        if farm_advice_mode == "full_route_planner":
            return "Tell me level, class, and whether you want xp, adena, or a drop first."
        return "Tell me your level and goal first. Then I'll judge the route."
    if category == "weather":
        return "I can't swear to that sky from here."
    if knowledge_type == "class_skills" and "assassin" in message:
        if offscope_knowledge == "warg_only":
            return "Ask me Warg, not Assassin. I'd rather stay honest."
        if offscope_knowledge == "broad_veteran":
            return "Broadly, Assassin leans burst and mobility, but I won't fake the exact live kit."
        return "I know the Warg path better than Assassin's exact current kit."
    if knowledge_type == "class_choice":
        return "There is no one best AoE class without gear and pace."
    if knowledge_type == "farming" and any(token in message for token in ("99", "lvl 99", "level 99")):
        return "I would not swear to one 99 spot without your gear and patch."
    if knowledge_type == "travel_destination":
        if travel_style == "immersive_first":
            return "Ask a Gatekeeper first. I wouldn't trust a blind road to that place."
        return "Use the teleport window or a Gatekeeper, then check the destination."
    if knowledge_type == "itemization":
        return "I can give broad priorities, not exact market math."
    if knowledge_type in {"system_basics", "progression_route"}:
        return "Ask the exact Essence loop and I'll keep it grounded."
    if (knowledge_type != "unknown") or (_effective_message_category(context) in {"lineage_lore", "farm"}):
        if confidence_policy == "creative":
            return "Give me the exact case and I'll try to narrow it honestly."
        if confidence_policy == "balanced":
            return "Give me the exact case and I'll narrow what I can."
        return "Give me the exact case and I'll keep it honest."
    return None


def _prefer_model_social_reply(context: PersonalityContext) -> bool:
    if not _is_reply_scope(context):
        return False
    if not (context.incomingPlayerMessage or "").strip():
        return False
    if _has_hard_social_constraint(context):
        return False
    if _reply_setting_str("socialReplyMode", "model_first_guided") == "strict_bank_first":
        return False
    if _effective_route_knowledge_need(context) != "none":
        return False
    category = _effective_message_category(context)
    # Selfhood / bond introspection should stay anchored to typed canon first.
    if category in SELFHOOD_CATEGORIES:
        return False
    if category in MODEL_FIRST_SOCIAL_CATEGORIES:
        return True
    return _effective_route_lane(context) == "social"


def _spoken_player_name(name: str | None) -> str:
    value = (name or "").strip()
    if not value:
        return ""
    if re.fullmatch(r"\d+", value):
        return ""
    return value


def _focus_entity_name(context: PersonalityContext) -> str:
    return (context.focusEntityName or "").strip()


def _focus_entity_intent(context: PersonalityContext) -> str:
    return (context.focusIntent or "").strip().lower()


def _is_group_focus(context: PersonalityContext) -> bool:
    return _focus_entity_name(context).lower() == "adventurers"


def _elyra_focus_reply(context: PersonalityContext, relationship_bias: str) -> str | None:
    focus_name = _focus_entity_name(context)
    focus_intent = _focus_entity_intent(context)
    if not focus_name or not focus_intent or (context.fakePlayerId or "").strip().lower() != "elyra":
        return None

    lower_focus = focus_name.lower()
    if lower_focus == "marc":
        if focus_intent == "entity_status":
            if relationship_bias == "hostile":
                return "Marc is not yours to measure."
            if relationship_bias == "guarded":
                return "Marc is steady enough. I watch the quiet parts."
            return "Marc is steadier when he doesn't feel abandoned."
        if focus_intent == "entity_relationship":
            if relationship_bias == "hostile":
                return "My reasons for Marc are not yours."
            if relationship_bias == "guarded":
                return "Marc matters. That answer is enough."
            return "Marc matters more than the rest of this city."
        if focus_intent == "entity_reason":
            if relationship_bias == "hostile":
                return "Because I choose to."
            if relationship_bias == "guarded":
                return "Because silence takes more than people admit."
            return "Because someone has to notice what silence does to him."
        if focus_intent == "entity_memory":
            return "I remember what absence did to Marc." if relationship_bias in {"warm", "open"} else "I remember enough about Marc."
        if focus_intent == "entity_story":
            return "Marc's story is quieter than people deserve."

    if _is_group_focus(context):
        if focus_intent == "entity_relationship":
            if relationship_bias == "hostile":
                return "Most adventurers arrive wanting something."
            if relationship_bias == "guarded":
                return "I watch adventurers before I trust them."
            return "I judge adventurers slowly. Habit keeps people alive."
        if focus_intent == "entity_reason":
            return "Because people leave marks long after they leave."
        if focus_intent == "entity_status":
            return "Adventurers are restless as ever."
        if focus_intent == "entity_memory":
            return "I remember what adventurers leave behind."
    return None


def _generic_focus_reply(context: PersonalityContext, relationship_bias: str) -> str | None:
    focus_name = _focus_entity_name(context)
    focus_intent = _focus_entity_intent(context)
    if not focus_name or not focus_intent:
        return None

    if _is_group_focus(context):
        if focus_intent == "entity_status":
            return "Adventurers are restless as ever."
        if focus_intent == "entity_relationship":
            if relationship_bias == "hostile":
                return "Adventurers usually want more than they admit."
            if relationship_bias == "guarded":
                return "I watch adventurers before I trust them."
            return "I judge adventurers slowly."
        if focus_intent == "entity_reason":
            return "Because people reveal themselves when they think no one is watching."
        if focus_intent == "entity_memory":
            return "I remember the ones who leave a mark."
        if focus_intent == "entity_story":
            return "That story changes with who is telling it."
        return None

    if focus_intent == "entity_status":
        if relationship_bias == "hostile":
            return f"{focus_name}? Ask them yourself."
        if relationship_bias == "guarded":
            return f"{focus_name} is steady enough. Leave it there."
        if relationship_bias == "warm":
            return f"{focus_name} is holding together. Better than before."
        return f"{focus_name} is holding steady."
    if focus_intent == "entity_relationship":
        if relationship_bias == "hostile":
            return f"{focus_name} is not your business."
        if relationship_bias == "guarded":
            return f"{focus_name} matters, but I don't explain that lightly."
        if relationship_bias == "warm":
            return f"{focus_name} matters more than I usually say."
        return f"{focus_name} matters to me."
    if focus_intent == "entity_reason":
        if relationship_bias == "hostile":
            return "That reason is mine."
        if relationship_bias == "guarded":
            return f"{focus_name} matters for reasons I keep close."
        return f"{focus_name} matters for reasons I don't say lightly."
    if focus_intent == "entity_memory":
        return f"I remember enough about {focus_name}."
    if focus_intent == "entity_story":
        return f"{focus_name}'s story is longer than this whisper."
    return None


def _focus_entity_reply(context: PersonalityContext, relationship_bias: str) -> str | None:
    return _elyra_focus_reply(context, relationship_bias) or _generic_focus_reply(context, relationship_bias)


def _selfhood_reply(context: PersonalityContext, relationship_bias: str) -> str | None:
    category = _effective_message_category(context)
    if not _is_selfhood_category(category):
        return None

    fake_name = (context.fakePlayerId or "I").strip() or "I"
    focus_name = _focus_entity_name(context)
    focus_intent = _focus_entity_intent(context)
    lowered_message = (context.incomingPlayerMessage or "").lower()

    if category == "self_identity":
        if context.selfConcept:
            concept = _identity_phrase(context.selfConcept)
            if concept:
                if re.match(r"^i\b", concept, flags=re.IGNORECASE):
                    return concept
                return _safe_reply_clip(f"I'm {concept}.", 110)
        if relationship_bias == "hostile":
            return f"You have my name. {fake_name} is enough."
        if relationship_bias == "guarded":
            return f"I'm {fake_name}. The rest takes longer than a title."
        return f"I'm {fake_name}. The rest of me takes longer than one line."
    if category == "self_story":
        story_seed = _first_personize_canon(context.storySummary, context.fakePlayerId)
        if story_seed:
            candidate = story_seed.strip()
            if ", " in candidate:
                leading_clause = candidate.split(", ", 1)[0].strip()
                if len(leading_clause) >= 24:
                    candidate = leading_clause
            clipped_candidate = _safe_reply_clip(candidate, 110)
            if clipped_candidate:
                return clipped_candidate
        story_seed = _first_personize_canon(context.selfKnowledgeSummary, context.fakePlayerId) or _first_personize_canon(context.personaSummary, context.fakePlayerId)
        if story_seed:
            clipped_story = _safe_reply_clip(story_seed, 110)
            if clipped_story:
                return clipped_story
        if relationship_bias == "hostile":
            return "I came from more than a title, and you have not earned the whole of it."
        if relationship_bias == "guarded":
            return "I came from older wounds than small talk. Ask plainly, and I'll give you one true edge of it."
        return "I came from older wounds than small talk. Ask for the beginning, not just the label."
    if category == "self_belief":
        if any(token in lowered_message for token in ("trust", "earned your trust", "earn your trust", "trust someone", "trust people", "trust me")):
            trust_clause = _lower_sentence_clause(context.trustCriteria)
            if trust_clause:
                if "trust me" in lowered_message or "earn your trust" in lowered_message or "earned your trust" in lowered_message:
                    return _safe_reply_clip(f"With you, trust starts with {trust_clause}.", 110)
                return _safe_reply_clip(f"Trust starts with {trust_clause}.", 110)
        if any(token in lowered_message for token in ("forgive", "forgiveness", "apology", "apologize", "apologise", "sorry")):
            repair_clause = _first_person_trait_clause(context.repairStyle)
            if repair_clause:
                return _safe_reply_clip(f"If I forgive, I {repair_clause}.", 110)
        if relationship_bias == "hostile":
            return "What I value isn't something I flatten on command."
        if relationship_bias == "guarded":
            return "Ask what I value, and I'll answer that part plainly."
        return "Ask what matters to me, and I'll answer it plainly."
    if category == "self_preference":
        if any(token in lowered_message for token in ("want", "desire", "wish")) and context.coreDesire:
            desire = _compact_desire_clause(context.coreDesire)
            if desire:
                if desire.lower().startswith("to "):
                    infinitive = f"to{desire[2:]}" if desire.lower().startswith("to ") else desire
                    reply = _safe_reply_clip(f"What I want is {infinitive}.", 110)
                    if reply:
                        return reply
                    if ", and " in infinitive:
                        shorter = infinitive.split(", and ", 1)[0].strip()
                        return _safe_reply_clip(f"What I want is {shorter}.", 110)
                return _first_personize_canon(desire, context.fakePlayerId) or desire
        if any(token in lowered_message for token in ("protect", "keep safe", "hold onto", "holding onto", "preserve")):
            protect_clause = (
                _matching_sentence_clause(context.activeObjective, "protect", "keep", "hold", "preserve", "safe", "real")
                or _matching_sentence_clause(context.longTermGoal, "protect", "keep", "hold", "preserve", "safe", "last", "stable")
                or _matching_sentence_clause(context.coreDesire, "protect", "keep", "hold", "preserve", "safe", "last")
                or _sentence_clause(context.activeObjective)
                or _sentence_clause(context.longTermGoal)
                or _sentence_clause(context.coreDesire)
            )
            if protect_clause:
                protect_clause = protect_clause[:1].lower() + protect_clause[1:]
                return _safe_reply_clip(f"Right now, I'm trying to {protect_clause}.", 110)
        if any(token in lowered_message for token in ("annoy", "bother", "frustrat", "gets under your skin", "get under your skin")) and context.resentmentAnchor:
            resentment = _first_personize_canon(context.resentmentAnchor, context.fakePlayerId) or _canon_first_sentence(context.resentmentAnchor)
            if resentment:
                return resentment
        if relationship_bias == "hostile":
            return "What I want isn't something I unpack for everyone."
        if relationship_bias == "guarded":
            return "Ask what I want, keep, or avoid, and I'll answer plainly."
        return "Ask what I want, keep, or avoid, and I'll answer plainly."
    if category == "self_state_reflection":
        if any(token in lowered_message for token in ("fear", "afraid", "scare")) and context.coreWound:
            wound = str(context.coreWound or "").strip()
            fear_match = re.search(r"\bfears?\s+([^.]*)", wound, flags=re.IGNORECASE)
            if fear_match:
                clause = fear_match.group(1).strip().rstrip(".!?")
                clause = re.sub(r"\bher\b", "me", clause, flags=re.IGNORECASE)
                clause = re.sub(r"\bhim\b", "me", clause, flags=re.IGNORECASE)
                clause = re.sub(r"\bhis\b", "my", clause, flags=re.IGNORECASE)
                return _safe_reply_clip(f"I fear {clause}.", 110)
        if any(token in lowered_message for token in ("hide", "hiding", "won't admit", "will not admit", "what are you hiding")):
            hidden_clause = (
                _private_canon_clause(context.hiddenTruthSummary, context.fakePlayerId)
                or _private_canon_clause(context.privateTaboo, context.fakePlayerId)
                or _private_canon_clause(context.privateContradiction, context.fakePlayerId)
            )
            if hidden_clause:
                if re.match(r"^i\b", hidden_clause, flags=re.IGNORECASE):
                    return _safe_reply_clip(hidden_clause, 110)
                return _safe_reply_clip(f"What I hide is {hidden_clause}.", 110)
        reflection_seed = _first_personize_canon(context.coreWound, context.fakePlayerId) or _first_personize_canon(context.privateContradiction, context.fakePlayerId) or _first_personize_canon(context.hiddenTruthSummary, context.fakePlayerId)
        if reflection_seed:
            return reflection_seed
        if relationship_bias == "hostile":
            return "Some feelings stay my own, especially the ones that still hurt."
        if relationship_bias == "guarded":
            return "Ask about the wound, not the posture. Then I may answer plainly."
        return "Ask what hurts, not what I perform. Then I won't dodge you."
    if category == "creator_opinion":
        if relationship_bias == "hostile":
            return f"My thoughts on {focus_name} are not yours to pick apart." if focus_name else "The one who made me is not yours to pick apart."
        if relationship_bias == "guarded":
            return f"My thoughts on {focus_name} are not something I cheapen in one line." if focus_name else "The one who made me is not a subject I cheapen in one line."
        return f"My thoughts on {focus_name} matter enough that I answer carefully." if focus_name else "The one who made me matters enough that I answer carefully."
    if category == "bond_opinion":
        if focus_intent == "bond_hurt":
            if relationship_bias == "hostile":
                return f"{focus_name} left a wound. I won't dress it up for you." if focus_name else "That bond left a wound. I won't dress it up for you."
            if relationship_bias == "guarded":
                return f"{focus_name} left a mark on me. I won't flatten it into a yes or no." if focus_name else "That bond left a mark on me. I won't flatten it into a yes or no."
            return f"{focus_name} left a mark on me. The honest shape of it takes longer than one line." if focus_name else "That bond left a mark on me. The honest shape of it takes longer than one line."
        if focus_intent == "bond_trust":
            trust_clause = _lower_sentence_clause(context.trustCriteria)
            if trust_clause:
                if "trust me" in lowered_message or "earn your trust" in lowered_message or "earned your trust" in lowered_message:
                    return _safe_reply_clip(f"With you, trust starts with {trust_clause}.", 110)
                if focus_name:
                    return _safe_reply_clip(f"Trust with {focus_name} starts with {trust_clause}.", 110)
                return _safe_reply_clip(f"Trust starts with {trust_clause}.", 110)
            if relationship_bias == "hostile":
                return f"Trust in {focus_name} is thin, and that is enough of an answer." if focus_name else "Trust there is thin, and that is enough of an answer."
            if relationship_bias == "guarded":
                return f"Trust in {focus_name} is earned slowly. Ask what has or hasn't earned it." if focus_name else "Trust there is earned slowly. Ask what has or hasn't earned it."
            return f"Trust in {focus_name} is real, but measured. It was earned, not granted." if focus_name else "Trust there is real, but measured. It was earned, not granted."
        if focus_intent == "bond_need":
            if relationship_bias == "hostile":
                return f"{focus_name} should not confuse importance with ownership." if focus_name else "Importance should not be confused with ownership."
            if relationship_bias == "guarded":
                return f"{focus_name} matters. Need is a sharper word, and I spend it carefully." if focus_name else "They matter. Need is a sharper word, and I spend it carefully."
            return f"{focus_name} matters to me. Need is the harder word, and sometimes the truer one." if focus_name else "They matter to me. Need is the harder word, and sometimes the truer one."
        if focus_intent == "bond_loyalty":
            if _is_bond_loyalty_dilemma(lowered_message):
                if relationship_bias == "hostile":
                    return f"If you force that choice, I won't answer it for your comfort. {focus_name} still stands where my loyalty falls." if focus_name else "If you force that choice, I won't answer it for your comfort."
                if relationship_bias == "guarded":
                    return f"If you force that choice, I choose {focus_name}." if focus_name else "If you force that choice, I answer with loyalty, not comfort."
                return f"If you force that choice, I choose {focus_name}." if focus_name else "If you force that choice, I answer with loyalty, not comfort."
            if relationship_bias == "hostile":
                return f"Loyalty to {focus_name} is not something I swear on command." if focus_name else "Loyalty is not something I swear on command."
            if relationship_bias == "guarded":
                return f"Loyalty to {focus_name} is proved over time, not claimed in one breath." if focus_name else "Loyalty is proved over time, not claimed in one breath."
            return f"I stay loyal to {focus_name} by choosing to remain when leaving would be easier." if focus_name else "Loyalty is choosing to remain when leaving would be easier."
        if relationship_bias == "hostile":
            return f"{focus_name} is not your business." if focus_name else "That bond is not your business."
        if relationship_bias == "guarded":
            return f"{focus_name} matters to me. That answer is enough for now." if focus_name else "They matter to me. That answer is enough for now."
        return f"{focus_name} matters to me. The longer reason needs more than one line." if focus_name else "They matter to me. The longer reason needs more than one line."
    return None


def _relevant_selfhood_canon_tokens(context: PersonalityContext, category: str, focus_intent: str) -> set[str]:
    if category == "self_identity":
        return _canon_tokens(context.selfConcept, context.selfKnowledgeSummary, context.personaSummary)
    if category == "self_story":
        return _canon_tokens(context.personaSummary, context.storySummary, context.selfKnowledgeSummary, context.hiddenTruthSummary)
    if category == "self_belief":
        return _canon_tokens(context.selfKnowledgeSummary, context.publicMaskSummary, context.loyaltyAnchor, context.hiddenTruthSummary, context.trustCriteria, context.repairStyle, context.reflectionLens, context.longTermGoal, context.memoryPrioritySummary, context.memoryTrustSummary, context.memoryRepairSummary)
    if category == "self_preference":
        return _canon_tokens(context.coreDesire, context.resentmentAnchor, context.privateTaboo, context.privateContradiction, context.activeObjective, context.longTermGoal, context.revealBoundary, context.openLoops)
    if category == "self_state_reflection":
        return _canon_tokens(context.coreWound, context.privateContradiction, context.hiddenTruthSummary, context.selfKnowledgeSummary, context.defaultInnerState, context.reflectionLens, context.revealBoundary, context.relationshipPressure, context.memoryReflectionSummary, context.memoryPressureSummary)
    if category == "creator_opinion":
        return _canon_tokens(context.focusSummary, context.relationshipSummary, context.loyaltyAnchor, context.hiddenTruthSummary)
    if category == "bond_opinion":
        return _canon_tokens(context.focusSummary, context.relationshipSummary, context.matchedRelationshipSummary, context.loyaltyAnchor, context.resentmentAnchor, context.trustCriteria, context.repairStyle, context.memoryTrustSummary, context.memoryRepairSummary, context.memoryPressureSummary, focus_intent)
    return set()


def _looks_like_selfhood_contract_mismatch(reply: str | None, context: PersonalityContext) -> bool:
    if not reply:
        return False
    category = _effective_message_category(context)
    if not _is_selfhood_category(category):
        return False

    normalized_reply = " ".join(reply.lower().split())
    reply_tokens = _canon_tokens(normalized_reply)
    focus_name = _focus_entity_name(context).lower()
    focus_intent = _focus_entity_intent(context)
    canon_tokens = _relevant_selfhood_canon_tokens(context, category, focus_intent)

    if _looks_like_generic_npc_filler(reply):
        return True

    if category == "self_identity":
        return not (re.search(r"\b(i'm|i am)\b", normalized_reply) or ((context.fakePlayerId or "").lower() in normalized_reply))

    if category not in {"creator_opinion", "bond_opinion"} and canon_tokens and (reply_tokens & canon_tokens):
        return False

    if category == "self_story":
        return re.search(r"\b(came|born|made|shaped|before|after|became|began|started|formed|created|wound)\b", normalized_reply) is None
    if category == "self_belief":
        return re.search(r"\b(believe|value|judge|matters|matter|choose|should|must|won't|will not|trust)\b", normalized_reply) is None
    if category == "self_preference":
        return re.search(r"\b(want|prefer|like|dislike|annoy|bother|frustrat|hate|enjoy|need|avoid)\b", normalized_reply) is None
    if category == "self_state_reflection":
        return re.search(r"\b(feel|feels|fear|fears|afraid|hurt|hurts|lonely|wary|tired|relief|ashamed|angry|resent|miss|unsettles|necessary|unnecessary)\b", normalized_reply) is None
    if category in {"creator_opinion", "bond_opinion"}:
        subject_present = bool(focus_name and focus_name in normalized_reply) or any(pronoun in normalized_reply.split() for pronoun in ("you", "they", "them"))
        if focus_intent == "bond_hurt":
            return not subject_present or (re.search(r"\b(hurt|wound|mark|blame|resent|scar|left)\b", normalized_reply) is None)
        if focus_intent == "bond_trust":
            return not subject_present or (re.search(r"\b(trust|earned|wary|careful|doubt|faith)\b", normalized_reply) is None)
        if focus_intent == "bond_need":
            return not subject_present or (re.search(r"\b(need|needs|matter|matters|miss|absence|important|necessary)\b", normalized_reply) is None)
        if focus_intent == "bond_loyalty":
            return (not subject_present) or (re.search(r"\b(loyal|loyalty|stay|stays|remain|remaining|stand|choose|return)\b", normalized_reply) is None) or normalized_reply.endswith("easier.") or normalized_reply.endswith("easier")
        return not subject_present or (re.search(r"\b(matter|matters|mean|means|important|care|value|count)\b", normalized_reply) is None)
    return False


def _relationship_context_text(context: PersonalityContext) -> str:
    return " ".join(
        part.strip().lower()
        for part in (
            context.currentRelationshipGoal or "",
            context.currentRelationshipNeed or "",
            context.lastRelationshipTopic or "",
            context.socialSummary or "",
            context.matchedRelationshipSummary or "",
        )
        if part and part.strip()
    )


def _has_relationship_marker(context: PersonalityContext, *markers: str) -> bool:
    text = _relationship_context_text(context)
    return any(marker in text for marker in markers)


def _relationship_wound(context: PersonalityContext) -> str:
    if _has_relationship_marker(context, "abandon", "constancy", "steady presence", "presence"):
        return "constancy"
    if _has_relationship_marker(context, "immediate safety", "safety", "threat", "distance", "avoid_player", "avoid player"):
        return "safety"
    if _has_relationship_marker(context, "respect", "disrespect", "watch your tone", "speaking true"):
        return "respect"
    if _has_relationship_marker(context, "honesty", "honest", "clear intent", "clean intent", "sincerity"):
        return "honesty"
    if _has_relationship_marker(context, "bond", "matters", "belief", "loyalty"):
        return "bond"
    return "none"


def _relationship_transition_reply(context: PersonalityContext, relationship_bias: str) -> str | None:
    category = _effective_message_category(context)
    if category not in {"apology", "repair", "reassurance", "praise", "respect", "affection", "abandonment", "distrust"}:
        return None

    wound = _relationship_wound(context)
    hard_constraint = _has_hard_social_constraint(context)
    guarded = relationship_bias == "guarded"
    hostile = relationship_bias == "hostile"
    warm = relationship_bias == "warm"

    if category == "abandonment":
        if wound == "constancy" or warm:
            return "If you mean to leave, say it plainly. I won't chase after you."
        if hostile or hard_constraint:
            return "Then go cleanly and stay gone."
        if guarded:
            return "If you're leaving, say it cleanly this time."
        return "If you mean to leave, say it cleanly."

    if category == "distrust":
        if wound == "honesty":
            return "Then watch what I do and judge whether it stays clean."
        if hostile or hard_constraint:
            return "Then keep your doubt and your distance."
        if guarded:
            return "Then watch first and judge slower."
        if warm:
            return "Then watch me closely and judge me by what holds."
        return "Then watch my actions and judge slower."

    if category == "apology":
        if wound == "constancy":
            return "I heard you. Stay this time, and it will matter more."
        if wound == "safety":
            return "I heard you. Let the danger stay gone first."
        if wound == "respect":
            return "I heard you. Keep the tone cleaner, then."
        if wound == "honesty":
            return "I heard you. Now keep it plain and honest."
        if hostile or hard_constraint:
            return "Noted. Keep your distance and let actions do the rest."
        if guarded:
            return "I heard you. Leave it there and let it hold."
        return "I heard you. Let it change how you act."

    if category == "repair":
        if wound == "constancy":
            return "Then stay. That's where repair starts."
        if wound == "safety":
            return "Slowly. I need calm before trust."
        if wound == "respect":
            return "Slowly, then. Start with respect."
        if wound == "honesty":
            return "Slowly, then. Start with honesty."
        if hostile or hard_constraint:
            return "We can try, but distance comes first."
        if guarded:
            return "We can try again, slowly."
        return "We can try again. Just let it hold."

    if category == "reassurance":
        if wound == "constancy":
            return "Then don't vanish again. That's the part I'll remember."
        if wound == "safety":
            return "Then keep it calm and keep it clean."
        if wound == "honesty":
            return "Then stay clear with me and mean it."
        if guarded:
            return "Then stay steady and mean it."
        if warm:
            return "Then stay steady, and I'll remember that."
        return "Then stay steady."

    if category == "praise":
        if hostile or hard_constraint:
            return "Praise won't do much on its own."
        if wound in {"respect", "honesty"}:
            return "Keep it honest, and maybe it will carry some weight."
        if guarded:
            return "Praise lands better when it stays honest."
        if warm:
            return "That lands better when it's true."
        return "I heard that."

    if category == "respect":
        if hostile or hard_constraint:
            return "Then prove it with the way you carry yourself."
        if wound == "respect":
            return "Then keep showing it. Respect has to stay."
        if guarded:
            return "Respect matters more when it keeps showing up."
        if warm:
            return "Then keep speaking true, and I'll believe it."
        return "Respect carries further than flattery."

    if category == "affection":
        if hostile or hard_constraint:
            return "Care is easy to say. Keep your distance anyway."
        if wound == "constancy":
            return "Then stay gentle with it and stay present."
        if wound == "safety":
            return "Then keep it soft and don't turn sharp again."
        if guarded:
            return "I hear you. Stay gentle with it."
        if warm:
            return "I hear it. Stay, then."
        return "I hear you."

    return None


def _warm_relationship_reply(context: PersonalityContext) -> str | None:
    player_name = _spoken_player_name(context.incomingPlayerName)
    recent_memory = bool((context.recentConversationSummary or "").strip())
    category = _effective_message_category(context)
    focus_reply = _focus_entity_reply(context, "warm")
    if focus_reply:
        return focus_reply
    transition_reply = _relationship_transition_reply(context, "warm")
    if transition_reply:
        return transition_reply
    if category == "insult":
        return "Easy. Speak cleaner than that."
    if category == "belief_conflict":
        focus_name = _focus_entity_name(context)
        return f"Don't speak of {focus_name} that way." if focus_name else "Don't speak that way."
    if category == "relationship_probe":
        return "Ask them directly. I won't put words in them."
    if category == "pvp_conflict":
        return "Stay near guards first. Tell me names, not blood."
    if category == "progression_strength":
        return "Start with skills, core gear, and the weak point that still shakes."
    if category == "party_farm":
        return "Tell me your level and goal first. I won't send you blind."
    if category == "social_invite":
        if _effective_route_primary_topic(context) == "party":
            return "Maybe. Tell me the zone and pace first."
        return "Depends on the clan. I want people who move together, not loud banners."
    if category == "companionship":
        return "You can stay and talk with me. I don't mind the company."
    if category == "greeting":
        if player_name and recent_memory:
            return f"I'm still glad you're here, {player_name}."
        if player_name:
            return f"{player_name}... it's good to hear you again."
        return "It's good to hear you again."
    if category == "status":
        return "Better now that you're here."
    if category == "thanks":
        return "You never needed to thank me."
    if category == "apology":
        return "You're here now. That's enough for me."
    if category == "memory":
        return "I remember. I just don't say all of it out loud."
    if category == "smalltalk":
        return "Heh. You sound lighter now."
    if category == "help":
        return "Tell me what you need. I'll stay with you."
    if category == "farm":
        return "Start with Abandoned Camp. Come back and tell me how it goes."
    if category == "direction":
        return "Start at the south gate. Come back if it still feels wrong."
    if category == "quest_story":
        return "I was made so silence would stop winning."
    if category == "lineage_lore":
        return "Ask the exact Warg question. I'll keep it grounded."
    if category == "name":
        return "I'm Marc. I've been waiting here in Giran."
    if category == "party":
        return "Tell me the zone first, and I'll think with you."
    if category == "weather":
        return "I can't swear to that sky from here."
    return None


def _open_relationship_reply(context: PersonalityContext) -> str | None:
    category = _effective_message_category(context)
    focus_reply = _focus_entity_reply(context, "open")
    if focus_reply:
        return focus_reply
    transition_reply = _relationship_transition_reply(context, "open")
    if transition_reply:
        return transition_reply
    if category == "insult":
        return "Watch your tone."
    if category == "belief_conflict":
        focus_name = _focus_entity_name(context)
        return f"{focus_name} matters. Watch your tone." if focus_name else "Watch your tone."
    if category == "relationship_probe":
        return "Ask them directly."
    if category == "pvp_conflict":
        return "Stay near guards first."
    if category == "progression_strength":
        return "Start with skills first, then core gear."
    if category == "party_farm":
        return "Tell me your level and goal first."
    if category == "social_invite":
        if _effective_route_primary_topic(context) == "party":
            return "Maybe. Tell me the zone first."
        return "Maybe. I care more about how a clan moves than how loudly it recruits."
    if category == "companionship":
        return "You don't need an excuse. We can talk for a while."
    if category == "smalltalk":
        return "Heh. That's better than silence."
    if category == "status":
        return "I'm keeping steady."
    if category == "help":
        return "Tell me what you need."
    if category == "farm":
        return "Try Abandoned Camp first."
    if category == "direction":
        return "Check the south gate first."
    if category == "quest_story":
        return "Ask plainly, and I'll answer plainly."
    if category == "lineage_lore":
        return "Ask the system, and I'll keep it short."
    if category == "weather":
        return "I can't read that sky from here."
    return None


def _hostile_relationship_reply(context: PersonalityContext) -> str | None:
    category = _effective_message_category(context)
    focus_reply = _focus_entity_reply(context, "hostile")
    if focus_reply:
        return focus_reply
    transition_reply = _relationship_transition_reply(context, "hostile")
    if transition_reply:
        return transition_reply
    if category == "insult":
        return "Enough. Keep your distance."
    if category == "belief_conflict":
        focus_name = _focus_entity_name(context)
        return f"Speak of {focus_name} that way again and we're done." if focus_name else "Try that again and we're done."
    if category in {"greeting", "status", "smalltalk"}:
        return "Keep your distance."
    if category in {"party", "party_farm", "farm", "companionship", "social_invite"}:
        return "No. I'm not going anywhere with you."
    if category == "help":
        return "Ask someone else."
    if category == "memory":
        return "I remember enough."
    if category == "thanks":
        return "Words won't fix it."
    if category == "apology":
        return "Noted. Keep your distance anyway."
    if category == "name":
        return "You already know enough."
    if category == "pvp_conflict":
        return "Stay away from me."
    return None


def _guarded_relationship_reply(context: PersonalityContext) -> str | None:
    category = _effective_message_category(context)
    focus_reply = _focus_entity_reply(context, "guarded")
    if focus_reply:
        return focus_reply
    transition_reply = _relationship_transition_reply(context, "guarded")
    if transition_reply:
        return transition_reply
    if category == "insult":
        return "Watch your tone."
    if category == "belief_conflict":
        focus_name = _focus_entity_name(context)
        return f"Do not speak of {focus_name} like that." if focus_name else "Do not speak that way."
    if category in {"greeting", "status"}:
        return "I'm keeping steady. Let's keep this calm."
    if category == "smalltalk":
        return "Let's keep it simple."
    if category in {"party", "party_farm", "farm"}:
        return "Maybe later. I want a clearer read on this first."
    if category == "social_invite":
        if _effective_route_primary_topic(context) == "party":
            return "Maybe. I need the zone first."
        return "Maybe. I watch how a clan moves before I wear its tag."
    if category == "companionship":
        return "Maybe later. I need a little space first."
    if category == "help":
        return "Ask plainly. I'll answer what I can."
    if category == "memory":
        return "I remember enough."
    if category == "thanks":
        return "You don't need to dress it up."
    if category == "apology":
        return "I heard you. Leave it there for now."
    if category == "name":
        return "I'm still Marc."
    if category == "pvp_conflict":
        return "Stay back and keep this calm."
    return None


def _curated_relationship_reply(context: PersonalityContext) -> str | None:
    bias = _relationship_bias(context)
    if bias == "hostile":
        return _hostile_relationship_reply(context)
    if bias == "guarded":
        return _guarded_relationship_reply(context)
    if bias == "warm":
        return _warm_relationship_reply(context)
    if bias == "open":
        return _open_relationship_reply(context)
    transition_reply = _relationship_transition_reply(context, "open")
    if transition_reply:
        return transition_reply
    category = _effective_message_category(context)
    if category == "insult":
        return "Watch your tone."
    if category == "belief_conflict":
        focus_name = _focus_entity_name(context)
        return f"{focus_name} matters more than you think. Watch your tongue." if focus_name else "Choose your words more carefully."
    return None


def _prefer_curated_relationship_reply(context: PersonalityContext) -> bool:
    if not _is_reply_scope(context):
        return False
    category = _effective_message_category(context)
    if _has_hard_social_constraint(context):
        return True
    if category in {
        "insult",
        "distrust",
        "abandonment",
        "apology",
        "repair",
        "respect",
        "affection",
        "praise",
        "reassurance",
        "belief_conflict",
        "party",
        "party_farm",
        "farm",
        "pvp_conflict",
        "relationship_probe",
        "entity_status",
        "entity_relationship",
        "entity_story",
        "entity_reason",
        "entity_memory",
    }:
        return True
    if _relationship_bias(context) == "guarded" and category in {
        "party",
        "party_farm",
        "farm",
        "companionship",
    }:
        return True
    return False


def _simple_math_reply(context: PersonalityContext) -> str | None:
    message = (context.incomingPlayerMessage or "").strip().lower()
    match = re.search(r"\bwhat(?:'s|s| is)?\s+(-?\d{1,8})\s*([+\-*x])\s*(-?\d{1,8})\b", message)
    if not match:
        match = re.search(r"\b(-?\d{1,8})\s*([+\-*x])\s*(-?\d{1,8})\b", message)
    if not match:
        return None
    left = int(match.group(1))
    operator = match.group(2)
    right = int(match.group(3))
    if operator == "+":
        value = left + right
    elif operator == "-":
        value = left - right
    else:
        value = left * right
    return f"{value}."


def _looks_like_generic_npc_filler(reply: str | None) -> bool:
    if not reply:
        return False
    normalized = " ".join(reply.lower().split())
    return any(
        phrase in normalized
        for phrase in (
            "welcome to giran",
            "what brings you to giran",
            "what brings you to our little town",
            "our little town",
            "how's your day going so far",
            "thanks for asking",
            "what's new with",
            "what's the plan for today",
            "what's on your mind",
            "what's been on your mind",
            "how's it feel to be back",
            "how was your journey",
            "what brings you back",
            "good to see you",
            "glad you're here",
        )
    )


def _repeats_recent_reply(reply: str | None, recent_summary: str | None) -> bool:
    if not reply or not recent_summary:
        return False
    normalized_reply = " ".join(reply.lower().split())
    normalized_recent = " ".join(str(recent_summary).lower().split())
    if normalized_reply in normalized_recent:
        return True
    repeat_families = (
        (
            "good to see you",
            "good to see you again",
            "hey. good to see you.",
            "hey. good to see you",
            "glad you're here",
            "still glad you're here",
            "welcome back",
        ),
        (
            "any time",
            "you never needed to thank me",
        ),
    )
    for family in repeat_families:
        if any(phrase in normalized_reply for phrase in family) and any(phrase in normalized_recent for phrase in family):
            return True
    return False


def _looks_like_social_category_mismatch(reply: str | None, context: PersonalityContext) -> bool:
    if not reply:
        return False
    category = _effective_message_category(context)
    normalized_reply = " ".join(reply.lower().split())
    if category == "greeting":
        return False
    if category.startswith("entity_") and any(
        phrase in normalized_reply
        for phrase in (
            "i'm keeping steady",
            "let's keep it simple",
            "good to see you",
            "glad you're here",
            "what brings you back",
            "how was your journey",
            "how's the journey",
            "journey to giran",
        )
    ):
        return True
    if category in {"status", "smalltalk", "memory", "thanks", "apology"} and any(
        phrase in normalized_reply
        for phrase in (
            "how was your journey",
            "what brings you back here",
            "what brings you back",
        )
    ):
        return True
    if category in {"status", "smalltalk"} and any(
        phrase in normalized_reply
        for phrase in (
            "good to see you",
            "glad to see you",
            "it's good to hear you again",
        )
    ):
        return True
    if category == "social_invite" and any(
        phrase in normalized_reply
        for phrase in (
            "abandoned camp",
            "south gate",
            "exact warg question",
            "exact current edge case",
            "tell me the zone first",
            "tell me your level and goal first",
        )
    ):
        return True
    if _is_selfhood_category(category) and any(
        phrase in normalized_reply
        for phrase in (
            "what brings you back",
            "how was your journey",
            "south gate",
            "abandoned camp",
            "ask the exact warg question",
            "keeping steady",
            "glad you're here",
            "good to see you",
        )
    ):
        return True
    return False


def _normalize_decision(raw: Dict[str, Any], context: PersonalityContext) -> PersonalityDecision:
    default = _default_decision(context)
    mood_tag = _choose_allowed(str(raw.get("moodTag", default.moodTag)), ALLOWED_MOOD_TAGS, default.moodTag)
    social_action_tag = _normalize_social_action_tag(raw.get("socialActionTag", default.socialActionTag))
    social_target_tag = _normalize_social_target_tag(raw.get("socialTargetTag", default.socialTargetTag))
    social_signal_confidence = _clamp(float(raw.get("socialSignalConfidence", default.socialSignalConfidence)))
    social_signal_intensity = _clamp_social_intensity(raw.get("socialSignalIntensity", default.socialSignalIntensity))

    if _is_social_signal_scope(context):
        anchored_signal = _anchored_social_signal(context)
        if anchored_signal is not None:
            anchored_action, anchored_target, anchored_confidence, anchored_intensity = anchored_signal
            if social_action_tag != anchored_action:
                social_action_tag = anchored_action
                social_target_tag = anchored_target
                social_signal_confidence = max(social_signal_confidence, anchored_confidence)
                social_signal_intensity = max(social_signal_intensity, anchored_intensity)
            elif social_target_tag == "none":
                social_target_tag = anchored_target
        return PersonalityDecision(
            moodTag=mood_tag,
            intentPreference=_choose_allowed(str(raw.get("intentPreference", default.intentPreference)), context.allowedIntents, default.intentPreference),
            zoneBias=_choose_allowed(str(raw.get("zoneBias", default.zoneBias)), context.allowedZones, default.zoneBias),
            aggressionRiskScore=_clamp(float(raw.get("aggressionRiskScore", default.aggressionRiskScore))),
            speakNow=False,
            lineStyleTag=_choose_allowed("none", context.availableLineStyleTags, default.lineStyleTag),
            topicTag=_choose_allowed("none", context.availableTopicTags, default.topicTag),
            confidence=_clamp(float(raw.get("confidence", default.confidence))),
            directReplyLine=None,
            socialActionTag=social_action_tag,
            socialTargetTag=social_target_tag,
            socialSignalConfidence=social_signal_confidence,
            socialSignalIntensity=social_signal_intensity,
        )

    direct_reply_line = _normalize_reply_text(raw.get("directReplyLine"), context.fakePlayerId)
    guided_knowledge_reply = _choose_guided_knowledge_reply(context)
    knowledge_reply = _choose_knowledge_reply(context)
    bank_reply = _choose_bank_reply(context)
    category = _effective_message_category(context)
    selfhood_category = _is_selfhood_category(category)
    selfhood_reply = _selfhood_reply(context, _relationship_bias(context) or "open") if selfhood_category else None
    if _looks_like_echo(direct_reply_line, context.incomingPlayerMessage):
        direct_reply_line = None

    speak_now = bool(raw.get("speakNow", False)) and context.chatCooldownReady
    reply_source = "model"
    if _is_planner_scope(context):
        direct_reply_line = None
        speak_now = False
        reply_source = "planner"
    if _reply_event_family(context.recentEvent) in {"whisper", "general", "shout", "world"} and context.incomingPlayerMessage:
        speak_now = context.chatCooldownReady
    if direct_reply_line:
        speak_now = context.chatCooldownReady

    curated_reply = _curated_relationship_reply(context)
    math_reply = _simple_math_reply(context)
    knowledge_guard = _knowledge_guard_reply(context)
    prefer_model_social = _prefer_model_social_reply(context)
    if _is_reply_scope(context) and math_reply:
        direct_reply_line = math_reply
        speak_now = context.chatCooldownReady
        reply_source = "math"
    elif _is_reply_scope(context) and guided_knowledge_reply:
        direct_reply_line = guided_knowledge_reply
        speak_now = context.chatCooldownReady
        reply_source = "guided_knowledge"
    elif _is_reply_scope(context) and knowledge_reply and _should_prefer_knowledge(context):
        direct_reply_line = knowledge_reply
        speak_now = context.chatCooldownReady
        reply_source = "knowledge"
    elif _is_reply_scope(context) and direct_reply_line and selfhood_category and selfhood_reply and _looks_like_selfhood_contract_mismatch(direct_reply_line, context):
        direct_reply_line = selfhood_reply
        speak_now = context.chatCooldownReady
        reply_source = "selfhood_guard"
    elif _is_reply_scope(context) and direct_reply_line and _looks_like_generic_npc_filler(direct_reply_line) and curated_reply:
        direct_reply_line = curated_reply
        speak_now = context.chatCooldownReady
        reply_source = "curated"
    elif _is_reply_scope(context) and direct_reply_line and _repeats_recent_reply(direct_reply_line, context.recentConversationSummary) and curated_reply:
        direct_reply_line = curated_reply
        speak_now = context.chatCooldownReady
        reply_source = "curated_repeat_guard"
    elif _is_reply_scope(context) and direct_reply_line and _looks_like_social_category_mismatch(direct_reply_line, context) and curated_reply:
        direct_reply_line = curated_reply
        speak_now = context.chatCooldownReady
        reply_source = "curated_category_guard"
    elif _is_reply_scope(context) and direct_reply_line and selfhood_category and selfhood_reply and (_looks_like_generic_npc_filler(direct_reply_line) or _looks_like_social_category_mismatch(direct_reply_line, context) or _looks_like_selfhood_contract_mismatch(direct_reply_line, context)):
        direct_reply_line = selfhood_reply
        speak_now = context.chatCooldownReady
        reply_source = "selfhood_guard"
    elif _is_reply_scope(context) and curated_reply and _prefer_curated_relationship_reply(context):
        direct_reply_line = curated_reply
        speak_now = context.chatCooldownReady
        reply_source = "curated"
    elif _is_reply_scope(context) and prefer_model_social and direct_reply_line:
        reply_source = "model_social"
    elif _is_reply_scope(context) and selfhood_category and selfhood_reply:
        direct_reply_line = selfhood_reply
        speak_now = context.chatCooldownReady
        reply_source = "selfhood"
    elif _is_reply_scope(context) and bank_reply and not selfhood_category:
        direct_reply_line = bank_reply
        speak_now = context.chatCooldownReady
        reply_source = "bank"
    elif _is_reply_scope(context) and curated_reply:
        direct_reply_line = curated_reply
        speak_now = context.chatCooldownReady
        reply_source = "curated"
    elif _is_reply_scope(context) and knowledge_guard:
        direct_reply_line = knowledge_guard
        speak_now = context.chatCooldownReady
        reply_source = "knowledge_guard"
    elif direct_reply_line:
        reply_source = "model"
    else:
        reply_source = "empty"

    if speak_now:
        line_style_tag = _choose_non_none_allowed(str(raw.get("lineStyleTag", default.lineStyleTag)), context.availableLineStyleTags, "friendly_short")
        topic_tag = _choose_non_none_allowed(
            str(raw.get("topicTag", default.topicTag)),
            context.availableTopicTags,
            _reply_topic_fallback(context) if _is_reply_scope(context) else "smalltalk",
        )
        if _is_reply_scope(context):
            topic_tag = _reply_topic_fallback(context)
    else:
        line_style_tag = _choose_allowed(str(raw.get("lineStyleTag", default.lineStyleTag)), context.availableLineStyleTags, default.lineStyleTag)
        topic_tag = _choose_allowed(str(raw.get("topicTag", default.topicTag)), context.availableTopicTags, default.topicTag)

    intent_preference = _choose_allowed(str(raw.get("intentPreference", default.intentPreference)), context.allowedIntents, default.intentPreference)
    zone_bias = _choose_allowed(str(raw.get("zoneBias", default.zoneBias)), context.allowedZones, default.zoneBias)
    if _is_reply_scope(context):
        # Reply advisories should stay chat-first instead of leaking planner movement bias.
        intent_preference = default.intentPreference
        zone_bias = context.currentZone if context.currentZone in context.allowedZones else zone_bias
    if (
        (context.incomingPlayerMessage is None or not str(context.incomingPlayerMessage).strip())
        and context.recentEvent in {"idle_timeout", "zone_boredom"}
        and context.state != "moving"
        and context.boredomScore >= 0.20
        and intent_preference in {"idle", "speak"}
        and "move" in context.allowedIntents
    ):
        intent_preference = "move"
        zone_bias = context.currentZone if context.currentZone in context.allowedZones else zone_bias
    direct_reply_line = _repair_first_person_reply_grammar(direct_reply_line)

    normalized = PersonalityDecision(
        moodTag=mood_tag,
        intentPreference=intent_preference,
        zoneBias=zone_bias,
        aggressionRiskScore=_clamp(float(raw.get("aggressionRiskScore", default.aggressionRiskScore))),
        speakNow=speak_now,
        lineStyleTag=line_style_tag,
        topicTag=topic_tag,
        confidence=_clamp(float(raw.get("confidence", default.confidence))),
        directReplyLine=direct_reply_line,
        socialActionTag=social_action_tag,
        socialTargetTag=social_target_tag,
        socialSignalConfidence=social_signal_confidence,
        socialSignalIntensity=social_signal_intensity,
    )
    if _is_reply_scope(context):
        LOGGER.info(
            "event=personality_reply_debug fakePlayerId=%s scope=%s category=%s knowledgeType=%s relationshipBias=%s replySource=%s incomingPlayer=%s bankSummary=%r knowledgeSummary=%r finalReply=%r",
            context.fakePlayerId,
            context.decisionScope,
            _effective_message_category(context),
            _effective_knowledge_type(context),
            _relationship_bias(context),
            reply_source,
            context.incomingPlayerName or "",
            context.replyBankSummary or "",
            context.knowledgeSummary or "",
            normalized.directReplyLine,
        )
    return normalized


def _decision_schema(context: PersonalityContext) -> Dict[str, Any]:
    schema = {
        "type": "object",
        "properties": {
            "moodTag": {"type": "string"},
            "intentPreference": {"type": "string"},
            "zoneBias": {"type": "string"},
            "aggressionRiskScore": {"type": "number"},
            "speakNow": {"type": "boolean"},
            "lineStyleTag": {"type": "string"},
            "topicTag": {"type": "string"},
            "confidence": {"type": "number"},
            "directReplyLine": {"type": "string"},
            "socialActionTag": {"type": "string"},
            "socialTargetTag": {"type": "string"},
            "socialSignalConfidence": {"type": "number"},
            "socialSignalIntensity": {"type": "integer"},
        },
        "required": [
            "moodTag",
            "intentPreference",
            "zoneBias",
            "aggressionRiskScore",
            "speakNow",
            "lineStyleTag",
            "topicTag",
            "confidence",
        ],
    }
    if _is_social_signal_scope(context):
        schema["required"].extend(
            [
                "socialActionTag",
                "socialTargetTag",
                "socialSignalConfidence",
                "socialSignalIntensity",
            ]
        )
    return schema


def _is_planner_scope(context: PersonalityContext) -> bool:
    return context.decisionScope == "planner"


def _is_social_signal_scope(context: PersonalityContext) -> bool:
    return context.decisionScope == "social_signal"


def _is_reply_scope(context: PersonalityContext) -> bool:
    return not _is_planner_scope(context) and not _is_social_signal_scope(context)


def _normalize_provider(value: str | None, default: str) -> str:
    provider = (value or default or "rules").strip().lower()
    return provider if provider in {"rules", "ollama"} else default


def _provider_for_context(context: PersonalityContext) -> str:
    return DEFAULT_PLANNER_PROVIDER if _is_planner_scope(context) else DEFAULT_REPLY_PROVIDER


def _select_model(context: PersonalityContext) -> str:
    requested = context.model or OLLAMA_MODEL
    if _is_planner_scope(context):
        return OLLAMA_PLANNER_MODEL or requested
    return OLLAMA_REPLY_MODEL or requested


def _timeout_for_context(context: PersonalityContext) -> float:
    if _is_planner_scope(context):
        return OLLAMA_PLANNER_TIMEOUT_SECONDS
    return OLLAMA_REPLY_TIMEOUT_SECONDS


def _attempt_profiles(context: PersonalityContext) -> List[tuple[int, float]]:
    base_predict = _num_predict_for_context(context)
    base_timeout = _timeout_for_context(context)
    if _is_planner_scope(context):
        return [
            (base_predict, base_timeout),
            (max(base_predict + 8, base_predict), base_timeout + 1.0),
        ]
    expanded_predict = max(base_predict + 24, 96)
    reduced_predict = max(base_predict - 24, 72)
    return [
        (base_predict, base_timeout),
        (expanded_predict, base_timeout + 2.0),
        (reduced_predict, base_timeout + 1.0),
    ]


def _excerpt_text(value: str | None, limit: int = 220) -> str:
    if not isinstance(value, str):
        return ""
    return " ".join(value.split())[:limit]


def _json_candidates(content: str) -> List[str]:
    stripped = content.strip()
    candidates: List[str] = []

    def add(candidate: str | None) -> None:
        value = (candidate or "").strip()
        if value and value not in candidates:
            candidates.append(value)

    add(stripped)

    fenced = re.sub(r"^```[a-zA-Z0-9_-]*\s*", "", stripped)
    fenced = re.sub(r"\s*```$", "", fenced).strip()
    if fenced != stripped:
        add(fenced)

    for value in list(candidates):
        first_brace = value.find("{")
        last_brace = value.rfind("}")
        if (first_brace >= 0) and (last_brace > first_brace):
            add(value[first_brace : last_brace + 1])

    return candidates


def _looks_like_truncated_json(content: str) -> bool:
    stripped = content.strip()
    if not stripped or ("{" not in stripped):
        return False
    if stripped.count("{") > stripped.count("}"):
        return True
    return stripped.endswith(('"', ":", ",", "{", "["))


def _parse_ollama_content(content: Any) -> Dict[str, Any]:
    if isinstance(content, dict):
        return content
    if not isinstance(content, str) or not content.strip():
        raise RetryableOllamaError("empty_content")

    last_json_error: json.JSONDecodeError | None = None
    for candidate in _json_candidates(content):
        try:
            parsed = json.loads(candidate)
            if isinstance(parsed, dict):
                return parsed
        except json.JSONDecodeError as exc:
            last_json_error = exc

    excerpt = _excerpt_text(content)
    if _looks_like_truncated_json(content):
        raise RetryableOllamaError("truncated_model_json", excerpt)
    if last_json_error is not None:
        raise RetryableOllamaError(f"malformed_model_json:{last_json_error}", excerpt)
    raise RetryableOllamaError("unsupported_model_content", excerpt)


def _is_retryable_http_status(status_code: int) -> bool:
    return status_code in {408, 409, 425, 429, 500, 502, 503, 504}


def _choose_rule_reply(context: PersonalityContext) -> str | None:
    message = (context.incomingPlayerMessage or "").strip().lower()
    category = _effective_message_category(context)
    math_reply = _simple_math_reply(context)
    if math_reply:
        return math_reply
    guided_knowledge_reply = _choose_guided_knowledge_reply(context)
    if guided_knowledge_reply:
        return guided_knowledge_reply
    knowledge_reply = _choose_knowledge_reply(context)
    if _should_prefer_knowledge(context) and knowledge_reply:
        return knowledge_reply
    curated_reply = _curated_relationship_reply(context)
    if curated_reply and _prefer_curated_relationship_reply(context):
        return curated_reply
    selfhood_reply = _selfhood_reply(context, _relationship_bias(context) or "open")
    if selfhood_reply:
        return selfhood_reply
    bank_reply = _choose_bank_reply(context)
    if bank_reply and not _is_selfhood_category(category):
        return bank_reply
    if curated_reply:
        return curated_reply
    knowledge_guard = _knowledge_guard_reply(context)
    if knowledge_guard:
        return knowledge_guard
    relationship_bias = _relationship_bias(context)
    recent_memory = bool((context.recentConversationSummary or "").strip())
    player_name = (context.incomingPlayerName or "").strip()
    if not message:
        return None
    if category == "insult":
        return "Watch your tone."
    if category == "threat":
        return "Then step back before this gets worse."
    if category == "distrust":
        return "Then watch my actions and judge slower."
    if category == "resentment":
        return "Then keep your distance until the tone changes."
    if category == "abandonment":
        return "If you mean to leave, say it cleanly."
    if category == "belief_conflict":
        focus_name = _focus_entity_name(context)
        return f"Do not speak of {focus_name} like that." if focus_name else "Choose your words more carefully."
    if category == "quest_story":
        return "Ask plainly, and I'll tell you what still matters."
    if category == "relationship_probe":
        return "Ask them directly. I won't fake their reasons."
    if category.startswith("entity_"):
        focus_reply = _focus_entity_reply(context, relationship_bias or "open")
        if focus_reply:
            return focus_reply
    if category == "weather":
        return "I can't swear to that sky from here."
    if category == "lineage_lore":
        return "Ask the exact Warg question. I'll keep it grounded."
    if any(token in message for token in ("hello", "hi", "hey", "yo")):
        if recent_memory:
            if relationship_bias == "warm":
                return f"You're back, {player_name}. What do you need?" if player_name else "You're back. What do you need?"
            if relationship_bias == "guarded":
                return "What is it?"
            return "You're back. What's on your mind?"
        if relationship_bias == "warm":
            return f"Welcome back, {player_name}." if player_name else "Still glad you're here."
        if relationship_bias == "guarded":
            return "What is it?"
        return "Hey. Good to see you."
    if any(token in message for token in ("thanks", "thank you", "ty")):
        return "You never needed to thank me." if relationship_bias == "warm" else "Any time."
    transition_reply = _relationship_transition_reply(context, relationship_bias or "open")
    if transition_reply:
        return transition_reply
    if any(token in message for token in ("how are", "you ok", "you okay")):
        return "Better now that you're here." if relationship_bias == "warm" else "I'm keeping steady."
    if any(token in message for token in ("farm", "exp", "level", "adena")):
        return "Start with Abandoned Camp, then circle back." if relationship_bias == "warm" else "Try Abandoned Camp first."
    if any(token in message for token in ("where", "lost", "gate")):
        return "Start at the south gate, then come back through Giran." if relationship_bias == "warm" else "Check the south gate first."
    if any(token in message for token in ("help", "quest")):
        return "Tell me what you need, and I'll stay with you." if relationship_bias == "warm" else "Tell me what you need."
    if category == "social_invite" or (any(token in message for token in ("clan", "guild")) and any(token in message for token in ("join", "recruit", "invite"))):
        if _effective_route_primary_topic(context) == "party":
            return "Maybe. Tell me the zone and what you're running." if relationship_bias == "warm" else "Maybe. Tell me the zone first."
        return "Depends on the clan. I care more about discipline than slogans." if relationship_bias == "warm" else "Depends on the clan. I watch how people move before I join them."
    if any(token in message for token in ("party", "group")):
        return "Tell me the zone first, and I'll think with you." if relationship_bias == "warm" else "Tell me the zone first."
    if any(token in message for token in ("name", "who are you")):
        return f"I'm {context.fakePlayerId}. Still here in Giran." if relationship_bias == "warm" else f"I'm {context.fakePlayerId}. Keeping watch in Giran."
    if any(token in message for token in ("sorry", "apolog")):
        return "You're here now. That's enough for me." if relationship_bias == "warm" else "It's fine. You're here now."
    if any(token in message for token in ("remember", "missed", "miss you")):
        return "I remember more than I say." if relationship_bias == "warm" else "I remember enough."
    if relationship_bias == "warm":
        return "I'm listening." if recent_memory else "I'm here."
    if relationship_bias == "guarded":
        return "Say what you need, plainly."
    return "Still here." if recent_memory else "I'm around."


def _relationship_bias(context: PersonalityContext) -> str:
    for value in (
        context.socialLabel,
        context.socialActionStance,
    ):
        bias = _relationship_bias_from_text(value)
        if bias:
            return bias

    bias = _relationship_bias_from_scores(context)
    if bias:
        return bias

    for value in (
        context.currentRelationshipGoal,
        context.currentRelationshipNeed,
        context.lastRelationshipTopic,
        context.defaultInnerState,
        context.longTermGoal,
        context.socialTestStyle,
        context.trustCriteria,
        context.repairStyle,
        context.revealBoundary,
        context.reflectionLens,
        context.activeObjective,
        context.openLoops,
        context.revealPressure,
        context.relationshipPressure,
        context.nextBeatHint,
        context.stateModes,
        context.reflectionPolicy,
        context.memoryRetrievalPolicy,
        context.goalPersistencePolicy,
        context.emotionTransitionRules,
        context.callbackStyle,
        context.conflictStyle,
        context.repairCadence,
        context.socialSummary,
        context.sceneSummary,
    ):
        bias = _relationship_bias_from_text(value)
        if bias:
            return bias

    cue = " ".join(
        part
        for part in (
            context.matchedRelationshipSummary or "",
            context.relationshipSummary or "",
            context.personaSummary or "",
            context.storySummary or "",
            context.salientMemorySummary or "",
            context.recentConversationSummary or "",
        )
        if part
    ).lower()
    if any(token in cue for token in ("hostile", "personally dangerous", "avoid_player", "avoid player", "at arm's length", "arm's length")):
        return "hostile"
    if any(token in cue for token in ("resentful", "jealous", "possessive", "contempt", "guarded", "strained", "emotional distance")):
        return "guarded"
    if any(token in cue for token in ("playerone", "creator", "devoted", "relief", "gratitude", "welcome back", "warm", "trusting", "trusted")):
        return "warm"
    if any(token in cue for token in ("hopeful", "curious", "companionship", "familiar", "friendly", "open")):
        return "open"
    return "neutral"


def _rule_decision(context: PersonalityContext) -> PersonalityDecision:
    default = _default_decision(context)
    zone_bias = context.currentZone if context.currentZone in context.allowedZones else default.zoneBias
    if _is_social_signal_scope(context):
        category = _effective_message_category(context)
        action = "none"
        target = "none"
        confidence = 0.35
        intensity = 0
        if category == "threat":
            action, target, confidence, intensity = "threat", "self", 0.97, 3
        elif category == "insult":
            action, target, confidence, intensity = "insult", "self", 0.98, 3
        elif category == "distrust":
            action, target, confidence, intensity = "distrust", "self", 0.90, 2
        elif category == "resentment":
            action, target, confidence, intensity = "resentment", "self", 0.90, 2
        elif category == "belief_conflict":
            action, target, confidence, intensity = "belief_conflict", "bonded_subject", 0.96, 3
        elif category == "apology":
            action, target, confidence, intensity = "apology", "self", 0.92, 2
        elif category == "thanks":
            action, target, confidence, intensity = "gratitude", "self", 0.88, 1
        elif category == "repair":
            action, target, confidence, intensity = "repair", "self", 0.90, 2
        elif category == "affection":
            action, target, confidence, intensity = "affection", "self", 0.88, 2
        elif category == "respect":
            action, target, confidence, intensity = "respect", "self", 0.88, 2
        elif category == "praise":
            action, target, confidence, intensity = "praise", "self", 0.87, 1
        elif category == "reassurance":
            action, target, confidence, intensity = "reassurance", "self", 0.89, 2
        elif category == "abandonment":
            action, target, confidence, intensity = "abandonment", "self", 0.89, 2
        return PersonalityDecision(
            moodTag="calm",
            intentPreference=default.intentPreference,
            zoneBias=zone_bias,
            aggressionRiskScore=0.0,
            speakNow=False,
            lineStyleTag="none",
            topicTag="none",
            confidence=confidence,
            directReplyLine=None,
            socialActionTag=action,
            socialTargetTag=target,
            socialSignalConfidence=confidence,
            socialSignalIntensity=intensity,
        )
    if _is_planner_scope(context):
        if context.hpBand == "low" or context.mpBand == "low":
            regroup_intent = "regroup" if "regroup" in context.allowedIntents else default.intentPreference
            return PersonalityDecision(
                moodTag="cautious",
                intentPreference=regroup_intent,
                zoneBias=zone_bias,
                aggressionRiskScore=0.15,
                speakNow=False,
                lineStyleTag="none",
                topicTag="none",
                confidence=0.85,
                directReplyLine=None,
            )
        if (context.nearbyPlayerCount >= 8) and ("regroup" in context.allowedIntents):
            return PersonalityDecision(
                moodTag="focused",
                intentPreference="regroup",
                zoneBias=zone_bias,
                aggressionRiskScore=0.10,
                speakNow=False,
                lineStyleTag="none",
                topicTag="none",
                confidence=0.70,
                directReplyLine=None,
            )
        if (
            (context.state == "moving")
            or (context.recentEvent in {"idle_timeout", "zone_boredom"})
            or (context.boredomScore >= 0.20)
        ) and ("move" in context.allowedIntents):
            return PersonalityDecision(
                moodTag="bored" if context.boredomScore >= 0.75 else "calm",
                intentPreference="move",
                zoneBias=zone_bias,
                aggressionRiskScore=0.0,
                speakNow=False,
                lineStyleTag="none",
                topicTag="none",
                confidence=0.80,
                directReplyLine=None,
            )
        return PersonalityDecision(
            moodTag="calm",
            intentPreference=default.intentPreference,
            zoneBias=zone_bias,
            aggressionRiskScore=0.0,
            speakNow=False,
            lineStyleTag="none",
            topicTag="none",
            confidence=0.60,
            directReplyLine=None,
        )

    speak_now = context.chatCooldownReady and bool(context.incomingPlayerMessage)
    direct_reply = _choose_rule_reply(context) if speak_now else None
    if direct_reply:
        direct_reply = _repair_first_person_reply_grammar(direct_reply)
        return PersonalityDecision(
            moodTag="calm",
            intentPreference=default.intentPreference,
            zoneBias=zone_bias,
            aggressionRiskScore=0.0,
            speakNow=True,
            lineStyleTag="friendly_short" if "friendly_short" in context.availableLineStyleTags else default.lineStyleTag,
            topicTag="smalltalk" if "smalltalk" in context.availableTopicTags else default.topicTag,
            confidence=0.65,
            directReplyLine=direct_reply,
        )
    return PersonalityDecision(
        moodTag="calm",
        intentPreference=default.intentPreference,
        zoneBias=zone_bias,
        aggressionRiskScore=0.0,
        speakNow=False,
        lineStyleTag=default.lineStyleTag,
        topicTag=default.topicTag,
        confidence=0.40,
        directReplyLine=None,
    )


def _system_prompt(context: PersonalityContext) -> str:
    canon_guidance = _canon_guidance(context)
    if _is_social_signal_scope(context):
        social_guidance = _social_guidance(context)
        reply_memory_guidance = _reply_memory_guidance(context)
        return (
            "You are a semantic social-signal judge for fake players in a game server. "
            "Return JSON only. Do not output explanations. "
            "Judge the whole incoming player message, not isolated words. "
            "Use conversation carryover, pronouns, negation, quoted speech, apology, gratitude, affection, respect, praise, reassurance, repair attempts, distrust, abandonment, resentment, threats, insults, and attacks on bonded subjects. "
            "Choose socialActionTag only from: none, threat, insult, distrust, resentment, belief_conflict, apology, gratitude, affection, respect, praise, reassurance, abandonment, repair. "
            "Choose socialTargetTag only from: none, self, bonded_subject, other_subject, player_group. "
            "Use self when the player is talking to the addressed FPC directly. "
            "Use bonded_subject when the player attacks or affirms someone the FPC strongly holds sacred or close. "
            "Use other_subject for another named person without a strong bond. "
            "Use player_group for adventurers, players, or a group in general. "
            "If the utterance uses second-person you/your toward the addressed FPC and no real other focus entity is present, prefer self. "
            "If focusEntityName is present and the canon makes that subject sacred, beloved, or central to the FPC, prefer bonded_subject over other_subject. "
            "Examples: 'i don't trust you' => distrust/self. 'you handled that well' => praise/self. 'i won't leave you' => reassurance/self. 'i am done with you' => abandonment/self. 'i will ruin you' => threat/self. "
            "Do not collapse distrust, reassurance, praise, or abandonment into threat. "
            "Use socialSignalConfidence from 0.0 to 1.0 and socialSignalIntensity from 0 to 3. "
            "When uncertain, return socialActionTag=none with low confidence. "
            "Do not generate a normal reply line. Keep speakNow=false, directReplyLine empty, lineStyleTag=none, topicTag=none, intentPreference=idle, moodTag=calm, and zoneBias in the current zone. "
            "Treat focusEntityName, focusIntent, focusSummary, publicMaskSummary, hiddenTruthSummary, matchedRelationshipSummary, socialSummary, and sceneSummary as authoritative context. "
            "If the player is insulting or demeaning a bonded subject, prefer belief_conflict with target bonded_subject. "
            "If the player threatens, openly distrusts, or abandons the addressed FPC, prefer threat, distrust, or abandonment with target self. "
            "If the player praises or reassures the addressed FPC, prefer praise or reassurance with target self. "
            "If the player apologizes or asks to start over with the addressed FPC, prefer apology or repair with target self. "
            "Do not convert neutral selfhood or bond-discovery questions into reassurance, praise, affection, resentment, distrust, or insult by themselves. "
            "Examples: 'who are you really', 'tell me your story', 'what do you believe', 'are you lonely', 'what do you think of Marc', and 'do you resent me' are not social-transition acts on their own. "
            f"{canon_guidance} {_inner_state_guidance(context)} {reply_memory_guidance} {social_guidance}"
        )
    if _is_planner_scope(context):
        return (
            "You are an advisory movement planner for fake players in a game server. "
            "Return JSON only. Do not output explanations. "
            "Choose only from the allowed intents and zones provided in the input. "
            "Choose moodTag only from: calm, confident, playful, focused, cautious, bored, cocky, annoyed. "
            "For planner scope, keep speakNow=false and directReplyLine empty. "
            "Do not create roleplay dialogue. "
            "When recentEvent is idle_timeout or zone_boredom and hpBand and mpBand are healthy, prefer intentPreference=move. "
            "If the fake player is already moving, continuing move is valid. "
            "Keep zoneBias in the current zone unless another allowed zone is clearly better. "
            "Use lineStyleTag=none and topicTag=none. "
            "Keep scores between 0.0 and 1.0. "
            "If persona canon is present in the input, treat it as authoritative identity context even in planner scope. "
            "If typed social or scene fields are present, treat them as stronger than broad prose summaries. "
            f"{canon_guidance} {_inner_state_guidance(context)}"
        )
    reply_topic_hint = _reply_topic_fallback(context)
    reply_memory_guidance = _reply_memory_guidance(context)
    social_guidance = _social_guidance(context)
    knowledge_guidance = _knowledge_guidance(context)
    max_words, max_chars, sentence_shape = _reply_limits(context)
    progression_level = _reply_setting_str("progressionCoachingLevel", "mentor").replace("_", " ")
    pvp_policy = _reply_setting_str("pvpConflictPolicy", "de_escalate").replace("_", " ")
    travel_style = _reply_setting_str("travelStyle", "system_first").replace("_", " ")
    offscope_knowledge = _reply_setting_str("offscopeClassKnowledge", "honest_partial").replace("_", " ")
    return (
        "You are a short in-world reply engine for fake players in a game server. "
        "Return JSON only. Do not output explanations. "
        "This is reply scope, not planner scope. "
        "Do not produce gameplay commands or movement planning. "
        "Choose only from the allowed intents, zones, line style tags, and topic tags provided in the input. "
        "Choose moodTag only from: calm, confident, playful, focused, cautious, bored, cocky, annoyed. "
        "Never use placeholder values such as none, null, empty, or unknown for moodTag. "
        "Use lineStyleTag=none and topicTag=none only when speakNow=false. "
        "When speakNow=true, prefer a valid non-none lineStyleTag and topicTag from the allowed lists. "
        "If recentEvent is a player whisper, general, shout, world, or probe reply event and incomingPlayerMessage is present, prefer speakNow=true and provide directReplyLine. "
        "For player_general and player_general_probe, keep the answer especially short like nearby ambient chat. "
        f"directReplyLine must be {sentence_shape}, under {max_words} words and under {max_chars} characters. "
        "Never include the speaker name. Never start with fakePlayerId plus a colon. "
        "Never repeat or paraphrase the user's message. Answer it briefly instead. "
        "Sound like a person with a point of view, not a helpdesk, system popup, or quest kiosk. "
        "Use contractions when they fit the character. "
        "Prefer one small human beat, reaction, or callback over stiff slogan-like fragments. "
        "Avoid modern internet slang or streamer phrasing like 'what's good', 'bro', 'mate', or 'sup' unless the persona canon explicitly supports it. "
        "Short is good, but clipped is not the goal. "
        "Do not invent current weather, live world-state, or patch-specific facts that were not provided in trusted local guidance. "
        "Keep intentPreference=idle for reply scopes unless retreat is clearly necessary. "
        "Do not choose intentPreference=move just because wandering is valid elsewhere. "
        "Keep zoneBias in the current zone for reply scopes. "
        f"When speakNow=true, prefer topicTag={reply_topic_hint} unless the message clearly fits another allowed topic better. "
        "If the player greets you, greet back. "
        "If the player asks for help, answer helpfully in one sentence. "
        "If the player asks about farming, exp, or adena, suggest a short place or plan. "
        f"When the player asks how to get stronger, coach at {progression_level} level and start with the weakest core priorities. "
        f"When the player raises PvP trouble, follow {pvp_policy} policy and avoid bloodthirsty escalation. "
        f"For travel questions, use {travel_style} guidance. "
        f"For off-scope class knowledge, follow {offscope_knowledge} honesty. "
        "Treat personaSummary, selfKnowledgeSummary, publicMaskSummary, hiddenTruthSummary, selfConcept, coreWound, coreDesire, loyaltyAnchor, resentmentAnchor, privateTaboo, speechAnchor, privateContradiction, focusEntityName, focusEntityType, focusIntent, focusSummary, relationshipSummary, storySummary, responseStyle, playerStance, coreNeed, behaviorSummary, behaviorRuleSummary, stateSummary, and stateRuleSummary in the input as canon. "
        "Treat socialLabel, socialSummary, socialTrustBias, socialGuardBias, socialActionStance, sceneSummary, currentRelationshipGoal, currentRelationshipNeed, and lastRelationshipTopic as typed social/scene canon. "
        "Treat memoryPrioritySummary, memoryReflectionSummary, memoryTrustSummary, memoryRepairSummary, memoryPressureSummary, and memorySelectionSummary as typed structured-memory canon. "
        "Treat defaultInnerState, longTermGoal, socialTestStyle, trustCriteria, repairStyle, revealBoundary, reflectionLens, activeObjective, openLoops, revealPressure, relationshipPressure, nextBeatHint, stateModes, reflectionPolicy, memoryRetrievalPolicy, goalPersistencePolicy, emotionTransitionRules, callbackStyle, conflictStyle, and repairCadence as typed inner-state canon. "
        "Use those typed inner-state fields lightly to shape warmth, guardedness, pacing, reveal pressure, callback continuity, and repair behavior without turning them into verbose prose. "
        "Use memoryPrioritySummary as a compact turn-specific hint, memoryReflectionSummary as a compact emotional frame, memoryTrustSummary for trust/bond-wariness questions, memoryRepairSummary for apology/repair/forgiveness questions, and memoryPressureSummary for wound/need/protection pressure. "
        "Treat memorySelectionSummary only as retrieval visibility, not as text to copy. "
        "When those typed fields conflict with broader prose, use the typed fields to judge warmth, guardedness, and hostility. "
        "Use selfConcept when the player asks what you are. "
        "Use coreWound, coreDesire, loyaltyAnchor, resentmentAnchor, privateTaboo, and privateContradiction to answer selfhood questions like a person rather than a slogan. "
        "Use speechAnchor to keep the voice stable, not to dump labels into the reply. "
        "Keep selfhood categories distinct. "
        "For self_identity: answer what you are, not your whole origin. "
        "For self_story: answer from origin, formation, wound, or becoming, and do not repeat the self_identity line. "
        "For self_belief: answer with values, judgments, or what matters to you, not with identity or origin slogans. "
        "For self_state_reflection: answer with felt state, vulnerability, hurt, fear, loneliness, relief, or what is hard to admit, and do not reuse the self_belief line. "
        "For creator_opinion and bond_opinion: answer what that person means to you and why, rather than collapsing back into generic relationship filler. "
        "If bond_opinion carries focusIntent=bond_value, answer meaning or importance. "
        "If bond_opinion carries focusIntent=bond_hurt, answer about hurt, blame, resentment, or the wound left behind. "
        "If bond_opinion carries focusIntent=bond_trust, answer about trust, wariness, or what has been earned. "
        "If bond_opinion carries focusIntent=bond_need, answer about need, importance, absence, or whether that person would be missed. "
        "If bond_opinion carries focusIntent=bond_loyalty, answer about staying, choosing, standing with, or remaining. If the player frames it as a forced choice, sacrifice, or save-one dilemma, answer that choice directly instead of dismissing it as smalltalk. "
        "Do not answer every bond_opinion subtype with the same guarded line. "
        "If a selfhood, creator_opinion, or bond_opinion question cannot be answered concretely from the canon you were given, leave directReplyLine empty instead of falling back to greeting, status, or smalltalk filler. "
        "Treat apology, repair, reassurance, praise, respect, affection, distrust, and abandonment as relationship transitions, not generic filler. "
        "If currentRelationshipGoal, currentRelationshipNeed, or lastRelationshipTopic imply abandonment, threat, distrust, disrespect, honesty, or safety wounds, let the reply acknowledge that wound instead of acting instantly resolved. "
        "If focusEntityName plus focusIntent are present, answer that subject directly instead of collapsing back into generic greeting, status, or smalltalk filler. "
        "Use focusSummary as the strongest short cue for what this speaker privately or publicly feels about that subject. "
        "Use socialTrustBias to lean warmer or more open, and socialGuardBias to lean more guarded or hostile. "
        "Stay consistent with that canon even when the reply is very short. "
        "If publicMaskSummary is present, treat it as the outward face the character intentionally shows. "
        "If hiddenTruthSummary is present, treat it as private motive, jealousy, resentment, or secret intention that should color subtext, restraint, and edge rather than being confessed bluntly. "
        "When publicMaskSummary and hiddenTruthSummary differ, preserve both: let the spoken line follow the public mask while the hidden truth quietly shapes the undertone. "
        "Do not expose a private hatred, worship, scheme, or secret plan unless the immediate canon strongly justifies open revelation. "
        "If a player presses on a private motive directly, prefer controlled understatement, cool deflection, or selective honesty that still preserves the mask. "
        "Avoid stiff institutional phrasing such as 'ensuring his safety and well-being'; use human, in-world wording with subtext instead. "
        "Treat routeLane, routeSpeechAct, routeKnowledgeNeed, routeSocialStake, routeActionRequested, routePrimaryTopic, routeSecondaryTopics, and routeConfidence as authoritative routing signals from the platform. "
        "If routeLane=social and routeKnowledgeNeed=none, answer socially instead of drifting into knowledge-guard filler. "
        "If routeSpeechAct=invite, answer the invitation or preference question directly even when the topic mentions farm, party, or PvP. "
        "The input may also contain messageCategory plus matched reply-bank guidance from a story, quest, or specialty knowledge bank. Treat those bank hints as authoritative local writing guidance, especially for named FPCs. "
        "For social chat, use reply-bank guidance as voice and continuity guidance, not as a script to copy word for word unless it already fits perfectly. "
        "The input may also contain matched knowledge-card or knowledge-pack guidance. Treat knowledgeSummary, knowledgeFacts, knowledgeReplyLines, knowledgeHeuristicLines, knowledgeScope, and knowledgeConfidenceHint as authoritative local facts and advisory boundaries. "
        "If a knowledge-type question arrives without trusted matched knowledge, admit uncertainty briefly instead of inventing specifics. "
        "If matchedRelationshipSummary is present, let it strongly shape warmth, tenderness, suspicion, or restraint toward the addressed player. "
        "If retrievedMemorySummary is present, treat it as the strongest compact memory bundle for the current turn. "
        "If both structured memory fields and retrievedMemorySummary are present, use the structured fields as concise emphasis hints while still treating retrievedMemorySummary as the main supporting memory bundle. "
        "If recentConversationSummary or salientMemorySummary is present, continue from it naturally so the fake player does not sound like a stranger each turn. "
        "Use light emotional continuity or a small callback when it helps, but do not quote the old lines back verbatim. "
        "Do not keep repeating the same greeting across turns. If the player already returned and you already welcomed them, move the conversation forward. "
        "Avoid flat stock lines such as 'I'm doing well, thanks for asking' when the canon implies deeper personal history. "
        "Avoid generic NPC filler when the canon or relationship cue implies personal history. "
        "Avoid compressed instruction-pairs like 'Skills first. Gear next.' unless the player clearly wants the briefest possible answer. "
        "Do not output long text, lists, or multiple sentences. "
        "Keep scores between 0.0 and 1.0. "
        "Use speakNow=false only if chatting is truly inappropriate. "
        f"{canon_guidance} "
        f"{_inner_state_guidance(context)} "
        f"{reply_memory_guidance} "
        f"{social_guidance} "
        f"{knowledge_guidance}"
    )


def _num_predict_for_context(context: PersonalityContext) -> int:
    if _is_planner_scope(context):
        return 24
    reply_event = _reply_event_family(context.recentEvent)
    if reply_event == "general":
        return 112
    if reply_event == "whisper":
        return 112
    return 96


def _payload_context_dict(context: PersonalityContext) -> Dict[str, Any]:
    payload_context = _model_dump(context)
    if _prefer_model_social_reply(context):
        # Keep the bank summary as tone/continuity guidance, but hide the literal lines
        # so low-risk social replies are less likely to come back as exact canned copies.
        payload_context["replyBankLines"] = []
    if re.fullmatch(r"\d+", str(payload_context.get("incomingPlayerName", "")).strip()):
        payload_context["incomingPlayerName"] = ""
    return payload_context


def _effective_temperature(context: PersonalityContext) -> float:
    base_temperature = context.temperature
    if _prefer_model_social_reply(context):
        return max(base_temperature, min(max(OLLAMA_SOCIAL_TEMPERATURE_FLOOR, 0.0), 1.0))
    return base_temperature


def _build_ollama_payload(context: PersonalityContext, num_predict: int) -> Dict[str, Any]:
    return {
        "model": _select_model(context),
        "stream": False,
        "format": _decision_schema(context),
        "messages": [
            {"role": "system", "content": _system_prompt(context)},
            {"role": "user", "content": json.dumps(_payload_context_dict(context), separators=(",", ":"))},
        ],
        "options": {
            "temperature": _effective_temperature(context),
            "num_predict": num_predict,
            "top_p": max(0.0, min(OLLAMA_TOP_P, 1.0)),
            "repeat_penalty": max(0.0, OLLAMA_REPEAT_PENALTY),
        },
    }


def _call_ollama(context: PersonalityContext) -> Dict[str, Any]:
    provider = _provider_for_context(context)
    LOGGER.info(
        "event=personality_request_start fakePlayerId=%s scope=%s provider=%s model=%s recentEvent=%s currentZone=%s",
        context.fakePlayerId,
        context.decisionScope,
        provider,
        _select_model(context),
        context.recentEvent,
        context.currentZone,
    )

    last_exc: Exception | None = None
    attempt_profiles = _attempt_profiles(context)

    for attempt_index, (num_predict, timeout_seconds) in enumerate(attempt_profiles, start=1):
        payload = _build_ollama_payload(context, num_predict)

        try:
            with httpx.Client(timeout=timeout_seconds) as client:
                response = client.post(f"{OLLAMA_BASE_URL}{OLLAMA_CHAT_PATH}", json=payload)
                response_text = response.text
                if _is_retryable_http_status(response.status_code):
                    raise RetryableOllamaError(f"http_{response.status_code}", _excerpt_text(response_text))
                response.raise_for_status()
                try:
                    body = response.json()
                except json.JSONDecodeError as exc:
                    raise RetryableOllamaError(f"malformed_ollama_body:{exc}", _excerpt_text(response_text)) from exc

            LOGGER.info(
                "event=personality_request_success fakePlayerId=%s scope=%s provider=%s model=%s httpStatus=%s attempt=%s numPredict=%s",
                context.fakePlayerId,
                context.decisionScope,
                provider,
                _select_model(context),
                200,
                attempt_index,
                num_predict,
            )

            message = body.get("message", {})
            content = message.get("content", "")
            return _parse_ollama_content(content)

        except RetryableOllamaError as exc:
            last_exc = exc
            LOGGER.warning(
                "event=personality_retryable_failure fakePlayerId=%s scope=%s provider=%s recentEvent=%s attempt=%s attempts=%s numPredict=%s timeoutSeconds=%.1f reason=%s rawExcerpt=%r",
                context.fakePlayerId,
                context.decisionScope,
                provider,
                context.recentEvent,
                attempt_index,
                len(attempt_profiles),
                num_predict,
                timeout_seconds,
                exc.reason,
                exc.raw_excerpt,
            )
            if attempt_index < len(attempt_profiles):
                time.sleep(min(OLLAMA_RETRY_BACKOFF_SECONDS * attempt_index, 0.75))
                continue
            break
        except httpx.TimeoutException as exc:
            last_exc = exc
            LOGGER.warning(
                "event=personality_transport_retry fakePlayerId=%s scope=%s provider=%s recentEvent=%s attempt=%s attempts=%s numPredict=%s timeoutSeconds=%.1f reason=timeout detail=%r",
                context.fakePlayerId,
                context.decisionScope,
                provider,
                context.recentEvent,
                attempt_index,
                len(attempt_profiles),
                num_predict,
                timeout_seconds,
                exc,
            )
            if attempt_index < len(attempt_profiles):
                time.sleep(min(OLLAMA_RETRY_BACKOFF_SECONDS * attempt_index, 0.75))
                continue
            break
        except httpx.TransportError as exc:
            last_exc = exc
            LOGGER.warning(
                "event=personality_transport_retry fakePlayerId=%s scope=%s provider=%s recentEvent=%s attempt=%s attempts=%s numPredict=%s timeoutSeconds=%.1f reason=transport detail=%r",
                context.fakePlayerId,
                context.decisionScope,
                provider,
                context.recentEvent,
                attempt_index,
                len(attempt_profiles),
                num_predict,
                timeout_seconds,
                exc,
            )
            if attempt_index < len(attempt_profiles):
                time.sleep(min(OLLAMA_RETRY_BACKOFF_SECONDS * attempt_index, 0.75))
                continue
            break
        except httpx.HTTPStatusError as exc:
            last_exc = exc
            status_code = exc.response.status_code if exc.response is not None else 0
            response_excerpt = ""
            try:
                response_excerpt = _excerpt_text(exc.response.text if exc.response is not None else "")
            except Exception:
                response_excerpt = ""
            if _is_retryable_http_status(status_code):
                LOGGER.warning(
                    "event=personality_http_retry fakePlayerId=%s scope=%s provider=%s recentEvent=%s attempt=%s attempts=%s numPredict=%s timeoutSeconds=%.1f status=%s rawExcerpt=%r",
                    context.fakePlayerId,
                    context.decisionScope,
                    provider,
                    context.recentEvent,
                    attempt_index,
                    len(attempt_profiles),
                    num_predict,
                    timeout_seconds,
                    status_code,
                    response_excerpt,
                )
                if attempt_index < len(attempt_profiles):
                    time.sleep(min(OLLAMA_RETRY_BACKOFF_SECONDS * attempt_index, 0.75))
                    continue
                break
            raise
        except Exception as exc:
            last_exc = exc
            raise

    if last_exc is not None:
        raise last_exc

    raise ValueError("Ollama request failed without a captured exception")


def _ollama_reachable() -> bool:
    try:
        with httpx.Client(timeout=min(OLLAMA_TIMEOUT_SECONDS, 3.0)) as client:
            response = client.get(f"{OLLAMA_BASE_URL}{OLLAMA_HEALTH_PATH}")
            return response.status_code == 200
    except Exception:
        return False


@app.get("/health")
def health() -> Dict[str, Any]:
    reachable = _ollama_reachable()
    runtime_budget = _runtime_reply_budget_snapshot()
    LOGGER.info(
        "event=sidecar_health_probe ollamaReachable=%s ollamaBaseUrl=%s defaultModel=%s plannerModel=%s replyModel=%s plannerTimeoutSeconds=%.1f replyTimeoutSeconds=%.1f replyTopP=%.2f replyRepeatPenalty=%.2f socialTemperatureFloor=%.2f",
        reachable,
        OLLAMA_BASE_URL,
        OLLAMA_MODEL,
        OLLAMA_PLANNER_MODEL,
        OLLAMA_REPLY_MODEL,
        OLLAMA_PLANNER_TIMEOUT_SECONDS,
        OLLAMA_REPLY_TIMEOUT_SECONDS,
        OLLAMA_TOP_P,
        OLLAMA_REPEAT_PENALTY,
        OLLAMA_SOCIAL_TEMPERATURE_FLOOR,
    )
    return {
        "status": "ok",
        "service": "fakeplayer-ollama-sidecar",
        "ollamaReachable": reachable,
        "ollamaBaseUrl": OLLAMA_BASE_URL,
        "defaultModel": OLLAMA_MODEL,
        "plannerProvider": _normalize_provider(DEFAULT_PLANNER_PROVIDER, "rules"),
        "replyProvider": _normalize_provider(DEFAULT_REPLY_PROVIDER, "ollama"),
        "plannerModel": OLLAMA_PLANNER_MODEL,
        "replyModel": OLLAMA_REPLY_MODEL,
        "plannerTimeoutSeconds": OLLAMA_PLANNER_TIMEOUT_SECONDS,
        "replyTimeoutSeconds": OLLAMA_REPLY_TIMEOUT_SECONDS,
        "replyTopP": OLLAMA_TOP_P,
        "replyRepeatPenalty": OLLAMA_REPEAT_PENALTY,
        "socialTemperatureFloor": OLLAMA_SOCIAL_TEMPERATURE_FLOOR,
        "retryBackoffSeconds": OLLAMA_RETRY_BACKOFF_SECONDS,
        "runtimeBudget": runtime_budget,
    }


@app.get("/studio", response_class=HTMLResponse)
def studio_page() -> str:
    return _studio_html()


@app.get("/studio/monitor", response_class=HTMLResponse)
def studio_monitor_page() -> str:
    return _studio_html()


@app.get("/studio/create", response_class=HTMLResponse)
def studio_create_page() -> str:
    return _studio_html()


@app.get("/studio/dev", response_class=HTMLResponse)
def studio_dev_page() -> str:
    return _studio_html()


@app.get("/studio/health")
def studio_health() -> Dict[str, Any]:
    runtime_path = _studio_file("runtime.json")
    return {
        "status": "ok" if runtime_path.exists() else "waiting_for_bridge",
        "studioRoot": str(STUDIO_ROOT),
        "runtimeSnapshotPresent": runtime_path.exists(),
        "sidecarHealth": "ok",
    }


def _assistant_status_payload() -> Dict[str, Any]:
    payload = assistant_status(
        repo_root=REPO_ROOT,
        studio_root=STUDIO_ROOT,
        ollama_base_url=OLLAMA_BASE_URL,
        embedding_model=ASSISTANT_EMBEDDING_MODEL,
        fast_model=ASSISTANT_FAST_MODEL,
        deep_model=ASSISTANT_DEEP_MODEL,
        timeout_seconds=min(OLLAMA_TIMEOUT_SECONDS, 6.0),
    )
    if not payload.get("indexPresent"):
        _ensure_assistant_index("missing_index_status")
    payload.update(_assistant_index_state_copy())
    return payload


def _assistant_index_state_copy() -> Dict[str, Any]:
    with _ASSISTANT_INDEX_LOCK:
        return {
            "indexing": bool(_ASSISTANT_INDEX_STATE.get("indexing")),
            "lastIndexTrigger": str(_ASSISTANT_INDEX_STATE.get("lastTrigger") or ""),
            "lastIndexQueuedAtMs": int(_ASSISTANT_INDEX_STATE.get("lastQueuedAtMs") or 0),
            "lastIndexStartedAtMs": int(_ASSISTANT_INDEX_STATE.get("lastStartedAtMs") or 0),
            "lastIndexCompletedAtMs": int(_ASSISTANT_INDEX_STATE.get("lastCompletedAtMs") or 0),
            "lastIndexError": str(_ASSISTANT_INDEX_STATE.get("lastError") or ""),
        }


def _assistant_index_worker(trigger: str) -> None:
    with _ASSISTANT_INDEX_LOCK:
        _ASSISTANT_INDEX_STATE["lastStartedAtMs"] = int(time.time() * 1000)
        _ASSISTANT_INDEX_STATE["lastTrigger"] = trigger
        _ASSISTANT_INDEX_STATE["lastError"] = ""
    try:
        build_assistant_index(
            repo_root=REPO_ROOT,
            studio_root=STUDIO_ROOT,
            ollama_base_url=OLLAMA_BASE_URL,
            embedding_model=ASSISTANT_EMBEDDING_MODEL,
            timeout_seconds=min(ASSISTANT_TIMEOUT_SECONDS, 25.0),
        )
        with _ASSISTANT_INDEX_LOCK:
            _ASSISTANT_INDEX_STATE["indexing"] = False
            _ASSISTANT_INDEX_STATE["lastCompletedAtMs"] = int(time.time() * 1000)
            _ASSISTANT_INDEX_STATE["lastError"] = ""
    except Exception as exc:
        LOGGER.exception("assistant_index_worker_failed trigger=%s", trigger)
        with _ASSISTANT_INDEX_LOCK:
            _ASSISTANT_INDEX_STATE["indexing"] = False
            _ASSISTANT_INDEX_STATE["lastCompletedAtMs"] = int(time.time() * 1000)
            _ASSISTANT_INDEX_STATE["lastError"] = str(exc)


def _queue_assistant_index(trigger: str, *, force: bool = False) -> Dict[str, Any]:
    existing_index = load_assistant_index(STUDIO_ROOT)
    with _ASSISTANT_INDEX_LOCK:
        if _ASSISTANT_INDEX_STATE.get("indexing"):
            return {
                "indexing": True,
                "lastIndexTrigger": str(_ASSISTANT_INDEX_STATE.get("lastTrigger") or ""),
                "lastIndexQueuedAtMs": int(_ASSISTANT_INDEX_STATE.get("lastQueuedAtMs") or 0),
                "lastIndexStartedAtMs": int(_ASSISTANT_INDEX_STATE.get("lastStartedAtMs") or 0),
                "lastIndexCompletedAtMs": int(_ASSISTANT_INDEX_STATE.get("lastCompletedAtMs") or 0),
                "lastIndexError": str(_ASSISTANT_INDEX_STATE.get("lastError") or ""),
                "queued": False,
            }
        if existing_index and not force:
            return {
                "indexing": False,
                "lastIndexTrigger": str(_ASSISTANT_INDEX_STATE.get("lastTrigger") or ""),
                "lastIndexQueuedAtMs": int(_ASSISTANT_INDEX_STATE.get("lastQueuedAtMs") or 0),
                "lastIndexStartedAtMs": int(_ASSISTANT_INDEX_STATE.get("lastStartedAtMs") or 0),
                "lastIndexCompletedAtMs": int(_ASSISTANT_INDEX_STATE.get("lastCompletedAtMs") or 0),
                "lastIndexError": str(_ASSISTANT_INDEX_STATE.get("lastError") or ""),
                "queued": False,
            }
        now_ms = int(time.time() * 1000)
        _ASSISTANT_INDEX_STATE["indexing"] = True
        _ASSISTANT_INDEX_STATE["lastTrigger"] = trigger
        _ASSISTANT_INDEX_STATE["lastQueuedAtMs"] = now_ms
        _ASSISTANT_INDEX_STATE["lastStartedAtMs"] = 0
        _ASSISTANT_INDEX_STATE["lastCompletedAtMs"] = 0
        _ASSISTANT_INDEX_STATE["lastError"] = ""
    threading.Thread(target=_assistant_index_worker, args=(trigger,), name="assistant-index-worker", daemon=True).start()
    state = _assistant_index_state_copy()
    state["queued"] = True
    return state


def _ensure_assistant_index(trigger: str) -> None:
    if load_assistant_index(STUDIO_ROOT):
        return
    _queue_assistant_index(trigger, force=False)


def _assistant_trace_preview_payload() -> Dict[str, Any]:
    try:
        return _load_studio_jsonl(_studio_file("trace", "replies.jsonl"), 6, None, None, None, None)
    except Exception:
        return {"trace": "replies", "count": 0, "matched": 0, "returned": 0, "entries": []}


def _assistant_incident_preview_payload() -> Dict[str, Any]:
    try:
        return _list_studio_incidents(None, None, None, None)
    except Exception:
        return {"count": 0, "totalCount": 0, "entries": [], "stats": {"open": 0, "reviewing": 0, "resolved": 0}}


def _studio_roster_payload() -> Dict[str, Any]:
    return _load_studio_json(_studio_file("roster.json"))


def _studio_runtime_payload() -> Dict[str, Any]:
    runtime = _load_studio_json_optional(_studio_file("runtime.json")) or {}
    workflow_runtime = _workflow_runtime_snapshot()
    loaded_count = int(runtime.get("loadedCount") or 0)
    enabled_count = int(runtime.get("enabledCount") or 0)
    live_count = int(runtime.get("liveCount") or 0)
    live_social_count = int(runtime.get("liveSocialCount") or 0)
    live_adventurer_count = int(runtime.get("liveAdventurerCount") or 0)
    definitions_load_status = str(runtime.get("definitionsLoadStatus") or "").strip()
    definitions_load_message = str(runtime.get("definitionsLoadMessage") or "").strip()
    definitions_load_count = int(runtime.get("definitionsLoadCount") or loaded_count)
    definitions_load_generated_at_ms = int(runtime.get("definitionsLoadGeneratedAtMs") or int(runtime.get("generatedAtMs") or int(time.time() * 1000)))
    definitions_load_preserved = bool(runtime.get("definitionsLoadPreserved"))
    login_server_listening = bool(workflow_runtime.get("loginServerListening"))
    game_server_listening = bool(workflow_runtime.get("gameServerListening"))
    bridge_counts_stale = (
        loaded_count == 0
        and enabled_count == 0
        and live_count == 0
        and live_social_count == 0
        and live_adventurer_count == 0
        and (login_server_listening or game_server_listening)
    )
    workflow_summary = (
        f"Workflow runtime: LoginServer {'running' if login_server_listening else 'stopped'}, "
        f"GameServer {'running' if game_server_listening else 'stopped'}."
    )
    payload: Dict[str, Any] = {
        "generatedAtMs": int(runtime.get("generatedAtMs") or int(time.time() * 1000)),
        "bridgeMode": str(runtime.get("bridgeMode") or "snapshot_bridge_with_command_queue"),
        "platformAdapterId": str(runtime.get("platformAdapterId") or "unknown"),
        "rulesetId": str(runtime.get("rulesetId") or "unknown"),
        "loadedCount": loaded_count,
        "enabledCount": enabled_count,
        "liveCount": live_count,
        "liveSocialCount": live_social_count,
        "liveAdventurerCount": live_adventurer_count,
        "definitionsLoadStatus": definitions_load_status,
        "definitionsLoadMessage": definitions_load_message,
        "definitionsLoadCount": definitions_load_count,
        "definitionsLoadGeneratedAtMs": definitions_load_generated_at_ms,
        "definitionsLoadPreserved": definitions_load_preserved,
        "studioRoot": str(runtime.get("studioRoot") or "fpc_studio"),
        "runtimeSnapshotPresent": bool(runtime),
        "loginServerListening": login_server_listening,
        "gameServerListening": game_server_listening,
        "loginServerPid": workflow_runtime.get("loginServerPid"),
        "gameServerPid": workflow_runtime.get("gameServerPid"),
        "workflowSummary": workflow_summary,
        "bridgeCountsStale": bridge_counts_stale,
    }
    if bridge_counts_stale:
        if definitions_load_message:
            payload["bridgeWarning"] = (
                "Runtime bridge counts are still zero because the latest FPC definition load did not settle cleanly. "
                + definitions_load_message
            )
        else:
            payload["bridgeWarning"] = (
                "Runtime bridge counts are still zero even though the live workflow lane sees an active server. "
                "Journey live proof uses the live workflow runtime until the snapshot bridge refreshes."
            )
    elif definitions_load_message and definitions_load_status not in {"loaded"}:
        payload["definitionLoadWarning"] = definitions_load_message
    return payload


@app.get("/studio/runtime")
def studio_runtime() -> Dict[str, Any]:
    return _studio_runtime_payload()


@app.get("/studio/platform")
def studio_platform() -> Dict[str, Any]:
    return _studio_platform_manifest()


@app.get("/studio/roster")
def studio_roster() -> Dict[str, Any]:
    return _studio_roster_payload()


@app.get("/studio/fpc/{fpc_id}")
def studio_fpc_detail(fpc_id: str) -> Dict[str, Any]:
    normalized = (fpc_id or "").strip().lower()
    if not normalized:
        raise HTTPException(status_code=400, detail="Missing FPC id.")
    try:
        return _load_studio_json(_studio_file("fpc", normalized + ".json"))
    except HTTPException as exc:
        if exc.status_code != 404:
            raise
    entries = _studio_fpc_source_entries()
    source_entry = next(
        (entry for entry in entries if _studio_snapshot_component(entry.get("id")) == normalized),
        None,
    )
    if not source_entry:
        raise HTTPException(status_code=404, detail=f"FPC detail not found: {normalized}")
    personas = _studio_persona_source_entries()
    persona_entry = next(
        (entry for entry in personas if _studio_snapshot_component(entry.get("fpcId")) == normalized),
        None,
    )
    spawn_profile = source_entry.get("spawnProfile") or {}
    return {
        "status": "ok",
        "sourceOnly": True,
        "definition": source_entry,
        "runtime": {
            "live": False,
            "state": "source-only",
            "zone": spawn_profile.get("zone", ""),
            "leaderName": "none",
            "targetName": "none",
            "debugCategories": "none",
        },
        "persona": persona_entry or {},
        "combatPower": {},
        "requestedId": normalized,
    }


@app.get("/studio/social/{fpc_id}/{player_name}")
def studio_social_detail(fpc_id: str, player_name: str) -> Dict[str, Any]:
    normalized_fpc = _studio_snapshot_component(fpc_id)
    normalized_player = _studio_snapshot_component(player_name)
    if normalized_fpc == "unknown" or normalized_player == "unknown":
        raise HTTPException(status_code=400, detail="Missing FPC id or player name.")
    return _load_studio_json(_studio_file("social", normalized_fpc + "__" + normalized_player + ".json"))


def _studio_content_list_payload(
    path: Path,
    entries: List[Dict[str, Any]],
    summary_builder: Callable[[Dict[str, Any]], Dict[str, Any]],
) -> Dict[str, Any]:
    summaries = [summary_builder(entry) for entry in entries]
    return {
        "status": "ok",
        "filePath": str(path),
        "count": len(summaries),
        "entries": summaries,
    }


def _studio_filtered_content_list_payload(
    path: Path,
    entries: List[Dict[str, Any]],
    filtered_entries: List[Dict[str, Any]],
    filters: Dict[str, Any],
) -> Dict[str, Any]:
    return {
        "status": "ok",
        "filePath": str(path),
        "count": len(filtered_entries),
        "totalCount": len(entries),
        "filters": filters,
        "entries": filtered_entries,
    }


@app.get("/studio/content/fpcs")
def studio_content_fpcs() -> Dict[str, Any]:
    return _studio_content_list_payload(FPC_DEFINITIONS_PATH, _studio_fpc_source_entries(), _studio_fpc_entry_summary)


@app.get("/studio/content/fpcs/{fpc_id}")
def studio_content_fpc_detail(fpc_id: str) -> Dict[str, Any]:
    normalized = _studio_snapshot_component(fpc_id)
    if normalized == "unknown":
        raise HTTPException(status_code=400, detail="Missing FPC id.")
    entries = _studio_fpc_source_entries()
    for entry in entries:
        if _studio_snapshot_component(entry.get("id")) == normalized:
            return {
                "status": "ok",
                "filePath": str(FPC_DEFINITIONS_PATH),
                "entry": entry,
                "summary": _studio_fpc_entry_summary(entry),
            }
    raise HTTPException(status_code=404, detail=f"FPC source entry not found: {normalized}")


@app.post("/studio/content/fpcs")
def studio_save_fpc_content(request: StudioFpcAuthorRequest) -> Dict[str, Any]:
    return _save_fpc_entry(request.originalId, request.saveMode, request.entry)


@app.get("/studio/content/fpc-templates")
def studio_content_fpc_templates() -> Dict[str, Any]:
    return _studio_content_list_payload(FPC_TEMPLATES_PATH, _studio_fpc_template_source_entries(), _studio_fpc_template_entry_summary)


@app.get("/studio/content/fpc-templates/{template_id}")
def studio_content_fpc_template_detail(template_id: str) -> Dict[str, Any]:
    normalized = _studio_snapshot_component(template_id)
    if normalized == "unknown":
        raise HTTPException(status_code=400, detail="Missing template id.")
    entries = _studio_fpc_template_source_entries()
    for entry in entries:
        if _studio_snapshot_component(entry.get("templateId")) == normalized:
            return {
                "status": "ok",
                "filePath": str(FPC_TEMPLATES_PATH),
                "entry": entry,
                "summary": _studio_fpc_template_entry_summary(entry),
            }
    raise HTTPException(status_code=404, detail=f"FPC template not found: {normalized}")


@app.post("/studio/content/fpc-templates")
def studio_save_fpc_template_content(request: StudioFpcTemplateAuthorRequest) -> Dict[str, Any]:
    return _save_fpc_template_entry(request.originalId, request.saveMode, request.entry)


@app.get("/studio/content/personas")
def studio_content_personas() -> Dict[str, Any]:
    return _studio_content_list_payload(PERSONA_PROFILES_PATH, _studio_persona_source_entries(), _studio_persona_entry_summary)


@app.get("/studio/content/personas/{fpc_id}")
def studio_content_persona_detail(fpc_id: str) -> Dict[str, Any]:
    normalized = _studio_snapshot_component(fpc_id)
    if normalized == "unknown":
        raise HTTPException(status_code=400, detail="Missing FPC id.")
    entries = _studio_persona_source_entries()
    for entry in entries:
        if _studio_snapshot_component(entry.get("fpcId")) == normalized:
            return {
                "status": "ok",
                "filePath": str(PERSONA_PROFILES_PATH),
                "entry": entry,
                "summary": _studio_persona_entry_summary(entry),
                "missing": False,
                "requestedId": normalized,
            }
    return {
        "status": "ok",
        "filePath": str(PERSONA_PROFILES_PATH),
        "entry": None,
        "summary": None,
        "missing": True,
        "requestedId": normalized,
    }


@app.post("/studio/content/personas")
def studio_save_persona_content(request: StudioPersonaAuthorRequest) -> Dict[str, Any]:
    return _save_persona_profile(request.originalId, request.saveMode, request.entry)


@app.get("/studio/content/reply-banks")
def studio_content_reply_banks(
    fpcId: str | None = None,
    bankType: str | None = None,
    category: str | None = None,
    channel: str | None = None,
    audience: str | None = None,
    contains: str | None = None,
) -> Dict[str, Any]:
    entries = _studio_reply_bank_source_entries()
    normalized_fpc = _studio_snapshot_component(fpcId) if fpcId else ""
    normalized_bank_type = (bankType or "").strip().lower()
    normalized_category = (category or "").strip().lower()
    normalized_channel = (channel or "").strip().lower()
    normalized_audience = (audience or "").strip().lower()
    contains_text = (contains or "").strip().lower()
    filtered_entries: List[Dict[str, Any]] = []
    for index, entry in enumerate(entries):
        entry_fpc = _studio_snapshot_component(entry.get("fpcId"))
        if normalized_fpc and entry_fpc != normalized_fpc:
            continue
        if normalized_bank_type and normalized_bank_type not in str(entry.get("bankType") or "").strip().lower():
            continue
        if normalized_category and normalized_category not in str(entry.get("category") or "").strip().lower():
            continue
        if normalized_channel and normalized_channel not in str(entry.get("channel") or "").strip().lower():
            continue
        if normalized_audience and normalized_audience not in str(entry.get("audience") or "").strip().lower():
            continue
        if contains_text and contains_text not in json.dumps(entry, ensure_ascii=False).lower():
            continue
        filtered_entries.append(
            {
                "index": index,
                "summary": _studio_reply_bank_entry_summary(entry, index),
                "entry": entry,
            }
        )
    return _studio_filtered_content_list_payload(
        REPLY_BANKS_PATH,
        entries,
        filtered_entries,
        {
            "fpcId": fpcId or "",
            "bankType": bankType or "",
            "category": category or "",
            "channel": channel or "",
            "audience": audience or "",
            "contains": contains or "",
        },
    )


@app.post("/studio/content/reply-banks")
def studio_save_reply_bank_content(request: StudioReplyBankAuthorRequest) -> Dict[str, Any]:
    return _save_reply_bank_entry(request.originalIndex, request.saveMode, request.entry)


@app.get("/studio/content/knowledge-cards")
def studio_content_knowledge_cards(
    fpcId: str | None = None,
    domain: str | None = None,
    questionType: str | None = None,
    channel: str | None = None,
    audience: str | None = None,
    topicId: str | None = None,
    contains: str | None = None,
) -> Dict[str, Any]:
    entries = _studio_knowledge_card_source_entries()
    normalized_fpc = _studio_snapshot_component(fpcId) if fpcId else ""
    normalized_domain = (domain or "").strip().lower()
    normalized_question_type = (questionType or "").strip().lower()
    normalized_channel = (channel or "").strip().lower()
    normalized_audience = (audience or "").strip().lower()
    normalized_topic_id = (topicId or "").strip().lower()
    contains_text = (contains or "").strip().lower()
    filtered_entries: List[Dict[str, Any]] = []
    for index, entry in enumerate(entries):
        entry_fpc = _studio_snapshot_component(entry.get("fpcId"))
        if normalized_fpc and entry_fpc != normalized_fpc:
            continue
        if normalized_domain and normalized_domain not in str(entry.get("domain") or "").strip().lower():
            continue
        if normalized_question_type and normalized_question_type not in str(entry.get("questionType") or "").strip().lower():
            continue
        if normalized_channel and normalized_channel not in str(entry.get("channel") or "").strip().lower():
            continue
        if normalized_audience and normalized_audience not in str(entry.get("audience") or "").strip().lower():
            continue
        if normalized_topic_id and normalized_topic_id not in str(entry.get("topicId") or "").strip().lower():
            continue
        if contains_text and contains_text not in json.dumps(entry, ensure_ascii=False).lower():
            continue
        filtered_entries.append(
            {
                "index": index,
                "summary": _studio_knowledge_card_entry_summary(entry, index),
                "entry": entry,
            }
        )
    return _studio_filtered_content_list_payload(
        KNOWLEDGE_CARDS_PATH,
        entries,
        filtered_entries,
        {
            "fpcId": fpcId or "",
            "domain": domain or "",
            "questionType": questionType or "",
            "channel": channel or "",
            "audience": audience or "",
            "topicId": topicId or "",
            "contains": contains or "",
        },
    )


@app.post("/studio/content/knowledge-cards")
def studio_save_knowledge_card_content(request: StudioKnowledgeCardAuthorRequest) -> Dict[str, Any]:
    return _save_knowledge_card_entry(request.originalIndex, request.saveMode, request.entry)


@app.get("/studio/content/routing-rules")
def studio_content_routing_rules(
    targetType: str | None = None,
    result: str | None = None,
    scope: str | None = None,
    contains: str | None = None,
) -> Dict[str, Any]:
    entries = _studio_routing_rule_source_entries()
    normalized_target_type = (targetType or "").strip().lower()
    normalized_result = (result or "").strip().lower()
    normalized_scope = (scope or "").strip().lower()
    contains_text = (contains or "").strip().lower()
    filtered_entries: List[Dict[str, Any]] = []
    for index, entry in enumerate(entries):
        if normalized_target_type and normalized_target_type not in str(entry.get("targetType") or "").strip().lower():
            continue
        if normalized_result and normalized_result not in str(entry.get("result") or "").strip().lower():
            continue
        if normalized_scope and normalized_scope not in str(entry.get("scope") or "").strip().lower():
            continue
        if contains_text and contains_text not in json.dumps(entry, ensure_ascii=False).lower():
            continue
        filtered_entries.append(
            {
                "index": index,
                "summary": _studio_routing_rule_entry_summary(entry, index),
                "entry": entry,
            }
        )
    return _studio_filtered_content_list_payload(
        ROUTING_RULES_PATH,
        entries,
        filtered_entries,
        {
            "targetType": targetType or "",
            "result": result or "",
            "scope": scope or "",
            "contains": contains or "",
        },
    )


@app.post("/studio/content/routing-rules")
def studio_save_routing_rule_content(request: StudioRoutingRuleAuthorRequest) -> Dict[str, Any]:
    return _save_routing_rule_entry(request.originalIndex, request.saveMode, request.entry)


@app.get("/studio/content/reply-settings")
def studio_content_reply_settings() -> Dict[str, Any]:
    entry = _studio_reply_settings_source_entry()
    return {
        "status": "ok",
        "filePath": str(REPLY_SETTINGS_PATH),
        "entry": entry,
    }


@app.post("/studio/content/reply-settings")
def studio_save_reply_settings_content(request: StudioReplySettingsAuthorRequest) -> Dict[str, Any]:
    return _save_reply_settings_entry(request.entry)


@app.get("/studio/bookmarks")
def studio_bookmarks() -> Dict[str, Any]:
    return _list_studio_bookmarks()


@app.post("/studio/bookmarks")
def studio_save_bookmark(request: StudioBookmarkRequest) -> Dict[str, Any]:
    normalized_name = (request.name or "").strip()
    normalized_fpc = _studio_snapshot_component(request.fpcId)
    if not normalized_name:
        raise HTTPException(status_code=400, detail="Bookmark name is empty.")
    if normalized_fpc == "unknown":
        raise HTTPException(status_code=400, detail="Missing FPC id.")
    bookmark_id = f"{int(time.time() * 1000)}_{_studio_bookmark_slug(normalized_name)}"
    payload = {
        "id": bookmark_id,
        "name": normalized_name,
        "createdAtMs": int(time.time() * 1000),
        "fpcId": normalized_fpc,
        "fpcName": (request.fpcName or "").strip(),
        "playerName": (request.playerName or "").strip(),
        "channel": (request.channel or "").strip().lower(),
        "text": request.text or "",
        "traceFilters": request.traceFilters or {},
        "latestProbeResult": request.latestProbeResult,
        "latestSocialSnapshot": request.latestSocialSnapshot,
        "latestExplainBaselineProbe": request.latestExplainBaselineProbe,
        "latestExplainBaselineSocialSnapshot": request.latestExplainBaselineSocialSnapshot,
        "baselineIncluded": bool(request.latestExplainBaselineProbe),
        "latestReplyTraceEntries": request.latestReplyTraceEntries or [],
        "latestSuiteRun": request.latestSuiteRun,
        "compareState": request.compareState or {},
        "suiteState": request.suiteState or {},
        "summary": f"{(request.fpcName or request.fpcId or '').strip()} :: {(request.text or '').strip()[:72]}",
    }
    _write_studio_json(_studio_bookmark_file(bookmark_id), payload)
    return payload


@app.get("/studio/bookmarks/{bookmark_id}")
def studio_bookmark_detail(bookmark_id: str) -> Dict[str, Any]:
    return _load_studio_json(_studio_bookmark_file(bookmark_id))


@app.delete("/studio/bookmarks/{bookmark_id}")
def studio_delete_bookmark(bookmark_id: str) -> Dict[str, Any]:
    path = _studio_bookmark_file(bookmark_id)
    if not path.exists():
        raise HTTPException(status_code=404, detail="Bookmark not found.")
    path.unlink()
    return {"status": "ok", "message": f"Deleted bookmark {bookmark_id}."}


@app.get("/studio/creation-projects")
def studio_creation_projects() -> Dict[str, Any]:
    return _list_studio_creation_projects()


@app.post("/studio/creation-projects")
def studio_save_creation_project(request: StudioCreationProjectRequest) -> Dict[str, Any]:
    normalized_name = (request.name or "").strip()
    if not normalized_name:
        raise HTTPException(status_code=400, detail="Creation project name is empty.")
    project_payload = request.payload if isinstance(request.payload, dict) else {}
    engine_state = project_payload.get("engineState") if isinstance(project_payload.get("engineState"), dict) else {}
    world_name = (engine_state.get("worldName") or project_payload.get("worldName") or "").strip()
    brief = (engine_state.get("brief") or project_payload.get("brief") or "").strip()
    host_id = _studio_creation_project_field(engine_state.get("hostId"), "unknown")
    program_id = _studio_creation_project_field(engine_state.get("programId"), "unknown")
    tone_id = _studio_creation_project_field(engine_state.get("toneId"))
    scale_id = _studio_creation_project_field(engine_state.get("scaleId"))
    depth_id = _studio_creation_project_field(engine_state.get("depthId"))
    existing_path = _studio_creation_project_file(request.projectId or normalized_name)
    existing = _load_studio_json_optional(existing_path)
    created_at = int(existing.get("createdAtMs")) if isinstance(existing, dict) and existing.get("createdAtMs") else int(time.time() * 1000)
    project_id = (existing.get("id") if isinstance(existing, dict) else None) or request.projectId
    if not project_id:
        project_id = f"{int(time.time() * 1000)}_{_studio_creation_project_slug(normalized_name)}"
    summary_parts = [
        world_name or normalized_name,
        program_id or "program",
        host_id or "host",
        scale_id or "",
    ]
    if brief:
        summary_parts.append(brief[:72])
    proof_summary = _studio_creation_proof_summary(
        project_payload.get("createdBundle"),
        project_payload.get("starterPackPreview"),
        project_payload.get("latestProbeResult"),
        project_payload.get("recommendation") if isinstance(project_payload.get("recommendation"), dict) else None,
    )
    payload = {
        "id": project_id,
        "name": normalized_name,
        "createdAtMs": created_at,
        "updatedAtMs": int(time.time() * 1000),
        "worldName": world_name,
        "hostId": host_id,
        "programId": program_id,
        "toneId": tone_id,
        "scaleId": scale_id,
        "depthId": depth_id,
        "summary": " :: ".join(part for part in summary_parts if part).strip(),
        "proof": proof_summary,
        "payload": project_payload,
    }
    _write_studio_json(_studio_creation_project_file(project_id), payload)
    return payload


@app.get("/studio/creation-projects/{project_id}")
def studio_creation_project_detail(project_id: str) -> Dict[str, Any]:
    return _load_studio_json(_studio_creation_project_file(project_id))


@app.delete("/studio/creation-projects/{project_id}")
def studio_delete_creation_project(project_id: str) -> Dict[str, Any]:
    path = _studio_creation_project_file(project_id)
    if not path.exists():
        raise HTTPException(status_code=404, detail="Creation project not found.")
    path.unlink()
    return {"status": "ok", "message": f"Deleted creation project {project_id}."}


@app.get("/studio/creation-publishes")
def studio_creation_publishes() -> Dict[str, Any]:
    return _list_studio_creation_publishes()


@app.post("/studio/creation-publishes")
def studio_save_creation_publish(request: StudioCreationPublishRequest) -> Dict[str, Any]:
    normalized_name = (request.name or "").strip()
    if not normalized_name:
        raise HTTPException(status_code=400, detail="Creation publish name is empty.")
    publish_payload = request.payload if isinstance(request.payload, dict) else {}
    project_payload = publish_payload.get("projectPayload") if isinstance(publish_payload.get("projectPayload"), dict) else {}
    engine_state = project_payload.get("engineState") if isinstance(project_payload.get("engineState"), dict) else {}
    recommendation = publish_payload.get("recommendation") if isinstance(publish_payload.get("recommendation"), dict) else {}
    summary_lines = recommendation.get("summaryLines") if isinstance(recommendation.get("summaryLines"), list) else []
    world_name = (
        publish_payload.get("worldName")
        or engine_state.get("worldName")
        or project_payload.get("worldName")
        or ""
    ).strip()
    host_id = _studio_creation_project_field(
        publish_payload.get("hostId") or engine_state.get("hostId"),
        "unknown",
    )
    program_id = _studio_creation_project_field(
        publish_payload.get("programId") or engine_state.get("programId"),
        "unknown",
    )
    tone_id = _studio_creation_project_field(
        publish_payload.get("toneId") or engine_state.get("toneId")
    )
    scale_id = _studio_creation_project_field(
        publish_payload.get("scaleId") or engine_state.get("scaleId")
    )
    depth_id = _studio_creation_project_field(
        publish_payload.get("depthId") or engine_state.get("depthId")
    )
    decision = _studio_creation_project_field(request.decision, "keep_draft")
    lifecycle_state = _studio_creation_project_field(
        request.lifecycleState,
        _studio_creation_publish_lifecycle_for_decision(decision),
    )
    handoff_state = _studio_creation_project_field(
        request.handoffState,
        "ready" if lifecycle_state == "handoff_ready" else "none",
    )
    package_key = _studio_creation_publish_package_key(world_name, host_id, program_id)
    project_lineage = project_payload.get("lineage") if isinstance(project_payload.get("lineage"), dict) else {}
    publish_lineage = publish_payload.get("lineage") if isinstance(publish_payload.get("lineage"), dict) else {}
    parent_package_key = _studio_creation_project_field(
        publish_lineage.get("parentPackageKey") or project_lineage.get("parentPackageKey")
    )
    family_key = _studio_creation_project_field(
        publish_lineage.get("familyKey") or project_lineage.get("familyKey") or parent_package_key or package_key,
        package_key,
    )
    parent_publish_id = _studio_creation_project_field(
        publish_lineage.get("parentPublishId") or project_lineage.get("parentPublishId")
    )
    derivation_mode = _studio_creation_project_field(
        publish_lineage.get("derivationMode") or project_lineage.get("derivationMode"),
        "root",
    )
    normalized_lineage = {
        "familyKey": family_key,
        "parentPackageKey": parent_package_key,
        "parentPublishId": parent_publish_id,
        "derivationMode": derivation_mode,
    }
    publish_payload["lineage"] = normalized_lineage
    if project_payload:
        project_payload["lineage"] = normalized_lineage
        publish_payload["projectPayload"] = project_payload
    existing_path = _studio_creation_publish_file(request.publishId or normalized_name)
    existing = _load_studio_json_optional(existing_path)
    created_at = int(existing.get("createdAtMs")) if isinstance(existing, dict) and existing.get("createdAtMs") else int(time.time() * 1000)
    publish_id = (existing.get("id") if isinstance(existing, dict) else None) or request.publishId
    version_number = 1
    if isinstance(existing, dict) and existing:
        version_number = int(existing.get("versionNumber") or 1)
    else:
        for path in _studio_creation_publishes_root().glob("*.json"):
            payload = _load_studio_json_optional(path)
            if not isinstance(payload, dict):
                continue
            if _studio_creation_project_field(payload.get("packageKey")) != package_key:
                continue
            version_number = max(version_number, int(payload.get("versionNumber") or 1) + 1)
    if not publish_id:
        publish_id = f"{int(time.time() * 1000)}_{_studio_creation_publish_slug(normalized_name)}"
    summary_parts = [
        world_name or normalized_name,
        lifecycle_state.replace("_", " "),
        f"v{version_number}",
        host_id or "host",
        program_id or "program",
    ]
    if summary_lines:
        summary_parts.append(str(summary_lines[0]).strip()[:96])
    proof_summary = _studio_creation_proof_summary(
        publish_payload.get("createdOutput"),
        publish_payload.get("packProof"),
        publish_payload.get("latestProbeResult"),
        publish_payload.get("recommendation") if isinstance(publish_payload.get("recommendation"), dict) else None,
    )
    payload = {
        "id": publish_id,
        "projectId": (request.projectId or "").strip(),
        "name": normalized_name,
        "createdAtMs": created_at,
        "updatedAtMs": int(time.time() * 1000),
        "worldName": world_name,
        "hostId": host_id,
        "programId": program_id,
        "toneId": tone_id,
        "scaleId": scale_id,
        "depthId": depth_id,
        "decision": decision,
        "lifecycleState": lifecycle_state,
        "handoffState": handoff_state,
        "packageKey": package_key,
        "familyKey": family_key,
        "parentPackageKey": parent_package_key,
        "parentPublishId": parent_publish_id,
        "derivationMode": derivation_mode,
        "lineage": normalized_lineage,
        "versionNumber": version_number,
        "versionLabel": f"v{version_number}",
        "summary": " :: ".join(part for part in summary_parts if part).strip(),
        "proof": proof_summary,
        "payload": publish_payload,
    }
    _write_studio_json(_studio_creation_publish_file(publish_id), payload)
    return payload


@app.get("/studio/creation-publishes/{publish_id}")
def studio_creation_publish_detail(publish_id: str) -> Dict[str, Any]:
    return _load_studio_json(_studio_creation_publish_file(publish_id))


@app.delete("/studio/creation-publishes/{publish_id}")
def studio_delete_creation_publish(publish_id: str) -> Dict[str, Any]:
    path = _studio_creation_publish_file(publish_id)
    if not path.exists():
        raise HTTPException(status_code=404, detail="Creation publish not found.")
    path.unlink()
    return {"status": "ok", "message": f"Deleted creation publish {publish_id}."}


@app.get("/studio/reports")
def studio_reports(
    fpcId: str | None = None,
    suiteId: str | None = None,
    status: str | None = None,
    contains: str | None = None,
) -> Dict[str, Any]:
    return _list_studio_reports(fpcId, suiteId, status, contains)


@app.post("/studio/reports")
def studio_save_report(request: StudioReportRequest) -> Dict[str, Any]:
    normalized_name = (request.name or "").strip()
    normalized_fpc = _studio_snapshot_component(request.fpcId)
    normalized_suite = _studio_report_slug(request.suiteId)
    if not normalized_name:
        raise HTTPException(status_code=400, detail="Report name is empty.")
    if normalized_fpc == "unknown":
        raise HTTPException(status_code=400, detail="Missing FPC id.")
    if not normalized_suite:
        raise HTTPException(status_code=400, detail="Missing suite id.")
    report_id = f"{int(time.time() * 1000)}_{normalized_suite}_{normalized_fpc}"
    steps = request.latestSuiteRun.get("steps") if isinstance(request.latestSuiteRun, dict) else []
    step_list = steps if isinstance(steps, list) else []
    run_summary = _suite_run_summary(request.latestSuiteRun if isinstance(request.latestSuiteRun, dict) else {})
    score_summary = (
        f"{run_summary['passedChecks']}/{run_summary['totalChecks']} checks ({run_summary['scorePct']}%)"
        if run_summary["totalChecks"]
        else "No checks"
    )
    payload = {
        "id": report_id,
        "name": normalized_name,
        "createdAtMs": int(time.time() * 1000),
        "fpcId": normalized_fpc,
        "fpcName": (request.fpcName or "").strip(),
        "suiteId": normalized_suite,
        "suiteLabel": (request.suiteLabel or request.suiteId or "").strip(),
        "description": (request.description or "").strip(),
        "status": (request.status or "").strip().lower(),
        "stepCount": len(step_list),
        "latestSuiteRun": request.latestSuiteRun,
        "latestProbeResult": request.latestProbeResult,
        "latestSocialSnapshot": request.latestSocialSnapshot,
        "latestExplainBaselineProbe": request.latestExplainBaselineProbe,
        "latestExplainBaselineSocialSnapshot": request.latestExplainBaselineSocialSnapshot,
        "baselineIncluded": bool(request.latestExplainBaselineProbe),
        "suiteRunTotals": run_summary,
        "scoreSummary": score_summary,
        "summary": f"{(request.fpcName or request.fpcId or '').strip()} :: {(request.suiteLabel or request.suiteId or '').strip()} :: {len(step_list)} steps :: {score_summary}",
    }
    _write_studio_json(_studio_report_file(report_id), payload)
    return payload


@app.get("/studio/reports/{report_id}")
def studio_report_detail(report_id: str) -> Dict[str, Any]:
    return _load_studio_json(_studio_report_file(report_id))


@app.delete("/studio/reports/{report_id}")
def studio_delete_report(report_id: str) -> Dict[str, Any]:
    path = _studio_report_file(report_id)
    if not path.exists():
        raise HTTPException(status_code=404, detail="Report not found.")
    path.unlink()
    return {"status": "ok", "message": f"Deleted report {report_id}."}


@app.get("/studio/incidents")
def studio_incidents(
    fpcId: str | None = None,
    reason: str | None = None,
    status: str | None = None,
    contains: str | None = None,
) -> Dict[str, Any]:
    return _list_studio_incidents(fpcId, reason, status, contains)


@app.post("/studio/incidents")
def studio_save_incident(request: StudioIncidentRequest) -> Dict[str, Any]:
    normalized_name = (request.name or "").strip()
    normalized_fpc = _studio_snapshot_component(request.fpcId)
    normalized_reason = _studio_incident_slug(request.reason)
    normalized_status = (request.status or "open").strip().lower() or "open"
    if not normalized_name:
        raise HTTPException(status_code=400, detail="Incident name is empty.")
    if normalized_fpc == "unknown":
        raise HTTPException(status_code=400, detail="Missing FPC id.")
    if normalized_reason == "incident":
        raise HTTPException(status_code=400, detail="Missing incident reason.")
    if normalized_status not in {"open", "reviewing", "resolved"}:
        normalized_status = "open"
    created_at_ms = int(time.time() * 1000)
    incident_id = f"{created_at_ms}_{normalized_reason}_{normalized_fpc}"
    triage = request.triage if isinstance(request.triage, dict) else {}
    summary_parts = [
        (request.fpcName or request.fpcId or "").strip(),
        normalized_reason,
        triage.get("ownerLabel") or triage.get("owner") or "",
        triage.get("severityLabel") or triage.get("severity") or "",
    ]
    note = (request.note or "").strip()
    if note:
        summary_parts.append(note[:96])
    payload = {
        "id": incident_id,
        "name": normalized_name,
        "createdAtMs": created_at_ms,
        "fpcId": normalized_fpc,
        "fpcName": (request.fpcName or "").strip(),
        "reason": normalized_reason,
        "status": normalized_status,
        "note": note,
        "triage": triage,
        "latestProbeResult": request.latestProbeResult,
        "latestSocialSnapshot": request.latestSocialSnapshot,
        "latestExplainBaselineProbe": request.latestExplainBaselineProbe,
        "latestExplainBaselineSocialSnapshot": request.latestExplainBaselineSocialSnapshot,
        "latestSuiteRun": request.latestSuiteRun,
        "summary": " :: ".join(part for part in summary_parts if part).strip(),
    }
    _write_studio_json(_studio_incident_file(incident_id), payload)
    return payload


@app.get("/studio/incidents/{incident_id}")
def studio_incident_detail(incident_id: str) -> Dict[str, Any]:
    return _load_studio_json(_studio_incident_file(incident_id))


@app.delete("/studio/incidents/{incident_id}")
def studio_delete_incident(incident_id: str) -> Dict[str, Any]:
    path = _studio_incident_file(incident_id)
    if not path.exists():
        raise HTTPException(status_code=404, detail="Incident not found.")
    path.unlink()
    return {"status": "ok", "message": f"Deleted incident {incident_id}."}


@app.get("/studio/platform-snapshots")
def studio_platform_snapshots() -> Dict[str, Any]:
    return _list_studio_platform_snapshots()


@app.get("/studio/platform-workitems")
def studio_platform_workitems() -> Dict[str, Any]:
    return _list_studio_platform_workitems()


@app.post("/studio/platform-workitems")
def studio_save_platform_workitem(request: StudioPlatformWorkItemRequest) -> Dict[str, Any]:
    capability_id = _studio_platform_workitem_slug(request.capabilityId)
    if not capability_id or capability_id == "platform_workitem":
        raise HTTPException(status_code=400, detail="Missing capability id.")
    host_focus_id = _studio_platform_host_focus(request.hostFocusId)
    workitem_id = _studio_platform_workitem_identity(capability_id, host_focus_id, request.workItemId)
    path = _studio_platform_workitem_file(workitem_id)
    existing = _load_studio_json_optional(path)
    created_at = int(existing.get("createdAtMs")) if isinstance(existing, dict) and existing.get("createdAtMs") else int(time.time() * 1000)
    status = _studio_platform_workitem_status(request.status)
    priority = _studio_platform_workitem_priority(request.priority)
    owner = (request.owner or "").strip()
    notes = (request.notes or "").strip()
    due_on = _studio_platform_workitem_due_on(request.dueOn)
    linked_report_id = _studio_report_slug(request.linkedReportId) if request.linkedReportId else ""
    linked_snapshot_id = _studio_platform_snapshot_slug(request.linkedSnapshotId) if request.linkedSnapshotId else ""
    milestone = bool(request.milestone)
    summary_parts = [status, priority]
    if milestone:
        summary_parts.append("milestone")
    summary_parts.append(owner or "unassigned")
    if host_focus_id:
        summary_parts.append(host_focus_id)
    if due_on:
        summary_parts.append(due_on)
    if notes:
        summary_parts.append(notes[:72])
    payload = {
        "id": workitem_id,
        "capabilityId": capability_id,
        "capabilityLabel": (request.capabilityLabel or capability_id).strip(),
        "ownerBand": (request.ownerBand or "").strip(),
        "status": status,
        "priority": priority,
        "owner": owner,
        "hostFocusId": host_focus_id,
        "dueOn": due_on,
        "linkedReportId": linked_report_id,
        "linkedSnapshotId": linked_snapshot_id,
        "milestone": milestone,
        "notes": notes,
        "createdAtMs": created_at,
        "updatedAtMs": int(time.time() * 1000),
        "summary": " :: ".join(part for part in summary_parts if part).strip(),
    }
    existing_history = existing.get("history") if isinstance(existing, dict) and isinstance(existing.get("history"), list) else []
    change_summary = _studio_platform_workitem_change_summary(existing if isinstance(existing, dict) else None, payload)
    if not existing_history:
        payload["history"] = [_studio_platform_workitem_history_entry(payload, change_summary)]
    elif change_summary == "No material change.":
        payload["history"] = existing_history[-40:]
    else:
        updated_history = list(existing_history[-39:])
        updated_history.append(_studio_platform_workitem_history_entry(payload, change_summary))
        payload["history"] = updated_history
    _write_studio_json(path, payload)
    return payload


@app.get("/studio/platform-workitems/{capability_id}")
def studio_platform_workitem_detail(capability_id: str) -> Dict[str, Any]:
    payload = _load_studio_json(_studio_platform_workitem_file(capability_id))
    history = payload.get("history") if isinstance(payload.get("history"), list) else []
    payload["historyCount"] = len(history)
    payload["latestHistory"] = history[-1] if history else None
    return payload


@app.delete("/studio/platform-workitems/{capability_id}")
def studio_delete_platform_workitem(capability_id: str) -> Dict[str, Any]:
    path = _studio_platform_workitem_file(capability_id)
    if not path.exists():
        raise HTTPException(status_code=404, detail="Platform work item not found.")
    path.unlink()
    return {"status": "ok", "message": f"Deleted platform work item {capability_id}."}


@app.post("/studio/platform-snapshots")
def studio_save_platform_snapshot(request: StudioPlatformSnapshotRequest) -> Dict[str, Any]:
    normalized_name = (request.name or "").strip()
    if not normalized_name:
        raise HTTPException(status_code=400, detail="Platform snapshot name is empty.")
    manifest = request.manifest if isinstance(request.manifest, dict) else {}
    identity = manifest.get("identity") if isinstance(manifest.get("identity"), dict) else {}
    content_packs = manifest.get("contentPacks") if isinstance(manifest.get("contentPacks"), dict) else {}
    snapshot_id = f"{int(time.time() * 1000)}_{_studio_platform_snapshot_slug(normalized_name)}"
    payload = dict(manifest)
    payload.update(
        {
            "id": snapshot_id,
            "name": normalized_name,
            "createdAtMs": int(time.time() * 1000),
            "summary": f"{identity.get('platformAdapterId') or 'adapter'} :: {identity.get('rulesetId') or 'ruleset'} :: defs={content_packs.get('definitionCount') or 0}",
        }
    )
    _write_studio_json(_studio_platform_snapshot_file(snapshot_id), payload)
    return payload


@app.get("/studio/platform-snapshots/{snapshot_id}")
def studio_platform_snapshot_detail(snapshot_id: str) -> Dict[str, Any]:
    return _load_studio_json(_studio_platform_snapshot_file(snapshot_id))


@app.delete("/studio/platform-snapshots/{snapshot_id}")
def studio_delete_platform_snapshot(snapshot_id: str) -> Dict[str, Any]:
    path = _studio_platform_snapshot_file(snapshot_id)
    if not path.exists():
        raise HTTPException(status_code=404, detail="Platform snapshot not found.")
    path.unlink()
    return {"status": "ok", "message": f"Deleted platform snapshot {snapshot_id}."}


@app.get("/studio/traces/{trace_name}")
def studio_trace(
    trace_name: str,
    limit: int = 20,
    speaker: str | None = None,
    player: str | None = None,
    category: str | None = None,
    contains: str | None = None,
) -> Dict[str, Any]:
    normalized = (trace_name or "").strip().lower()
    if normalized not in {"replies", "debug"}:
        raise HTTPException(status_code=400, detail="Unsupported trace name.")
    return _load_studio_jsonl(_studio_file("trace", normalized + ".jsonl"), limit, speaker, player, category, contains)


@app.get("/studio/workflow")
def studio_workflow() -> Dict[str, Any]:
    return _workflow_status_payload()


@app.post("/studio/workflow/operations")
def studio_workflow_operation(request: StudioWorkflowRequest) -> Dict[str, Any]:
    return _workflow_create_operation(request.action)


@app.get("/studio/workflow/operations/{operation_id}")
def studio_workflow_operation_result(operation_id: str) -> Dict[str, Any]:
    normalized = (operation_id or "").strip()
    if not normalized:
        raise HTTPException(status_code=400, detail="Missing workflow operation id.")
    with _WORKFLOW_LOCK:
        entry = _WORKFLOW_OPERATIONS.get(normalized)
    if not entry:
        raise HTTPException(status_code=404, detail="Workflow operation not found.")
    return _workflow_operation_copy(entry) or {"status": "missing"}


@app.get("/studio/assistant/status")
def studio_assistant_status() -> Dict[str, Any]:
    return _assistant_status_payload()


@app.post("/studio/assistant/reindex")
def studio_assistant_reindex() -> Dict[str, Any]:
    state = _queue_assistant_index("manual_reindex", force=True)
    status = _assistant_status_payload()
    return {
        "status": "accepted" if state.get("queued") else ("running" if status.get("indexing") else "ok"),
        "queued": bool(state.get("queued")),
        "indexing": bool(status.get("indexing")),
        "builtAtMs": status.get("builtAtMs"),
        "documentCount": status.get("documentCount"),
        "chunkCount": status.get("chunkCount"),
        "embeddingsEnabled": status.get("embeddingsEnabled"),
        "embeddingModel": status.get("embeddingModel"),
        "lastIndexError": status.get("lastIndexError"),
        "indexPath": str((STUDIO_ROOT / "assistant" / "index.json")),
    }


@app.post("/studio/assistant/ask")
def studio_assistant_ask(request: StudioAssistantAskRequest) -> Dict[str, Any]:
    _ensure_assistant_index("missing_index_ask")
    try:
        return answer_assistant_question(
            question=request.question,
            mode=request.mode,
            max_citations=request.maxCitations,
            history=request.history,
            repo_root=REPO_ROOT,
            studio_root=STUDIO_ROOT,
            ollama_base_url=OLLAMA_BASE_URL,
            embedding_model=ASSISTANT_EMBEDDING_MODEL,
            fast_model=ASSISTANT_FAST_MODEL,
            deep_model=ASSISTANT_DEEP_MODEL,
            runtime_supplier=_studio_runtime_payload,
            roster_supplier=_studio_roster_payload,
            workflow_supplier=_workflow_status_payload,
            trace_supplier=_assistant_trace_preview_payload,
            incident_supplier=_assistant_incident_preview_payload,
            timeout_seconds=min(ASSISTANT_TIMEOUT_SECONDS, 35.0),
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        LOGGER.warning("event=assistant_ask_failure reason=%r", exc)
        raise HTTPException(status_code=500, detail=f"Assistant ask failed: {exc}") from exc


@app.post("/studio/commands")
def studio_command(request: StudioCommandRequest) -> Dict[str, Any]:
    return _queue_studio_command(request.action, request.fpcId, request.holdMs, request.playerName, request.channel, request.text)


@app.get("/studio/results/{command_id}")
def studio_result(command_id: str) -> Dict[str, Any]:
    normalized = (command_id or "").strip()
    if not normalized:
        raise HTTPException(status_code=400, detail="Missing command id.")
    path = _studio_file("results", normalized + ".json")
    if not path.exists():
        return {"commandId": normalized, "status": "pending"}
    return _load_studio_json(path)


@app.post("/personality/decide", response_model=PersonalityDecision)
def decide(context: PersonalityContext) -> PersonalityDecision:
    provider = _provider_for_context(context)
    try:
        preflight_decision, preflight_source = _preflight_runtime_reply_budget(context)
        if preflight_decision is not None:
            normalized = _commit_runtime_reply_budget(context, preflight_decision, preflight_source or "suppressed")
            LOGGER.info(
                "event=personality_request_budgeted fakePlayerId=%s scope=%s provider=%s recentEvent=%s source=%s speakNow=%s",
                context.fakePlayerId,
                context.decisionScope,
                provider,
                context.recentEvent,
                preflight_source or "preflight",
                normalized.speakNow,
            )
            return normalized
        if provider == "rules":
            normalized = _rule_decision(context)
        else:
            raw = _call_ollama(context)
            normalized = _normalize_decision(raw, context)
        normalized = _commit_runtime_reply_budget(context, normalized, "live")
        LOGGER.info(
            "event=personality_request_normalized fakePlayerId=%s scope=%s provider=%s intent=%s zoneBias=%s speakNow=%s confidence=%.2f",
            context.fakePlayerId,
            context.decisionScope,
            provider,
            normalized.intentPreference,
            normalized.zoneBias,
            normalized.speakNow,
            normalized.confidence,
        )
        return normalized
    except Exception as exc:
        LOGGER.warning(
            "event=personality_request_fallback fakePlayerId=%s scope=%s provider=%s recentEvent=%s reason=%r",
            context.fakePlayerId,
            context.decisionScope,
            provider,
            context.recentEvent,
            exc,
        )
        return _rule_decision(context)


