# Planning: Camunda sebagai Workflow Execution Engine (LoanApplication)

> **SUPERSEDED (2026-08-27)**: Diganti oleh pendekatan Workflow Engine terpisah (aplikasi sendiri,
> komunikasi via WebSocket + JWT service-to-service, lihat diskusi di sesi 2026-08-27). Camunda 8
> embedded via `CamundaClient` **tidak jadi dipakai**. File ini dibiarkan sebagai arsip referensi
> desain, bukan rencana aktif.

Status: planning, belum diimplementasi. Konteks kode per 2026-08-24 (setelah restrukturisasi
package flat: model/enums/repository/service/dto/config/controller/workflow).

## Keputusan desain yang sudah difinalisasi

1. **Dual-mode**: endpoint REST manual (`submit`, `/document-verification`, `/scoring`) TETAP
   ada sebagai override manual, berjalan berdampingan dengan orkestrasi Camunda. Risiko: kalau
   dipanggil manual sementara process Zeebe jalan, job worker bisa dapat
   `InvalidLoanStatusTransitionException` (status sudah dipindah duluan) -> job gagal -> incident
   di Operate. Mitigasi: idempotency guard di job worker (cek status current dulu sebelum
   manggil service, skip/complete kalau sudah "lebih maju" dari yang seharusnya dikerjakan job
   ini, jangan rethrow).
2. **MANUAL_REVIEW**: pakai Camunda User Task + Tasklist (bukan REST-only). Officer complete user
   task lewat endpoint baru di app ini yang manggil `CamundaClient`.
3. **DISBURSED**: masuk scope. Ditambah User Task "Disburse Funds" di BPMN setelah APPROVE,
   endpoint baru untuk complete-nya.

## Diagram proses target

```
Start (submit(), variable: loanApplicationId)
  -> Service Task "document-verification"   [JobWorker sudah ada: LoanApplicationWorkflowService]
  -> Service Task "run-scoring"              [JobWorker sudah ada, return "decision"]
  -> Exclusive Gateway (decision)
       - APPROVE ----------------+
       - REJECT  -> End          |
       - MANUAL_REVIEW           |
            -> User Task "Officer Review" (Tasklist)
              -> Exclusive Gateway (reviewDecision)
                   - APPROVE ----+
                   - REJECT -> End
                                 v
                    User Task "Disburse Funds"
                                 v
                                End (DISBURSED)
```

Status entity APPROVED/REJECTED sudah otomatis di-set oleh `ScoringService`/endpoint review
(lewat `LoanApplicationStatusService`, satu-satunya gate perubahan status) - gateway BPMN cuma
soal routing token, bukan yang mengubah status.

## Fase 0 - Keputusan teknis yang perlu difinalisasi sebelum ngoding

- Tambah kolom `zeebeProcessInstanceKey` (Long, nullable) di entity `LoanApplication`. Diisi
  saat `submit()` memulai process instance. Dipakai buat cari user task terbuka lewat
  `CamundaClient.newUserTaskSearchRequest().filter(f -> f.processInstanceKey(key))`, lalu
  `newCompleteUserTaskCommand(userTaskKey)`. `ddl-auto: update` otomatis nambah kolomnya.
- Idempotency guard di job worker (lihat poin 1 di atas) - domain code, ditulis sendiri.

## Fase 1 - BPMN authoring (Camunda Modeler)

- Simpan sebagai `src/main/resources/bpmn/loan-application-process.bpmn`.
- Service Task "document-verification" -> Type = `document-verification` (harus persis sama
  dengan `@JobWorker(type = "document-verification")` yang sudah ada).
- Service Task "run-scoring" -> Type = `run-scoring` (sudah ada juga).
- User Task "Officer Review" & "Disburse Funds" -> User Task **type Zeebe** (bukan Camunda
  Platform 7 style) - tidak butuh `@JobWorker`, dikerjakan manusia lewat Tasklist/endpoint kita.
- Exclusive Gateway pertama: FEEL `=decision = "APPROVE"`, `=decision = "REJECT"`, default/else
  -> MANUAL_REVIEW.
- Exclusive Gateway kedua (setelah Officer Review): `=reviewDecision = "APPROVE"` / else REJECT.

## Fase 2 - Auto-deploy BPMN

`@Deployment(resources = "classpath:bpmn/loan-application-process.bpmn")` di `@Configuration`
baru (misal `CamundaDeploymentConfig` di `config/`). Property `camunda.client.deployment.enabled`
default `true` (dikonfirmasi dari source `CamundaClientDeploymentProperties` di starter
8.8.6) - tidak perlu setting tambahan, cukup annotation ini.

## Fase 3 - Mulai process instance saat submit

Di `LoanApplicationService.submit()`: setelah `changeStatus(..., SUBMITTED, ...)`, inject
`CamundaClient`, panggil
`newCreateInstanceCommand().bpmnProcessId("loan-application-process").latestVersion().variables(Map.of("loanApplicationId", id)).send()`,
simpan `processInstanceKey` hasilnya ke entity.

PENTING: karena Camunda bisa off (`camunda.client.enabled=false`), bagian ini wajib
dibungkus try-catch/dicek toggle-nya - submit tidak boleh gagal total kalau broker mati.

## Fase 4 - Endpoint Officer Review (MANUAL_REVIEW)

`POST /loan-applications/{id}/review` (body: `decision` APPROVE/REJECT):
1. `LoanApplicationStatusService.changeStatus(...)` ke APPROVED/REJECTED (transisi SCORING ->
   APPROVED/REJECTED sudah valid di `LoanStatusTransitionValidator`, tidak perlu ubah state
   machine).
2. Cari user task terbuka lewat `processInstanceKey` tersimpan, complete dengan variable
   `reviewDecision`.

## Fase 5 - Endpoint Disburse Funds

`POST /loan-applications/{id}/disburse`:
1. `changeStatus(..., DISBURSED, ...)` (APPROVED -> DISBURSED sudah ada di map, tidak perlu
   ubah state machine).
2. Complete user task "Disburse Funds" yang sedang menunggu, lewat `processInstanceKey` yang
   sama.

## Fase 6 - Testing & observability

- Nyalakan C8Run lokal, pastikan `CAMUNDA_ENABLED` tidak `false`.
- Deploy otomatis saat startup (Fase 2) -> cek process definition muncul di Operate
  (`localhost:8080`).
- Skenario penuh lewat REST: create -> submit -> (job worker jalan otomatis, cek token di
  Operate) -> kalau MANUAL_REVIEW, panggil `/review` -> kalau APPROVE, panggil `/disburse`.
- Skenario dual-mode: matikan Camunda (`CAMUNDA_ENABLED=false`), jalankan create->submit->
  document-verification->scoring lewat REST manual seperti sekarang - pastikan tetap jalan
  normal tanpa broker.

## Urutan pengerjaan disarankan

Fase 0 (kolom + idempotency guard) -> Fase 1+2 (BPMN + auto-deploy, bisa dites sendiri lewat
Operate tanpa nyentuh kode Java lain) -> Fase 3 (trigger start) -> Fase 4 (review) -> Fase 5
(disburse) -> Fase 6. Tiap fase bisa di-commit terpisah & dites independen.
