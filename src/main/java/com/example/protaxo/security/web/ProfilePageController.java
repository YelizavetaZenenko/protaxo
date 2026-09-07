package com.example.protaxo.security.web;

import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.security.dto.ProfileFormData;
import com.example.protaxo.security.entity.User;
import com.example.protaxo.security.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfilePageController {

    private final ProfileService profileService;

    @GetMapping
    public String view(Model model) {
        if (!model.containsAttribute("profile")) {
            User user = profileService.getCurrentUser();
            ProfileFormData form = new ProfileFormData();
            form.setFullName(user.getFullName());
            model.addAttribute("profile", form);
        }
        return "profile";
    }

    @PostMapping
    public String update(@Valid @ModelAttribute("profile") ProfileFormData form, BindingResult bindingResult,
                          Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "profile";
        }
        try {
            profileService.updateProfile(form);
        } catch (BusinessRuleException ex) {
            model.addAttribute("formError", ex.getMessage());
            return "profile";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Профіль оновлено");
        return "redirect:/profile";
    }
}
