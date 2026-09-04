package org.project.loslite.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Body generik {@code {"id": ...}} - dipakai endpoint LoanApplication yang tidak butuh
 * input lain selain id pengajuannya sendiri (submit, document-verification, scoring,
 * disburse). Reuse SATU record (bukan bikin record terpisah per endpoint yang isinya
 * sama persis) supaya tidak duplikasi - lihat {@link ReviewLoanApplicationRequest} untuk
 * endpoint yang butuh field tambahan selain id.
 */
public record LoanApplicationIdRequest(

        @NotNull(message = "id wajib diisi")
        Long id
) {
}
