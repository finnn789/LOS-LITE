(function () {
    // Base URL + token bersama - lihat auto-layout-config.js (dimuat sebelum file ini
    // di index.html).
    const AUTO_LAYOUT_BASE_URL = window.AutoLayoutConfig.baseUrl;
    const AUTO_LAYOUT_BEARER_TOKEN = window.AutoLayoutConfig.bearerToken;

    const els = {
        banner: document.getElementById("banner"),
        skeleton: document.getElementById("skeleton"),
        table: document.getElementById("formsTable"),
        tbody: document.getElementById("formsTableBody"),
    };

    function showBanner(kind, message) {
        els.banner.className = "banner show " + kind;
        els.banner.textContent = message;
    }

    function formatDate(iso) {
        if (!iso) return "-";
        const d = new Date(iso);
        if (isNaN(d.getTime())) return iso;
        return d.toLocaleString("id-ID", {
            day: "2-digit", month: "short", year: "numeric",
            hour: "2-digit", minute: "2-digit",
        });
    }

    function buildRow(form) {
        const tr = document.createElement("tr");

        const tdId = document.createElement("td");
        tdId.textContent = form.id;
        tr.appendChild(tdId);

        const tdKey = document.createElement("td");
        tdKey.textContent = form.formKey || "-";
        tr.appendChild(tdKey);

        const tdStatus = document.createElement("td");
        const badge = document.createElement("span");
        badge.className = "badge " + (form.active ? "active" : "inactive");
        badge.textContent = form.active ? "Aktif" : "Nonaktif";
        tdStatus.appendChild(badge);
        tr.appendChild(tdStatus);

        const tdVersion = document.createElement("td");
        tdVersion.textContent = form.version != null ? form.version : "-";
        tr.appendChild(tdVersion);

        const tdCreated = document.createElement("td");
        tdCreated.textContent = formatDate(form.createdAt);
        tr.appendChild(tdCreated);

        const tdAction = document.createElement("td");
        const link = document.createElement("a");
        link.className = "btn-open";
        link.href = "dynamic-form.html?formId=" + encodeURIComponent(form.id);
        link.textContent = "Isi Form";
        tdAction.appendChild(link);
        tr.appendChild(tdAction);

        return tr;
    }

    function renderTable(forms) {
        if (!forms.length) {
            els.skeleton.textContent = "Belum ada form yang tersedia.";
            return;
        }

        forms.forEach(function (form) {
            els.tbody.appendChild(buildRow(form));
        });

        els.skeleton.style.display = "none";
        els.table.style.display = "table";
    }

    function loadForms() {
        const url = AUTO_LAYOUT_BASE_URL + "/forms";
        const headers = {};
        if (AUTO_LAYOUT_BEARER_TOKEN) {
            headers["Authorization"] = "Bearer " + AUTO_LAYOUT_BEARER_TOKEN;
        }

        fetch(url, { headers: headers })
            .then(function (res) {
                if (res.status === 401 || res.status === 403) {
                    throw new Error("Akses ditolak (" + res.status + ") - token Bearer tidak valid/kedaluwarsa");
                }
                if (!res.ok) {
                    throw new Error("Server membalas status " + res.status);
                }
                return res.json();
            })
            .then(function (forms) {
                renderTable(Array.isArray(forms) ? forms : []);
            })
            .catch(function (err) {
                els.skeleton.style.display = "none";
                showBanner(
                    "error",
                    "Tidak bisa mengambil daftar form dari " + AUTO_LAYOUT_BASE_URL + ": " + err.message +
                    ". Kalau ini error jaringan/CORS, pastikan server Auto Layout mengizinkan origin halaman ini (Access-Control-Allow-Origin)."
                );
            });
    }

    // Tidak auto-run lagi seperti sebelumnya - halaman ini sekarang mensyaratkan login
    // (lihat login.js). login.js yang memanggil LosLiteForms.load() setelah user berhasil
    // masuk (atau langsung, kalau token LOS-LITE sudah tersimpan dari sesi sebelumnya).
    window.LosLiteForms = { load: loadForms };
})();
