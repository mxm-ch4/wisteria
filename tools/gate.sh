#!/usr/bin/env bash
# Runs the readiness gate headless: tools/gate.sh [OPENGL|VULKAN]
# Needs JDK 25, Xvfb and Mesa (on Windows, run ./gradlew runClientGameTest directly). Audio is written to
# build/gate/<run>.wav through OpenAL Soft's wave backend, so the run works without a sound card.
set -euo pipefail
cd "$(dirname "$0")/.."
backend="${1:-}"
run="gate${backend:+-$backend}"
out="$PWD/build/gate"
mkdir -p "$out"
# Start the first launch from an empty game directory: no old worlds, options, frames or client config.
rm -rf "build/run/clientGameTest"
printf '[general]\ndrivers=wave\nfrequency=44100\n[wave]\nfile=%s/%s.wav\n' "$out" "$run" > "$out/$run.alsoft.conf"
export ALSOFT_DRIVERS=wave ALSOFT_CONF="$out/$run.alsoft.conf"
args=(runClientGameTest --console=plain)
[ -n "$backend" ] && args+=("-Pbackend=$backend")
status=0
: > "$out/$run.restart.log"
xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew "${args[@]}" > "$out/$run.log" 2>&1 || status=$?
# Second launch over the same game directory: did the settings from the first one survive a real restart?
if [ $status -eq 0 ]; then
	WISTERIA_GATE_PHASE=restart xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew "${args[@]}" > "$out/$run.restart.log" 2>&1 || status=$?
	if [ $status -eq 0 ] && ! grep -q "gate restart check passed" "$out/$run.restart.log"; then
		echo "gate.sh: the restart phase did not run" >&2
		status=4
	fi
fi
mkdir -p "$out/$run"
rm -f "$out/$run"/*.png
cp build/run/clientGameTest/gate/*.png "$out/$run/" 2>/dev/null || true
cat "$out/$run.log" "$out/$run.restart.log" 2>/dev/null | grep -E "WisteriaGate|Using graphics backend|Gate failed|BUILD" | sed -E 's/^\[[0-9:]+\] \[[^]]+\] //' || true
# The game silently falls back to the other backend if the requested one cannot start: that is not a pass.
if [ $status -eq 0 ] && [ -n "$backend" ]; then
	wanted=$([ "$backend" = VULKAN ] && echo Vulkan || echo OpenGL)
	if ! grep -q "gate passed: .* on $wanted" "$out/$run.log"; then
		echo "gate.sh: asked for $wanted but the game ran on another backend" >&2
		status=3
	fi
fi
exit $status
