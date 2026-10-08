package com.example.protaxo.worker.dto;

import java.time.LocalDate;

/**
 * Full worker card for the «Користувачі» page (CAN_MANAGE_USERS only). The picker API keeps
 * returning {@link RepairWorkerResponse} — name and position only — because it is open to every
 * signed-in user, and home address/emergency contact are not theirs to see.
 */
public record RepairWorkerDetails(
        Long id,
        String fullName,
        String position,
        String phone,
        String email,
        String address,
        LocalDate hireDate,
        String workshopCardNumber,
        LocalDate workshopCardValidUntil,
        String emergencyContact,
        String notes
) {
}
