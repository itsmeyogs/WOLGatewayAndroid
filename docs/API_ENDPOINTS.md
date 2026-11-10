# API Endpoint Reference — WoL Gateway

## 1. Tujuan

REST API untuk mengelola device WoL, memeriksa status target, dan mengirim magic packet dari Android gateway.

API memakai server Ktor yang sama dengan Cloudflare Tunnel bila tunnel aktif.

## 2. Base URL

Local default:

```text
http://127.0.0.1:8080/api/v1
```

Port dapat diubah melalui **Settings → API settings**, tetapi hanya `1024..65535`. Bind address selalu `127.0.0.1`.

Dengan tunnel, gunakan URL publik tunnel dengan path yang sama:

```text
https://<tunnel-host>/api/v1
```

## 3. Authentication

Semua endpoint device memerlukan:

```http
Authorization: Bearer <api-token>
```

Token dibuat, diganti, dan disimpan melalui **Settings → API settings**. API tidak menyediakan endpoint untuk mengelola token.

`GET /health` bersifat publik dan hanya mengembalikan health minimum.

## 4. Endpoint ringkas

| Method | Path | Auth | Kegunaan |
|---|---|---:|---|
| `GET` | `/health` | No | Memastikan API hidup |
| `GET` | `/diagnostics` | Yes | Mengambil diagnostics gateway yang sudah di-redact |
| `POST` | `/discovery/scan` | Yes | Memindai device pada LAN Android |
| `GET` | `/devices` | Yes | Mengambil semua device |
| `GET` | `/devices/{id}` | Yes | Mengambil satu device |
| `POST` | `/devices` | Yes | Menambah device |
| `PUT` | `/devices/{id}` | Yes | Mengubah device |
| `DELETE` | `/devices/{id}` | Yes | Menghapus device |
| `GET` | `/devices/{id}/status` | Yes | Menjalankan status check satu kali |
| `POST` | `/devices/{id}/wake` | Yes | Mengirim magic packet WoL |

## 5. Endpoint detail

### `GET /health`

Memeriksa apakah API menerima request. Tidak memeriksa status PC target, tunnel, atau service Android.

Response `200`:

```json
{
  "status": "ok"
}
```

Endpoint ini tidak memakai authentication. Request yang tidak valid ke path lain ditangani sebagai routing error, bukan response health.

Contoh:

```bash
curl -i http://127.0.0.1:8080/api/v1/health
```

### `GET /devices`

Menjalankan status check best-effort untuk seluruh device tersimpan, lalu mengembalikan satu snapshot dengan status terbaru. Client tidak perlu memanggil `/status` satu per satu.

Jika status check satu device gagal, device tersebut tetap dikembalikan menggunakan status terakhir yang tersimpan. Kegagalan satu target tidak menggagalkan seluruh response.

Response `200`: array `DeviceResponse`.

Response sukses `200`:

```json
[
  {
    "id": 1,
    "name": "Office PC",
    "ipAddress": "192.168.1.20",
    "macAddress": "AA:BB:CC:DD:EE:FF",
    "broadcastAddress": "255.255.255.255",
    "wolPort": 9,
    "statusCheckMethod": "ICMP_AND_TCP",
    "statusCheckPort": 3389,
    "status": "ONLINE",
    "lastCheckAt": 1730000000000,
    "lastCheckLatencyMs": 42,
    "lastCheckReason": "tcp_connected",
    "lastWolResult": "NOT_ATTEMPTED",
    "lastWolAt": null
  }
]
```

Response gagal `401`:

```json
{
  "error": {
    "code": "unauthorized",
    "message": "Authentication required",
    "fields": {}
  }
}
```

Contoh:

```bash
curl -i http://127.0.0.1:8080/api/v1/devices \
  -H "Authorization: Bearer $TOKEN"
```

### `GET /diagnostics`

Mengambil snapshot diagnostics gateway untuk monitoring dan troubleshooting. Endpoint ini read-only dan tidak menjalankan status check device atau network scan.

Response `200` mengikuti data yang ditampilkan Diagnostics Screen, dibagi menjadi tiga object top-level: `general`, `thermal`, dan `serviceMode`.

```text
DiagnosticsResponse
├── general: DiagnosticsGeneralResponse
├── thermal: DiagnosticsThermalResponse
└── serviceMode: DiagnosticsServiceModeResponse
```

`serviceMode.data` berisi seluruh data mode runtime pada `effectiveMode`:

- `effectiveMode: "STANDARD"`: delapan item Standard Mode.
- `effectiveMode: "ROOT"`: enam item Root Mode.
- Root fallback: `effectiveMode: "STANDARD"`, `fallback: true`, dan delapan item Standard Mode.

Response juga memuat `device` (`manufacturer`, `model`, `product`) dan `battery` (`levelPercent`, `charging`, `batterySaver`). Field baterai dapat bernilai `null` bila tidak tersedia. Identifier sensitif seperti serial number, Android ID, dan IMEI tidak dikembalikan.

`thermal` adalah top-level object yang memuat suhu perangkat yang tersedia secara aman. Object `thermal` selalu ada; nilainya tidak digabung ke `general` dan tidak bernilai `null`.

- `source`: `ANDROID_PUBLIC`, `ROOT_ALLOWLIST`, atau `UNAVAILABLE`.
- `mode`: mode efektif saat sample diambil.
- `status`: `NOMINAL`, `LIGHT`, `MODERATE`, `SEVERE`, `CRITICAL`, atau `UNKNOWN`.
- `batteryTemperatureCelsius`, `thermalHeadroom`, dan `thermalStatus`: nullable bila API/OEM tidak menyediakan data.
- `sensors`: maksimal 16 sensor dengan nama/type yang sudah dinormalisasi; tidak ada path thermal zone.
- `sampledAt`: Unix epoch milliseconds.
- `available`: false bila tidak ada data valid.
- `safeError`: nullable, allowlisted, tanpa raw root output.

### Schema `thermal`

| Field | JSON type | Nullable | Nilai/arti |
|---|---|---:|---|
| `source` | `string` | No | `ANDROID_PUBLIC`, `ROOT_ALLOWLIST`, `UNAVAILABLE` |
| `mode` | `string` | No | Effective mode saat sample: `STANDARD` atau `ROOT` |
| `status` | `string` | No | `NOMINAL`, `LIGHT`, `MODERATE`, `SEVERE`, `CRITICAL`, `UNKNOWN` |
| `batteryTemperatureCelsius` | `number` | Yes | Suhu battery dalam Celsius; berasal dari Android public battery API |
| `thermalStatus` | `string` | Yes | Nama status thermal Android; biasanya sama dengan status aplikasi bila tersedia |
| `thermalHeadroom` | `number` | Yes | Nilai headroom Android; bukan Celsius dan tidak boleh diperlakukan sebagai suhu |
| `sensors` | `array` | No | Maksimal 16 sensor; dapat kosong |
| `sampledAt` | `integer` | No | Unix epoch milliseconds |
| `available` | `boolean` | No | `true` bila minimal satu data thermal valid tersedia |
| `safeError` | `string` | Yes | Pesan aman; tidak memuat path, command, raw output, atau credential |

### Schema `thermal.sensors[]`

| Field | JSON type | Nullable | Nilai/arti |
|---|---|---:|---|
| `name` | `string` | No | Nama normalisasi: `battery`, `cpu`, `gpu`, `soc`, `skin`, `modem`, atau `unknown` |
| `type` | `string` | No | `BATTERY`, `CPU`, `GPU`, `SOC`, `SKIN`, `MODEM`, atau `UNKNOWN` |
| `temperatureCelsius` | `number` | No | Suhu sensor dalam Celsius |
| `status` | `string` | No | Status thermal hasil klasifikasi sensor |
| `source` | `string` | No | `ANDROID_PUBLIC` atau `ROOT_ALLOWLIST` |

Sensor tidak boleh diasumsikan selalu ada, selalu lengkap, atau berada dalam urutan tertentu. Client harus memakai `name`/`type`, bukan index array.

### Perbedaan hasil Standard dan Root

| Kondisi runtime | `thermal.mode` | `thermal.source` | Sensor yang mungkin ada | `serviceMode.effectiveMode` | `serviceMode.fallback` |
|---|---|---|---|---|---:|
| Non-root/Standard dengan public data | `STANDARD` | `ANDROID_PUBLIC` | Biasanya `battery` | `STANDARD` | `false` |
| Non-root tanpa data | `STANDARD` | `UNAVAILABLE` | `[]` | `STANDARD` | `false` |
| Root aktif, allowlist terbaca | `ROOT` | `ROOT_ALLOWLIST` | `battery` + CPU/GPU/SoC/SKIN/vendor yang dikenali | `ROOT` | `false` |
| Root aktif, sensor root tidak terbaca | `ROOT` | `ANDROID_PUBLIC` | Public data, biasanya `battery` | `ROOT` | `false` |
| Root supervisor gagal total | `STANDARD` | `ANDROID_PUBLIC`/`UNAVAILABLE` | Standard Mode data | `STANDARD` | `true` |

Root Mode tidak menjamin sensor tambahan. Root hanya membuka kemungkinan pembacaan sensor allowlist; ketersediaan tetap bergantung kernel, OEM, dan thermal zone device.

Standard Mode memakai Android public API. Root Mode boleh memakai sensor thermal allowlist dan fallback ke public API bila pembacaan root gagal. Data thermal bersifat diagnostik, bukan bukti target WoL online dan bukan health check API/tunnel.

Response Standard Mode `200`:

```json
{
  "general": {
    "appVersion": "1.0", "androidVersion": "14", "abi": "arm64-v8a", "deviceClassification": "OTHER", "networkInterface": "wlan0",
    "api": {"label": "API", "value": "Running", "status": "PASS"}, "tunnel": {"label": "Tunnel", "value": "Not configured", "status": "NOT_CONFIGURED"}, "gatewayService": {"label": "GatewayService", "value": "Running", "status": "PASS"}, "safeErrors": [],
    "device": {"manufacturer": "Google", "model": "Pixel 7", "product": "panther"}, "battery": {"levelPercent": 82, "charging": true, "batterySaver": false}
  },
  "thermal": {
    "source": "ANDROID_PUBLIC", "mode": "STANDARD", "status": "NOMINAL",
    "batteryTemperatureCelsius": 34.2, "thermalStatus": "NOMINAL", "thermalHeadroom": 0.18,
    "sensors": [{"name": "battery", "type": "BATTERY", "temperatureCelsius": 34.2, "status": "NOMINAL", "source": "ANDROID_PUBLIC"}],
    "sampledAt": 1730000000000, "available": true, "safeError": null
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

Response Root Mode `200`:

```json
{
  "general": {
    "appVersion": "1.0", "androidVersion": "14", "abi": "arm64-v8a", "deviceClassification": "OTHER", "networkInterface": "wlan0",
    "api": {"label": "API", "value": "Running", "status": "PASS"}, "tunnel": {"label": "Tunnel", "value": "Connected", "status": "PASS"}, "gatewayService": {"label": "GatewayService", "value": "Running", "status": "PASS"}, "safeErrors": [],
    "device": {"manufacturer": "Google", "model": "Pixel 7", "product": "panther"}, "battery": {"levelPercent": 82, "charging": true, "batterySaver": false}
  },
  "thermal": {
    "source": "ROOT_ALLOWLIST", "mode": "ROOT", "status": "NOMINAL",
    "batteryTemperatureCelsius": 34.2, "thermalStatus": "NOMINAL", "thermalHeadroom": 0.18,
    "sensors": [{"name": "battery", "type": "BATTERY", "temperatureCelsius": 34.2, "status": "NOMINAL", "source": "ANDROID_PUBLIC"}, {"name": "cpu", "type": "CPU", "temperatureCelsius": 41.0, "status": "NOMINAL", "source": "ROOT_ALLOWLIST"}],
    "sampledAt": 1730000000000, "available": true, "safeError": null
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

Contoh Root aktif tetapi sensor root gagal; thermal fallback sendiri ke public API `200`:

```json
{
  "general": {
    "appVersion": "1.0", "androidVersion": "14", "abi": "arm64-v8a", "deviceClassification": "OTHER", "networkInterface": "wlan0",
    "api": {"label": "API", "value": "Running", "status": "PASS"}, "tunnel": {"label": "Tunnel", "value": "Connected", "status": "PASS"}, "gatewayService": {"label": "GatewayService", "value": "Running", "status": "PASS"}, "safeErrors": ["Root thermal sensors unavailable; using Android public data"],
    "device": {"manufacturer": "Samsung", "model": "SM-S918B", "product": "dm3q"}, "battery": {"levelPercent": 61, "charging": false, "batterySaver": false}
  },
  "thermal": {
    "source": "ANDROID_PUBLIC", "mode": "ROOT", "status": "LIGHT",
    "batteryTemperatureCelsius": 38.7, "thermalStatus": "LIGHT", "thermalHeadroom": 0.42,
    "sensors": [{"name": "battery", "type": "BATTERY", "temperatureCelsius": 38.7, "status": "NOMINAL", "source": "ANDROID_PUBLIC"}],
    "sampledAt": 1730000000000, "available": true, "safeError": "Root thermal sensors unavailable; using Android public data"
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

Contoh Root supervisor gagal total lalu gateway kembali ke Standard Mode `200`:

```json
{
  "general": {"safeErrors": ["Root mode unavailable; using Standard Mode"]},
  "thermal": {
    "source": "ANDROID_PUBLIC", "mode": "STANDARD", "status": "NOMINAL",
    "batteryTemperatureCelsius": 34.2, "thermalStatus": "NOMINAL", "thermalHeadroom": 0.18,
    "sensors": [{"name": "battery", "type": "BATTERY", "temperatureCelsius": 34.2, "status": "NOMINAL", "source": "ANDROID_PUBLIC"}],
    "sampledAt": 1730000000000, "available": true, "safeError": null
  },
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

Response gagal `401`:

```json
{
  "error": {
    "code": "unauthorized",
    "message": "Authentication required",
    "fields": {}
  }
}
```

Contoh request:

```bash
curl -i http://127.0.0.1:8080/api/v1/diagnostics \
  -H "Authorization: Bearer $TOKEN"
```

`effectiveMode` adalah mode runtime aktual. Jika Root Mode gagal dan fallback terjadi, response memuat `effectiveMode: "STANDARD"` dan `fallback: true`.

### Thermal unavailable `200`

Data thermal tidak tersedia bukan error HTTP. Endpoint tetap mengembalikan `200`; client memakai `available: false`, `status: "UNKNOWN"`, field numerik `null`, dan `sensors: []`.

```json
{
  "general": {
    "appVersion": "1.0",
    "androidVersion": "10",
    "abi": "arm64-v8a",
    "deviceClassification": "OTHER",
    "networkInterface": "wlan0",
    "api": {"label": "API", "value": "Running", "status": "PASS"},
    "tunnel": {"label": "Tunnel", "value": "Not configured", "status": "NOT_CONFIGURED"},
    "gatewayService": {"label": "GatewayService", "value": "Running", "status": "PASS"},
    "safeErrors": [],
    "device": {"manufacturer": "Generic", "model": "Device", "product": "device"},
    "battery": {"levelPercent": null, "charging": null, "batterySaver": null}
  },
  "thermal": {
    "source": "UNAVAILABLE",
    "mode": "STANDARD",
    "status": "UNKNOWN",
    "batteryTemperatureCelsius": null,
    "thermalStatus": null,
    "thermalHeadroom": null,
    "sensors": [],
    "sampledAt": 1730000000000,
    "available": false,
    "safeError": null
  },
  "serviceMode": {
    "effectiveMode": "STANDARD",
    "fallback": false,
    "data": []
  }
}
```

### Client handling rules

- Parse `thermal` as a required object, not an optional nested field under `general`.
- Check `available` before displaying temperature values.
- Treat `batteryTemperatureCelsius`, `thermalStatus`, `thermalHeadroom`, and `safeError` as nullable.
- Accept `sensors: []`; do not require CPU/GPU/SoC readings.
- Do not use `thermalHeadroom` as Celsius.
- Use `thermal.source` to show public versus root-allowlisted data.
- Use `thermal.mode` for the mode that produced the thermal snapshot.
- Use `serviceMode.effectiveMode` for the actual gateway service mode.
- Use `serviceMode.fallback` to detect Root → Standard service fallback.
- A thermal fallback (`thermal.mode: "ROOT"`, `thermal.source: "ANDROID_PUBLIC"`) is different from a gateway fallback (`serviceMode.effectiveMode: "STANDARD"`, `fallback: true`).
- Ignore sensor order and tolerate future sensor names/types.

Token, credential, raw shell output, command, dan path sensitif tidak pernah dikembalikan.

### `POST /discovery/scan`

Memindai subnet IPv4 pada interface Android yang aktif. Endpoint menunggu scan selesai, dengan batas waktu discovery sekitar 30 detik, lalu mengembalikan hasil final. Hasil kosong adalah response valid.

Discovery tidak otomatis menyimpan device. Client memilih hasil lalu memakai `POST /devices` untuk registrasi.

Response `200`:

```json
{
  "durationMs": 4280,
  "devices": [
    {
      "ipAddress": "192.168.1.20",
      "hostName": "OFFICE-PC",
      "macAddress": null,
      "openPorts": [3389]
    }
  ]
}
```

IP yang sudah tersimpan tidak dikembalikan. Hanya satu scan yang berjalan pada satu waktu; request scan berikutnya berbagi hasil scan aktif yang sama.

Mode service mengikuti runtime gateway:

- Standard Mode memakai discovery biasa; MAC dapat `null`.
- Root Mode memakai neighbor MAC bila RootSupervisor aktif dan capability/consent tersedia.
- Root fallback ke Standard Mode tidak menjalankan root command dari endpoint API.

Contoh:

```bash
curl -i -X POST http://127.0.0.1:8080/api/v1/discovery/scan \
  -H "Authorization: Bearer $TOKEN"
```

Response gagal `401`:

```json
{
  "error": {
    "code": "unauthorized",
    "message": "Authentication required",
    "fields": {}
  }
}
```

Response gagal `504` jika scan melewati batas waktu:

```json
{
  "error": {
    "code": "discovery_timeout",
    "message": "Device discovery timed out",
    "fields": {}
  }
}
```

### `GET /devices/{id}`

Mengambil konfigurasi dan state terakhir satu device. Nilai `status`, `lastCheckAt`, dan `lastWolAt` berasal dari state terakhir yang tersimpan.

Response:

- `200` — device ditemukan.
- `401` — token tidak ada atau tidak valid.
- `404` — ID tidak ditemukan.

Response sukses `200`:

```json
{
  "id": 1,
  "name": "Office PC",
  "ipAddress": "192.168.1.20",
  "macAddress": "AA:BB:CC:DD:EE:FF",
  "broadcastAddress": "255.255.255.255",
  "wolPort": 9,
  "statusCheckMethod": "ICMP_AND_TCP",
  "statusCheckPort": 3389,
  "status": "ONLINE",
  "lastCheckAt": 1730000000000,
  "lastCheckLatencyMs": 42,
  "lastCheckReason": "tcp_connected",
  "lastWolResult": "NOT_ATTEMPTED",
  "lastWolAt": null
}
```

Response gagal `404`:

```json
{
  "error": {
    "code": "device_not_found",
    "message": "Device not found",
    "fields": {}
  }
}
```

Response gagal `401` memakai envelope `unauthorized` yang dijelaskan pada bagian Error response.

Contoh:

```bash
curl -i http://127.0.0.1:8080/api/v1/devices/1 \
  -H "Authorization: Bearer $TOKEN"
```

### `POST /devices`

Menambah device baru. Setelah persistence berhasil, aplikasi dapat menjalankan status check best-effort secara terpisah; kegagalan check tidak membatalkan device yang sudah tersimpan.

Request body:

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

Field wajib:

- `name`
- `ipAddress`
- `macAddress`

Field opsional memiliki default:

- `broadcastAddress`: `255.255.255.255`
- `wolPort`: `9`
- `statusCheckMethod`: `ICMP_AND_TCP`
- `statusCheckPort`: `3389`

Response:

- `201` — device berhasil dibuat; body berisi device lengkap dengan ID.
- `400` — JSON atau field tidak valid.
- `401` — token tidak ada atau tidak valid.
- `409` — MAC address sudah digunakan device lain.

Response sukses `201` memakai bentuk `DeviceResponse` yang sama seperti `GET /devices/{id}`.

Contoh response sukses `201`:

```json
{
  "id": 1,
  "name": "Office PC",
  "ipAddress": "192.168.1.20",
  "macAddress": "AA:BB:CC:DD:EE:FF",
  "broadcastAddress": "255.255.255.255",
  "wolPort": 9,
  "statusCheckMethod": "ICMP_AND_TCP",
  "statusCheckPort": 3389,
  "status": "UNKNOWN",
  "lastCheckAt": null,
  "lastCheckLatencyMs": null,
  "lastCheckReason": null,
  "lastWolResult": "NOT_ATTEMPTED",
  "lastWolAt": null
}
```

Response gagal `400`:

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

Response gagal `409`:

```json
{
  "error": {
    "code": "duplicate_device",
    "message": "Device already exists",
    "fields": {"field": "MAC_ADDRESS"}
  }
}
```

Response gagal `401` memakai envelope `unauthorized`. Response `400` untuk JSON invalid memakai `invalid_json`.

Contoh:

```bash
curl -i -X POST http://127.0.0.1:8080/api/v1/devices \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name":"Office PC",
    "ipAddress":"192.168.1.20",
    "macAddress":"AA:BB:CC:DD:EE:FF"
  }'
```

### `PUT /devices/{id}`

Mengganti konfigurasi device berdasarkan ID. Body memakai bentuk yang sama dengan `POST /devices` dan tidak memakai partial update.

Response:

- `200` — device berhasil diubah; body berisi device terbaru.
- `400` — JSON, ID, atau field tidak valid.
- `401` — token tidak ada atau tidak valid.
- `404` — ID tidak ditemukan.
- `409` — MAC address sudah digunakan device lain.

Response sukses `200` memakai bentuk `DeviceResponse` yang sama seperti `GET /devices/{id}`.

Contoh response sukses `200`:

```json
{
  "id": 1,
  "name": "Office PC Updated",
  "ipAddress": "192.168.1.20",
  "macAddress": "AA:BB:CC:DD:EE:FF",
  "broadcastAddress": "255.255.255.255",
  "wolPort": 9,
  "statusCheckMethod": "ICMP_AND_TCP",
  "statusCheckPort": 3389,
  "status": "UNKNOWN",
  "lastCheckAt": null,
  "lastCheckLatencyMs": null,
  "lastCheckReason": null,
  "lastWolResult": "NOT_ATTEMPTED",
  "lastWolAt": null
}
```

Response gagal `404`:

```json
{
  "error": {
    "code": "device_not_found",
    "message": "Device not found",
    "fields": {}
  }
}
```

Response gagal `401` memakai envelope `unauthorized`. Response `400` untuk ID/body invalid memakai `bad_request`; response `409` memakai `duplicate_device`.

Contoh:

```bash
curl -i -X PUT http://127.0.0.1:8080/api/v1/devices/1 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name":"Office PC Updated",
    "ipAddress":"192.168.1.20",
    "macAddress":"AA:BB:CC:DD:EE:FF"
  }'
```

### `DELETE /devices/{id}`

Menghapus device. Penghapusan tidak mengirim magic packet dan tidak memengaruhi device lain.

Response:

- `204` — device berhasil dihapus.
- `401` — token tidak ada atau tidak valid.
- `404` — ID tidak ditemukan.

Response sukses `204` tidak memiliki response body.

Response gagal `404`:

```json
{
  "error": {
    "code": "device_not_found",
    "message": "Device not found",
    "fields": {}
  }
}
```

Response gagal `401` memakai envelope `unauthorized`.

Contoh:

```bash
curl -i -X DELETE http://127.0.0.1:8080/api/v1/devices/1 \
  -H "Authorization: Bearer $TOKEN"
```

### `GET /devices/{id}/status`

Menjalankan status check satu kali sesuai konfigurasi device. Hasil disimpan ke device.

Response `200`:

```json
{
  "deviceId": 1,
  "status": "ONLINE",
  "method": "ICMP_AND_TCP",
  "latencyMs": 42,
  "reason": "tcp_connected",
  "checkedAt": 1730000000000
}
```

Makna status:

- `ONLINE` — check memperoleh bukti konektivitas.
- `OFFLINE` — metode check memberi bukti kuat target tidak tersedia.
- `UNKNOWN` — hasil ambigu atau bukti tidak cukup.

Response:

- `200` — check selesai; status target ada pada body.
- `401` — token tidak ada atau tidak valid.
- `404` — ID tidak ditemukan.

Response gagal `404`:

```json
{
  "error": {
    "code": "device_not_found",
    "message": "Device not found",
    "fields": {}
  }
}
```

Response gagal `401` memakai envelope `unauthorized`.

Contoh:

```bash
curl -i http://127.0.0.1:8080/api/v1/devices/1/status \
  -H "Authorization: Bearer $TOKEN"
```

### `POST /devices/{id}/wake`

Mengirim magic packet ke broadcast address dan UDP WoL port milik device. Response `PACKET_SENT` hanya berarti pengiriman lokal tidak menghasilkan error; bukan bukti PC sudah menyala.

Response `200`:

```json
{
  "deviceId": 1,
  "result": "PACKET_SENT",
  "latencyMs": 8,
  "reason": "packet_sent"
}
```

Response:

- `200` — operasi pengiriman selesai; periksa `result`.
- `401` — token tidak ada atau tidak valid.
- `404` — ID tidak ditemukan.

Response gagal `404`:

```json
{
  "error": {
    "code": "device_not_found",
    "message": "Device not found",
    "fields": {}
  }
}
```

Response gagal `401` memakai envelope `unauthorized`.

Contoh:

```bash
curl -i -X POST http://127.0.0.1:8080/api/v1/devices/1/wake \
  -H "Authorization: Bearer $TOKEN"
```

## 6. Bentuk response device

```json
{
  "id": 1,
  "name": "Office PC",
  "ipAddress": "192.168.1.20",
  "macAddress": "AA:BB:CC:DD:EE:FF",
  "broadcastAddress": "255.255.255.255",
  "wolPort": 9,
  "statusCheckMethod": "ICMP_AND_TCP",
  "statusCheckPort": 3389,
  "status": "UNKNOWN",
  "lastCheckAt": null,
  "lastCheckLatencyMs": null,
  "lastCheckReason": null,
  "lastWolResult": "NOT_ATTEMPTED",
  "lastWolAt": null
}
```

Timestamp memakai Unix epoch milliseconds.

## 7. Error response

Semua error memakai envelope berikut:

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

Kode error:

| Code | Arti |
|---|---|
| `unauthorized` | Bearer token tidak ada atau tidak valid |
| `invalid_json` | Body bukan JSON valid atau tidak cocok dengan schema |
| `validation_failed` | Field device tidak valid; detail ada di `fields` |
| `duplicate_device` | MAC address sudah terdaftar |
| `device_not_found` | ID device tidak ditemukan |
| `discovery_timeout` | Discovery melewati batas waktu |
| `request_too_large` | Body melebihi 64 KiB |
| `bad_request` | Request atau parameter tidak valid |
| `internal_error` | Error internal yang tidak diekspos detailnya |

API tidak menaruh token atau secret pada response error, log, notification, atau diagnostics.

## 8. Batasan dan perilaku

- `GET /devices` menjalankan status check best-effort untuk seluruh device, lalu mengembalikan status terbaru yang tersimpan.
- Kegagalan status check satu device tidak menggagalkan response seluruh daftar; status terakhir device tersebut tetap dikembalikan.
- Semua operasi endpoint memiliki batas waktu internal berbasis operasi; timeout tidak mengubah bentuk response sukses atau error envelope.
- `GET /devices` tetap menjalankan live check best-effort. Target lambat memakai state terakhir yang tersimpan.
- Diagnostics mengembalikan snapshot cache; probe root dan thermal diperbarui di background.
- Request discovery bersamaan berbagi satu scan aktif; refresh MAC Root berjalan di background dan MAC yang belum tersedia tetap valid sebagai `null`.
- API server harus aktif melalui Settings sebelum endpoint dapat digunakan.
- Tunnel hanya meneruskan API yang sama; tunnel bukan mekanisme autentikasi.
- Request body dibatasi 64 KiB.
- Tidak ada endpoint token-management, shell execution, atau arbitrary process execution.
