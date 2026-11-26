import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('mobile_release', ROOT / 'scripts/release/release.py')
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseTests(unittest.TestCase):
    def test_manifest_rejects_modified_apk(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / 'app.apk'
            apk.write_bytes(b'fixture')
            manifest = dict(app_id=release.APP_ID, platform='android', channel='stable', version='1.2', build=4,
                            sha256=release.digest(apk), size=apk.stat().st_size)
            (root / 'manifest.json').write_text(json.dumps(manifest))
            self.assertEqual(release.read_manifest(root)[1], manifest)
            apk.write_bytes(b'tampered')
            with self.assertRaisesRegex(ValueError, '校验'):
                release.read_manifest(root)

    def test_endpoint_validation(self):
        self.assertEqual(release.https('https://example.com/api/'), 'https://example.com/api')
        for value in ('http://example.com', 'file:///tmp/test', 'https://user:pass@example.com', 'https://example.com/#token', 'https://example.com/?key=x'):
            with self.assertRaises(ValueError):
                release.https(value)
        self.assertEqual(release.https('http://localhost/api', True), 'http://localhost/api')

    def test_version_rejects_invalid_and_path_traversal(self):
        for value in ('1.2', '1.2.3', '1.10.0'):
            self.assertEqual(release.version(value), value)
        for value in ('../tmp', '1.02', '1.0-beta', '1', '1.2.3.4'):
            with self.assertRaises(ValueError):
                release.version(value)

    def test_request_does_not_follow_redirects(self):
        self.assertIsNone(release.NoRedirect().redirect_request(None, None, 302, '', {}, 'https://other.com'))

    def test_missing_token_cannot_send_request(self):
        with patch.object(release.OPENER, 'open') as request:
            with self.assertRaises(ValueError):
                release.api({'MOBILE_RELEASE_TOKEN': 'short', 'SDK_LOG_API_URL': 'https://example.com'}, '/mobile/releases', {})
            request.assert_not_called()

    def test_public_download_must_match_size_and_hash(self):
        import io
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / 'app.apk'
            apk.write_bytes(b'correct')
            manifest = {'size': apk.stat().st_size, 'sha256': release.digest(apk)}
            with patch.object(release.OPENER, 'open', return_value=io.BytesIO(b'correct')):
                release.public_verify('https://example.com/app.apk', manifest)
            with patch.object(release.OPENER, 'open', return_value=io.BytesIO(b'incorrect')):
                with self.assertRaises(ValueError):
                    release.public_verify('https://example.com/app.apk', manifest)


if __name__ == '__main__':
    unittest.main()
