#!/usr/bin/env bash

set -euo pipefail

if [[ "$#" -ne 1 ]]; then
  echo "Usage: $0 <git-diff-range>" >&2
  exit 2
fi

diff_range="$1"

echo "Checking whitespace in ${diff_range}"
git diff --check "$diff_range"

media_files="$({
  git ls-files |
    grep -Ei '\.(mp3|flac|m4a|aac|ogg|oga|wav|aiff|aif|alac|wma|opus|mp4|mkv|webm)$' |
    grep -Ev '^fixtures/synthetic-library/|^spikes/android-platform-proof/app/src/main/assets/fixtures/'
} || true)"

sensitive_files="$({
  git ls-files |
    grep -Ei '\.(db|db-[^/]*|sqlite|sqlite3|p12|pfx|pem|key)$|(^|/)\.env($|\.)|(^|/)(credentials?|oauth|tokens?)[^/]*\.json$'
} || true)"

prohibited_files="$(printf '%s\n%s\n' "$media_files" "$sensitive_files" | sed '/^$/d')"

if [[ -n "$prohibited_files" ]]; then
  echo "Prohibited files are tracked:"
  echo "$prohibited_files"
  exit 1
fi

android_proof_media="$({
  git ls-files 'spikes/android-platform-proof/app/src/main/assets/fixtures/*' |
    grep -Ei '\.(mp3|flac|m4a|ogg|wav|aiff)$'
} || true)"

if [[ -n "$android_proof_media" ]]; then
  python3 spikes/android-platform-proof/scripts/verify_fixtures.py
fi

# Split sensitive examples so this script scans itself without matching its
# own source text. The values concatenate into ordinary extended regexes.
known_email='schuyler[.]sync@gmail''[.]com'
windows_home='[A-Za-z]:\\Users''\\[^\\/:*?"<>|[:space:]]+'
unix_home='/home''/[^/[:space:]]+/'
macos_home='/Users''/[^/[:space:]]+/'
android_storage='/storage''/emulated/0/'
private_key='BEGIN (RSA |EC |OPENSSH )?PRIVATE ''KEY'
access_key='AKIA[0-9A-Z]{16}'
github_token='gh[pousr]_[A-Za-z0-9_]{20,}'
api_key='sk-[A-Za-z0-9_-]{20,}'

personal_or_secret_regex="${known_email}|${windows_home}|${unix_home}|${macos_home}|${android_storage}|${private_key}|${access_key}|${github_token}|${api_key}"
matches="$(git grep -InE "$personal_or_secret_regex" -- . || true)"

if [[ -n "$matches" ]]; then
  echo "Personal configuration or credential material found:"
  echo "$matches"
  exit 1
fi

required=(
  README.md
  LICENSE
  AGENTS.md
  docs/product/Media_Ecosystem_v1.0.0_DoD.md
  docs/product/ROADMAP_v1.md
  docs/product/V1_ACCEPTANCE_MATRIX.md
  docs/architecture/FOUNDATION_DECISIONS.md
  docs/architecture/adr/0000-template.md
  docs/implementation/REPO_BOOTSTRAP_PLAN.md
  docs/implementation/SUPPORTED_TEST_DEVICE_MATRIX.md
  docs/implementation/CAPABILITY_SPIKE_PROTOCOL.md
  docs/implementation/PHASE_1_CAPABILITY_ISSUES.md
  docs/privacy/PRIVACY_AND_FIXTURE_POLICY.md
)

for path in "${required[@]}"; do
  if [[ ! -s "$path" ]]; then
    echo "Required document missing or empty: $path"
    exit 1
  fi
done

apache_license_sha256="c71d239df91726fc519c6eb72d318ec65820627232b2f796219e87dcf35d0ab4"
actual_license_sha256="$(sha256sum LICENSE | cut -d ' ' -f 1)"

if [[ "$actual_license_sha256" != "$apache_license_sha256" ]]; then
  echo "LICENSE is not the complete canonical Apache License 2.0 text."
  exit 1
fi

echo "Foundation guardrails passed."
