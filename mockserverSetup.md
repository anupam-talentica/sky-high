## MockServer setup for AviationStack

This guide shows how to:
- **Record** real AviationStack traffic via MockServer
- **Replay** those responses locally for dev/tests without calling the real API

### 1. Run MockServer as a proxy

Start MockServer (Docker is easiest):

```bash
docker run --rm -p 1080:1080 mockserver/mockserver
```

By default, it listens on `http://localhost:1080`.

### 2. Point the backend at MockServer (recording mode)

In `application.yml` (for recording mode), point the AviationStack base URL to MockServer:

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

> Alternative: instead of building full URLs yourself, you can use MockServer’s “forward to host” configuration via its REST API.

### 3. Configure MockServer to record proxied traffic (via REST)

You can run MockServer with its default Docker command and configure the **forwarding / proxy behavior entirely via REST** (no extra `command:` block in `docker-compose.yml` needed).

First, reset state:

```bash
curl -v -X PUT "http://localhost:1080/mockserver/reset"
```

Then create a **forwarding expectation** that proxies all AviationStack paths to the real API and **sets the correct `Host` header** (so Cloudflare/API accept the request). Use `httpOverrideForwardedRequest` with `requestOverride.socketAddress` and `requestModifier.headers.replace`:

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

The simplest option (no filtering, get everything recorded) is:

```bash
curl -v -X PUT "http://localhost:1080/mockserver/retrieve?type=RECORDED_EXPECTATIONS" \
  -H "Content-Type: application/json" \
  -d '{}' > aviationstack-recorded-expectations.json
```

If you want to filter by path, you can pass a valid `httpRequest` matcher (note: **do not** use a `host` field; it is not part of the matcher schema and will cause the error you saw):

```bash
curl -v -X PUT "http://localhost:1080/mockserver/retrieve?type=RECORDED_EXPECTATIONS" \
  -H "Content-Type: application/json" \
  -d '{
    "httpRequest": {
      "path": "/v1/flights"
    }
  }' > aviationstack-recorded-expectations.json
```

Either way, the resulting file contains request/response pairs for the AviationStack calls you made.

Commit it to the repo (for example alongside `aviationstack-recorded-expectations.json` in the Sky‑High project root).

### 6. Run MockServer in replay mode (offline)

In replay mode:

1. Start MockServer (same Docker command as before).
2. Load the recorded expectations:

```bash
curl -v -X PUT "http://localhost:1080/mockserver/expectation" \
  -H "Content-Type: application/json" \
  -d @aviationstack-recorded-expectations.json
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
