# WoL Gateway

WoL Gateway turns an Android device into a Wake-on-LAN (WoL) gateway to wake PCs and other devices on a local network. It provides an Android UI, an authenticated REST API, optional Cloudflare Tunnel integration, background services, diagnostics, and an optional Root Mode.

## Downloads & Client

- **WoL Gateway (Android):** Download the latest APK release from [GitHub Releases](https://github.com/itsmeyogs/WOLGatewayAndroid/releases).
- **WoL Client:** Companion client app available at [wol_client](https://github.com/itsmeyogs/wol_client).

## Features

- WoL device registration, editing, deletion, search, and detail view.
- LAN subnet device discovery.
- Magic packet transmission via UDP broadcast.
- Target status checks via ICMP and/or TCP.
- Versioned REST API on `/api/v1`.
- Configurable CORS origins for browser/web client access.
- API token stored using Android Keystore.
- Optional Cloudflare Tunnel for external network access.
- Foreground service, API autostart, tunnel autostart, and network recovery.
- Standard Mode as default; Root Mode as an optional feature.
- Secure diagnostics with secret redaction and thermal data when available.
- Jetpack Compose Material 3 UI with light/dark theme and accessible layout.

## How It Works

```text
External Client
      │
      ├── Cloudflare Tunnel (optional)
      │
Android Gateway ── Ktor REST API
      │
      └── LAN ── UDP WoL / ICMP / TCP ── Target Device
```

Target status is decoupled from API, tunnel, and service status:

- `ONLINE`: Active connectivity probe succeeded.
- `OFFLINE`: Strong evidence that the target is unreachable.
- `UNKNOWN`: Ambiguous result or insufficient evidence.

`PACKET_SENT` only confirms the magic packet was transmitted locally; it is not proof that the target has powered on.

## Requirements

- Android 10 or later (`minSdk 29`).
- ARM64 architecture (`arm64-v8a`).
- Same Wi-Fi or Ethernet network as the WoL targets.
- Root is not required for Standard Mode.
- Cloudflare account/token required only when Tunnel is used.

## Usage

1. Download and install the latest APK from [Releases](https://github.com/itsmeyogs/WOLGatewayAndroid/releases).
2. Open **Add Devices**.
3. Run LAN discovery or register manually.
4. Fill in target name, IP address, and MAC address.
5. Verify broadcast address and WoL port configuration if needed.
6. Save the device.
7. Use **Wake** to send a magic packet.
8. Use **Check status** to inspect target reachability.
9. Enable **API service** via **Settings → API settings** if external requests are needed.

Discovery does not automatically persist devices. Discovered targets must be selected and saved manually.

## REST API

Default API URL:

```text
http://127.0.0.1:8080/api/v1
```

Bind address is loopback-only (`127.0.0.1`). Only ports `1024..65535` can be configured via Settings. The API server is stopped by default and must be explicitly enabled from the app.

### Endpoints

| Method | Path | Auth | Purpose |
|---|---|---:|---|
| `GET` | `/health` | No | Minimal health check |
| `GET` | `/diagnostics` | Yes | Redacted gateway diagnostics |
| `POST` | `/discovery/scan` | Yes | Scan LAN devices |
| `GET` | `/devices` | Yes | List devices |
| `GET` | `/devices/{id}` | Yes | Get device details |
| `POST` | `/devices` | Yes | Add device |
| `PUT` | `/devices/{id}` | Yes | Update device |
| `DELETE` | `/devices/{id}` | Yes | Delete device |
| `GET` | `/devices/{id}/status` | Yes | Single status check |
| `POST` | `/devices/{id}/wake` | Yes | Send magic packet |

Protected endpoints require:

```http
Authorization: Bearer <api-token>
```

Health check:

```powershell
curl.exe -i http://127.0.0.1:8080/api/v1/health
```

List devices:

```powershell
$token = '<copy-token-from-Settings>'
curl.exe -i http://127.0.0.1:8080/api/v1/devices `
  -H "Authorization: Bearer $token"
```

Add device:

```powershell
$body = '{"name":"Office PC","ipAddress":"192.168.1.20","macAddress":"AA:BB:CC:DD:EE:FF","broadcastAddress":"255.255.255.255","wolPort":9,"statusCheckMethod":"ICMP_AND_TCP","statusCheckPort":3389}'
curl.exe -i -X POST http://127.0.0.1:8080/api/v1/devices `
  -H "Authorization: Bearer $token" `
  -H 'Content-Type: application/json' `
  -d $body
```

Errors return a consistent envelope:

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

Common error codes: `unauthorized`, `invalid_json`, `validation_failed`, `duplicate_device`, `device_not_found`, `discovery_timeout`, `request_too_large`, `bad_request`, and `internal_error`.

Full endpoint specifications are documented in [`docs/API_ENDPOINTS.md`](docs/API_ENDPOINTS.md) and [`docs/API.md`](docs/API.md).

## API Token

- Generated automatically upon first API setup.
- Persisted in Android Keystore-backed secure storage.
- Can be replaced with a custom token or regenerated via Settings.
- Displayed masked in UI.
- No remote token-management endpoint exists.
- Never logged, notified, exposed in diagnostics, error envelopes, reports, or screenshots.

## CORS Allowed Origins

When calling the REST API directly from browser-based clients (such as web applications or single-page apps), browsers enforce Cross-Origin Resource Sharing (CORS). If the web client domain is not allowlisted, requests will fail due to CORS policy errors.

CORS is always active on the Ktor server and configurable via **Settings → API settings → CORS allowed origins**.

### Configuration

- **Development origins:** Web client origins during local development (default: `http://localhost:9999`).
- **Production origins:** Web client production domains (e.g. `https://app.example.com`).

### Format & Validation Rules

- Enter complete origins including scheme: `http://` or `https://` (and port if non-standard).
- Separate multiple origins with commas: `http://localhost:9999, https://app.example.com`.
- **Do not** include paths, query strings, fragments, wildcards (`*`), or wildcard subdomains.
- Valid: `http://localhost:9999`, `https://app.example.com:8443`.
- Invalid: `localhost:9999` (missing scheme), `https://app.example.com/` (trailing path), `https://*.example.com` (wildcards rejected).

Tap **Save & restart service** to apply. The app persists the allowed origins to DataStore, restarts the Ktor API server, and reconnects an active Cloudflare Tunnel.

Preflight (`OPTIONS`) requests, along with `Authorization` and `Content-Type` headers, are allowed for configured origins. CORS is not an authentication layer; valid Bearer tokens remain required for protected endpoints.

## Cloudflare Tunnel

Cloudflare Tunnel exposes the local Android API externally without port forwarding or inbound firewall openings. Outbound tunnels connect from Android to Cloudflare Edge; external clients connect via Cloudflare HTTPS hostnames.

```text
External Client
      │ HTTPS
      ▼
Cloudflare Edge
      │ encrypted tunnel
      ▼
cloudflared on Android
      │ loopback
      ▼
Ktor API: 127.0.0.1:<configured-api-port>
```

The tunnel only forwards traffic. It is not an auth layer; all endpoints except `/api/v1/health` still require:

```http
Authorization: Bearer <api-token>
```

Origin URL always mirrors the configured local API port:

```text
127.0.0.1:<configured-api-port>
```

The origin cannot be set independently from the Tunnel page. The API service must be active before starting the tunnel. Autostart also requires active API service and a saved tunnel token.

### Prerequisites

- Cloudflare account.
- Active domain on Cloudflare.
- Subdomain to use, e.g. `wol.example.com`.
- Android device connected to the target LAN.
- WoL Gateway API verified locally.

### Creating a Tunnel

Use a **remotely-managed tunnel** in Cloudflare Dashboard. The Android app embeds `cloudflared` and consumes the token; no external daemon is needed on PC.

1. Open **Cloudflare Dashboard**.
2. Select your account and domain.
3. Navigate to **Zero Trust → Networks → Connectors → Tunnels**.
4. Click **Create a tunnel**.
5. Select **Cloudflared** connector.
6. Enter tunnel name, e.g. `wol-gateway-home`.
7. Save tunnel.
8. Copy the **tunnel token** from the connector page.
9. Do not commit, log, or leak this token.

In the Android app:

1. Go to **Settings → Tunnel**.
2. Paste the tunnel token.
3. Save token.
4. Toggle **Connection** switch to `ON`.
5. Verify status changes to `CONNECTED`.

Tunnel tokens are stored in Keystore-backed storage. Tokens cannot be edited while in `CONNECTING`, `CONNECTED`, or `RECONNECTING` states.

### Configuring Public Hostname and Route

1. Open the tunnel details in Cloudflare Dashboard.
2. Go to **Routes** / **Published application**.
3. Select **Add route → Published application**.
4. Enter **Subdomain** (e.g. `wol`).
5. Select **Domain** (e.g. `example.com`).
6. Target hostname: `wol.example.com`.
7. Enter **Service URL** matching configured API port:

   ```text
   http://127.0.0.1:8080
   ```

8. Save route.
9. Ensure DNS CNAME record is created/routed by Cloudflare.
10. Confirm connector status displays **Healthy** / **Connected**.

Do not append `/api/v1` to the service URL; use root `http://127.0.0.1:<port>`. Clients call:

```text
https://wol.example.com/api/v1/health
https://wol.example.com/api/v1/devices
https://wol.example.com/api/v1/devices/1/wake
```

### Route Configuration Example

Conceptual equivalent ingress YAML:

```yaml
tunnel: <tunnel-id>

ingress:
  - hostname: wol.example.com
    service: http://127.0.0.1:8080
  - service: http_status:404
```

### Testing External Access

Run tests outside LAN (cellular or separate network):

```powershell
curl.exe -i https://wol.example.com/api/v1/health
```

Expected:

```json
{"status":"ok"}
```

Test protected endpoints with token:

```powershell
$token = '<copy-token-from-Settings>'
curl.exe -i https://wol.example.com/api/v1/devices `
  -H "Authorization: Bearer $token"
```

Requests without token or with invalid token must return `401`.

### Operational Rules

- Start **API service** before connecting tunnel.
- Route port on Cloudflare must match the API port in Settings.
- Never set origin to the Android LAN IP; use `127.0.0.1:<port>`.
- Do not open router ports to the internet for Tunnel.
- Do not treat the tunnel URL as an auth mechanism.
- Tunnel must be `CONNECTED` before external requests succeed.
- Stopping the API disconnects the tunnel; stopping the tunnel leaves local API intact.
- Auto-reconnect operates only when connection is active and token is saved.

Supported tunnel states:
`NOT_CONFIGURED`, `DISCONNECTED`, `CONNECTING`, `CONNECTED`, `RECONNECTING`, `ERROR`.

Binary provenance and lifecycle details are documented in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) and [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md).

### Tunnel Troubleshooting

| Symptom | Check |
|---|---|
| `NOT_CONFIGURED` | Save tunnel token in Settings. |
| Switch toggles back `OFF` | Invalid token, failed connection, or API not running. Check error message in Tunnel screen. |
| `502 Bad Gateway` | Ensure API service is active, route port matches API port, and origin is `http://127.0.0.1:<port>`. |
| Hostname does not resolve | Verify public route hostname and DNS record on Cloudflare dashboard. |
| Tunnel `CONNECTED`, API `401` | Tunnel is healthy; supply valid API Bearer token. |
| Browser CORS error / blocked | Add client origin (e.g. `https://app.example.com`) in **Settings → API settings → CORS allowed origins** and save. |
| `/health` OK, device fails | Check device ID, target status, MAC, broadcast address, firewall, and LAN connectivity. |
| Repeated reconnects | Check Android connectivity, battery optimization exclusions, and OEM background kill policies. |

## Standard Mode & Root Mode

### Standard Mode

Default non-root mode. Android foreground service maintains API and tunnel runtimes within OS/OEM constraints, battery policies, notification permissions, and background restrictions.

### Root Mode

Optional mode with explicit user consent. Root supervisor manages process lifecycle via allowlisted operations, readiness checks, limited restarts, and safe fallback to Standard Mode.

Root Mode does not:

- Execute arbitrary shell commands.
- Run Ktor, Room, Keystore, or credentials as UID 0.
- Guarantee immunity from aggressive OEM task killers.
- Bypass physical network topology or ARP constraints.

## Security

- API binds loopback-only (`127.0.0.1`).
- Configurable CORS allowed origins (rejects wildcards and paths; preflight support for web clients).
- Protected routes enforce Bearer authentication.
- Constant-time token comparison prevents timing attacks.
- Request body capped at 64 KiB.
- Device inputs validated strictly at trust boundaries.
- No shell or arbitrary process execution via API.
- Sensitive credentials isolated in Keystore-backed storage.
- Diagnostics, logs, notifications, and error responses redact secrets.
- Root features require explicit user consent and capabilities check.

## Limitations

- WoL depends on target NIC, BIOS/UEFI settings, power states, and subnet broadcast rules.
- MAC addresses may not resolve via ARP scan; manual entry is supported.
- Failed ICMP/TCP checks indicate `UNKNOWN` when reachability evidence is inconclusive, not automatically `OFFLINE`.
- Default TCP status check port is `3389` (RDP); customizable per device.
- Background recovery is best-effort under Android OEM resource controls.
- Thermal diagnostics provide hardware health insight, not WoL target proof.

## Architecture

Single Android module structured by layer:

- **Presentation**: Jetpack Compose, ViewModel, navigation, shared UI components.
- **Domain**: models, validation, use cases, repository/service contracts.
- **Data**: Room database, DataStore, repositories, Keystore secure storage.
- **Gateway**: Ktor embedded server, routing, DTOs, auth, error mapping.
- **Network/WoL**: discovery, status checker, magic packet UDP sender.
- **Tunnel**: controller, state machine, retry policy, runtime adapter.
- **Android service**: foreground service, boot/network recovery, lifecycle coordinator.
- **Root**: capability detector, consent check, supervisor, safe fallback.

Base package: `com.yihs.wolgateway`.

## Documentation

- [`docs/PRD.md`](docs/PRD.md) — Product requirements, scope, security, acceptance criteria.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — Deployment, layers, lifecycle, storage, security boundaries.
- [`docs/API.md`](docs/API.md) — API summary and manual verification instructions.
- [`docs/API_ENDPOINTS.md`](docs/API_ENDPOINTS.md) — Endpoint specifications and request/response schemas.
- [`docs/DESIGN.md`](docs/DESIGN.md) — UI/UX guidelines, design tokens, navigation, accessibility.
- [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md) — Checkpoints, status, and validation logs.

## Status

Core features (Compose UI, device management, REST API, ARM64 tunnel, background service, Root Mode, diagnostics) implemented and validated per test suites. Real-world behavior depends on local network topology, target device hardware, and OEM background policies.
