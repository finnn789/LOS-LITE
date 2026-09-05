(function () {
    // Token key SAMA dengan yang dipakai loan-lookup.js dan dynamic-form.html
    // (LOS_LITE_TOKEN_KEY di file-file itu) - supaya begitu user login lewat halaman ini,
    // halaman lain yang submit ke endpoint LOS-LITE ber-JWT otomatis ikut terautentikasi
    // tanpa login ulang, dan sebaliknya: kalau token sudah ada dari sesi sebelumnya,
    // halaman ini langsung anggap sudah login.
    const LOS_LITE_TOKEN_KEY = "losLiteJwtToken";
    const LOS_LITE_USER_KEY = "losLiteUser";

    // Base URL backend LOS-LITE sendiri - lihat los-lite-config.js (dimuat sebelum file
    // ini di index.html), dipakai bareng juga oleh loan-lookup.js.
    const LOS_LITE_BASE_URL = window.LosLiteConfig.baseUrl;

    const els = {
        loginContainer: document.getElementById("loginContainer"),
        formsContainer: document.getElementById("formsContainer"),
        loginForm: document.getElementById("loginForm"),
        loginUsername: document.getElementById("loginUsername"),
        loginPassword: document.getElementById("loginPassword"),
        loginBanner: document.getElementById("loginBanner"),
        loginSubmitBtn: document.getElementById("loginSubmitBtn"),
        sessionUser: document.getElementById("sessionUser"),
        logoutLink: document.getElementById("logoutLink"),
    };

    function showLoginBanner(message) {
        els.loginBanner.className = "banner show error";
        els.loginBanner.textContent = message;
    }

    function hideLoginBanner() {
        els.loginBanner.className = "banner";
        els.loginBanner.textContent = "";
    }

    function getStoredToken() {
        try {
            return localStorage.getItem(LOS_LITE_TOKEN_KEY) || "";
        } catch (e) {
            // localStorage bisa diblokir (mode privat/browser tertentu) - anggap belum login.
            return "";
        }
    }

    function getStoredUser() {
        try {
            return localStorage.getItem(LOS_LITE_USER_KEY) || "";
        } catch (e) {
            return "";
        }
    }

    function storeSession(token, displayName) {
        try {
            localStorage.setItem(LOS_LITE_TOKEN_KEY, token);
            localStorage.setItem(LOS_LITE_USER_KEY, displayName || "");
        } catch (e) {
            // Gagal simpan bukan alasan gagalkan login yang sudah sukses - sesi cuma
            // tidak "diingat" kalau halaman dibuka ulang.
        }
    }

    function clearSession() {
        try {
            localStorage.removeItem(LOS_LITE_TOKEN_KEY);
            localStorage.removeItem(LOS_LITE_USER_KEY);
        } catch (e) {
            // diamkan
        }
    }

    function showForms(displayName) {
        els.loginContainer.style.display = "none";
        els.formsContainer.style.display = "block";
        els.sessionUser.textContent = displayName ? "Masuk sebagai " + displayName : "";

        // forms-list.js memuat daftar form dari server Auto Layout (bukan bagian dari
        // pengecekan login LOS-LITE ini) - dipicu di sini supaya baru jalan setelah
        // Daftar Form ditampilkan, bukan langsung saat script dimuat.
        if (window.LosLiteForms && typeof window.LosLiteForms.load === "function") {
            window.LosLiteForms.load();
        }
    }

    function showLogin() {
        els.formsContainer.style.display = "none";
        els.loginContainer.style.display = "block";
        els.loginForm.reset();
        els.loginUsername.focus();
    }

    function handleLoginSubmit(e) {
        e.preventDefault();
        hideLoginBanner();

        const username = els.loginUsername.value.trim();
        const password = els.loginPassword.value;

        if (!username || !password) {
            showLoginBanner("Username dan password wajib diisi.");
            return;
        }

        els.loginSubmitBtn.disabled = true;
        els.loginSubmitBtn.textContent = "Memproses...";

        // LOS_LITE_BASE_URL + "/auth/login" -> menyasar backend LOS-LITE (lihat definisi
        // LOS_LITE_BASE_URL di atas), BUKAN server Auto Layout. Endpoint ini PUBLIC di
        // SecurityConfig (PUBLIC_PATHS "/auth/**"), jadi tidak butuh header Authorization.
        fetch(LOS_LITE_BASE_URL + "/auth/login", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username: username, password: password }),
        })
            .then(function (res) {
                return res
                    .json()
                    .catch(function () {
                        return null;
                    })
                    .then(function (body) {
                        if (!res.ok) {
                            const msg = (body && body.message) || "Login gagal (status " + res.status + ")";
                            throw new Error(msg);
                        }
                        return body;
                    });
            })
            .then(function (body) {
                // Bentuk response AuthController: ApiResponse<LoginResponse> ->
                // {success, message, data:{token, username, fullName, role}}.
                const data = body && body.data;
                if (!data || !data.token) {
                    throw new Error("Response login tidak berisi token.");
                }
                const displayName = data.fullName || data.username;
                storeSession(data.token, displayName);
                showForms(displayName);
            })
            .catch(function (err) {
                showLoginBanner(err.message || "Login gagal.");
            })
            .finally(function () {
                els.loginSubmitBtn.disabled = false;
                els.loginSubmitBtn.textContent = "Masuk";
            });
    }

    function handleLogout(e) {
        e.preventDefault();
        clearSession();
        showLogin();
    }

    els.loginForm.addEventListener("submit", handleLoginSubmit);
    els.logoutLink.addEventListener("click", handleLogout);

    // Token LOS-LITE sudah ada (dari login sebelumnya di halaman ini, atau dari form
    // Auto Layout "auth-login" di dynamic-form.html - keduanya pakai key yang sama) ->
    // lewati layar login, langsung tampilkan Daftar Form.
    const existingToken = getStoredToken();
    if (existingToken) {
        showForms(getStoredUser());
    } else {
        showLogin();
    }
})();
