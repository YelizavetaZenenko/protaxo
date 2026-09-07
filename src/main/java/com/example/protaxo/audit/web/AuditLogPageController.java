package com.example.protaxo.audit.web;

import com.example.protaxo.audit.entity.AuditLog;
import com.example.protaxo.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        Page<AuditLog> result = auditLogService.findRecent(PageRequest.of(page, PAGE_SIZE));
        model.addAttribute("entries", result.getContent());
        model.addAttribute("page", page);
        model.addAttribute("totalPages", result.getTotalPages());
        return "audit-log/list";
    }
}
