package com.example.protaxo.calibration.repository;

import com.example.protaxo.calibration.entity.MasterCard;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MasterCardRepository extends JpaRepository<MasterCard, Long> {

    List<MasterCard> findAllByOrderByCardNumberAsc();

    boolean existsByCardNumberIgnoreCase(String cardNumber);
}
