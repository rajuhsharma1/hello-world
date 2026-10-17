package com.spa.booking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface OutboxRepository extends JpaRepository<Outbox,UUID> { List<Outbox> findTop50BySentFalseOrderById(); }
