package org.project.loslite.dto;

import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.IdMapping;
import org.project.loslite.model.Applicant;

import java.time.LocalDate;

/**
 * Proyeksi untuk DAFTAR applicant. Blaze menyusun SELECT hanya untuk kolom
 * yang disebut di sini - nik dan nikHash tidak pernah ikut diambil dari DB.
 */
@EntityView(Applicant.class)
public interface ApplicantSummaryView {

    @IdMapping
    Long getId();

    String getFullName();

    LocalDate getDateOfBirth();

    String getPhoneNumber();

    String getEmail();

    String getAddress();
}