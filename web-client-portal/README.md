# IHC Portal Klien (Web)

Portal web statis (HTML, CSS, JavaScript murni) untuk klien **Iyan Health Community (IHC)**.

## Fitur Portal Klien
- Login Email/Password, tab **Buat Password** (terbatas pada email yang sudah didaftarkan coach di Firestore `clientInvites/{emailNormalized}`), dan **Reset Password**.
- 5 Menu Klien (hanya mengakses data milik email yang cocok):
  1. **Data Diri**: Identitas, paket, periode coaching, sesi terpakai, ringkasan fasilitas.
  2. **Jadwal**: Kalender bulanan + daftar sesi WIB + tombol **Minta Reschedule** (terkunci untuk paket Basic).
  3. **Progress Statistik**: Ringkasan dan grafik tren SVG sesuai paket.
  4. **Foto Progres**: Galeri dengan filter sudut sesuai paket & mode perbesar.
  5. **Gerakan Olahraga**: Program latihan dari coach, timer istirahat interaktif, serta form **Request Gerakan Workout**.

## Langkah Deploy
1. Aktifkan **Authentication → Sign-in method → Email/Password** di Firebase Console.
2. Salin konfigurasi Web App Firebase ke `web-client-portal/firebase-config.js`.
3. Deploy menggunakan Firebase Hosting (`firebase deploy --only hosting`) atau unggah folder `web-client-portal/` ke Netlify (`netlify.toml` sudah tersedia).
