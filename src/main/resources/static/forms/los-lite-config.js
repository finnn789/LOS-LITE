// Base URL backend LOS-LITE SENDIRI (BEDA dari AutoLayoutConfig di
// auto-layout-config.js - itu server Auto Layout yang terpisah, dipakai buat AMBIL
// schema form & daftar form). File ini dipakai buat KIRIM data ke LOS-LITE sendiri:
// login.js (POST /auth/login) dan loan-lookup.js (rantai NIK -> pengajuan -> aksi) -
// base URL cuma ditulis SEKALI di sini, bukan dobel-tulis di file-file itu.
// dynamic-form.html TIDAK memuat file ini - dia berdiri sendiri (satu file HTML+CSS+JS,
// lihat komentar KONFIGURASI di dalamnya) dan menghitung base URL-nya sendiri dengan
// logic yang sama persis.
//
// - Kalau halaman /forms ini dibuka lewat http/https (disajikan langsung oleh server
//   LOS-LITE, mis. http://<host>:8082/forms/index.html), path relatif otomatis benar -
//   dipakai origin halaman itu sendiri, host manapun itu, TIDAK perlu diubah manual.
// - Kalau dibuka sebagai file lokal (file:///... - double click di Explorer), path
//   relatif tadi ikut dianggap path file, BUKAN alamat server - makanya perlu fallback
//   URL absolut di bawah. Ganti kalau server LOS-LITE tidak jalan di localhost:8082.
window.LosLiteConfig = {
    baseUrl:
        window.location.protocol === "file:"
            ? "http://localhost:8082"
            : window.location.origin,
};
