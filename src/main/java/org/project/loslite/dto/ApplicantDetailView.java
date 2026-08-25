package org.project.loslite.dto;

import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.IdMapping;
import org.project.loslite.model.Applicant;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Proyeksi untuk DETAIL satu applicant - lebih lengkap dari Summary,
 * ditambah createdAt/updatedAt yang diwarisi dari BaseEntity.
 */
@EntityView(Applicant.class)
public interface ApplicantDetailView {

    @IdMapping
    Long getId();

    String getFullName();

    LocalDate getDateOfBirth();

    String getPhoneNumber();

    String getEmail();

    String getAddress();

    Instant getCreatedAt();

    Instant getUpdatedAt();
}