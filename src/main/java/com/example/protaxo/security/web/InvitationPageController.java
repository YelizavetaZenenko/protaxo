package com.example.protaxo.security.web;

import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.security.dto.AcceptInviteFormData;
import com.example.protaxo.security.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class InvitationPageController {

    private final UserManagementService userManagementService;

    @GetMapping("/accept-invite")
    public String showForm(@RequestParam String token, Model model) {
        if (!userManagementService.isTokenValid(token)) {
            model.addAttribute("invalid", true);
            return "accept-invite";
        }
        model.addAttribute("token", token);
        model.addAttribute("form", new AcceptInviteFormData());
        return "accept-invite";
    }

    @PostMapping("/accept-invite")
    public String submit(@RequestParam String token, @Valid @ModelAttribute("form") AcceptInviteFormData form,
                          BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("token", token);
            return "accept-invite";
        }
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            model.addAttribute("token", token);
            model.addAttribute("formError", "Паролі не збігаються");
            return "accept-invite";
        }
        try {
            userManagementService.acceptInvite(token, form.getPassword());
        } catch (BusinessRuleException ex) {
            model.addAttribute("invalid", true);
            return "accept-invite";
        }
        return "redirect:/login?activated";
    }
}
