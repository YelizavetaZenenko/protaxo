package com.example.protaxo.suggestion.repository;

import com.example.protaxo.suggestion.entity.FieldSuggestion;
import com.example.protaxo.suggestion.entity.FieldSuggestionCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FieldSuggestionRepository extends JpaRepository<FieldSuggestion, Long> {

    List<FieldSuggestion> findByCategoryOrderByValueAsc(FieldSuggestionCategory category);

    boolean existsByCategoryAndValueIgnoreCase(FieldSuggestionCategory category, String value);
}
