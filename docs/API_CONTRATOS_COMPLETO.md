# AviMax — Contratos de API Completos

> Documento generado el 2026-06-15.  
> Cubre **backend-java** (Raspberry/local, puerto 8080) y **avimax-central-backend** (central, puerto 8080).  
> Incluye: cada endpoint, request, response, entidades involucradas y relaciones entre ambos backends.

---

## Tabla de contenidos

1. [Arquitectura y relación entre backends](#1-arquitectura-y-relación-entre-backends)
2. [Convenciones comunes](#2-convenciones-comunes)
3. [BACKEND LOCAL — backend-java](#3-backend-local--backend-java)
   - [Entidades](#31-entidades-locales)
   - [Lecturas de sensores `/api/readings`](#32-lecturas-de-sensores)
   - [Parvadas `/api/flocks`](#33-parvadas)
   - [Extractores `/api/extractors`](#34-extractores)
   - [Criadoras `/api/criadoras`](#35-criadoras)
   - [Bombas `/api/bombas`](#36-bombas)
   - [Control automático `/api/control`](#37-control-automático)
   - [Control manual local `/api/local/actuadores`](#38-control-manual-local)
   - [Alarmas `/api/alarms`](#39-alarmas)
   - [Mortalidad `/api/mortalidad`](#310-mortalidad)
   - [Peso `/api/peso`](#311-peso)
   - [Consumo `/api/consumo`](#312-consumo)
   - [Dashboard `/api/dashboard`](#313-dashboard-local)
   - [Estado MQTT `/api/status`](#314-estado-mqtt-local)
4. [BACKEND CENTRAL — avimax-central-backend](#4-backend-central--avimax-central-backend)
   - [Entidades](#41-entidades-centrales)
   - [Galpones `/api/galpones`](#42-galpones)
   - [Gateways `/api/gateways`](#43-gateways)
   - [Sensores `/api/sensores`](#44-sensores)
   - [Lecturas `/api/galpones/{id}/lecturas`](#45-lecturas-de-sensores-central)
   - [Parvadas `/api/galpones/{id}/flocks`](#46-parvadas-central)
   - [Actuadores — Extractores](#47-extractores-central)
   - [Actuadores — Criadoras y Bombas](#48-criadoras-y-bombas-central)
   - [Programación sincronizada (MQTT)](#49-programación-sincronizada-vía-mqtt)
   - [Control de actuadores `/api/.../control`](#410-control-de-actuadores-central)
   - [Alarmas `/api/alarms`](#411-alarmas-central)
   - [Mortalidad / Peso / Consumo](#412-registros-productivos-central)
   - [Provisioning `/api/provisioning`](#413-provisioning)
   - [Sync Events `/api/sync`](#414-sync-events)
   - [Usuarios `/api/users`](#415-usuarios)
   - [Dashboard `/api/dashboard`](#416-dashboard-central)
   - [Estado MQTT `/api/status`](#417-estado-mqtt-central)
5. [Contratos MQTT entre backends](#5-contratos-mqtt-entre-backends)
6. [Mapa de relaciones: endpoint local ↔ central](#6-mapa-de-relaciones-endpoint-local--central)
7. [Tabla de diferencias clave](#7-tabla-de-diferencias-clave)

---

## 1. Arquitectura y relación entre backends

```
┌─────────────────────────────────────────────────────────────────────┐
│  RASPBERRY PI (backend-java)           SERVIDOR CENTRAL             │
│  Puerto 8080 · TimescaleDB :5432       Puerto 8080 · PostgreSQL :5434│
│                                                                     │
│  ┌─────────────────┐   MQTT QoS=1    ┌──────────────────────────┐  │
│  │  Sensors MQTT   │ ──────────────▶ │  avimax-central-backend  │  │
│  │  Lecturas       │                 │  (ingesta, dashboard,     │  │
│  │  Mortalidad     │ ──────────────▶ │   alarmas centrales)      │  │
│  │  Peso, Consumo  │                 │                          │  │
│  │  Actuator State │ ──────────────▶ │                          │  │
│  └────────┬────────┘                 └───────────┬──────────────┘  │
│           │ escucha                              │ envía            │
│           │                                     │                  │
│  ┌────────▼────────┐   MQTT QoS=1    ┌───────────▼──────────────┐  │
│  │ Comandos cmd    │ ◀────────────── │  Comandos actuadores     │  │
│  │ Programación    │ ◀────────────── │  Programación actuadores │  │
│  └─────────────────┘                 └──────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

**Flujo de datos principal:**
1. Sensores MQTT publican lecturas → `backend-java` las almacena en TimescaleDB
2. `backend-java` evalúa programación y cambia estado de actuadores localmente
3. `backend-java` publica sync de lecturas/mortalidad/peso/actuadores al central vía MQTT
4. Central recibe lecturas → evalúa alarmas → genera comandos PENDING
5. Frontend llama a central para despachar comandos
6. Central publica comando MQTT → `backend-java` lo recibe, aplica, responde

---

## 2. Convenciones comunes

### Wrapper de respuesta local (`ApiResponse<T>`)
La mayoría de endpoints del backend local retornan:
```json
{
  "data": { ... },
  "error": null,
  "status": 200
}
```

### Fechas
- Formato ISO-8601 con offset: `2026-06-15T10:30:00Z`
- Fechas de parvada/mortalidad/peso: `2026-06-15` (LocalDate)

### Puertos
- **backend-java**: `:8080` (no cambiar si corre solo; usar `SERVER_PORT=8081` si corre junto al central en la misma máquina)
- **avimax-central-backend**: `:8080` (dentro de Docker Compose)

### Enums locales (backend-java)
| Enum | Valores |
|------|---------|
| `AlarmVariable` | `TEMPERATURA`, `HUMEDAD`, `AMONIACO` |
| `AlarmCondition` | `MAYOR`, `MAYOR_IGUAL`, `MENOR`, `MENOR_IGUAL`, `IGUAL` |
| `AlarmSeverity` (local) | `BAJA`, `MEDIA`, `ALTA`, `CRITICA` |
| `FlockStatus` | `ACTIVE`, `CLOSED` |
| `Gender` (peso) | `MALE`, `FEMALE` |
| `WeightLocation` | `NORTH`, `SOUTH`, `CENTER`, `EAST`, `WEST` |

### Enums centrales (avimax-central-backend)
| Enum | Valores |
|------|---------|
| `AlarmSeverity` (central) | `BAJA`, `MEDIA`, `ALTA`, `CRITICA` |
| `conditionType` (string) | `MAYOR_QUE`, `MENOR_QUE`, `IGUAL_A`, `RANGO` |
| `variable` alarma (string) | `TEMPERATURA`, `HUMEDAD`, `NH3` |
| `GatewayEstado` | `SIN_DATOS`, `ACTIVO`, `INACTIVO` |
| `SensorEstado` | `ACTIVO`, `INACTIVO`, `SIN_DATOS` |
| `GalponEstado` | `ACTIVO`, `INACTIVO` |

---

## 3. BACKEND LOCAL — backend-java

> Base URL: `http://localhost:8080`  
> DB: TimescaleDB, puerto 5432, base de datos `avimax`  
> Flyway: **desactivado** (migraciones manuales)

### 3.1 Entidades locales

#### `Flock` (tabla `flocks`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | BIGSERIAL PK | |
| `name` | VARCHAR(120) | Nombre de la parvada |
| `totalBirds` | INTEGER | Total de aves iniciales |
| `maleCount` | INTEGER | Machos |
| `femaleCount` | INTEGER | Hembras |
| `flockDate` | DATE | Fecha de ingreso al galpón |
| `birdLot` | VARCHAR(80) | Lote de aves |
| `notes` | TEXT | Observaciones |
| `status` | VARCHAR (enum FlockStatus) | `ACTIVE` o `CLOSED` |
| `startedAt` | TIMESTAMPTZ | Fecha de creación de la parvada |
| `endedAt` | TIMESTAMPTZ | Fecha de cierre (null si activa) |

**Restricción**: índice único `uq_flocks_single_active` — solo una parvada ACTIVE a la vez.

#### `SensorReading` (tabla `sensor_readings`, hypertable TimescaleDB)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | BIGSERIAL PK | |
| `flock` | FK → Flock | **NOT NULL**: sin parvada activa no se guarda |
| `recordedAt` | TIMESTAMPTZ | Dimensión de tiempo del hypertable |
| `gatewayId` | VARCHAR | ID del gateway que publicó |
| `sourceTopic` | VARCHAR | Topic MQTT de origen |
| `temperatureC` | DECIMAL | Temperatura en °C |
| `humidityPercent` | DECIMAL | Humedad relativa % |
| `nh3Ppm` | DECIMAL | NH3 en ppm (default 0.0 si ausente) |

#### `Extractor` (tabla `extractors`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | BIGSERIAL PK | |
| `name` | VARCHAR(100) | Nombre (ej. "Ventilador 1") |
| `enabled` | BOOLEAN | Habilitado para evaluación automática |
| `createdAt` | TIMESTAMPTZ | |

Relacionado: `ExtractorProgramming` (temperatureOn, temperatureOff), `ExtractorProgrammingHistory`.

#### `Criadora` (tabla `criadoras`)
Igual a Extractor: `id`, `name`, `enabled`, `createdAt`. Programación en `CriadoraProgramming`.

#### `Bomba` (tabla `bombas`)
Igual pero `BombaProgramming` agrega `workDurationSeconds` (duración del ciclo de trabajo).

#### `ActuatorControlState` (tabla `actuator_control_states`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `actuatorType` | VARCHAR | `EXTRACTOR`, `CRIADORA`, `BOMBA` |
| `actuatorId` | BIGINT | FK al actuador |
| `actuatorName` | VARCHAR | Nombre en el momento del estado |
| `currentState` | BOOLEAN | true=ON, false=OFF |
| `lastUpdatedAt` | TIMESTAMPTZ | |

#### `ActuatorControlCommand` (tabla `actuator_control_commands`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | BIGSERIAL PK | |
| `actuatorType` | VARCHAR | |
| `actuatorId` | BIGINT | |
| `actuatorName` | VARCHAR | |
| `command` | VARCHAR | `ON` o `OFF` |
| `workDurationSeconds` | INTEGER | Solo para bombas |
| `reason` | TEXT | Motivo del comando |
| `createdAt` | TIMESTAMPTZ | |
| `dispatchedAt` | TIMESTAMPTZ | NULL = pendiente; fecha = despachado |

#### `AlarmRule` (tabla `alarm_rules`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | BIGSERIAL PK | |
| `name` | VARCHAR(120) | |
| `variable` | AlarmVariable | `TEMPERATURA`, `HUMEDAD`, `AMONIACO` |
| `conditionType` | AlarmCondition | `MAYOR`, `MAYOR_IGUAL`, `MENOR`, `MENOR_IGUAL`, `IGUAL` |
| `threshold` | DECIMAL | Umbral de disparo |
| `unit` | VARCHAR(10) | `°C`, `%`, `ppm` |
| `minimumDurationSeconds` | INTEGER | Bug: ignorado efectivamente |
| `severity` | AlarmSeverity | `BAJA`, `MEDIA`, `ALTA`, `CRITICA` |
| `message` | VARCHAR(500) | |
| `active` | BOOLEAN | |

#### `Alarm` (tabla `alarms`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | BIGSERIAL PK | |
| `rule` | FK → AlarmRule | |
| `ruleName` | VARCHAR | Snapshot del nombre al momento de activación |
| `variable` | AlarmVariable | |
| `detectedValue` | DECIMAL | Valor que disparó la alarma |
| `threshold` | DECIMAL | |
| `unit` | VARCHAR | |
| `conditionType` | AlarmCondition | |
| `severity` | AlarmSeverity | |
| `message` | VARCHAR | |
| `status` | AlarmStatus | `ACTIVE`, `ACKNOWLEDGED`, `RESOLVED`, `CLOSED` |
| `activatedAt` | TIMESTAMPTZ | |
| `acknowledgedAt` | TIMESTAMPTZ | |
| `resolvedAt` | TIMESTAMPTZ | |
| `closedAt` | TIMESTAMPTZ | |

#### `MortalityRecord` (tabla `mortality_records`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | BIGSERIAL PK | |
| `flock` | FK → Flock | Parvada activa al momento del registro |
| `recordDate` | DATE | Fecha del registro |
| `ageDays` | INTEGER | Edad de la parvada en días (calculado) |
| `maleCount` | INTEGER | Machos muertos |
| `femaleCount` | INTEGER | Hembras muertas |
| `totalCount` | INTEGER | Total (calculado) |
| `observations` | TEXT | |
| `createdAt` | TIMESTAMPTZ | |

#### `WeightRecord` (tabla `weight_records`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | BIGSERIAL PK | |
| `flock` | FK → Flock | |
| `sampledBirdsCount` | INTEGER | Aves muestreadas |
| `averageWeight` | DECIMAL | Peso promedio en gramos |
| `age` | INTEGER | Días de vida |
| `recordDate` | DATE | |
| `gender` | Gender | `MALE`, `FEMALE` |
| `location` | WeightLocation | `NORTH`, `SOUTH`, `CENTER`, `EAST`, `WEST` |
| `createdAt` | TIMESTAMPTZ | |

#### `ConsumptionRecord` (tabla `consumption_records`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | BIGSERIAL PK | |
| `flock` | FK → Flock | |
| `age` | INTEGER | Días de vida |
| `recordDate` | DATE | |
| `totalConsumptionKg` | DECIMAL | Consumo total en kg |
| `birdsCountUsed` | INTEGER | Aves vivas al momento (calculado) |
| `consumptionPerBirdKg` | DECIMAL | Consumo por ave (calculado) |
| `createdAt` | TIMESTAMPTZ | |

#### `LocalMqttOutboxMessage` (tabla `local_mqtt_outbox_messages`)
Outbox para publicaciones MQTT fallidas. Estados: `PENDING → SENT` o `FAILED → DEAD`.

#### `ProcessedMqttCommand` / `ProcessedProgrammingConfig`
Idempotencia: almacenan `command_id` / `config_id` ya procesados para evitar re-ejecución.

---

### 3.2 Lecturas de sensores

**Controller**: `ReadingController` — `GET /api/readings`

---

#### `GET /api/readings`
Lista lecturas paginadas de la parvada activa.

**Query params:**
| Param | Tipo | Default | Descripción |
|-------|------|---------|-------------|
| `start` | ISO-8601 timestamp | — | Desde (ej. `2026-06-15T00:00:00Z`) |
| `end` | ISO-8601 timestamp | — | Hasta |
| `variable` | string | — | Filtro por variable (no implementado en backend, filtro de visualización) |
| `gateway` | string | — | Filtro por gatewayId |
| `sensor` | string | — | Filtro por sourceTopic |
| `page` | int | `0` | Página (0-based) |
| `size` | int | `100` | Tamaño de página |
| `sort` | string | — | Ej. `recordedAt,desc` |

**Respuesta `200`:**
```json
{
  "data": {
    "data": [
      {
        "id": 1,
        "flockId": 3,
        "gatewayId": "raspi5-galpon-01",
        "sensor": "avicola/galpon/1/lecturas",
        "deviceId": "avicola/galpon/1/lecturas",
        "timestamp": "2026-06-15T10:30:00Z",
        "temperatura_c": 29.5,
        "humedad_relativa": 65.0,
        "nh3_ppm": 0.0
      }
    ],
    "meta": {
      "page": 0,
      "size": 100,
      "total": 1234
    }
  },
  "error": null,
  "status": 200
}
```

---

#### `GET /api/readings/latest`
Última lectura de la parvada activa.

**Respuesta `200`:**
```json
{
  "data": {
    "id": 1245,
    "flockId": 3,
    "gatewayId": "raspi5-galpon-01",
    "sensor": "avicola/galpon/1/lecturas",
    "deviceId": "avicola/galpon/1/lecturas",
    "timestamp": "2026-06-15T10:30:00Z",
    "temperatura_c": 29.5,
    "humedad_relativa": 65.0,
    "nh3_ppm": 0.0
  },
  "error": null,
  "status": 200
}
```
**Respuesta `204`**: si no hay lecturas.

---

#### `GET /api/readings/recent`
Últimas lecturas (sin paginación, límite interno del servicio).

**Respuesta `200`:** igual a `/latest` pero `data` es un array.

---

### 3.3 Parvadas

**Controller**: `FlockController` — `GET|POST /api/flocks`

---

#### `POST /api/flocks`
Crea una nueva parvada activa. Falla si ya hay una con `status=ACTIVE`.

**Body:**
```json
{
  "name": "Parvada Junio 2026",
  "totalBirds": 12500,
  "maleCount": 6250,
  "femaleCount": 6250,
  "flockDate": "2026-06-15",
  "birdLot": "LOT-2026-06-A",
  "notes": "Primer lote 2026"
}
```
| Campo | Requerido | Reglas |
|-------|-----------|--------|
| `name` | ✅ | max 120 chars |
| `totalBirds` | ✅ | ≥1 |
| `maleCount` | ✅ | ≥0 |
| `femaleCount` | ✅ | ≥0 |
| `flockDate` | ✅ | fecha ISO |
| `birdLot` | ✅ | max 80 chars |
| `notes` | — | max 500 chars |

**Respuesta `201`:**
```json
{
  "id": 3,
  "name": "Parvada Junio 2026",
  "totalBirds": 12500,
  "maleCount": 6250,
  "femaleCount": 6250,
  "flockDate": "2026-06-15",
  "birdLot": "LOT-2026-06-A",
  "notes": "Primer lote 2026",
  "status": "ACTIVE",
  "startedAt": "2026-06-15T10:00:00Z",
  "endedAt": null
}
```

---

#### `GET /api/flocks`
Lista todas las parvadas (activas y cerradas).

**Respuesta `200`:** array de `FlockResponse` (misma estructura que el objeto de arriba).

---

#### `GET /api/flocks/active`
Retorna la parvada activa actual.

**Respuesta `200`:** `FlockResponse`  
**Respuesta `204`**: no hay parvada activa.

---

#### `POST /api/flocks/{id}/close`
Cierra la parvada. Establece `status=CLOSED` y `endedAt=now()`.

**Respuesta `200`:** `FlockResponse` con `status: "CLOSED"` y `endedAt` poblado.

---

### 3.4 Extractores

**Controller**: `ExtractorController` — `GET|POST /api/extractors`

---

#### `POST /api/extractors`
Crea un nuevo extractor.

**Body:**
```json
{ "name": "Ventilador 13" }
```
| Campo | Requerido | Reglas |
|-------|-----------|--------|
| `name` | ✅ | max 100 chars |

**Respuesta `201`:**
```json
{
  "data": {
    "id": 13,
    "name": "Ventilador 13",
    "enabled": true,
    "createdAt": "2026-06-15T10:00:00Z",
    "temperatureOn": null,
    "temperatureOff": null,
    "programmingUpdatedAt": null
  },
  "error": null,
  "status": 200
}
```

---

#### `GET /api/extractors`
Lista todos los extractores con su programación actual.

**Respuesta `200`:**
```json
{
  "data": [
    {
      "id": 1,
      "name": "Ventilador 1",
      "enabled": true,
      "createdAt": "2026-06-01T08:00:00Z",
      "temperatureOn": 28.0,
      "temperatureOff": 25.0,
      "programmingUpdatedAt": "2026-06-10T09:00:00Z"
    }
  ],
  "error": null,
  "status": 200
}
```

---

#### `PUT /api/extractors/{extractorId}/programming`
Configura o actualiza la programación de temperatura de un extractor. Guarda historial.

**Body:**
```json
{
  "temperatureOn": 28.0,
  "temperatureOff": 25.0
}
```
| Campo | Requerido |
|-------|-----------|
| `temperatureOn` | ✅ |
| `temperatureOff` | ✅ |

**Respuesta `200`:** `ApiResponse<ExtractorItemResponse>` con la programación actualizada.

**Efecto secundario**: `backend-java` publica vía MQTT (outbox) el nuevo estado de programación al central.

---

#### `GET /api/extractors/{extractorId}/history`
Historial de cambios de programación de un extractor específico.

**Query params:** `limit` (opcional, int).

**Respuesta `200`:** array de:
```json
{
  "id": 5,
  "actuatorId": 1,
  "actuatorName": "Ventilador 1",
  "actuatorType": "EXTRACTOR",
  "temperatureOn": 28.0,
  "temperatureOff": 25.0,
  "recordedAt": "2026-06-10T09:00:00Z"
}
```

---

#### `GET /api/extractors/history`
Historial de todos los extractores. Query param: `limit`.

---

### 3.5 Criadoras

**Controller**: `CriadoraController` — `GET|POST /api/criadoras`

Misma estructura que extractores. Los campos son idénticos excepto que la entidad es `Criadora`.

#### `POST /api/criadoras`
**Body:** `{ "name": "Criadora 6" }`  
**Respuesta `201`:** `ApiResponse<CriadoraItemResponse>`

#### `GET /api/criadoras`
**Respuesta `200`:** `ApiResponse<List<CriadoraItemResponse>>`

#### `PUT /api/criadoras/{criadoraId}/programming`
**Body:** `{ "temperatureOn": 33.0, "temperatureOff": 30.0 }`  
**Respuesta `200`:** `ApiResponse<CriadoraItemResponse>`

#### `GET /api/criadoras/{criadoraId}/history`
**Respuesta `200`:** array de `CriadoraHistoryResponse` (igual a `ExtractorHistoryResponse`)

#### `GET /api/criadoras/history`
Historial global. Query param: `limit`.

---

### 3.6 Bombas

**Controller**: `BombaController` — `GET|POST /api/bombas`

Igual a extractores pero incluye `workDurationSeconds` en programación.

#### `POST /api/bombas`
**Body:** `{ "name": "Bomba 3" }`  
**Respuesta `201`:** `ApiResponse<BombaItemResponse>`

#### `GET /api/bombas`
**Respuesta `200`:**
```json
{
  "data": [
    {
      "id": 1,
      "name": "Bomba 1",
      "enabled": true,
      "createdAt": "2026-06-01T08:00:00Z",
      "temperatureOn": 26.0,
      "temperatureOff": 24.0,
      "workDurationSeconds": 300,
      "programmingUpdatedAt": "2026-06-10T09:00:00Z"
    }
  ],
  "error": null,
  "status": 200
}
```

#### `PUT /api/bombas/{bombaId}/programming`
**Body:**
```json
{
  "temperatureOn": 26.0,
  "temperatureOff": 24.0,
  "workDurationSeconds": 300
}
```
| Campo | Requerido | Reglas |
|-------|-----------|--------|
| `temperatureOn` | ✅ | |
| `temperatureOff` | ✅ | |
| `workDurationSeconds` | ✅ | ≥1 |

**Respuesta `200`:** `ApiResponse<BombaItemResponse>`

#### `GET /api/bombas/{bombaId}/history`
**Respuesta `200`:** array de `BombaHistoryResponse` (incluye `workDurationSeconds`):
```json
{
  "id": 3,
  "actuatorId": 1,
  "actuatorName": "Bomba 1",
  "actuatorType": "BOMBA",
  "temperatureOn": 26.0,
  "temperatureOff": 24.0,
  "workDurationSeconds": 300,
  "recordedAt": "2026-06-10T09:00:00Z"
}
```

#### `GET /api/bombas/history`
Historial global. Query param: `limit`.

---

### 3.7 Control automático

**Controller**: `ControlController` — `/api/control`

---

#### `POST /api/control/evaluate/latest`
Evalúa la última lectura y genera comandos PENDING para actuadores según programación de temperatura (histéresis).

**Body:** ninguno.

**Respuesta `200`:**
```json
{
  "evaluatedAt": "2026-06-15T10:30:01Z",
  "temperatureC": 29.5,
  "humidityPercent": 65.0,
  "nh3Ppm": 0.0,
  "counts": {
    "extractorsTotal": 12,
    "extractorsOn": 8,
    "criadorasTotal": 5,
    "criadorasOn": 0,
    "bombasTotal": 2,
    "bombasOn": 0
  },
  "signals": [
    {
      "commandId": 42,
      "actuatorType": "EXTRACTOR",
      "actuatorId": 3,
      "actuatorName": "Ventilador 3",
      "command": "ON",
      "workDurationSeconds": null,
      "reason": "Temperatura 29.5°C supera umbral ON 28.0°C",
      "createdAt": "2026-06-15T10:30:01Z"
    }
  ]
}
```

**Error `500`**: si no hay lecturas disponibles.

---

#### `GET /api/control/commands/pending`
Lista comandos generados automáticamente que aún no han sido despachados (`dispatchedAt IS NULL`).

**Respuesta `200`:** array de `ActuatorSignalResponse`:
```json
[
  {
    "commandId": 42,
    "actuatorType": "EXTRACTOR",
    "actuatorId": 3,
    "actuatorName": "Ventilador 3",
    "command": "ON",
    "workDurationSeconds": null,
    "reason": "Temperatura 29.5°C supera umbral ON 28.0°C",
    "createdAt": "2026-06-15T10:30:01Z"
  }
]
```

---

#### `POST /api/control/commands/{commandId}/dispatch`
Marca un comando como despachado (establece `dispatchedAt=now()`). No envía MQTT — sólo marca el registro.

**Respuesta `200`:** `ActuatorSignalResponse` del comando actualizado.

---

### 3.8 Control manual local

**Controller**: `LocalActuatorControlController` — `/api/local/actuadores`

---

#### `POST /api/local/actuadores/manual`
Aplica un comando manual inmediato sobre un actuador. Actualiza estado, publica MQTT vía outbox.

**Body:**
```json
{
  "actuatorType": "EXTRACTOR",
  "actuatorId": 1,
  "action": "ON",
  "reason": "Prueba manual",
  "workDurationSeconds": null
}
```
| Campo | Requerido | Valores |
|-------|-----------|---------|
| `actuatorType` | ✅ | `EXTRACTOR`, `CRIADORA`, `BOMBA` |
| `actuatorId` | ✅ | ID del actuador |
| `action` | ✅ | `ON`, `OFF` |
| `reason` | — | texto libre |
| `workDurationSeconds` | — | solo para `BOMBA` con `ON`; si `null` usa el de la programación |

**Respuesta `200`:**
```json
{
  "actuatorType": "EXTRACTOR",
  "actuatorId": 1,
  "actuatorName": "Ventilador 1",
  "action": "ON",
  "state": true,
  "reason": "Prueba manual",
  "triggeredBy": "MANUAL_LOCAL",
  "workDurationSeconds": null,
  "changedAt": "2026-06-15T10:30:05Z"
}
```

`triggeredBy` posibles: `MANUAL_LOCAL`, `CENTRAL_COMMAND`, `LOCAL_EVALUATION`, `AUTO_OFF`

---

### 3.9 Alarmas

**Controller**: `AlarmController` — `/api/alarms`

---

#### `POST /api/alarms/rules`
Crea una nueva regla de alarma.

**Body:**
```json
{
  "nombre": "Temperatura crítica",
  "variable": "TEMPERATURA",
  "condicion": "MAYOR",
  "umbral": 35.0,
  "unidad": "°C",
  "tiempoMinimoSegundos": 60,
  "severidad": "CRITICA",
  "mensaje": "Temperatura superó el umbral crítico",
  "activa": true
}
```
| Campo | Requerido | Valores |
|-------|-----------|---------|
| `nombre` | ✅ | max 120 |
| `variable` | ✅ | `TEMPERATURA`, `HUMEDAD`, `AMONIACO` |
| `condicion` | ✅ | `MAYOR`, `MAYOR_IGUAL`, `MENOR`, `MENOR_IGUAL`, `IGUAL` |
| `umbral` | ✅ | número decimal |
| `unidad` | ✅ | `°C`, `%`, `ppm` (max 10) |
| `tiempoMinimoSegundos` | ✅ | ≥0 (bug: ignorado) |
| `severidad` | ✅ | `BAJA`, `MEDIA`, `ALTA`, `CRITICA` |
| `mensaje` | ✅ | max 500 |
| `activa` | ✅ | boolean |

**Respuesta `201`:**
```json
{
  "idRegla": 1,
  "nombre": "Temperatura crítica",
  "variableMonitoreada": "temperatura",
  "condicion": "mayor",
  "umbral": 35.0,
  "unidad": "°C",
  "tiempoMinimoSegundos": 60,
  "severidad": "critica",
  "mensaje": "Temperatura superó el umbral crítico",
  "activa": true,
  "fechaCreacion": "2026-06-15T10:00:00Z",
  "fechaActualizacion": "2026-06-15T10:00:00Z"
}
```

---

#### `GET /api/alarms/rules`
Lista todas las reglas.

**Respuesta `200`:** array de `AlarmRuleResponse`.

---

#### `PUT /api/alarms/rules/{ruleId}`
Actualiza una regla existente. Mismo body que POST pero sin `activa`.

**Body:** igual a `CreateAlarmRuleRequest` sin `activa`.  
**Respuesta `200`:** `AlarmRuleResponse`.

---

#### `PATCH /api/alarms/rules/{ruleId}/active`
Activa o desactiva una regla.

**Body:** `{ "activa": false }`  
**Respuesta `200`:** `AlarmRuleResponse`.

---

#### `GET /api/alarms/active`
Lista alarmas activas (`status=ACTIVE` o `ACKNOWLEDGED`).

**Respuesta `200`:** array de `AlarmResponse`:
```json
[
  {
    "idAlarma": 7,
    "idRegla": 1,
    "nombreRegla": "Temperatura crítica",
    "variable": "temperatura",
    "valorDetectado": 36.2,
    "umbral": 35.0,
    "unidad": "°C",
    "condicion": "mayor",
    "severidad": "critica",
    "mensaje": "Temperatura superó el umbral crítico",
    "estado": "active",
    "fechaActivacion": "2026-06-15T10:30:00Z",
    "fechaReconocimiento": null,
    "fechaResolucion": null,
    "fechaCierre": null
  }
]
```

---

#### `GET /api/alarms/history`
Lista todas las alarmas (historial completo).

**Respuesta `200`:** array de `AlarmResponse`.

---

#### `GET /api/alarms/{alarmId}/events`
Eventos de una alarma específica (activación, reconocimiento, cierre).

**Respuesta `200`:** array de `AlarmEventResponse`:
```json
[
  {
    "idEvento": 1,
    "idAlarma": 7,
    "tipoEvento": "ACTIVATED",
    "estadoAnterior": null,
    "estadoNuevo": "active",
    "descripcion": "Condición de alarma detectada",
    "fechaEvento": "2026-06-15T10:30:00Z"
  }
]
```

---

#### `POST /api/alarms/{alarmId}/acknowledge`
Reconoce una alarma activa. Cambia estado a `ACKNOWLEDGED`.

**Body:** ninguno.  
**Respuesta `200`:** `AlarmResponse` con `estado: "acknowledged"`.

---

#### `POST /api/alarms/{alarmId}/close`
Cierra una alarma. Cambia estado a `CLOSED`.

**Body:** ninguno.  
**Respuesta `200`:** `AlarmResponse` con `estado: "closed"`.

---

### 3.10 Mortalidad

**Controller**: `MortalityController` — `/api/mortalidad`

---

#### `POST /api/mortalidad`
Registra mortalidad para la parvada activa. Calcula `ageDays` y `totalCount` automáticamente. Publica a central vía MQTT outbox.

**Body:**
```json
{
  "maleCount": 3,
  "femaleCount": 2,
  "observations": "Causas desconocidas"
}
```
| Campo | Requerido | Reglas |
|-------|-----------|--------|
| `maleCount` | ✅ | ≥0 |
| `femaleCount` | ✅ | ≥0 |
| `observations` | — | texto libre |

**Respuesta `201`:**
```json
{
  "id": 15,
  "recordDate": "2026-06-15",
  "ageDays": 12,
  "maleCount": 3,
  "femaleCount": 2,
  "totalCount": 5,
  "observations": "Causas desconocidas",
  "createdAt": "2026-06-15T10:30:00Z"
}
```

---

#### `GET /api/mortalidad`
Lista todos los registros de mortalidad de la parvada activa.

**Query params:** `from` (LocalDate), `to` (LocalDate) — opcionales, filtran por rango de fechas.

**Respuesta `200`:** array de `MortalityResponse`.

---

### 3.11 Peso

**Controller**: `WeightController` — `/api/peso`

---

#### `POST /api/peso`
Registra un pesaje para la parvada activa. Publica a central vía MQTT outbox.

**Body:**
```json
{
  "sampledBirdsCount": 50,
  "averageWeight": 1850.5,
  "age": 35,
  "recordDate": "2026-06-15",
  "gender": "MALE",
  "location": "CENTER"
}
```
| Campo | Requerido | Valores |
|-------|-----------|---------|
| `sampledBirdsCount` | — | entero |
| `averageWeight` | — | en gramos |
| `age` | — | días de vida |
| `recordDate` | — | fecha ISO |
| `gender` | — | `MALE`, `FEMALE` |
| `location` | — | `NORTH`, `SOUTH`, `CENTER`, `EAST`, `WEST` |

**Respuesta `201`:** `WeightResponse`:
```json
{
  "id": 20,
  "flockId": 3,
  "sampledBirdsCount": 50,
  "averageWeight": 1850.5,
  "age": 35,
  "recordDate": "2026-06-15",
  "gender": "MALE",
  "location": "CENTER",
  "createdAt": "2026-06-15T10:30:00Z"
}
```

---

#### `GET /api/peso`
Lista todos los registros de peso.

**Respuesta `200`:** array de `WeightResponse`.

---

#### `GET /api/peso/flock/{flockId}`
Registros de peso de una parvada específica.

---

#### `GET /api/peso/flock/{flockId}/gender/{gender}`
Registros por parvada y género (`MALE` o `FEMALE`).

---

#### `GET /api/peso/flock/{flockId}/latest/gender/{gender}`
Último registro de peso para parvada + género.

---

#### `GET /api/peso/latest/male`
Último pesaje masculino de la parvada activa.

---

#### `GET /api/peso/latest/female`
Último pesaje femenino de la parvada activa.

---

#### `GET /api/peso/flock/{flockId}/range`
**Query params:** `from` (LocalDate), `to` (LocalDate).  
Registros en rango de fechas.

---

### 3.12 Consumo

**Controller**: `ConsumptionController` — `/api/consumo`

---

#### `POST /api/consumo`
Registra consumo de alimento para la parvada activa. Calcula `consumptionPerBirdKg` y `birdsCountUsed`. Publica a central vía MQTT outbox.

**Body:**
```json
{
  "age": 35,
  "recordDate": "2026-06-15",
  "totalConsumptionKg": 187.5
}
```

**Respuesta `201`:**
```json
{
  "id": 10,
  "flockId": 3,
  "age": 35,
  "recordDate": "2026-06-15",
  "totalConsumptionKg": 187.5,
  "birdsCountUsed": 12490,
  "consumptionPerBirdKg": 0.015013,
  "createdAt": "2026-06-15T10:30:00Z"
}
```

---

#### `GET /api/consumo`
Lista todos los registros de consumo.

---

#### `GET /api/consumo/flock/{flockId}`
Registros de consumo de una parvada específica.

---

### 3.13 Dashboard local

**Controller**: `DashboardController` — `/api/dashboard`

---

#### `GET /api/dashboard/principal`
Dashboard principal con estado actual del galpón.

**Respuesta `200`:**
```json
{
  "data": {
    "galpon_id": 1,
    "parvada": {
      "parvada_id": 3,
      "fecha_ingreso": "2026-06-01",
      "edad_dias": 14,
      "aves_vivas": 12490
    },
    "peso_actual": {
      "fecha_registro": "2026-06-14",
      "peso_promedio_kg": 1.8505
    },
    "telemetria_actual": {
      "event_time": "2026-06-15T10:30:00Z",
      "temperatura_c": 29.5,
      "humedad_relativa": 65.0,
      "nh3_ppm": 0.0
    },
    "telemetria_min_max_dia": {
      "temperatura_c": { "min": 22.1, "max": 31.5 },
      "humedad_relativa": { "min": 58.0, "max": 72.0 },
      "nh3_ppm": { "min": 0.0, "max": 2.5 }
    }
  },
  "meta": {
    "generated_at": "2026-06-15T10:30:01Z",
    "status": "OK"
  }
}
```

---

### 3.14 Estado MQTT local

**Controller**: `StatusController` — `/api/status`

---

#### `GET /api/status/mqtt`
Estado de la conexión MQTT del backend local.

**Respuesta `200`:**
```json
{
  "connected": true,
  "subscribedTopic": "avicola/galpon/1/lecturas",
  "lastMessageReceivedAt": "2026-06-15T10:30:15Z",
  "totalMessagesReceived": 1234,
  "connectionStatus": "CONNECTED",
  "lastError": null,
  "lastErrorAt": null,
  "brokerUrl": "tcp://localhost:1883"
}
```
`connectionStatus` posibles: `CONNECTED`, `DISCONNECTED`, `CONNECTING_ERROR`, `INITIALIZING`

---

#### `GET /api/status/health`
Estado general del sistema.

**Respuesta `200`:**
```json
{
  "status": "UP",
  "message": "Sistema operativo. MQTT conectado.",
  "mqtt": { "...": "igual a /status/mqtt" }
}
```
`status` posibles: `UP`, `DEGRADED`, `DOWN`, `ERROR`

---

## 4. BACKEND CENTRAL — avimax-central-backend

> Base URL: `http://localhost:8080`  
> DB: PostgreSQL, puerto 5434, base de datos `avimax_central`  
> Flyway: **habilitado** (migraciones V1–V9 aplicadas automáticamente)

### 4.1 Entidades centrales

#### `Galpon` (tabla `galpones`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | BIGSERIAL PK | |
| `codigo` | VARCHAR UNIQUE | Código único (ej. `GAL-01`) |
| `nombre` | VARCHAR | Nombre descriptivo |
| `ubicacion` | VARCHAR | Ubicación física |
| `estado` | GalponEstado | `ACTIVO`, `INACTIVO` |
| `createdAt` | TIMESTAMPTZ | |
| `updatedAt` | TIMESTAMPTZ | |

#### `Gateway` (tabla `gateways`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | BIGSERIAL PK | |
| `galponId` | FK → Galpon | |
| `gatewayCode` | VARCHAR UNIQUE | ID único del gateway (ej. `raspi5-galpon-01`) |
| `nombre` | VARCHAR | |
| `tipo` | VARCHAR | Ej. `RASPBERRY_PI` |
| `ipAddress` | VARCHAR | IP (opcional) |
| `estado` | GatewayEstado | `SIN_DATOS`, `ACTIVO`, `INACTIVO` |
| `ultimaConexion` | TIMESTAMPTZ | |
| `createdAt` | TIMESTAMPTZ | |

#### `Sensor` (tabla `sensores`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | BIGSERIAL PK | |
| `gatewayId` | FK → Gateway | |
| `codigo` | VARCHAR | Código del sensor |
| `nombre` | VARCHAR | |
| `tipo` | VARCHAR | Ej. `TEMPERATURA`, `HUMEDAD`, `NH3` |
| `ubicacion` | VARCHAR | Posición física |
| `unidad` | VARCHAR | `°C`, `%`, `ppm` |
| `rangoMin` | DECIMAL | |
| `rangoMax` | DECIMAL | |
| `calibracionOffset` | DECIMAL | |
| `estado` | SensorEstado | `ACTIVO`, `INACTIVO`, `SIN_DATOS` |
| `ultimaLectura` | TIMESTAMPTZ | |
| `createdAt` | TIMESTAMPTZ | |

#### `SensorReading` central (tabla `sensor_readings`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | BIGSERIAL PK | |
| `galponId` | FK → Galpon | |
| `gatewayId` | FK → Gateway | |
| `sensorId` | FK → Sensor (nullable) | |
| `flockId` | FK → Flock (nullable) | Nullable a diferencia del local |
| `recordedAt` | TIMESTAMPTZ | |
| `temperatureC` | DECIMAL | |
| `humidityPercent` | DECIMAL | |
| `nh3Ppm` | DECIMAL | |
| `rawPayload` | TEXT | Payload MQTT original |

#### `Flock` central (tabla `flocks`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | BIGSERIAL PK | |
| `galponId` | FK → Galpon | A diferencia del local que no tiene galponId |
| `name` | VARCHAR | |
| `status` | VARCHAR | `ACTIVE`, `CLOSED` |
| `startedAt` | TIMESTAMPTZ | |
| `endedAt` | TIMESTAMPTZ | |

#### `Extractor` central (tabla `extractors`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | BIGSERIAL PK | |
| `galponId` | FK → Galpon | |
| `name` | VARCHAR | Ej. `Extractor 1` |
| `codeName` | VARCHAR | Ej. `EXT-01` |
| `estado` | VARCHAR | `ON`, `OFF` |
| `createdAt` | TIMESTAMPTZ | |

Igual para `Criadora` (`CRI-01`) y `Bomba` (`BOM-01`).

#### `AlarmRule` central (tabla `alarm_rules`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | BIGSERIAL PK | |
| `galponId` | FK → Galpon | |
| `name` | VARCHAR | |
| `variable` | VARCHAR | `TEMPERATURA`, `HUMEDAD`, `NH3` |
| `conditionType` | VARCHAR | `MAYOR_QUE`, `MENOR_QUE`, `IGUAL_A`, `RANGO` |
| `threshold` | DECIMAL | |
| `unit` | VARCHAR | |
| `minimumDurationSeconds` | INTEGER | |
| `severity` | AlarmSeverity | `BAJA`, `MEDIA`, `ALTA`, `CRITICA` |
| `message` | VARCHAR | |
| `active` | BOOLEAN | |

#### `ActuatorControlCommand` central
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | BIGSERIAL PK | |
| `galponId` | FK → Galpon | |
| `gatewayId` | FK → Gateway | |
| `actuatorType` | VARCHAR | `EXTRACTOR`, `CRIADORA`, `BOMBA` |
| `actuatorId` | BIGINT | ID del actuador en el central |
| `actuatorName` | VARCHAR | |
| `action` | VARCHAR | `ON`, `OFF` |
| `status` | VARCHAR | `PENDING`, `SENT`, `EXECUTED`, `FAILED` |
| `reason` | TEXT | |
| `workDurationSeconds` | INTEGER | |
| `createdAt` | TIMESTAMPTZ | |
| `sentAt` | TIMESTAMPTZ | |
| `executedAt` | TIMESTAMPTZ | |

#### `SyncEvent` (tabla `sync_events`)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | UUID PK | |
| `galponId` | FK → Galpon | |
| `eventType` | VARCHAR | Tipo de evento de sincronización |
| `payload` | TEXT | Bug: campo con `insertable=false,updatable=false` → siempre null |
| `status` | VARCHAR | `PENDING`, `ACKNOWLEDGED` |
| `createdAt` | TIMESTAMPTZ | |
| `acknowledgedAt` | TIMESTAMPTZ | |

#### `User` / `UserGalponAccess`
Usuarios del sistema con acceso a galpones. Sin autenticación real (no hay Spring Security).

---

### 4.2 Galpones

**Controller**: `GalponController` — `/api/galpones`

---

#### `GET /api/galpones`
Lista todos los galpones.

**Respuesta `200`:** array de `Galpon` (entidad directa):
```json
[
  {
    "id": 1,
    "codigo": "GAL-01",
    "nombre": "Galpón 1",
    "ubicacion": "Sector Norte",
    "estado": "ACTIVO",
    "createdAt": "2026-06-01T00:00:00Z",
    "updatedAt": "2026-06-01T00:00:00Z"
  }
]
```

---

#### `POST /api/galpones`
Crea un galpón manualmente (alternativa al provisioning).

**Body:** objeto `Galpon` (entidad directa, no DTO):
```json
{
  "codigo": "GAL-02",
  "nombre": "Galpón 2",
  "ubicacion": "Sector Sur",
  "estado": "ACTIVO"
}
```

**Respuesta `201`:** `Galpon` creado. Header `Location: /api/galpones/{id}`.

---

#### `GET /api/galpones/{id}`
Obtiene un galpón por ID.

**Respuesta `200`:** `Galpon`.  
**Respuesta `404`**: no encontrado.

---

### 4.3 Gateways

**Controller**: `GatewayController` — sin `@RequestMapping` base (paths completos en cada método)

---

#### `GET /api/gateways`
Lista todos los gateways.

**Respuesta `200`:** array de `GatewayResponseDto`:
```json
[
  {
    "id": 1,
    "galponId": 1,
    "gatewayCode": "raspi5-galpon-01",
    "nombre": "Gateway Galpón 1",
    "tipo": "RASPBERRY_PI",
    "ipAddress": null,
    "estado": "SIN_DATOS",
    "ultimaConexion": "2026-06-15T10:00:00Z",
    "createdAt": "2026-06-01T00:00:00Z"
  }
]
```

---

#### `POST /api/gateways`
Crea un gateway.

**Body:** `GatewayRequestDto`:
```json
{
  "galponId": 1,
  "gatewayCode": "raspi5-galpon-02",
  "nombre": "Gateway Galpón 2",
  "tipo": "RASPBERRY_PI",
  "ipAddress": "192.168.1.101",
  "estado": "SIN_DATOS"
}
```
| Campo | Requerido |
|-------|-----------|
| `galponId` | ✅ |
| `gatewayCode` | ✅ |
| `nombre` | ✅ |
| `tipo` | — |
| `ipAddress` | — |
| `estado` | — (default `SIN_DATOS`) |

**Respuesta `201`:** `GatewayResponseDto`.

---

#### `GET /api/gateways/{id}`
**Respuesta `200`:** `GatewayResponseDto`.

---

#### `PUT /api/gateways/{id}`
Actualiza un gateway. Body igual a POST.

**Respuesta `200`:** `GatewayResponseDto`.

---

#### `GET /api/galpones/{galponId}/gateways`
Lista gateways de un galpón específico.

**Respuesta `200`:** array de `GatewayResponseDto`.

---

### 4.4 Sensores

**Controller**: `SensorController` — sin `@RequestMapping` base

---

#### `GET /api/sensores`
Lista todos los sensores.

**Respuesta `200`:** array de `SensorResponseDto`:
```json
[
  {
    "id": 1,
    "gatewayId": 1,
    "codigo": "SEN-01",
    "nombre": "Sensor Temperatura 1",
    "tipo": "TEMPERATURA",
    "ubicacion": "Centro",
    "unidad": "°C",
    "rangoMin": -10.0,
    "rangoMax": 60.0,
    "calibracionOffset": 0.0,
    "estado": "SIN_DATOS",
    "ultimaLectura": null,
    "createdAt": "2026-06-01T00:00:00Z"
  }
]
```

---

#### `POST /api/sensores`
Crea un sensor.

**Body:** `SensorRequestDto`:
```json
{
  "gatewayId": 1,
  "codigo": "SEN-04",
  "nombre": "Sensor NH3",
  "tipo": "NH3",
  "ubicacion": "Galpón 1 Centro",
  "unidad": "ppm",
  "rangoMin": 0.0,
  "rangoMax": 100.0,
  "calibracionOffset": 0.0,
  "estado": "ACTIVO"
}
```
| Campo | Requerido |
|-------|-----------|
| `gatewayId` | ✅ |
| `codigo` | ✅ |
| `nombre` | ✅ |
| resto | — |

**Respuesta `201`:** `SensorResponseDto`.

---

#### `GET /api/sensores/{id}`
**Respuesta `200`:** `SensorResponseDto`.

---

#### `PUT /api/sensores/{id}`
Actualiza un sensor. Body igual a POST.

**Respuesta `200`:** `SensorResponseDto`.

---

#### `GET /api/gateways/{gatewayId}/sensores`
Sensores asociados a un gateway.

**Respuesta `200`:** array de `SensorResponseDto`.

---

#### `GET /api/galpones/{galponId}/sensores`
Sensores de todos los gateways de un galpón.

**Respuesta `200`:** array de `SensorResponseDto`.

---

### 4.5 Lecturas de sensores (central)

**Controller**: `SensorReadingController` — `/api/galpones/{galponId}/lecturas`

Las lecturas llegan principalmente por MQTT. Estos endpoints son de consulta.

---

#### `GET /api/galpones/{galponId}/lecturas`
Lista paginada de lecturas de un galpón.

**Query params:**
| Param | Tipo | Default | Descripción |
|-------|------|---------|-------------|
| `start` | OffsetDateTime | — | Desde |
| `end` | OffsetDateTime | — | Hasta |
| `variable` | string | — | Filtro variable |
| `gatewayId` | Long | — | Filtro gateway |
| `gatewayCode` | string | — | Filtro por código de gateway |
| `sensorId` | Long | — | Filtro sensor |
| `sensor` | string | — | Filtro por nombre de sensor |
| `page` | int | `0` | |
| `size` | int | `100` | |
| `sort` | string | `recordedAt,desc` | |

**Respuesta `200`:** `PagedResponse<SensorReading>` (entidad directa):
```json
{
  "content": [
    {
      "id": 1,
      "galponId": 1,
      "gatewayId": 1,
      "sensorId": null,
      "flockId": 2,
      "recordedAt": "2026-06-15T10:30:00Z",
      "temperatureC": 29.5,
      "humidityPercent": 65.0,
      "nh3Ppm": 0.0,
      "rawPayload": "{...}"
    }
  ],
  "page": 0,
  "size": 100,
  "totalElements": 5000,
  "totalPages": 50,
  "last": false
}
```

---

#### `GET /api/galpones/{galponId}/lecturas/recent`
Lecturas recientes (último día por default).

**Query params:** `start` y `end` (OffsetDateTime, opcionales).

**Respuesta `200`:** array de `SensorReading` (entidad directa).

---

#### `GET /api/galpones/{galponId}/lecturas/latest`
Última lectura del galpón.

**Respuesta `200`:** `SensorReading` (entidad directa).  
**Respuesta `204`:** sin lecturas.

---

### 4.6 Parvadas (central)

**Controller**: `FlockController` — `/api/galpones/{galponId}/flocks`

---

#### `GET /api/galpones/{galponId}/flocks`
Lista parvadas de un galpón.

**Respuesta `200`:** array de `Flock` (entidad directa):
```json
[
  {
    "id": 1,
    "galponId": 1,
    "name": "Parvada Demo",
    "status": "ACTIVE",
    "startedAt": "2026-06-01T00:00:00Z",
    "endedAt": null
  }
]
```

---

#### `POST /api/galpones/{galponId}/flocks`
Crea una parvada en el galpón.

**Body:** objeto `Flock` (entidad directa):
```json
{
  "name": "Parvada Julio 2026",
  "status": "ACTIVE"
}
```

**Respuesta `201`:** `Flock`.

---

#### `GET /api/galpones/{galponId}/flocks/active`
Parvada activa del galpón.

**Respuesta `200`:** `Flock`.  
**Respuesta `204`:** sin parvada activa.

---

#### `POST /api/galpones/{galponId}/flocks/{flockId}/close`
Cierra una parvada.

**Respuesta `200`:** `Flock` con `status: "CLOSED"`.

---

### 4.7 Extractores (central)

**Controller**: `ActuatorController` — `/api/galpones/{galponId}`

---

#### `GET /api/galpones/{galponId}/actuadores`
Lista actuadores del galpón.  
**⚠️ Bug conocido**: solo retorna extractores independientemente del tipo solicitado.

**Respuesta `200`:** array de `Extractor` (entidad directa):
```json
[
  {
    "id": 1,
    "galponId": 1,
    "name": "Extractor 1",
    "codeName": "EXT-01",
    "estado": "OFF",
    "createdAt": "2026-06-01T00:00:00Z"
  }
]
```

---

#### `POST /api/galpones/{galponId}/extractors`
Crea un extractor en el galpón.

**Body:** objeto `Extractor` parcial:
```json
{
  "name": "Extractor 2",
  "codeName": "EXT-02",
  "estado": "OFF"
}
```

**Respuesta `201`:** `Extractor`.

---

#### `GET /api/galpones/{galponId}/extractors`
Lista extractores del galpón.

**Respuesta `200`:** array de `Extractor`.

---

#### `PUT /api/galpones/{galponId}/extractors/{extractorId}/programming`
Actualiza programación de un extractor (sin dispatch MQTT — usa `ActuatorProgrammingService`).

**Body:** `ExtractorProgrammingDto`:
```json
{
  "temperatureOn": 30.0,
  "temperatureOff": 27.0,
  "active": true
}
```

**Respuesta `200`:** array de `ExtractorProgramming`.

---

#### `GET /api/galpones/{galponId}/extractors/{extractorId}/history`
Historial de programación de un extractor.

**Respuesta `200`:** array de `ExtractorProgrammingHistory`.

---

### 4.8 Criadoras y Bombas (central)

**Controller**: `CriadoraBombaController` — `/api/galpones/{galponId}`

---

#### `POST /api/galpones/{galponId}/criadoras`
**Body:** objeto `Criadora`: `{ "name": "Criadora 2", "codeName": "CRI-02", "estado": "OFF" }`  
**Respuesta `201`:** `Criadora`.

---

#### `GET /api/galpones/{galponId}/criadoras`
**Respuesta `200`:** array de `Criadora`.

---

#### `PUT /api/galpones/{galponId}/criadoras/{criadoraId}/programming`
**Body:** `CriadoraProgrammingDto`: `{ "temperatureOn": 33.0, "temperatureOff": 30.0, "active": true }`  
**Respuesta `200`:** array de `CriadoraProgramming`.

---

#### `GET /api/galpones/{galponId}/criadoras/{criadoraId}/history`
**Respuesta `200`:** array de `CriadoraProgrammingHistory`.

---

#### `POST /api/galpones/{galponId}/bombas`
**Body:** `{ "name": "Bomba 2", "codeName": "BOM-02", "estado": "OFF" }`  
**Respuesta `201`:** `Bomba`.

---

#### `GET /api/galpones/{galponId}/bombas`
**Respuesta `200`:** array de `Bomba`.

---

#### `PUT /api/galpones/{galponId}/bombas/{bombaId}/programming`
**Body:** `BombaProgrammingDto`:
```json
{
  "temperatureOn": 26.0,
  "temperatureOff": 24.0,
  "workDurationSeconds": 300,
  "active": true
}
```
**Respuesta `200`:** array de `BombaProgramming`.

---

#### `GET /api/galpones/{galponId}/bombas/{bombaId}/history`
**Respuesta `200`:** array de `BombaProgrammingHistory`.

---

### 4.9 Programación sincronizada vía MQTT

**Controller**: `ActuatorProgrammingSyncController`

Este es el endpoint que **despacha programación al backend local** por MQTT.

---

#### `PUT /api/galpones/{galponId}/actuadores/{actuatorType}/{actuatorId}/programming`
Crea una configuración de programación en el central Y la publica por MQTT al backend local del galpón.

**Path params:**
- `galponId`: ID del galpón en central
- `actuatorType`: `EXTRACTOR`, `CRIADORA`, `BOMBA`
- `actuatorId`: ID del actuador en central

**Body:** `ActuatorProgrammingRequest`:
```json
{
  "temperatureOn": 30.0,
  "temperatureOff": 27.0,
  "workDurationSeconds": null,
  "dispatchNow": true
}
```
| Campo | Requerido | Descripción |
|-------|-----------|-------------|
| `temperatureOn` | ✅ | Temperatura de encendido |
| `temperatureOff` | ✅ | Temperatura de apagado |
| `workDurationSeconds` | — | Solo bombas (>0) |
| `dispatchNow` | — | true = publica MQTT inmediatamente |

**Respuesta `200`:** `ActuatorProgrammingResponse`:
```json
{
  "configId": 7,
  "galponId": 1,
  "gatewayId": 1,
  "actuatorType": "EXTRACTOR",
  "actuatorId": 1,
  "temperatureOn": 30.0,
  "temperatureOff": 27.0,
  "workDurationSeconds": null,
  "syncStatus": "SENT",
  "message": "Programación enviada al gateway",
  "createdAt": "2026-06-15T10:30:00Z",
  "sentAt": "2026-06-15T10:30:00Z",
  "appliedAt": null
}
```

**Flujo MQTT:** publica al topic `avicola/galpon/{galponId}/config/programming`  
El backend local recibe, aplica y responde en `avicola/galpon/{galponId}/config/programming/ack`.

---

### 4.10 Control de actuadores (central)

**Controller**: `ActuatorControlController`

---

#### `POST /api/galpones/{galponId}/control/evaluate/latest`
Evalúa la última lectura del galpón y genera comandos PENDING.

**Body:** ninguno.

**Respuesta `200`:** `EvaluationResultDto`:
```json
{
  "evaluatedAt": "2026-06-15T10:30:01Z",
  "galponId": 1,
  "gatewayId": 1,
  "temperatureC": 29.5,
  "humidityPercent": 65.0,
  "nh3Ppm": 0.0,
  "counts": {
    "extractorsTotal": 1,
    "extractorsOn": 0,
    "criadorasTotal": 1,
    "criadorasOn": 0,
    "bombasTotal": 1,
    "bombasOn": 0
  },
  "signals": [
    {
      "commandId": 42,
      "actuatorType": "EXTRACTOR",
      "actuatorId": 1,
      "actuatorName": "Extractor 1",
      "command": "ON",
      "workDurationSeconds": null,
      "reason": "Temperatura supera umbral",
      "createdAt": "2026-06-15T10:30:01Z"
    }
  ]
}
```

**Nota**: los comandos generados quedan en estado `PENDING`. No se despachan automáticamente.

---

#### `GET /api/galpones/{galponId}/control/commands/pending`
Lista comandos PENDING del galpón.

**Respuesta `200`:** array de `ActuatorControlCommand` (entidad directa).

---

#### `POST /api/control/commands/{commandId}/dispatch`
Despacha un comando PENDING: cambia estado a `SENT` y publica vía MQTT.

**Body:** ninguno.

**Respuesta `200`:** `ActuatorControlCommand` actualizado.

**Flujo MQTT**: publica al topic `avicola/galpon/{galponId}/actuadores/cmd`.  
El backend local recibe y responde en `avicola/galpon/{galponId}/actuadores/respuestas`.

---

#### `POST /api/galpones/{galponId}/control/manual`
Crea un comando manual. Si `dispatchNow=true` lo despacha por MQTT inmediatamente.

**Body:** `ManualActuatorCommandRequest`:
```json
{
  "actuatorType": "EXTRACTOR",
  "actuatorId": 1,
  "action": "ON",
  "reason": "Prueba manual desde central",
  "workDurationSeconds": null,
  "dispatchNow": true
}
```
| Campo | Requerido | Valores |
|-------|-----------|---------|
| `actuatorType` | ✅ | `EXTRACTOR`, `CRIADORA`, `BOMBA` |
| `actuatorId` | ✅ | ID del actuador en central |
| `action` | ✅ | `ON`, `OFF` |
| `reason` | — | texto libre |
| `workDurationSeconds` | — | solo bombas |
| `dispatchNow` | — | default false |

**Respuesta `200`:** `ActuatorControlCommand`.

---

### 4.11 Alarmas (central)

**Controller**: `AlarmController` — `/api/alarms`

---

#### `POST /api/alarms/rules`
Crea una regla de alarma en el central.

**Body:** `AlarmRuleDto`:
```json
{
  "galponId": 1,
  "name": "Temperatura alta central",
  "variable": "TEMPERATURA",
  "conditionType": "MAYOR_QUE",
  "threshold": 35.0,
  "unit": "°C",
  "minimumDurationSeconds": 60,
  "severity": "ALTA",
  "message": "Temperatura superó umbral en galpón central",
  "active": true
}
```
| Campo | Requerido | Diferencia vs local |
|-------|-----------|---------------------|
| `galponId` | — | No existe en local |
| `name` | ✅ | local usa `nombre` |
| `variable` | ✅ | `TEMPERATURA`, `HUMEDAD`, `NH3` ← `NH3` no `AMONIACO` |
| `conditionType` | ✅ | `MAYOR_QUE`, `MENOR_QUE`, `IGUAL_A`, `RANGO` ← diferente a local |
| `threshold` | ✅ | local usa `umbral` |
| `severity` | ✅ | `AlarmSeverity` enum |
| `message` | ✅ | local usa `mensaje` |

**Respuesta `200`:** `AlarmRule` (entidad directa).

---

#### `GET /api/alarms/rules`
Lista todas las reglas.

**Respuesta `200`:** array de `AlarmRule`.

---

#### `PUT /api/alarms/rules/{ruleId}`
Actualiza una regla. Body igual a POST.

**Respuesta `200`:** `AlarmRule`.

---

#### `PATCH /api/alarms/rules/{ruleId}/active`
Activa/desactiva una regla.

**⚠️ Diferencia**: usa `@RequestParam` NO `@RequestBody`.

**Query param:** `active=true`  
Ejemplo: `PATCH /api/alarms/rules/1/active?active=false`

**Respuesta `200`:** `AlarmRule`.

---

#### `GET /api/alarms`
Lista alarmas. Acepta filtros por query param.

**Query params:** `galpon_id` (Long, opcional), `estado` (string, opcional — si `ACTIVA` filtra activas).

**Respuesta `200`:** array de `Alarm` (entidad directa).

---

#### `GET /api/alarms/active`
Lista alarmas activas.

**Respuesta `200`:** array de `Alarm`.

---

#### `GET /api/alarms/history`
Historial completo de alarmas.

**Respuesta `200`:** array de `Alarm`.

---

#### `GET /api/alarms/{alarmId}/events`
Eventos de una alarma.

**Respuesta `200`:** array de `AlarmEvent`.

---

#### `POST /api/alarms/{alarmId}/acknowledge`
Reconoce una alarma.

**Body:** ninguno.  
**Respuesta `200`:** `Alarm`.

---

#### `POST /api/alarms/{alarmId}/close`
Cierra una alarma.

**Body:** ninguno.  
**Respuesta `200`:** `Alarm`.

---

### 4.12 Registros productivos (central)

**Controller**: `ProductiveRecordsController` — `/api/galpones/{galponId}`

---

#### `POST /api/galpones/{galponId}/mortality`
Registra mortalidad en el central. Puede llegar vía MQTT del local o directamente.

**Body:** `MortalityRecordDto`:
```json
{
  "maleCount": 3,
  "femaleCount": 2,
  "observations": "Causas desconocidas"
}
```

**Respuesta `201`:** `MortalityRecord` (entidad directa).

---

#### `GET /api/galpones/{galponId}/mortality`
Lista registros de mortalidad del galpón.

**Respuesta `200`:** array de `MortalityRecord`.

---

#### `POST /api/galpones/{galponId}/weight`
Registra un pesaje.

**Body:** `WeightRecordDto`:
```json
{
  "flockId": 1,
  "sampledBirdsCount": 50,
  "averageWeight": 1850.5,
  "age": 35,
  "recordDate": "2026-06-15",
  "gender": "MALE",
  "location": "CENTER"
}
```
| Campo | Requerido |
|-------|-----------|
| `sampledBirdsCount` | ✅ (≥1) |
| `averageWeight` | ✅ |
| resto | — |

**Respuesta `201`:** `WeightRecord`.

---

#### `GET /api/galpones/{galponId}/weight`
**Respuesta `200`:** array de `WeightRecord`.

---

#### `GET /api/galpones/{galponId}/weight/flock/{flockId}`
Peso por parvada.

---

#### `GET /api/galpones/{galponId}/weight/flock/{flockId}/gender/{gender}`
Peso por parvada y género.

---

#### `GET /api/galpones/{galponId}/weight/flock/{flockId}/latest/gender/{gender}`
Último pesaje por parvada y género.

---

#### `GET /api/galpones/{galponId}/weight/latest/male`
Último pesaje masculino de parvada activa del galpón.

---

#### `GET /api/galpones/{galponId}/weight/latest/female`
Último pesaje femenino.

---

#### `GET /api/galpones/{galponId}/weight/flock/{flockId}/range`
**Query params:** `from` (LocalDate), `to` (LocalDate).

---

#### `POST /api/galpones/{galponId}/consumption`
Registra consumo.

**Body:** `ConsumptionRecordDto`:
```json
{
  "flockId": 1,
  "consumptionDate": "2026-06-15",
  "waterLiters": 500.0,
  "foodKg": 187.5
}
```
**Nota**: el central incluye `waterLiters` — campo que no existe en el local.

**Respuesta `201`:** `ConsumptionRecord`.

---

#### `GET /api/galpones/{galponId}/consumption`
**Respuesta `200`:** array de `ConsumptionRecord`.

---

### 4.13 Provisioning

**Controller**: `ProvisioningController` — `/api/provisioning/galpones`

Flujo completo para registrar un nuevo galpón + gateway + sensores + actuadores + reglas de alarma.

---

#### `POST /api/provisioning/galpones`
Crea atómicamente todo el stack de un galpón.

**Body:** `ProvisioningGalponRequest`:
```json
{
  "code": "GAL-02",
  "name": "Galpón 2",
  "location": "Sector Sur",
  "gatewayCode": "raspi5-galpon-02",
  "gatewayName": "Gateway Galpón 2",
  "mqttBrokerUrl": "tcp://192.168.1.100:1883",
  "sensors": [
    { "code": "SEN-01", "name": "Sensor Temperatura", "type": "TEMPERATURA", "unit": "°C" },
    { "code": "SEN-02", "name": "Sensor Humedad", "type": "HUMEDAD", "unit": "%" },
    { "code": "SEN-03", "name": "Sensor NH3", "type": "NH3", "unit": "ppm" }
  ],
  "actuators": {
    "extractors": 12,
    "criadoras": 5,
    "bombas": 2
  },
  "createDefaultRules": true
}
```
| Campo | Requerido | Reglas |
|-------|-----------|--------|
| `code` | ✅ | único |
| `name` | ✅ | |
| `gatewayCode` | ✅ | único |
| `location` | — | |
| `gatewayName` | — | default = gatewayCode |
| `mqttBrokerUrl` | — | default `tcp://localhost:1883` |
| `sensors` | — | array, cada sensor requiere `type` |
| `actuators` | — | extractors/criadoras/bombas ≥0 |
| `createDefaultRules` | — | crea 4 reglas de alarma default |

**Reglas de alarma creadas si `createDefaultRules=true`:**
- Temperatura alta: `MAYOR_QUE 35.0°C` ALTA
- Temperatura baja: `MENOR_QUE 20.0°C` MEDIA
- Humedad alta: `MAYOR_QUE 75.0%` MEDIA
- NH3 alto: `MAYOR_QUE 15.0ppm` CRITICA

**Respuesta `201`:** `ProvisioningGalponResponse`:
```json
{
  "galponId": 2,
  "galponCode": "GAL-02",
  "galponName": "Galpón 2",
  "gatewayId": 2,
  "gatewayCode": "raspi5-galpon-02",
  "mqttBrokerUrl": "tcp://192.168.1.100:1883",
  "localEnvironment": {
    "GALPON_ID": "2",
    "GATEWAY_ID": "raspi5-galpon-02",
    "MQTT_BROKER_URL": "tcp://192.168.1.100:1883"
  },
  "localRunCommand": "SERVER_PORT=8081 GALPON_ID=2 GATEWAY_ID=raspi5-galpon-02 MQTT_BROKER_URL=tcp://192.168.1.100:1883 mvn spring-boot:run",
  "mqttTopics": {
    "readings": "avicola/galpon/2/lecturas",
    "commands": "avicola/galpon/2/actuadores/cmd",
    "responses": "avicola/galpon/2/actuadores/respuestas",
    "sync": "avicola/galpon/2/sync/#",
    "programming": "avicola/galpon/2/config/programming",
    "programmingAck": "avicola/galpon/2/config/programming/ack"
  },
  "createdResources": {
    "sensors": 3,
    "extractors": 12,
    "criadoras": 5,
    "bombas": 2,
    "defaultRules": 4
  }
}
```

---

#### `GET /api/provisioning/galpones`
Lista todos los galpones provisionados (resumen).

**Respuesta `200`:** array de `ProvisioningSummaryResponse`:
```json
[
  {
    "galponId": 1,
    "galponCode": "GAL-01",
    "galponName": "Galpón 1",
    "gatewayCode": "raspi5-galpon-01"
  }
]
```

---

#### `GET /api/provisioning/galpones/{galponId}`
Detalle de provisioning de un galpón.

**Respuesta `200`:** `ProvisioningGalponResponse`.

---

### 4.14 Sync Events

**Controller**: `SyncEventController` — `/api/sync`

---

#### `POST /api/sync/events`
Crea un evento de sincronización manualmente.

**Body:** objeto `SyncEvent` (entidad directa).  
**Respuesta `200`:** `SyncEvent`.

**⚠️ Bug**: el campo `payload` tiene `@Column(insertable=false, updatable=false)` → siempre null.

---

#### `GET /api/sync/galpones/{galponId}/sync/events/pending`
Lista eventos de sync pendientes de un galpón.

**Respuesta `200`:** array de `SyncEvent`.

---

#### `POST /api/sync/events/{eventId}/ack`
Confirma un evento de sync recibido.

**Path param:** `eventId` es UUID.  
**Respuesta `200`:** `SyncEvent` con `status: "ACKNOWLEDGED"`.

---

### 4.15 Usuarios

**Controller**: `UserController` — `/api/users`

---

#### `POST /api/users`
Crea un usuario.

**Body:** objeto `User` (entidad directa): `{ "username": "admin", "email": "admin@avimax.com" }`  
**Respuesta `200`:** `User`.

---

#### `GET /api/users`
Lista todos los usuarios.

**Respuesta `200`:** array de `User`.

---

#### `POST /api/users/{userId}/galpones/{galponId}/access`
Otorga acceso a un usuario sobre un galpón.

**Query param:** `permissionLevel` (string, ej. `ADMIN`, `VIEWER`)  
**Respuesta `200`:** `UserGalponAccess`.

---

#### `GET /api/users/{userId}/galpones`
Lista accesos de un usuario.

**Respuesta `200`:** array de `UserGalponAccess`.

---

### 4.16 Dashboard (central)

**Controller**: `DashboardController` — `/api/dashboard`

---

#### `GET /api/dashboard/general`
Dashboard general con estado de todos los galpones.

**Respuesta `200`:** `DashboardGeneralDto`:
```json
{
  "totalGalpones": 2,
  "galponesNormales": 1,
  "galponesAdvertencia": 1,
  "galponesCriticos": 0,
  "gatewaysOffline": 0,
  "alertasCriticas": 2,
  "ultimaActualizacion": "2026-06-15T10:30:01Z",
  "galpones": [
    {
      "galponId": 1,
      "codigo": "GAL-01",
      "nombre": "Galpón 1",
      "estado": "ACTIVO",
      "gatewayEstado": "ACTIVO",
      "ultimaLectura": "2026-06-15T10:30:00Z",
      "parvada": {
        "id": 2,
        "nombre": "Parvada Demo",
        "dia": 14,
        "avesIniciales": 12500,
        "avesActuales": 12490,
        "mortalidadHoy": 5
      },
      "lecturaActual": {
        "temperatura": 29.5,
        "humedad": 65.0,
        "nh3": 0.0
      },
      "alertasActivas": 1,
      "actuadoresActivos": {
        "extractoresOn": 8,
        "criadorasOn": 0,
        "bombasOn": 0
      }
    }
  ]
}
```

---

### 4.17 Estado MQTT (central)

**Controller**: `StatusController` — `/api/status`

---

#### `GET /api/status/health`
Estado de salud del backend central.

**Respuesta `200`:** `StatusHealthDto`:
```json
{
  "status": "OK",
  "service": "avimax-central-backend",
  "timestamp": "2026-06-15T10:30:01Z",
  "database": "UP"
}
```

---

#### `GET /api/status/mqtt`
Estado de la conexión MQTT del central.

**Respuesta `200`:** `MqttStatusDto`:
```json
{
  "connected": true,
  "brokerUrl": "tcp://localhost:1883",
  "clientId": "avimax-central-ingestion",
  "subscriptions": [
    "avicola/galpon/+/lecturas",
    "avicola/galpon/+/actuadores/respuestas",
    "avicola/galpon/+/config/programming/ack",
    "avicola/galpon/+/sync/#"
  ],
  "lastMessageReceivedAt": "2026-06-15T10:30:15Z",
  "totalMessagesReceived": 5678,
  "lastError": null,
  "lastErrorAt": null
}
```

---

## 5. Contratos MQTT entre backends

### 5.1 Lecturas de sensores

**Dirección**: Sensores → `backend-java` (ingesta) → `avimax-central-backend` (sync)

**Topic local**: `avicola/galpon/{galponId}/lecturas` (configurable via `MQTT_TOPIC`)  
**⚠️ CRÍTICO**: el default legacy es `avicola/galpon1/lecturas` (sin slash). El central escucha `avicola/galpon/+/lecturas`. Se debe configurar `MQTT_TOPIC=avicola/galpon/1/lecturas`.

**Payload aceptado (local):**
```json
{
  "gateway_id": "raspi5-galpon-01",
  "timestamp": "2026-06-15T10:30:00Z",
  "temperature": 29.5,
  "humidity": 65.0,
  "nh3": 0.0
}
```
Aliases aceptados: `temperature` o `temperatura_c`; `humidity` o `humedad_relativa`; `nh3` o `nh3_ppm` o `amoniaco`.

**También acepta formato array:**
```json
{
  "gateway_id": "raspi5-galpon-01",
  "readings": [
    { "sensor": "TEMP-01", "temperature": 29.5, "humidity": 65.0, "nh3": 0.0, "timestamp": "..." }
  ]
}
```

---

### 5.2 Comandos de actuadores: central → local

**Topic**: `avicola/galpon/{galponId}/actuadores/cmd`

**Payload publicado por central:**
```json
{
  "commandId": 42,
  "galponId": 1,
  "gatewayId": "raspi5-galpon-01",
  "actuatorType": "EXTRACTOR",
  "actuatorId": 3,
  "action": "ON",
  "workDurationSeconds": null,
  "reason": "Temperatura supera umbral"
}
```

**Respuesta del local** (topic: `avicola/galpon/{galponId}/actuadores/respuestas`):
```json
{
  "commandId": 42,
  "galponId": 1,
  "gatewayId": "raspi5-galpon-01",
  "actuatorType": "EXTRACTOR",
  "actuatorId": 3,
  "action": "ON",
  "status": "EXECUTED",
  "message": "Comando ON aplicado en EXTRACTOR 3",
  "executedAt": "2026-06-15T10:30:01Z"
}
```

`status` posibles: `EXECUTED`, `FAILED`, `ALREADY_PROCESSED` (idempotencia).

---

### 5.3 Programación de actuadores: central → local

**Topic**: `avicola/galpon/{galponId}/config/programming`

**Payload publicado por central:**
```json
{
  "configId": 7,
  "galponId": 1,
  "gatewayId": "raspi5-galpon-01",
  "actuatorType": "EXTRACTOR",
  "actuatorId": 3,
  "temperatureOn": 30.0,
  "temperatureOff": 27.0,
  "workDurationSeconds": null
}
```

**ACK del local** (topic: `avicola/galpon/{galponId}/config/programming/ack`):
```json
{
  "configId": 7,
  "galponId": 1,
  "gatewayId": "raspi5-galpon-01",
  "actuatorType": "EXTRACTOR",
  "actuatorId": 3,
  "status": "APPLIED",
  "message": "Programación aplicada en EXTRACTOR 3",
  "appliedAt": "2026-06-15T10:30:02Z"
}
```

---

### 5.4 Sync: local → central

**Topics:**

| Tipo | Topic | Descripción |
|------|-------|-------------|
| Mortalidad | `avicola/galpon/{id}/sync/mortality` | Registro de mortalidad |
| Peso | `avicola/galpon/{id}/sync/weight` | Registro de pesaje |
| Consumo | `avicola/galpon/{id}/sync/consumption` | Registro de consumo |
| Estado actuador | `avicola/galpon/{id}/sync/actuator-state` | Cambio de estado de actuador |

**Payload sync/actuator-state:**
```json
{
  "galponId": 1,
  "gatewayId": "raspi5-galpon-01",
  "actuatorType": "EXTRACTOR",
  "actuatorId": 3,
  "actuatorName": "Ventilador 3",
  "state": true,
  "command": "ON",
  "triggeredBy": "CENTRAL_COMMAND",
  "workDurationSeconds": null,
  "changedAt": "2026-06-15T10:30:01Z"
}
```

`triggeredBy` valores: `LOCAL_EVALUATION`, `CENTRAL_COMMAND`, `MANUAL_LOCAL`, `AUTO_OFF`

---

### 5.5 Topics WebSocket para UI (local)

**Puerto**: 9001 (WebSocket sobre Mosquitto)

| Topic | Descripción |
|-------|-------------|
| `avimax/actuator/{type}/{n}/state` | Estado de un actuador individual |
| `avimax/actuators/state` | Estado agregado de todos |
| `avimax/actuator/command` | Comandos desde UI |
| `avimax/ack/{commandId}` | ACK de comandos UI |

Tipos en topic: `fan` (extractor), `heater` (criadora), `pump` (bomba)

---

## 6. Mapa de relaciones: endpoint local ↔ central

| Operación | Local (backend-java) | Central (avimax-central-backend) | Canal |
|-----------|---------------------|-----------------------------------|-------|
| Ingresar lectura | MQTT ingesta automática | `GET /api/galpones/{id}/lecturas` | MQTT → central |
| Ver última lectura | `GET /api/readings/latest` | `GET /api/galpones/{id}/lecturas/latest` | — |
| Registrar mortalidad | `POST /api/mortalidad` | `POST /api/galpones/{id}/mortality` | MQTT sync outbox |
| Registrar peso | `POST /api/peso` | `POST /api/galpones/{id}/weight` | MQTT sync outbox |
| Registrar consumo | `POST /api/consumo` | `POST /api/galpones/{id}/consumption` | MQTT sync outbox |
| Ver parvada activa | `GET /api/flocks/active` | `GET /api/galpones/{id}/flocks/active` | independientes |
| Crear parvada | `POST /api/flocks` | `POST /api/galpones/{id}/flocks` | independientes |
| Configurar extractor | `PUT /api/extractors/{id}/programming` | `PUT /api/galpones/{id}/actuadores/EXTRACTOR/{id}/programming` | MQTT → local |
| Enviar comando ON/OFF | Recibe vía MQTT → aplica | `POST /api/galpones/{id}/control/manual` → dispatch | MQTT → local |
| Estado MQTT | `GET /api/status/mqtt` | `GET /api/status/mqtt` | independientes |
| Evaluación automática | `POST /api/control/evaluate/latest` | `POST /api/galpones/{id}/control/evaluate/latest` | independientes |
| Comandos pendientes | `GET /api/control/commands/pending` | `GET /api/galpones/{id}/control/commands/pending` | independientes |
| Provisioning nuevo galpón | `GALPON_ID` / `GATEWAY_ID` env vars | `POST /api/provisioning/galpones` | genera env vars |
| Alarmas | `GET /api/alarms/active` | `GET /api/alarms/active` | independientes |

---

## 7. Tabla de diferencias clave

| Aspecto | backend-java (local) | avimax-central-backend (central) |
|---------|---------------------|----------------------------------|
| **Puerto** | 8080 | 8080 (Docker: expuesto en 8080) |
| **Base de datos** | TimescaleDB :5432 `avimax` | PostgreSQL :5434 `avimax_central` |
| **Flyway** | Desactivado (`flyway.enabled=false`) | Habilitado (V1-V9 automáticas) |
| **JPA ddl-auto** | `update` | `none` |
| **Estructura por galpón** | Sin `galponId` en entidades (1 instancia = 1 galpón) | Todas las entidades tienen `galponId` |
| **SensorReading.flockId** | NOT NULL (sin parvada activa = lectura descartada) | Nullable |
| **Variable de alarma NH3** | `AMONIACO` | `NH3` |
| **Condición de alarma** | `MAYOR`, `MAYOR_IGUAL`, `MENOR`, `MENOR_IGUAL`, `IGUAL` | `MAYOR_QUE`, `MENOR_QUE`, `IGUAL_A`, `RANGO` |
| **Wrapper de respuesta** | `ApiResponse<T>` `{ data, error, status }` | Respuesta directa (sin wrapper) |
| **Lectura paginada** | `SensorReadingPageResponse` con `data[]` y `meta` | `PagedResponse<T>` con `content[]` y paginación Spring |
| **MQTT topic lecturas** | Configurable `MQTT_TOPIC` (default legacy roto) | Hardcoded `avicola/galpon/+/lecturas` |
| **Consumo** | `totalConsumptionKg` (solo alimento) | `waterLiters` + `foodKg` (agua y alimento) |
| **Auto-OFF bombas** | ScheduledExecutorService daemon | No implementado en central |
| **Idempotencia** | `processed_mqtt_commands`, `processed_programming_configs` | No |
| **Outbox MQTT** | `local_mqtt_outbox_messages` (retry hasta 20 intentos) | No |
| **Dispatch automático** | Al recibir comando MQTT → aplica inmediatamente | Comandos PENDING requieren dispatch explícito |
| **Actuadores data seed** | `DataInitializer`: 12 extractores, 5 criadoras, 2 bombas | `V5__seed_demo_data.sql`: 1 extractor, 1 criadora, 1 bomba |
| **CORS** | `allowed-origins: "*"` sin auth | `allowed-origins: "*"` sin auth |
| **Autenticación** | Sin Spring Security | Sin Spring Security |

---

*Fin del documento — AviMax API Contratos Completos v1.0*
