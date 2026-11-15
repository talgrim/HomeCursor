#!/usr/bin/env bash
# Builds the Windows host as a self-contained single-file exe and zips it into artifacts/.
# Works on Linux/WSL as well as Windows (Git Bash); needs the .NET 9 SDK and python3.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
project="$root/src/host/HomeCursor.Host.Tray/HomeCursor.Host.Tray.csproj"
out="$root/artifacts/host-win-x64"
zip_path="$root/artifacts/HomeCursor-Host-win-x64.zip"

rm -rf "$out" "$zip_path"
mkdir -p "$root/artifacts"

dotnet publish "$project" \
  -c Release \
  -r win-x64 \
  --self-contained true \
  -p:PublishSingleFile=true \
  -p:DebugType=none \
  -p:EnableCompressionInSingleFile=true \
  -o "$out"

cp "$root/LICENSE" "$out/LICENSE"

(cd "$out" && python3 -m zipfile -c "$zip_path" *)
echo "Created $zip_path"
