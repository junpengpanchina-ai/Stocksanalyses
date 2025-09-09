#!/usr/bin/env bash
set -euo pipefail

BASE_URL=${BASE_URL:-http://localhost:8080}
VUS=${VUS:-10}
DURATION=${DURATION:-2m}

export BASE_URL VUS DURATION

echo "Running k6 with BASE_URL=$BASE_URL VUS=$VUS DURATION=$DURATION"
k6 run benchmarks/k6-smoke.js


