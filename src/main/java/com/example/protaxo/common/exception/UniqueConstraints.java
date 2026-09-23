package com.example.protaxo.common.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Tells a violation of one specific unique index apart from every other integrity error
 * (value too long, NOT NULL, foreign key...). Services map only the former to a friendly
 * "already exists" message; anything else must surface as the real error instead of being
 * mislabeled as a duplicate.
 */
public final class UniqueConstraints {

    public static final String CLIENT_EDRPOU = "uq_clients_edrpou_active";
    public static final String VEHICLE_VIN = "uq_vehicles_vin_active";
    public static final String TACHOGRAPH_SERIAL_NUMBER = "uq_tachographs_serial_number_active";

    private UniqueConstraints() {
    }

    public static boolean isViolationOf(DataIntegrityViolationException ex, String constraintName) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve
                    && constraintName.equalsIgnoreCase(cve.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
