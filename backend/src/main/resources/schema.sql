-- Idempotent schema creation (safe to run multiple times)
CREATE TABLE IF NOT EXISTS usuarios (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    saldo NUMERIC(12,2) DEFAULT 1000.00,
    profile_photo TEXT NULL,
    ultima_recompensa_diaria TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Idempotent column addition for profile_photo (safe if column already exists)
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS profile_photo TEXT NULL;
ALTER TABLE usuarios ALTER COLUMN profile_photo TYPE TEXT;

CREATE TABLE IF NOT EXISTS caballos (
    id SERIAL PRIMARY KEY,
    propietario_id INTEGER REFERENCES usuarios(id) ON DELETE SET NULL,
    nombre VARCHAR(100) NOT NULL,
    edad INTEGER NOT NULL,
    velocidad INTEGER NOT NULL,
    resistencia INTEGER NOT NULL,
    corazon INTEGER NOT NULL,
    fatiga INTEGER DEFAULT 0,
    carreras_totales INTEGER DEFAULT 0,
    victorias INTEGER DEFAULT 0,
    posicion_promedio NUMERIC(4,2) NULL,
    en_venta BOOLEAN DEFAULT FALSE,
    precio_venta NUMERIC(12,2) NULL,
    es_bot BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS carreras (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(100) NULL,
    estado VARCHAR(20) DEFAULT 'programada' CHECK (estado IN ('programada', 'en_curso', 'finalizada', 'cancelada')),
    fecha_programada TIMESTAMP NOT NULL,
    fecha_inicio_real TIMESTAMP NULL,
    fecha_fin_real TIMESTAMP NULL,
    cupo_maximo INTEGER DEFAULT 12,
    tiene_interaccion_humana BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS inscripciones (
    id SERIAL PRIMARY KEY,
    carrera_id INTEGER REFERENCES carreras(id) ON DELETE CASCADE,
    caballo_id INTEGER REFERENCES caballos(id) ON DELETE CASCADE,
    usuario_id INTEGER REFERENCES usuarios(id) ON DELETE CASCADE,
    numero_carril INTEGER NULL,
    fecha_inscripcion TIMESTAMP DEFAULT NOW(),
    UNIQUE (carrera_id, caballo_id)
);

CREATE TABLE IF NOT EXISTS resultados_carrera (
    id SERIAL PRIMARY KEY,
    carrera_id INTEGER REFERENCES carreras(id) ON DELETE CASCADE,
    caballo_id INTEGER REFERENCES caballos(id) ON DELETE CASCADE,
    posicion_final INTEGER NOT NULL,
    tiempo_final NUMERIC(8,2) NULL,
    UNIQUE (carrera_id, caballo_id),
    UNIQUE (carrera_id, posicion_final)
);

CREATE TABLE IF NOT EXISTS apuestas (
    id SERIAL PRIMARY KEY,
    usuario_id INTEGER REFERENCES usuarios(id) ON DELETE CASCADE,
    carrera_id INTEGER REFERENCES carreras(id) ON DELETE CASCADE,
    caballo_id INTEGER REFERENCES caballos(id) ON DELETE CASCADE,
    monto NUMERIC(12,2) NOT NULL,
    cuota NUMERIC(6,2) NOT NULL,
    estado VARCHAR(20) DEFAULT 'pendiente' CHECK (estado IN ('pendiente', 'ganada', 'perdida', 'cancelada')),
    monto_ganado NUMERIC(12,2) NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS configuracion (
    id SERIAL PRIMARY KEY,
    clave VARCHAR(100) UNIQUE NOT NULL,
    valor VARCHAR(255) NOT NULL,
    descripcion TEXT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS transacciones_saldo (
    id SERIAL PRIMARY KEY,
    usuario_id INTEGER REFERENCES usuarios(id) ON DELETE CASCADE,
    tipo VARCHAR(30) NOT NULL CHECK (tipo IN ('compra_caballo', 'venta_caballo', 'apuesta_realizada', 'apuesta_ganada', 'moneda_diaria', 'ajuste_admin', 'comision_dueno', 'puja_subasta')),
    monto NUMERIC(12,2) NOT NULL,
    saldo_resultante NUMERIC(12,2) NOT NULL,
    referencia_tabla VARCHAR(50) NULL,
    referencia_id INTEGER NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- =============================================
-- NEW TABLES: Subastas (Auctions) + Pujas (Bids)
-- =============================================

CREATE TABLE IF NOT EXISTS subastas (
    id SERIAL PRIMARY KEY,
    caballo_id INTEGER NOT NULL REFERENCES caballos(id) ON DELETE CASCADE,
    vendedor_id INTEGER NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    precio_inicial NUMERIC(12,2) NOT NULL,
    precio_reserva NUMERIC(12,2) NULL,
    fecha_inicio TIMESTAMP NOT NULL DEFAULT NOW(),
    fecha_fin TIMESTAMP NOT NULL,
    estado VARCHAR(20) DEFAULT 'activa' CHECK (estado IN ('activa', 'finalizada', 'cancelada')),
    ganador_id INTEGER NULL REFERENCES usuarios(id) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS pujas (
    id SERIAL PRIMARY KEY,
    subasta_id INTEGER NOT NULL REFERENCES subastas(id) ON DELETE CASCADE,
    usuario_id INTEGER NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    monto NUMERIC(12,2) NOT NULL,
    fecha TIMESTAMP DEFAULT NOW(),
    es_ganadora BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (subasta_id, usuario_id)
);

-- Migrations for existing deployments
ALTER TABLE transacciones_saldo DROP CONSTRAINT IF EXISTS transacciones_saldo_tipo_check;
ALTER TABLE transacciones_saldo ALTER COLUMN referencia_id TYPE BIGINT;
ALTER TABLE pujas ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT NOW();


-- =============================================
-- Condición del caballo: felicidad y lesiones
-- =============================================
ALTER TABLE caballos ADD COLUMN IF NOT EXISTS felicidad INTEGER DEFAULT 70;
ALTER TABLE caballos ADD COLUMN IF NOT EXISTS lesion VARCHAR(20) NULL;
ALTER TABLE caballos ADD COLUMN IF NOT EXISTS lesion_hasta TIMESTAMP NULL;
UPDATE caballos SET felicidad = 70 WHERE felicidad IS NULL;
ALTER TABLE caballos DROP CONSTRAINT IF EXISTS caballos_lesion_check;
ALTER TABLE caballos ADD CONSTRAINT caballos_lesion_check CHECK (lesion IS NULL OR lesion IN ('leve', 'moderada', 'grave'));

-- =============================================
-- Jinetes
-- =============================================
CREATE TABLE IF NOT EXISTS jinetes (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(100) UNIQUE NOT NULL,
    habilidad INTEGER NOT NULL,
    peso INTEGER NOT NULL,
    experiencia INTEGER DEFAULT 0,
    especialidad VARCHAR(20) NULL CHECK (especialidad IS NULL OR especialidad IN ('lluvia', 'barro', 'sequia')),
    precio_contratacion NUMERIC(12,2) NOT NULL,
    empleador_id INTEGER NULL REFERENCES usuarios(id) ON DELETE SET NULL,
    caballo_id INTEGER NULL UNIQUE REFERENCES caballos(id) ON DELETE SET NULL,
    fecha_contratacion TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- =============================================
-- Tienda e inventario
-- =============================================
CREATE TABLE IF NOT EXISTS items_tienda (
    id SERIAL PRIMARY KEY,
    codigo VARCHAR(50) UNIQUE NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    descripcion TEXT NULL,
    categoria VARCHAR(20) NOT NULL CHECK (categoria IN ('curacion', 'comida', 'cuidado')),
    calidad VARCHAR(20) NOT NULL CHECK (calidad IN ('basica', 'estandar', 'premium')),
    precio NUMERIC(12,2) NOT NULL,
    cura_nivel INTEGER DEFAULT 0,
    reduce_fatiga INTEGER DEFAULT 0,
    aumenta_felicidad INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS inventario (
    id SERIAL PRIMARY KEY,
    usuario_id INTEGER NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    item_id INTEGER NOT NULL REFERENCES items_tienda(id) ON DELETE CASCADE,
    cantidad INTEGER NOT NULL DEFAULT 0 CHECK (cantidad >= 0),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (usuario_id, item_id)
);

-- =============================================
-- Eventos aleatorios de carrera
-- =============================================
CREATE TABLE IF NOT EXISTS eventos_carrera (
    id SERIAL PRIMARY KEY,
    carrera_id INTEGER NOT NULL REFERENCES carreras(id) ON DELETE CASCADE,
    tipo VARCHAR(30) NOT NULL,
    caballo_id INTEGER NULL REFERENCES caballos(id) ON DELETE SET NULL,
    segundo INTEGER NOT NULL DEFAULT 0,
    descripcion VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- =============================================
-- Regalos entre usuarios
-- =============================================
CREATE TABLE IF NOT EXISTS regalos (
    id SERIAL PRIMARY KEY,
    remitente_id INTEGER NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    destinatario_id INTEGER NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    monto NUMERIC(12,2) NOT NULL DEFAULT 0,
    item_id INTEGER NULL REFERENCES items_tienda(id) ON DELETE SET NULL,
    cantidad INTEGER NOT NULL DEFAULT 0,
    mensaje VARCHAR(200) NULL,
    created_at TIMESTAMP DEFAULT NOW()
);
