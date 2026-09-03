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

    function renderForm(schema, formKey) {
        els.title.textContent = formKey || "Form";
        els.subtitle.textContent = "Isi data di bawah ini";

        const fields = (schema && schema.fields) || [];
        const inputFields = [];
        let submitField = null;

        fields.forEach(function (field) {
            if (field.type === "button") {
                // Field bertipe "button" bukan input, tapi definisi tombol submit -
                // "url" di dalamnya dipakai apa adanya sebagai tujuan POST.
                submitField = field;
                return;
            }
            inputFields.push(field);
            els.form.appendChild(buildField(field));
        });

        const submitBtn = document.createElement("button");
        submitBtn.type = "submit";
        submitBtn.textContent = (submitField && submitField.label) || "Kirim";
        els.form.appendChild(submitBtn);

        els.form.addEventListener("submit", function (e) {
            e.preventDefault();
            handleSubmit(inputFields, submitField, submitBtn);
        });

        els.skeleton.style.display = "none";
        els.form.style.display = "block";
    }

    function handleSubmit(inputFields, submitField, submitBtn) {
        hideBanner();

        if (!submitField || !submitField.url) {
            showBanner("error", "Form ini tidak punya tujuan submit (field button tanpa url).");
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

        fetch(submitField.url, {
            method: "POST",
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
