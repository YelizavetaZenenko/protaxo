package com.example.protaxo.stockrevision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.protaxo.catalog.entity.CatalogItem;
import com.example.protaxo.catalog.entity.CatalogItemType;
import com.example.protaxo.catalog.repository.CatalogItemRepository;
import com.example.protaxo.stockrevision.dto.StockRevisionForm;
import com.example.protaxo.stockrevision.service.StockRevisionService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Сторінки ревізії рендеряться без помилок шаблонів; доступ — бухгалтер так, майстер ні. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StockRevisionPagesTest {

    @Autowired MockMvc mvc;
    @Autowired StockRevisionService revisionService;
    @Autowired CatalogItemRepository catalogItemRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void draftAndCompletedPagesRenderForAccountant() throws Exception {
        MockHttpSession session = sessionFor("ROLE_ACCOUNTANT");
        SecurityContextHolder.setContext(securityContext("ROLE_ACCOUNTANT"));
        CatalogItem item = catalogItemRepository.save(CatalogItem.builder().type(CatalogItemType.MATERIAL)
                .name("Товар " + UUID.randomUUID()).basePrice(new BigDecimal("100"))
                .stockQuantity(new BigDecimal("3")).build());
        Long id = revisionService.createDraft();

        mvc.perform(get("/stock-revisions").session(session)).andExpect(status().isOk());
        String draftHtml = mvc.perform(get("/stock-revisions/" + id).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(draftHtml).contains("Провести ревізію").contains(item.getName());

        StockRevisionForm form = new StockRevisionForm();
        StockRevisionForm.Line line = new StockRevisionForm.Line();
        line.setId(revisionService.findById(id).lines().stream()
                .filter(l -> l.catalogItemId().equals(item.getId())).findFirst().orElseThrow().id());
        line.setActualQuantity(new BigDecimal("2"));
        form.getLines().add(line);
        revisionService.complete(id, form);

        String doneHtml = mvc.perform(get("/stock-revisions/" + id).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(doneHtml).contains("Нестача").doesNotContain("Провести ревізію");
        mvc.perform(get("/catalog-items").session(session)).andExpect(status().isOk());
    }

    @Test
    void masterCannotOpenRevisions() throws Exception {
        mvc.perform(get("/stock-revisions").session(sessionFor("ROLE_MASTER"))).andExpect(status().isForbidden());
    }

    private MockHttpSession sessionFor(String role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext(role));
        return session;
    }

    private SecurityContext securityContext(String role) {
        return new SecurityContextImpl(new UsernamePasswordAuthenticationToken(
                "admin@protaxo.local", null, List.of(new SimpleGrantedAuthority(role))));
    }
}
