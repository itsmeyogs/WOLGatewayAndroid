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

Contoh:

```bash
curl -i http://127.0.0.1:8080/api/v1/health
```

### `GET /devices`

Menjalankan status check best-effort untuk seluruh device tersimpan, lalu mengembalikan satu snapshot dengan status terbaru. Client tidak perlu memanggil `/status` satu per satu.

Jika status check satu device gagal, device tersebut tetap dikembalikan menggunakan status terakhir yang tersimpan. Kegagalan satu target tidak menggagalkan seluruh response.

Response `200`: array `DeviceResponse`.

Contoh:

```bash
curl -i http://127.0.0.1:8080/api/v1/devices \
  -H "Authorization: Bearer $TOKEN"
```

### `GET /diagnostics`

Mengambil snapshot diagnostics gateway untuk monitoring dan troubleshooting. Endpoint ini read-only dan tidak menjalankan status check device atau network scan.

Response `200` berisi versi aplikasi, Android/ABI, interface aktif, API, tunnel, `GatewayService`, safe errors, serta `selectedMode` dan `effectiveMode`.

Contoh:

```bash
curl -i http://127.0.0.1:8080/api/v1/diagnostics \
  -H "Authorization: Bearer $TOKEN"
```

`effectiveMode` adalah mode runtime aktual. Jika Root Mode gagal dan fallback terjadi, response dapat memuat `selectedMode: "ROOT"`, `effectiveMode: "STANDARD"`, dan `fallback: true`.

Token, credential, raw shell output, command, dan path sensitif tidak pernah dikembalikan.

### `POST /discovery/scan`

Memindai subnet IPv4 pada interface Android yang aktif. Endpoint menunggu scan selesai, dengan batas waktu discovery sekitar 15 detik, lalu mengembalikan hasil final. Hasil kosong adalah response valid.

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

IP yang sudah tersimpan tidak dikembalikan. Hanya satu scan yang berjalan pada satu waktu; request scan berikutnya menunggu scan aktif selesai.

Mode service mengikuti runtime gateway:

- Standard Mode memakai discovery biasa; MAC dapat `null`.
- Root Mode memakai neighbor MAC bila RootSupervisor aktif dan capability/consent tersedia.
- Root fallback ke Standard Mode tidak menjalankan root command dari endpoint API.

Contoh:

```bash
curl -i -X POST http://127.0.0.1:8080/api/v1/discovery/scan \
  -H "Authorization: Bearer $TOKEN"
```

### `GET /devices/{id}`

Mengambil konfigurasi dan state terakhir satu device. Nilai `status`, `lastCheckAt`, dan `lastWolAt` berasal dari state terakhir yang tersimpan.

Response:

- `200` — device ditemukan.
- `401` — token tidak ada atau tidak valid.
- `404` — ID tidak ditemukan.

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
| `request_too_large` | Body melebihi 64 KiB |
| `bad_request` | Request atau parameter tidak valid |
| `internal_error` | Error internal yang tidak diekspos detailnya |

API tidak menaruh token atau secret pada response error, log, notification, atau diagnostics.

## 8. Batasan dan perilaku

- `GET /devices` menjalankan status check best-effort untuk seluruh device, lalu mengembalikan status terbaru yang tersimpan.
- Kegagalan status check satu device tidak menggagalkan response seluruh daftar; status terakhir device tersebut tetap dikembalikan.
- API server harus aktif melalui Settings sebelum endpoint dapat digunakan.
- Tunnel hanya meneruskan API yang sama; tunnel bukan mekanisme autentikasi.
- Request body dibatasi 64 KiB.
- Tidak ada endpoint token-management, shell execution, atau arbitrary process execution.
