# RemotWRT Bot — App Android

App pendamping native untuk **RemotWRT-Bot** (`luci-app-remotbot` + `remotbot`) yang sudah
terpasang di Raspberry Pi 4B OpenWrt Anda. Diakses lewat domain `.my.id` yang di-tunnel
via cloudflared, jadi tidak perlu buka port apa pun di router.

## Fitur

- **Dashboard native** — menampilkan status bot, suhu CPU, RAM, disk, load average,
  uptime, status WAN, dan jumlah device (online/whitelist/pending/blocked), diambil
  langsung dari endpoint JSON `admin/services/remotbot/status` yang sudah ada di
  `luci-app-remotbot`. Auto-refresh tiap 10 detik saat dashboard dibuka.
- **Tombol Pengaturan** — membuka halaman LuCI asli (`admin/services/remotbot/settings`)
  di WebView, memakai ulang sesi login supaya tidak perlu login dua kali.
- **Notifikasi push** — job latar belakang (WorkManager, tiap 15 menit — batas minimum
  Android untuk periodic work) yang mengirim notifikasi saat:
  - WAN terputus
  - Bot berhenti berjalan padahal `enabled`
  - Ada device baru yang pending approval
  - Suhu CPU melewati `cpu_temp_threshold`
  - Pemakaian RAM melewati `ram_threshold`

  Notifikasi hanya dikirim saat terjadi *perubahan status* (bukan tiap 15 menit selama
  kondisi bertahan), supaya tidak spam.
- **Kredensial tersimpan aman** — URL server, username, password LuCI, dan cookie sesi
  disimpan di `EncryptedSharedPreferences` (dienkripsi AES256, per-device keystore),
  bukan plain text.

## Cara kerja login

App melakukan flow login LuCI standar: POST `luci_username` + `luci_password` ke
`{base_url}/cgi-bin/luci/`, lalu memakai cookie sesi (`Set-Cookie`) yang dikembalikan
untuk semua request berikutnya ke endpoint status. Kalau sesi kedaluwarsa, app otomatis
login ulang pakai kredensial yang tersimpan.

> **Kalau path login LuCI Anda berbeda** (misal tema/versi OpenWrt yang berbeda memakai
> path lain), ubah `loginUrl()` di
> `app/src/main/kotlin/com/remotwrt/bot/data/LuciClient.kt`.

## Build APK via GitHub Actions (tanpa Android Studio)

1. Buat repo baru di GitHub, push seluruh folder project ini ke branch `main`.
2. Buka tab **Actions** — workflow `Build APK` akan otomatis jalan setiap push
   (atau klik **Run workflow** untuk trigger manual).
3. Setelah selesai (~5 menit), buka hasil run tersebut → bagian **Artifacts** →
   download `RemotWRTBot-debug-apk`.
4. Extract zip-nya, dapatkan `app-debug.apk`, kirim ke HP (lewat Google Drive/Telegram/
   kabel), lalu install (aktifkan dulu "Install from unknown sources" di Android).

APK debug ini **sudah bisa langsung dipakai** untuk penggunaan pribadi — ditandatangani
dengan debug keystore otomatis dari Android Gradle Plugin, tidak perlu setup signing
key terpisah. Kalau suatu saat ingin publish ke Play Store, baru perlu keystore rilis
sendiri (di luar cakupan ini).

## Build lokal via Android Studio (alternatif)

1. Buka folder ini di Android Studio (Koala+ direkomendasikan, atau versi apa pun yang
   mendukung AGP 8.5).
2. Biarkan Gradle sync (butuh koneksi internet untuk resolve dependency pertama kali).
3. Run ke device/emulator, atau **Build > Build APK(s)**.

## Struktur project

```
app/src/main/kotlin/com/remotwrt/bot/
├── MainActivity.kt          # Compose UI: setup screen + dashboard
├── WebViewActivity.kt       # Buka halaman settings LuCI, reuse cookie sesi
├── data/
│   ├── Prefs.kt             # EncryptedSharedPreferences (kredensial, cookie, state)
│   ├── LuciClient.kt        # Login LuCI + fetch status JSON
│   └── RemotbotStatus.kt    # Model data status
└── work/
    ├── MonitorWorker.kt     # Cek berkala + kirim notifikasi saat transisi status
    ├── MonitorScheduler.kt  # Jadwalkan/batalkan WorkManager periodic job
    └── NotificationHelper.kt
```

## Catatan keamanan

- Semua komunikasi lewat HTTPS ke domain `.my.id` Anda sendiri (via cloudflared),
  jadi tidak ada trafik plaintext ke internet.
- Password hanya dikirim sekali saat login awal / re-auth; setelah itu app memakai
  cookie sesi untuk request berikutnya, sama seperti browser biasa.
- `android:usesCleartextTraffic="false"` dipasang di manifest — app akan menolak
  request HTTP biasa (non-HTTPS), sesuai dengan setup cloudflared Anda.

## Pengembangan lanjutan (opsional)

- Kalau nanti ingin polling lebih cepat dari 15 menit, alternatifnya adalah foreground
  service ringan, tapi ini menambah kompleksitas (dan makan baterai) — untuk kasus
  monitoring router, 15 menit biasanya cukup.
- Endpoint status saat ini read-only. Kalau mau tambah aksi dari HP (approve/block
  device langsung dari notifikasi), perlu tambah endpoint baru di
  `luci-app-remotbot` (controller Lua) yang menerima POST, lalu app tinggal panggil.
