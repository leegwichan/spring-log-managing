# Kibana Pre-Configuration

This directory contains Kibana pre-configuration files that can be imported via the `kibana-init.sh` script.

## Directory Structure

```
docker/
├── kibana/
│   ├── config/
│   │   └── kibana.yml              # Kibana main configuration
│   ├── saved-objects/
│   │   ├── index-patterns.ndjson   # Index patterns for dev, prod, and all logs
│   │   └── dashboards.ndjson       # Pre-built dashboards and visualizations
│   └── README.md                   # This file
└── kibana-init.sh                  # Initialization script (run from local machine)
```

## What Gets Configured

### 1. Index Patterns
- `spring-logs-dev-*` - Development environment logs
- `spring-logs-prod-*` - Production environment logs
- `spring-logs-*` - All logs (default)

All patterns use `@timestamp` as the time field.

### 2. Dashboard & Visualizations

**Dashboard: "Spring Boot Application Dashboard"**
- **[Logs by Level] Count** - Pie chart showing log distribution by level (INFO, WARN, ERROR, etc.)
- **[Request Performance] Average Duration** - Line chart tracking average request duration over time
- **[HTTP Status] Distribution** - Histogram showing HTTP status code distribution
- **[Top Endpoints] Request Count** - Table showing most-requested endpoints with average duration
- **Recent Application Logs** - Searchable log table with key fields (level, message, URI, traceId, etc.)

### 3. Kibana Settings
- Default locale: English
- Default app: Discover
- Telemetry: Disabled
- Security: Disabled (for local development)

## How It Works

1. **docker-compose.yml** mounts `kibana.yml` into the Kibana container
2. **kibana.yml** is loaded automatically when Kibana starts
3. After Kibana is ready, run **`./kibana-init.sh`** from the `docker/` directory
4. The script imports saved objects via Kibana REST API (http://localhost:5601)

## Quick Start

```bash
# 1. Start Kibana
cd docker
docker-compose up -d kibana

# 2. Wait for Kibana to be ready, then initialize
./kibana-init.sh

# 3. Open Kibana
open http://localhost:5601
```

## Manual Import/Export

### Re-run Initialization
```bash
# From docker/ directory
./kibana-init.sh

# Or specify custom Kibana host
KIBANA_HOST=http://localhost:5601 ./kibana-init.sh
```

### Export Current Configuration
```bash
# Export all saved objects
curl -X POST "http://localhost:5601/api/saved_objects/_export" \
  -H "kbn-xsrf: true" \
  -H "Content-Type: application/json" \
  -d '{"type": ["index-pattern", "dashboard", "visualization", "search"]}' \
  > my-export.ndjson
```

### Manual Import
```bash
# Import specific file
curl -X POST "http://localhost:5601/api/saved_objects/_import?overwrite=true" \
  -H "kbn-xsrf: true" \
  --form file=@kibana/saved-objects/dashboards.ndjson
```

## Accessing Kibana

After running `docker-compose up`:

1. Wait for Kibana initialization (check logs: `docker-compose logs kibana-init`)
2. Open browser: http://localhost:5601
3. Navigate to:
   - **Discover** - View raw logs with search and filters
   - **Dashboard** - Open "Spring Boot Application Dashboard"
   - **Management > Index Patterns** - View configured patterns

## Customization

### Adding New Visualizations

1. Create visualizations manually in Kibana UI
2. Export via **Stack Management > Saved Objects > Export**
3. Add exported `.ndjson` content to `saved-objects/dashboards.ndjson`
4. Restart: `docker-compose restart kibana kibana-init`

### Modifying Index Patterns

Edit `saved-objects/index-patterns.ndjson` and update:
- `title` - Index pattern string (e.g., `spring-logs-*`)
- `timeFieldName` - Timestamp field name (default: `@timestamp`)

### Kibana Configuration

Edit `config/kibana.yml` for:
- Language (`i18n.locale`)
- Default app (`kibana.defaultAppId`)
- Logging level (`logging.root.level`)

## Troubleshooting

### Saved Objects Not Imported
```bash
# Verify Kibana is running
docker-compose ps kibana

# Check Kibana logs
docker-compose logs kibana

# Re-run initialization script
./kibana-init.sh
```

### Index Patterns Not Showing Data
```bash
# Verify Elasticsearch indices exist
curl http://localhost:9200/_cat/indices/spring-logs-*

# Refresh index pattern fields in Kibana
# Management > Index Patterns > [Select Pattern] > Refresh field list
```

### Dashboard Empty or Broken
- Ensure logs are flowing from Spring Boot app
- Check time range filter (top-right corner in Kibana)
- Verify field names match (e.g., `duration`, `level`, `httpStatus`)

## Field Mappings

Expected fields in logs (from Spring Boot application):

| Field | Type | Description |
|-------|------|-------------|
| `@timestamp` | date | Log timestamp |
| `level` | keyword | Log level (INFO, WARN, ERROR) |
| `message` | text | Log message |
| `traceId` | keyword | Distributed trace ID |
| `spanId` | keyword | Current span ID |
| `parentSpanId` | keyword | Parent span ID |
| `serviceName` | keyword | Service name |
| `environment` | keyword | Environment (dev, prod) |
| `requestUri` | keyword | HTTP request URI |
| `method` | keyword | HTTP method |
| `httpStatus` | long | HTTP status code |
| `duration` | long | Request duration (ms) |
| `clientIp` | ip | Client IP address |

## References

- [Kibana Saved Objects API](https://www.elastic.co/guide/en/kibana/current/saved-objects-api.html)
- [Kibana Configuration](https://www.elastic.co/guide/en/kibana/current/settings.html)
- [NDJSON Format](http://ndjson.org/)
