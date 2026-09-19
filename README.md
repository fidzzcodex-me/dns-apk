# DNS Switch

Aplikasi Android (bukan web) untuk ganti DNS perangkat lewat VPN lokal tanpa
root — mirip "1.1.1.1 by Cloudflare" tapi bisa pilih beberapa provider
sekaligus: Cloudflare, Google Public DNS, AdGuard, Quad9, OpenDNS, dan
CleanBrowsing.

## Cara kerja

Android tidak mengizinkan aplikasi biasa mengganti DNS sistem secara
langsung tanpa root. Trik yang dipakai aplikasi seperti ini (dan yang
dipakai di sini) adalah `android.net.VpnService`: aplikasi membuat
antarmuka VPN lokal yang hanya meneruskan trafik DNS (UDP port 53) ke
server DNS yang dipilih, lalu menuliskan balasannya kembali. Trafik lain
tidak disentuh sama sekali — jadi ini bukan VPN penyembunyi IP, murni
DNS switcher.

Begitu satu provider dipilih dan disambungkan, warna aksen seluruh UI
otomatis berubah mengikuti warna khas provider itu (oranye untuk
Cloudflare, biru Google, hijau AdGuard, dst) dengan transisi warna yang
smooth, bukan langsung berubah.

## Kenapa bukan web (AOS dari permintaan awal)

AOS (Animate On Scroll) adalah library JavaScript untuk web, tidak bisa
dipakai di aplikasi Android native. Efek animasi masuk yang sama (fade +
slide bertahap per item) di sini dibuat native pakai Jetpack Compose
(`AnimatedVisibility` + delay bertingkat), jadi hasilnya setara secara
visual meski librarynya beda.

## Build via GitHub Actions

Setiap push ke branch `main` otomatis build APK debug lewat
`.github/workflows/build.yml`. Hasil APK bisa diunduh dari tab **Actions**
pada repo, di bagian **Artifacts** hasil workflow run.

Workflow ini juga yang men-generate `gradle-wrapper.jar` (lewat perintah
`gradle wrapper`), karena file jar wrapper itu binary dan sengaja tidak
disertakan di repo ini.

## Build/buka secara lokal

Buka folder ini di Android Studio (Hedgehog atau lebih baru) — Android
Studio otomatis mengunduh Gradle wrapper yang benar saat pertama kali
membuka project. Kalau ingin lewat command line tanpa Android Studio:

```bash
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

APK hasil build ada di `app/build/outputs/apk/debug/app-debug.apk`.

## Struktur

```
app/src/main/java/com/fidzz/dnsswitch/
├── MainActivity.kt          UI utama (Compose) + alur izin VPN
├── DnsSwitchApp.kt          Application class
├── data/DnsProvider.kt      Daftar provider DNS + warna tema masing-masing
├── ui/theme/                Warna & tipografi, tema yang beradaptasi ke accent provider
└── vpn/DnsVpnService.kt     VpnService asli: relay paket DNS (UDP/53) ke provider terpilih
```

## Menambah provider DNS baru

Tinggal tambah satu entri baru di `DnsProviders.all` (`data/DnsProvider.kt`)
dengan IP primer/sekunder, warna aksen, dan ikon Material — otomatis muncul
di daftar dan otomatis dapat tema warnanya sendiri.
