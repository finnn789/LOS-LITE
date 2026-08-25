package org.project.loslite.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Penampung query string untuk GET /applicants.
 * Contoh: /applicants?keyword=budi&page=2&perPage=5
 */
@Getter
@Setter
public class ListApplicantRequest {

    private String keyword;
    private Integer page;
    private Integer perPage;
}