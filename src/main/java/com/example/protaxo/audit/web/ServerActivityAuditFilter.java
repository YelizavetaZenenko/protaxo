package com.example.protaxo.audit.web;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Дії на сервері, які не проходять через сервіси з власним аудитом (docs/Журнал дій (Audit Log).md):
 * <ul>
 *   <li>відкриті/завантажені файли — PDF рахунку, акта, договору, протоколу, CSV, вкладення;</li>
 *   <li>помилки сервера — необроблений виняток або відповідь 5xx (людина бачила сторінку помилки);</li>
 *   <li>остання відкрита сторінка й IP — у сесію, щоб запис «Сесію завершено» показав, де людина
 *       була (SessionEndAuditJob).</li>
 * </ul>
 * Як {@code @Component} фільтр стоїть усередині Spring Security, тож користувач уже відомий.
 * Відмови в доступі (403) пише {@code SecurityConfig} — до цього фільтра такі запити не доходять.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ServerActivityAuditFilter extends OncePerRequestFilter {

    public static final String LAST_PAGE_ATTR = "protaxo.audit.lastPage";
    public static final String LAST_IP_ATTR = "protaxo.audit.lastIp";

    /** /invoices/42/bill-pdf → Invoice #42 — щоб у журналі було посилання й «Історія» елемента. */
    private static final Map<String, String> ENTITY_BY_SECTION = Map.of(
            "invoices", "Invoice",
            "contracts", "Contract",
            "calibration-protocols", "CalibrationProtocol",
            "clients", "Client",
            "vehicles", "Vehicle",
            "tachographs", "Tachograph");
    private static final Pattern ENTITY_PATH = Pattern.compile("^/([a-z-]+)/(\\d+)(/.*)?$");
    private static final Pattern ATTACHMENT_PATH = Pattern.compile("^/finance/attachments/(\\d+)$");
    private static final Pattern FILENAME = Pattern.compile("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE);

    private final AuditLogService auditLogService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        return path.startsWith("/session/") || path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")
                || path.startsWith("/ws/") || path.startsWith("/favicon") || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        rememberLastPage(request);
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            recordError(request, 500, e);
            throw e;
        }
        int status = response.getStatus();
        if (status >= 500) {
            Object handled = request.getAttribute(DispatcherServlet.EXCEPTION_ATTRIBUTE);
            recordError(request, status, handled instanceof Throwable t ? t : null);
        } else if (status == 200 && isFile(response)) {
            recordDownload(request, response);
        }
    }

    private void rememberLastPage(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod())) {
            return;
        }
        String accept = request.getHeader("Accept");
        HttpSession session = request.getSession(false);
        if (session == null || accept == null || !accept.contains("text/html")) {
            return;
        }
        String page = pathWithQuery(request);
        if (!page.equals(session.getAttribute(LAST_PAGE_ATTR))) {
            session.setAttribute(LAST_PAGE_ATTR, truncate(page, 300));
        }
        String ip = clientIp(request);
        if (ip != null && !ip.equals(session.getAttribute(LAST_IP_ATTR))) {
            session.setAttribute(LAST_IP_ATTR, ip);
        }
    }

    private static boolean isFile(HttpServletResponse response) {
        String type = response.getContentType();
        if (response.getHeader("Content-Disposition") != null) {
            return true;
        }
        return type != null && (type.startsWith("application/pdf") || type.startsWith("text/csv"));
    }

    private void recordDownload(HttpServletRequest request, HttpServletResponse response) {
        String path = path(request);
        String entityType = "File";
        Long entityId = null;
        Matcher attachment = ATTACHMENT_PATH.matcher(path);
        Matcher entity = ENTITY_PATH.matcher(path);
        if (attachment.matches()) {
            entityType = "FinanceAttachment";
            entityId = Long.valueOf(attachment.group(1));
        } else if (entity.matches() && ENTITY_BY_SECTION.containsKey(entity.group(1))) {
            entityType = ENTITY_BY_SECTION.get(entity.group(1));
            entityId = Long.valueOf(entity.group(2));
        } else if (path.startsWith("/finance/")) {
            entityType = "Finance";
        }
        Map<String, String[]> changes = new LinkedHashMap<>();
        String fileName = fileName(response.getHeader("Content-Disposition"));
        if (fileName != null) {
            changes.put("Файл", new String[]{"", fileName});
        }
        changes.put("Адреса", new String[]{"", truncate(pathWithQuery(request), 500)});
        if (path.startsWith("/verify/")) {
            changes.put("Хто", new String[]{"", "Перевірка протоколу за QR-кодом (без входу)"});
        }
        String type = entityType;
        Long id = entityId;
        safely(() -> auditLogService.record(AuditAction.FILE_DOWNLOAD, type, id, changes));
    }

    private void recordError(HttpServletRequest request, int status, Throwable error) {
        Map<String, String[]> changes = new LinkedHashMap<>();
        changes.put("Адреса", new String[]{"", request.getMethod() + " " + truncate(pathWithQuery(request), 500)});
        changes.put("Код відповіді", new String[]{"", String.valueOf(status)});
        if (error != null) {
            Throwable root = error;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            changes.put("Помилка", new String[]{"", truncate(root.getClass().getSimpleName()
                    + (root.getMessage() != null ? ": " + root.getMessage() : ""), 1000)});
        }
        safely(() -> auditLogService.record(AuditAction.SERVER_ERROR, "Page", null, changes));
    }

    /** record(…) бере користувача з SecurityContext — як і решта журналу. */
    private void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            // Журнал не повинен ламати саму відповідь (напр., база недоступна).
            log.warn("Audit record failed: {}", e.getMessage());
        }
    }

    static String fileName(String contentDisposition) {
        if (contentDisposition == null) {
            return null;
        }
        String found = null;
        Matcher m = FILENAME.matcher(contentDisposition);
        while (m.find()) {
            found = m.group(1); // filename* (UTF-8) іде після filename — беремо останній
        }
        return found == null ? null : found.contains("%") ? URLDecoder.decode(found, StandardCharsets.UTF_8) : found;
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }

    private static String pathWithQuery(HttpServletRequest request) {
        String query = request.getQueryString();
        return path(request) + (query != null ? "?" + query : "");
    }

    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return ip == null ? null : truncate(ip, 64);
    }

    private static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) + "…" : value;
    }
}
