package com.ultimacuota.services;

import com.ultimacuota.exceptions.ResourceNotFoundException;
import com.ultimacuota.models.Usuario;
import com.ultimacuota.repositories.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UsuarioRepository usuarioRepository;

    public Map<String, Object> getUserProfile(Long userId) {
        Usuario user = usuarioRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("profile_photo", user.getProfilePhoto());
        map.put("created_at", user.getCreatedAt());
        return map;
    }

    public List<Map<String, Object>> searchUsers(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String lowerQuery = query.trim().toLowerCase();
        return usuarioRepository.findAll().stream()
                .filter(u -> u.getUsername().toLowerCase().contains(lowerQuery))
                .limit(20)
                .map(u -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", u.getId());
                    map.put("username", u.getUsername());
                    map.put("profile_photo", u.getProfilePhoto());
                    return map;
                })
                .collect(Collectors.toList());
    }
}
