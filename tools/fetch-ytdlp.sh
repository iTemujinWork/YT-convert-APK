#!/usr/bin/env bash
# Downloads the newest yt-dlp release, verifies its SHA-256 against the published
# checksum file, and puts it where the app bundles it (app/src/main/res/raw/ytdlp).
#
#   tools/fetch-ytdlp.sh app/src/main/res/raw/ytdlp
#
# GH_TOKEN is optional (it only raises the API rate limit).
set -euo pipefail

target="${1:-app/src/main/res/raw/ytdlp}"
auth=()
if [[ -n "${GH_TOKEN:-}" ]]; then auth=(-H "Authorization: Bearer $GH_TOKEN"); fi

tag=$(curl -fsSL "${auth[@]}" -H "Accept: application/vnd.github+json" \
  https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest | python3 -c 'import json,sys; print(json.load(sys.stdin)["tag_name"])')
echo "yt-dlp $tag"

tmp=$(mktemp -d)
curl -fsSL -o "$tmp/yt-dlp" "https://github.com/yt-dlp/yt-dlp/releases/download/$tag/yt-dlp"
curl -fsSL -o "$tmp/SHA2-256SUMS" "https://github.com/yt-dlp/yt-dlp/releases/download/$tag/SHA2-256SUMS"

expected=$(awk '$2 == "yt-dlp" || $2 == "*yt-dlp" {print $1}' "$tmp/SHA2-256SUMS")
if [[ -z "$expected" ]]; then echo "no checksum for yt-dlp in SHA2-256SUMS" >&2; exit 1; fi
echo "$expected  $tmp/yt-dlp" | sha256sum -c -

inside=$(python3 - "$tmp/yt-dlp" <<'EOF'
import re, sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    text = z.read("yt_dlp/version.py").decode()
print(re.search(r"__version__\s*=\s*'([^']+)'", text).group(1))
EOF
)
if [[ "$inside" != "$tag" ]]; then echo "the file says $inside but the release is $tag" >&2; exit 1; fi

mkdir -p "$(dirname "$target")"
cp "$tmp/yt-dlp" "$target"
rm -rf "$tmp"
echo "bundled yt-dlp $tag as $target ($(stat -c %s "$target") bytes)"
