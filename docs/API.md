# WoL Gateway API — v1

Base path: `/api/v1`.

Default server bind: `127.0.0.1:8080`. The API server is stopped by default and is started from Settings. All device routes require:

```http
Authorization: Bearer <api-token>
```

The token is automatically generated on first Settings initialization and stored encrypted with an Android Keystore-backed AES key. It can be replaced by a custom token or refreshed automatically. It must not appear in logs, diagnostics, notifications, or error responses.

## Endpoints

| Method | Path | Auth | Response |
|---|---|---:|---|
| GET | `/health` | No | `200 {"status":"ok"}` |
| GET | `/devices` | Yes | `200` device array |
| GET | `/devices/{id}` | Yes | `200` device |
| POST | `/devices` | Yes | `201` device |
| PUT | `/devices/{id}` | Yes | `200` device |
| DELETE | `/devices/{id}` | Yes | `204` |
| GET | `/devices/{id}/status` | Yes | `200` status result |
| POST | `/devices/{id}/wake` | Yes | `200` WoL result |

Full paths include the base path. For example, the health URL is `/api/v1/health`.

### Expected status codes

| Endpoint | Success | Expected failures |
|---|---:|---|
| `GET /health` | `200` | — |
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
2. Keep base URL `http://127.0.0.1`, port `8080`, and automatic token mode, then enable the API server. The token is generated and stored automatically on first initialization.
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
