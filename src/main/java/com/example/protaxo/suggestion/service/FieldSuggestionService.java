package com.example.protaxo.suggestion.service;

import com.example.protaxo.suggestion.entity.FieldSuggestion;
import com.example.protaxo.suggestion.entity.FieldSuggestionCategory;
import com.example.protaxo.suggestion.repository.FieldSuggestionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class FieldSuggestionService {

    private final FieldSuggestionRepository fieldSuggestionRepository;

    @Transactional(readOnly = true)
    public List<String> findValues(FieldSuggestionCategory category) {
        return fieldSuggestionRepository.findByCategoryOrderByValueAsc(category).stream()
                .map(FieldSuggestion::getValue)
                .toList();
    }

    /**
     * Case-insensitive existence check (so "Іваненко" and "іваненко" count as the same suggestion)
     * backed by a DB-level case-insensitive unique index (uq_field_suggestions_category_value_ci,
     * V42) — the exists() check alone still has a TOCTOU gap under concurrent saves of the same
     * brand-new value, so a losing race falls back to a caught constraint violation instead of a
     * duplicate row. This is a best-effort autocomplete convenience, not user-facing data, so a
     * lost race is simply logged and swallowed rather than surfaced as an error.
     *
     * <p>{@code REQUIRES_NEW}: {@code remember()} is always called as a minor side effect inside a
     * much bigger transaction (saving an Invoice or CalibrationProtocol). Postgres aborts the
     * *entire* transaction on any statement error, including a caught one — without a separate
     * transaction here, a lost race on this unrelated suggestion insert would poison the enclosing
     * save and fail it too, for a reason that has nothing to do with what the user was actually
     * doing.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void remember(FieldSuggestionCategory category, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String trimmed = value.trim();
        if (fieldSuggestionRepository.existsByCategoryAndValueIgnoreCase(category, trimmed)) {
            return;
        }
        try {
            fieldSuggestionRepository.saveAndFlush(FieldSuggestion.builder()
                    .category(category)
                    .value(trimmed)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            log.debug("Suggestion '{}' ({}) already saved by a concurrent request, skipping", trimmed, category);
        }
    }
}
