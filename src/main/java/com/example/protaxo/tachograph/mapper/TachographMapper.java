package com.example.protaxo.tachograph.mapper;

import com.example.protaxo.tachograph.dto.TachographRequest;
import com.example.protaxo.tachograph.dto.TachographResponse;
import com.example.protaxo.tachograph.entity.Tachograph;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface TachographMapper {

    @Mapping(source = "vehicle.id", target = "vehicleId")
    TachographResponse toResponse(Tachograph tachograph);

    @Mapping(target = "vehicle", ignore = true)
    Tachograph toEntity(TachographRequest request);

    @Mapping(target = "vehicle", ignore = true)
    void updateEntity(TachographRequest request, @MappingTarget Tachograph tachograph);
}
