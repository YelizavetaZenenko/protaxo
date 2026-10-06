package com.example.protaxo.calibration.dto;

import java.util.Collection;

/**
 * Which sticker a protocol prints — never chosen by hand, always derived from the calibration
 * service on the linked наряд-заказ (see [[Print Agent]] "Наклейки Smart 1 / Smart 2"). Analogue,
 * digital and unlinked protocols keep the original label with the QR code.
 */
public enum CalibrationLabelType {
    STANDARD,
    SMART_1,
    SMART_2;

    private static final String CALIBRATION_SERVICE_PREFIX = "Калібрування тахографа";

    /**
     * Smart 2 wins if both are on one наряд-заказ. A bare "...-Smart" (invoices from before the
     * service was split in two) counts as Smart 1 — that's what the old service was renamed to.
     */
    public static CalibrationLabelType fromServiceNames(Collection<String> itemNames) {
        CalibrationLabelType result = STANDARD;
        for (String name : itemNames) {
            if (name == null || !name.startsWith(CALIBRATION_SERVICE_PREFIX)) {
                continue;
            }
            if (name.contains("Smart 2")) {
                return SMART_2;
            }
            if (name.contains("Smart")) {
                result = SMART_1;
            }
        }
        return result;
    }

    public boolean isSmart() {
        return this != STANDARD;
    }
}
