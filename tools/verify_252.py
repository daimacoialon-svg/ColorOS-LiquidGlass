from pathlib import Path
root=Path(__file__).resolve().parents[1]
app=(root/'app/build.gradle').read_text()
bridge=(root/'app/src/main/java/top/liquidglass/vector/KyantViewBridge251.java').read_text()
activity=(root/'app/src/main/java/top/liquidglass/vector/KyantBridgeTestActivity251.java').read_text()
assert "1.9.205.252" in app
assert 'Bitmap crop=' not in activity
assert 'Matrix localToBackdrop' in bridge
assert 'input.setLocalMatrix(localToBackdrop)' in bridge
assert (root/'kyant-backdrop-engine/backdrop/build.gradle.kts').is_file()
assert (root/'kyant-backdrop-engine/LICENSE').is_file()
assert (root/'tools/build_kyant_upstream_252.sh').is_file()
print('PASS: V252 source invariants, full-backdrop test, upstream build entry, license')
print('NOTE: this is NOT an Android compilation or full-engine integration check')
