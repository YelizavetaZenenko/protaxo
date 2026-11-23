package com.example.protaxo.calendar;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record AppointmentRequest(
        @NotNull(message = "Вкажіть дату й час") LocalDateTime startsAt,
        @Min(value = 5, message = "Тривалість — від 5 хвилин") @Max(value = 720, message = "Тривалість — до 12 годин")
        int durationMinutes,
        Long clientId,
        @NotBlank(message = "Вкажіть клієнта") @Size(max = 255) String clientName,
        @Size(max = 32) String phone,
        @Size(max = 255) String vehicle,
        @Size(max = 2000, message = "Примітка — до 2000 символів") String note
) {
}
