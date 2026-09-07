package com.example.protaxo.driver.mapper;

import com.example.protaxo.driver.dto.DriverRequest;
import com.example.protaxo.driver.dto.DriverResponse;
import com.example.protaxo.driver.entity.Driver;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface DriverMapper {

    @Mapping(source = "client.id", target = "clientId")
    DriverResponse toResponse(Driver driver);

    @Mapping(target = "client", ignore = true)
    Driver toEntity(DriverRequest request);

    @Mapping(target = "client", ignore = true)
    void updateEntity(DriverRequest request, @MappingTarget Driver driver);
}
