package com.example.protaxo.calendar;

import java.time.LocalDateTime;

public record AppointmentResponse(
        Long id,
        LocalDateTime startsAt,
        int durationMinutes,
        Long clientId,
        String clientName,
        String phone,
        String vehicle,
        String note
) {

    static AppointmentResponse of(Appointment a) {
        return new AppointmentResponse(a.getId(), a.getStartsAt(), a.getDurationMinutes(), a.getClientId(),
                a.getClientName(), a.getPhone(), a.getVehicle(), a.getNote());
    }
}
