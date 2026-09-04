(function () {
    // Base URL + token: lihat auto-layout-config.js (dimuat sebelum file ini di
    // dynamic-form.html) - fetch LANGSUNG dari browser (bukan lewat proxy backend
    // LOS-LITE, sesuai keputusan eksplisit). Kalau server Auto Layout belum set
    // header CORS (Access-Control-Allow-Origin) untuk origin halaman ini, request di
    // bawah bakal diblokir browser - itu PR di sisi server Auto Layout, bukan sesuatu
    // yang bisa diperbaiki dari file JS ini.
    const AUTO_LAYOUT_BASE_URL = window.AutoLayoutConfig.baseUrl;
    const AUTO_LAYOUT_BEARER_TOKEN = window.AutoLayoutConfig.bearerToken;

    // Token LOS-LITE (JWT hasil login, BEDA dari AUTO_LAYOUT_BEARER_TOKEN di atas) -
    // disimpan di localStorage supaya "nyambung" lintas form/halaman: form "auth-login"
    // (id 43) yang submit ke POST /auth/login menghasilkan token ini, lalu form LAIN
    // (mis. "Data Nasabah" -> POST /applicants, yang wajib JWT) otomatis ikut mengirim
    // token yang sama tanpa perlu login ulang. localStorage dipilih (bukan
    // sessionStorage) supaya tetap tersimpan walau tab/halaman ditutup lalu dibuka lagi.
    const LOS_LITE_TOKEN_KEY = "losLiteJwtToken";

    function getStoredLosLiteToken() {
        try {
            return localStorage.getItem(LOS_LITE_TOKEN_KEY) || "";
        } catch (e) {
            // localStorage bisa saja diblokir (mode privat/browser tertentu) - form
            // tetap harus jalan tanpa token, bukan crash.
            return "";
        }
    }

    function storeLosLiteToken(token) {
        try {
            localStorage.setItem(LOS_LITE_TOKEN_KEY, token);
        } catch (e) {
            // Diamkan - gagal simpan bukan alasan buat gagalkan submit yang sudah sukses.
        }
    }

    // Coba temukan JWT di response submit, bentuknya bisa macam-macam tergantung
    // endpoint tujuan (schema Auto Layout generic, bisa nunjuk ke endpoint apa saja) -
    // dicoba beberapa lokasi umum: langsung di root, dibungkus ApiResponse LOS-LITE
    // ({success,message,data:{token,...}}), atau nama field "accessToken".
    function extractTokenFromResponseBody(body) {
        if (!body || typeof body !== "object") return null;
        const candidates = [
            body.token,
            body.accessToken,
            body.data && body.data.token,
            body.data && body.data.accessToken,
        ];
        return candidates.find(function (v) { return typeof v === "string" && v.length > 0; }) || null;
    }

    // id form bisa di-override lewat query string, contoh: dynamic-form.html?formId=17
    const params = new URLSearchParams(window.location.search);
    const formId = params.get("formId") || "17";

    const els = {
        title: document.getElementById("formTitle"),
        subtitle: document.getElementById("formSubtitle"),
        banner: document.getElementById("banner"),
        skeleton: document.getElementById("skeleton"),
        form: document.getElementById("dynamicForm"),
        meta: document.getElementById("metaInfo"),
        container: document.querySelector(".container"),
        // Dipakai kalau schema.button.method GET (lihat renderDataTable) - form di atas
        // ("dynamicForm") dipakai kalau bukan GET, dua-duanya tidak pernah tampil bareng.
        tableWrap: document.getElementById("dynamicTableWrap"),
        tableHead: document.getElementById("dynamicTableHead"),
        tableBody: document.getElementById("dynamicTableBody"),
        refreshBtn: document.getElementById("refreshTableBtn"),
        // Modal "Detail" / "Update" per baris - lihat openDetailModal/openUpdateModal.
        rowModalOverlay: document.getElementById("rowModalOverlay"),
        rowModalTitle: document.getElementById("rowModalTitle"),
        rowModalBanner: document.getElementById("rowModalBanner"),
        rowModalBody: document.getElementById("rowModalBody"),
        rowModalCloseBtn: document.getElementById("rowModalCloseBtn"),
        rowModalSaveBtn: document.getElementById("rowModalSaveBtn"),
    };

    function showBanner(kind, message) {
        els.banner.className = "banner show " + kind;
        els.banner.textContent = message;
    }

    function hideBanner() {
        els.banner.className = "banner";
        els.banner.textContent = "";
    }

    // Mapping tipe field schema Auto Layout -> tipe <input> HTML. Tipe yang belum
    // dikenal (di luar textfield/date/textarea/select/checkbox/number/email) sengaja
    // fallback ke text, bukan dilempar error - biar form generator tidak crash total
    // kalau Auto Layout nambah tipe field baru di kemudian hari.
    const INPUT_TYPE_MAP = {
        textfield: "text",
        date: "date",
        number: "number",
        email: "email",
    };

    function buildField(field) {
        const wrapper = document.createElement("div");
        wrapper.className = "field";

        if (field.type === "checkbox") {
            wrapper.classList.add("field-checkbox");
            const input = document.createElement("input");
            input.type = "checkbox";
            input.id = "f_" + field.key;
            input.name = field.key;
            if (field.required) input.required = true;

            const label = document.createElement("label");
            label.htmlFor = input.id;
            label.textContent = field.label || field.key;
            appendRequiredMark(label, field.required);

            wrapper.appendChild(input);
            wrapper.appendChild(label);
            return wrapper;
        }

        const label = document.createElement("label");
        label.htmlFor = "f_" + field.key;
        label.textContent = field.label || field.key;
        appendRequiredMark(label, field.required);
        wrapper.appendChild(label);

        let input;
        if (field.type === "textarea") {
            input = document.createElement("textarea");
            input.rows = 3;
        } else if (field.type === "select") {
            input = document.createElement("select");
            (field.options || []).forEach(function (opt) {
                const optionEl = document.createElement("option");
                // dukung options berbentuk string ["a","b"] maupun {value,label}
                const isObj = opt && typeof opt === "object";
                optionEl.value = isObj ? opt.value : opt;
                optionEl.textContent = isObj ? (opt.label || opt.value) : opt;
                input.appendChild(optionEl);
            });
        } else {
            input = document.createElement("input");
            // password ditangani lewat INPUT_TYPE_MAP + fallback text - schema
            // "login test 2" (form id 18) pakai key "password" tapi type "textfield",
            // jadi tetap kerender sebagai text biasa mengikuti type schema-nya, bukan
            // ditebak dari nama key.
            input.type = INPUT_TYPE_MAP[field.type] || "text";
        }

        input.id = "f_" + field.key;
        input.name = field.key;
        if (field.required) input.required = true;

        wrapper.appendChild(input);
        return wrapper;
    }

    function appendRequiredMark(label, required) {
        if (!required) return;
        const mark = document.createElement("span");
        mark.className = "req";
        mark.textContent = "*";
        label.appendChild(mark);
    }

    // Gabungkan definisi endpoint (bentuknya beda-beda tergantung dari mana asalnya)
    // jadi {url, method} siap-fetch:
    // 1) LAMA: field bertipe "button" di dalam fields[], "url" di dalamnya SUDAH
    //    absolut - dipakai apa adanya.
    // 2) BARU (schema.button, FORM / schema.source, DATATABLE): {path, method, ...}
    //    top-level, "path" di dalamnya RELATIF (mis. "/loan-applications") - wajib
    //    digabung dulu dengan LosLiteConfig.baseUrl (lihat los-lite-config.js), karena
    //    ini endpoint bisnis LOS-LITE sendiri, bukan Auto Layout.
    // defaultMethod dipakai kalau schema tidak menyebut "method" sama sekali - "POST"
    // untuk form input (schema.type "FORM"), "GET" untuk tabel data (schema.type
    // "DATATABLE"), method APAPUN yang schema kasih tetap dihormati apa adanya.
    function resolveSubmitTarget(submitDef, defaultMethod) {
        if (!submitDef) return null;
        if (submitDef.url) {
            return { url: submitDef.url, method: (submitDef.method || defaultMethod).toUpperCase() };
        }
        if (submitDef.path) {
            const base = (window.LosLiteConfig && window.LosLiteConfig.baseUrl) || "";
            return { url: base + submitDef.path, method: (submitDef.method || defaultMethod).toUpperCase() };
        }
        return null;
    }

    // Router utama: schema.type "DATATABLE" ditampilkan sebagai TABEL data, di-fetch
    // dari schema.source (endpoint sumber yang dipilih saat schema ini digenerate di
    // Auto Layout - BUKAN schema.button, itu punya arti beda: endpoint create buat
    // form "Tambah Data" / schema.createForm, dan Auto Layout sengaja tidak mengisi
    // schema.button untuk Datatable). Selain itu (schema.type "FORM") tetap dirender
    // sebagai form input seperti biasa, memakai schema.button seperti sebelumnya.
    function renderForm(schema, formKey) {
        const fields = (schema && schema.fields) || [];
        const nonButtonFields = [];
        // submitField bentuk BARU (schema.button top-level) dipakai duluan; field
        // bertipe "button" di dalam fields[] (bentuk LAMA) menimpanya kalau ada,
        // supaya form lama tetap jalan seperti sebelumnya.
        let submitField = (schema && schema.button) || null;

        fields.forEach(function (field) {
            if (field.type === "button") {
                submitField = field;
                return;
            }
            nonButtonFields.push(field);
        });

        const schemaType = schema && typeof schema.type === "string" ? schema.type.toUpperCase() : "";
        const isDataTable = schemaType === "DATATABLE";

        if (isDataTable) {
            const target = resolveSubmitTarget(schema && schema.source, "GET");
            renderDataTable(formKey, nonButtonFields, target);
            return;
        }

        const target = resolveSubmitTarget(submitField, "POST");
        renderInputForm(schema, formKey, nonButtonFields, submitField, target);
    }

    function renderInputForm(schema, formKey, inputFields, submitField, target) {
        els.title.textContent = formKey || "Form";
        els.subtitle.textContent = "Isi data di bawah ini";

        // Layout 2 kolom (schema.layout === "two-column") - lihat .layout-two-column
        // di forms.css. Container digantikan "wide" juga supaya kolomnya tidak sesak
        // di dalam .container yang defaultnya cuma 560px.
        if (schema && schema.layout === "two-column") {
            els.form.classList.add("layout-two-column");
            if (els.container) els.container.classList.add("wide");
        }

        inputFields.forEach(function (field) {
            els.form.appendChild(buildField(field));
        });

        const submitBtn = document.createElement("button");
        submitBtn.type = "submit";
        submitBtn.textContent = (submitField && submitField.label) || "Kirim";
        els.form.appendChild(submitBtn);

        els.form.addEventListener("submit", function (e) {
            e.preventDefault();
            handleSubmit(inputFields, target, submitField, submitBtn);
        });

        els.skeleton.style.display = "none";
        els.form.style.display = "block";
    }

    function handleSubmit(inputFields, target, submitField, submitBtn) {
        hideBanner();

        if (!target) {
            showBanner("error", "Form ini tidak punya tujuan submit (button tanpa url/path).");
            return;
        }

        const payload = {};
        inputFields.forEach(function (field) {
            const el = document.getElementById("f_" + field.key);
            if (!el) return;
            payload[field.key] = field.type === "checkbox" ? el.checked : el.value;
        });

        submitBtn.disabled = true;
        submitBtn.textContent = "Mengirim...";

        const headers = { "Content-Type": "application/json" };
        const storedToken = getStoredLosLiteToken();
        if (storedToken) {
            // Token dari login form sebelumnya (lihat LOS_LITE_TOKEN_KEY) - dikirim ke
            // SEMUA target submit apa adanya, termasuk kalau url-nya bukan LOS-LITE
            // (schema Auto Layout generic, bisa nunjuk kemana saja). Cukup aman untuk
            // pemakaian internal/staff seperti sekarang; kalau nanti form ini dipakai
            // publik dan submit ke domain pihak ketiga yang tidak dipercaya, ini perlu
            // dibatasi supaya token tidak ikut bocor ke situ.
            headers["Authorization"] = "Bearer " + storedToken;
        }

        fetch(target.url, {
            method: target.method,
            headers: headers,
            body: JSON.stringify(payload),
        })
            .then(function (res) {
                if (!res.ok) {
                    throw new Error("Server membalas status " + res.status);
                }
                return res.text();
            })
            .then(function (text) {
                let body = null;
                try {
                    body = text ? JSON.parse(text) : null;
                } catch (e) {
                    // Response bukan JSON - abaikan, tetap dianggap sukses (status sudah ok).
                }

                const token = extractTokenFromResponseBody(body);
                if (token) {
                    storeLosLiteToken(token);
                    showBanner("success", "Data berhasil dikirim. Token login tersimpan - form lain otomatis pakai token ini.");
                } else {
                    showBanner("success", "Data berhasil dikirim.");
                }
            })
            .catch(function (err) {
                showBanner("error", "Gagal mengirim data: " + err.message);
            })
            .finally(function () {
                submitBtn.disabled = false;
                submitBtn.textContent = (submitField && submitField.label) || "Kirim";
            });
    }

    // Kolom & target GET dari tabel yang lagi ditampilkan - disimpan di sini supaya
    // tombol "Muat Ulang" bisa fetch ulang tanpa perlu parse schema lagi.
    let currentTableColumns = null;
    let currentTableTarget = null;

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

    function renderTableRows(columns, rows) {
        els.tableBody.innerHTML = "";

        if (!rows.length) {
            els.skeleton.textContent = "Belum ada data.";
            els.skeleton.style.display = "block";
            return;
        }

        rows.forEach(function (row) {
            const tr = document.createElement("tr");
            columns.forEach(function (field) {
                const td = document.createElement("td");
                td.textContent = formatCellValue(row ? row[field.key] : undefined);
                tr.appendChild(td);
            });

            // Kolom aksi - "Detail" ambil SEMUA field respons (bukan cuma kolom yang
            // ditampilkan, mis. NIK yang sengaja tidak disertakan di list summary),
            // "Update" ambil data lengkap yang sama lalu tampilkan sebagai form edit.
            // Dua-duanya butuh row.id, jadi tidak ada aksi kalau baris tidak punya id.
            const tdAction = document.createElement("td");
            if (row && row.id !== undefined && row.id !== null) {
                const wrap = document.createElement("div");
                wrap.className = "row-actions";

                const detailBtn = document.createElement("button");
                detailBtn.type = "button";
                detailBtn.className = "btn-row-action";
                detailBtn.textContent = "Detail";
                detailBtn.addEventListener("click", function () {
                    openDetailModal(row.id);
                });

                const updateBtn = document.createElement("button");
                updateBtn.type = "button";
                updateBtn.className = "btn-row-action primary";
                updateBtn.textContent = "Update";
                updateBtn.addEventListener("click", function () {
                    openUpdateModal(row.id);
                });

                wrap.appendChild(detailBtn);
                wrap.appendChild(updateBtn);
                tdAction.appendChild(wrap);
            }
            tr.appendChild(tdAction);

            els.tableBody.appendChild(tr);
        });

        els.skeleton.style.display = "none";
    }

    // Endpoint detail/update per baris NGIKUTIN KONVENSI REST "{endpoint list}/{id}"
    // (mis. sumber tabel /applicants -> detail & update di /applicants/{id}) - sama
    // seperti pola /applicants/{id} dan /loan-applications/{id} yang sudah ada di
    // discovery endpoint LOS-LITE. Tidak ada info terpisah di schema Auto Layout
    // buat ini, jadi konvensi ini SATU-SATUNYA cara dynamic-form.js tahu ke mana
    // harus manggil - kalau resource sumbernya tidak punya endpoint /{id}, tombol
    // Detail/Update di baris itu bakal gagal fetch (ditangani lewat banner error).
    function buildRowUrl(id) {
        return currentTableTarget.url + "/" + encodeURIComponent(id);
    }

    function rowAuthHeaders() {
        const headers = {};
        const storedToken = getStoredLosLiteToken();
        if (storedToken) headers["Authorization"] = "Bearer " + storedToken;
        return headers;
    }

    // Sama seperti normalisasi bentuk response di loadTableData, tapi buat SATU
    // object (bukan array) - endpoint detail LOS-LITE selalu balikin
    // {success,message,data:{...}}, tapi ditulis toleran kalau nanti ada endpoint
    // lain yang balikin object langsung tanpa amplop.
    function extractSingleRecord(body) {
        if (body && typeof body === "object" && body.data && typeof body.data === "object" && !Array.isArray(body.data)) {
            return body.data;
        }
        return body;
    }

    function closeRowModal() {
        els.rowModalOverlay.classList.remove("show");
        els.rowModalBody.innerHTML = "";
        els.rowModalBanner.className = "banner";
        els.rowModalSaveBtn.style.display = "none";
        els.rowModalSaveBtn.onclick = null;
    }

    function showRowModalError(message) {
        els.rowModalBanner.className = "banner show error";
        els.rowModalBanner.textContent = message;
    }

    // "Detail" murni baca - nampilin SEMUA pasangan key-value dari respons apa
    // adanya (dl/dt/dd), termasuk field yang sengaja tidak ada di kolom tabel.
    function openDetailModal(id) {
        els.rowModalTitle.textContent = "Detail Data #" + id;
        els.rowModalBody.innerHTML = "<div class=\"skeleton\">Mengambil data…</div>";
        els.rowModalSaveBtn.style.display = "none";
        els.rowModalOverlay.classList.add("show");

        fetch(buildRowUrl(id), { headers: rowAuthHeaders() })
            .then(function (res) {
                if (res.status === 401 || res.status === 403) {
                    throw new Error("Akses ditolak (" + res.status + ") - login ulang lewat index.html.");
                }
                if (!res.ok) throw new Error("Server membalas status " + res.status);
                return res.json();
            })
            .then(function (body) {
                const record = extractSingleRecord(body);
                const dl = document.createElement("dl");
                dl.className = "detail-list";
                Object.keys(record || {}).forEach(function (key) {
                    const dt = document.createElement("dt");
                    dt.textContent = key;
                    const dd = document.createElement("dd");
                    dd.textContent = formatCellValue(record[key]);
                    dl.appendChild(dt);
                    dl.appendChild(dd);
                });
                els.rowModalBody.innerHTML = "";
                els.rowModalBody.appendChild(dl);
            })
            .catch(function (err) {
                els.rowModalBody.innerHTML = "";
                showRowModalError("Gagal mengambil detail: " + err.message);
            });
    }

    // Tebak tipe <input> dari NILAI yang sekarang (bukan dari schema - endpoint
    // detail tidak bawa metadata tipe field), biar minimal tanggal & angka dapat
    // input yang sesuai, sisanya fallback ke text.
    function guessInputType(value) {
        if (typeof value === "number") return "number";
        if (typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value)) return "date";
        return "text";
    }

    // "Update" ambil data lengkap yang sama seperti Detail, tapi dirender sebagai
    // form edit (satu <input> per key, kecuali "id" - itu identitas baris, bukan
    // sesuatu yang diedit). Submit PUT ke endpoint yang sama, body-nya field-field
    // ini apa adanya (lihat submitRowUpdate).
    function openUpdateModal(id) {
        els.rowModalTitle.textContent = "Update Data #" + id;
        els.rowModalBody.innerHTML = "<div class=\"skeleton\">Mengambil data…</div>";
        els.rowModalSaveBtn.style.display = "none";
        els.rowModalOverlay.classList.add("show");

        fetch(buildRowUrl(id), { headers: rowAuthHeaders() })
            .then(function (res) {
                if (res.status === 401 || res.status === 403) {
                    throw new Error("Akses ditolak (" + res.status + ") - login ulang lewat index.html.");
                }
                if (!res.ok) throw new Error("Server membalas status " + res.status);
                return res.json();
            })
            .then(function (body) {
                const record = extractSingleRecord(body) || {};
                const form = document.createElement("div");

                Object.keys(record).forEach(function (key) {
                    if (key === "id") return;

                    const field = document.createElement("div");
                    field.className = "field";

                    const label = document.createElement("label");
                    label.htmlFor = "rowField_" + key;
                    label.textContent = key;
                    field.appendChild(label);

                    const value = record[key];
                    const input = document.createElement("input");
                    input.id = "rowField_" + key;
                    input.name = key;
                    input.type = guessInputType(value);
                    input.value = value === null || value === undefined ? "" : value;
                    field.appendChild(input);

                    form.appendChild(field);
                });

                els.rowModalBody.innerHTML = "";
                els.rowModalBody.appendChild(form);
                els.rowModalSaveBtn.style.display = "inline-block";
                els.rowModalSaveBtn.onclick = function () {
                    submitRowUpdate(id, Object.keys(record).filter(function (k) { return k !== "id"; }));
                };
            })
            .catch(function (err) {
                els.rowModalBody.innerHTML = "";
                showRowModalError("Gagal mengambil data: " + err.message);
            });
    }

    function submitRowUpdate(id, keys) {
        const payload = {};
        keys.forEach(function (key) {
            const input = document.getElementById("rowField_" + key);
            if (!input) return;
            payload[key] = input.type === "number" && input.value !== "" ? Number(input.value) : input.value;
        });

        els.rowModalSaveBtn.disabled = true;
        els.rowModalSaveBtn.textContent = "Menyimpan…";

        fetch(buildRowUrl(id), {
            method: "PUT",
            headers: Object.assign({ "Content-Type": "application/json" }, rowAuthHeaders()),
            body: JSON.stringify(payload),
        })
            .then(function (res) {
                return res.text().then(function (text) {
                    let body = null;
                    try { body = text ? JSON.parse(text) : null; } catch (e) { /* bukan JSON, abaikan */ }
                    if (!res.ok) {
                        const msg = (body && body.message) || "Server membalas status " + res.status;
                        throw new Error(msg);
                    }
                });
            })
            .then(function () {
                closeRowModal();
                showBanner("success", "Data #" + id + " berhasil diperbarui.");
                if (currentTableColumns && currentTableTarget) {
                    loadTableData(currentTableColumns, currentTableTarget);
                }
            })
            .catch(function (err) {
                showRowModalError("Gagal menyimpan: " + err.message);
            })
            .finally(function () {
                els.rowModalSaveBtn.disabled = false;
                els.rowModalSaveBtn.textContent = "Simpan";
            });
    }

    els.rowModalCloseBtn.addEventListener("click", closeRowModal);
    els.rowModalOverlay.addEventListener("click", function (e) {
        if (e.target === els.rowModalOverlay) closeRowModal();
    });

    function loadTableData(columns, target) {
        hideBanner();
        els.tableBody.innerHTML = "";
        els.skeleton.textContent = "Mengambil data…";
        els.skeleton.style.display = "block";

        const headers = {};
        const storedToken = getStoredLosLiteToken();
        if (storedToken) {
            // Sama seperti submit form biasa - token login LOS-LITE (lihat
            // LOS_LITE_TOKEN_KEY) dikirim ke endpoint ini kalau ada.
            headers["Authorization"] = "Bearer " + storedToken;
        }

        // Method DIAMBIL dari schema (target.method, lihat resolveSubmitTarget) -
        // biasanya GET, tapi dihormati apa adanya kalau schema menyebut yang lain.
        // Tidak pernah kirim body di mode tabel - ini murni fetch untuk MENAMPILKAN
        // data, bukan submit.
        fetch(target.url, { method: target.method, headers: headers })
            .then(function (res) {
                if (res.status === 401 || res.status === 403) {
                    throw new Error("Akses ditolak (" + res.status + ") - token LOS-LITE tidak ada/kedaluwarsa, login ulang lewat index.html.");
                }
                if (!res.ok) {
                    throw new Error("Server membalas status " + res.status);
                }
                return res.json();
            })
            .then(function (body) {
                // Bentuk response bisa array langsung, dibungkus ApiResponse LOS-LITE
                // ({success,message,data:[...]}), atau satu object tunggal (dianggap 1 baris).
                let rows;
                if (Array.isArray(body)) {
                    rows = body;
                } else if (body && Array.isArray(body.data)) {
                    rows = body.data;
                } else if (body && typeof body === "object") {
                    rows = [body];
                } else {
                    rows = [];
                }
                renderTableRows(columns, rows);
            })
            .catch(function (err) {
                els.skeleton.style.display = "none";
                showBanner("error", "Gagal mengambil data: " + err.message);
            });
    }

    function renderDataTable(formKey, columns, target) {
        els.title.textContent = formKey || "Data";
        els.subtitle.textContent = "Data dari server";
        if (els.container) els.container.classList.add("wide");

        if (!target) {
            els.skeleton.style.display = "none";
            showBanner("error", "Form tabel ini tidak punya sumber data (button tanpa url/path).");
            return;
        }

        els.tableHead.innerHTML = "";
        columns.forEach(function (field) {
            const th = document.createElement("th");
            th.textContent = field.label || field.key;
            els.tableHead.appendChild(th);
        });
        const actionTh = document.createElement("th");
        actionTh.textContent = "Aksi";
        els.tableHead.appendChild(actionTh);

        currentTableColumns = columns;
        currentTableTarget = target;

        els.refreshBtn.style.display = "inline-block";
        els.tableWrap.style.display = "block";

        loadTableData(columns, target);
    }

    els.refreshBtn.addEventListener("click", function () {
        if (currentTableColumns && currentTableTarget) {
            loadTableData(currentTableColumns, currentTableTarget);
        }
    });

    function loadSchema() {
        const url = AUTO_LAYOUT_BASE_URL + "/forms/" + encodeURIComponent(formId);
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
            .then(function (data) {
                if (!data.active) {
                    showBanner("error", "Form ini sedang tidak aktif.");
                }

                // formSchema dari Auto Layout adalah STRING berisi JSON (double-encoded),
                // BUKAN object langsung - wajib di-parse sekali lagi di sini.
                let schema;
                try {
                    schema = JSON.parse(data.formSchema);
                } catch (parseErr) {
                    throw new Error("formSchema bukan JSON valid: " + parseErr.message);
                }

                renderForm(schema, data.formKey);
                els.meta.textContent = "Form ID " + data.id + " - versi " + data.version;
            })
            .catch(function (err) {
                els.skeleton.style.display = "none";
                els.title.textContent = "Gagal memuat form";
                showBanner(
                    "error",
                    "Tidak bisa mengambil form schema dari " + AUTO_LAYOUT_BASE_URL + ": " + err.message +
                    ". Kalau ini error jaringan/CORS, pastikan server Auto Layout mengizinkan origin halaman ini (Access-Control-Allow-Origin)."
                );
            });
    }

    loadSchema();
})();
