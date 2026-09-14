package com.example.protaxo.audit.service;

/** A short human-readable label for an audited entity, plus a link to its view page when one exists. */
public record AuditEntityRef(String label, String url) {
}
