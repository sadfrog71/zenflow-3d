#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# The dedicated file is intentionally not named .env; load it explicitly for Metro.
[ -f .env.invelle ] || { echo 'Missing .env.invelle product configuration'; exit 1; }
set -a
source .env.invelle
set +a
[ -n "${EXPO_PUBLIC_ZENFLOW_API_URL:-}" ] && [ -n "${EXPO_PUBLIC_ZENFLOW_OAUTH_CLIENT_ID:-}" ] || { echo 'Missing Invelle API or OAuth configuration'; exit 1; }
build=$(node -p "require('./app.json').expo.ios.buildNumber")
root=$(cd .. && pwd)
archive_path="${INVELLE_ARCHIVE_PATH:-$root/.local-runtime/invelle-20261006/Invelle-build$build.xcarchive}"
derived_path="${INVELLE_DERIVED_DATA_PATH:-$root/.local-runtime/invelle-20261006/device-build}"
xcodebuild -workspace ios/Invelle.xcworkspace -scheme Invelle -configuration Release -destination 'generic/platform=iOS' -archivePath "$archive_path" -derivedDataPath "$derived_path" -allowProvisioningUpdates -allowProvisioningDeviceRegistration DEVELOPMENT_TEAM="${ZENFLOW_APPLE_TEAM_ID:?Missing Apple Team}" archive

# Validate the shipped bundle, not just the shell environment used to build it.
node - "$archive_path/Products/Applications/Invelle.app/main.jsbundle" <<'JS'
const { readFileSync } = require('node:fs');
const bundle = readFileSync(process.argv[2]);
for (const name of ['EXPO_PUBLIC_ZENFLOW_API_URL', 'EXPO_PUBLIC_ZENFLOW_OAUTH_CLIENT_ID']) {
  if (!bundle.includes(Buffer.from(process.env[name].trim()))) {
    throw new Error(`Release bundle is missing ${name}; do not install this archive`);
  }
}
console.log('Invelle release bundle API and OAuth configuration verified');
JS
