#!/usr/bin/env bash
# Runs the version gate headless: tools/gate.sh <26_1|26_2|26_3> [OPENGL|VULKAN]
# Needs JDK 25, Xvfb and Mesa. Audio is written to build/gate/<run>.wav through OpenAL Soft's wave backend,
# so the run works on a machine without a sound card and the result can be measured.
set -euo pipefail
cd "$(dirname "$0")/.."
version="${1:?usage: tools/gate.sh <26_1|26_2|26_3> [OPENGL|VULKAN]}"
backend="${2:-}"
run="v${version}${backend:+-$backend}"
out="$PWD/build/gate"
mkdir -p "$out"
rm -rf "compat/v${version}/build/run/clientGameTest/gate"
printf '[general]\ndrivers=wave\nfrequency=44100\n[wave]\nfile=%s/%s.wav\n' "$out" "$run" > "$out/$run.alsoft.conf"
export ALSOFT_DRIVERS=wave ALSOFT_CONF="$out/$run.alsoft.conf"
# 26.3 creates its window through SDL, which finds no GLX visual under Xvfb; EGL works. Older versions ignore this.
export SDL_VIDEO_FORCE_EGL=1
args=(":v${version}:runClientGameTest" --console=plain)
[ -n "$backend" ] && args+=("-Pbackend=$backend")
status=0
xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew "${args[@]}" > "$out/$run.log" 2>&1 || status=$?
mkdir -p "$out/$run"
rm -f "$out/$run"/*.png
cp compat/v"${version}"/build/run/clientGameTest/gate/*.png "$out/$run/" 2>/dev/null || true
grep -E "WisteriaGate|Using graphics backend|Gate failed|BUILD" "$out/$run.log" | sed -E 's/^\[[0-9:]+\] \[[^]]+\] //' || true
# The game silently falls back to the other backend if the requested one cannot start: that is not a pass.
if [ $status -eq 0 ] && [ -n "$backend" ]; then
	wanted=$([ "$backend" = VULKAN ] && echo Vulkan || echo OpenGL)
	if ! grep -q "gate passed: .* on $wanted" "$out/$run.log"; then
		echo "gate.sh: asked for $wanted but the game ran on another backend" >&2
		status=3
	fi
fi
exit $status
