#!/usr/bin/env bash
# Signs a host release zip so installed hosts accept it as an update. Writes <zip>.sig next to the zip.
#
#   UPDATE_SIGNING_KEY="$(cat update-signing-key.pem)" scripts/sign-host-update.sh <zip> <version>
#
# The key is an ECDSA P-256 private key in PEM form (the repository secret UPDATE_SIGNING_KEY). Before it
# writes anything the script checks the signature against the public key that is built into the host
# (src/host/HomeCursor.Core/Updates/ReleaseSigningKey.cs), so a wrong or outdated secret fails here and not
# on the users' PCs. What is signed must match UpdateSignature.GetPayload in the host.
#
# Make a new key pair (then put its public half into ReleaseSigningKey.cs and the private half into the secret):
#   openssl ecparam -genkey -name prime256v1 -noout -out update-signing-key.pem
#   openssl ec -in update-signing-key.pem -pubout -outform DER | base64 -w0
set -euo pipefail

[ $# -eq 2 ] && [ -f "$1" ] || { echo "usage: $0 <zip> <version>" >&2; exit 1; }
zip="$1" version="$2"
: "${UPDATE_SIGNING_KEY:?set UPDATE_SIGNING_KEY to the PEM private key}"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "bad version: $version (x.y.z only)" >&2; exit 1; }

root="$(cd "$(dirname "$0")/.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
umask 077

public_key="$(sed -nE 's/.*PublicKey = "([^"]+)".*/\1/p' "$root/src/host/HomeCursor.Core/Updates/ReleaseSigningKey.cs")"
[ -n "$public_key" ] || { echo "could not read the public key from ReleaseSigningKey.cs" >&2; exit 1; }

printf '%s\n' "$UPDATE_SIGNING_KEY" > "$work/key.pem"
hash="$(sha256sum "$zip" | cut -d' ' -f1)"
printf 'HomeCursor.Host|%s|%s' "$version" "$hash" > "$work/payload"

openssl dgst -sha256 -sign "$work/key.pem" -out "$work/signature" "$work/payload"

printf '%s' "$public_key" | base64 -d | openssl pkey -pubin -inform DER -out "$work/public.pem"
openssl dgst -sha256 -verify "$work/public.pem" -signature "$work/signature" "$work/payload" >/dev/null \
    || { echo "the signing key does not match the public key built into the host" >&2; exit 1; }

base64 -w0 "$work/signature" > "$zip.sig"
echo "signed $zip ($version, sha256 $hash) -> $zip.sig"
