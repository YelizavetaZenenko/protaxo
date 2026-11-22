package com.example.protaxo.audit.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ServerActivityAuditFilterTest {

    @Test
    void readsFileNameFromContentDisposition() {
        assertThat(ServerActivityAuditFilter.fileName("inline; filename=\"bill-42.pdf\"")).isEqualTo("bill-42.pdf");
        assertThat(ServerActivityAuditFilter.fileName("attachment; filename=\"a.pdf\"; filename*=UTF-8''%D0%B0%D0%BA%D1%82.pdf"))
                .isEqualTo("акт.pdf");
        assertThat(ServerActivityAuditFilter.fileName(null)).isNull();
    }
}
