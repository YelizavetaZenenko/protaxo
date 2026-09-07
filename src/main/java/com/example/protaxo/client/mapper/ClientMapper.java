package com.example.protaxo.client.mapper;

import com.example.protaxo.client.dto.ClientRequest;
import com.example.protaxo.client.dto.ClientResponse;
import com.example.protaxo.client.entity.Client;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ClientMapper {

    @Mapping(source = "employerClient.id", target = "employerClientId")
    ClientResponse toResponse(Client client);

    // The employer is resolved from its id by ClientService, which has the repository.
    @Mapping(target = "employerClient", ignore = true)
    Client toEntity(ClientRequest request);

    @Mapping(target = "employerClient", ignore = true)
    void updateEntity(ClientRequest request, @MappingTarget Client client);
}
