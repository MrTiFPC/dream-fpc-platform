from __future__ import annotations

import hashlib
import json
import math
import os
import re
import subprocess
import time
from pathlib import Path
from typing import Any, Callable

import httpx

ASSISTANT_INDEX_VERSION = 1
ASSISTANT_INDEX_FILE_NAME = "index.json"
ASSISTANT_EMBED_PATHS = ("/api/embed", "/api/embeddings")
ASSISTANT_EMBED_BATCH_SIZE = 24
ASSISTANT_CHAT_PROFILE = "bounded_project_chat"
ASSISTANT_MAX_HISTORY_TURNS = 6
TOKEN_PATTERN = re.compile(r"[a-z0-9_]+", re.IGNORECASE)
SEARCHABLE_EXTENSIONS = {".java", ".json", ".md", ".py", ".xml"}
TOKEN_STOPWORDS = {
    "a",
    "about",
    "all",
    "an",
    "and",
    "are",
    "around",
    "as",
    "at",
    "be",
    "been",
    "by",
    "can",
    "current",
    "do",
    "does",
    "for",
    "from",
    "get",
    "give",
    "how",
    "i",
    "if",
    "in",
    "into",
    "is",
    "it",
    "its",
    "just",
    "like",
    "me",
    "mode",
    "more",
    "need",
    "not",
    "of",
    "on",
    "or",
    "our",
    "out",
    "please",
    "project",
    "runtime",
    "server",
    "should",
    "show",
    "so",
    "that",
    "the",
    "their",
    "them",
    "there",
    "these",
    "this",
    "to",
    "use",
    "want",
    "what",
    "when",
    "where",
    "which",
    "who",
    "why",
    "with",
    "you",
    "your",
}
ASSISTANT_CAPABILITY_LINES = [
    "Grounded project chat for Warg/FPC runtime, roster, workflow, traces, incidents, sidecar, and indexed repo truth.",
    "Not a coding agent: no broad implementation ownership, no deep architecture work, no training/fine-tuning ownership.",
    "Best at narrow operational questions, file/data location questions, behavior explanation, and safe next-step suggestions.",
]
ASSISTANT_GREETING_HINT = (
    "I can chat about the live Warg/FPC project, explain what the current stack is doing, "
    "help you find FPCs, and answer bounded questions about runtime, workflow, and behavior."
)
CHAT_FOLLOW_UP_PREFIXES = (
    "and",
    "what about",
    "how about",
    "why",
    "how so",
    "explain",
    "tell me more",
    "what do you mean",
    "are you sure",
    "really",
    "ok",
    "okay",
    "so",
    "then",
    "there",
    "him",
    "her",
    "it",
    "that",
    "those",
)


def assistant_index_path(studio_root: Path) -> Path:
    return studio_root / "assistant" / ASSISTANT_INDEX_FILE_NAME


def _assistant_root(studio_root: Path) -> Path:
    path = studio_root / "assistant"
    path.mkdir(parents=True, exist_ok=True)
    return path


def _safe_read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8-sig", errors="ignore")


def _safe_json_load(path: Path) -> Any:
    return json.loads(_safe_read_text(path))


def _normalize_whitespace(text: str) -> str:
    return re.sub(r"\s+", " ", text or "").strip()


def _slug(value: str) -> str:
    normalized = re.sub(r"[^a-z0-9_-]+", "_", (value or "").strip().lower()).strip("_")
    return normalized or "chunk"


def _tokenize(text: str) -> list[str]:
    return [token.lower() for token in TOKEN_PATTERN.findall(text or "")]


def _significant_tokens(text: str, limit: int = 8) -> list[str]:
    seen: list[str] = []
    for token in _tokenize(text):
        if len(token) < 3:
            continue
        if token in TOKEN_STOPWORDS:
            continue
        if token in seen:
            continue
        seen.append(token)
        if len(seen) >= limit:
            break
    return seen


def _word_count(text: str) -> int:
    return len(re.findall(r"\b[\w'-]+\b", text or ""))


def _indefinite_article(value: str) -> str:
    trimmed = (value or "").strip().lower()
    if not trimmed:
        return "a"
    if trimmed[0] in {"a", "e", "i", "o", "u"}:
        return "an"
    return "a"


def _normalize_chat_history(history: Any, max_turns: int = ASSISTANT_MAX_HISTORY_TURNS) -> list[dict[str, str]]:
    if not isinstance(history, list):
        return []
    cleaned: list[dict[str, str]] = []
    for item in history:
        if not isinstance(item, dict):
            continue
        role = str(item.get("role") or "").strip().lower()
        if role not in {"user", "assistant"}:
            continue
        content = _normalize_whitespace(str(item.get("content") or ""))
        if not content:
            continue
        cleaned.append({"role": role, "content": content[:420]})
    return cleaned[-max(max_turns * 2, 2) :]


def _contextualize_question(question: str, history: list[dict[str, str]]) -> str:
    trimmed = _normalize_whitespace(question)
    if not trimmed or not history:
        return trimmed
    normalized = trimmed.lower()
    token_count = len(_significant_tokens(trimmed, limit=8))
    if token_count >= 3 and not any(normalized.startswith(prefix) for prefix in CHAT_FOLLOW_UP_PREFIXES):
        return trimmed
    last_user = ""
    last_assistant = ""
    for item in reversed(history):
        if item.get("role") == "user":
            last_user = _normalize_whitespace(str(item.get("content") or ""))
            if last_user:
                break
    for item in reversed(history):
        if item.get("role") == "assistant":
            last_assistant = _normalize_whitespace(str(item.get("content") or ""))
            if last_assistant:
                break
    if not last_user and not last_assistant:
        return trimmed
    if last_user.lower() == normalized:
        return trimmed
    context_parts = []
    if last_user:
        context_parts.append(f"Previous user question: {last_user}.")
    if last_assistant:
        context_parts.append(f"Previous assistant answer: {last_assistant}.")
    context_parts.append(f"Current follow-up: {trimmed}")
    return " ".join(context_parts)


def _bounded_project_guard(question: str, mode: str) -> dict[str, Any] | None:
    normalized = _normalize_whitespace(question).lower()
    if not normalized:
        return None
    heavy_patterns = (
        r"\b(implement|patch|edit|rewrite|refactor|build|create)\b.*\b(code|class|service|feature|system|ui|console|endpoint)\b",
        r"\b(write|generate)\b.*\b(code|patch|implementation)\b",
        r"\b(train|fine[- ]?tune|lora|adapter)\b",
        r"\b(commit|push|pull request|create pr)\b",
        r"\bact like\b.*\b(codex|coding agent|developer)\b",
    )
    if not any(re.search(pattern, normalized) for pattern in heavy_patterns):
        return None
    return {
        "answer": (
            "I'm the bounded FPC Studio assistant. I can help with runtime status, roster locations, workflow meaning, "
            "sidecar/Ollama health, incidents, traces, FPC behavior, and grounded project questions from the indexed Warg/FPC docs. "
            "I'm not the right lane for broad code implementation, refactors, or training work."
        ),
        "confidence": 0.96,
        "citationIds": [],
        "suggestedActions": ["inspect_workflow", "view_traces", "open_roster"],
        "mode": mode,
        "profile": ASSISTANT_CHAT_PROFILE,
    }


def _looks_like_greeting(question: str) -> bool:
    normalized = _normalize_whitespace(question).lower()
    if not normalized:
        return False
    if _word_count(normalized) > 5:
        return False
    return bool(
        re.fullmatch(
            r"(hi|hello|hey|yo|sup|good morning|good afternoon|good evening|hiya|hello there|hey there)[!.?]?",
            normalized,
        )
    )


def _looks_like_capability_question(question: str) -> bool:
    normalized = _normalize_whitespace(question).lower()
    if not normalized:
        return False
    patterns = (
        r"\bwhat can you do\b",
        r"\bhow can you help\b",
        r"\bwhat do you do\b",
        r"\bwho are you\b",
        r"\bwhat are you\b",
        r"\bhelp me\b",
        r"\bhow does this assistant work\b",
    )
    return any(re.search(pattern, normalized) for pattern in patterns)


def _direct_meta_chat_answer(question: str, mode: str) -> dict[str, Any] | None:
    normalized = _normalize_whitespace(question).lower()
    if _looks_like_greeting(normalized):
        return {
            "answer": f"Hi. {ASSISTANT_GREETING_HINT}",
            "confidence": 0.93,
            "citationIds": [],
            "suggestedActions": ["open_roster", "inspect_workflow"],
            "mode": mode,
            "citations": [],
            "sourceBuckets": [],
        }
    if _looks_like_capability_question(normalized):
        return {
            "answer": (
                "I'm the bounded FPC Studio assistant. I'm good for live roster questions, runtime/workflow meaning, "
                "sidecar and Ollama health, trace or incident explanation, and grounded questions about FPC behavior or the indexed Warg project files. "
                "I'm not meant to take on broad coding-agent work."
            ),
            "confidence": 0.95,
            "citationIds": [],
            "suggestedActions": ["open_roster", "inspect_workflow", "view_traces"],
            "mode": mode,
            "citations": [],
            "sourceBuckets": [],
        }
    return None


def _hash_text(text: str) -> str:
    return hashlib.sha1((text or "").encode("utf-8")).hexdigest()


def _assistant_corpus_files(repo_root: Path) -> list[dict[str, Any]]:
    files: list[dict[str, Any]] = []
    explicit = [
        (repo_root / "marc" / "README.md", "canonical", "readme"),
        (repo_root / "alphastage" / "fakeainpcs" / "sidecar" / "README.md", "sidecar_doc", "doc"),
        (repo_root / "alphastage" / "fakeainpcs" / "sidecar" / "run.md", "sidecar_doc", "doc"),
        (repo_root / "alphastage" / "fakeainpcs" / "sidecar" / "api_contract.md", "sidecar_doc", "doc"),
    ]
    for path, bucket, kind in explicit:
        if path.exists():
            files.append({"path": path, "sourceBucket": bucket, "kind": kind})

    fake_players_root = repo_root / "dist" / "game" / "data" / "fake_players"
    if fake_players_root.exists():
        for path in sorted(fake_players_root.glob("*.json")):
            files.append({"path": path, "sourceBucket": "repo_data", "kind": "fake_players_data"})
    return files


def _tail_lines(path: Path, limit: int = 24) -> list[str]:
    if not path.exists():
        return []
    lines = _safe_read_text(path).splitlines()
    return lines[-limit:]


def _assistant_runtime_docs(studio_root: Path) -> list[dict[str, Any]]:
    docs: list[dict[str, Any]] = []
    runtime_path = studio_root / "runtime.json"
    roster_path = studio_root / "roster.json"
    for path, kind in ((runtime_path, "runtime_snapshot"), (roster_path, "roster_snapshot")):
        if path.exists():
            docs.append({"path": path, "sourceBucket": "live_runtime", "kind": kind})

    for folder_name, bucket, kind_prefix in (("reports", "live_report", "report"), ("incidents", "live_incident", "incident")):
        root = studio_root / folder_name
        if not root.exists():
            continue
        paths = sorted(root.glob("*.json"), key=lambda item: item.stat().st_mtime, reverse=True)[:8]
        for path in paths:
            docs.append({"path": path, "sourceBucket": bucket, "kind": kind_prefix})

    trace_root = studio_root / "trace"
    if trace_root.exists():
        for trace_name in ("replies", "debug"):
            path = trace_root / f"{trace_name}.jsonl"
            if not path.exists():
                continue
            for index, line in enumerate(_tail_lines(path, limit=20), start=1):
                stripped = line.strip()
                if not stripped:
                    continue
                docs.append(
                    {
                        "virtualId": f"{trace_name}_{index}",
                        "virtualText": stripped,
                        "title": f"{trace_name} trace {index}",
                        "path": path,
                        "sourceBucket": "live_trace",
                        "kind": "trace_entry",
                    }
                )
    return docs


def _chunk_text(
    *,
    path: Path,
    title: str,
    text: str,
    source_bucket: str,
    kind: str,
    max_chars: int = 1300,
    overlap_lines: int = 3,
) -> list[dict[str, Any]]:
    lines = text.splitlines() or [text]
    chunks: list[dict[str, Any]] = []
    current_lines: list[str] = []
    current_len = 0
    chunk_start = 1

    def flush(end_line: int) -> None:
        nonlocal current_lines, current_len, chunk_start
        chunk_text = "\n".join(current_lines).strip()
        if not chunk_text:
            current_lines = []
            current_len = 0
            chunk_start = end_line + 1
            return
        chunk_id = _slug(f"{path.stem}_{chunk_start}_{end_line}_{_hash_text(chunk_text)[:8]}")
        chunks.append(
            {
                "id": chunk_id,
                "title": title,
                "path": str(path),
                "sourceBucket": source_bucket,
                "kind": kind,
                "lineStart": chunk_start,
                "lineEnd": end_line,
                "text": chunk_text,
            }
        )
        preserved = current_lines[-overlap_lines:] if overlap_lines > 0 else []
        current_lines = list(preserved)
        current_len = sum(len(item) + 1 for item in current_lines)
        chunk_start = max(end_line - len(preserved) + 1, end_line + 1) if preserved else end_line + 1

    for index, line in enumerate(lines, start=1):
        current_lines.append(line)
        current_len += len(line) + 1
        if current_len >= max_chars and len(current_lines) > overlap_lines:
            flush(index)

    if current_lines:
        flush(len(lines))
    return chunks


def _ollama_tags(ollama_base_url: str, timeout_seconds: float) -> list[str]:
    try:
        with httpx.Client(timeout=timeout_seconds) as client:
            response = client.get(f"{ollama_base_url.rstrip('/')}/api/tags")
            response.raise_for_status()
            payload = response.json()
    except Exception:
        return []
    tags: list[str] = []
    for entry in payload.get("models", []):
        name = str(entry.get("name") or "").strip()
        if name:
            tags.append(name)
    return tags


def _model_available(tags: list[str], model: str) -> bool:
    normalized = str(model or "").strip().lower()
    if not normalized:
        return False
    for tag in tags:
        candidate = str(tag or "").strip().lower()
        if candidate == normalized:
            return True
        if candidate.startswith(normalized + ":"):
            return True
        if normalized.startswith(candidate + ":"):
            return True
    return False


def _try_embed_batch(
    *,
    ollama_base_url: str,
    embedding_model: str,
    texts: list[str],
    timeout_seconds: float,
) -> list[list[float]] | None:
    payload = {"model": embedding_model, "input": texts}
    with httpx.Client(timeout=timeout_seconds) as client:
        for endpoint in ASSISTANT_EMBED_PATHS:
            try:
                response = client.post(f"{ollama_base_url.rstrip('/')}{endpoint}", json=payload)
                response.raise_for_status()
                data = response.json()
            except Exception:
                continue
            if isinstance(data.get("embeddings"), list):
                embeddings = data.get("embeddings") or []
                normalized: list[list[float]] = []
                for item in embeddings:
                    if isinstance(item, list):
                        normalized.append([float(value) for value in item])
                if len(normalized) == len(texts):
                    return normalized
            if isinstance(data.get("embedding"), list) and len(texts) == 1:
                return [[float(value) for value in data.get("embedding")]]
    return None


def _embed_texts(
    *,
    ollama_base_url: str,
    embedding_model: str,
    texts: list[str],
    timeout_seconds: float,
    batch_size: int = ASSISTANT_EMBED_BATCH_SIZE,
) -> list[list[float]] | None:
    if not texts:
        return []
    normalized_batch_size = max(1, int(batch_size))
    vectors: list[list[float]] = []
    for start in range(0, len(texts), normalized_batch_size):
        window = texts[start : start + normalized_batch_size]
        window_timeout = max(timeout_seconds, 10.0)
        embedded = _try_embed_batch(
            ollama_base_url=ollama_base_url,
            embedding_model=embedding_model,
            texts=window,
            timeout_seconds=window_timeout,
        )
        if not embedded or len(embedded) != len(window):
            return None
        vectors.extend(embedded)
    return vectors


def _vector_norm(values: list[float]) -> float:
    return math.sqrt(sum(value * value for value in values))


def _cosine_similarity(left: list[float], right: list[float]) -> float:
    left_norm = _vector_norm(left)
    right_norm = _vector_norm(right)
    if left_norm <= 0.0 or right_norm <= 0.0:
        return 0.0
    return sum(a * b for a, b in zip(left, right)) / (left_norm * right_norm)


def _dynamic_doc_from_payload(title: str, path: str, source_bucket: str, payload: Any) -> dict[str, Any]:
    text = json.dumps(payload, ensure_ascii=False, indent=2)
    return {
        "id": _slug(f"{title}_{_hash_text(text)[:8]}"),
        "title": title,
        "path": path,
        "sourceBucket": source_bucket,
        "kind": "dynamic",
        "lineStart": 1,
        "lineEnd": len(text.splitlines()),
        "text": text,
    }


def _repo_search_chunks(repo_root: Path, question: str, limit: int = 8) -> list[dict[str, Any]]:
    tokens = _significant_tokens(question, limit=6)
    if not tokens:
        return []
    search_roots = [
        repo_root / "java" / "org" / "l2jmobius" / "gameserver" / "fakeplayer",
        repo_root / "alphastage" / "fakeainpcs" / "sidecar",
        repo_root / "dist" / "game" / "data" / "fake_players",
        repo_root / "marc",
    ]
    existing_roots = [str(path) for path in search_roots if path.exists()]
    if not existing_roots:
        return []
    pattern = "|".join(re.escape(token) for token in tokens)
    command = [
        "rg",
        "-n",
        "-S",
        "--max-count",
        "2",
        "--glob",
        "*.java",
        "--glob",
        "*.md",
        "--glob",
        "*.json",
        "--glob",
        "*.py",
        "--glob",
        "*.xml",
        pattern,
        *existing_roots,
    ]
    try:
        completed = subprocess.run(
            command,
            cwd=str(repo_root),
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="ignore",
            timeout=12,
            check=False,
        )
    except Exception:
        return []

    chunks: list[dict[str, Any]] = []
    for raw in (completed.stdout or "").splitlines():
        parts = raw.split(":", 2)
        if len(parts) < 3:
            continue
        path_text, line_text, excerpt = parts
        try:
            line_number = int(line_text)
        except ValueError:
            continue
        chunk_text = excerpt.strip()
        if not chunk_text:
            continue
        chunks.append(
            {
                "id": _slug(f"rg_{Path(path_text).stem}_{line_number}_{_hash_text(chunk_text)[:8]}"),
                "title": Path(path_text).name,
                "path": path_text,
                "sourceBucket": "repo_search",
                "kind": "search_match",
                "lineStart": line_number,
                "lineEnd": line_number,
                "text": chunk_text,
            }
        )
        if len(chunks) >= limit:
            break
    return chunks


def _score_chunk(question: str, query_tokens: list[str], chunk: dict[str, Any]) -> float:
    text = str(chunk.get("text") or "")
    text_lower = text.lower()
    path_lower = str(chunk.get("path") or "").lower()
    question_lower = question.lower().strip()
    score = 0.0
    matched = False
    if question_lower and question_lower in text_lower:
        score += 48.0
        matched = True
    for token in query_tokens:
        occurrences = text_lower.count(token)
        if occurrences:
            score += 8.0 + min(occurrences, 4) * 3.0
            matched = True
        if token in path_lower:
            score += 5.0
            matched = True
        if token == str(chunk.get("sourceBucket") or "").lower():
            score += 4.0
            matched = True
    if matched:
        bucket = str(chunk.get("sourceBucket") or "").lower()
        if bucket == "canonical":
            score += 2.0
        elif bucket.startswith("live_"):
            score += 1.0
    return score


def _dedupe_chunks(chunks: list[dict[str, Any]]) -> list[dict[str, Any]]:
    seen: set[tuple[str, int, int, str]] = set()
    deduped: list[dict[str, Any]] = []
    for chunk in chunks:
        key = (
            str(chunk.get("path") or ""),
            int(chunk.get("lineStart") or 0),
            int(chunk.get("lineEnd") or 0),
            _hash_text(str(chunk.get("text") or ""))[:12],
        )
        if key in seen:
            continue
        seen.add(key)
        deduped.append(chunk)
    return deduped


def _fallback_actions(question: str, citations: list[dict[str, Any]]) -> list[str]:
    normalized = question.lower()
    actions: list[str] = []
    if any(token in normalized for token in ("runtime", "server", "sidecar", "login", "game")):
        actions.append("inspect_workflow")
    if any(token in normalized for token in ("trace", "reply", "conversation", "why did")):
        actions.append("view_traces")
    if any(token in normalized for token in ("incident", "bad reply", "wrong answer")):
        actions.append("view_incidents")
    if any(token in normalized for token in ("index", "reindex", "refresh docs")):
        actions.append("reindex_assistant")
    if not actions and citations:
        actions.append("open_roster")
    return actions[:3]


def _fallback_answer(question: str, citations: list[dict[str, Any]], mode: str) -> dict[str, Any]:
    if not citations:
        return {
            "answer": "I do not have enough grounded evidence yet. Reindex the assistant or ask a narrower question tied to runtime, traces, or the fakeplayer/sidecar seams.",
            "confidence": 0.18,
            "citationIds": [],
            "suggestedActions": ["reindex_assistant", "inspect_workflow"],
            "mode": mode,
        }
    lead = citations[0]
    lead_excerpt = _normalize_whitespace(str(lead.get("excerpt") or ""))
    answer = f"The strongest grounded match points to {lead.get('label')}. {lead_excerpt}"
    if len(citations) > 1:
        second = citations[1]
        answer += f" I also found support in {second.get('label')}."
    return {
        "answer": answer[:420],
        "confidence": 0.56 if len(citations) > 1 else 0.46,
        "citationIds": [item["id"] for item in citations[:3]],
        "suggestedActions": _fallback_actions(question, citations),
        "mode": mode,
    }


def _assistant_decision_schema() -> dict[str, Any]:
    return {
        "type": "object",
        "properties": {
            "answer": {"type": "string"},
            "confidence": {"type": "number"},
            "citationIds": {"type": "array", "items": {"type": "string"}},
            "suggestedActions": {"type": "array", "items": {"type": "string"}},
            "mode": {"type": "string"},
        },
        "required": ["answer", "confidence", "citationIds", "suggestedActions", "mode"],
        "additionalProperties": False,
    }


def _parse_json_object(text: str) -> dict[str, Any]:
    stripped = (text or "").strip()
    if not stripped:
        raise ValueError("empty assistant model response")
    try:
        parsed = json.loads(stripped)
        if isinstance(parsed, dict):
            return parsed
    except json.JSONDecodeError:
        pass
    match = re.search(r"\{.*\}", stripped, re.DOTALL)
    if not match:
        raise ValueError("assistant model response did not contain JSON")
    parsed = json.loads(match.group(0))
    if not isinstance(parsed, dict):
        raise ValueError("assistant model response was not an object")
    return parsed


def _assistant_model_answer(
    *,
    ollama_base_url: str,
    model: str,
    timeout_seconds: float,
    question: str,
    history: list[dict[str, str]],
    mode: str,
    evidence: list[dict[str, Any]],
    draft_answer: str = "",
) -> dict[str, Any]:
    evidence_lines = []
    for item in evidence:
        excerpt = _normalize_whitespace(str(item.get("excerpt") or ""))[:420]
        evidence_lines.append(
            f"[{item['id']}] bucket={item['sourceBucket']} label={item['label']} path={item['path']} excerpt={excerpt}"
        )
    payload = {
        "model": model,
        "stream": False,
        "format": _assistant_decision_schema(),
        "messages": [
            {
                "role": "system",
                "content": (
                    "You are the bounded FPC Studio assistant for the L2J Mobius Essence Warg FPC project. "
                    "You are project-aware but deliberately limited. "
                    "Stay inside operational/runtime/helpful project chat: runtime status, roster, workflow, traces, incidents, sidecar health, fakeplayer behavior, and grounded repo/data truth. "
                    "You are not a coding agent, not an architecture lead, and not a training/fine-tuning owner. "
                    "If a request asks for broad implementation or work beyond this scope, say so plainly and redirect to the main development lane. "
                    "Use only the supplied evidence and recent chat history. "
                    "If evidence is weak or incomplete, say so plainly. "
                    "Answer like a normal chat assistant inside Studio, not like a raw search dump. "
                    "Keep the answer concise, practical, grounded, and natural to read. "
                    "Never invent citations. "
                    "Choose citationIds only from the supplied evidence ids."
                ),
            },
            {
                "role": "user",
                "content": json.dumps(
                    {
                        "profile": ASSISTANT_CHAT_PROFILE,
                        "mode": mode,
                        "question": question,
                        "history": history,
                        "evidence": evidence_lines,
                        "draftAnswer": draft_answer,
                    },
                    ensure_ascii=False,
                ),
            },
        ],
        "options": {
            "temperature": 0.1 if mode == "fast" else 0.18,
            "num_predict": 220 if mode == "fast" else 320,
            "top_p": 0.9,
        },
    }
    with httpx.Client(timeout=timeout_seconds) as client:
        response = client.post(f"{ollama_base_url.rstrip('/')}/api/chat", json=payload)
        response.raise_for_status()
        body = response.json()
    content = ((body.get("message") or {}).get("content") or "").strip()
    return _parse_json_object(content)


def _citation_label(chunk: dict[str, Any]) -> str:
    path = Path(str(chunk.get("path") or ""))
    line_start = int(chunk.get("lineStart") or 0)
    line_end = int(chunk.get("lineEnd") or 0)
    if line_start > 0 and line_end >= line_start:
        if line_start == line_end:
            return f"{path.name}:{line_start}"
        return f"{path.name}:{line_start}-{line_end}"
    return path.name or str(chunk.get("title") or "evidence")


def build_assistant_index(
    *,
    repo_root: Path,
    studio_root: Path,
    ollama_base_url: str,
    embedding_model: str,
    timeout_seconds: float = 20.0,
) -> dict[str, Any]:
    assistant_root = _assistant_root(studio_root)
    index_path = assistant_root / ASSISTANT_INDEX_FILE_NAME
    tag_models = _ollama_tags(ollama_base_url, min(timeout_seconds, 6.0))
    embedding_available = _model_available(tag_models, embedding_model)

    chunks: list[dict[str, Any]] = []
    documents: list[dict[str, Any]] = []
    sources = _assistant_corpus_files(repo_root) + _assistant_runtime_docs(studio_root)
    for source in sources:
        path = Path(source["path"])
        if source.get("virtualText") is not None:
            text = str(source.get("virtualText") or "")
            title = str(source.get("title") or path.name)
        else:
            if not path.exists():
                continue
            if path.suffix.lower() not in SEARCHABLE_EXTENSIONS and path.name not in {"README", "README.md"}:
                continue
            text = _safe_read_text(path)
            title = path.name
        if not text.strip():
            continue
        document_chunks = _chunk_text(
            path=path,
            title=title,
            text=text,
            source_bucket=str(source.get("sourceBucket") or "repo"),
            kind=str(source.get("kind") or "document"),
        )
        if not document_chunks:
            continue
        chunks.extend(document_chunks)
        documents.append(
            {
                "path": str(path),
                "title": title,
                "sourceBucket": str(source.get("sourceBucket") or "repo"),
                "kind": str(source.get("kind") or "document"),
                "chunkCount": len(document_chunks),
                "mtimeMs": int(path.stat().st_mtime * 1000) if path.exists() else int(time.time() * 1000),
            }
        )

    payload = {
        "version": ASSISTANT_INDEX_VERSION,
        "builtAtMs": int(time.time() * 1000),
        "repoRoot": str(repo_root),
        "studioRoot": str(studio_root),
        "embeddingModel": embedding_model,
        "embeddingsEnabled": False,
        "embeddingError": "",
        "embeddingBatchSize": ASSISTANT_EMBED_BATCH_SIZE,
        "documentCount": len(documents),
        "chunkCount": len(chunks),
        "documents": documents,
        "chunks": chunks,
    }
    index_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    embedding_error = ""
    if embedding_available and chunks:
        texts = [str(chunk.get("text") or "") for chunk in chunks]
        embedded = _embed_texts(
            ollama_base_url=ollama_base_url,
            embedding_model=embedding_model,
            texts=texts,
            timeout_seconds=timeout_seconds,
        )
        if embedded and len(embedded) == len(chunks):
            for chunk, vector in zip(chunks, embedded):
                chunk["embedding"] = vector
            payload["embeddingsEnabled"] = True
        else:
            embedding_available = False
            embedding_error = "Embedding model was visible but batching failed; lexical retrieval remains active."

    payload["embeddingError"] = embedding_error
    payload["builtAtMs"] = int(time.time() * 1000)
    index_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return payload


def load_assistant_index(studio_root: Path) -> dict[str, Any] | None:
    path = assistant_index_path(studio_root)
    if not path.exists():
        return None
    try:
        payload = _safe_json_load(path)
    except Exception:
        return None
    if not isinstance(payload, dict):
        return None
    return payload


def assistant_status(
    *,
    repo_root: Path,
    studio_root: Path,
    ollama_base_url: str,
    embedding_model: str,
    fast_model: str,
    deep_model: str,
    timeout_seconds: float = 6.0,
) -> dict[str, Any]:
    index = load_assistant_index(studio_root)
    tags = _ollama_tags(ollama_base_url, timeout_seconds)
    index_path = assistant_index_path(studio_root)
    return {
        "status": "ok",
        "profile": ASSISTANT_CHAT_PROFILE,
        "capabilities": list(ASSISTANT_CAPABILITY_LINES),
        "assistantRoot": str(_assistant_root(studio_root)),
        "indexPath": str(index_path),
        "indexPresent": bool(index),
        "builtAtMs": int(index.get("builtAtMs") or 0) if index else 0,
        "documentCount": int(index.get("documentCount") or 0) if index else 0,
        "chunkCount": int(index.get("chunkCount") or 0) if index else 0,
        "embeddingModel": embedding_model,
        "embeddingModelAvailable": _model_available(tags, embedding_model),
        "embeddingsEnabled": bool(index.get("embeddingsEnabled")) if index else False,
        "embeddingError": str(index.get("embeddingError") or "") if index else "",
        "embeddingBatchSize": int(index.get("embeddingBatchSize") or ASSISTANT_EMBED_BATCH_SIZE) if index else ASSISTANT_EMBED_BATCH_SIZE,
        "fastModel": fast_model,
        "fastModelAvailable": _model_available(tags, fast_model),
        "deepModel": deep_model,
        "deepModelAvailable": _model_available(tags, deep_model),
        "ollamaReachable": bool(tags),
        "repoRoot": str(repo_root),
        "studioRoot": str(studio_root),
        "availableModels": tags,
    }


def _roster_entries(payload: Any) -> list[dict[str, Any]]:
    if not isinstance(payload, dict):
        return []
    entries = payload.get("entries")
    if not isinstance(entries, list):
        return []
    return [entry for entry in entries if isinstance(entry, dict)]


def _looks_like_roster_location_question(question: str) -> bool:
    normalized = question.lower().strip()
    if not normalized:
        return False
    return (
        normalized.startswith("where is ")
        or normalized.startswith("where's ")
        or normalized.startswith("find ")
        or normalized.startswith("locate ")
        or bool(re.search(r"\bwhere\s+is\b", normalized))
        or bool(re.search(r"\bwhere's\b", normalized))
    )


def _looks_like_roster_profile_question(question: str) -> bool:
    normalized = question.lower().strip()
    if not normalized:
        return False
    return (
        normalized.startswith("tell me about ")
        or normalized.startswith("who is ")
        or normalized.startswith("what about ")
        or normalized.startswith("how is ")
        or bool(re.search(r"\btell me about\b", normalized))
        or bool(re.search(r"\bwho is\b", normalized))
        or bool(re.search(r"\bwhat about\b", normalized))
        or bool(re.search(r"\bhow is\b", normalized))
    )


def _roster_match_score(question: str, entry: dict[str, Any]) -> float:
    normalized_question = question.lower().strip()
    if not normalized_question:
        return 0.0

    entry_id = str(entry.get("id") or "").strip().lower()
    entry_name = str(entry.get("name") or "").strip().lower()
    if not entry_id and not entry_name:
        return 0.0

    score = 0.0
    if entry_name and re.search(rf"\b{re.escape(entry_name)}\b", normalized_question):
        score += 120.0
    if entry_id and re.search(rf"\b{re.escape(entry_id)}\b", normalized_question):
        score += 110.0

    query_tokens = set(_significant_tokens(normalized_question, limit=8))
    entry_tokens = set(_significant_tokens(entry_name.replace("_", " "), limit=6))
    entry_tokens.update(_significant_tokens(entry_id.replace("_", " "), limit=6))
    overlap = query_tokens.intersection(entry_tokens)
    score += float(len(overlap) * 22.0)
    if overlap and overlap == entry_tokens:
        score += 28.0
    if entry.get("live") is True:
        score += 3.0
    return score


def _resolve_roster_entry(question: str, roster_payload: Any, *, purpose: str = "location") -> dict[str, Any] | None:
    if purpose == "location":
        if not _looks_like_roster_location_question(question):
            return None
        minimum_score = 40.0
    else:
        if not _looks_like_roster_profile_question(question):
            return None
        minimum_score = 32.0

    if not question.strip():
        return None

    best_entry: dict[str, Any] | None = None
    best_score = 0.0
    second_best_score = 0.0
    for entry in _roster_entries(roster_payload):
        score = _roster_match_score(question, entry)
        if score <= 0.0:
            continue
        if score > best_score:
            second_best_score = best_score
            best_score = score
            best_entry = entry
        elif score > second_best_score:
            second_best_score = score
    if best_score < minimum_score:
        return None
    if second_best_score > 0.0 and (best_score - second_best_score) < 8.0:
        return None
    return best_entry


def _roster_entry_citation(entry: dict[str, Any]) -> dict[str, Any]:
    entry_id = str(entry.get("id") or "").strip() or "unknown"
    name = str(entry.get("name") or entry_id).strip() or entry_id
    live = bool(entry.get("live"))
    state = str(entry.get("state") or "unknown").strip() or "unknown"
    zone = str(entry.get("zone") or "unknown").strip() or "unknown"
    leader = str(entry.get("leaderName") or "").strip()
    target = str(entry.get("targetName") or "").strip()
    excerpt_parts = [
        f"name={name}",
        f"id={entry_id}",
        f"live={str(live).lower()}",
        f"state={state}",
        f"zone={zone}",
    ]
    if leader:
        excerpt_parts.append(f"leader={leader}")
    if target:
        excerpt_parts.append(f"target={target}")
    return {
        "id": _slug(f"live_roster_{entry_id}_{state}_{zone}"),
        "label": f"roster:{name}",
        "path": "live:/studio/roster",
        "sourceBucket": "live_roster",
        "lineStart": 1,
        "lineEnd": 1,
        "excerpt": " | ".join(excerpt_parts),
    }


def _direct_roster_answer(question: str, roster_payload: Any, mode: str) -> dict[str, Any] | None:
    entry = _resolve_roster_entry(question, roster_payload, purpose="location")
    if entry is None:
        return None

    name = str(entry.get("name") or entry.get("id") or "That FPC").strip()
    live = bool(entry.get("live"))
    state = str(entry.get("state") or "unknown").strip() or "unknown"
    zone = str(entry.get("zone") or "unknown").strip() or "unknown"
    leader = str(entry.get("leaderName") or "").strip()
    target = str(entry.get("targetName") or "").strip()

    if live:
        answer = f"{name} is live in zone {zone} and currently {state}."
    else:
        answer = f"{name} is not live right now. Last roster state: {state} in zone {zone}."
    if leader:
        answer += f" Leader: {leader}."
    if target and target.lower() != leader.lower():
        answer += f" Target: {target}."

    citation = _roster_entry_citation(entry)
    return {
        "answer": answer,
        "confidence": 0.94 if live else 0.82,
        "citationIds": [citation["id"]],
        "suggestedActions": ["open_roster"],
        "mode": mode,
        "citations": [citation],
        "sourceBuckets": ["live_roster"],
    }


def _direct_roster_profile_answer(question: str, roster_payload: Any, mode: str) -> dict[str, Any] | None:
    entry = _resolve_roster_entry(question, roster_payload, purpose="profile")
    if entry is None:
        return None

    name = str(entry.get("name") or entry.get("id") or "That FPC").strip()
    fpc_type = str(entry.get("type") or "FPC").strip() or "FPC"
    title = str(entry.get("title") or "").strip()
    category = str(entry.get("category") or "").strip()
    archetype = str(entry.get("archetype") or "").strip()
    persona = str(entry.get("personaTemplate") or "").strip()
    live = bool(entry.get("live"))
    state = str(entry.get("state") or "unknown").strip() or "unknown"
    zone = str(entry.get("zone") or "unknown").strip() or "unknown"
    leader = str(entry.get("leaderName") or "").strip()
    target = str(entry.get("targetName") or "").strip()

    parts = [f"{name} is {_indefinite_article(fpc_type)} {fpc_type}"]
    if title:
        parts[0] += f' titled "{title}"'
    if archetype:
        parts.append(f"Archetype: {archetype}.")
    if persona:
        parts.append(f"Persona template: {persona}.")
    if category:
        parts.append(f"Category: {category}.")
    if live:
        parts.append(f"Right now {name} is live in {zone} and {state}.")
    else:
        parts.append(f"Right now {name} is not live. Last known zone/state: {zone}, {state}.")
    if leader:
        parts.append(f"Leader: {leader}.")
    if target and target.lower() != leader.lower():
        parts.append(f"Target: {target}.")

    citation = _roster_entry_citation(entry)
    return {
        "answer": " ".join(parts),
        "confidence": 0.9 if live else 0.8,
        "citationIds": [citation["id"]],
        "suggestedActions": ["open_roster"],
        "mode": mode,
        "citations": [citation],
        "sourceBuckets": ["live_roster"],
    }


def _model_chatify_answer(
    *,
    ollama_base_url: str,
    mode: str,
    fast_model: str,
    deep_model: str,
    history: list[dict[str, str]],
    question: str,
    citations: list[dict[str, Any]],
    fallback_result: dict[str, Any],
    timeout_seconds: float,
) -> dict[str, Any]:
    model_name = fast_model if mode == "fast" else deep_model
    tags = _ollama_tags(ollama_base_url, min(timeout_seconds, 6.0))
    if not _model_available(tags, model_name):
        return fallback_result
    try:
        result = _assistant_model_answer(
            ollama_base_url=ollama_base_url,
            model=model_name,
            timeout_seconds=timeout_seconds,
            question=question,
            history=history,
            mode=mode,
            evidence=citations,
            draft_answer=str(fallback_result.get("answer") or ""),
        )
    except Exception:
        return fallback_result
    used = dict(fallback_result)
    used["answer"] = str(result.get("answer") or fallback_result.get("answer") or "").strip()
    used["confidence"] = max(0.0, min(1.0, float(result.get("confidence") or fallback_result.get("confidence") or 0.0)))
    used["suggestedActions"] = [str(item).strip() for item in (result.get("suggestedActions") or fallback_result.get("suggestedActions") or []) if str(item).strip()][:3]
    used["citationIds"] = list(result.get("citationIds") or [item["id"] for item in citations[:3]])
    used["usedModel"] = model_name
    return used


def answer_assistant_question(
    *,
    question: str,
    mode: str,
    max_citations: int,
    repo_root: Path,
    studio_root: Path,
    ollama_base_url: str,
    embedding_model: str,
    fast_model: str,
    deep_model: str,
    runtime_supplier: Callable[[], dict[str, Any]] | None = None,
    roster_supplier: Callable[[], dict[str, Any]] | None = None,
    workflow_supplier: Callable[[], dict[str, Any]] | None = None,
    trace_supplier: Callable[[], dict[str, Any]] | None = None,
    incident_supplier: Callable[[], dict[str, Any]] | None = None,
    history: list[dict[str, str]] | None = None,
    timeout_seconds: float = 25.0,
) -> dict[str, Any]:
    normalized_mode = "deep" if str(mode or "").strip().lower() == "deep" else "fast"
    trimmed_question = _normalize_whitespace(question)
    if not trimmed_question:
        raise ValueError("Assistant question is required.")
    normalized_history = _normalize_chat_history(history)
    contextual_question = _contextualize_question(trimmed_question, normalized_history)

    index = load_assistant_index(studio_root)
    bounded_guard = _bounded_project_guard(trimmed_question, normalized_mode)
    if bounded_guard is not None:
        return {
            "status": "ok",
            "question": trimmed_question,
            "answer": str(bounded_guard.get("answer") or "").strip(),
            "confidence": max(0.0, min(1.0, float(bounded_guard.get("confidence") or 0.0))),
            "citations": [],
            "sourceBuckets": [],
            "suggestedActions": list(bounded_guard.get("suggestedActions") or [])[:3],
            "mode": normalized_mode,
            "profile": ASSISTANT_CHAT_PROFILE,
            "usedModel": "",
            "indexBuiltAtMs": int(index.get("builtAtMs") or 0) if index else 0,
            "indexPath": str(assistant_index_path(studio_root)),
        }

    direct_meta = _direct_meta_chat_answer(trimmed_question, normalized_mode)
    if direct_meta is not None:
        return {
            "status": "ok",
            "question": trimmed_question,
            "answer": str(direct_meta.get("answer") or "").strip(),
            "confidence": max(0.0, min(1.0, float(direct_meta.get("confidence") or 0.0))),
            "citations": list(direct_meta.get("citations") or []),
            "sourceBuckets": list(direct_meta.get("sourceBuckets") or []),
            "suggestedActions": list(direct_meta.get("suggestedActions") or [])[:3],
            "mode": normalized_mode,
            "profile": ASSISTANT_CHAT_PROFILE,
            "usedModel": "",
            "indexBuiltAtMs": int(index.get("builtAtMs") or 0) if index else 0,
            "indexPath": str(assistant_index_path(studio_root)),
        }

    roster_payload: dict[str, Any] | None = None
    if roster_supplier is not None:
        try:
            roster_payload = roster_supplier()
        except Exception:
            roster_payload = None

    direct_roster = _direct_roster_answer(contextual_question, roster_payload, normalized_mode)
    if direct_roster is not None:
        direct_roster = _model_chatify_answer(
            ollama_base_url=ollama_base_url,
            mode=normalized_mode,
            fast_model=fast_model,
            deep_model=deep_model,
            history=normalized_history,
            question=contextual_question,
            citations=list(direct_roster.get("citations") or []),
            fallback_result=direct_roster,
            timeout_seconds=timeout_seconds,
        )
        return {
            "status": "ok",
            "question": trimmed_question,
            "answer": str(direct_roster.get("answer") or "").strip(),
            "confidence": max(0.0, min(1.0, float(direct_roster.get("confidence") or 0.0))),
            "citations": list(direct_roster.get("citations") or []),
            "sourceBuckets": list(direct_roster.get("sourceBuckets") or ["live_roster"]),
            "suggestedActions": list(direct_roster.get("suggestedActions") or ["open_roster"])[:3],
            "mode": normalized_mode,
            "profile": ASSISTANT_CHAT_PROFILE,
            "usedModel": str(direct_roster.get("usedModel") or ""),
            "indexBuiltAtMs": int(index.get("builtAtMs") or 0) if index else 0,
            "indexPath": str(assistant_index_path(studio_root)),
        }

    direct_roster_profile = _direct_roster_profile_answer(contextual_question, roster_payload, normalized_mode)
    if direct_roster_profile is not None:
        direct_roster_profile = _model_chatify_answer(
            ollama_base_url=ollama_base_url,
            mode=normalized_mode,
            fast_model=fast_model,
            deep_model=deep_model,
            history=normalized_history,
            question=contextual_question,
            citations=list(direct_roster_profile.get("citations") or []),
            fallback_result=direct_roster_profile,
            timeout_seconds=timeout_seconds,
        )
        return {
            "status": "ok",
            "question": trimmed_question,
            "answer": str(direct_roster_profile.get("answer") or "").strip(),
            "confidence": max(0.0, min(1.0, float(direct_roster_profile.get("confidence") or 0.0))),
            "citations": list(direct_roster_profile.get("citations") or []),
            "sourceBuckets": list(direct_roster_profile.get("sourceBuckets") or ["live_roster"]),
            "suggestedActions": list(direct_roster_profile.get("suggestedActions") or ["open_roster"])[:3],
            "mode": normalized_mode,
            "profile": ASSISTANT_CHAT_PROFILE,
            "usedModel": str(direct_roster_profile.get("usedModel") or ""),
            "indexBuiltAtMs": int(index.get("builtAtMs") or 0) if index else 0,
            "indexPath": str(assistant_index_path(studio_root)),
        }

    if index is None:
        index = build_assistant_index(
            repo_root=repo_root,
            studio_root=studio_root,
            ollama_base_url=ollama_base_url,
            embedding_model=embedding_model,
        )

    query_tokens = _significant_tokens(contextual_question, limit=8)
    candidate_chunks = list(index.get("chunks") or [])
    candidate_chunks.extend(_repo_search_chunks(repo_root, contextual_question, limit=8))

    if runtime_supplier is not None:
        try:
            candidate_chunks.append(
                _dynamic_doc_from_payload("runtime_snapshot", "live:/studio/runtime", "live_runtime", runtime_supplier())
            )
        except Exception:
            pass
    if roster_payload is not None:
        candidate_chunks.append(_dynamic_doc_from_payload("roster_snapshot", "live:/studio/roster", "live_roster", roster_payload))
    if workflow_supplier is not None:
        try:
            candidate_chunks.append(
                _dynamic_doc_from_payload("workflow_snapshot", "live:/studio/workflow", "live_runtime", workflow_supplier())
            )
        except Exception:
            pass
    if trace_supplier is not None:
        try:
            candidate_chunks.append(_dynamic_doc_from_payload("trace_snapshot", "live:/studio/traces", "live_trace", trace_supplier()))
        except Exception:
            pass
    if incident_supplier is not None:
        try:
            candidate_chunks.append(
                _dynamic_doc_from_payload("incident_snapshot", "live:/studio/incidents", "live_incident", incident_supplier())
            )
        except Exception:
            pass

    query_embedding: list[float] | None = None
    if bool(index.get("embeddingsEnabled")) and query_tokens:
        embedded = _try_embed_batch(
            ollama_base_url=ollama_base_url,
            embedding_model=embedding_model,
            texts=[contextual_question],
            timeout_seconds=min(timeout_seconds, 12.0),
        )
        if embedded and embedded[0]:
            query_embedding = embedded[0]

    scored: list[tuple[float, dict[str, Any]]] = []
    for chunk in _dedupe_chunks(candidate_chunks):
        score = _score_chunk(contextual_question, query_tokens, chunk)
        if query_embedding is not None and isinstance(chunk.get("embedding"), list):
            score += max(0.0, _cosine_similarity(query_embedding, chunk.get("embedding") or [])) * 24.0
        if score <= 0.0:
            continue
        scored.append((score, chunk))
    scored.sort(key=lambda item: item[0], reverse=True)

    top_chunks = [chunk for _, chunk in scored[: max(max_citations * 2, 6)]]
    citations: list[dict[str, Any]] = []
    for chunk in top_chunks[:max_citations]:
        citations.append(
            {
                "id": str(chunk.get("id") or _slug(_hash_text(str(chunk.get("text") or ""))[:8])),
                "label": _citation_label(chunk),
                "path": str(chunk.get("path") or ""),
                "sourceBucket": str(chunk.get("sourceBucket") or "repo"),
                "lineStart": int(chunk.get("lineStart") or 0),
                "lineEnd": int(chunk.get("lineEnd") or 0),
                "excerpt": _normalize_whitespace(str(chunk.get("text") or ""))[:280],
            }
        )

    model_name = fast_model if normalized_mode == "fast" else deep_model
    tags = _ollama_tags(ollama_base_url, min(timeout_seconds, 6.0))
    use_model = _model_available(tags, model_name)

    result: dict[str, Any]
    if use_model and citations:
        try:
            result = _assistant_model_answer(
                ollama_base_url=ollama_base_url,
                model=model_name,
                timeout_seconds=timeout_seconds,
                question=trimmed_question,
                history=normalized_history,
                mode=normalized_mode,
                evidence=citations,
            )
        except Exception:
            result = _fallback_answer(contextual_question, citations, normalized_mode)
    else:
        result = _fallback_answer(contextual_question, citations, normalized_mode)

    cited_ids = set(str(item) for item in (result.get("citationIds") or []))
    if cited_ids:
        selected_citations = [item for item in citations if item["id"] in cited_ids]
    else:
        selected_citations = citations[:max_citations]
    if not selected_citations:
        selected_citations = citations[:max_citations]
    source_buckets = sorted({item["sourceBucket"] for item in selected_citations})
    suggested_actions = [str(item).strip() for item in (result.get("suggestedActions") or []) if str(item).strip()]
    if not suggested_actions:
        suggested_actions = _fallback_actions(contextual_question, selected_citations)

    return {
        "status": "ok",
        "question": trimmed_question,
        "answer": str(result.get("answer") or "").strip(),
        "confidence": max(0.0, min(1.0, float(result.get("confidence") or 0.0))),
        "citations": selected_citations,
        "sourceBuckets": source_buckets,
        "suggestedActions": suggested_actions[:3],
        "mode": normalized_mode,
        "profile": ASSISTANT_CHAT_PROFILE,
        "usedModel": model_name if use_model else "",
        "indexBuiltAtMs": int(index.get("builtAtMs") or 0),
        "indexPath": str(assistant_index_path(studio_root)),
    }


