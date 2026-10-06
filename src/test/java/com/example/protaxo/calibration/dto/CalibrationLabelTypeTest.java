package com.example.protaxo.calibration.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CalibrationLabelTypeTest {

    @Test
    void pickedFromTheCalibrationServiceOnTheInvoice() {
        assertThat(CalibrationLabelType.fromServiceNames(List.of("Калібрування тахографа-Аналоговий")))
                .isEqualTo(CalibrationLabelType.STANDARD);
        assertThat(CalibrationLabelType.fromServiceNames(List.of("Калібрування тахографа-Цифровий")))
                .isEqualTo(CalibrationLabelType.STANDARD);
        assertThat(CalibrationLabelType.fromServiceNames(List.of("Пломба", "Калібрування тахографа-Smart 1")))
                .isEqualTo(CalibrationLabelType.SMART_1);
        assertThat(CalibrationLabelType.fromServiceNames(List.of("Калібрування тахографа-Smart 2", "Пломба")))
                .isEqualTo(CalibrationLabelType.SMART_2);
    }

    @Test
    void oldSingleSmartServiceCountsAsSmart1() {
        assertThat(CalibrationLabelType.fromServiceNames(List.of("Калібрування тахографа-Smart")))
                .isEqualTo(CalibrationLabelType.SMART_1);
    }

    @Test
    void noCalibrationServiceMeansStandardLabel() {
        assertThat(CalibrationLabelType.fromServiceNames(List.of("Діагностика Smart тахографа")))
                .isEqualTo(CalibrationLabelType.STANDARD);
        assertThat(CalibrationLabelType.fromServiceNames(List.of())).isEqualTo(CalibrationLabelType.STANDARD);
    }
}
