package com.example.protaxo.catalog.mapper;

import com.example.protaxo.catalog.dto.CatalogItemRequest;
import com.example.protaxo.catalog.dto.CatalogItemResponse;
import com.example.protaxo.catalog.entity.CatalogItem;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CatalogItemMapper {

    CatalogItemResponse toResponse(CatalogItem catalogItem);

    CatalogItem toEntity(CatalogItemRequest request);

    void updateEntity(CatalogItemRequest request, @MappingTarget CatalogItem catalogItem);
}
