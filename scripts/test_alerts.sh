#!/bin/bash
# Test script for real-time alerts with webhook verification

set -e

BASE_URL="http://localhost:8080"
WEBHOOK_URL="http://localhost:8000"

echo "=== K-line Alerts Test ==="
echo "1. Starting webhook receiver (run in another terminal):"
echo "   python3 scripts/verify_webhook.py"
echo ""
echo "2. Creating subscription with HMAC signature..."
SUBSCRIPTION_RESPONSE=$(curl -s -X POST "$BASE_URL/api/alerts/subscriptions" \
  -H 'Content-Type: application/json' \
  -d '{
    "symbol":"TEST",
    "strategyId":"dsl",
    "params":{},
    "channels":["webhook"],
    "webhookUrl":"'$WEBHOOK_URL'",
    "webhookSecret":"my-secret",
    "cooldownSec":15,
    "maxRetries":3,
    "backoffBaseSec":1
  }')

echo "Subscription created: $SUBSCRIPTION_RESPONSE"
SUBSCRIPTION_ID=$(echo $SUBSCRIPTION_RESPONSE | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
echo "Subscription ID: $SUBSCRIPTION_ID"
echo ""

echo "3. Listing subscriptions..."
curl -s "$BASE_URL/api/alerts/subscriptions" | jq '.[] | {id, symbol, webhookUrl, maxRetries}'
echo ""

echo "4. Monitoring events (will show recent alerts)..."
echo "   Watch for events with status: queued -> retrying -> delivered/failed"
echo "   Mock data generates 1 bar/sec, strategy triggers on patterns"
echo ""

# Monitor events for 30 seconds
echo "Events in last 30 seconds:"
for i in {1..6}; do
  echo "--- Check $i ---"
  curl -s "$BASE_URL/api/alerts/events" | jq '.[] | {id, symbol, status, signal: .signal.type}'
  sleep 5
done

echo ""
echo "5. Prometheus metrics:"
curl -s "$BASE_URL/actuator/prometheus" | grep alerts_triggered_total || echo "No metrics yet"

echo ""
echo "6. Cleanup (optional):"
echo "   curl -X DELETE $BASE_URL/api/alerts/subscriptions/$SUBSCRIPTION_ID"
