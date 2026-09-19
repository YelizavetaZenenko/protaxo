package com.example.protaxo.security.web;

import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.security.dto.UserEditFormData;
import com.example.protaxo.security.dto.UserInviteFormData;
import com.example.protaxo.security.dto.UserSummary;
import com.example.protaxo.security.entity.Role;
import com.example.protaxo.security.service.UserManagementService;
import com.example.protaxo.worker.service.RepairWorkerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserPageController {

    private final UserManagementService userManagementService;
    private final RepairWorkerService repairWorkerService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userManagementService.findAll());
        model.addAttribute("workers", repairWorkerService.findAll());
        return "users/list";
    }

    @GetMapping("/new")
    public String inviteForm(Model model) {
        model.addAttribute("form", new UserInviteFormData());
        model.addAttribute("roles", Role.values());
        return "users/form";
    }

    @PostMapping
    public String invite(@Valid @ModelAttribute("form") UserInviteFormData form, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("roles", Role.values());
            return "users/form";
        }
        try {
            userManagementService.invite(form);
        } catch (BusinessRuleException ex) {
            model.addAttribute("formError", ex.getMessage());
            model.addAttribute("roles", Role.values());
            return "users/form";
        }
        return "redirect:/users";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        UserSummary user = userManagementService.findById(id);
        UserEditFormData form = new UserEditFormData();
        form.setFullName(user.fullName());
        form.setEmail(user.email());
        form.setRole(user.role());
        model.addAttribute("form", form);
        model.addAttribute("editId", id);
        model.addAttribute("roles", Role.values());
        return "users/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") UserEditFormData form,
                          BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("editId", id);
            model.addAttribute("roles", Role.values());
            return "users/form";
        }
        try {
            userManagementService.update(id, form);
        } catch (BusinessRuleException ex) {
            model.addAttribute("formError", ex.getMessage());
            model.addAttribute("editId", id);
            model.addAttribute("roles", Role.values());
            return "users/form";
        }
        return "redirect:/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userManagementService.delete(id);
        } catch (BusinessRuleException ex) {
            redirectAttributes.addFlashAttribute("formError", ex.getMessage());
        }
        return "redirect:/users";
    }
}
