package com.example.protaxo.stockrevision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.protaxo.catalog.entity.CatalogItem;
import com.example.protaxo.catalog.entity.CatalogItemType;
import com.example.protaxo.catalog.repository.CatalogItemRepository;
import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.stockrevision.dto.StockRevisionForm;
import com.example.protaxo.stockrevision.dto.StockRevisionView;
import com.example.protaxo.stockrevision.entity.StockRevisionStatus;
import com.example.protaxo.stockrevision.service.StockRevisionService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** Ревізія складу (docs/Ревізія складу.md). Кожен тест відкочується. */
@SpringBootTest
@Transactional
class StockRevisionServiceTest {

    @Autowired StockRevisionService revisionService;
    @Autowired CatalogItemRepository catalogItemRepository;

    CatalogItem oil;
    CatalogItem filter;
    CatalogItem service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin@protaxo.local", null, List.of(new SimpleGrantedAuthority("ROLE_ACCOUNTANT"))));
        oil = material("Олива " + UUID.randomUUID(), "10.000", "250.00");
        filter = material("Фільтр " + UUID.randomUUID(), "4.000", "180.00");
        service = catalogItemRepository.save(CatalogItem.builder().type(CatalogItemType.SERVICE)
                .name("Послуга " + UUID.randomUUID()).basePrice(new BigDecimal("500")).build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void draftSnapshotsOnlyStockedMaterials() {
        StockRevisionView draft = revisionService.findById(revisionService.createDraft());

        assertThat(draft.status()).isEqualTo(StockRevisionStatus.DRAFT);
        assertThat(line(draft, oil).expectedQuantity()).isEqualByComparingTo("10");
        assertThat(draft.lines()).noneMatch(l -> l.catalogItemId().equals(service.getId()));
    }

    @Test
    void onlyOneDraftAtATime() {
        assertThat(revisionService.createDraft()).isEqualTo(revisionService.createDraft());
    }

    @Test
    void completingSetsActualStockAndRecordsDifferences() {
        Long id = revisionService.createDraft();
        StockRevisionView draft = revisionService.findById(id);
        StockRevisionForm form = form(draft, oil, "8.5", "розлито");
        // Фільтр не рахували — його залишок не має змінитись.

        revisionService.complete(id, form);

        StockRevisionView done = revisionService.findById(id);
        assertThat(done.status()).isEqualTo(StockRevisionStatus.COMPLETED);
        assertThat(catalogItemRepository.findById(oil.getId()).orElseThrow().getStockQuantity()).isEqualByComparingTo("8.5");
        assertThat(catalogItemRepository.findById(filter.getId()).orElseThrow().getStockQuantity()).isEqualByComparingTo("4");
        assertThat(line(done, oil).difference()).isEqualByComparingTo("-1.5");
        assertThat(line(done, oil).differenceValue()).isEqualByComparingTo("-375");
        assertThat(done.shortageValue()).isGreaterThanOrEqualTo(new BigDecimal("375"));
    }

    @Test
    void expectedQuantityIsRetakenAtCompletion() {
        Long id = revisionService.createDraft();
        StockRevisionForm form = form(revisionService.findById(id), oil, "7", null);
        // Між створенням чернетки й проведенням продали 2 одиниці.
        CatalogItem current = catalogItemRepository.findById(oil.getId()).orElseThrow();
        current.setStockQuantity(new BigDecimal("8"));
        catalogItemRepository.save(current);

        revisionService.complete(id, form);

        assertThat(line(revisionService.findById(id), oil).expectedQuantity()).isEqualByComparingTo("8");
        assertThat(line(revisionService.findById(id), oil).difference()).isEqualByComparingTo("-1");
    }

    @Test
    void completedRevisionCannotBeChanged() {
        Long id = revisionService.createDraft();
        StockRevisionForm form = form(revisionService.findById(id), oil, "10", null);
        revisionService.complete(id, form);

        assertThatThrownBy(() -> revisionService.saveDraft(id, form)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void negativeActualQuantityIsRejected() {
        Long id = revisionService.createDraft();
        StockRevisionForm form = form(revisionService.findById(id), oil, "-1", null);

        assertThatThrownBy(() -> revisionService.saveDraft(id, form)).isInstanceOf(BusinessRuleException.class);
    }

    // ------------------------------------------------------------------ helpers

    private CatalogItem material(String name, String stock, String price) {
        return catalogItemRepository.save(CatalogItem.builder().type(CatalogItemType.MATERIAL).name(name)
                .basePrice(new BigDecimal(price)).stockQuantity(new BigDecimal(stock)).build());
    }

    private StockRevisionView.Line line(StockRevisionView revision, CatalogItem item) {
        return revision.lines().stream().filter(l -> l.catalogItemId().equals(item.getId())).findFirst().orElseThrow();
    }

    private StockRevisionForm form(StockRevisionView revision, CatalogItem item, String actual, String comment) {
        StockRevisionForm form = new StockRevisionForm();
        StockRevisionForm.Line line = new StockRevisionForm.Line();
        line.setId(line(revision, item).id());
        line.setActualQuantity(new BigDecimal(actual));
        line.setComment(comment);
        form.getLines().add(line);
        return form;
    }
}
