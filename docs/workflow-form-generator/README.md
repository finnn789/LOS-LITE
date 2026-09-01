# Dummy Schema: Form Generator ↔ Workflow Engine

Data dummy untuk FE tim yang bikin **Form Generator** — komponen yang render form
secara dinamis berdasarkan schema yang datang dari Workflow Engine (Camunda 8 /
Zeebe), bukan hardcode per halaman. Dibuat sebagai pendamping
[`.claude/tasks/camunda-workflow-plan.md`](../../.claude/tasks/camunda-workflow-plan.md)
(planning Camunda yang belum diimplementasi) — dipakai FE untuk mulai ngoding Form
Generator duluan, sebelum endpoint proxy-nya beneran ada di backend.

## Kenapa bukan langsung dari Camunda Tasklist API

Zeebe **tidak** menyimpan data bisnis (nama applicant, jumlah pinjaman, hasil
scoring, dst) sebagai process variable secara lengkap — sesuai keputusan di
plan, cuma `loanApplicationId`, `decision`, dan `reviewDecision` yang benar-benar
jadi variable di process instance. Jadi FE tidak bisa langsung nembak Tasklist
REST/GraphQL API dan dapat semua data yang dibutuhkan form.

Asumsi kontraknya: **backend app ini** yang jadi proxy — endpoint baru misal
`GET /api/workflow/tasks/{taskId}` yang:
1. Query task terbuka ke `CamundaClient` (dapat `taskId`, `formKey`, variable Zeebe).
2. Query DB pakai `loanApplicationId` dari variable itu buat ambil data
   Applicant/LoanApplication/ScoringResult.
3. Gabungin semuanya + `formSchema` (JSON schema ala [form-js](https://github.com/bpmn-io/form-js))
   jadi satu response yang dikonsumsi Form Generator FE.

Endpoint ini **belum diimplementasi** — file di sini murni dummy response-nya,
supaya FE dan BE sepakat bentuk kontrak dulu sebelum ngoding.

## Isi folder

| File | User Task Camunda | Tahap di plan |
|---|---|---|
| [`officer-review-task.json`](officer-review-task.json) | "Officer Review" | Fase 4 |
| [`disburse-funds-task.json`](disburse-funds-task.json) | "Disburse Funds" | Fase 5 |

Tiap file punya 4 bagian:

- **`task`** — metadata task dari Camunda (bentuknya mirip response Tasklist API:
  `taskId`, `processInstanceKey`, `taskState`, `formKey`, dst).
- **`context`** — data bisnis read-only hasil join ke DB (applicant, detail
  pinjaman, hasil scoring). **Bukan** Zeebe variable — jangan dikirim balik saat
  submit.
- **`variables`** — Zeebe process variable yang *benar-benar* ada di process
  instance, persis seperti di diagram plan.
- **`formSchema`** — schema komponen form (format form-js: `components[]`,
  masing-masing punya `type`, `key`/binding ke variable, `label`, `validate`).
  Field dengan `key` = yang harus di-submit balik; field `text`/`group` tanpa
  `key` cuma tampilan.
- **`submitContract`** — endpoint REST existing/rencana yang dipanggil saat form
  di-submit, plus DTO backend yang jadi acuan validasi field mana yang beneran
  dikirim.

## Yang sudah pasti vs proposal

- **`officer-review-task.json`**: field submit (`decision`) **sudah match** DTO
  existing [`ReviewLoanApplicationRequest`](../../src/main/java/org/project/loslite/dto/ReviewLoanApplicationRequest.java)
  — aman dipakai sebagai kontrak.
- **`disburse-funds-task.json`**: field submit (`disbursementAccountNumber`,
  `disbursementDate`, `disbursementNotes`) **belum ada DTO/kolom backend-nya** —
  Fase 5 di plan cuma bilang "changeStatus + complete user task" tanpa body.
  Ini proposal buat didiskusikan; kalau disepakati, backend perlu nambah
  `DisburseFundsRequest` DTO (dan kolom entity kalau mau disimpan) sebelum FE
  benar-benar ngirim field-field itu.

## Cara pakai buat mock FE

Langsung load salah satu file `.json` di sini ke tool mock kalian (MSW,
json-server, dst) sebagai response `GET /api/workflow/tasks/{taskId}`, lalu
Form Generator tinggal render `formSchema.components` dan prefill pakai
`context`/`variables`.
