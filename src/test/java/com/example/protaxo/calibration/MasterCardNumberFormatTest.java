package com.example.protaxo.calibration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.protaxo.calibration.dto.MasterCardRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

/** Номер картки майстерні: реальний UAM000000EOS3000 і старий формат проходять, сміття — ні. */
class MasterCardNumberFormatTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean valid(String number) {
        return validator.validate(new MasterCardRequest(number, "ФОП Вишивата Д. О.")).isEmpty();
    }

    @Test
    void acceptsRealWorkshopCardNumbers() {
        assertThat(valid("UAM000000EOS3000")).isTrue();
        assertThat(valid("UAW123456789012")).isTrue();
    }

    @Test
    void rejectsSpacesLowercaseAndTooShort() {
        assertThat(valid("UAM 000000 EOS3000")).isFalse();
        assertThat(valid("uam000000eos3000")).isFalse();
        assertThat(valid("UAM123")).isFalse();
    }
}
