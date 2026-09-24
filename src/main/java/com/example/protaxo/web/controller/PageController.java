package com.example.protaxo.web.controller;

import com.example.protaxo.security.entity.Role;
import com.example.protaxo.security.service.CurrentUserRoles;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return CurrentUserRoles.has(Role.ACCOUNTANT) ? "redirect:/finance" : "redirect:/invoices";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
