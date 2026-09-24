package com.example.protaxo.invoice.mapper;

import com.example.protaxo.invoice.dto.InvoiceItemResponse;
import com.example.protaxo.invoice.dto.InvoiceResponse;
import com.example.protaxo.invoice.entity.Invoice;
import com.example.protaxo.invoice.entity.InvoiceItem;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InvoiceMapper {

    DateTimeFormatter BUYER_ORDER_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @Mapping(source = "client.id", target = "clientId")
    @Mapping(target = "buyerOrderLabel", expression = "java(buildBuyerOrderLabel(invoice))")
    @Mapping(target = "totalAmount", expression = "java(computeTotal(invoice))")
    @Mapping(target = "totalVat", expression = "java(sum(invoice, InvoiceItem::getVatAmount))")
    @Mapping(target = "totalWithoutVat", expression = "java(sum(invoice, InvoiceItem::getAmountWithoutVat))")
    InvoiceResponse toResponse(Invoice invoice);

    @Mapping(source = "catalogItem.id", target = "catalogItemId")
    @Mapping(source = "catalogItem.type", target = "catalogItemType")
    InvoiceItemResponse toItemResponse(InvoiceItem item);

    default String buildBuyerOrderLabel(Invoice invoice) {
        return "Замовлення покупця, номер %s від %s".formatted(
                invoice.getNumber(), invoice.getDocumentDate().format(BUYER_ORDER_DATE_FORMAT));
    }

    default BigDecimal computeTotal(Invoice invoice) {
        return sum(invoice, InvoiceItem::getAmount);
    }

    /** Підсумки документа — суми рядків, тож завжди узгоджені з позиціями (розд. 11 концепції). */
    default BigDecimal sum(Invoice invoice, Function<InvoiceItem, BigDecimal> field) {
        return invoice.getItems().stream()
                .map(field)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
