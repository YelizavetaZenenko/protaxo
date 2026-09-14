-- Adds every field from the official "ПРОТОКОЛ ПЕРЕВІРКИ ТА АДАПТАЦІЇ ТАХОГРАФА" form (see
-- samples/ПРОТОКОЛ.pdf) that wasn't captured in the first CalibrationProtocol iteration:
-- vehicle VRN/VIN, tachograph serial number/manufacture year, inspection reason, check method,
-- the full п.8 results table (mileage, tire size/pressure, W/K/L, path/speed/time deviations,
-- speed limiter, event registrations), and the п.10 executor (посада/ПІБ). All free text and
-- nullable — this is a paper-form transcription tool, not a source of truth requiring strict
-- numeric types, and every field on the real form can legitimately be left blank.

ALTER TABLE calibration_protocols
    ADD COLUMN vehicle_vrn VARCHAR(50),
    ADD COLUMN vehicle_vin VARCHAR(50),
    ADD COLUMN tachograph_serial_number VARCHAR(100),
    ADD COLUMN tachograph_manufacture_year VARCHAR(20),
    ADD COLUMN inspection_reason VARCHAR(255),
    ADD COLUMN check_method VARCHAR(255),
    ADD COLUMN mileage_before VARCHAR(50),
    ADD COLUMN mileage_after VARCHAR(50),
    ADD COLUMN tire_size VARCHAR(50),
    ADD COLUMN tire_pressure VARCHAR(50),
    ADD COLUMN tire_circumference_l VARCHAR(50),
    ADD COLUMN coefficient_w VARCHAR(50),
    ADD COLUMN constant_k VARCHAR(50),
    ADD COLUMN path_deviation_after_install VARCHAR(50),
    ADD COLUMN path_deviation_in_service VARCHAR(50),
    ADD COLUMN speed_deviation_after_install VARCHAR(50),
    ADD COLUMN speed_deviation_in_service VARCHAR(50),
    ADD COLUMN time_deviation_after_install VARCHAR(50),
    ADD COLUMN time_deviation_in_service VARCHAR(50),
    ADD COLUMN speed_limiter_value VARCHAR(50),
    ADD COLUMN cover_opening_registered VARCHAR(50),
    ADD COLUMN power_cutoff_registered VARCHAR(50),
    ADD COLUMN pulse_sensor_interruption_registered VARCHAR(50),
    ADD COLUMN executor_position VARCHAR(255),
    ADD COLUMN executor_name VARCHAR(255);
