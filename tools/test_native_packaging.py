"""Small release payload guards; no model, Android SDK or native build required."""

import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import verify_native_packaging as packaging


class PinnedRuntimeTest(unittest.TestCase):
    def test_missing_build_id_is_only_allowed_when_requested(self):
        output = "LOAD 0x0 0x0 0x0 0x100 0x100 R E 0x4000\n"
        with (
            tempfile.TemporaryDirectory() as temporary,
            patch.object(packaging.subprocess, "check_output", return_value=output),
        ):
            with self.assertRaisesRegex(ValueError, "missing build ID"):
                packaging.inspect(b"fixture", "readelf", Path(temporary))
            _, alignment, build_id = packaging.inspect(
                b"fixture", "readelf", Path(temporary), allow_missing_build_id=True
            )
            self.assertEqual(alignment, [0x4000])
            self.assertIsNone(build_id)

    def test_load_segments_are_still_required_for_pinned_runtime(self):
        with (
            tempfile.TemporaryDirectory() as temporary,
            patch.object(
                packaging.subprocess, "check_output", return_value="Build ID: 1234\n"
            ),
        ):
            with self.assertRaisesRegex(ValueError, "LOAD segments"):
                packaging.inspect(
                    b"fixture", "readelf", Path(temporary), allow_missing_build_id=True
                )

    def test_verified_payload_passes_and_changed_payload_is_rejected(self):
        payload = b"verified runtime fixture"
        digest = hashlib.sha256(payload).hexdigest()
        for name in tuple(packaging.PINNED_PAYLOADS):
            with (
                self.subTest(name=name),
                patch.dict(packaging.PINNED_PAYLOADS, {name: digest}),
            ):
                packaging.verify_pinned_payload(name, payload)
                with self.assertRaisesRegex(ValueError, "Pinned payload bytes changed"):
                    packaging.verify_pinned_payload(name, payload + b"changed")

    def test_plain_cleanup_passes_and_speculative_cleanup_is_rejected(self):
        packaging.verify_pinned_payload("libs1mini.so", b"ordinary greedy cleanup")
        with self.assertRaisesRegex(ValueError, "speculation must remain disabled"):
            packaging.verify_pinned_payload("libs1mini.so", b"report ngramPoolBytes")


if __name__ == "__main__":
    unittest.main()
