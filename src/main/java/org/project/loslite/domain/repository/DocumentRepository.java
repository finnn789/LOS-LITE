package org.project.loslite.domain.repository;

import org.project.loslite.domain.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByLoanApplicationId(Long loanApplicationId);
}
