package com.example.protaxo.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.common.exception.BusinessRuleException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Додавання банку з модального вікна: без повторів МФО й назви. */
class BankServiceTest {

    private final BankRepository repository = mock(BankRepository.class);
    private final BankService service = new BankService(repository, mock(AuditLogService.class));

    @Test
    void rejectsDuplicateMfo() {
        when(repository.findFirstByMfo("334851")).thenReturn(Optional.of(Bank.builder().name("АТ «ПУМБ»").mfo("334851").build()));
        assertThatThrownBy(() -> service.create(new BankRequest("Новий банк", "334851", null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("АТ «ПУМБ»");
    }

    @Test
    void rejectsDuplicateNameIgnoringCase() {
        when(repository.findFirstByMfo(any())).thenReturn(Optional.empty());
        when(repository.findFirstByNameIgnoreCase("ат «пумб»")).thenReturn(Optional.of(Bank.builder().name("АТ «ПУМБ»").build()));
        assertThatThrownBy(() -> service.create(new BankRequest("ат «пумб»", null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void savesUserBankWithTrimmedName() {
        when(repository.findFirstByMfo(any())).thenReturn(Optional.empty());
        when(repository.findFirstByNameIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> {
            Bank bank = inv.getArgument(0);
            bank.setId(99L);
            return bank;
        });
        Bank saved = service.create(new BankRequest("  АТ   «Новий Банк» ", "123456", ""));
        assertThat(saved.getName()).isEqualTo("АТ «Новий Банк»");
        assertThat(saved.getMfo()).isEqualTo("123456");
        assertThat(saved.getEdrpou()).isNull();
        assertThat(saved.getSource()).isEqualTo("USER");
    }
}
