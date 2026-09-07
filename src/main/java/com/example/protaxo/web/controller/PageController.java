package com.example.protaxo.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "redirect:/invoices";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
