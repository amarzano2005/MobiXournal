"""Exercise release signing preflight and certificate checks without an SDK or real key."""
import base64
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SOURCE = Path(__file__).with_name("release-apk.sh")
BASH = r"C:\Program Files\Git\bin\bash.exe" if os.name == "nt" else shutil.which("bash")
NAMES = ("MOBIX_ANDROID_KEYSTORE_BASE64", "MOBIX_ANDROID_KEY_ALIAS", "MOBIX_ANDROID_STORE_PASSWORD",
         "MOBIX_ANDROID_KEY_PASSWORD", "MOBIX_ANDROID_CERT_SHA256")

class SigningTest(unittest.TestCase):
    def run_signing(self, updates=None, missing=None):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            scripts = root / "scripts"
            scripts.mkdir()
            shutil.copyfile(SOURCE, scripts / SOURCE.name)
            (scripts / "host-build.sh").write_text("#!/bin/sh\nmkdir -p app/build/outputs/apk/release\n: > built\n: > app/build/outputs/apk/release/app-release-unsigned.apk\n")
            sdk = root / "sdk" / "build-tools" / "1.0"
            sdk.mkdir(parents=True)
            align = sdk / "zipalign"
            align.write_text('#!/bin/sh\ncp "$4" "$5"\n')
            signer = sdk / "apksigner"
            signer.write_text('''#!/bin/sh
if [ "$1" = "verify" ]; then
    echo "Signer #1 certificate SHA-256 digest: abababababababababababababababababababababababababababababababab"
    exit 0
fi
while [ "$#" -gt 0 ]; do
    if [ "$1" = "--out" ]; then shift; : > "$1"; exit 0; fi
    shift
done
exit 1
''')
            align.chmod(0o755)
            signer.chmod(0o755)
            env = {k: v for k, v in os.environ.items() if not k.startswith("MOBIX_ANDROID_")}
            env.update(dict(zip(NAMES, (base64.b64encode(b"fixture key").decode(), "alias", "store", "key", "ab" * 32))))
            env.update(MOBIX_REQUIRE_RELEASE_SIGNING="1", ANDROID_HOME=str(root / "sdk").replace("\\", "/"))
            if missing:
                env.pop(missing)
            env.update(updates or {})
            result = subprocess.run([BASH, str(scripts / SOURCE.name)], env=env, capture_output=True, text=True)
            return result, (root / "built").exists(), (root / "app/build/outputs/apk/release/app-release.apk").exists()

    def test_missing_configuration_never_starts_a_build(self):
        for name in NAMES:
            with self.subTest(name=name):
                result, built, _ = self.run_signing(missing=name)
                self.assertNotEqual(0, result.returncode)
                self.assertIn(name, result.stderr)
                self.assertFalse(built)

    def test_invalid_base64_never_starts_a_build(self):
        result, built, _ = self.run_signing({NAMES[0]: "!invalid!"})
        self.assertNotEqual(0, result.returncode)
        self.assertIn("invalid signing keystore encoding", result.stderr)
        self.assertFalse(built)

    def test_invalid_fingerprint_never_starts_a_build(self):
        result, built, _ = self.run_signing({NAMES[-1]: "not-a-fingerprint"})
        self.assertNotEqual(0, result.returncode)
        self.assertIn("MOBIX_ANDROID_CERT_SHA256", result.stderr)
        self.assertFalse(built)

    def test_wrong_certificate_removes_publishable_apk(self):
        result, built, signed = self.run_signing({NAMES[-1]: "cd" * 32})
        self.assertTrue(built, result.stderr)
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(signed)
        self.assertIn("certificate does not match", result.stderr)

    def test_expected_certificate_succeeds(self):
        result, built, signed = self.run_signing()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertTrue(built)
        self.assertTrue(signed)

if __name__ == "__main__":
    unittest.main()
