package com.example.protaxo.address;

import com.example.protaxo.common.exception.NotFoundException;
import java.util.List;
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

    public record SettlementView(String code, String label, String shortLabel, String regionCode,
                                 String districtName, String hromadaName) {
        static SettlementView of(AddressDirectoryService.Settlement s) {
            return new SettlementView(s.code(), s.label(), s.shortLabel(), s.regionCode(), s.districtName(), s.hromadaName());
        }
    }

    @GetMapping("/settlements")
    public List<SettlementView> search(@RequestParam(defaultValue = "") String q,
                                       @RequestParam(required = false) String region) {
        return addressDirectoryService.search(q, region).stream().map(SettlementView::of).toList();
    }

    @GetMapping("/settlements/{code}")
    public SettlementView one(@PathVariable String code) {
        return addressDirectoryService.find(code).map(SettlementView::of)
                .orElseThrow(() -> new NotFoundException("Населений пункт " + code + " не знайдено"));
    }
}
