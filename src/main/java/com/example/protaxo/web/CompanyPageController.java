package com.example.protaxo.web;

import com.example.protaxo.calibration.service.MasterCardService;
import com.example.protaxo.printagent.PrintAgentDistribution;
import com.example.protaxo.security.entity.PermissionKey;
import com.example.protaxo.security.service.PermissionService;
import com.example.protaxo.security.service.UserManagementService;
import com.example.protaxo.worker.service.RepairWorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * «Основна інформація» (docs/Основна інформація.md): користувачі (з правом керувати ними),
 * робітники майстерні й картки майстерні в одному місці. Перегляд — адміністратор і майстер; зміни — лише адміністратор
 * (/api/repair-workers, /api/master-cards, див. SecurityConfig).
 */
@Controller
@RequestMapping("/company")
@RequiredArgsConstructor
public class CompanyPageController {

    private final RepairWorkerService repairWorkerService;
    private final MasterCardService masterCardService;
    private final UserManagementService userManagementService;
    private final PermissionService permissionService;
    private final PrintAgentDistribution printAgentDistribution;

    @GetMapping
    public String index(Model model, Authentication authentication) {
        // Користувачі — лише тим, кому дозволено ними керувати (як і раніше на /users).
        if (permissionService.hasPermission(authentication, PermissionKey.CAN_MANAGE_USERS)) {
            model.addAttribute("users", userManagementService.findAll());
        }
        model.addAttribute("workers", repairWorkerService.findAllDetails());
        model.addAttribute("cards", masterCardService.findAll());
        model.addAttribute("printAgentBuildTime", printAgentDistribution.buildTime().orElse(null));
        return "company/index";
    }
}
