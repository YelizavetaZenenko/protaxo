package com.example.protaxo.calibration.mapper;

import com.example.protaxo.calibration.dto.CalibrationProtocolResponse;
import com.example.protaxo.calibration.entity.CalibrationProtocol;
import com.example.protaxo.invoice.entity.Invoice;
import java.time.format.DateTimeFormatter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CalibrationProtocolMapper {

    DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @Mapping(target = "protocolNumber", expression = "java(buildProtocolNumber(protocol))")
    @Mapping(source = "client.id", target = "clientId")
    @Mapping(source = "invoice.id", target = "invoiceId")
    @Mapping(target = "orderLabel", expression = "java(buildOrderLabel(protocol))")
    CalibrationProtocolResponse toResponse(CalibrationProtocol protocol);

    default String buildProtocolNumber(CalibrationProtocol protocol) {
        return "№%06d".formatted(protocol.getId());
    }

    default String buildOrderLabel(CalibrationProtocol protocol) {
        Invoice invoice = protocol.getInvoice();
        if (invoice != null) {
            // internalNumber is always invoice.number here (enforced in the service) —
            // showing it twice would just be a confusing repeat of the same number.
            return "Наряд-заказ № %s від %s".formatted(
                    invoice.getNumber(), invoice.getDocumentDate().format(DATE_FORMAT));
        }
        return "Наряд-заказ %s, внутрішній номер %s від %s".formatted(
                buildProtocolNumber(protocol), protocol.getInternalNumber(),
                protocol.getProtocolDate().format(DATE_FORMAT));
    }
}
