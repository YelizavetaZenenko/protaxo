package com.example.protaxo.security.service;

import com.example.protaxo.security.dto.MasterOption;
import com.example.protaxo.security.entity.Role;
import com.example.protaxo.security.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserDirectoryService {

    private final UserRepository userRepository;

    public List<MasterOption> findMasters() {
        return userRepository.findByRoleAndActiveTrue(Role.MASTER).stream()
                .map(u -> new MasterOption(u.getId(), u.getFullName()))
                .toList();
    }
}
