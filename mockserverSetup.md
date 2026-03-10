## MockServer setup for AviationStack & WeatherAPI

This guide shows how to:
- **Record** real AviationStack traffic via MockServer
- **Record** real WeatherAPI traffic via MockServer
- **Replay** those responses locally for dev/tests without calling the real APIs

### 1. Run MockServer as a proxy

Start MockServer (Docker is easiest):

```bash
docker run --rm -p 1080:1080 mockserver/mockserver
```

By default, it listens on `http://localhost:1080`.

### 2. Point the backend at MockServer (recording mode)

In `application.yml` (for recording mode), point the external API base URLs you want to record at MockServer.

**AviationStack (flights / airlines / airports)**:

```yaml
external:
  aviationstack:
    base-url: http://localhost:1080
```

Keep the real AviationStack host in an environment variable:

```bash
export AVIATIONSTACK_TARGET_HOST="api.aviationstack.com"
```

In `AviationStackConfig` (or a small proxy helper), make sure each request includes the real host in the URL you give to MockServer, for example:

```java
String url = "http://" + System.getenv("AVIATIONSTACK_TARGET_HOST") + "/v1/flights?...";
```

MockServer will see the full URL and forward it to the real AviationStack API.

**WeatherAPI (current weather)**:

To record WeatherAPI, similarly point the WeatherAPI base URL to MockServer while recording:

```yaml
external:
  weatherapi:
    base-url: http://localhost:1080
```

Behind the scenes, `WeatherServiceImpl` builds URLs like:

```text
{weatherapi.base-url}/current.json?key=...&q=...
```

So during recording, calls will go to `http://localhost:1080/current.json?...`, which MockServer will forward to the real `https://api.weatherapi.com/v1/current.json` when configured in the expectations below.

> Alternative: instead of building full URLs yourself, you can use MockServer’s “forward to host” configuration via its REST API.

### 3. Configure MockServer to record proxied traffic (via REST)

You can run MockServer with its default Docker command and configure the **forwarding / proxy behavior entirely via REST** (no extra `command:` block in `docker-compose.yml` needed).

First, reset state:

```bash
curl -v -X PUT "http://localhost:1080/mockserver/reset"
```

Then create **forwarding expectations** that proxy selected paths to the real APIs and **set the correct `Host` header** (so Cloudflare / providers accept the request). Use `httpOverrideForwardedRequest` with `requestOverride.socketAddress` and `requestModifier.headers.replace`.

**AviationStack forwarding (all `/v1/...` paths)**:

```bash
curl -v -X PUT "http://localhost:1080/mockserver/expectation" \
  -H "Content-Type: application/json" \
  -d '{
    "httpRequest": {
      "path": "/v1/.*"
    },
    "httpOverrideForwardedRequest": {
      "requestOverride": {
        "socketAddress": {
          "host": "api.aviationstack.com",
          "port": 80,
          "scheme": "HTTP"
        }
      },
      "requestModifier": {
        "headers": {
          "replace": [
            { "name": "Host", "values": ["api.aviationstack.com"] }
          ]
        }
      }
    }
  }'
```

- **requestOverride.socketAddress** sends the request to `api.aviationstack.com:80`.
- **requestModifier.headers.replace** sets `Host: api.aviationstack.com` on the outgoing request (instead of `Host: mockserver:1080`), which avoids Cloudflare blocking the request.

With this in place, any request your backend sends to `http://<mockserver-host>:1080/v1/...` will be **forwarded to `http://api.aviationstack.com/v1/...` with the correct Host header and recorded** by MockServer.

**WeatherAPI forwarding (only `/v1/current.json` paths)**:

```bash
curl -v -X PUT "http://localhost:1080/mockserver/expectation" \
  -H "Content-Type: application/json" \
  -d '{
    "httpRequest": {
      "path": "/v1/current\\.json"
    },
    "httpOverrideForwardedRequest": {
      "requestOverride": {
        "socketAddress": {
          "host": "api.weatherapi.com",
          "port": 443,
          "scheme": "HTTPS"
        }
      },
      "requestModifier": {
        "headers": {
          "replace": [
            { "name": "Host", "values": ["api.weatherapi.com"] }
          ]
        }
      }
    }
  }'
```

- This forwards `http://localhost:1080/current.json?...` to `https://api.weatherapi.com/v1/current.json?...` with `Host: api.weatherapi.com`.
- Because the backend builds a full URL using `weatherapi.base-url`, using `/current.json` as the path is sufficient to match and forward.

### 4. Drive real traffic through the proxy (record phase)

1. Start the Sky‑High backend with:
   - `AVIATIONSTACK_API_KEY` set
   - `external.aviationstack.base-url` pointing to `http://localhost:1080`
2. Use the app normally:
   - Open flight selection
   - Trigger flight status, airline, and airport calls for a few flight numbers you care about (e.g. `AF381`, `NH885`, `SQ282`)

MockServer will capture a log of all requests/responses for those AviationStack endpoints.

### 5. Export recorded expectations to JSON

Use MockServer’s “retrieve recorded expectations” API.

The simplest option (no filtering, get everything recorded for **all** external APIs) is:

```bash
curl -v -X PUT "http://localhost:1080/mockserver/retrieve?type=RECORDED_EXPECTATIONS" \
  -H "Content-Type: application/json" \
  -d '{}' > all-external-recorded-expectations.json
```

If you want to **export per API**, filter by path using a valid `httpRequest` matcher (note: **do not** use a `host` field; it is not part of the matcher schema and will cause errors):

**AviationStack-only expectations**:

```bash
curl -v -X PUT "http://localhost:1080/mockserver/retrieve?type=RECORDED_EXPECTATIONS" \
  -H "Content-Type: application/json" \
  -d '{
    "httpRequest": {
      "path": "/v1/.*"
    }
  }' > aviationstack-recorded-expectations.json
```

**WeatherAPI-only expectations**:

```bash
curl -v -X PUT "http://localhost:1080/mockserver/retrieve?type=RECORDED_EXPECTATIONS" \
  -H "Content-Type: application/json" \
  -d '{
    "httpRequest": {
      "path": "/v1/current\\.json"
    }
  }' > weatherapi-recorded-expectations.json
```

Either way, the resulting files contain request/response pairs for the AviationStack and/or WeatherAPI calls you made.

Commit them to the repo (for example alongside `aviationstack-recorded-expectations.json` and `weatherapi-recorded-expectations.json` in the Sky‑High project root).

### 6. Run MockServer in replay mode (offline)

In replay mode:

1. Start MockServer (same Docker command as before).
2. Load the recorded expectations you need:

**AviationStack only**:

```bash
curl -v -X PUT "http://localhost:1080/mockserver/expectation" \
  -H "Content-Type: application/json" \
  -d @aviationstack-recorded-expectations.json
```

**WeatherAPI only**:

```bash
curl -v -X PUT "http://localhost:1080/mockserver/expectation" \
  -H "Content-Type: application/json" \
  -d @weatherapi-recorded-expectations.json
```

**Both AviationStack and WeatherAPI together** (either load both files separately, or use a combined file):

```bash
curl -v -X PUT "http://localhost:1080/mockserver/expectation" \
  -H "Content-Type: application/json" \
  -d @aviationstack-recorded-expectations.json

curl -v -X PUT "http://localhost:1080/mockserver/expectation" \
  -H "Content-Type: application/json" \
  -d @weatherapi-recorded-expectations.json
```

Do **not** configure forwarding this time; MockServer will answer directly from the expectations.

Keep your backend `external.aviationstack.base-url` pointed at `http://localhost:1080`.

### 7. Use in local dev and automated tests

**Local development without hitting real AviationStack**

- Start MockServer
- Load the expectations JSON
- Run the backend with `base-url=http://localhost:1080`
- Use the app as normal; all AviationStack calls are served from MockServer

**Automated tests**

As part of test setup:

1. Start MockServer (Docker container).
2. Load `aviationstack-recorded-expectations.json`.
3. Run backend tests against it (no real API calls, deterministic responses).

### 8. Refresh recordings when needed

When AviationStack responses or your usage patterns change:

1. Switch the backend back to proxy mode (`base-url=http://localhost:1080` + forwarding).
2. Clear old expectations.
3. Exercise the new scenarios through the app or tests.
4. Export updated expectations to JSON again and commit the updated file.
