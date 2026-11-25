package com.ultimacuota.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Borra todos los datos de prueba respetando el orden de las claves foráneas. */
@Component
public class TestDataCleaner {

    private static final String[] TABLAS = {
            "regalos", "inventario", "items_tienda", "eventos_carrera", "jinetes",
            "pujas", "subastas", "resultados_carrera", "apuestas", "inscripciones", "carreras",
            "transacciones_saldo", "caballos", "usuarios"
    };

    private final JdbcTemplate jdbc;

    public TestDataCleaner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void limpiar() {
        for (String tabla : TABLAS) {
            jdbc.update("DELETE FROM " + tabla);
        }
    }
}
