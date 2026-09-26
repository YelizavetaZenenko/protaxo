package com.example.protaxo.catalog.web;

import com.example.protaxo.catalog.dto.CatalogUsageLine;
import com.example.protaxo.catalog.dto.CatalogUsageRow;
import com.example.protaxo.catalog.entity.CatalogItemType;
import com.example.protaxo.catalog.service.CatalogUsageService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Звіт «Витрата товарів» (docs/Витрата товарів.md) — лише перегляд, для всіх, хто бачить каталог. */
@Controller
@RequestMapping("/catalog-items/usage")
@RequiredArgsConstructor
public class CatalogUsagePageController {

    private final CatalogUsageService usageService;

    @GetMapping
    public String report(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         @RequestParam(defaultValue = "MATERIAL") CatalogItemType type,
                         @RequestParam(required = false) String q,
                         Model model) {
        LocalDate periodFrom = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate periodTo = to != null ? to : LocalDate.now();
        String needle = q == null ? "" : q.trim().toLowerCase();
        List<CatalogUsageRow> rows = usageService.usage(periodFrom, periodTo, type).stream()
                .filter(r -> needle.isEmpty() || r.name().toLowerCase().contains(needle))
                .toList();
        addPeriod(model, periodFrom, periodTo);
        model.addAttribute("rows", rows);
        model.addAttribute("type", type);
        model.addAttribute("q", q);
        model.addAttribute("totalAmount", rows.stream().map(CatalogUsageRow::usedAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        return "catalog-items/usage";
    }

    @GetMapping("/{catalogItemId}")
    public String detail(@PathVariable Long catalogItemId,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         Model model) {
        LocalDate periodFrom = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate periodTo = to != null ? to : LocalDate.now();
        List<CatalogUsageLine> lines = usageService.lines(catalogItemId, periodFrom, periodTo);
        addPeriod(model, periodFrom, periodTo);
        model.addAttribute("catalogItemId", catalogItemId);
        model.addAttribute("itemName", usageService.itemName(catalogItemId));
        model.addAttribute("lines", lines);
        model.addAttribute("totalQuantity", lines.stream().map(CatalogUsageLine::quantity).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("totalAmount", lines.stream().map(CatalogUsageLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        return "catalog-items/usage-detail";
    }

    private void addPeriod(Model model, LocalDate from, LocalDate to) {
        LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("thisMonthFrom", thisMonth);
        model.addAttribute("thisMonthTo", LocalDate.now());
        model.addAttribute("lastMonthFrom", thisMonth.minusMonths(1));
        model.addAttribute("lastMonthTo", thisMonth.minusDays(1));
    }
}
