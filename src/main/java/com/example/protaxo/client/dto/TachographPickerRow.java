package com.example.protaxo.client.dto;

import java.time.LocalDate;

/**
 * Backs the "Тахограф" picker on the calibration protocol form — see
 * ClientLookupController#tachographs and calibration-protocols/form.html. Carries the raw
 * manufacturer/model/serialNumber/productionDate (not just a display label) so the form's JS can
 * auto-fill those fields directly without re-parsing a formatted string.
 */
public record TachographPickerRow(
        Long id,
        String manufacturer,
        String model,
        String serialNumber,
        LocalDate productionDate,
        String vehicleLabel
) {
}
