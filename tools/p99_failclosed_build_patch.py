"""P99 build-time fail-closed patch. Mutates only app/build.gradle.kts."""
from pathlib import Path
import sys

root = Path(sys.argv[1])
file = root / "app" / "build.gradle.kts"
original = file.read_text(encoding="utf-8")
old_default = '?: "https://balanced-creativity-production-f316.up.railway.app"'
old_preview = "val securePreviewUrl = project.findProperty(\"KHATMA_V2_API_BASE_URL\")?.toString().orEmpty().trimEnd('/')"
assert original.count(old_default) == 1, "unexpected legacy default: refusing patch"
assert original.count(old_preview) == 1, "unexpected V2 config: refusing patch"
assert original.count("versionCode = 121") == 1
assert original.count('versionName = "0.19.92-p97-single-server-safe"') == 1
new = original.replace(old_default, '?: "https://khatma-v2-api-staging.up.railway.app"', 1)
new = new.replace(old_preview,
    'val securePreviewUrl = (project.findProperty("KHATMA_V2_API_BASE_URL") ?: "https://khatma-v2-api-staging.up.railway.app").toString().trimEnd(\'/\')\n' +
    'check(configuredApiBaseUrl == "https://khatma-v2-api-staging.up.railway.app" && securePreviewUrl == configuredApiBaseUrl) {\n' +
    '    "AB Khatma release refuses invalid or legacy API endpoint: official Railway V2 required"\n' +
    '}', 1)
new = new.replace("versionCode = 121", "versionCode = 122", 1)
new = new.replace('versionName = "0.19.92-p97-single-server-safe"',
                  'versionName = "0.19.93-p99-security-failclosed-safe"', 1)
assert '?: "https://balanced-creativity-production-f316.up.railway.app"' not in new
assert new.count("versionCode = 122") == 1
assert new.count("check(configuredApiBaseUrl ==") == 1
file.write_text(new, encoding="utf-8")
print("PASS: P99 security patch changed exactly app/build.gradle.kts, VC122, official V2 only")
