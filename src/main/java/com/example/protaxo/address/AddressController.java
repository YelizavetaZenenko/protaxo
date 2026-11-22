package com.example.protaxo.address;

import com.example.protaxo.common.exception.NotFoundException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Пошук населених пунктів КАТОТТГ для поля «Юридична адреса» у формі контрагента. */
@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressDirectoryService addressDirectoryService;

    /** postalCodes — індекси, які вже вводили для пункту, найчастіший першим (може бути порожньо). */
    public record SettlementView(String code, String label, String shortLabel, String regionCode,
                                 String districtName, String hromadaName, List<String> postalCodes) {
        static SettlementView of(AddressDirectoryService.Settlement s, Map<String, List<String>> postal) {
            return new SettlementView(s.code(), s.label(), s.shortLabel(), s.regionCode(), s.districtName(),
                    s.hromadaName(), postal.getOrDefault(s.code(), List.of()));
        }
    }

    @GetMapping("/settlements")
    public List<SettlementView> search(@RequestParam(defaultValue = "") String q,
                                       @RequestParam(required = false) String region) {
        List<AddressDirectoryService.Settlement> found = addressDirectoryService.search(q, region);
        Map<String, List<String>> postal = addressDirectoryService.postalCodes(
                found.stream().map(AddressDirectoryService.Settlement::code).toList());
        return found.stream().map(s -> SettlementView.of(s, postal)).toList();
    }

    @GetMapping("/settlements/{code}")
    public SettlementView one(@PathVariable String code) {
        return addressDirectoryService.find(code)
                .map(s -> SettlementView.of(s, addressDirectoryService.postalCodes(List.of(s.code()))))
                .orElseThrow(() -> new NotFoundException("Населений пункт " + code + " не знайдено"));
    }
}
