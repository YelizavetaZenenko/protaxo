package com.example.protaxo.vehicle.mapper;

import com.example.protaxo.vehicle.dto.VehicleRequest;
import com.example.protaxo.vehicle.dto.VehicleResponse;
import com.example.protaxo.vehicle.entity.Vehicle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface VehicleMapper {

    @Mapping(source = "client.id", target = "clientId")
    VehicleResponse toResponse(Vehicle vehicle);

    @Mapping(target = "client", ignore = true)
    Vehicle toEntity(VehicleRequest request);

    @Mapping(target = "client", ignore = true)
    void updateEntity(VehicleRequest request, @MappingTarget Vehicle vehicle);
}
