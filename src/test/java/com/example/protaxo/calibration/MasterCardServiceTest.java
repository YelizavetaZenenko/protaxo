package com.example.protaxo.calibration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.calibration.entity.MasterCard;
import com.example.protaxo.calibration.repository.MasterCardRepository;
import com.example.protaxo.calibration.service.MasterCardService;
import com.example.protaxo.common.exception.BusinessRuleException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Редагування й видалення карток майстерні (сторінка «Основна інформація»). */
class MasterCardServiceTest {

    private final MasterCardRepository repository = mock(MasterCardRepository.class);
    private final MasterCardService service = new MasterCardService(repository, mock(AuditLogService.class));

    private MasterCard card() {
        return MasterCard.builder().id(1L).cardNumber("UAW123456789012").holderName("Вишивата Д.О.").build();
    }

    @Test
    void updatesNumberAndHolder() {
        when(repository.findById(1L)).thenReturn(Optional.of(card()));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        var updated = service.update(1L, " UAW999999999999 ", " Петренко І.І. ");
        assertThat(updated.cardNumber()).isEqualTo("UAW999999999999");
        assertThat(updated.holderName()).isEqualTo("Петренко І.І.");
    }

    @Test
    void rejectsNumberOfAnotherCard() {
        when(repository.findById(1L)).thenReturn(Optional.of(card()));
        when(repository.existsByCardNumberIgnoreCaseAndIdNot("UAW000000000000", 1L)).thenReturn(true);
        assertThatThrownBy(() -> service.update(1L, "UAW000000000000", "X"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void deletes() {
        MasterCard c = card();
        when(repository.findById(1L)).thenReturn(Optional.of(c));
        service.delete(1L);
        verify(repository).delete(c);
    }
}
