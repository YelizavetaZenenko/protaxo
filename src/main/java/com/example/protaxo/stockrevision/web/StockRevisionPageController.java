package com.example.protaxo.stockrevision.web;

import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.stockrevision.dto.StockRevisionForm;
import com.example.protaxo.stockrevision.dto.StockRevisionView;
import com.example.protaxo.stockrevision.service.StockRevisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Ревізія складу (docs/Ревізія складу.md). Доступ — ADMIN і ACCOUNTANT (SecurityConfig).
 * Помилки бізнес-правил повертають на ту саму сторінку з повідомленням; введені в чернетку
 * кількості при цьому не губляться, бо форма спершу зберігається окремою транзакцією.
 */
@Controller
@RequestMapping("/stock-revisions")
@RequiredArgsConstructor
public class StockRevisionPageController {

    private final StockRevisionService revisionService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("revisions", revisionService.findAll());
        model.addAttribute("draftId", revisionService.findDraftId().orElse(null));
        return "stock-revisions/list";
    }

    @PostMapping
    public String create(RedirectAttributes redirect) {
        try {
            return "redirect:/stock-revisions/" + revisionService.createDraft();
        } catch (BusinessRuleException ex) {
            redirect.addFlashAttribute("revisionError", ex.getMessage());
            return "redirect:/stock-revisions";
        }
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        StockRevisionView revision = revisionService.findById(id);
        model.addAttribute("revision", revision);
        return "stock-revisions/view";
    }

    @PostMapping("/{id}")
    public String save(@PathVariable Long id, @ModelAttribute StockRevisionForm form,
                       @RequestParam(defaultValue = "save") String action, RedirectAttributes redirect) {
        try {
            if ("complete".equals(action)) {
                // Спершу зберегти введене — якщо проведення впаде на перевірці, дані не загубляться.
                revisionService.saveDraft(id, form);
                revisionService.complete(id, form);
                redirect.addFlashAttribute("revisionSuccess", "Ревізію проведено — залишки в каталозі оновлено");
            } else {
                revisionService.saveDraft(id, form);
                redirect.addFlashAttribute("revisionSuccess", "Чернетку збережено");
            }
        } catch (BusinessRuleException ex) {
            redirect.addFlashAttribute("revisionError", ex.getMessage());
        }
        return "redirect:/stock-revisions/" + id;
    }

    @PostMapping("/{id}/delete-draft")
    public String deleteDraft(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            revisionService.deleteDraft(id);
            redirect.addFlashAttribute("revisionSuccess", "Чернетку ревізії видалено");
        } catch (BusinessRuleException ex) {
            redirect.addFlashAttribute("revisionError", ex.getMessage());
        }
        return "redirect:/stock-revisions";
    }
}
