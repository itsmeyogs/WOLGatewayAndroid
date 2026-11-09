# WoL Gateway

WoL Gateway menjadikan perangkat Android sebagai gateway Wake-on-LAN (WoL) untuk membangunkan PC atau perangkat lain di jaringan lokal. Aplikasi menyediakan UI Android, REST API terautentikasi, Cloudflare Tunnel opsional, background service, diagnostics, serta Root Mode opsional.

## Fitur

- Registrasi, edit, hapus, pencarian, dan detail device WoL.
- Discovery device pada subnet LAN aktif.
- Pengiriman magic packet melalui UDP broadcast.
- Pemeriksaan status target melalui ICMP dan/atau TCP.
- REST API versioned pada `/api/v1`.
- API token tersimpan menggunakan Android Keystore.
- Cloudflare Tunnel opsional untuk akses dari jaringan eksternal.
- Foreground service, API autostart, tunnel autostart, dan network recovery.
- Standard Mode sebagai mode utama; Root Mode sebagai fitur opsional.
- Diagnostics aman dengan redaction secret dan data thermal bila tersedia.
- UI Jetpack Compose Material 3, light/dark theme, dan layout accessible.

## Cara kerja

```text
External Client
      │
      ├── Cloudflare Tunnel (opsional)
      │
Android Gateway ── Ktor REST API
      │
      └── LAN ── UDP WoL / ICMP / TCP ── Target Device
```

Status target dipisahkan dari status API, tunnel, dan service:

- `ONLINE`: pemeriksaan memperoleh bukti konektivitas.
- `OFFLINE`: tersedia bukti kuat target tidak reachable.
- `UNKNOWN`: hasil ambigu atau bukti tidak cukup.

`PACKET_SENT` hanya berarti magic packet berhasil dikirim secara lokal. Status tersebut bukan bukti target sudah menyala.

## Persyaratan

- Android 10 atau lebih baru (`minSdk 29`).
- ARM64 (`arm64-v8a`).
- Jaringan Wi-Fi atau Ethernet yang sama dengan target WoL.
- Root tidak diperlukan untuk Standard Mode.
- Cloudflare account/token hanya diperlukan bila Tunnel digunakan.

## Penggunaan

1. Install APK pada perangkat Android.
2. Buka **Add Devices**.
3. Jalankan discovery LAN atau pilih registrasi manual.
4. Isi nama, IP, dan MAC address target.
5. Periksa konfigurasi broadcast address dan port WoL bila diperlukan.
6. Simpan device.
7. Gunakan **Wake** untuk mengirim magic packet.
8. Gunakan **Check status** untuk memeriksa target.
9. Aktifkan **API service** melalui **Settings → API settings** bila aplikasi perlu menerima request eksternal.

Discovery tidak otomatis menyimpan device. Hasil discovery harus dipilih lalu didaftarkan.

## REST API

API default:

```text
http://127.0.0.1:8080/api/v1
```

Bind address selalu `127.0.0.1`. Hanya port `1024..65535` yang dapat diubah melalui Settings. API berhenti secara default dan harus diaktifkan dari aplikasi.

### Endpoint

| Method | Path | Auth | Fungsi |
|---|---|---:|---|
| `GET` | `/health` | Tidak | Health minimum |
| `GET` | `/diagnostics` | Ya | Diagnostics gateway yang sudah di-redact |
| `POST` | `/discovery/scan` | Ya | Scan device LAN |
| `GET` | `/devices` | Ya | Daftar device |
| `GET` | `/devices/{id}` | Ya | Detail device |
| `POST` | `/devices` | Ya | Tambah device |
| `PUT` | `/devices/{id}` | Ya | Ubah device |
| `DELETE` | `/devices/{id}` | Ya | Hapus device |
| `GET` | `/devices/{id}/status` | Ya | Status check satu kali |
| `POST` | `/devices/{id}/wake` | Ya | Kirim magic packet |

Endpoint protected memakai:

```http
Authorization: Bearer <api-token>
```

Health check:

```powershell
curl.exe -i http://127.0.0.1:8080/api/v1/health
```

Daftar device:

```powershell
$token = '<copy-token-from-Settings>'
curl.exe -i http://127.0.0.1:8080/api/v1/devices `
  -H "Authorization: Bearer $token"
```

Tambah device:

```powershell
$body = '{"name":"Office PC","ipAddress":"192.168.1.20","macAddress":"AA:BB:CC:DD:EE:FF","broadcastAddress":"255.255.255.255","wolPort":9,"statusCheckMethod":"ICMP_AND_TCP","statusCheckPort":3389}'
curl.exe -i -X POST http://127.0.0.1:8080/api/v1/devices `
  -H "Authorization: Bearer $token" `
  -H 'Content-Type: application/json' `
  -d $body
```

Error memakai envelope konsisten:

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

Kode error umum: `unauthorized`, `invalid_json`, `validation_failed`, `duplicate_device`, `device_not_found`, `discovery_timeout`, `request_too_large`, `bad_request`, dan `internal_error`.

Dokumentasi endpoint lengkap tersedia di [`docs/API_ENDPOINTS.md`](docs/API_ENDPOINTS.md) dan [`docs/API.md`](docs/API.md).

## API token

- Token dibuat otomatis saat konfigurasi API pertama kali.
- Token disimpan dalam secure storage berbasis Android Keystore.
- Token dapat diganti dengan custom token atau automatic refresh dari Settings.
- Token ditampilkan secara masked.
- Tidak ada endpoint token-management.
- Token tidak boleh masuk log, notification, diagnostics, response error, report, atau screenshot.

## Cloudflare Tunnel

Cloudflare Tunnel membuat API Android dapat diakses dari luar jaringan lokal tanpa port forwarding atau inbound firewall rule. Koneksi dibuat outbound dari Android ke Cloudflare Edge; client eksternal mengakses hostname HTTPS Cloudflare.

```text
External Client
      │ HTTPS
      ▼
Cloudflare Edge
      │ encrypted tunnel
      ▼
cloudflared di Android
      │ loopback
      ▼
Ktor API: 127.0.0.1:<configured-api-port>
```

Tunnel hanya meneruskan request. Tunnel bukan autentikasi; semua endpoint selain `/api/v1/health` tetap memerlukan:

```http
Authorization: Bearer <api-token>
```

Origin tunnel selalu mengikuti port API pada aplikasi:

```text
127.0.0.1:<configured-api-port>
```

Origin tidak dapat diubah terpisah dari halaman Tunnel. API service harus aktif agar tunnel dapat berjalan. Tunnel autostart juga membutuhkan API service dan tunnel token yang tersimpan.

### Prasyarat

- Akun Cloudflare.
- Domain aktif pada Cloudflare.
- Subdomain yang akan dipakai, misalnya `wol.example.com`.
- Android tetap terhubung ke LAN target.
- API service WoL Gateway sudah diuji secara lokal.

### Membuat tunnel

Gunakan **remotely-managed tunnel** melalui Cloudflare Dashboard. Model ini cocok untuk WoL Gateway karena aplikasi Android menjalankan `cloudflared` secara embedded dan menerima tunnel token; pengguna tidak perlu menjalankan proses `cloudflared` terpisah pada PC.

1. Buka **Cloudflare Dashboard**.
2. Pilih akun yang memiliki domain.
3. Buka **Zero Trust → Networks → Connectors → Tunnels**.
4. Pilih **Create a tunnel**.
5. Pilih connector **Cloudflared**.
6. Isi nama tunnel, misalnya `wol-gateway-home`.
7. Buat tunnel.
8. Salin **tunnel token** dari halaman connector.
9. Jangan commit, log, screenshot, atau membagikan token. Token memberi izin connector untuk menjalankan tunnel.

Di aplikasi Android:

1. Buka **Settings → Tunnel**.
2. Masukkan tunnel token.
3. Simpan token.
4. Aktifkan switch **Connection**.
5. Pastikan status berubah menjadi `CONNECTED`.

Token tunnel disimpan terpisah dari API token menggunakan Android Keystore-backed storage. Saat `CONNECTING`, `CONNECTED`, atau `RECONNECTING`, token tidak dapat diedit.

### Mengatur public hostname dan route

Route menentukan hostname publik mana yang diteruskan ke API lokal Android.

1. Pada tunnel yang dibuat, buka tab **Routes** atau **Published application**.
2. Pilih **Add route → Published application**.
3. Isi **Subdomain**, misalnya `wol`.
4. Pilih domain, misalnya `example.com`.
5. Pastikan hostname menjadi `wol.example.com`.
6. Isi **Service URL** sesuai port API pada aplikasi:

   ```text
   http://127.0.0.1:8080
   ```

   Ganti `8080` bila port API di Settings memakai port lain.

7. Simpan route.
8. Pastikan DNS record/CNAME untuk hostname dibuat atau terhubung otomatis ke tunnel oleh Cloudflare.
9. Pastikan connector tunnel menunjukkan status **Healthy** atau **Connected**.

Route tidak perlu menambahkan `/api/v1`; gunakan origin root `http://127.0.0.1:<port>`. Path request diteruskan apa adanya, sehingga client memakai:

```text
https://wol.example.com/api/v1/health
https://wol.example.com/api/v1/devices
https://wol.example.com/api/v1/devices/1/wake
```

Untuk API WoL Gateway, route publik yang disarankan adalah satu hostname ke satu origin API. Jika memakai path route, path tersebut tidak otomatis dihapus atau di-rewrite sebelum diterima origin; konfigurasi rewrite tambahan dapat membuat URL API tidak sesuai.

### Contoh konfigurasi route Cloudflare

Konsep route remotely-managed equivalent:

```yaml
tunnel: <tunnel-id>

ingress:
  - hostname: wol.example.com
    service: http://127.0.0.1:8080
  - service: http_status:404
```

Rule catch-all `http_status:404` harus berada paling akhir. Pada integrasi Android ini, konfigurasi ingress dikelola oleh runtime tunnel dan Cloudflare Dashboard; contoh YAML hanya menjelaskan mapping hostname ke origin, bukan file yang harus disimpan di repository.

### Menguji akses dari luar LAN

Lakukan pengujian dari jaringan seluler atau Wi-Fi lain, bukan jaringan yang sama dengan Android:

```powershell
curl.exe -i https://wol.example.com/api/v1/health
```

Expected:

```json
{"status":"ok"}
```

Uji endpoint protected memakai API token:

```powershell
$token = '<copy-token-from-Settings>'
curl.exe -i https://wol.example.com/api/v1/devices `
  -H "Authorization: Bearer $token"
```

Tanpa token atau dengan token salah, response harus `401`. Health endpoint yang berhasil tidak membuktikan device WoL online; endpoint tersebut hanya membuktikan API menerima request.

### Aturan operasional

- Nyalakan **API service** sebelum menghubungkan tunnel.
- Port pada route Cloudflare harus sama dengan port API Settings.
- Jangan mengisi origin dengan IP LAN Android; gunakan `127.0.0.1:<port>`.
- Jangan membuka port router ke internet hanya untuk Tunnel.
- Jangan memakai URL tunnel sebagai pengganti API token.
- Gunakan HTTPS hostname Cloudflare untuk client eksternal.
- Tunnel harus berstatus `CONNECTED` sebelum request eksternal diuji.
- Mematikan API akan memutus tunnel; mematikan tunnel tidak mematikan API lokal.
- Auto-reconnect hanya berjalan ketika connection aktif dan token tersimpan.

Tunnel mendukung state berikut:

`NOT_CONFIGURED`, `DISCONNECTED`, `CONNECTING`, `CONNECTED`, `RECONNECTING`, `ERROR`.

Runtime tunnel ditargetkan untuk ARM64 dan menggunakan binary yang dibangun dari source resmi Cloudflare. Detail provenance, checksum, lifecycle, dan validasi ada di [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) serta [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md).

### Troubleshooting tunnel

| Gejala | Pemeriksaan |
|---|---|
| `NOT_CONFIGURED` | Simpan tunnel token pada Settings. |
| Switch kembali `OFF` | Token invalid, koneksi gagal, atau origin API belum aktif. Baca safe error pada halaman Tunnel. |
| `502 Bad Gateway` | Pastikan API aktif, port route sama dengan port API, dan origin memakai `http://127.0.0.1:<port>`. |
| Hostname tidak resolve | Pastikan public hostname route dan DNS record dibuat pada tunnel yang benar. |
| Tunnel `CONNECTED`, API `401` | Tunnel sehat; gunakan API token WoL Gateway yang benar. |
| `/health` berhasil, device gagal | Periksa device ID, status target, MAC, broadcast address, firewall, dan jaringan LAN Android. |
| Tunnel sering reconnect | Periksa koneksi Android, battery optimization, network recovery, dan kebijakan OEM. |

## Standard Mode dan Root Mode

### Standard Mode

Mode default tanpa root. Android foreground service menjaga API dan tunnel tetap berjalan sesuai batasan OS/OEM, battery policy, notification permission, dan background-start restriction.

### Root Mode

Mode opsional dengan explicit consent. Root supervisor hanya mengelola lifecycle gateway melalui operasi allowlisted, readiness check, restart terbatas, dan fallback ke Standard Mode.

Root Mode tidak:

- Menjalankan arbitrary shell command.
- Menjalankan Ktor, Room, Keystore, atau credential tunnel sebagai UID 0.
- Menjamin aplikasi selalu hidup pada setiap OEM.
- Menghilangkan batasan jaringan atau ARP discovery.

## Keamanan

- API bind loopback-only pada `127.0.0.1`.
- Semua route sensitif membutuhkan Bearer token.
- Perbandingan token constant-time.
- Request body dibatasi 64 KiB.
- Input device divalidasi pada trust boundary.
- Tidak ada shell execution atau arbitrary process execution melalui API.
- Secret dipisahkan dari DataStore biasa dan disimpan pada Keystore-backed storage.
- Diagnostics, log, notification, dan error memakai data yang sudah di-redact.
- Root membutuhkan capability serta consent eksplisit.

## Batasan penting

- WoL bergantung pada konfigurasi broadcast, router, firewall, NIC, dan power state target.
- MAC address tidak selalu dapat ditemukan melalui discovery; input manual tetap tersedia.
- ICMP atau TCP yang gagal tidak otomatis berarti target `OFFLINE`; hasil ambigu menjadi `UNKNOWN`.
- TCP status check default Windows memakai port `3389`; port dapat diubah per device.
- Background recovery bersifat best-effort dan dipengaruhi Android/OEM.
- Data thermal bersifat diagnostik; bukan bukti target WoL online dan bukan health check API/tunnel.
- Tunnel publik tetap memerlukan autentikasi API.

## Arsitektur

Proyek memakai satu Android module dengan pembagian layer:

- **Presentation**: Jetpack Compose, ViewModel, navigation, shared components.
- **Domain**: model, validation, use case, repository/service contract.
- **Data**: Room, DataStore, repository, secure storage.
- **Gateway**: Ktor server, routing, DTO, auth, validation, error mapping.
- **Network/WoL**: discovery, status checker, magic packet, UDP sender.
- **Tunnel**: controller, state machine, retry, runtime adapter.
- **Android service**: foreground service, boot/network recovery, lifecycle coordinator.
- **Root**: capability detector, consent, supervisor, safe fallback.

Package utama: `com.yihs.wolgateway`.

## Dokumentasi

- [`docs/PRD.md`](docs/PRD.md) — product requirements, scope, security, acceptance criteria.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — deployment, layer, lifecycle, storage, security boundary.
- [`docs/API.md`](docs/API.md) — ringkasan API dan manual verification.
- [`docs/API_ENDPOINTS.md`](docs/API_ENDPOINTS.md) — referensi endpoint dan schema lengkap.
- [`docs/DESIGN.md`](docs/DESIGN.md) — UI/UX, design tokens, navigation, accessibility.
- [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md) — checkpoint, status implementasi, dan validasi.

## Status

Fitur utama Android UI, device management, REST API, tunnel ARM64, background service, Root Mode, dan diagnostics telah didokumentasikan sebagai implemented/validated sesuai scope masing-masing. Validasi real-device tetap bergantung pada perangkat, jaringan, konfigurasi OEM, dan target WoL yang digunakan.
