#!/usr/bin/env bash

set -euo pipefail

./gradlew test

(
  cd frontend
  npm run lint
  npm run build
)

git diff --check

echo "Feature verification completed."
