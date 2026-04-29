import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from fastapi.testclient import TestClient


SIDE_CAR_ROOT = Path(__file__).resolve().parents[1]
if str(SIDE_CAR_ROOT) not in sys.path:
    sys.path.insert(0, str(SIDE_CAR_ROOT))

import app as sidecar_app  # noqa: E402
import assistant_runtime  # noqa: E402


class ConsoleAssistantContractTests(unittest.TestCase):
    def setUp(self):
        self._tempdir = tempfile.TemporaryDirectory()
        self._temp_path = Path(self._tempdir.name)
        self._original_studio_root = sidecar_app.STUDIO_ROOT
        self._original_fast_model = sidecar_app.ASSISTANT_FAST_MODEL
        self._original_assistant_index_state = dict(sidecar_app._ASSISTANT_INDEX_STATE)
        sidecar_app.STUDIO_ROOT = self._temp_path
        sidecar_app.ASSISTANT_FAST_MODEL = "missing-fast-model-for-test"
        assistant_root = self._temp_path / "assistant"
        assistant_root.mkdir(parents=True, exist_ok=True)
        (assistant_root / "index.json").write_text(
            json.dumps(
                {
                    "version": 1,
                    "builtAtMs": 1776000000000,
                    "documentCount": 0,
                    "chunkCount": 0,
                    "embeddingModel": "embeddinggemma",
                    "embeddingsEnabled": False,
                    "documents": [],
                    "chunks": [],
                },
                indent=2,
            ),
            encoding="utf-8",
        )
        sidecar_app._ASSISTANT_INDEX_STATE.update(
            {
                "indexing": False,
                "lastTrigger": "",
                "lastQueuedAtMs": 0,
                "lastStartedAtMs": 0,
                "lastCompletedAtMs": 0,
                "lastError": "",
            }
        )
        self.client = TestClient(sidecar_app.app)

    def tearDown(self):
        sidecar_app.STUDIO_ROOT = self._original_studio_root
        sidecar_app.ASSISTANT_FAST_MODEL = self._original_fast_model
        sidecar_app._ASSISTANT_INDEX_STATE.clear()
        sidecar_app._ASSISTANT_INDEX_STATE.update(self._original_assistant_index_state)
        self._tempdir.cleanup()

    def test_assistant_status_reports_cached_index_shape(self):
        assistant_root = self._temp_path / "assistant"
        payload = {
            "version": 1,
            "builtAtMs": 1776000000123,
            "documentCount": 2,
            "chunkCount": 4,
            "embeddingModel": "embeddinggemma",
            "embeddingsEnabled": False,
            "documents": [],
            "chunks": [],
        }
        (assistant_root / "index.json").write_text(json.dumps(payload, indent=2), encoding="utf-8")

        response = self.client.get("/studio/assistant/status")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertTrue(data["indexPresent"])
        self.assertEqual(data["documentCount"], 2)
        self.assertEqual(data["chunkCount"], 4)
        self.assertEqual(data["embeddingModel"], "embeddinggemma")
        self.assertEqual(data["fastModel"], "missing-fast-model-for-test")
        self.assertEqual(data["profile"], "bounded_project_chat")
        self.assertTrue(data["capabilities"])
        self.assertFalse(data["indexing"])
        self.assertEqual(data["lastIndexError"], "")

    def test_assistant_reindex_endpoint_queues_background_build(self):
        assistant_root = self._temp_path / "assistant"
        index_path = assistant_root / "index.json"
        if index_path.exists():
            index_path.unlink()

        class FakeThread:
            def __init__(self, target=None, args=(), name=None, daemon=None):
                self.target = target
                self.args = args
                self.name = name
                self.daemon = daemon

            def start(self):
                return None

        with mock.patch.object(sidecar_app.threading, "Thread", FakeThread):
            response = self.client.post("/studio/assistant/reindex", json={})

        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["status"], "accepted")
        self.assertTrue(data["queued"])
        self.assertTrue(data["indexing"])

    def test_assistant_ask_returns_grounded_citations_from_cached_index(self):
        assistant_root = self._temp_path / "assistant"
        assistant_root.mkdir(parents=True, exist_ok=True)
        payload = {
            "version": 1,
            "builtAtMs": 1776000000456,
            "documentCount": 1,
            "chunkCount": 1,
            "embeddingModel": "embeddinggemma",
            "embeddingsEnabled": False,
            "documents": [
                {
                    "path": str(self._temp_path / "docs" / "glacier_studio.md"),
                    "title": "glacier_studio.md",
                    "sourceBucket": "canonical",
                    "kind": "doc",
                    "chunkCount": 1,
                    "mtimeMs": 1776000000456,
                }
            ],
            "chunks": [
                {
                    "id": "glacier_control_chunk",
                    "title": "glacier_studio.md",
                    "path": str(self._temp_path / "docs" / "glacier_studio.md"),
                    "sourceBucket": "canonical",
                    "kind": "doc",
                    "lineStart": 1,
                    "lineEnd": 3,
                    "text": "The glacier relay control cadence is 1000 ms for runtime and workflow polling, and 2000 ms for traces and incidents.",
                }
            ],
        }
        (assistant_root / "index.json").write_text(json.dumps(payload, indent=2), encoding="utf-8")

        response = self.client.post(
            "/studio/assistant/ask",
            json={"question": "what is the glacier relay control cadence", "mode": "fast", "maxCitations": 3},
        )
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["status"], "ok")
        self.assertEqual(data["mode"], "fast")
        self.assertGreaterEqual(data["confidence"], 0.4)
        self.assertTrue(data["citations"])
        self.assertIn("canonical", data["sourceBuckets"])
        self.assertIn("1000", data["answer"])
        self.assertEqual(data["citations"][0]["label"], "glacier_studio.md:1-3")

    def test_assistant_ask_uses_live_roster_for_direct_location_questions(self):
        assistant_root = self._temp_path / "assistant"
        assistant_root.mkdir(parents=True, exist_ok=True)
        payload = {
            "version": 1,
            "builtAtMs": 1776000000789,
            "documentCount": 0,
            "chunkCount": 0,
            "embeddingModel": "embeddinggemma",
            "embeddingsEnabled": False,
            "documents": [],
            "chunks": [],
        }
        (assistant_root / "index.json").write_text(json.dumps(payload, indent=2), encoding="utf-8")
        roster_payload = {
            "generatedAtMs": 1776000000901,
            "entries": [
                {
                    "id": "marc",
                    "name": "Marc",
                    "live": True,
                    "state": "idle",
                    "zone": "giran",
                    "leaderName": "PlayerOne",
                    "targetName": "PlayerOne",
                }
            ],
        }
        (self._temp_path / "roster.json").write_text(json.dumps(roster_payload, indent=2), encoding="utf-8")

        response = self.client.post(
            "/studio/assistant/ask",
            json={"question": "where is marc", "mode": "fast", "maxCitations": 4},
        )
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["status"], "ok")
        self.assertIn("Marc is live in zone giran", data["answer"])
        self.assertIn("live_roster", data["sourceBuckets"])
        self.assertEqual(data["citations"][0]["label"], "roster:Marc")
        self.assertEqual(data["citations"][0]["sourceBucket"], "live_roster")
        self.assertEqual(data["suggestedActions"], ["open_roster"])
        self.assertEqual(data["profile"], "bounded_project_chat")

    def test_assistant_ask_accepts_chat_history_for_follow_up_context(self):
        assistant_root = self._temp_path / "assistant"
        assistant_root.mkdir(parents=True, exist_ok=True)
        payload = {
            "version": 1,
            "builtAtMs": 1776000000999,
            "documentCount": 0,
            "chunkCount": 0,
            "embeddingModel": "embeddinggemma",
            "embeddingsEnabled": False,
            "documents": [],
            "chunks": [],
        }
        (assistant_root / "index.json").write_text(json.dumps(payload, indent=2), encoding="utf-8")
        roster_payload = {
            "generatedAtMs": 1776000001001,
            "entries": [
                {
                    "id": "marc",
                    "name": "Marc",
                    "live": True,
                    "state": "idle",
                    "zone": "giran",
                    "leaderName": "PlayerOne",
                    "targetName": "PlayerOne",
                }
            ],
        }
        (self._temp_path / "roster.json").write_text(json.dumps(roster_payload, indent=2), encoding="utf-8")

        response = self.client.post(
            "/studio/assistant/ask",
            json={
                "question": "where is he",
                "mode": "fast",
                "maxCitations": 4,
                "history": [{"role": "user", "content": "where is marc"}],
            },
        )
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertIn("Marc is live in zone giran", data["answer"])
        self.assertEqual(data["profile"], "bounded_project_chat")

    def test_contextualize_question_uses_last_assistant_answer_for_short_follow_up(self):
        contextualized = assistant_runtime._contextualize_question(
            "why?",
            [
                {"role": "user", "content": "where is marc"},
                {"role": "assistant", "content": "Marc is live in zone giran and currently idle."},
            ],
        )
        self.assertIn("Previous user question: where is marc.", contextualized)
        self.assertIn("Previous assistant answer: Marc is live in zone giran and currently idle.", contextualized)
        self.assertTrue(contextualized.endswith("Current follow-up: why?"))

    def test_assistant_ask_guardrails_heavy_coding_requests(self):
        response = self.client.post(
            "/studio/assistant/ask",
            json={"question": "implement a new service and patch the codebase for me", "mode": "fast", "maxCitations": 4},
        )
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["status"], "ok")
        self.assertIn("bounded FPC Studio assistant", data["answer"])
        self.assertEqual(data["profile"], "bounded_project_chat")
        self.assertFalse(data["citations"])

    def test_assistant_ask_handles_greeting_without_irrelevant_citations(self):
        response = self.client.post(
            "/studio/assistant/ask",
            json={"question": "hi", "mode": "fast", "maxCitations": 4},
        )
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["status"], "ok")
        self.assertIn("I can chat about the live Warg/FPC project", data["answer"])
        self.assertFalse(data["citations"])

    def test_assistant_ask_uses_roster_for_profile_questions(self):
        assistant_root = self._temp_path / "assistant"
        assistant_root.mkdir(parents=True, exist_ok=True)
        payload = {
            "version": 1,
            "builtAtMs": 1776000001111,
            "documentCount": 0,
            "chunkCount": 0,
            "embeddingModel": "embeddinggemma",
            "embeddingsEnabled": False,
            "documents": [],
            "chunks": [],
        }
        (assistant_root / "index.json").write_text(json.dumps(payload, indent=2), encoding="utf-8")
        roster_payload = {
            "generatedAtMs": 1776000001222,
            "entries": [
                {
                    "id": "marc",
                    "name": "Marc",
                    "title": "Origin of the FPCs",
                    "type": "AFPC",
                    "category": "generic_fpc",
                    "archetype": "town_anchor",
                    "personaTemplate": "origin_guide",
                    "live": True,
                    "state": "idle",
                    "zone": "giran",
                    "leaderName": "",
                    "targetName": "",
                }
            ],
        }
        (self._temp_path / "roster.json").write_text(json.dumps(roster_payload, indent=2), encoding="utf-8")

        response = self.client.post(
            "/studio/assistant/ask",
            json={"question": "tell me about marc", "mode": "fast", "maxCitations": 4},
        )
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["status"], "ok")
        self.assertIn('Marc is an AFPC titled "Origin of the FPCs"', data["answer"])
        self.assertIn("Right now Marc is live in giran and idle.", data["answer"])
        self.assertEqual(data["citations"][0]["label"], "roster:Marc")
        self.assertEqual(data["sourceBuckets"], ["live_roster"])

    def test_assistant_ask_can_chatify_direct_roster_answers_through_model(self):
        assistant_root = self._temp_path / "assistant"
        assistant_root.mkdir(parents=True, exist_ok=True)
        payload = {
            "version": 1,
            "builtAtMs": 1776000001333,
            "documentCount": 0,
            "chunkCount": 0,
            "embeddingModel": "embeddinggemma",
            "embeddingsEnabled": False,
            "documents": [],
            "chunks": [],
        }
        (assistant_root / "index.json").write_text(json.dumps(payload, indent=2), encoding="utf-8")
        roster_payload = {
            "generatedAtMs": 1776000001444,
            "entries": [
                {
                    "id": "marc",
                    "name": "Marc",
                    "live": True,
                    "state": "idle",
                    "zone": "giran",
                    "leaderName": "PlayerOne",
                    "targetName": "PlayerOne",
                }
            ],
        }
        (self._temp_path / "roster.json").write_text(json.dumps(roster_payload, indent=2), encoding="utf-8")

        with mock.patch.object(assistant_runtime, "_ollama_tags", return_value=["missing-fast-model-for-test:latest"]):
            with mock.patch.object(
                assistant_runtime,
                "_assistant_model_answer",
                return_value={
                    "answer": "Marc is in Giran right now, standing idle and still tied to PlayerOne as leader.",
                    "confidence": 0.91,
                    "citationIds": ["live_roster_marc_idle_giran"],
                    "suggestedActions": ["open_roster"],
                    "mode": "fast",
                },
            ):
                response = self.client.post(
                    "/studio/assistant/ask",
                    json={"question": "where is marc", "mode": "fast", "maxCitations": 4},
                )

        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["status"], "ok")
        self.assertEqual(data["usedModel"], "missing-fast-model-for-test")
        self.assertIn("Marc is in Giran right now", data["answer"])
        self.assertEqual(data["citations"][0]["label"], "roster:Marc")

    def test_build_assistant_index_batches_embeddings_for_visible_model_tags(self):
        repo_root = self._temp_path / "repo"
        docs_root = repo_root / "marc"
        docs_root.mkdir(parents=True, exist_ok=True)
        (docs_root / "README.md").write_text(("Marc companion Studio.\n" * 220).strip(), encoding="utf-8")

        calls: list[int] = []

        def fake_embed_batch(*, ollama_base_url, embedding_model, texts, timeout_seconds):
            calls.append(len(texts))
            return [[0.1, 0.2, 0.3] for _ in texts]

        with mock.patch.object(assistant_runtime, "_assistant_runtime_docs", return_value=[]):
            with mock.patch.object(assistant_runtime, "_ollama_tags", return_value=["embeddinggemma:latest"]):
                with mock.patch.object(assistant_runtime, "_try_embed_batch", side_effect=fake_embed_batch):
                    payload = assistant_runtime.build_assistant_index(
                        repo_root=repo_root,
                        studio_root=self._temp_path,
                        ollama_base_url="http://127.0.0.1:11434",
                        embedding_model="embeddinggemma",
                        timeout_seconds=5.0,
                    )

        self.assertTrue(payload["embeddingsEnabled"])
        self.assertEqual(payload["embeddingBatchSize"], assistant_runtime.ASSISTANT_EMBED_BATCH_SIZE)
        self.assertTrue(calls)
        self.assertLessEqual(max(calls), assistant_runtime.ASSISTANT_EMBED_BATCH_SIZE)
        self.assertTrue(any("embedding" in chunk for chunk in payload["chunks"]))


if __name__ == "__main__":
    unittest.main()
