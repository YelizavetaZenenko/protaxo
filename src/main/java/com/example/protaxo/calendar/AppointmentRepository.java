package com.example.protaxo.calendar;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(LocalDateTime from, LocalDateTime to);
}
