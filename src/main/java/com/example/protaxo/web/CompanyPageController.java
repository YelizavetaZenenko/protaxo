package com.example.protaxo.web;

import com.example.protaxo.calibration.service.MasterCardService;
import com.example.protaxo.worker.service.RepairWorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * «Основна інформація» (docs/Основна інформація.md): робітники майстерні й картки майстерні
 * в одному місці. Перегляд — адміністратор і майстер; зміни — лише адміністратор
 * (/api/repair-workers, /api/master-cards, див. SecurityConfig).
 */
@Controller
@RequestMapping("/company")
@RequiredArgsConstructor
public class CompanyPageController {

    private final RepairWorkerService repairWorkerService;
    private final MasterCardService masterCardService;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("workers", repairWorkerService.findAllDetails());
        model.addAttribute("cards", masterCardService.findAll());
        return "company/index";
    }
}
