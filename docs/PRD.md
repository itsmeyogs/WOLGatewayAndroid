# Product Requirements Document — WoL Gateway

**Status:** Draft for approval — Checkpoint 3 device UI implemented
**Tanggal:** 2026-10-26
**Package:** `com.yihs.wolgateway`

## 1. Ringkasan

WoL Gateway menjadikan perangkat Android sebagai gateway jaringan lokal untuk mengirimkan Wake-on-LAN magic packet kepada PC atau perangkat lain. Aplikasi menyediakan UI lokal, Ktor REST API terautentikasi, optional Cloudflare Tunnel, background service, dan optional Root Mode.

Nilai utama: perangkat eksternal dapat membangunkan target LAN tanpa VPS, selama Android tetap terhubung ke jaringan yang dapat mengirim packet dan service masih diizinkan sistem berjalan.

## 2. Tujuan

- Mendaftarkan dan mengelola perangkat WoL secara aman.
- Mengirim magic packet dengan hasil lokal yang jelas.
- Menyediakan API `/api/v1` untuk integrasi eksternal.
- Menjalankan gateway tanpa root sebagai mode utama.
- Menyediakan tunnel eksternal opsional dengan status aktual.
- Menjelaskan keterbatasan Android, jaringan, OEM, dan WoL tanpa klaim berlebihan.

## 3. Non-goals

- Menjamin aplikasi selalu hidup pada semua perangkat/OEM.
- Menentukan PC online hanya dari kegagalan ICMP.
- Menyediakan shell atau arbitrary command execution.
- Menjadikan Root Mode sebagai dependency.
- Menyimpan atau menampilkan secret dalam log, report, screenshot, atau diagnostics.
- Menjadikan URL tunnel sebagai autentikasi.

## 4. Personas dan use cases

### Operator rumah/lab

Mendaftarkan PC, menguji WoL, melihat status gateway, lalu mengaktifkan service.

### Integrator/API client

Memanggil API terautentikasi dari luar jaringan melalui tunnel atau jaringan lokal.

### Troubleshooter

Membedakan masalah target PC, Android gateway, Ktor API, tunnel, permission, dan jaringan.

## 5. Functional requirements

### FR-01 Device management

Devices menampilkan nama, IP, MAC, status `ONLINE`/`OFFLINE`/`UNKNOWN`, waktu pemeriksaan, dan hasil WoL terakhir. Mendukung search nama/IP/MAC, refresh, add, edit, detail, wake, status check, delete dengan konfirmasi, loading, empty, error, serta success feedback. Settings diakses dari action app bar Devices; Add Devices diakses dari FAB atau empty-state add action.

`ONLINE` berarti pemeriksaan yang dikonfigurasi memperoleh bukti konektivitas. `OFFLINE` hanya digunakan jika metode pemeriksaan memperoleh bukti kuat target tidak tersedia. `UNKNOWN` digunakan jika bukti tidak memadai, termasuk ICMP yang gagal atau diblokir.

### FR-02 Discovery dan registration

Add Devices pada kunjungan pertama memilih interface Wi-Fi/Ethernet IPv4 yang aktif, memindai host yang reachable dengan timeout, menyimpan hostname best-effort sebagai metadata internal, menampilkan IP dan common TCP ports, serta mendeduplikasi hasil. Saved device dengan IP yang sama tidak ditampilkan lagi pada hasil discovery. Hasil discovery dipertahankan saat pengguna membuka Add device lalu kembali; scan tidak diulang kecuali pull-to-refresh atau retry. MAC tidak diasumsikan tersedia dan ditampilkan sebagai unavailable sebelum registrasi. Tap result membuka registrasi dengan IP terisi. Manual registration tetap boleh memakai IP yang sama selama MAC berbeda. Save menampilkan snackbar sukses atau gagal; field invalid juga menampilkan inline validation.

### FR-03 WoL engine

Membuat magic packet dari MAC tervalidasi, mengirimkannya melalui UDP broadcast/socket asynchronous, membersihkan resource, dan mencatat diagnostics tanpa secret. `PACKET_SENT` hanya berarti pengiriman lokal tidak menghasilkan error; bukan bukti target menyala.

### FR-04 REST API

Endpoint minimum:

| Method | Path | Auth | Fungsi |
|---|---|---:|---|
| GET | `/api/v1/health` | Tidak wajib bila tidak membocorkan detail | Health dasar |
| GET | `/api/v1/devices` | Ya | Daftar perangkat |
| GET | `/api/v1/devices/{id}` | Ya | Detail |
| POST | `/api/v1/devices` | Ya | Tambah |
| PUT | `/api/v1/devices/{id}` | Ya | Ubah |
| DELETE | `/api/v1/devices/{id}` | Ya | Hapus |
| GET | `/api/v1/devices/{id}/status` | Ya | Check status |
| POST | `/api/v1/devices/{id}/wake` | Ya | Kirim WoL |

API memakai JSON, DTO validation, HTTP status code sesuai, error envelope konsisten, timeout, request size limit, repository yang sama dengan UI, dan tanpa operasi blocking di handler.

CP6 implementation exposes the routes above under `/api/v1`. `/api/v1/health` returns minimal public health; all device routes require a Bearer token. Manual verification must cover success and failure status codes for each endpoint, automatic/custom token handling, automatic refresh, and server start/stop from Settings.

### FR-05 Tunnel

Tunnel mendukung token, connect, disconnect, reconnect, auto-connect sesuai konfigurasi, health monitoring, last-connected time, error aman, token replacement, duplicate prevention, cleanup, dan status:

`NOT_CONFIGURED`, `DISCONNECTED`, `CONNECTING`, `CONNECTED`, `RECONNECTING`, `ERROR`.

Cloudflare domain/public hostname ditunda sampai gateway lokal stabil. CP7 memvalidasi runtime dan forwarding lokal terlebih dahulu, lalu public hostname. [`roufsweb/cloudflared-android`](https://github.com/roufsweb/cloudflared-android) digunakan sebagai referensi teknis saja. Official Cloudflare/cloudflared `2026.10.0` commit `18cdfe0a6fc7b72a0702d255a1f984e776ce0498` dibangun untuk ARM64 Android dengan Go `1.26.0`, `GOOS=android`, `GOARCH=arm64`, `CGO_ENABLED=0`; artifact SHA-256 `C1D7F45057016C6771655316A5AA114FF4419D06FA194580544004E4EE199E6D`. Runtime sudah dibundle, di-wire, dan divalidasi pada perangkat ARM64 Android 10 non-root untuk native execution, tunnel registration, local forwarding, serta lifecycle connect/disconnect.

### FR-06 Secret dan retry

Tunnel token dibuat, diganti, dicabut, dan disimpan dengan Android Keystore-backed storage. API token dibuat otomatis, dapat diganti dengan custom token atau automatic refresh, lalu disimpan dengan Android Keystore-backed storage. DataStore hanya untuk preference non-secret. Retry memakai exponential backoff, cap, jitter, cancellation, dan satu supervisor aktif.

### FR-07 Gateway lifecycle

Pengguna dapat mengatur port, token, start/stop server, dan melihat status API melalui Settings. Bind address selalu `127.0.0.1` dan tidak dapat diedit; port default `8080` serta hanya rentang `1024..65535` yang valid. Server stopped sampai diaktifkan. Toggle start/stop tidak menampilkan snackbar sukses; snackbar dipakai untuk port/configuration atau server bind error. Kegagalan bind tidak boleh menghentikan process Android. Foreground-service hosting tetap CP9.

### FR-08 Background

Foreground service dengan notification persisten digunakan untuk operasi koneksi persisten. WorkManager hanya untuk pekerjaan tertunda/recovery yang sesuai. Boot recovery dan network recovery bersifat best-effort serta mengikuti pembatasan OS/OEM.

### FR-09 Root Mode

Root Mode optional dan menjadi checkpoint terakhir setelah Standard Mode stabil. Fitur mencakup capability detection, consent, advanced startup/supervision/diagnostics jika layak, serta fallback Standard Mode. Tidak ada shell arbitrer atau input command tak tervalidasi.

### FR-10 Status monitoring

Metode dapat dikombinasikan: ICMP, TCP port, dan service check. Polling dibatasi. `ONLINE` membutuhkan bukti positif; `UNKNOWN` dipakai bila hasil ambigu; `OFFLINE` hanya dipakai bila metode relevan memberi bukti kuat. Status gateway, tunnel, API, dan target dipisahkan.

### FR-12 API authentication token

API menyediakan token random yang dibuat aplikasi sebagai default. Settings memiliki halaman API terpisah yang mendukung automatic/custom token, masked copy, custom edit, dan automatic refresh. Token disimpan pada Keystore-backed secure storage.

### FR-11 Settings dan diagnostics

Settings mencakup API, port/bind, token management, tunnel, WoL port/broadcast, interval status, Standard/Root Mode, service, root, versi, dan diagnostics yang tidak membocorkan secret.

## 6. Security requirements

- Semua operasi administratif/API sensitif memerlukan token.
- Token dibandingkan dengan mekanisme constant-time.
- Token tidak masuk log, exception message, notification, URI, report, atau command argument bila runtime memungkinkan alternatif.
- Tidak ada endpoint shell/arbitrary process execution.
- Bind address selalu loopback `127.0.0.1`; port privileged di bawah `1024` ditolak.
- Request validation dan batas ukuran diterapkan di trust boundary.
- Native binary/vendor dependency dipin dan diverifikasi sebelum dipakai. CP7 runtime provenance, checksum, and license are recorded in `reports/checkpoints/CP7-tunnel.md`; artifact is not accepted as usable until device validation passes.
- Root tidak diminta tanpa consent.

## 7. Non-functional requirements

- Kotlin, Android Native, Compose Material 3, MVVM, Coroutines/Flow, Hilt dependency injection.
- Min SDK ditetapkan `29` (Android 10), sesuai device ARM64 non-root yang tersedia dan baseline runtime tunnel yang akan divalidasi.
- Build reproducible dan dependency versions dipin melalui version catalog.
- Dependency injection memakai Hilt; Application, Activity, ViewModel, database, preferences, dan repository bindings mengikuti Hilt component scope.
- Struktur kode memisahkan setiap top-level class/component ke file dan package sesuai tanggung jawab; Activity tidak memuat layout screen. UI pages berada di `ui/screen/devices`, `ui/screen/add_devices`, `ui/screen/device/detail`, `ui/screen/device/form`, dan `ui/screen/settings`; reusable components berada di `ui/component`.
- UI accessible, scrollable, font scaling, light/dark theme. Device Details, Settings, and add/edit forms must remain usable when content exceeds the viewport. User-facing copy is localized through Android string resources.
- Resource cleanup saat stop, cancellation, process exit, network change, dan app shutdown.
- Diagnostics membedakan automatic test, emulator, dan real device.

## 8. Metrics of completion

- CRUD device lulus unit/integration/UI tests.
- Magic packet lulus byte-level test dan device test.
- API auth/validation/error contract lulus integration tests.
- Tunnel hanya dinyatakan usable setelah CP7 end-to-end PASS.
- Background/root compatibility hanya dinyatakan berdasarkan perangkat yang benar-benar diuji.

## 9. Resolved decisions

- **Target ABI:** ARM64 (`arm64-v8a`) saja. ABI lain bukan scope.
- **Tunnel rollout:** stabilkan gateway lokal dahulu; uji public hostname/domain setelahnya.
- **Tunnel reference:** `cloudflared-android` hanya referensi. Bila diperlukan, build binary sendiri dari source Cloudflare/cloudflared yang dipin.
- **Status checks:** kombinasikan ICMP, TCP, dan service check; hasil harus menyimpan method, waktu, dan alasan.
- **API bind:** permanently loopback-only pada `127.0.0.1`; hanya port `1024..65535` yang configurable.
- **API token:** random generated default + custom token optional; masked copy and automatic refresh. Revoke tidak diperlukan.
- **Root Mode:** ditunda sampai Standard Mode stabil; implementasi terakhir.

## 10. Resolved validation prerequisites

- **Test device:** Standard Mode pada non-rooted Android 10 ARM64; Root Mode pada rooted Android 13 ARM64.
- **Fixture lokal:** Android ARM64 + Ktor + self-built official Cloudflare runtime; uji forwarding, health check, dan authentication sebelum domain eksternal.
- **Fixture eksternal:** subdomain pengujian, tunnel aktif, API health/authentication lulus dari jaringan berbeda.
- **Status default:** ICMP aktif + TCP aktif pada port `3389` (Windows RDP); port tetap configurable per device.
- **Polling:** screen Devices aktif auto-check tiap 60 detik; manual check selalu tersedia; background service tidak polling target; post-Wake checks pada 5/15/30 detik.
- **Runtime provenance:** `cloudflared-android` reference only; production runtime hanya self-built dari official Cloudflare source.
