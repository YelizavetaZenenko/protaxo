package com.example.protaxo.audit.web;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.entity.AuditLog;
import com.example.protaxo.audit.service.AuditEntityLabelResolver;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.security.entity.User;
import com.example.protaxo.security.repository.UserRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/audit-log")
@RequiredArgsConstructor
public class AuditLogPageController {

    private static final int PAGE_SIZE = 50;

    private final AuditLogService auditLogService;
    private final AuditEntityLabelResolver auditEntityLabelResolver;
    private final UserRepository userRepository;

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(required = false) String username,
                       @RequestParam(required = false) AuditAction action,
                       @RequestParam(required = false) String entityType,
                       @RequestParam(required = false) Long entityId,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(required = false) String q,
                       Model model) {
        AuditLogService.Filter filter = new AuditLogService.Filter(username, action, entityType, entityId, from, to, q);
        Page<AuditLog> result = auditLogService.search(filter, PageRequest.of(Math.max(page, 0), PAGE_SIZE));

        model.addAttribute("entries", result.getContent());
        model.addAttribute("page", page);
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("totalElements", result.getTotalElements());
        model.addAttribute("filter", filter);
        model.addAttribute("filtered", username != null && !username.isBlank() || action != null
                || entityType != null && !entityType.isBlank() || entityId != null || from != null || to != null
                || q != null && !q.isBlank());
        model.addAttribute("usernames", auditLogService.findUsernames());
        model.addAttribute("userNames", fullNamesByEmail());
        model.addAttribute("entityTypes", auditLogService.findEntityTypes());
        model.addAttribute("actions", AuditAction.values());
        if (entityType != null && !entityType.isBlank() && entityId != null) {
            model.addAttribute("historyOf", auditEntityLabelResolver.resolve(entityType, entityId));
        }
        return "audit-log/list";
    }

    /** Журнал зберігає email; для читабельності поруч показується ПІБ, якщо акаунт є. */
    private Map<String, String> fullNamesByEmail() {
        Map<String, String> names = new HashMap<>();
        List<User> users = userRepository.findAll();
        for (User user : users) {
            names.put(user.getEmail(), user.getFullName());
        }
        return names;
    }
}
