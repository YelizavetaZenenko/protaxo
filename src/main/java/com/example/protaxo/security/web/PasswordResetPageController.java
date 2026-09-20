package com.example.protaxo.security.web;

import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.security.dto.ForgotPasswordFormData;
import com.example.protaxo.security.dto.ResetPasswordFormData;
import com.example.protaxo.security.service.PasswordResetService;
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
public class PasswordResetPageController {

    private final PasswordResetService passwordResetService;

    @GetMapping("/forgot-password")
    public String showForgotForm(Model model) {
        model.addAttribute("form", new ForgotPasswordFormData());
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String submitForgot(@Valid @ModelAttribute("form") ForgotPasswordFormData form, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "forgot-password";
        }
        passwordResetService.requestReset(form.getEmail());
        return "redirect:/forgot-password?sent";
    }

    @GetMapping("/reset-password")
    public String showResetForm(@RequestParam String token, Model model) {
        if (!passwordResetService.isTokenValid(token)) {
            model.addAttribute("invalid", true);
            return "reset-password";
        }
        model.addAttribute("token", token);
        model.addAttribute("form", new ResetPasswordFormData());
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String submitReset(@RequestParam String token, @Valid @ModelAttribute("form") ResetPasswordFormData form,
                               BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("token", token);
            return "reset-password";
        }
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            model.addAttribute("token", token);
            model.addAttribute("formError", "Паролі не збігаються");
            return "reset-password";
        }
        try {
            passwordResetService.resetPassword(token, form.getPassword());
        } catch (BusinessRuleException ex) {
            model.addAttribute("invalid", true);
            return "reset-password";
        }
        return "redirect:/login?resetSuccess";
    }
}
