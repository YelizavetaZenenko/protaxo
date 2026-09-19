package com.example.protaxo.contract.mapper;

import com.example.protaxo.contract.dto.ContractRequest;
import com.example.protaxo.contract.dto.ContractResponse;
import com.example.protaxo.contract.entity.Contract;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ContractMapper {

    @Mapping(source = "client.id", target = "clientId")
    @Mapping(target = "contractDate", expression = "java(contract.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate())")
    ContractResponse toResponse(Contract contract);

    @Mapping(target = "client", ignore = true)
    Contract toEntity(ContractRequest request);

    @Mapping(target = "client", ignore = true)
    void updateEntity(ContractRequest request, @MappingTarget Contract contract);
}
