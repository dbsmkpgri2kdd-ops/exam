# PRIDA EXAMBRO 2026 — Panduan Rilis Aplikasi Native Android & AdMob

Project Native Android (Kotlin) ini telah dikonfigurasi siap rilis dengan proteksi ujian Kiosk (`FLAG_SECURE`, `startLockTask`, deteksi split-screen, WebRTC Camera Bridge, Google Account Cookie Sync) serta integrasi lengkap **6 Format Iklan Google AdMob**.

## 1. Konfigurasi AdMob yang Sudah Tertanam

- **App ID (`AndroidManifest.xml`)**: `ca-app-pub-3979615977174432~1153915437`
- **Banner Adaptif**: `ca-app-pub-3979615977174432/1997378769` (Tampil di Login & Dashboard Siswa, otomatis hilang saat ujian dimulai)
- **Interstisial**: `ca-app-pub-3979615977174432/1125995275` (Tampil setelah ujian selesai)
- **Pengantara Berbonus (Rewarded Interstitial)**: `ca-app-pub-3979615977174432/2596144566` (Mode Simulasi/Kisi-kisi)
- **Iklan Reward**: `ca-app-pub-3979615977174432/1466895725` (Buka Latihan Bank Soal AI Siswa)
- **Native Advanced**: `ca-app-pub-3979615977174432/4572230359` (Kartu Native di Dashboard Siswa, otomatis hilang saat ujian)
- **Pembukaan Aplikasi (App Open)**: `ca-app-pub-3979615977174432/9058270279` (Tampil saat buka aplikasi, nonaktif saat ujian)
- **Perangkat Pengujian Iklan (GAID)**: `d9fb552c-cf63-4133-a677-8130f7262b14`

> **Kebijakan Keamanan Ujian & AdMob**: `AdMobManager` dan `ExambroApplication` memblokir seluruh iklan selama `isExamLocked == true` serta mendukung **Owner Ad-Safe Mode** agar HP admin/pemilik tidak pernah memicu invalid traffic.

## 2. Cara Build Release APK / AAB di Android Studio

1. Ekstrak folder `android-native/` dan buka di **Android Studio** (Jellyfish / Ladybug atau lebih baru).
2. Buat signing keystore (sekali saja):
   ```bash
   keytool -genkey -v -keystore release-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias exambro_release
   ```
3. Salin `keystore.properties.example` menjadi `keystore.properties` lalu isi password keystore Anda.
4. Pastikan aset web terbaru berada di `app/src/main/assets/web/` (sudah disertakan otomatis di paket ini), atau ubah `EXAM_START_URL` di `app/build.gradle.kts` jika ingin memuat langsung dari URL Firebase Hosting (`https://exam-prida.web.app`).
5. Di Android Studio pilih menu **Build > Generate Signed Bundle / APK...** dan pilih **Android App Bundle (.aab)** untuk upload ke Google Play Console, atau **APK** untuk distribusi langsung.
6. Atau cukup push isi folder `android-native/` ke repository GitHub, lalu jalankan tab **Actions > Build Native Android APK & AAB** untuk mendapatkan file APK/AAB otomatis.
