// Konfigurasi bersama koneksi ke Auto Layout (form generator eksternal) - dipakai
// dynamic-form.js (ambil satu form) dan forms-list.js (daftar semua form), disatukan
// di sini supaya base URL & token cuma ditulis SEKALI, tidak dobel-tulis di dua file.
//
// PERINGATAN: token ini HARDCODED plain-text dan bakal ke-load browser sebagai file
// statis biasa - siapapun yang buka DevTools/View Source halaman ini bisa membacanya.
// Aman selama halaman-halaman /forms ini cuma diakses staff internal. Kalau nanti
// dipublish untuk diisi publik/nasabah langsung, token role SERVICE ini WAJIB
// dipindah ke backend (proxy server-side), tidak boleh tetap di file client-side ini.
window.AutoLayoutConfig = {
    baseUrl: "http://25.21.167.25:8082",
    bearerToken:
        "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJsb3MtbGl0ZS1zZXJ2aWNlIiwicm9sZSI6IlNFUlZJQ0UifQ.sdjmMfnzU-vjDkuq94JIrbBvxrc8u8hAqeuPUkdVTatrYK_15DxES10TKH3_hnRR",
};
