package com.example.protaxo.audit.service;

import com.example.protaxo.calibration.repository.CalibrationProtocolRepository;
import com.example.protaxo.catalog.repository.CatalogItemRepository;
import com.example.protaxo.client.repository.ClientRepository;
import com.example.protaxo.contract.repository.ContractRepository;
import com.example.protaxo.driver.repository.DriverRepository;
import com.example.protaxo.invoice.repository.InvoiceRepository;
import com.example.protaxo.security.repository.UserRepository;
import com.example.protaxo.tachograph.repository.TachographRepository;
import com.example.protaxo.vehicle.repository.VehicleRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Turns an audit log entry's raw {@code entityType}/{@code entityId} into something a human can
 * actually recognize (a plate number, a contract number, a name...) plus a link to that item's
 * view page — so "Журнал дій" shows *what* was edited, not just an opaque database ID. See
 * audit-log/list.html.
 *
 * <p>The label comes from a native "including deleted" query (a plain column read, not an entity
 * load) that bypasses each entity's {@code deleted_at IS NULL} @SQLRestriction — so a DELETE
 * entry, or anything touching an item since deleted, still shows its name instead of "#id".
 * The link is only shown when the item is still live ({@code existsById}, which *does* respect
 * the restriction) since its view page would 404 otherwise.
 */
@Component
@RequiredArgsConstructor
public class AuditEntityLabelResolver {

    private final VehicleRepository vehicleRepository;
    private final TachographRepository tachographRepository;
    private final ClientRepository clientRepository;
    private final ContractRepository contractRepository;
    private final InvoiceRepository invoiceRepository;
    private final CatalogItemRepository catalogItemRepository;
    private final CalibrationProtocolRepository calibrationProtocolRepository;
    private final DriverRepository driverRepository;
    private final UserRepository userRepository;

    public AuditEntityRef resolve(String entityType, Long entityId) {
        if (entityId == null) {
            return fallback(null);
        }
        return switch (entityType) {
            case "Vehicle" -> build(vehicleRepository.findRegistrationNumberByIdIncludingDeleted(entityId),
                    entityId, vehicleRepository.existsById(entityId), "/vehicles/" + entityId);
            case "Tachograph" -> build(tachographRepository.findSerialNumberByIdIncludingDeleted(entityId),
                    entityId, tachographRepository.existsById(entityId), "/tachographs/" + entityId);
            case "Client" -> build(clientRepository.findNameByIdIncludingDeleted(entityId),
                    entityId, clientRepository.existsById(entityId), "/clients/" + entityId);
            case "Contract" -> build(contractRepository.findContractNumberByIdIncludingDeleted(entityId),
                    entityId, contractRepository.existsById(entityId), "/contracts/" + entityId);
            case "Invoice" -> build(invoiceRepository.findNumberByIdIncludingDeleted(entityId),
                    entityId, invoiceRepository.existsById(entityId), "/invoices/" + entityId);
            case "CatalogItem" -> build(catalogItemRepository.findNameByIdIncludingDeleted(entityId),
                    entityId, catalogItemRepository.existsById(entityId), "/catalog-items/" + entityId);
            case "CalibrationProtocol" -> build(calibrationProtocolRepository.findInternalNumberByIdIncludingDeleted(entityId),
                    entityId, calibrationProtocolRepository.existsById(entityId), "/calibration-protocols/" + entityId);
            // Drivers and users have no standalone view page - drivers are only shown nested
            // under their client, users are list + invite/delete only - so never link either.
            case "Driver" -> build(driverRepository.findFullNameByIdIncludingDeleted(entityId), entityId, false, null);
            case "User" -> build(userRepository.findFullNameByIdIncludingDeleted(entityId), entityId, false, null);
            default -> fallback(entityId);
        };
    }

    private AuditEntityRef build(Optional<String> label, Long entityId, boolean isLive, String url) {
        if (label.isEmpty() || label.get().isBlank()) {
            return fallback(entityId);
        }
        return new AuditEntityRef(label.get(), isLive ? url : null);
    }

    private AuditEntityRef fallback(Long entityId) {
        return new AuditEntityRef(entityId == null ? "—" : "#" + entityId, null);
    }
}
