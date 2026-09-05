package com.ultimacuota.controllers;

import com.ultimacuota.dto.ApiResponse;
import com.ultimacuota.models.Caballo;
import com.ultimacuota.repositories.CaballoRepository;
import com.ultimacuota.repositories.InscripcionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/caballos")
@RequiredArgsConstructor
public class CaballoController {

    private final CaballoRepository caballoRepository;
    private final InscripcionRepository inscripcionRepository;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> getHorseById(@PathVariable Long id) {
        Caballo horse = caballoRepository.findById(id)
                .orElseThrow(() -> new com.ultimacuota.exceptions.ResourceNotFoundException("Caballo no encontrado"));

        Map<String, Object> map = new HashMap<>();
        map.put("id", horse.getId());
        map.put("nombre", horse.getNombre());
        map.put("edad", horse.getEdad());
        map.put("velocidad", horse.getVelocidad());
        map.put("resistencia", horse.getResistencia());
        map.put("corazon", horse.getCorazon());
        map.put("fatiga", horse.getFatiga());
        map.put("carreras_totales", horse.getCarrerasTotales());
        map.put("victorias", horse.getVictorias());
        map.put("posicion_promedio", horse.getPosicionPromedio());
        map.put("en_venta", horse.getEnVenta());
        map.put("precio_venta", horse.getPrecioVenta());
        map.put("es_bot", horse.getEsBot());
        map.put("created_at", horse.getCreatedAt());

        if (horse.getPropietario() != null) {
            Map<String, Object> owner = new HashMap<>();
            owner.put("id", horse.getPropietario().getId());
            owner.put("username", horse.getPropietario().getUsername());
            owner.put("profile_photo", horse.getPropietario().getProfilePhoto());
            map.put("owner", owner);
        }

        return ResponseEntity.ok(ApiResponse.ok(Map.of("horse", map)));
    }
}
