from pathlib import Path
root = Path(__file__).resolve().parents[1]
p = root / "kyant-backdrop-engine"
required = ["LICENSE", "settings.gradle.kts", "backdrop/build.gradle.kts", "backdrop/src/commonMain/kotlin/com/kyant/backdrop/effects/Lens.kt", "backdrop/src/commonMain/kotlin/com/kyant/backdrop/internal/Shaders.kt", "backdrop/src/androidMain/kotlin/com/kyant/backdrop/RuntimeShader.kt"]
for name in required:
    assert (p / name).is_file(), f"missing upstream file: {name}"
assert "1.9.205.250" in (root / "app/build.gradle").read_text()
print("PASS: upstream source, shaders, license, Gradle and version present (not a compilation test)")
