"""Small release payload guards; no model, Android SDK or native build required."""

import hashlib
import unittest
from unittest.mock import patch

import verify_native_packaging as packaging


class PinnedRuntimeTest(unittest.TestCase):
    def test_verified_payload_passes_and_changed_payload_is_rejected(self):
        payload = b"verified runtime fixture"
        digest = hashlib.sha256(payload).hexdigest()
        for name in tuple(packaging.PINNED_PAYLOADS):
            with self.subTest(name=name), patch.dict(
                packaging.PINNED_PAYLOADS, {name: digest}
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
