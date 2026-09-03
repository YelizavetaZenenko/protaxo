package com.example.protaxo.client.mapper;

import com.example.protaxo.client.dto.ClientRequest;
import com.example.protaxo.client.dto.ClientResponse;
import com.example.protaxo.client.entity.Client;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ClientMapper {

    ClientResponse toResponse(Client client);

    Client toEntity(ClientRequest request);

    void updateEntity(ClientRequest request, @MappingTarget Client client);
}
