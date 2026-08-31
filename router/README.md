# Backend router: kategori perangkat melalui Netify

Implementasi endpoint `named_devices` berada pada paket LuCI yang terpasang di
router, bukan di modul Android ini:

`/usr/lib/lua/luci/controller/remotbot.lua`

Endpoint tersebut sekarang membaca cache flow lokal dari Netify. Netify menganalisis
paket langsung pada interface LAN/WAN dan collector menyimpan IP perangkat, aplikasi,
serta hostname selama 15 menit. Endpoint lalu mengirim kategori yang sama dengan model
Android `NamedDeviceInfo`:

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

## Kombinasi pemantauan native OpenWrt

Pasang dan aktifkan tiga layanan berikut:

```sh
opkg update
opkg install netifyd wrtbwmon nlbwmon luci-app-nlbwmon socat
uci set netifyd.@netifyd[0].enabled='1'
uci set netifyd.@netifyd[0].autoconfig='0'
uci add_list netifyd.@netifyd[0].internal_if='br-lan'
uci add_list netifyd.@netifyd[0].external_if='eth1'
uci set nlbwmon.@nlbwmon[0].refresh_interval='15s'
uci set nlbwmon.@nlbwmon[0].commit_interval='1h'
uci commit netifyd
uci commit nlbwmon
/etc/init.d/netifyd enable && /etc/init.d/netifyd restart
/etc/init.d/wrtbwmon enable && /etc/init.d/wrtbwmon restart
/etc/init.d/nlbwmon enable && /etc/init.d/nlbwmon restart
```

Salin `openwrt-live-traffic/remotbot-netify-collector` ke
`/usr/sbin/` dan `openwrt-live-traffic/remotbot-netify-collector.init` ke
`/etc/init.d/remotbot-netify-collector`, lalu:

```sh
chmod 755 /usr/sbin/remotbot-netify-collector /etc/init.d/remotbot-netify-collector
/etc/init.d/remotbot-netify-collector enable
/etc/init.d/remotbot-netify-collector start
/etc/init.d/uhttpd restart
```

Ganti `br-lan` dan `eth1` sesuai router. Collector memakai socket UNIX Netify yang
hanya bisa diakses lokal, sehingga tidak ada kredensial, port HTTP, atau layanan Docker
yang perlu dibuka. `wrtbwmon` menulis ringkasan live ke `/tmp/usage.htm`; `nlbwmon`
menyimpan basis data riwayat di `/var/lib/nlbwmon/`.

## Endpoint disk read-only

Tab **Memori** pada Android membaca endpoint `remotbot/disks`. Endpoint tersebut
memakai `luci.model.diskman` yang sama dengan halaman DiskMan untuk menampilkan
path, model, serial number, size, temperature, partition table, SATA/NVMe
version, serta health/status dan partisi. Tidak ada endpoint format, mount,
unmount, atau aksi disk destruktif yang diekspos ke aplikasi.
