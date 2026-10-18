package com.spa.operations;
import org.springframework.data.jpa.repository.*;
import java.time.LocalDate;
import java.util.*;
public interface AttendanceRepository extends JpaRepository<Attendance,UUID> {
 @EntityGraph(attributePaths="staff") List<Attendance> findByDayOrderByStaffNameAsc(LocalDate day);
 Optional<Attendance> findByStaffIdAndDay(UUID staffId,LocalDate day);
}
