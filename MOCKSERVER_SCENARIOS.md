# MockServer Recording & Replay Scenarios

This document describes common scenarios for recording and replaying **AviationStack** and **WeatherAPI** traffic with MockServer.

It builds on the base setup in `mockserverSetup.md`.

---

## 1. Record & replay AviationStack **only**

### 1.1 Recording phase

1. **Start MockServer**

   ```bash
   docker run --rm -p 1080:1080 mockserver/mockserver
   ```

2. **Reset MockServer state**

   ```bash
   curl -v -X PUT "http://localhost:1080/mockserver/reset"
   ```

3. **Configure AviationStack forwarding**

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

4. **Point backend AviationStack base URL at MockServer**

   In `application.yml` (or via env):

   ```yaml
   external:
     aviationstack:
       base-url: http://localhost:1080
   ```

5. **Exercise AviationStack flows**

   - Load flight selection
   - Trigger calls that use AviationStack (flight status, airline, airport).

6. **Export AviationStack expectations**

   ```bash
   curl -v -X PUT "http://localhost:1080/mockserver/retrieve?type=RECORDED_EXPECTATIONS" \
     -H "Content-Type: application/json" \
     -d '{
       "httpRequest": {
         "path": "/v1/.*"
       }
     }' > aviationstack-recorded-expectations.json
   ```

### 1.2 Replay phase

1. Start MockServer.
2. Load AviationStack expectations:

   ```bash
   curl -v -X PUT "http://localhost:1080/mockserver/expectation" \
     -H "Content-Type: application/json" \
     -d @aviationstack-recorded-expectations.json
   ```

3. Keep `external.aviationstack.base-url: http://localhost:1080`.
4. Run the app / tests — AviationStack calls will be served from MockServer.

---

## 2. Record & replay WeatherAPI **only**

### 2.1 Recording phase

1. **Start MockServer**

   ```bash
   docker run --rm -p 1080:1080 mockserver/mockserver
   ```

2. **Reset MockServer state**

   ```bash
   curl -v -X PUT "http://localhost:1080/mockserver/reset"
   ```

3. **Configure WeatherAPI forwarding**

   ```bash
   curl -v -X PUT "http://localhost:1080/mockserver/expectation" \
     -H "Content-Type: application/json" \
     -d '{
       "httpRequest": {
         "path": "/current\\.json"
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

4. **Point backend WeatherAPI base URL at MockServer**

   ```yaml
   external:
     weatherapi:
       base-url: http://localhost:1080
   ```

5. **Exercise WeatherAPI flows**

   - Open the Flight Selection page.
   - Open an Airport Details popup.
   - Click **“Check weather now”** to trigger WeatherAPI calls.

6. **Export WeatherAPI expectations**

   ```bash
   curl -v -X PUT "http://localhost:1080/mockserver/retrieve?type=RECORDED_EXPECTATIONS" \
     -H "Content-Type: application/json" \
     -d '{
       "httpRequest": {
         "path": "/current\\.json"
       }
     }' > weatherapi-recorded-expectations.json
   ```

### 2.2 Replay phase

1. Start MockServer.
2. Load WeatherAPI expectations:

   ```bash
   curl -v -X PUT "http://localhost:1080/mockserver/expectation" \
     -H "Content-Type: application/json" \
     -d @weatherapi-recorded-expectations.json
   ```

3. Keep `external.weatherapi.base-url: http://localhost:1080`.
4. Run the app / tests — Weather calls (e.g. from the “Check weather now” link) will be served from MockServer.

---

## 3. Record & replay **both** AviationStack and WeatherAPI together

### 3.1 Recording phase

1. Start MockServer and reset state:

   ```bash
   docker run --rm -p 1080:1080 mockserver/mockserver

   curl -v -X PUT "http://localhost:1080/mockserver/reset"
   ```

2. Configure **both** forwarding expectations:

   - AviationStack (`/v1/.*`) – same as section 1.1.3
   - WeatherAPI (`/current.json`) – same as section 2.1.3

3. Point **both** external base URLs at MockServer:

   ```yaml
   external:
     aviationstack:
       base-url: http://localhost:1080
     weatherapi:
       base-url: http://localhost:1080
   ```

4. Exercise flows that touch both APIs:

   - Use check-in / flight selection that triggers AviationStack calls.
   - Open Airport Details and click **“Check weather now”** to trigger WeatherAPI.

5. Export **combined** expectations (optional convenience file):

   ```bash
   curl -v -X PUT "http://localhost:1080/mockserver/retrieve?type=RECORDED_EXPECTATIONS" \
     -H "Content-Type: application/json" \
     -d '{}' > aviationstack-weatherapi-recorded-expectations.json
   ```

   You can still export per-API files in the same run if you want:

   - `aviationstack-recorded-expectations.json` (filter `/v1/.*`)
   - `weatherapi-recorded-expectations.json` (filter `/current\.json`)

### 3.2 Replay phase

You have two options:

#### Option A: Load per-API expectation files

```bash
curl -v -X PUT "http://localhost:1080/mockserver/expectation" \
  -H "Content-Type: application/json" \
  -d @aviationstack-recorded-expectations.json

curl -v -X PUT "http://localhost:1080/mockserver/expectation" \
  -H "Content-Type: application/json" \
  -d @weatherapi-recorded-expectations.json
```

#### Option B: Load a single combined file

```bash
curl -v -X PUT "http://localhost:1080/mockserver/expectation" \
  -H "Content-Type: application/json" \
  -d @aviationstack-weatherapi-recorded-expectations.json
```

In both cases, keep:

```yaml
external:
  aviationstack:
    base-url: http://localhost:1080
  weatherapi:
    base-url: http://localhost:1080
```

---

## 4. Refreshing recordings independently

Because AviationStack and WeatherAPI expectations are stored in **separate JSON files**, you can refresh one without touching the other:

- To refresh only WeatherAPI:
  - Reset MockServer.
  - Configure only the WeatherAPI forwarding expectation.
  - Point only `external.weatherapi.base-url` at `http://localhost:1080`.
  - Exercise only the weather-related flows.
  - Export to `weatherapi-recorded-expectations.json` and replace the old file.

All existing AviationStack recordings in `aviationstack-recorded-expectations.json` remain valid and can still be loaded alongside the refreshed WeatherAPI expectations.

