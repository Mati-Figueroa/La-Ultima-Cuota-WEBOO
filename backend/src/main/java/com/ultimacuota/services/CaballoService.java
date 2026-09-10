package com.ultimacuota.services;

import com.ultimacuota.exceptions.ResourceNotFoundException;
import com.ultimacuota.models.Caballo;
import com.ultimacuota.repositories.CaballoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CaballoService {

    private final CaballoRepository caballoRepository;

    public Map<String, Object> getHorseById(Long id) {
        Caballo horse = caballoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Caballo no encontrado"));

        Map<String, Object> map = new HashMap<>();
        map.put("id", horse.getId());
        map.put("nombre", horse.getNombre());
        map.put("edad", horse.getEdad());
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

        return map;
    }
}
