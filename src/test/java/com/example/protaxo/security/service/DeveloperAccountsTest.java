package com.example.protaxo.security.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeveloperAccountsTest {

    @Test
    void matchesListedEmailsIgnoringCaseAndSpaces() {
        DeveloperAccounts accounts = new DeveloperAccounts(" Dev@Example.com , second@example.com ");

        assertThat(accounts.isDeveloper("dev@example.com")).isTrue();
        assertThat(accounts.isDeveloper("SECOND@example.com")).isTrue();
        assertThat(accounts.isDeveloper("admin@protaxo.local")).isFalse();
        assertThat(accounts.isDeveloper(null)).isFalse();
    }

    @Test
    void emptySettingMeansNoDevelopers() {
        DeveloperAccounts accounts = new DeveloperAccounts("");

        assertThat(accounts.isDeveloper("dev@example.com")).isFalse();
        assertThat(accounts.isDeveloper("")).isFalse();
    }
}
