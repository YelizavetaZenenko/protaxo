package com.example.protaxo.bank;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Довідник банків для форми контрагента: список і «+ Додати банк» з модального вікна. */
@RestController
@RequestMapping("/api/banks")
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;

    public record BankResponse(Long id, String name, String mfo, String edrpou) {
        static BankResponse of(Bank bank) {
            return new BankResponse(bank.getId(), bank.getName(), bank.getMfo(), bank.getEdrpou());
        }
    }

    @GetMapping
    public List<BankResponse> list() {
        return bankService.findAll().stream().map(BankResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BankResponse create(@Valid @RequestBody BankRequest request) {
        return BankResponse.of(bankService.create(request));
    }
}
