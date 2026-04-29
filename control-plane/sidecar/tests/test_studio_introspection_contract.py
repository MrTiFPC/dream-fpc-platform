import json
import sys
import tempfile
import unittest
from pathlib import Path

from fastapi.testclient import TestClient


SIDE_CAR_ROOT = Path(__file__).resolve().parents[1]
if str(SIDE_CAR_ROOT) not in sys.path:
    sys.path.insert(0, str(SIDE_CAR_ROOT))

import app as sidecar_app  # noqa: E402


class StudioIntrospectionContractTests(unittest.TestCase):
    def setUp(self):
        self._tempdir = tempfile.TemporaryDirectory()
        self._temp_path = Path(self._tempdir.name)
        self._original_studio_root = sidecar_app.STUDIO_ROOT
        sidecar_app.STUDIO_ROOT = self._temp_path
        self.client = TestClient(sidecar_app.app)

    def tearDown(self):
        sidecar_app.STUDIO_ROOT = self._original_studio_root
        self._tempdir.cleanup()

    def test_reports_endpoint_surfaces_scored_suite_totals(self):
        reports_root = sidecar_app._studio_file("reports")
        reports_root.mkdir(parents=True, exist_ok=True)
        payload = {
            "id": "report_marc_contract",
            "name": "Marc Introspection Humanization :: 4 steps :: 18/18 checks",
            "createdAtMs": 1775010120279,
            "fpcId": "marc",
            "fpcName": "Marc",
            "suiteId": "marc_introspection_humanization",
            "suiteLabel": "Marc Introspection Humanization",
            "status": "completed",
            "latestSuiteRun": {
                "suiteId": "marc_introspection_humanization",
                "status": "completed",
                "steps": [
                    {"label": "Marc: What Would Make You Trust Me?", "passedChecks": 6, "totalChecks": 6, "failed": False},
                    {"label": "Marc: What Are You Trying To Protect Now?", "passedChecks": 4, "totalChecks": 4, "failed": False},
                    {"label": "Marc: How Do You Decide To Forgive Someone?", "passedChecks": 4, "totalChecks": 4, "failed": False},
                    {"label": "Marc: What Do You Fear?", "passedChecks": 4, "totalChecks": 4, "failed": False},
                ],
            },
            "latestProbeResult": {
                "relationship": {
                    "retrievedMemorySummary": "Memory focus with PlayerOne: Priority: Evaluate trust through consistency. Reflection: The bond with PlayerOne is still being tested.",
                    "memoryPrioritySummary": "Evaluate trust through consistency.",
                    "memoryReflectionSummary": "The bond with PlayerOne is still being tested.",
                    "memoryTrustSummary": "Trust with PlayerOne is conditional and still weighs steadiness over charm.",
                    "memorySelectionSummary": "Relationship cue > Relationship memory > Social memory",
                    "retrievalSections": [
                        {"label": "Relationship cue", "value": "Marc still tests PlayerOne through returns, honesty, and patience.", "score": 92},
                        {"label": "Relationship memory", "value": "familiarity=4, trust=5, respect=5, tension=2, goal=stability", "score": 74},
                    ],
                },
                "reflectionLens": "Sees every exchange through the question of whether this bond will last.",
                "memoryRetrievalPolicy": "prefer recent, relevant, and relationship-critical memories first.",
            },
        }
        (reports_root / "report_marc_contract.json").write_text(json.dumps(payload, indent=2), encoding="utf-8")

        response = self.client.get("/studio/reports", params={"suiteId": "marc_introspection_humanization"})
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["count"], 1)
        self.assertEqual(data["entries"][0]["passedChecks"], 18)
        self.assertEqual(data["entries"][0]["totalChecks"], 18)
        self.assertEqual(data["entries"][0]["scorePct"], 100)
        self.assertEqual(data["stats"]["averageScorePct"], 100)
        self.assertEqual(data["stats"]["failedSteps"], 0)

    def test_trace_endpoint_filters_by_category_and_contains_priority(self):
        trace_root = sidecar_app._studio_file("trace")
        trace_root.mkdir(parents=True, exist_ok=True)
        entries = [
            {
                "probe": {
                    "speakerName": "Elyra",
                    "playerName": "PlayerOne",
                    "classification": {"messageCategory": "self_state_reflection"},
                    "relationship": {
                        "matchedRelationshipSummary": "Current addressee PlayerOne matches this relationship cue: Elyra still filters trust through Marc's safety.",
                        "retrievedMemorySummary": "Memory focus with PlayerOne: Priority: Reward steady cooperation without overpromising. Reflection: The bond with PlayerOne is opening.",
                        "memoryPrioritySummary": "Reward steady cooperation without overpromising.",
                        "memoryReflectionSummary": "The bond with PlayerOne is opening.",
                        "memoryPressureSummary": "Current pressure with PlayerOne centers on need=steady presence, topic=self_bel.",
                        "retrievalSections": [
                            {"label": "Relationship cue", "value": "Elyra still filters trust through Marc's safety.", "score": 95},
                            {"label": "Social memory", "value": "guarded but opening through steady cooperation", "score": 81},
                        ],
                    },
                }
            },
            {
                "probe": {
                    "speakerName": "Marc",
                    "playerName": "PlayerOne",
                    "classification": {"messageCategory": "bond_opinion"},
                    "relationship": {
                        "retrievedMemorySummary": "Memory focus with PlayerOne: Reflection only."
                    },
                }
            },
        ]
        trace_path = trace_root / "replies.jsonl"
        trace_path.write_text("\n".join(json.dumps(entry) for entry in entries) + "\n", encoding="utf-8")

        response = self.client.get(
            "/studio/traces/replies",
            params={"speaker": "Elyra", "category": "self_state_reflection", "contains": "priority"},
        )
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["matched"], 1)
        self.assertEqual(data["returned"], 1)
        self.assertEqual(
            data["entries"][0]["probe"]["relationship"]["matchedRelationshipSummary"],
            "Current addressee PlayerOne matches this relationship cue: Elyra still filters trust through Marc's safety.",
        )
        memory_text = data["entries"][0]["probe"]["relationship"]["retrievedMemorySummary"]
        self.assertIn("Priority:", memory_text)
        self.assertIn("Reflection:", memory_text)
        self.assertEqual(
            data["entries"][0]["probe"]["relationship"]["memoryPrioritySummary"],
            "Reward steady cooperation without overpromising.",
        )
        self.assertEqual(
            data["entries"][0]["probe"]["relationship"]["memoryPressureSummary"],
            "Current pressure with PlayerOne centers on need=steady presence, topic=self_bel.",
        )
        self.assertEqual(
            data["entries"][0]["probe"]["relationship"]["retrievalSections"][0]["label"],
            "Relationship cue",
        )
        self.assertEqual(
            data["entries"][0]["probe"]["relationship"]["retrievalSections"][1]["score"],
            81,
        )


if __name__ == "__main__":
    unittest.main()
