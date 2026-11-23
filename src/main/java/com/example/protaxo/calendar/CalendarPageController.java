package com.example.protaxo.calendar;

import com.example.protaxo.client.dto.ClientResponse;
import com.example.protaxo.client.service.ClientService;
import com.example.protaxo.vehicle.dto.VehicleResponse;
import com.example.protaxo.vehicle.service.VehicleService;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import tools.jackson.databind.ObjectMapper;

/**
 * «Календар» (docs/Календар.md): місяць на все вікно, записи клієнтів по днях. Самі записи сторінка
 * бере з /api/appointments; тут — лише довідник контрагентів і їхніх авто для підказок у формі.
 */
@Controller
@RequestMapping("/calendar")
@RequiredArgsConstructor
public class CalendarPageController {

    private final ClientService clientService;
    private final VehicleService vehicleService;
    private final ObjectMapper objectMapper;

    /** Підказка у формі запису: контрагент, його телефон і авто. */
    public record ClientOption(Long id, String name, String phone, List<String> vehicles) {
    }

    @GetMapping
    public String index(Model model) {
        var vehiclesByClient = vehicleService.findAll().stream()
                .filter(v -> v.clientId() != null)
                .collect(Collectors.groupingBy(VehicleResponse::clientId,
                        Collectors.mapping(CalendarPageController::vehicleLabel, Collectors.toList())));
        List<ClientOption> clients = clientService.findAll().stream()
                .sorted(Comparator.comparing(ClientResponse::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(c -> new ClientOption(c.id(), c.name(), firstNonBlank(c.phone(), c.contactPersonPhone()),
                        vehiclesByClient.getOrDefault(c.id(), List.of())))
                .toList();
        // Готуємо JSON тут: Thymeleaf-серіалізатор не знає Jackson 3 і записи (record) не розбирає.
        model.addAttribute("clientOptionsJson", objectMapper.writeValueAsString(clients));
        return "calendar/index";
    }

    /** «AC1466HI (Volvo FH)». */
    static String vehicleLabel(VehicleResponse v) {
        String car = Stream.of(v.make(), v.model()).filter(Objects::nonNull).filter(s -> !s.isBlank())
                .collect(Collectors.joining(" "));
        String plate = v.registrationNumber() != null && !v.registrationNumber().isBlank() ? v.registrationNumber() : v.vin();
        if (plate == null || plate.isBlank()) {
            return car;
        }
        return car.isEmpty() ? plate : plate + " (" + car + ")";
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
