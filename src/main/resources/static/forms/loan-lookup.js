(function () {
    // Base URL backend LOS-LITE sendiri - lihat los-lite-config.js. Halaman ini TIDAK
    // pernah bicara ke Auto Layout sama sekali (beda dari dynamic-form.html) - seluruh
    // rantai NIK -> applicant -> daftar pengajuan -> detail + aksi murni manggil endpoint
    // bisnis LOS-LITE sendiri secara berurutan.
    const LOS_LITE_BASE_URL = window.LosLiteConfig.baseUrl;
    const LOS_LITE_TOKEN_KEY = "losLiteJwtToken";

    function getStoredToken() {
        try {
            return localStorage.getItem(LOS_LITE_TOKEN_KEY) || "";
        } catch (e) {
            return "";
        }
    }

    const els = {
        banner: document.getElementById("banner"),
        nikInput: document.getElementById("nikInput"),
        searchBtn: document.getElementById("searchBtn"),
        applicantSection: document.getElementById("applicantSection"),
        applicantInfo: document.getElementById("applicantInfo"),
        loanListSkeleton: document.getElementById("loanListSkeleton"),
        loanListTable: document.getElementById("loanListTable"),
        loanListBody: document.getElementById("loanListBody"),
        detailSection: document.getElementById("detailSection"),
        detailBanner: document.getElementById("detailBanner"),
        detailSkeleton: document.getElementById("detailSkeleton"),
        loanDetail: document.getElementById("loanDetail"),
        actionButtons: document.getElementById("actionButtons"),
    };

    function showBanner(kind, message) {
        els.banner.className = "banner show " + kind;
        els.banner.textContent = message;
    }

    function hideBanner() {
        els.banner.className = "banner";
        els.banner.textContent = "";
    }

    function showDetailBanner(kind, message) {
        els.detailBanner.className = "banner show " + kind;
        els.detailBanner.textContent = message;
    }

    function hideDetailBanner() {
        els.detailBanner.className = "banner";
        els.detailBanner.textContent = "";
    }

    function formatCellValue(value) {
        if (value === null || value === undefined || value === "") return "-";
        if (typeof value === "boolean") return value ? "Ya" : "Tidak";
        if (typeof value === "object") {
            try {
                return JSON.stringify(value);
            } catch (e) {
                return String(value);
            }
        }
        return String(value);
    }

    function renderDetailList(container, record) {
        container.innerHTML = "";
        Object.keys(record || {}).forEach(function (key) {
            const dt = document.createElement("dt");
            dt.textContent = key;
            const dd = document.createElement("dd");
            dd.textContent = formatCellValue(record[key]);
            container.appendChild(dt);
            container.appendChild(dd);
        });
    }

    // Amplop response LOS-LITE selalu {success,message,data:...,timestamp} - diambil
    // apa adanya, toleran kalau suatu saat ada endpoint yang balikin object langsung.
    function extractData(body) {
        if (body && typeof body === "object" && "data" in body) return body.data;
        return body;
    }

    function apiFetch(path, options) {
        options = options || {};
        const headers = Object.assign({}, options.headers || {});
        const token = getStoredToken();
        if (token) headers["Authorization"] = "Bearer " + token;

        return fetch(LOS_LITE_BASE_URL + path, Object.assign({}, options, { headers: headers }))
            .then(function (res) {
                return res.text().then(function (text) {
                    let body = null;
                    try {
                        body = text ? JSON.parse(text) : null;
                    } catch (e) {
                        // Bukan JSON - biarkan null, ditangani di bawah lewat status code.
                    }

                    if (res.status === 401 || res.status === 403) {
                        throw new Error("Akses ditolak (" + res.status + ") - login ulang lewat index.html.");
                    }
                    if (!res.ok) {
                        throw new Error((body && body.message) || "Server membalas status " + res.status);
                    }
                    return body;
                });
            });
    }

    // Langkah 1: NIK -> data nasabah, lalu otomatis lanjut ke langkah 2 (daftar
    // pengajuan). User cukup ketik NIK SEKALI - applicantId & seterusnya diturunkan
    // otomatis dari sini, tidak pernah diketik manual.
    function searchByNik() {
        hideBanner();
        els.applicantSection.style.display = "none";
        els.detailSection.style.display = "none";

        const nik = els.nikInput.value.trim();
        if (!nik) {
            showBanner("error", "NIK wajib diisi.");
            return;
        }

        els.searchBtn.disabled = true;
        els.searchBtn.textContent = "Mencari...";

        apiFetch("/applicants/by-nik/" + encodeURIComponent(nik))
            .then(function (body) {
                const applicant = extractData(body);
                renderDetailList(els.applicantInfo, applicant);
                els.applicantSection.style.display = "block";
                loadLoanList(applicant.id);
            })
            .catch(function (err) {
                showBanner("error", "Gagal mencari nasabah: " + err.message);
            })
            .finally(function () {
                els.searchBtn.disabled = false;
                els.searchBtn.textContent = "Cari";
            });
    }

    // Langkah 2: applicantId (dari langkah 1, bukan input manual) -> daftar pengajuan
    // milik nasabah itu.
    function loadLoanList(applicantId) {
        els.loanListTable.style.display = "none";
        els.loanListSkeleton.style.display = "block";
        els.detailSection.style.display = "none";

        apiFetch("/loan-applications/by-applicant/" + encodeURIComponent(applicantId))
            .then(function (body) {
                renderLoanList(extractData(body) || []);
            })
            .catch(function (err) {
                showBanner("error", "Gagal mengambil daftar pengajuan: " + err.message);
            })
            .finally(function () {
                els.loanListSkeleton.style.display = "none";
            });
    }

    function renderLoanList(loans) {
        els.loanListBody.innerHTML = "";

        if (!loans.length) {
            els.loanListBody.innerHTML = "<tr><td colspan=\"5\">Nasabah ini belum punya pengajuan.</td></tr>";
            els.loanListTable.style.display = "table";
            return;
        }

        loans.forEach(function (loan) {
            const tr = document.createElement("tr");

            [loan.id, loan.status, formatCellValue(loan.loanAmountRequested), formatCellValue(loan.purpose)]
                .forEach(function (text) {
                    const td = document.createElement("td");
                    td.textContent = text;
                    tr.appendChild(td);
                });

            // Tombol "Pilih" ini yang bikin id pengajuan otomatis "nempel" - id-nya
            // sudah ada di closure (loan.id), tidak pernah diketik ulang oleh user.
            const tdAction = document.createElement("td");
            const selectBtn = document.createElement("button");
            selectBtn.type = "button";
            selectBtn.className = "btn-row-action primary";
            selectBtn.textContent = "Pilih";
            selectBtn.addEventListener("click", function () {
                loadLoanDetail(loan.id);
            });
            tdAction.appendChild(selectBtn);
            tr.appendChild(tdAction);

            els.loanListBody.appendChild(tr);
        });

        els.loanListTable.style.display = "table";
    }

    // Langkah 3: id pengajuan (dari tombol "Pilih" yang diklik, BUKAN diketik manual) ->
    // detail lengkap + tombol aksi. successMessage opsional - dipakai renderActionButtons
    // supaya pesan sukses aksi sebelumnya tidak langsung ketutup saat detail dimuat ulang
    // (hideDetailBanner() di awal fungsi ini kalau tidak dijadwalkan ulang begini).
    function loadLoanDetail(loanId, successMessage) {
        hideDetailBanner();
        els.detailSection.style.display = "block";
        els.detailSection.scrollIntoView({ behavior: "smooth", block: "start" });
        els.loanDetail.innerHTML = "";
        els.actionButtons.innerHTML = "";
        els.detailSkeleton.style.display = "block";

        return apiFetch("/loan-applications/" + encodeURIComponent(loanId))
            .then(function (body) {
                renderDetailList(els.loanDetail, extractData(body));
                renderActionButtons(loanId);
                if (successMessage) showDetailBanner("success", successMessage);
            })
            .catch(function (err) {
                showDetailBanner("error", "Gagal mengambil detail: " + err.message);
            })
            .finally(function () {
                els.detailSkeleton.style.display = "none";
            });
    }

    // Definisi tombol aksi workflow - masing-masing kirim {"id": <id pengajuan>, ...extra}
    // ke path-nya. decision APPROVE/REJECT sengaja 2 tombol terpisah (bukan satu tombol +
    // dialog konfirmasi) supaya keputusan officer eksplisit dari tombol yang diklik.
    const ACTIONS = [
        { label: "Submit", path: "/loan-applications/submit" },
        { label: "Verifikasi Dokumen", path: "/loan-applications/document-verification" },
        { label: "Jalankan Scoring", path: "/loan-applications/scoring" },
        { label: "Approve", path: "/loan-applications/review", extra: { decision: "APPROVE" } },
        { label: "Reject", path: "/loan-applications/review", extra: { decision: "REJECT" }, danger: true },
        { label: "Cairkan Dana", path: "/loan-applications/disburse" },
    ];

    // Semua aksi selalu ditampilkan apa adanya - halaman ini TIDAK menebak status mana
    // yang "boleh" pindah ke mana (itu aturan state-machine di LoanApplicationStatusService,
    // bukan urusan FE). Kalau dipanggil di status yang salah, server yang menolak dan
    // pesan errornya tampil di detailBanner.
    function renderActionButtons(loanId) {
        els.actionButtons.innerHTML = "";
        ACTIONS.forEach(function (action) {
            const btn = document.createElement("button");
            btn.type = "button";
            btn.className = "btn-row-action" + (action.danger ? " danger" : " primary");
            btn.textContent = action.label;
            btn.addEventListener("click", function () {
                runAction(action, loanId, btn);
            });
            els.actionButtons.appendChild(btn);
        });
    }

    function runAction(action, loanId, btn) {
        hideDetailBanner();
        const originalLabel = btn.textContent;
        btn.disabled = true;
        btn.textContent = "Memproses...";

        const payload = Object.assign({ id: loanId }, action.extra || {});

        apiFetch(action.path, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload),
        })
            .then(function () {
                return loadLoanDetail(loanId, action.label + " berhasil dijalankan.");
            })
            .catch(function (err) {
                showDetailBanner("error", "Gagal " + action.label + ": " + err.message);
            })
            .finally(function () {
                btn.disabled = false;
                btn.textContent = originalLabel;
            });
    }

    els.searchBtn.addEventListener("click", searchByNik);
    els.nikInput.addEventListener("keydown", function (e) {
        if (e.key === "Enter") searchByNik();
    });
})();
