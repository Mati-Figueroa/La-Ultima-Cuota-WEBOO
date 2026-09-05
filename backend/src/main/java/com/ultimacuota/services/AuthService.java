package com.ultimacuota.services;

import com.ultimacuota.dto.LoginRequest;
import com.ultimacuota.dto.RegisterRequest;
import com.ultimacuota.dto.UpdateProfileRequest;
import com.ultimacuota.dto.UserResponse;
import com.ultimacuota.exceptions.ConflictException;
import com.ultimacuota.exceptions.ResourceNotFoundException;
import com.ultimacuota.models.Usuario;
import com.ultimacuota.repositories.UsuarioRepository;
import com.ultimacuota.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public Map<String, Object> register(RegisterRequest request) {
        if (request.getUsername() == null || request.getEmail() == null || request.getPassword() == null) {
            throw new IllegalArgumentException("Todos los campos son obligatorios");
        }
        if (request.getPassword().length() < 6) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 6 caracteres");
        }

        if (!usuarioRepository.findByEmailOrUsername(request.getEmail(), request.getUsername()).isEmpty()) {
            throw new ConflictException("El email o nombre de usuario ya está registrado");
        }

        Usuario user = Usuario.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();
        user = usuarioRepository.save(user);

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getEmail());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", UserResponse.from(user));
        return result;
    }

    public Map<String, Object> login(LoginRequest request) {
        if (request.getEmail() == null || request.getPassword() == null) {
            throw new IllegalArgumentException("Email y contraseña son obligatorios");
        }

        Usuario user = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Credenciales inválidas"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResourceNotFoundException("Credenciales inválidas");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getEmail());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", UserResponse.from(user));
        return result;
    }

    public UserResponse me(Long userId) {
        Usuario user = usuarioRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return UserResponse.from(user);
    }

    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        Usuario user = usuarioRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (request.getUsername() != null && !request.getUsername().trim().isEmpty()) {
            String newUsername = request.getUsername().trim();
            if (newUsername.length() > 50) {
                throw new IllegalArgumentException("El nombre de usuario no puede exceder 50 caracteres");
            }
            if (!newUsername.equals(user.getUsername())) {
                List<Usuario> existing = usuarioRepository.findByEmailOrUsername(user.getEmail(), newUsername);
                boolean taken = existing.stream().anyMatch(u -> !u.getId().equals(userId));
                if (taken) {
                    throw new ConflictException("El nombre de usuario ya está en uso");
                }
                user.setUsername(newUsername);
            }
        }

        if (request.getProfilePhoto() != null) {
            user.setProfilePhoto(request.getProfilePhoto());
        }

        user = usuarioRepository.save(user);
        return UserResponse.from(user);
    }
}
