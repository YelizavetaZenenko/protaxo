package com.example.protaxo.vehicle.service;

import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.common.exception.NotFoundException;
import com.example.protaxo.vehicle.dto.VehicleMakeResponse;
import com.example.protaxo.vehicle.dto.VehicleModelResponse;
import com.example.protaxo.vehicle.entity.VehicleMake;
import com.example.protaxo.vehicle.entity.VehicleModel;
import com.example.protaxo.vehicle.repository.VehicleMakeRepository;
import com.example.protaxo.vehicle.repository.VehicleModelRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reference catalog of truck makes/models, used to back the "Марка"/"Модель" dropdowns when
 * adding a vehicle (both from /vehicles/new and the quick-add modal on the invoice form).
 * Deliberately not wired to {@code Vehicle.make}/{@code model} as a FK — those stay plain
 * strings, this catalog is just the source of dropdown options and grows via "+ Додати нову".
 */
@Service
@RequiredArgsConstructor
@Transactional
public class VehicleCatalogService {

    private final VehicleMakeRepository makeRepository;
    private final VehicleModelRepository modelRepository;

    @Transactional(readOnly = true)
    public List<VehicleMakeResponse> findAllMakes() {
        return makeRepository.findAllByOrderByNameAsc().stream()
                .map(m -> new VehicleMakeResponse(m.getId(), m.getName()))
                .toList();
    }

    public VehicleMakeResponse createMake(String name) {
        String trimmed = name.trim();
        if (makeRepository.existsByNameIgnoreCase(trimmed)) {
            throw new BusinessRuleException("Ця марка вже є у довіднику");
        }
        VehicleMake saved = makeRepository.save(VehicleMake.builder().name(trimmed).build());
        return new VehicleMakeResponse(saved.getId(), saved.getName());
    }

    @Transactional(readOnly = true)
    public List<VehicleModelResponse> findModelsByMakeId(Long makeId) {
        return modelRepository.findByMakeIdOrderByNameAsc(makeId).stream()
                .map(m -> new VehicleModelResponse(m.getId(), m.getName()))
                .toList();
    }

    public VehicleModelResponse createModel(Long makeId, String name) {
        VehicleMake make = makeRepository.findById(makeId)
                .orElseThrow(() -> new NotFoundException("VehicleMake %d not found".formatted(makeId)));
        String trimmed = name.trim();
        if (modelRepository.existsByMakeIdAndNameIgnoreCase(makeId, trimmed)) {
            throw new BusinessRuleException("Ця модель вже є у довіднику для цієї марки");
        }
        VehicleModel saved = modelRepository.save(VehicleModel.builder().make(make).name(trimmed).build());
        return new VehicleModelResponse(saved.getId(), saved.getName());
    }
}
