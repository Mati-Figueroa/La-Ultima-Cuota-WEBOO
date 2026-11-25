package com.ultimacuota.repositories;

import com.ultimacuota.models.ItemTienda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemTiendaRepository extends JpaRepository<ItemTienda, Long> {
    List<ItemTienda> findAllByOrderByCategoriaAscPrecioAsc();
}
