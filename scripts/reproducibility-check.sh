#!/usr/bin/env bash
# Reproducibility gate: build the artifact twice from clean and require byte-identical output.
#
# Why this exists: Maven Central requires reproducible builds, doc 46 listed the config as
# "not configured" for a long time, and the only way to know the property actually holds is to
# measure it. `project.build.outputTimestamp` is set in the POM (line ~61); this gate proves the
# result is stable and keeps it that way on every push, instead of trusting the setting.
#
# Usage:  bash scripts/reproducibility-check.sh [jar-path]
# Exit:   0 reproducible, 1 not reproducible or the build failed.

set -u

JAR="${1:-target/junify-db-core-1.0.0.jar}"
# Prefer the Maven wrapper (what CI uses) so local and CI builds are the same build.
if [ -x ./mvnw ]; then MVN="./mvnw"; elif [ -f ./mvnw ]; then MVN="sh ./mvnw"; else MVN="mvn"; fi
WORK="$(mktemp -d 2>/dev/null || echo target/.repro-work)"
mkdir -p "$WORK"
trap 'rm -rf "$WORK"' EXIT

fail() { echo "REPRODUCIBILITY GATE: FAIL — $1"; exit 1; }

# `mvn clean` cannot delete a jar held open by a running server, and a leftover probe server
# from a previous manual test is the usual cause. Fail fast with the remedy rather than
# reporting a misleading build failure.
# Windows-only check (guarded so the script is a no-op check on Linux/CI, where the file-lock
# behaviour does not exist).
if command -v powershell >/dev/null 2>&1; then
  if powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -like '*junify-db-core*' } | Select-Object -ExpandProperty ProcessId" 2>/dev/null | grep -qE '[0-9]'; then
    echo "FAIL: a junify-db-core server is running and holds the jar open — stop it first:"
    powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -like '*junify-db-core*' } | Select-Object ProcessId, CommandLine | Format-Table -AutoSize | Out-String -Width 200" 2>/dev/null | head -8
    exit 1
  fi
fi

hash_of() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1
  else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

echo "== build 1/2 =="
$MVN -DskipTests -q clean package >"$WORK/build1.log" 2>&1 || { tail -20 "$WORK/build1.log"; fail "first build failed"; }
[ -f "$JAR" ] || fail "expected artifact not produced: $JAR"
cp "$JAR" "$WORK/a.jar"
H1=$(hash_of "$WORK/a.jar")
SIZE1=$(wc -c <"$WORK/a.jar" | tr -d ' ')
echo "   sha256 $H1  ($SIZE1 bytes)"

echo "== build 2/2 =="
$MVN -DskipTests -q clean package >"$WORK/build2.log" 2>&1 || { tail -20 "$WORK/build2.log"; fail "second build failed"; }
cp "$JAR" "$WORK/b.jar"
H2=$(hash_of "$WORK/b.jar")
echo "   sha256 $H2  ($(wc -c <"$WORK/b.jar" | tr -d ' ') bytes)"

# Keep one copy so the caller can inspect what passed the gate.
cp "$WORK/a.jar" target/reproducibility-artifact.jar 2>/dev/null || true

if [ "$H1" = "$H2" ]; then
  echo "REPRODUCIBILITY GATE: PASS ($JAR is byte-for-byte reproducible across clean builds)"
  exit 0
fi

# Diagnose by comparing the archive *listings* (name, size, date), not extracted content:
# a reproducibility break is usually a timestamp delta, and extraction drops timestamps, so a
# content-only diff reports "no differences" while the hashes disagree — a misleading clue.
echo "   differing entries (size/date/name, first 20):"
# Keep the WHOLE listing line: the date's time and year fields are what usually differ, and
# reducing the line to a few columns can hide the difference entirely (day name and month are
# the same for builds made minutes apart, which made an earlier version of this print nothing).
jar tvf "$WORK/a.jar" 2>/dev/null | sed 's/^ *//' | sort >"$WORK/a.list"
jar tvf "$WORK/b.jar" 2>/dev/null | sed 's/^ *//' | sort >"$WORK/b.list"
diff "$WORK/a.list" "$WORK/b.list" 2>/dev/null | head -20
fail "artifact differs between identical clean builds ($H1 != $H2)"
