package com.example.protaxo.audit.entity;

/** Назви до 20 символів — колонка audit_log.action VARCHAR(20). */
public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    LOGIN,
    LOGIN_FAILED,
    LOGOUT,
    /** Сесія згасла сама (закрито всі вкладки / неактивність) — SessionEndAuditJob. */
    SESSION_ENDED,
    /** Відкрито/завантажено файл (PDF, CSV, вкладення) — ServerActivityAuditFilter. */
    FILE_DOWNLOAD,
    /** Наклейку протоколу надіслано на друк (Print Agent). */
    PRINT,
    PRINTER_ONLINE,
    PRINTER_OFFLINE,
    /** 403: немає прав або застарілий CSRF-токен (форма з давно відкритої сторінки). */
    ACCESS_DENIED,
    /** Необроблений виняток або відповідь 5xx. */
    SERVER_ERROR,
    SERVER_START,
    SERVER_STOP
}
