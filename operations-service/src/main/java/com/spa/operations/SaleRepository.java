package com.spa.operations;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {
    @Override
    @EntityGraph(attributePaths = "staff")
    Optional<Sale> findById(UUID id);

    @EntityGraph(attributePaths = "staff")
    Page<Sale> findByDay(LocalDate day, Pageable page);
}
