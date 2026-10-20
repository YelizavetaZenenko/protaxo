package com.example.protaxo.security.service;

import com.example.protaxo.security.entity.User;
import com.example.protaxo.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final DeveloperAccounts developerAccounts;

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        String role = "ROLE_" + user.getRole().name();
        String[] authorities = developerAccounts.isDeveloper(user.getEmail())
                ? new String[] {role, DeveloperAccounts.AUTHORITY}
                : new String[] {role};
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .disabled(!user.isActive())
                .build();
    }
}
