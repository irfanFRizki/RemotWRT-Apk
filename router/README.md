# Backend router: kategori perangkat melalui AdGuard Home

Implementasi endpoint `named_devices` berada pada paket LuCI yang terpasang di
router, bukan di modul Android ini:

`/usr/lib/lua/luci/controller/remotbot.lua`

Endpoint tersebut sekarang mengambil `/control/querylog` dari AdGuard Home,
mengelompokkan `question.host` berdasarkan `client` IPv4, lalu mengirim kategori
yang sama dengan model Android `NamedDeviceInfo`:

```json
{
  "ip": "192.168.1.24",
  "categories": {
    "social": [{"name": "WhatsApp", "count": 12}],
    "game": [{"name": "Mobile Legends", "count": 5}]
  }
}
```

Perubahan backend telah diterapkan pada router ini. Saat sumber paket
`luci-app-remotbot` dipisahkan ke repository sendiri, pindahkan perubahan dari
controller tersebut ke paket agar tetap bertahan saat upgrade paket OpenWrt.

Konfigurasi opsional untuk jumlah record DNS yang dipindai:

```sh
uci set remotbot.main.adguard_category_limit='500'
uci commit remotbot
/etc/init.d/uhttpd restart
```

Nilai dibatasi ke 50–1000 dan default-nya 500. Perangkat yang memakai Private
DNS, DoH/DoT, VPN DNS, atau DNS selain AdGuard Home tidak dapat dikategorikan.

## Endpoint disk read-only

Tab **Memori** pada Android membaca endpoint `remotbot/disks`. Endpoint tersebut
memakai `luci.model.diskman` yang sama dengan halaman DiskMan untuk menampilkan
path, model, serial number, size, temperature, partition table, SATA/NVMe
version, serta health/status dan partisi. Tidak ada endpoint format, mount,
unmount, atau aksi disk destruktif yang diekspos ke aplikasi.
