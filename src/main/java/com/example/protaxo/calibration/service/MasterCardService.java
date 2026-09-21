package com.example.protaxo.calibration.service;

import com.example.protaxo.calibration.dto.MasterCardResponse;
import com.example.protaxo.calibration.entity.MasterCard;
import com.example.protaxo.calibration.repository.MasterCardRepository;
import com.example.protaxo.common.exception.BusinessRuleException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MasterCardService {

    private final MasterCardRepository masterCardRepository;

    @Transactional(readOnly = true)
    public List<MasterCardResponse> findAll() {
        return masterCardRepository.findAllByOrderByCardNumberAsc().stream()
                .map(c -> new MasterCardResponse(c.getId(), c.getCardNumber(), c.getHolderName()))
                .toList();
    }

    public MasterCardResponse create(String cardNumber, String holderName) {
        String trimmed = cardNumber.trim();
        if (masterCardRepository.existsByCardNumberIgnoreCase(trimmed)) {
            throw new BusinessRuleException("Ця картка вже є у довіднику");
        }
        // The existsBy check above has a TOCTOU gap under concurrent requests (two inserts for the
        // same card number racing past it before either commits) — saveAndFlush forces the INSERT
        // (and the DB's real unique constraint) to fire here, so the race still surfaces as this
        // friendly message instead of a raw DataIntegrityViolationException/500 further up the stack.
        try {
            MasterCard saved = masterCardRepository.saveAndFlush(MasterCard.builder()
                    .cardNumber(trimmed)
                    .holderName(holderName.trim())
                    .build());
            return new MasterCardResponse(saved.getId(), saved.getCardNumber(), saved.getHolderName());
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("Ця картка вже є у довіднику");
        }
    }
}
