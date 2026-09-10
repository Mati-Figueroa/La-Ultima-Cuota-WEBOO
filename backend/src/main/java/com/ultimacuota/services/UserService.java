package com.ultimacuota.services;

import com.ultimacuota.exceptions.ResourceNotFoundException;
import com.ultimacuota.models.Caballo;
import com.ultimacuota.models.Usuario;
import com.ultimacuota.repositories.CaballoRepository;
import com.ultimacuota.repositories.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UsuarioRepository usuarioRepository;
    private final CaballoRepository caballoRepository;

    public Map<String, Object> getUserProfile(Long userId) {
        Usuario user = usuarioRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("profile_photo", user.getProfilePhoto());
        map.put("profilePhoto", user.getProfilePhoto());
        map.put("created_at", user.getCreatedAt());

        List<Caballo> horses = caballoRepository.findByPropietarioIdOrderByCreatedAtDesc(userId);
        List<Map<String, Object>> horsesData = horses.stream().map(h -> {
            Map<String, Object> hm = new HashMap<>();
            hm.put("id", h.getId());
            hm.put("nombre", h.getNombre());
            hm.put("edad", h.getEdad());
            hm.put("fatiga", h.getFatiga());
            hm.put("carreras_totales", h.getCarrerasTotales());
            hm.put("victorias", h.getVictorias());
            double wr = h.getCarrerasTotales() != null && h.getCarrerasTotales() > 0
                    ? Math.round((double) h.getVictorias() / h.getCarrerasTotales() * 100.0)
                    : 0.0;
            hm.put("winrate", wr);
            hm.put("posicion_promedio", h.getPosicionPromedio());
            hm.put("en_venta", h.getEnVenta());
            hm.put("precio_venta", h.getPrecioVenta());
            return hm;
        }).collect(Collectors.toList());

        map.put("caballos", horsesData);
        return map;
    }

    public Map<String, Object> getPaginatedUsers(int page, int size, String search) {
        int pageNumber = Math.max(1, page);
        int pageSize = Math.max(1, Math.min(100, size));
        String query = search != null ? search.trim().toLowerCase() : "";

        List<Usuario> all = usuarioRepository.findAll().stream()
                .filter(u -> query.isEmpty() || u.getUsername().toLowerCase().contains(query))
                .collect(Collectors.toList());

        int totalUsers = all.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalUsers / pageSize));
        int fromIndex = Math.min((pageNumber - 1) * pageSize, totalUsers);
        int toIndex = Math.min(fromIndex + pageSize, totalUsers);

        List<Map<String, Object>> userList = all.subList(fromIndex, toIndex).stream()
                .map(u -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", u.getId());
                    map.put("username", u.getUsername());
                    map.put("profile_photo", u.getProfilePhoto());
                    map.put("profilePhoto", u.getProfilePhoto());
                    map.put("created_at", u.getCreatedAt());
                    return map;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("users", userList);
        response.put("total_users", totalUsers);
        response.put("total_pages", totalPages);
        response.put("current_page", pageNumber);
        return response;
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
                    map.put("profilePhoto", u.getProfilePhoto());
                    return map;
                })
                .collect(Collectors.toList());
    }
}
