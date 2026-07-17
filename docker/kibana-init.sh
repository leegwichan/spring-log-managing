#!/bin/bash
set -e

KIBANA_HOST="${KIBANA_HOST:-http://localhost:5601}"
MAX_RETRIES=60
RETRY_INTERVAL=5

echo "=========================================="
echo "Kibana Initialization Script"
echo "=========================================="
echo "Kibana Host: ${KIBANA_HOST}"
echo ""

# Get the script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SAVED_OBJECTS_DIR="${SCRIPT_DIR}/kibana/saved-objects"

# Check if saved objects directory exists
if [ ! -d "$SAVED_OBJECTS_DIR" ]; then
  echo "Error: Saved objects directory not found: $SAVED_OBJECTS_DIR"
  exit 1
fi

echo "Waiting for Kibana to be ready..."

# Wait for Kibana to be healthy
for i in $(seq 1 $MAX_RETRIES); do
  if curl -f -s "${KIBANA_HOST}/api/status" > /dev/null 2>&1; then
    echo "✓ Kibana is ready!"
    break
  fi

  if [ $i -eq $MAX_RETRIES ]; then
    echo "✗ Timeout waiting for Kibana to be ready"
    echo "  Please ensure Kibana is running: docker-compose ps kibana"
    exit 1
  fi

  echo "  Attempt $i/$MAX_RETRIES: Waiting for Kibana... (retry in ${RETRY_INTERVAL}s)"
  sleep $RETRY_INTERVAL
done

echo ""
echo "=========================================="
echo "Importing Kibana Saved Objects"
echo "=========================================="

# Import index patterns
INDEX_PATTERNS_FILE="${SAVED_OBJECTS_DIR}/index-patterns.ndjson"
if [ -f "$INDEX_PATTERNS_FILE" ]; then
  echo "→ Importing index patterns..."

  RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${KIBANA_HOST}/api/saved_objects/_import?overwrite=true" \
    -H "kbn-xsrf: true" \
    --form file=@"${INDEX_PATTERNS_FILE}")

  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  BODY=$(echo "$RESPONSE" | sed '$d')

  if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✓ Index patterns imported successfully!"
    # Parse success count from response
    SUCCESS_COUNT=$(echo "$BODY" | grep -o '"success":true' | wc -l)
    echo "  Imported $SUCCESS_COUNT index pattern(s)"
  else
    echo "✗ Failed to import index patterns (HTTP $HTTP_CODE)"
    echo "  Response: $BODY"
  fi
else
  echo "⚠ Warning: Index patterns file not found: $INDEX_PATTERNS_FILE"
fi

echo ""

# Import dashboards and visualizations
DASHBOARDS_FILE="${SAVED_OBJECTS_DIR}/dashboards.ndjson"
if [ -f "$DASHBOARDS_FILE" ]; then
  echo "→ Importing dashboards and visualizations..."

  RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${KIBANA_HOST}/api/saved_objects/_import?overwrite=true" \
    -H "kbn-xsrf: true" \
    --form file=@"${DASHBOARDS_FILE}")

  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  BODY=$(echo "$RESPONSE" | sed '$d')

  if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✓ Dashboards and visualizations imported successfully!"
    SUCCESS_COUNT=$(echo "$BODY" | grep -o '"success":true' | wc -l)
    echo "  Imported $SUCCESS_COUNT object(s)"
  else
    echo "✗ Failed to import dashboards (HTTP $HTTP_CODE)"
    echo "  Response: $BODY"
  fi
else
  echo "⚠ Warning: Dashboards file not found: $DASHBOARDS_FILE"
fi

echo ""

# Set default index pattern
echo "→ Setting default index pattern..."
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${KIBANA_HOST}/api/kibana/settings/defaultIndex" \
  -H "kbn-xsrf: true" \
  -H "Content-Type: application/json" \
  -d '{"value":"spring-logs-all"}')

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
  echo "✓ Default index pattern set to: spring-logs-all"
else
  echo "⚠ Could not set default index pattern (might already be set or API changed)"
fi

echo ""
echo "=========================================="
echo "Initialization Complete!"
echo "=========================================="
echo ""
echo "Available Resources:"
echo "  • Kibana UI:    ${KIBANA_HOST}"
echo "  • Discover:     ${KIBANA_HOST}/app/discover"
echo "  • Dashboards:   ${KIBANA_HOST}/app/dashboards"
echo ""
echo "Index Patterns:"
echo "  • spring-logs-dev-*  (Development logs)"
echo "  • spring-logs-prod-* (Production logs)"
echo "  • spring-logs-*      (All logs - default)"
echo ""
echo "Dashboard:"
echo "  • Spring Boot Application Dashboard"
echo ""
echo "Next Steps:"
echo "  1. Open ${KIBANA_HOST}/app/dashboards"
echo "  2. Select 'Spring Boot Application Dashboard'"
echo "  3. Generate logs: curl http://localhost:8080/"
echo ""
