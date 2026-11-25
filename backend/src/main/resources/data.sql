-- Fix any users with null saldo (created before @Builder.Default fix)
UPDATE usuarios SET saldo = 1000.00 WHERE saldo IS NULL;

INSERT INTO configuracion (clave, valor, descripcion) VALUES
    ('daily_reward_amount', '500', 'Monto del reclamo diario en $CC'),
    ('gacha_cost', '300', 'Costo de una tirada gacha en $CC'),
    ('owner_commission_pct', '10', 'Porcentaje de comision al dueno del caballo ganador'),
    ('race_duration_seconds', '30', 'Duracion de la carrera simulada en segundos'),
    ('race_interval_minutes', '30', 'Intervalo de creacion automatica de carreras en minutos')
ON CONFLICT (clave) DO NOTHING;

INSERT INTO caballos (nombre, edad, velocidad, resistencia, corazon, es_bot) VALUES
    ('Huaso del Sur', 4, 72, 65, 58, true),
    ('Poncho Rojo', 3, 55, 78, 62, true),
    ('Chacarero Veloz', 5, 81, 45, 70, true),
    ('Diablo del Ranco', 6, 68, 72, 55, true),
    ('Puma Andino', 3, 75, 60, 80, true),
    ('Condor de la Frontera', 7, 60, 85, 50, true),
    ('Machaqmara', 4, 70, 55, 75, true),
    ('Trauco Legendario', 5, 82, 68, 42, true),
    ('Boroniol del Bosque', 3, 50, 70, 85, true),
    ('Chiflon del Norte', 6, 78, 62, 58, true),
    ('Trentrenlevu', 4, 65, 80, 48, true),
    ('Pinen el Indomable', 5, 73, 52, 77, true),
    ('Lonco Pilcha', 3, 58, 75, 65, true),
    ('Caluquin Relampago', 7, 85, 48, 60, true),
    ('Nizca del Valle', 4, 62, 70, 72, true),
    ('Cheuque el Veloz', 5, 77, 58, 68, true),
    ('Pillan de Fuego', 6, 68, 82, 45, true),
    ('Huechun Misterioso', 3, 55, 65, 80, true),
    ('Tralka del Cielo', 4, 80, 55, 62, true),
    ('Ruetru Tormenta', 5, 63, 78, 57, true)
ON CONFLICT DO NOTHING;

INSERT INTO configuracion (clave, valor, descripcion) VALUES
    ('lesion_leve_minutos', '60', 'Minutos para que una lesión leve sane sola'),
    ('lesion_moderada_minutos', '360', 'Minutos para que una lesión moderada sane sola'),
    ('max_jinetes_usuario', '5', 'Máximo de jinetes contratados por usuario'),
    ('felicidad_minima_carrera', '10', 'Felicidad mínima para inscribir un caballo')
ON CONFLICT (clave) DO NOTHING;

INSERT INTO jinetes (nombre, habilidad, peso, especialidad, precio_contratacion) VALUES
    ('Pedro "Látigo" Fuentealba', 92, 52, NULL, 3000),
    ('Camila Huenchullán', 85, 50, 'lluvia', 2500),
    ('Rodrigo Catrileo', 80, 54, 'barro', 2200),
    ('Javiera Millapán', 76, 49, 'sequia', 1900),
    ('Tomás Ñancupil', 72, 55, NULL, 1600),
    ('Valentina Quilodrán', 68, 51, 'lluvia', 1400),
    ('Matías Curihual', 63, 56, 'barro', 1150),
    ('Fernanda Antiñir', 58, 50, NULL, 950),
    ('Ignacio Painemal', 52, 57, 'sequia', 800),
    ('Antonia Llancaqueo', 47, 53, NULL, 650),
    ('Benjamín Calfucura', 41, 58, 'barro', 500),
    ('Sofía Huaiquimil', 35, 52, NULL, 400)
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO items_tienda (codigo, nombre, descripcion, categoria, calidad, precio, cura_nivel, reduce_fatiga, aumenta_felicidad) VALUES
    ('vendaje', 'Vendaje', 'Cura una lesión leve.', 'curacion', 'basica', 150, 1, 0, 0),
    ('kit_veterinario', 'Kit veterinario', 'Cura lesiones leves y moderadas.', 'curacion', 'estandar', 450, 2, 0, 5),
    ('cirugia_equina', 'Cirugía equina', 'Cura cualquier lesión, incluso las graves.', 'curacion', 'premium', 1200, 3, 0, 10),
    ('heno_comun', 'Heno común', 'Comida básica: algo de descanso y ánimo.', 'comida', 'basica', 60, 0, 10, 3),
    ('avena', 'Avena', 'Comida nutritiva para recuperar energía.', 'comida', 'estandar', 140, 0, 20, 8),
    ('alfalfa_premium', 'Alfalfa premium', 'La mejor comida: recupera mucha energía y alegra al caballo.', 'comida', 'premium', 320, 0, 35, 15),
    ('zanahorias', 'Zanahorias', 'Un premio que sube la felicidad.', 'cuidado', 'basica', 80, 0, 0, 10),
    ('terron_azucar', 'Terrón de azúcar', 'Un capricho dulce: mucha felicidad.', 'cuidado', 'estandar', 200, 0, 0, 20),
    ('cepillado', 'Cepillado profesional', 'Sesión de cuidado completo.', 'cuidado', 'premium', 380, 0, 10, 30),
    ('suero_electrolitico', 'Suero electrolítico', 'Rehidrata y baja mucho la fatiga.', 'cuidado', 'premium', 300, 0, 40, 0)
ON CONFLICT (codigo) DO NOTHING;
