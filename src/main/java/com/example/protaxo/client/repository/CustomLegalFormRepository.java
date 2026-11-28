package com.example.protaxo.client.repository;

import com.example.protaxo.client.entity.CustomLegalForm;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomLegalFormRepository extends JpaRepository<CustomLegalForm, Long> {

    List<CustomLegalForm> findAllByOrderByShortLabelAsc();

    Optional<CustomLegalForm> findFirstByShortLabelIgnoreCase(String shortLabel);
}
