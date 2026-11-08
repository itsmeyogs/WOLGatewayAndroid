# WoL Gateway API — v1

Base path: `/api/v1`.

Default server bind: `127.0.0.1:8080`. Bind address is fixed to loopback; only the port can be changed, using `1024..65535`. The API server is stopped by default and is started from Settings. All device routes require:

```http
Authorization: Bearer <api-token>
```

The token is automatically generated on first Settings initialization and stored encrypted with an Android Keystore-backed AES key. It can be replaced by a custom token or refreshed automatically. It must not appear in logs, diagnostics, notifications, or error responses.

## Endpoints

| Method | Path | Auth | Response |
|---|---|---:|---|
| GET | `/health` | No | `200 {"status":"ok"}` |
| GET | `/diagnostics` | Yes | `200` safe gateway diagnostics; `401` unauthorized |
| POST | `/discovery/scan` | Yes | `200` completed LAN discovery result |
| GET | `/devices` | Yes | `200` device array |
| GET | `/devices/{id}` | Yes | `200` device |
| POST | `/devices` | Yes | `201` device |
| PUT | `/devices/{id}` | Yes | `200` device |
| DELETE | `/devices/{id}` | Yes | `204` |
| GET | `/devices/{id}/status` | Yes | `200` status result |
| POST | `/devices/{id}/wake` | Yes | `200` WoL result |

Full paths include the base path. For example, the health URL is `/api/v1/health`.

`GET /api/v1/devices` runs a best-effort status check for all saved devices, then returns one snapshot with the latest persisted status. A client does not need to call `/status` for each device. If one target check fails, its last persisted status is returned and the overall response still succeeds.

`GET /api/v1/diagnostics` returns a redacted, read-only gateway snapshot matching the Diagnostics screen. It never returns tokens, credentials, raw shell output, commands, or sensitive paths.

The response contains `general` and `serviceMode`. `serviceMode.data` contains the complete diagnostics list for `effectiveMode`:

- `effectiveMode: "STANDARD"` — eight Standard Mode items.
- `effectiveMode: "ROOT"` — six Root Mode items.
- Root fallback — `effectiveMode: "STANDARD"`, `fallback: true`, and eight Standard Mode items.

Standard Mode response example:

```json
{
  "general": {
    "appVersion": "1.0", "androidVersion": "14", "abi": "arm64-v8a",
    "deviceClassification": "OTHER", "networkInterface": "wlan0",
    "api": {"label": "API", "value": "Running", "status": "PASS"},
    "tunnel": {"label": "Tunnel", "value": "Not configured", "status": "NOT_CONFIGURED"},
    "gatewayService": {"label": "GatewayService", "value": "Running", "status": "PASS"},
    "safeErrors": [],
    "device": {"manufacturer": "Google", "model": "Pixel 7", "product": "panther"},
    "battery": {"levelPercent": 82, "charging": true, "batterySaver": false}
  },
  "serviceMode": {
    "effectiveMode": "STANDARD", "fallback": false,
    "data": [
      {"label": "Service running", "value": "Running", "status": "PASS"},
      {"label": "Service mode", "value": "API only", "status": "PASS"},
      {"label": "Foreground service", "value": "Running", "status": "PASS"},
      {"label": "Notifications", "value": "Enabled", "status": "PASS"},
      {"label": "API autostart", "value": "Enabled", "status": "PASS"},
      {"label": "Tunnel autostart", "value": "Disabled", "status": "NOT_CONFIGURED"},
      {"label": "Battery optimization", "value": "Exempt", "status": "PASS"},
      {"label": "Boot recovery", "value": "Best effort by OS/OEM", "status": "WARN"}
    ]
  }
}
```

Root Mode response example:

```json
{
  "general": {
    "appVersion": "1.0", "androidVersion": "14", "abi": "arm64-v8a",
    "deviceClassification": "OTHER", "networkInterface": "wlan0",
    "api": {"label": "API", "value": "Running", "status": "PASS"},
    "tunnel": {"label": "Tunnel", "value": "Connected", "status": "PASS"},
    "gatewayService": {"label": "GatewayService", "value": "Running", "status": "PASS"},
    "safeErrors": [],
    "device": {"manufacturer": "Google", "model": "Pixel 7", "product": "panther"},
    "battery": {"levelPercent": 82, "charging": true, "batterySaver": false}
  },
  "serviceMode": {
    "effectiveMode": "ROOT", "fallback": false,
    "data": [
      {"label": "Root capability", "value": "Available", "status": "PASS"},
      {"label": "Root consent", "value": "Enabled", "status": "PASS"},
      {"label": "Privilege check", "value": "Passed", "status": "PASS"},
      {"label": "Root supervisor", "value": "Running", "status": "PASS"},
      {"label": "Root features", "value": "Available", "status": "PASS"},
      {"label": "Fallback", "value": "Disabled", "status": "PASS"}
    ]
  }
}
```

Fallback response uses the complete Standard Mode data:

```json
{
  "general": {"safeErrors": ["Root mode unavailable; using Standard Mode"]},
  "serviceMode": {
    "effectiveMode": "STANDARD", "fallback": true,
    "data": [
      {"label": "Service running", "value": "Running", "status": "PASS"},
      {"label": "Service mode", "value": "API only", "status": "PASS"},
      {"label": "Foreground service", "value": "Running", "status": "PASS"},
      {"label": "Notifications", "value": "Enabled", "status": "PASS"},
      {"label": "API autostart", "value": "Enabled", "status": "PASS"},
      {"label": "Tunnel autostart", "value": "Disabled", "status": "NOT_CONFIGURED"},
      {"label": "Battery optimization", "value": "Exempt", "status": "PASS"},
      {"label": "Boot recovery", "value": "Best effort by OS/OEM", "status": "WARN"}
    ]
  }
}
```

The diagnostics response also includes safe device and battery facts:

```json
{
  "device": {
    "manufacturer": "Google",
    "model": "Pixel 7",
    "product": "panther"
  },
  "battery": {
    "levelPercent": 82,
    "charging": true,
    "batterySaver": false
  }
}
```

Battery fields are nullable when Android cannot provide a value. Serial numbers, Android IDs, IMEI, credentials, and other sensitive identifiers are excluded.

`POST /api/v1/discovery/scan` waits for the Android LAN scan to finish, up to the discovery timeout, then returns the final result. An empty `devices` array is valid. The scan does not persist devices; use `POST /api/v1/devices` after selecting a result. Discovery follows the effective runtime mode: Standard Mode uses the regular scan, while active Root Mode may resolve neighbor MAC addresses. Root fallback does not execute root commands from the API route.

### Expected status codes

| Endpoint | Success | Expected failures |
|---|---:|---|
| `GET /health` | `200` | — |
| `GET /diagnostics` | `200` | `401` missing/invalid token |
| `GET /devices` | `200` | `401` missing/invalid token |
| `GET /devices/{id}` | `200` | `401`, `404` unknown ID |
| `POST /devices` | `201` | `400` invalid JSON/fields, `401`, `409` duplicate MAC |
| `PUT /devices/{id}` | `200` | `400`, `401`, `404`, `409` duplicate MAC |
| `DELETE /devices/{id}` | `204` | `401`, `404` unknown ID |
| `GET /devices/{id}/status` | `200` | `401`, `404` unknown ID |
| `POST /devices/{id}/wake` | `200` | `401`, `404` unknown ID |

Create/update body:

```json
{
  "name": "Office PC",
  "ipAddress": "192.168.1.20",
  "macAddress": "AA:BB:CC:DD:EE:FF",
  "broadcastAddress": "255.255.255.255",
  "wolPort": 9,
  "statusCheckMethod": "ICMP_AND_TCP",
  "statusCheckPort": 3389
}
```

## Errors

```json
{
  "error": {
    "code": "validation_failed",
    "message": "Device validation failed",
    "fields": {
      "IP_ADDRESS": "IP_ADDRESS_INVALID"
    }
  }
}
```

Known codes: `unauthorized`, `invalid_json`, `validation_failed`, `duplicate_device`, `device_not_found`, `request_too_large`, `bad_request`, `internal_error`.

Request bodies are limited to 64 KiB when `Content-Length` is supplied. Handlers use the shared repository and existing domain use cases; no shell or arbitrary process execution is exposed.

## Manual verification

1. Install the debug APK and open **Settings → API settings**.
2. Keep the fixed URL `http://127.0.0.1`, use port `8080` or another port from `1024..65535`, and keep automatic token mode, then enable the API server. The token is generated and stored automatically on first initialization.
3. For an emulator or USB-connected device, forward the port:

```powershell
adb reverse tcp:8080 tcp:8080
```

4. Check the public health endpoint:

```powershell
curl.exe -i http://127.0.0.1:8080/api/v1/health
```

Expected: `200` and `{"status":"ok"}`.

5. Set the token without writing it to a report or shell history where that matters:

```powershell
$token = '<copy-token-from-Settings>'
$headers = @{ Authorization = "Bearer $token" }
```

6. Check every protected endpoint:

```powershell
curl.exe -i http://127.0.0.1:8080/api/v1/devices -H "Authorization: Bearer $token"
curl.exe -i http://127.0.0.1:8080/api/v1/devices/1 -H "Authorization: Bearer $token"
curl.exe -i http://127.0.0.1:8080/api/v1/devices/1/status -H "Authorization: Bearer $token"
curl.exe -i -X POST http://127.0.0.1:8080/api/v1/devices/1/wake -H "Authorization: Bearer $token"
```

Create a test device:

```powershell
$body = '{"name":"Office PC","ipAddress":"192.168.1.20","macAddress":"AA:BB:CC:DD:EE:FF","broadcastAddress":"255.255.255.255","wolPort":9,"statusCheckMethod":"ICMP_AND_TCP","statusCheckPort":3389}'
curl.exe -i -X POST http://127.0.0.1:8080/api/v1/devices -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d $body
```

Then replace `1` with the returned device ID and check:

```powershell
curl.exe -i -X PUT http://127.0.0.1:8080/api/v1/devices/1 -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d $body
curl.exe -i -X DELETE http://127.0.0.1:8080/api/v1/devices/1 -H "Authorization: Bearer $token"
```

Auth checks:

```powershell
curl.exe -i http://127.0.0.1:8080/api/v1/devices
curl.exe -i http://127.0.0.1:8080/api/v1/devices -H 'Authorization: Bearer wrong-token'
```

Both must return `401`. Copy the masked token from API Settings for protected requests. Automatic token refresh and custom-token save are Settings-only operations.

The API does not expose a token-management endpoint. Automatic generation, automatic refresh, custom-token save, and copy are API Settings-only operations; tokens remain masked in the UI. An unsaved custom-token selection is discarded when leaving the screen.

The API Settings page does not expose bind-address editing. A port below `1024`, an invalid port, or a port already in use must produce an error and leave the server stopped; it must not terminate the Android process. The Cloudflare Tunnel page does not expose a separate origin field: when connected, its ingress forwards to the same loopback origin `127.0.0.1:<configured API port>`. Tunnel connection failure turns the UI switch off; only an allowlisted short cause such as `Provided Tunnel token is not valid.` is shown, while full runtime diagnostics remain in Logcat. A fresh install shows the token form and no start-failure message. Tunnel token editing is locked during connection, and the masked preview is not copyable.
