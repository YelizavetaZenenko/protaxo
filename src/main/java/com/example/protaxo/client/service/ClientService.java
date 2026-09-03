package com.example.protaxo.client.service;

import com.example.protaxo.client.dto.ClientRequest;
import com.example.protaxo.client.dto.ClientResponse;
import com.example.protaxo.client.entity.Client;
import com.example.protaxo.client.mapper.ClientMapper;
import com.example.protaxo.client.repository.ClientRepository;
import com.example.protaxo.common.exception.NotFoundException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ClientService {

    private final ClientRepository clientRepository;
    private final ClientMapper clientMapper;

    @Transactional(readOnly = true)
    public List<ClientResponse> findAll() {
        return clientRepository.findAll().stream()
                .map(clientMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientResponse findById(Long id) {
        return clientMapper.toResponse(getOrThrow(id));
    }

    public ClientResponse create(ClientRequest request) {
        Client client = clientMapper.toEntity(request);
        return clientMapper.toResponse(clientRepository.save(client));
    }

    public ClientResponse update(Long id, ClientRequest request) {
        Client client = getOrThrow(id);
        clientMapper.updateEntity(request, client);
        return clientMapper.toResponse(clientRepository.save(client));
    }

    public void softDelete(Long id) {
        Client client = getOrThrow(id);
        client.setDeletedAt(Instant.now());
        clientRepository.save(client);
    }

    private Client getOrThrow(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Client %d not found".formatted(id)));
    }
}
