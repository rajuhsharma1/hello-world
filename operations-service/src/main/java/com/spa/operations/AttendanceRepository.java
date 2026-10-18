package com.spa.operations;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {
    @EntityGraph(attributePaths = "staff")
    List<Attendance> findByDayOrderByStaffNameAsc(LocalDate day);

    Optional<Attendance> findByStaffIdAndDay(UUID staffId, LocalDate day);
}
