package com.spa.operations;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.time.LocalDate;
import java.util.UUID;
import java.util.Optional;
public interface SaleRepository extends JpaRepository<Sale,UUID> {
 @Override @EntityGraph(attributePaths="staff") Optional<Sale> findById(UUID id);
 @EntityGraph(attributePaths="staff") Page<Sale> findByDay(LocalDate day,Pageable page);
}
