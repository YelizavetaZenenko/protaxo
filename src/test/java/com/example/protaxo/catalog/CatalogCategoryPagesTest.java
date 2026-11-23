package com.example.protaxo.catalog;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.catalog.dto.CatalogItemRequest;
import com.example.protaxo.catalog.dto.CatalogItemResponse;
import com.example.protaxo.catalog.entity.CatalogCategory;
import com.example.protaxo.catalog.entity.CatalogItemType;
import com.example.protaxo.catalog.entity.CatalogUnit;
import com.example.protaxo.catalog.service.CatalogCategoryService;
import com.example.protaxo.catalog.service.CatalogItemService;
import com.example.protaxo.catalog.web.CatalogItemPageController;
import com.example.protaxo.common.vat.VatRate;
import com.example.protaxo.maintenance.service.MaintenanceModeService;
import com.example.protaxo.security.config.SecurityConfig;
import com.example.protaxo.security.listener.AuthAuditRecorder;
import com.example.protaxo.security.repository.UserRepository;
import com.example.protaxo.security.service.DeveloperAccounts;
import com.example.protaxo.security.service.LoginAttemptService;
import com.example.protaxo.security.service.PermissionService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Підкатегорії товарів у каталозі: вкладки з кількістю, групи при «Усі», фільтр, форма з
 * «новою категорією». Без бази — сервіси замінені моками (docs/Каталог.md).
 */
@WebMvcTest(CatalogItemPageController.class)
@Import(SecurityConfig.class)
class CatalogCategoryPagesTest {

    @Autowired MockMvc mvc;

    @MockitoBean CatalogItemService catalogItemService;
    @MockitoBean CatalogCategoryService catalogCategoryService;
    @MockitoBean AuditLogService auditLogService;
    @MockitoBean AuthAuditRecorder authAuditRecorder;
    @MockitoBean LoginAttemptService loginAttemptService;
    @MockitoBean PermissionService permissionService;
    @MockitoBean UserRepository userRepository;
    @MockitoBean MaintenanceModeService maintenanceModeService;
    @MockitoBean DeveloperAccounts developerAccounts;
    /** @EnableJpaAuditing на ProtaxoApplication вимагає JPA-контексту, якого в зрізі WebMvc немає. */
    @MockitoBean JpaMetamodelMappingContext jpaMappingContext;

    @BeforeEach
    void setUp() {
        Map<Long, String> names = new LinkedHashMap<>();
        names.put(1L, "Датчики");
        names.put(2L, "Адаптери");
        names.put(3L, "Тахографи");
        when(catalogCategoryService.names()).thenReturn(names);
        when(catalogCategoryService.findAll()).thenReturn(List.of(
                CatalogCategory.builder().id(1L).name("Датчики").build(),
                CatalogCategory.builder().id(2L).name("Адаптери").build(),
                CatalogCategory.builder().id(3L).name("Тахографи").build()));
        when(catalogItemService.findAll()).thenReturn(List.of(
                item(10L, CatalogItemType.MATERIAL, "Датчик 2159", 1L),
                item(11L, CatalogItemType.MATERIAL, "Перехідник Isuzu 1318", 2L),
                item(12L, CatalogItemType.MATERIAL, "Щось без категорії", null),
                item(13L, CatalogItemType.SERVICE, "Калібрування", null)));
    }

    private static CatalogItemResponse item(Long id, CatalogItemType type, String name, Long categoryId) {
        return new CatalogItemResponse(id, type, name, new BigDecimal("100.00"), null, VatRate.VAT_20, null,
                CatalogUnit.PIECE, categoryId);
    }

    @Test
    void allGoodsAreGroupedUnderCategoryHeadings() throws Exception {
        mvc.perform(get("/catalog-items").with(user("a@b.c").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("category-chip"),
                        containsString("category-group-row"),
                        containsString("Ціна з ПДВ 20%"),
                        containsString("120.00"),
                        containsString("Датчик 2159"),
                        containsString("Перехідник Isuzu 1318"),
                        containsString("Без категорії"),
                        not(containsString("Калібрування")))));
    }

    @Test
    void chipFiltersOneCategory() throws Exception {
        mvc.perform(get("/catalog-items").param("category", "2").with(user("a@b.c").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Перехідник Isuzu 1318"),
                        not(containsString("Датчик 2159")))));
        mvc.perform(get("/catalog-items").param("category", "none").with(user("a@b.c").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Щось без категорії"),
                        not(containsString("Перехідник Isuzu 1318")))));
    }

    @Test
    void servicesTabIsAPlainList() throws Exception {
        mvc.perform(get("/catalog-items").param("activeType", "SERVICE").with(user("a@b.c").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Калібрування"),
                        not(containsString("category-group-row")))));
    }

    @Test
    void formOffersCategoriesAndNewCategoryField() throws Exception {
        mvc.perform(get("/catalog-items/new").param("categoryId", "1").with(user("a@b.c").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("name=\"categoryId\""),
                        containsString("newCategoryName"),
                        containsString("Тахографи"))));
    }

    @Test
    void newCategoryNameIsCreatedAndUsed() throws Exception {
        when(catalogCategoryService.findOrCreate(" Антени ")).thenReturn(7L);
        mvc.perform(post("/catalog-items").with(user("a@b.c").roles("ADMIN")).with(csrf())
                        .param("type", "MATERIAL").param("name", "Антена GNSS VDO").param("basePrice", "0")
                        .param("vatRate", "VAT_20").param("unit", "PIECE")
                        .param("categoryId", "1").param("newCategoryName", " Антени "))
                .andExpect(status().is3xxRedirection());
        ArgumentCaptor<CatalogItemRequest> request = ArgumentCaptor.forClass(CatalogItemRequest.class);
        verify(catalogItemService).create(request.capture());
        org.assertj.core.api.Assertions.assertThat(request.getValue().categoryId()).isEqualTo(7L);
    }
}
