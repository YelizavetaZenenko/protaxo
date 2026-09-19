package com.example.protaxo.calibration.mapper;

import com.example.protaxo.calibration.dto.CalibrationProtocolResponse;
import com.example.protaxo.calibration.entity.CalibrationProtocol;
import com.example.protaxo.invoice.entity.Invoice;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CalibrationProtocolMapper {

    DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /**
     * Періодичність повірки тахографа — не рідше ніж раз на 2 роки (стандартний інтервал,
     * той самий, що й у Регламенті (ЄС) № 165/2014 для вже встановлених цифрових тахографів).
     */
    int CALIBRATION_VALIDITY_YEARS = 2;

    @Mapping(target = "protocolNumber", expression = "java(buildProtocolNumber(protocol))")
    @Mapping(source = "client.id", target = "clientId")
    @Mapping(source = "invoice.id", target = "invoiceId")
    @Mapping(source = "tachograph.id", target = "tachographId")
    @Mapping(target = "orderLabel", expression = "java(buildOrderLabel(protocol))")
    @Mapping(target = "nextInspectionDate", expression = "java(buildNextInspectionDate(protocol))")
    CalibrationProtocolResponse toResponse(CalibrationProtocol protocol);

    default String buildProtocolNumber(CalibrationProtocol protocol) {
        return "№%06d".formatted(protocol.getId());
    }

    /**
     * Не окрема колонка БД — завжди похідне від {@code protocolDate}, той самий підхід, що й
     * {@link #buildProtocolNumber}/{@link #buildOrderLabel}: єдине джерело істини, жодного ризику
     * розсинхронізації збереженого значення з реальною датою протоколу.
     */
    default LocalDate buildNextInspectionDate(CalibrationProtocol protocol) {
        return protocol.getProtocolDate() == null ? null
                : protocol.getProtocolDate().toLocalDate().plusYears(CALIBRATION_VALIDITY_YEARS);
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
