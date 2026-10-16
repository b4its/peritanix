"""Strict tests: field-workflow protocol rules (destructive ooze trigger)."""
from __future__ import annotations

from app.services.agronomy import protocol_rules as pr


class TestWiltingDetect:
    def test_plain_indonesian(self):
        assert pr._is_wilting(["layu"]) is True

    def test_english(self):
        assert pr._is_wilting(["wilting"]) is True

    def test_case_insensitive(self):
        assert pr._is_wilting(["LAYU"]) is True

    def test_unrelated_symptom(self):
        assert pr._is_wilting(["keriting_daun", "bercak"]) is False

    def test_empty(self):
        assert pr._is_wilting([]) is False


class TestOozeTrigger:
    def test_mandatory_on_wilt_and_wet(self):
        t = pr.destructive_ooze_trigger(["layu"], local_twi=9.0)
        assert t is not None
        assert t.required and t.blocking
        assert t.code == "OOZE_TEST_MANDATORY"
        assert t.capture_s == 10
        assert len(t.steps) >= 3

    def test_no_trigger_when_dry(self):
        # Wilting but a dry (low-TWI) cell -> not the water-borne pattern.
        assert pr.destructive_ooze_trigger(["layu"], local_twi=3.0) is None

    def test_no_trigger_without_wilt(self):
        # Wet trough but no wilting symptom -> rule does not apply.
        assert pr.destructive_ooze_trigger(["bercak_daun"], local_twi=9.5) is None

    def test_threshold_boundary(self):
        assert pr.destructive_ooze_trigger(["layu"], local_twi=7.99) is None
        assert pr.destructive_ooze_trigger(["layu"], local_twi=8.0) is not None

    def test_suppressed_when_already_recorded(self):
        assert pr.destructive_ooze_trigger(["layu"], 9.0, already_recorded=True) is None

    def test_reason_mentions_both_pathogens(self):
        t = pr.destructive_ooze_trigger(["layu"], 9.0)
        assert "Ralstonia" in t.reason and "Fusarium" in t.reason

    def test_instruction_has_10s(self):
        t = pr.destructive_ooze_trigger(["layu"], 9.0)
        assert "10 detik" in t.instruction


class TestEvaluateAll:
    def test_returns_list(self):
        assert pr.evaluate_protocol_rules(["layu"], 9.0) != []
        assert pr.evaluate_protocol_rules(["layu"], 3.0) == []
        assert pr.evaluate_protocol_rules([], 9.0) == []

    def test_trigger_is_blocking(self):
        trigs = pr.evaluate_protocol_rules(["layu_bakteri"], 10.0)
        assert any(t.blocking for t in trigs)
