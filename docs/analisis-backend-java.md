# Análisis técnico profundo de backend-java — AviMax

---

## 1. Resumen ejecutivo

`backend-java` es el backend local/edge del sistema AviMax. Se ejecuta sobre Raspberry Pi o cualquier host Linux del galpón. Su función principal es:

- Recibir lecturas de temperatura, humedad y NH3 vía MQTT desde sensores del galpón.
- Evaluar reglas de programación para activar o apagar actuadores (extractores, criadoras, bombas).
- Publicar comandos de actuadores de vuelta al broker MQTT.
- Registrar mortalidad, peso y consumo de alimento de la parvada activa.
- Evaluar reglas de alarmas y publicar alertas al broker MQTT.
- Exponer datos en tiempo real al dashboard local vía WebSocket.
- Operar de forma completamente autónoma sin conexión a `avimax-central-backend`.

El sistema está diseñado para **un solo galpón** (single-galpon). No existe `galponId` en ninguna entidad; la parvada activa se resuelve siempre por `status = ACTIVE`.

La comunicación con el backend central es futura. Actualmente no hay ningún mecanismo de sincronización implementado.

---

## 2. Estructura del proyecto

```
backend-java/
├── pom.xml
├── src/
│   └── main/
│       ├── java/com/avimax/backend/
│       │   ├── AviMaxApplication.java            (clase principal)
│       │   ├── config/
│       │   │   ├── DataInitializer.java          (seed data al startup)
│       │   │   ├── MqttProperties.java           (record de configuración MQTT)
│       │   │   └── WebSocketConfig.java          (configuración WS)
│       │   ├── controller/                       (12 controllers REST)
│       │   ├── dto/                              (28 DTOs)
│       │   ├── entity/                           (20 entities + enums)
│       │   ├── repository/                       (15+ repositorios)
│       │   ├── service/                          (13 servicios)
│       │   └── websocket/
│       │       └── WebSocketSessions.java        (gestión de sesiones WS)
│       └── resources/
│           ├── application.yml
│           └── db/migration/                     (V1 a V11 Flyway SQL)
```

### Paquetes principales

| Paquete | Responsabilidad |
|---------|----------------|
| `config` | Configuración MQTT, seed data, WebSocket |
| `controller` | 12 controllers REST, sin lógica de negocio |
| `dto` | 28 clases: requests y responses; Java records en su mayoría |
| `entity` | 20 entidades JPA + enums |
| `repository` | Interfaces Spring Data JPA |
| `service` | Lógica de negocio, evaluación de actuadores, alarmas, MQTT |
| `websocket` | Broadcast de mensajes a clientes WebSocket conectados |

---

## 3. Configuración y dependencias

### `pom.xml` — Dependencias clave

| Dependencia | Versión | Propósito |
|-------------|---------|-----------|
| `spring-boot-starter-parent` | 3.4.0 | Framework principal |
| Java | 17 | Versión de compilación |
| `spring-boot-starter-web` | (boot) | REST API |
| `spring-boot-starter-data-jpa` | (boot) | ORM / JPA |
| `spring-boot-starter-websocket` | (boot) | WebSocket para dashboard |
| `spring-boot-starter-validation` | (boot) | Jakarta Validation |
| `spring-boot-starter-actuator` | (boot) | Endpoints de salud Spring |
| `postgresql` | (boot) | Driver JDBC |
| `flyway-core` | (boot) | Migraciones SQL (deshabilitado en runtime) |
| `org.eclipse.paho.client.mqttv3` | 1.2.5 | Cliente MQTT Eclipse Paho |
| `lombok` | (boot) | Generación de código |
| `jackson-databind` | (boot) | Serialización JSON |

### `application.yml` — Configuración principal

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/avimax
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
  jpa:
    hibernate:
      ddl-auto: update          # Flyway está deshabilitado; Hibernate gestiona el esquema
  flyway:
    enabled: false              # Las migraciones existen en /db/migration pero no se aplican en runtime

server:
  port: 8080

app:
  mqtt:
    enabled: ${MQTT_ENABLED:true}
    broker-url: ${MQTT_BROKER_URL:tcp://localhost:1883}
    client-id: ${MQTT_CLIENT_ID:avimax-backend-01}
    topic: ${MQTT_TOPIC:avicola/galpon1/lecturas}
    username: ${MQTT_USERNAME:}
    password: ${MQTT_PASSWORD:}
    qos: 1
    connection-timeout-seconds: 10
    keep-alive-seconds: 60
```

**Observaciones:**
- Base de datos `avimax` en `localhost:5432` (distinta de `avimax_central` en `5434` del central).
- `ddl-auto: update` significa que Hibernate crea/modifica tablas automáticamente. Las migraciones Flyway (V1–V11) existen como documentación pero **no se ejecutan en runtime**.
- MQTT habilitado por defecto. Puede desactivarse con `MQTT_ENABLED=false` para operar sin broker.
- No existe configuración de seguridad (Spring Security no está incluido).
- No existen perfiles (`spring.profiles.active`) configurados.

---

## 4. Arquitectura por capas

```
HTTP Request
     │
     ▼
[Controller]  ── valida path/query/body (@Valid) ──► ResponseEntity / DTO
     │
     ▼
[Service]     ── lógica de negocio, validaciones, transacciones
     │
     ├──► [Repository]  ── JPA / JPQL queries → PostgreSQL
     │
     ├──► [MqttService] ── publica comandos y alertas al broker
     │
     └──► [WebSocketSessions] ── broadcast en tiempo real
```

### Flujo de lectura MQTT (pipeline principal)

```
Sensor ──MQTT──► [MqttIngestionService]
                      │ procesa payload flexible (2 formatos)
                      ▼
               [SensorReadingService.saveIfActiveFlock()]
                      │
                      ├──► guarda SensorReading en BD
                      │
                      ├──► [AlarmEvaluationService.evaluate()]
                      │         └──► publica alarma → [AlarmMqttPublisherService]
                      │
                      └──► [ActuatorControlService.evaluateAndQueue()]
                                └──► crea ActuatorControlCommand
                                └──► publica a MQTT → [MqttActuatorPublisherService]
```

---

## 5. Endpoints REST

### 5.1 Dashboard

| Método | Ruta | Controller | Método Java | Descripción |
|--------|------|-----------|-------------|-------------|
| GET | `/api/dashboard/principal` | `DashboardController` | `principal()` | Dashboard completo: parvada, lecturas recientes, estado actuadores, resumen alarmas |

**Curl:**
```bash
curl http://localhost:8080/api/dashboard/principal
```

---

### 5.2 Lecturas de sensores

| Método | Ruta | Controller | Método Java | Query Params | Descripción |
|--------|------|-----------|-------------|--------------|-------------|
| GET | `/api/readings` | `ReadingController` | `getReadings()` | `start`, `end`, `variable`, `gateway`, `sensor`, `page`, `size`, `sort` | Lecturas paginadas con filtros |
| GET | `/api/readings/latest` | `ReadingController` | `latest()` | — | Última lectura registrada |
| GET | `/api/readings/recent` | `ReadingController` | `recent()` | — | Últimas 20 lecturas |

**Curls:**
```bash
curl "http://localhost:8080/api/readings/latest"
curl "http://localhost:8080/api/readings/recent"
curl "http://localhost:8080/api/readings?page=0&size=50&sort=recordedAt,desc"
curl "http://localhost:8080/api/readings?start=2026-06-01T00:00:00Z&end=2026-06-13T23:59:59Z&gateway=gw01"
```

---

### 5.3 Parvadas (Flocks)

| Método | Ruta | Controller | Método Java | Descripción |
|--------|------|-----------|-------------|-------------|
| POST | `/api/flocks` | `FlockController` | `create()` | Crea parvada activa (solo si no existe otra activa) |
| GET | `/api/flocks` | `FlockController` | `list()` | Lista todas las parvadas |
| GET | `/api/flocks/active` | `FlockController` | `active()` | Parvada activa actual (204 si no existe) |
| POST | `/api/flocks/{id}/close` | `FlockController` | `close()` | Cierra una parvada |

---

### 5.4 Mortalidad

| Método | Ruta | Controller | Método Java | Query Params | Descripción |
|--------|------|-----------|-------------|--------------|-------------|
| POST | `/api/mortalidad` | `MortalityController` | `create()` | — | Registra mortalidad del día en la parvada activa |
| GET | `/api/mortalidad` | `MortalityController` | `list()` | `from` (ISO date), `to` (ISO date) | Lista registros de mortalidad (con rango opcional) |

**Curls:**
```bash
curl -X POST http://localhost:8080/api/mortalidad \
  -H "Content-Type: application/json" \
  -d '{"maleCount": 3, "femaleCount": 2, "observations": "Encontrados en zona de bebederos"}'

curl "http://localhost:8080/api/mortalidad?from=2026-06-01&to=2026-06-13"
```

---

### 5.5 Peso

| Método | Ruta | Controller | Método Java | Path Vars | Query Params | Descripción |
|--------|------|-----------|-------------|-----------|--------------|-------------|
| POST | `/api/peso` | `WeightController` | `createWeightRecord()` | — | — | Crea registro de peso |
| GET | `/api/peso` | `WeightController` | `getAllWeightRecords()` | — | — | Todos los registros |
| GET | `/api/peso/latest/male` | `WeightController` | `getLatestMaleWeightRecord()` | — | — | Último peso macho (parvada activa) |
| GET | `/api/peso/latest/female` | `WeightController` | `getLatestFemaleWeightRecord()` | — | — | Último peso hembra (parvada activa) |
| GET | `/api/peso/flock/{flockId}` | `WeightController` | `getWeightRecordsByFlock()` | `flockId` | — | Pesos de una parvada |
| GET | `/api/peso/flock/{flockId}/gender/{gender}` | `WeightController` | `getWeightRecordsByFlockAndGender()` | `flockId`, `gender` | — | Filtrado por sexo |
| GET | `/api/peso/flock/{flockId}/latest/gender/{gender}` | `WeightController` | `getLatestWeightRecord()` | `flockId`, `gender` | — | Último peso por sexo |
| GET | `/api/peso/flock/{flockId}/range` | `WeightController` | `getWeightRecordsByDateRange()` | `flockId` | `from`, `to` | Por rango de fechas |

---

### 5.6 Consumo

| Método | Ruta | Controller | Método Java | Path Vars | Descripción |
|--------|------|-----------|-------------|-----------|-------------|
| POST | `/api/consumo` | `ConsumptionController` | `create()` | — | Registra consumo de alimento |
| GET | `/api/consumo` | `ConsumptionController` | `listAll()` | — | Lista todos los registros |
| GET | `/api/consumo/flock/{flockId}` | `ConsumptionController` | `listByFlock()` | `flockId` | Por parvada |

---

### 5.7 Alarmas

| Método | Ruta | Controller | Método Java | Descripción |
|--------|------|-----------|-------------|-------------|
| POST | `/api/alarms/rules` | `AlarmController` | `createRule()` | Crea regla de alarma |
| GET | `/api/alarms/rules` | `AlarmController` | `listRules()` | Lista todas las reglas |
| PUT | `/api/alarms/rules/{ruleId}` | `AlarmController` | `updateRule()` | Actualiza regla |
| PATCH | `/api/alarms/rules/{ruleId}/active` | `AlarmController` | `setRuleActive()` | Activa/desactiva regla |
| GET | `/api/alarms/active` | `AlarmController` | `activeAlarms()` | Alarmas activas actuales |
| GET | `/api/alarms/history` | `AlarmController` | `alarmHistory()` | Historial de alarmas |
| GET | `/api/alarms/{alarmId}/events` | `AlarmController` | `alarmEvents()` | Eventos de una alarma |
| POST | `/api/alarms/{alarmId}/acknowledge` | `AlarmController` | `acknowledgeAlarm()` | Reconoce alarma |
| POST | `/api/alarms/{alarmId}/close` | `AlarmController` | `closeAlarm()` | Cierra alarma |

---

### 5.8 Extractores

| Método | Ruta | Controller | Método Java | Path Vars | Query Params | Descripción |
|--------|------|-----------|-------------|-----------|--------------|-------------|
| POST | `/api/extractors` | `ExtractorController` | `create()` | — | — | Crea extractor |
| GET | `/api/extractors` | `ExtractorController` | `list()` | — | — | Lista con programación actual |
| PUT | `/api/extractors/{extractorId}/programming` | `ExtractorController` | `updateProgramming()` | `extractorId` | — | Configura umbral ON/OFF |
| GET | `/api/extractors/{extractorId}/history` | `ExtractorController` | `history()` | `extractorId` | `limit` | Historial de cambios de programación |
| GET | `/api/extractors/history` | `ExtractorController` | `historyAll()` | — | `limit` | Historial global |

---

### 5.9 Criadoras

| Método | Ruta | Controller | Método Java | Path Vars | Query Params | Descripción |
|--------|------|-----------|-------------|-----------|--------------|-------------|
| POST | `/api/criadoras` | `CriadoraController` | `create()` | — | — | Crea criadora |
| GET | `/api/criadoras` | `CriadoraController` | `list()` | — | — | Lista con programación |
| PUT | `/api/criadoras/{criadoraId}/programming` | `CriadoraController` | `updateProgramming()` | `criadoraId` | — | Configura umbral ON/OFF |
| GET | `/api/criadoras/{criadoraId}/history` | `CriadoraController` | `history()` | `criadoraId` | `limit` | Historial |
| GET | `/api/criadoras/history` | `CriadoraController` | `historyAll()` | — | `limit` | Historial global |

---

### 5.10 Bombas

| Método | Ruta | Controller | Método Java | Path Vars | Query Params | Descripción |
|--------|------|-----------|-------------|-----------|--------------|-------------|
| POST | `/api/bombas` | `BombaController` | `create()` | — | — | Crea bomba |
| GET | `/api/bombas` | `BombaController` | `list()` | — | — | Lista con programación |
| PUT | `/api/bombas/{bombaId}/programming` | `BombaController` | `updateProgramming()` | `bombaId` | — | Configura umbral + duración |
| GET | `/api/bombas/{bombaId}/history` | `BombaController` | `history()` | `bombaId` | `limit` | Historial |
| GET | `/api/bombas/history` | `BombaController` | `historyAll()` | — | `limit` | Historial global |

---

### 5.11 Control de actuadores

| Método | Ruta | Controller | Método Java | Path Vars | Descripción |
|--------|------|-----------|-------------|-----------|-------------|
| POST | `/api/control/evaluate/latest` | `ControlController` | `evaluateLatest()` | — | Evalúa programación con última lectura y genera comandos |
| GET | `/api/control/commands/pending` | `ControlController` | `pendingCommands()` | — | Comandos pendientes de despachar |
| POST | `/api/control/commands/{commandId}/dispatch` | `ControlController` | `dispatch()` | `commandId` | Marca comando como despachado |

**Curls:**
```bash
curl -X POST http://localhost:8080/api/control/evaluate/latest
curl http://localhost:8080/api/control/commands/pending
curl -X POST http://localhost:8080/api/control/commands/42/dispatch
```

---

### 5.12 Estado del sistema (MQTT)

| Método | Ruta | Controller | Método Java | Descripción |
|--------|------|-----------|-------------|-------------|
| GET | `/api/status/mqtt` | `StatusController` | `getMqttStatus()` | Estado del cliente MQTT (conexión, mensajes recibidos) |
| GET | `/api/status/health` | `StatusController` | `getHealth()` | Salud general del sistema |

---

## 6. DTOs y cuerpos JSON

### 6.1 Parvada

**`CreateFlockRequest`** (record)
```json
{
  "name": "Parvada Junio 2026",
  "totalBirds": 5000,
  "maleCount": 2500,
  "femaleCount": 2500,
  "flockDate": "2026-06-01",
  "birdLot": "LOTE-2026-001",
  "notes": "Lote Ross 308"
}
```
| Campo | Tipo | Requerido | Validación |
|-------|------|-----------|-----------|
| `name` | String | Sí | @NotBlank |
| `totalBirds` | Integer | Sí | @NotNull |
| `maleCount` | Integer | Sí | @NotNull |
| `femaleCount` | Integer | Sí | @NotNull |
| `flockDate` | LocalDate | Sí | @NotNull |
| `birdLot` | String | Sí | @NotBlank |
| `notes` | String | No | — |

**Validación de negocio:** `totalBirds == maleCount + femaleCount`

**`FlockResponse`** (record, construido por `FlockResponse.fromEntity()`)
```json
{
  "id": 1,
  "name": "Parvada Junio 2026",
  "totalBirds": 5000,
  "maleCount": 2500,
  "femaleCount": 2500,
  "flockDate": "2026-06-01",
  "birdLot": "LOTE-2026-001",
  "notes": "Lote Ross 308",
  "status": "ACTIVE",
  "startedAt": "2026-06-01T08:00:00Z",
  "endedAt": null
}
```

---

### 6.2 Mortalidad

**`CreateMortalityRequest`** (record)
```json
{
  "maleCount": 3,
  "femaleCount": 2,
  "observations": "Encontrados en zona de bebederos"
}
```
| Campo | Tipo | Requerido | Validación |
|-------|------|-----------|-----------|
| `maleCount` | Integer | Sí | @NotNull, @Min(0) |
| `femaleCount` | Integer | Sí | @NotNull, @Min(0) |
| `observations` | String | No | — |

**`MortalityResponse`** (record, construido por `MortalityResponse.of()`)
```json
{
  "id": 1,
  "recordDate": "2026-06-13",
  "ageDays": 12,
  "maleCount": 3,
  "femaleCount": 2,
  "totalCount": 5,
  "observations": "Encontrados en zona de bebederos",
  "createdAt": "2026-06-13T09:15:00Z"
}
```
- `ageDays`: calculado al construir `MortalityRecord` desde `ChronoUnit.DAYS.between(flock.flockDate, recordDate)`
- `totalCount`: calculado como `maleCount + femaleCount`
- `recordDate`: fecha del día del registro (LocalDate.now() en el servicio)

---

### 6.3 Peso

**`CreateWeightRequest`** (clase con @Getter, NO record)
```json
{
  "sampledBirdsCount": 30,
  "averageWeight": 1.85,
  "age": 32,
  "recordDate": "2026-06-13",
  "gender": "MALE",
  "location": "PANEL"
}
```
| Campo | Tipo | Requerido | Validación |
|-------|------|-----------|-----------|
| `sampledBirdsCount` | Integer | Sí | > 0 (validado en service) |
| `averageWeight` | Double | Sí | > 0 |
| `age` | Integer | Sí | >= 0 |
| `recordDate` | LocalDate | Sí | @NotNull |
| `gender` | String → Gender enum | Sí | MALE \| FEMALE |
| `location` | String → WeightLocation enum | Sí | PANEL \| ENMEDIO \| EXTRACTORES |

**`WeightResponse`** (record)
```json
{
  "id": 5,
  "flockId": 1,
  "sampledBirdsCount": 30,
  "averageWeight": 1.85,
  "age": 32,
  "recordDate": "2026-06-13",
  "gender": "MALE",
  "location": "PANEL",
  "createdAt": "2026-06-13T10:00:00Z"
}
```

---

### 6.4 Consumo

**`CreateConsumptionRequest`** (record con @Valid)
```json
{
  "age": 20,
  "recordDate": "2026-06-13",
  "totalConsumptionKg": 125.5,
  "birdsCountUsed": 4950
}
```
`consumptionPerBirdKg` se calcula automáticamente en el servicio: `totalConsumptionKg / birdsCountUsed`

**`ConsumptionResponse`** (record)
```json
{
  "id": 3,
  "flockId": 1,
  "age": 20,
  "recordDate": "2026-06-13",
  "totalConsumptionKg": 125.5,
  "birdsCountUsed": 4950,
  "consumptionPerBirdKg": 0.025353535,
  "createdAt": "2026-06-13T11:00:00Z"
}
```

---

### 6.5 Lecturas de sensores

**`SensorReadingResponse`** (record, `SensorReadingResponse.fromEntity()`)
```json
{
  "id": 1001,
  "flockId": 1,
  "recordedAt": "2026-06-13T14:30:00Z",
  "gatewayId": "gw-01",
  "sourceTopic": "avicola/galpon1/lecturas",
  "temperatureC": 28.5,
  "humidityPercent": 65.2,
  "nh3Ppm": 3.1
}
```

**`SensorReadingPageResponse`** (record)
```json
{
  "content": [ /* lista de SensorReadingResponse */ ],
  "page": 0,
  "size": 100,
  "totalElements": 8543
}
```

**`ApiResponse<T>`** (wrapper)
```json
{
  "data": { /* payload */ },
  "error": null,
  "status": "OK"
}
```

---

### 6.6 Programación de actuadores

**`ConfigureExtractorProgrammingRequest`** (record)
```json
{
  "temperatureOn": 28.0,
  "temperatureOff": 25.0
}
```
| Campo | Tipo | Requerido | Validación |
|-------|------|-----------|-----------|
| `temperatureOn` | Double | Sí | @NotNull |
| `temperatureOff` | Double | Sí | @NotNull |

**`ConfigureCriadoraProgrammingRequest`** (record, idéntico a extractor)
```json
{
  "temperatureOn": 33.0,
  "temperatureOff": 30.0
}
```

**`ConfigureBombaProgrammingRequest`** (record)
```json
{
  "temperatureOn": 26.0,
  "temperatureOff": 24.0,
  "workDurationSeconds": 300
}
```
| Campo | Tipo | Requerido | Validación |
|-------|------|-----------|-----------|
| `temperatureOn` | Double | Sí | @NotNull |
| `temperatureOff` | Double | Sí | @NotNull |
| `workDurationSeconds` | Integer | Sí | @NotNull, @Min(1) |

**`ExtractorItemResponse`** (record, construido por `ExtractorItemResponse.from()`)
```json
{
  "id": 1,
  "name": "Ventilador 1",
  "estado": "OFF",
  "createdAt": "2026-06-01T00:00:00Z",
  "programming": {
    "id": 1,
    "temperatureOn": 28.0,
    "temperatureOff": 25.0
  }
}
```

**`BombaItemResponse`** incluye también `workDurationSeconds` en el campo `programming`.

**`ExtractorHistoryResponse`** (record, `ExtractorHistoryResponse.of()`)
```json
{
  "id": 5,
  "extractorId": 1,
  "actuatorName": "Ventilador 1",
  "actuatorType": "EXTRACTOR",
  "temperatureOn": 28.0,
  "temperatureOff": 25.0,
  "recordedAt": "2026-06-10T08:00:00Z"
}
```

---

### 6.7 Control de actuadores

**`ActuatorSignalResponse`** (DTO de comando pendiente/despachado)
```json
{
  "id": 42,
  "actuatorType": "EXTRACTOR",
  "actuatorId": 3,
  "actuatorName": "Ventilador 3",
  "command": "ON",
  "workDurationSeconds": null,
  "reason": "temperature >= 28.0",
  "createdAt": "2026-06-13T14:31:00Z"
}
```

**`ControlEvaluationResponse`** (respuesta de `/api/control/evaluate/latest`)
```json
{
  "evaluatedAt": "2026-06-13T14:31:00Z",
  "temperatureC": 28.5,
  "humidityPercent": 65.2,
  "nh3Ppm": 3.1,
  "counts": {
    "totalExtractors": 12,
    "onExtractors": 5,
    "totalCriadoras": 5,
    "onCriadoras": 0,
    "totalBombas": 2,
    "onBombas": 1
  },
  "signals": [ /* lista de ActuatorSignalResponse */ ]
}
```

---

### 6.8 Alarmas

**`CreateAlarmRuleRequest`** (record con campos en español)
```json
{
  "nombre": "Temperatura alta crítica",
  "variable": "TEMPERATURA",
  "condicion": "MAYOR",
  "umbral": 35.0,
  "unidad": "°C",
  "tiempoMinimoSegundos": 300,
  "severidad": "CRITICA",
  "mensaje": "Temperatura superó 35°C",
  "activa": true
}
```

**`AlarmRuleResponse`** (con campos en español)
```json
{
  "idRegla": 1,
  "nombre": "Temperatura alta crítica",
  "variableMonitoreada": "TEMPERATURA",
  "condicion": "MAYOR",
  "umbral": 35.0,
  "unidad": "°C",
  "tiempoMinimoSegundos": 300,
  "severidad": "CRITICA",
  "mensaje": "Temperatura superó 35°C",
  "activa": true,
  "creadaEn": "2026-06-01T00:00:00Z"
}
```

**`AlarmResponse`** (instancia de alarma activa/histórica)
```json
{
  "id": 7,
  "ruleId": 1,
  "ruleName": "Temperatura alta crítica",
  "variable": "TEMPERATURA",
  "detectedValue": 36.2,
  "threshold": 35.0,
  "unit": "°C",
  "conditionType": "MAYOR",
  "severity": "CRITICA",
  "message": "Temperatura superó 35°C",
  "status": "ALARMA_ACTIVADA",
  "activatedAt": "2026-06-13T14:00:00Z",
  "acknowledgedAt": null,
  "resolvedAt": null,
  "closedAt": null
}
```

---

### 6.9 Estado MQTT

**`MqttStatusResponse`** (record)
```json
{
  "connected": true,
  "subscribedTopic": "avicola/galpon1/lecturas",
  "lastMessageReceivedAt": "2026-06-13T14:30:00Z",
  "totalMessagesReceived": 12450,
  "connectionStatus": "CONNECTED",
  "lastError": null,
  "lastErrorAt": null,
  "brokerUrl": "tcp://localhost:1883"
}
```

Estados posibles de `connectionStatus`: `CONNECTED`, `DISCONNECTED`, `CONNECTING_ERROR`

---

## 7. Entidades y modelo de datos

### 7.1 `Flock` (`flocks`)

| Campo | Tipo Java | Columna BD | Notas |
|-------|-----------|-----------|-------|
| `id` | Long | `id` BIGSERIAL PK | Auto-generado |
| `name` | String | `name` VARCHAR(120) | |
| `totalBirds` | Integer | `total_birds` | Se reduce con mortalidad |
| `maleCount` | Integer | `male_count` | Mutable (reduceCounts) |
| `femaleCount` | Integer | `female_count` | Mutable (reduceCounts) |
| `flockDate` | LocalDate | `flock_date` | Fecha de ingreso al galpón |
| `birdLot` | String | `bird_lot` VARCHAR(80) | |
| `notes` | String | `notes` VARCHAR(500) | |
| `status` | FlockStatus | `status` VARCHAR(20) | ACTIVE \| CLOSED |
| `startedAt` | OffsetDateTime | `started_at` | DEFAULT NOW() |
| `endedAt` | OffsetDateTime | `ended_at` | NULL hasta cierre |

**Índice único parcial:** `uq_flocks_single_active WHERE status = 'ACTIVE'` — garantiza a nivel BD que solo existe una parvada activa.

**Métodos de negocio:**
- `reduceCounts(int males, int females)`: resta de `maleCount`, `femaleCount` y `totalBirds` al registrar mortalidad. **Mutación directa de la entidad.**
- `close()`: cambia `status = CLOSED` y asigna `endedAt`.
- `isActive()`: compara `status == ACTIVE`.

---

### 7.2 `SensorReading` (`sensor_readings`)

| Campo | Tipo Java | Columna BD | Notas |
|-------|-----------|-----------|-------|
| `id` | Long | `id` BIGSERIAL PK | |
| `flock` | Flock | `flock_id` FK | @ManyToOne |
| `recordedAt` | OffsetDateTime | `recorded_at` TIMESTAMPTZ | Timestamp del sensor o NOW() |
| `gatewayId` | String | `gateway_id` VARCHAR(80) | ID del gateway MQTT |
| `sourceTopic` | String | `source_topic` VARCHAR(255) | Topic MQTT de origen |
| `temperatureC` | Double | `temperature_c` | Puede ser NULL si sensor no envió |
| `humidityPercent` | Double | `humidity_percent` | |
| `nh3Ppm` | Double | `nh3_ppm` | Default 0.0 si ausente |

**Hipertabla TimescaleDB:** `SELECT create_hypertable('sensor_readings', 'recorded_at')` — particionada automáticamente por tiempo.

**Diferencia con central:** central usa `Long galponId` (campo plano); local usa `@ManyToOne Flock`.

---

### 7.3 `MortalityRecord` (`mortality_records`)

| Campo | Tipo Java | Columna BD | Notas |
|-------|-----------|-----------|-------|
| `id` | Long | `id` | |
| `flock` | Flock | `flock_id` FK | @ManyToOne |
| `recordDate` | LocalDate | `record_date` | Fecha del registro |
| `ageDays` | Integer | `age_days` | **Calculado en constructor** |
| `maleCount` | Integer | `male_count` | |
| `femaleCount` | Integer | `female_count` | |
| `totalCount` | Integer | `total_count` | **Calculado en constructor** = male + female |
| `observations` | String | `observations` VARCHAR(500) | |
| `createdAt` | OffsetDateTime | `created_at` | |

**Constructor calcula:** `ageDays = ChronoUnit.DAYS.between(flock.getFlockDate(), recordDate)` y `totalCount = maleCount + femaleCount`.

**Restricción de negocio:** Solo un registro por parvada por día (validado en `MortalityService`).

---

### 7.4 `WeightRecord` (`weight_records`)

| Campo | Tipo Java | Columna BD | Notas |
|-------|-----------|-----------|-------|
| `id` | Long | `id` | |
| `flock` | Flock | `flock_id` FK | @ManyToOne |
| `sampledBirdsCount` | Integer | `sampled_birds_count` | Cantidad de aves muestreadas |
| `averageWeight` | Double | `average_weight` | Peso promedio en kg |
| `age` | Integer | `age` | Edad en días (ingresado manualmente) |
| `recordDate` | LocalDate | `record_date` | |
| `gender` | Gender (enum) | `gender` VARCHAR | MALE \| FEMALE |
| `location` | WeightLocation (enum) | `location` VARCHAR | PANEL \| ENMEDIO \| EXTRACTORES |
| `createdAt` | OffsetDateTime | `created_at` | |

---

### 7.5 `ConsumptionRecord` (`consumption_records`)

| Campo | Tipo Java | Columna BD | Notas |
|-------|-----------|-----------|-------|
| `id` | Long | `id` | |
| `flock` | Flock | `flock_id` FK | @ManyToOne |
| `age` | Integer | `age` | Edad en días |
| `recordDate` | LocalDate | `record_date` | |
| `totalConsumptionKg` | Double | `total_consumption_kg` | |
| `birdsCountUsed` | Integer | `birds_count_used` | Aves consideradas en el cálculo |
| `consumptionPerBirdKg` | Double | `consumption_per_bird_kg` | Calculado: total / birds |
| `createdAt` | OffsetDateTime | `created_at` | |

---

### 7.6 Actuadores: `Extractor`, `Criadora`, `Bomba`

Tres entidades con estructura prácticamente idéntica:

| Campo | Tipo Java | Columna BD | Notas |
|-------|-----------|-----------|-------|
| `id` | Long | `id` | |
| `name` | String | `name` | Nombre legible |
| `estado` | String | `estado` | "ON" \| "OFF" |
| `createdAt` | OffsetDateTime | `created_at` | |

Tablas: `extractors`, `criadoras`, `bombas`

---

### 7.7 Programación: `ExtractorProgramming`, `CriadoraProgramming`, `BombaProgramming`

| Campo | Tipo Java | Columna BD | Notas |
|-------|-----------|-----------|-------|
| `id` | Long | `id` | |
| `extractor/criadora/bomba` | Entidad | `*_id` FK UNIQUE | **@OneToOne** — solo 1 programación por actuador |
| `temperatureOn` | Double | `temperature_on` | Umbral para activar |
| `temperatureOff` | Double | `temperature_off` | Umbral para apagar |
| `workDurationSeconds` | Integer | `work_duration_seconds` | **Solo BombaProgramming** |

**`@OneToOne` con FK UNIQUE**: garantiza que cada actuador tiene exactamente una fila de programación.

Tablas: `extractor_programming`, `criadora_programming`, `bomba_programming`

---

### 7.8 Historial de programación

`ExtractorProgrammingHistory`, `CriadoraProgrammingHistory`, `BombaProgrammingHistory` — idéntica estructura:

| Campo | Tipo Java | Notas |
|-------|-----------|-------|
| `id` | Long | |
| `extractor/criadora/bomba` | entidad | @ManyToOne |
| `actuatorName` | String | snapshot del nombre |
| `actuatorType` | String | snapshot del tipo |
| `temperatureOn` | Double | valores al momento del cambio |
| `temperatureOff` | Double | |
| `workDurationSeconds` | Integer | solo bomba |
| `recordedAt` | OffsetDateTime | timestamp del cambio |

Tablas: `extractor_programming_history`, `criadora_programming_history`, `bomba_programming_history`

---

### 7.9 `ActuatorControlState` (`actuator_control_states`)

| Campo | Tipo Java | Columna BD | Notas |
|-------|-----------|-----------|-------|
| `id` | Long | `id` | |
| `actuatorType` | String | `actuator_type` VARCHAR(20) | "EXTRACTOR" \| "CRIADORA" \| "BOMBA" |
| `actuatorId` | Long | `actuator_id` | |
| `actuatorName` | String | `actuator_name` | snapshot |
| `currentState` | boolean | `current_state` BOOLEAN | true=ON, false=OFF |
| `lastUpdatedAt` | OffsetDateTime | `last_updated_at` | |

**Constraint UNIQUE:** `(actuator_type, actuator_id)` — una sola fila por actuador.

Esta tabla es la fuente de verdad para el estado actual de cada actuador. **No existe equivalente en `avimax-central-backend`.**

---

### 7.10 `ActuatorControlCommand` (`actuator_control_commands`)

| Campo | Tipo Java | Columna BD | Notas |
|-------|-----------|-----------|-------|
| `id` | Long | `id` | |
| `actuatorType` | String | `actuator_type` VARCHAR(20) | String, NO enum |
| `actuatorId` | Long | `actuator_id` | |
| `actuatorName` | String | `actuator_name` | |
| `command` | String | `command` VARCHAR(3) | "ON" \| "OFF" |
| `temperatureC` | Double | `temperature_c` | lectura que disparó el comando |
| `humidityPercent` | Double | `humidity_percent` | |
| `nh3Ppm` | Double | `nh3_ppm` | |
| `reason` | String | `reason` VARCHAR(500) | |
| `createdAt` | OffsetDateTime | `created_at` | |
| `dispatchedAt` | OffsetDateTime | `dispatched_at` | NULL = pendiente |
| `workDurationSeconds` | Integer | `work_duration_seconds` | para bombas |

**Pendiente = `dispatchedAt IS NULL`** — no hay enum de status. Contrasta con el central que usa `CommandStatus` enum (PENDING/SENT/EXECUTED).

---

### 7.11 Alarmas: `AlarmRule`, `Alarm`, `AlarmEvent`, `AlarmRuleState`

**`AlarmRule` (`alarm_rules`):**

| Campo | Tipo Java | Notas |
|-------|-----------|-------|
| `id` | Long | |
| `name` | String | |
| `variable` | AlarmVariable | TEMPERATURA \| HUMEDAD \| AMONIACO |
| `conditionType` | AlarmCondition | MAYOR \| MAYOR_IGUAL \| MENOR \| MENOR_IGUAL \| IGUAL |
| `threshold` | Double | Umbral de activación |
| `unit` | String | "°C", "%" , "ppm" |
| `minimumDurationSeconds` | Integer | Tiempo mínimo antes de disparar alarma |
| `severity` | AlarmSeverity | (enum) |
| `message` | String | |
| `active` | boolean | |
| `createdAt`, `updatedAt` | OffsetDateTime | |

**`AlarmRuleState` (`alarm_rule_states`):** Estado intermedio de evaluación.

| Campo | Tipo | Notas |
|-------|------|-------|
| `ruleId` | Long UNIQUE FK | Una fila por regla |
| `conditionMet` | Boolean | Si la condición está cumplida ahora |
| `metSince` | OffsetDateTime | Cuándo empezó a cumplirse |
| `lastValue` | Double | Último valor evaluado |
| `lastEvaluatedAt` | OffsetDateTime | |

---

### 7.12 Diagrama textual de relaciones

```
Flock 1 ── N  SensorReading
Flock 1 ── N  MortalityRecord
Flock 1 ── N  WeightRecord
Flock 1 ── N  ConsumptionRecord

Extractor 1 ── 1  ExtractorProgramming      (@OneToOne, FK UNIQUE)
Extractor 1 ── N  ExtractorProgrammingHistory
Criadora  1 ── 1  CriadoraProgramming
Criadora  1 ── N  CriadoraProgrammingHistory
Bomba     1 ── 1  BombaProgramming
Bomba     1 ── N  BombaProgrammingHistory

AlarmRule  1 ── N  Alarm
AlarmRule  1 ── 1  AlarmRuleState           (estado de evaluación continua)
Alarm      1 ── N  AlarmEvent

ActuatorControlState  (tabla de estado; no FK a entidades actuadoras)
ActuatorControlCommand (historial de comandos; no FK a actuadores)
```

---

## 8. Repositories

| Repository | Entidad | Métodos destacados |
|-----------|---------|-------------------|
| `FlockRepository` | Flock | `findFirstByStatus(FlockStatus)`, `existsByStatus(FlockStatus)`, `findAllByOrderByStartedAtDesc()` |
| `SensorReadingRepository` | SensorReading | `findTopByOrderByRecordedAtDesc()`, `findTop20ByOrderByRecordedAtDesc()`, `findAll(Specification, Pageable)` — extiende `JpaSpecificationExecutor<SensorReading>` |
| `MortalityRecordRepository` | MortalityRecord | `findByFlockIdOrderByRecordDateDesc(Long)`, `findByFlockIdAndRecordDate(Long, LocalDate)`, `findByFlockIdAndRecordDateBetweenOrderByRecordDateDesc()` |
| `WeightRecordRepository` | WeightRecord | `findAllByOrderByRecordDateDesc()`, `findByFlockIdOrderByRecordDateDesc()`, `findByFlockIdAndGenderOrderByRecordDateDesc()`, `findFirstByFlockIdAndGenderOrderByRecordDateDescCreatedAtDesc()`, `findByFlockIdAndDateRange()` (con @Query JPQL) |
| `ConsumptionRecordRepository` | ConsumptionRecord | `findAllByOrderByRecordDateDesc()`, `findByFlockIdOrderByRecordDateDesc()` |
| `ExtractorRepository` | Extractor | `findAllByOrderByCreatedAtDesc()` |
| `ExtractorProgrammingRepository` | ExtractorProgramming | `findByExtractorId(Long)` |
| `ExtractorProgrammingHistoryRepository` | ExtractorProgrammingHistory | `findByExtractorIdOrderByRecordedAtDesc(Long)`, `findAll(Sort)` |
| `CriadoraRepository` | Criadora | `findAllByOrderByCreatedAtDesc()` |
| `CriadoraProgrammingRepository` | CriadoraProgramming | `findByCriadoraId(Long)` |
| `CriadoraProgrammingHistoryRepository` | CriadoraProgrammingHistory | similar |
| `BombaRepository` | Bomba | `findAllByOrderByCreatedAtDesc()` |
| `BombaProgrammingRepository` | BombaProgramming | `findByBombaId(Long)` |
| `BombaProgrammingHistoryRepository` | BombaProgrammingHistory | similar |
| `ActuatorControlStateRepository` | ActuatorControlState | `findByActuatorTypeAndActuatorId(String, Long)` |
| `ActuatorControlCommandRepository` | ActuatorControlCommand | `findByDispatchedAtIsNullOrderByCreatedAtAsc()` — pendiente = NULL dispatched_at |
| `AlarmRuleRepository` | AlarmRule | `findByActiveTrue()` |
| `AlarmRepository` | Alarm | `findByStatusIn(List<AlarmStatus>)`, `findAllByOrderByActivatedAtDesc()` |
| `AlarmEventRepository` | AlarmEvent | `findByAlarmIdOrderByEventAtDesc(Long)` |
| `AlarmRuleStateRepository` | AlarmRuleState | `findByRuleId(Long)` |

**`SensorReadingSpecification`** — clase de especificación JPA con filtros combinables:
```java
SensorReadingSpecification.withFilters(start, end, variable, gateway, sensor, flockId)
```
- `variable` filtra por campo numérico relevante (temperatura/humedad/amoniaco) buscando no-null.
- `sensor` filtra por `sourceTopic LIKE '%sensor%'`.
- `flockId` filtra por `flock.id`.

---

## 9. Services

### 9.1 `SensorReadingService`

**Responsabilidad:** Punto de entrada de lecturas de sensores. Vincula la lectura con la parvada activa, la persiste, y dispara la cadena de evaluación.

**Método principal:**
```java
saveIfActiveFlock(gatewayId, sourceTopic, recordedAt, temperatureC, humidityPercent, nh3Ppm)
```
1. Busca parvada activa → si no existe, descarta la lectura con `log.warn`.
2. Crea y guarda `SensorReading` con `@ManyToOne` a la parvada activa.
3. Llama `alarmEvaluationService.evaluate(reading)`.
4. Llama `actuatorControlService.evaluateAndQueue(reading)`.

**Otros métodos:**
- `getLatestReading()` → `findTopByOrderByRecordedAtDesc()`
- `getRecentReadings()` → `findTop20ByOrderByRecordedAtDesc()`
- `getReadingsWithFilters(...)` → paginado con Specification + flockId del activo

---

### 9.2 `ActuatorControlService`

**Responsabilidad:** Evalúa lecturas contra programación de actuadores y genera comandos.

**Método `evaluateAndQueue(SensorReading reading)` → `ControlEvaluationResponse`:**
1. Carga programaciones de los 3 tipos: `ExtractorProgramming`, `CriadoraProgramming`, `BombaProgramming`.
2. Para cada programación, llama `evaluateOne(actuador, programming, reading)`.
3. `evaluateOne` compara la temperatura con umbrales:
   ```
   if temperature >= temperatureOn  → desiredState = true (ON)
   if temperature <= temperatureOff → desiredState = false (OFF)
   else                             → sin cambio
   ```
4. Si `desiredState != currentState` (consultado de `ActuatorControlState`):
   - Crea `ActuatorControlCommand` con `command = "ON"/"OFF"` y `dispatchedAt = null`.
   - Actualiza `ActuatorControlState.currentState`.
   - Publica el nuevo estado vía `MqttActuatorPublisherService.publishStateChange()`.
5. Retorna `ControlEvaluationResponse` con conteos y señales generadas.

**CRÍTICO — Sin inversión para Criadora:** La lógica de umbral es idéntica para EXTRACTOR y CRIADORA. Si temperatura >= `temperatureOn` → ON. Esto es correcto para una criadora solo si se entiende que una criadora se enciende cuando hace frío (umbral bajo) y se apaga cuando está caliente. Los valores semilla asignan `temperatureOn=33°C, temperatureOff=30°C` a las criadoras, lo que las activaría en temperatura alta, lo cual es contraproducente para un calentador. Ver sección 16.

**Método `getPendingCommands()`:** `findByDispatchedAtIsNullOrderByCreatedAtAsc()`

**Método `markDispatched(Long commandId)`:** Asigna `dispatchedAt = now()`.

---

### 9.3 `AlarmEvaluationService`

**Responsabilidad:** Evalúa las reglas de alarma activas contra cada lectura entrante.

**Flujo `evaluate(SensorReading reading)`:**
1. Carga reglas activas: `alarmRuleRepository.findByActiveTrue()`.
2. Para cada regla, extrae el valor relevante (temperatura/humedad/NH3).
3. Evalúa la condición (`AlarmCondition`: MAYOR, MENOR, IGUAL, MAYOR_IGUAL, MENOR_IGUAL).
4. Gestiona el estado en `AlarmRuleState`:
   - Si condición cumplida y `conditionMet = false`: registra `metSince = now()`, pone `conditionMet = true`.
   - Si condición cumplida y tiempo transcurrido >= `minimumDurationSeconds`: activa alarma (`AlarmService.activateAlarm()`).
   - Si condición ya no cumplida y había alarma activa: la resuelve automáticamente.
5. Guarda el nuevo estado en `AlarmRuleState`.

---

### 9.4 `AlarmMqttPublisherService`

**Responsabilidad:** Publica alarmas al broker MQTT.

- **Topic:** `avimax/galpon1/alertas`
- **clientId:** `avimax-backend-01-alarm-publisher`
- **Payload:** JSON con id, ruleName, variable, detectedValue, severity, status, activatedAt.
- Activada cuando `AlarmService` crea o cambia el estado de una alarma.

---

### 9.5 `AlarmService`

**Métodos públicos:**
- `createRule(CreateAlarmRuleRequest)` → guarda `AlarmRule` + crea `AlarmRuleState` asociado.
- `updateRule(ruleId, UpdateAlarmRuleRequest)` → actualiza regla existente.
- `setRuleActive(ruleId, ToggleAlarmRuleRequest)` → activa/desactiva regla.
- `listRules()`, `listActiveAlarms()`, `alarmHistory()`, `alarmEvents(alarmId)`.
- `acknowledgeAlarm(alarmId)` → cambia status a `ALARMA_RECONOCIDA`, registra `AlarmEvent`.
- `closeAlarm(alarmId)` → status a `ALARMA_CERRADA`.
- `activateAlarm(rule, detectedValue)` → crea `Alarm` con status `ALARMA_ACTIVADA`.
- `resolveAlarm(alarm)` → status `ALARMA_RESUELTA`.

---

### 9.6 `FlockService`

**Métodos:**
- `createActiveFlock(CreateFlockRequest)` → valida que no existe activa, valida total=macho+hembra, persiste.
- `getActiveFlock()` → `Optional<Flock>`.
- `getAllFlocks()` → todos ordenados por `startedAt DESC`.
- `closeFlock(Long id)` → llama `flock.close()`, persiste.

---

### 9.7 `MortalityService`

**Método `create(CreateMortalityRequest)`:**
1. Busca parvada activa → `IllegalStateException` si no existe.
2. Verifica que no existe registro para la misma parvada y fecha de hoy → `ResponseStatusException(CONFLICT)`.
3. Crea `MortalityRecord` (constructor calcula `ageDays` y `totalCount`).
4. Guarda el record.
5. **Llama `flock.reduceCounts(maleCount, femaleCount)`** → mutación permanente de los conteos de la parvada.
6. Guarda la parvada actualizada.

---

### 9.8 `WeightService`

Usa `@Autowired` (campo) en lugar de constructor injection — único service con esta diferencia.

**Métodos:**
- `create(CreateWeightRequest)` → busca parvada activa, valida campos manualmente en el service, crea `WeightRecord`.
- `getAllWeightRecords()`, `getWeightRecordsByFlock()`, `getWeightRecordsByFlockAndGender()`, `getLatestWeightRecord()`, `getWeightRecordsByDateRange()`.
- `getLatestMaleWeightRecord()` y `getLatestFemaleWeightRecord()` → resuelven la parvada activa internamente.

---

### 9.9 `ConsumptionService`

**Método `create(CreateConsumptionRequest)`:**
1. Busca parvada activa.
2. Calcula `consumptionPerBirdKg = totalConsumptionKg / birdsCountUsed`.
3. Persiste `ConsumptionRecord`.

**Métodos de consulta:** `listAll()`, `listByFlock(flockId)`.

---

### 9.10 `ExtractorService`, `CriadoraService`, `BombaService`

Patrón idéntico en los tres:
- `create(request)` → instancia y guarda entidad.
- `listWithProgramming()` → carga actuadores + join programación por `findBy*Id()`.
- `configureProgramming(id, request)` → upsert de programación (update si existe, insert si no); guarda snapshot en tabla de historial.
- `getHistory(actuatorId, limit)` → historia de cambios filtrada por actuador.
- `getAllHistory(limit)` → historia global ordenada por `recordedAt DESC`.

---

### 9.11 `DashboardService`

**Método `getPrincipalDashboard()` → `DashboardPrincipalResponse`:**
Agrega en una sola respuesta:
- Parvada activa (o null).
- Última lectura de sensor.
- Estado de todos los extractores, criadoras, bombas con su programación.
- Resumen de alarmas activas.

---

### 9.12 `MqttIngestionService`

Ver sección 12 (MQTT) para detalle completo.

---

### 9.13 `MqttActuatorPublisherService`, `MqttWebSocketBridgeService`

Ver sección 12 (MQTT).

---

## 10. Programación de actuadores

### 10.1 Modelo de datos

El local usa **`temperatureOn` / `temperatureOff`** como campos directos en la entidad de programación. Esto contrasta con el central, que usa `triggerCondition` (expresión string como `"TEMPERATURA>=28.0"`) y `action` ("ON"/"OFF").

| Aspecto | backend-java | avimax-central-backend |
|---------|-------------|----------------------|
| Campos | `temperatureOn`, `temperatureOff` | `triggerCondition`, `action` |
| Relación | @OneToOne (1 registro por actuador) | @ManyToOne (2 registros: ON + OFF) |
| Campo `active` | No existe | Sí (`active BOOLEAN`) |
| Campo `version` | No existe | No existe en central tampoco |
| Campo `origin` | No existe | No existe |
| Historial | Tabla separada `*_programming_history` | No existe |
| `workDurationSeconds` | Solo en `BombaProgramming` | En `BombaProgramming` del central |

### 10.2 Valores semilla (DataInitializer)

| Actuador | Cantidad | `temperatureOn` | `temperatureOff` | `workDurationSeconds` |
|---------|----------|-----------------|------------------|-----------------------|
| Extractores | 12 | 28.0°C | 25.0°C | N/A |
| Criadoras | 5 | 33.0°C | 30.0°C | N/A |
| Bombas | 2 | 26.0°C | 24.0°C | 300 s |

### 10.3 Endpoints de programación

```bash
# Configurar extractor
curl -X PUT http://localhost:8080/api/extractors/1/programming \
  -H "Content-Type: application/json" \
  -d '{"temperatureOn": 28.0, "temperatureOff": 25.0}'

# Configurar criadora
curl -X PUT http://localhost:8080/api/criadoras/1/programming \
  -H "Content-Type: application/json" \
  -d '{"temperatureOn": 33.0, "temperatureOff": 30.0}'

# Configurar bomba
curl -X PUT http://localhost:8080/api/bombas/1/programming \
  -H "Content-Type: application/json" \
  -d '{"temperatureOn": 26.0, "temperatureOff": 24.0, "workDurationSeconds": 300}'
```

### 10.4 Evaluación de reglas

El servicio `ActuatorControlService.evaluateOne()` implementa:

```
dado un actuador con temperatureOn=28 y temperatureOff=25:

temperatura actual = 29.0
→ 29.0 >= 28.0 → desiredState = true (ON)
→ si currentState = false → genera comando "ON"

temperatura actual = 24.0
→ 24.0 <= 25.0 → desiredState = false (OFF)
→ si currentState = true → genera comando "OFF"

temperatura actual = 26.5
→ ni >= 28 ni <= 25 → no cambia estado
```

**Zona muerta:** entre 25°C y 28°C no se genera ningún comando. Evita oscilaciones.

**No existen condiciones de humedad ni NH3 en las reglas de programación.** Solo temperatura.

---

## 11. Evaluación y control de actuadores

### 11.1 Trigger automático (vía MQTT)

Cada mensaje MQTT recibido en `avicola/galpon1/lecturas` dispara automáticamente:
```
MqttIngestionService.messageArrived()
  → SensorReadingService.saveIfActiveFlock()
    → ActuatorControlService.evaluateAndQueue(reading)
```
No hay scheduler ni tarea periódica. La evaluación ocurre en tiempo real con cada lectura.

### 11.2 Evaluación manual (REST)

```
POST /api/control/evaluate/latest
```
Recupera la última `SensorReading` de BD y ejecuta `evaluateAndQueue()`.

### 11.3 Pipeline completo de un comando

1. `evaluateAndQueue()` detecta `desiredState != currentState`.
2. Crea `ActuatorControlCommand` con `dispatchedAt = NULL`.
3. Actualiza `ActuatorControlState.currentState`.
4. Llama `MqttActuatorPublisherService.publishStateChange()`:
   - Publica a `avimax/actuator/fan/{n}/state` (o heater/pump).
   - Publica al topic agregado `avimax/actuators/state`.
   - Mensajes con `retained = true`, QoS configurado.
5. El gateway (Raspberry u otro) suscrito a ese topic ejecuta físicamente el actuador.
6. El frontend (vía WebSocket) recibe el estado actualizado en tiempo real.

### 11.4 Comandos pendientes

```
GET /api/control/commands/pending
```
Devuelve comandos donde `dispatched_at IS NULL`. Permite que el gateway consulte vía REST si MQTT no está disponible.

```
POST /api/control/commands/{commandId}/dispatch
```
Marca el comando como despachado (`dispatched_at = NOW()`).

### 11.5 Deduplicación

`ActuatorControlService` solo genera un nuevo comando cuando `desiredState != currentState` consultando `ActuatorControlState`. Si el actuador ya está en el estado deseado, no genera nada. No existe verificación de comandos pendientes en cola (a diferencia del central).

---

## 12. MQTT

### 12.1 Arquitectura MQTT local

El local mantiene **3 clientes MQTT independientes**, todos bajo la misma `MqttProperties` (`@ConditionalOnProperty(app.mqtt.enabled = true)`):

| Cliente | clientId | Tipo | Propósito |
|---------|---------|------|-----------|
| `MqttIngestionService` | `avimax-backend-01-ingest` | Subscriber | Recibe lecturas de sensores |
| `MqttActuatorPublisherService` | `avimax-backend-01-actuator` | Publisher | Publica estados de actuadores |
| `MqttWebSocketBridgeService` | `avimax-backend-01-wsbridge` | Sub + Pub | Bridge MQTT → WebSocket |

Adicional: `AlarmMqttPublisherService` (clientId: `avimax-backend-01-alarm-publisher`) — publisher de alarmas.

---

### 12.2 Topics suscritos

| Service | Topic | QoS | Descripción |
|---------|-------|-----|-------------|
| `MqttIngestionService` | `avicola/galpon1/lecturas` | 1 | Lecturas de sensores del galpón |
| `MqttWebSocketBridgeService` | `avimax/actuator/+/+/state` | config | Estado de actuadores individuales |
| `MqttWebSocketBridgeService` | `avimax/actuators/state` | config | Estado agregado |
| `MqttWebSocketBridgeService` | `avimax/galpon1/alertas` | config | Alertas de alarmas |

---

### 12.3 Topics publicados

| Service | Topic | Retained | QoS | Descripción |
|---------|-------|---------|-----|-------------|
| `MqttActuatorPublisherService` | `avimax/actuator/fan/{n}/state` | true | config | Estado de extractor N |
| `MqttActuatorPublisherService` | `avimax/actuator/heater/{n}/state` | true | config | Estado de criadora N |
| `MqttActuatorPublisherService` | `avimax/actuator/pump/{n}/state` | true | config | Estado de bomba N |
| `MqttActuatorPublisherService` | `avimax/actuators/state` | true | config | Snapshot agregado de todos |
| `AlarmMqttPublisherService` | `avimax/galpon1/alertas` | ? | config | Alarmas disparadas |
| `MqttWebSocketBridgeService` | (dinámico) | variable | config | Re-publica mensajes del WS al broker |

---

### 12.4 Formato de payload — Lecturas entrantes

El `MqttIngestionService` soporta **dos formatos de payload** (flexible):

**Formato A — campos directos:**
```json
{
  "gateway_id": "gw-01",
  "timestamp": "2026-06-13T14:30:00Z",
  "temperature": 28.5,
  "humidity": 65.2,
  "nh3": 3.1
}
```

**Formato B — array de lecturas:**
```json
{
  "gateway_id": "gw-01",
  "timestamp": "2026-06-13T14:30:00Z",
  "readings": [
    {
      "temperature": 28.5,
      "temperatura_c": 28.5,
      "humidity": 65.2,
      "humedad_relativa": 65.2,
      "nh3": 3.1,
      "nh3_ppm": 3.1,
      "amoniaco": 3.1
    }
  ]
}
```

El parser tiene aliases para cada campo (`temperature`/`temperatura_c`, `humidity`/`humedad_relativa`, `nh3`/`nh3_ppm`/`amoniaco`). NH3 default a 0.0 si ausente. `timestamp` default a `OffsetDateTime.now(UTC)` si ausente o inválido.

---

### 12.5 Formato de payload — Estados de actuadores (publicados)

**Topic individual** (`avimax/actuator/fan/3/state`):
```json
{
  "topic": "avimax/actuator/fan/3/state",
  "payload": {
    "type": "fan",
    "number": 3,
    "index": 2,
    "label": "E3",
    "name": "Ventilador 3",
    "actuatorId": 3,
    "state": true,
    "source": "backend-control",
    "timestamp": "2026-06-13T14:31:00Z"
  },
  "meta": {
    "timestamp": 1749824260
  }
}
```

**Topic agregado** (`avimax/actuators/state`):
```json
{
  "topic": "avimax/actuators/state",
  "payload": {
    "fans":    [false, false, true, false, false, false, false, false, false, false, false, false],
    "heaters": [false, false, false, false, false],
    "pumps":   [true, false],
    "timestamp": "2026-06-13T14:31:00Z",
    "source": "backend-control"
  },
  "meta": { "timestamp": 1749824260 }
}
```

Prefijo de labels: extractores = "E", criadoras = "C", bombas = "B". Tipos de topic: `fan`, `heater`, `pump`.

---

### 12.6 WebSocket Bridge

`MqttWebSocketBridgeService` actúa de puente bidireccional:
- **MQTT → WebSocket:** Reenvía como JSON a todos los clientes WebSocket conectados (`WebSocketSessions.broadcast()`).
- **WebSocket → MQTT:** Escucha eventos `MqttPublishRequest` via Spring `@EventListener`. El mensaje debe tener formato `{ topic, payload, retained }`.

Esto permite que el dashboard frontend (conectado por WebSocket) reciba actualizaciones en tiempo real de actuadores y alarmas sin polling REST.

---

### 12.7 Reconexión y configuración

- `setAutomaticReconnect(true)` en todos los clientes — reconexión automática.
- `cleanSession(true)` — no persiste sesión.
- `@PostConstruct` inicia la conexión al startup; `@PreDestroy` la cierra limpiamente.
- Si MQTT falla al iniciar: `IllegalStateException` es lanzada (el servicio no arranca). Puede prevenirse con `MQTT_ENABLED=false`.
- Username/password opcionales (solo se setean si no están en blanco).

---

## 13. Flujos funcionales locales

### Caso A: Registro manual de mortalidad

```
Usuario → POST /api/mortalidad
           {"maleCount": 3, "femaleCount": 2}
                │
                ▼
    MortalityController.create()
                │
                ▼
    MortalityService.create()
        1. findFirstByStatus(ACTIVE) → Flock
        2. findByFlockIdAndRecordDate(today) → CONFLICT si ya existe
        3. new MortalityRecord(flock, 3, 2, null)
             → ageDays = hoy - flockDate
             → totalCount = 5
        4. mortalityRecordRepository.save()
        5. flock.reduceCounts(3, 2) → totalBirds -= 5
        6. flockRepository.save(flock)
                │
                ▼
    HTTP 201 Created
    {"id":1, "recordDate":"2026-06-13", "ageDays":12,
     "maleCount":3, "femaleCount":2, "totalCount":5}
```

**Sin MQTT ni sincronización.** El dato queda solo en BD local.

---

### Caso B: Registro manual de peso

```
Usuario → POST /api/peso
           {"sampledBirdsCount":30, "averageWeight":1.85,
            "age":32, "recordDate":"2026-06-13",
            "gender":"MALE", "location":"PANEL"}
                │
                ▼
    WeightController → WeightService.create()
        1. findFirstByStatus(ACTIVE) → Flock
        2. valida sampledBirdsCount > 0, averageWeight > 0, age >= 0
        3. new WeightRecord(flock, 30, 1.85, 32, date, MALE, PANEL)
        4. weightRecordRepository.save()
                │
                ▼
    HTTP 201 Created + WeightResponse
```

---

### Caso C: Modificar programación de extractor

```
Usuario → PUT /api/extractors/3/programming
           {"temperatureOn": 30.0, "temperatureOff": 27.0}
                │
                ▼
    ExtractorService.configureProgramming(3, request)
        1. findById(3) → Extractor
        2. findByExtractorId(3) → Optional<ExtractorProgramming>
        3a. Si existe: existing.update(30.0, 27.0)
        3b. Si no: new ExtractorProgramming(extractor, 30.0, 27.0)
        4. extractorProgrammingRepository.save()
        5. new ExtractorProgrammingHistory(extractor, 30.0, 27.0)
           extractorProgrammingHistoryRepository.save()
                │
                ▼
    HTTP 200 + ExtractorItemResponse
    La próxima lectura de sensor usará los nuevos umbrales.
```

---

### Caso D: Modificar programación de bomba

```
Usuario → PUT /api/bombas/1/programming
           {"temperatureOn": 27.0, "temperatureOff": 24.0, "workDurationSeconds": 600}
                │
                ▼
    BombaService.configureProgramming(1, request)
        [mismo flujo que extractor + guarda workDurationSeconds]
```

---

### Caso E: Evaluación de actuadores con última lectura

```
Usuario → POST /api/control/evaluate/latest
                │
                ▼
    ControlController.evaluateLatest()
        sensorReadingService.getLatestReading()
                │
                ▼
    ActuatorControlService.evaluateAndQueue(reading)
        Para cada Extractor (12):
            cargar ExtractorProgramming
            if reading.temperatureC >= tempOn → desiredState = ON
            if reading.temperatureC <= tempOff → desiredState = OFF
            leer ActuatorControlState.currentState
            if desiredState != currentState:
                crear ActuatorControlCommand (command="ON"/"OFF")
                actualizar ActuatorControlState
                MqttActuatorPublisherService.publishStateChange()
        [mismo para Criadoras y Bombas]
                │
                ▼
    HTTP 200 + ControlEvaluationResponse
    {"evaluatedAt":"...", "counts":{...}, "signals":[...]}
```

---

### Caso F: Control manual de actuador

No existe un endpoint de control manual tipo "forzar ON/OFF un actuador específico". La única forma manual es:
1. Cambiar la programación para que los umbrales fuercen el estado deseado.
2. Llamar `POST /api/control/evaluate/latest`.

**No existe endpoint tipo `POST /api/extractors/{id}/toggle`.**

---

### Caso G: Operación sin conexión al backend central

El local opera completamente autónomo:
- Todas las lecturas, programaciones, mortalidad, peso y consumo se guardan localmente en PostgreSQL.
- La evaluación de actuadores ocurre con cada lectura MQTT.
- Las alarmas se evalúan y publican al broker local.
- El dashboard frontend se conecta directamente al local por HTTP y WebSocket.
- **No existe mecanismo de sincronización con el central.** Toda la data generada offline permanece solo en BD local hasta que se implemente sincronización.

---

## 14. Comparación con avimax-central-backend

| Funcionalidad | backend-java (local) | avimax-central-backend (central) | Brecha | Recomendación |
|--------------|---------------------|--------------------------------|--------|--------------|
| **Diseño** | Single-galpon, sin galponId | Multi-galpon, galponId en todas las entidades | El local no puede escalar sin refactor | Mantener separados; el local siempre es single |
| **Lecturas** | Filtrado por parvada activa; TimescaleDB hypertable | Filtrado por galponId; PostgreSQL simple | Diferente particionamiento | Evaluar TimescaleDB en central para escala |
| **Mortalidad** | Existe, `reduceCounts()` muta parvada | Existe en central pero estructura diferente | `reduceCounts()` no existe en central | Sincronizar mortalidad por MQTT |
| **Peso** | Existe, con Gender y WeightLocation | No verificado completamente en central | Probablemente brecha | Alinear estructura |
| **Consumo** | Existe: `totalConsumptionKg`, `birdsCountUsed`, `consumptionPerBirdKg` | Central usa `waterLiters`, `foodKg` | Campos completamente distintos | Definir modelo canónico único |
| **Alarmas** | Módulo completo: reglas + instancias + eventos + AlarmRuleState | No verificado en central | Probable brecha importante | Implementar en central si no existe |
| **Programación** | `temperatureOn`/`temperatureOff` + `@OneToOne` + historial | `triggerCondition`/`action` strings + `@ManyToOne` | Modelo de datos incompatible | Transformación necesaria en sincronización |
| **Control automático** | `ActuatorControlState` + evaluación reactiva | CommandStatus enum; evaluación diferente | Modelos distintos; estado actual en central por `estado` field | Alinear |
| **Comandos** | `command` = "ON"/"OFF" (VARCHAR 3); `dispatchedAt NULL` = pendiente | `action` = "ON"/"OFF"; `CommandStatus` enum | Semántica equivalente, estructura diferente | Mapear en sincronización |
| **MQTT lecturas** | Suscribe `avicola/galpon1/lecturas` | Suscribe también a lecturas | Compatible en topic | Asegurar mismo topic |
| **MQTT actuadores** | Publica `avimax/actuator/{tipo}/{n}/state` con retained | No implementado en central | Brecha | Central debe suscribirse a estos topics |
| **Historial programación** | Tablas `*_programming_history` | No existe | Brecha | Implementar en central o sincronizar |
| **WebSocket** | Bridge MQTT → WebSocket para dashboard | No existe | Brecha | Implementar si central tiene dashboard |
| **Sincronización** | No existe | No existe | Brecha crítica | Diseñar protocolo MQTT de sync |
| **Seguridad** | No existe (sin Spring Security) | No verificado | Posible brecha | Implementar en ambos |
| **TimescaleDB** | Sí, hypertable en sensor_readings | No | Diferencia operacional | Evaluar para central |
| **DataInitializer** | Sí, crea datos semilla al startup | No verificado | Útil para local | Mantener |

---

## 15. Propuesta de sincronización local-central

### Principios

- El local es **source of truth** de su galpón.
- La sincronización va **local → central** (push) via MQTT.
- El central puede enviar comandos de configuración **central → local** via MQTT.
- Todos los eventos deben incluir `gatewayId`, `galponId` (configurado en el local), y `syncedAt`.

### 15.1 Lecturas de sensores

```
Topic: avimax/sync/{galponId}/readings
QoS: 1
Retained: false
```
```json
{
  "event": "sensor_reading",
  "gatewayId": "gw-01",
  "galponId": 1,
  "recordedAt": "2026-06-13T14:30:00Z",
  "temperatureC": 28.5,
  "humidityPercent": 65.2,
  "nh3Ppm": 3.1,
  "syncedAt": "2026-06-13T14:30:01Z"
}
```

### 15.2 Programación de actuadores (cambios)

```
Topic: avimax/sync/{galponId}/programming/{actuatorType}/{actuatorId}
QoS: 1
Retained: true
```
```json
{
  "event": "programming_updated",
  "galponId": 1,
  "actuatorType": "EXTRACTOR",
  "actuatorId": 3,
  "actuatorName": "Ventilador 3",
  "temperatureOn": 30.0,
  "temperatureOff": 27.0,
  "workDurationSeconds": null,
  "updatedAt": "2026-06-13T08:00:00Z",
  "origin": "local",
  "syncStatus": "pending_ack"
}
```

### 15.3 Estado de actuadores

```
Topic: avimax/sync/{galponId}/actuator-state/{actuatorType}/{actuatorId}
QoS: 1
Retained: true
```
```json
{
  "event": "actuator_state_changed",
  "galponId": 1,
  "actuatorType": "EXTRACTOR",
  "actuatorId": 3,
  "state": true,
  "command": "ON",
  "triggeredBy": "auto_evaluation",
  "temperatureAtTrigger": 28.5,
  "changedAt": "2026-06-13T14:31:00Z"
}
```

### 15.4 Mortalidad

```
Topic: avimax/sync/{galponId}/mortality
QoS: 1
Retained: false
```
```json
{
  "event": "mortality_recorded",
  "galponId": 1,
  "flockId": 1,
  "recordDate": "2026-06-13",
  "ageDays": 12,
  "maleCount": 3,
  "femaleCount": 2,
  "totalCount": 5,
  "observations": "Zona de bebederos",
  "syncedAt": "2026-06-13T09:15:01Z"
}
```

### 15.5 Peso

```
Topic: avimax/sync/{galponId}/weight
QoS: 1
Retained: false
```
```json
{
  "event": "weight_recorded",
  "galponId": 1,
  "flockId": 1,
  "sampledBirdsCount": 30,
  "averageWeight": 1.85,
  "age": 32,
  "recordDate": "2026-06-13",
  "gender": "MALE",
  "location": "PANEL",
  "syncedAt": "2026-06-13T10:00:01Z"
}
```

### 15.6 Consumo

```
Topic: avimax/sync/{galponId}/consumption
QoS: 1
Retained: false
```
```json
{
  "event": "consumption_recorded",
  "galponId": 1,
  "age": 20,
  "recordDate": "2026-06-13",
  "totalConsumptionKg": 125.5,
  "birdsCountUsed": 4950,
  "consumptionPerBirdKg": 0.02535,
  "syncedAt": "2026-06-13T11:00:01Z"
}
```

### 15.7 Alarmas

```
Topic: avimax/sync/{galponId}/alarms
QoS: 1
Retained: false
```
```json
{
  "event": "alarm_activated",
  "galponId": 1,
  "alarmId": 7,
  "ruleName": "Temperatura alta crítica",
  "variable": "TEMPERATURA",
  "detectedValue": 36.2,
  "threshold": 35.0,
  "severity": "CRITICA",
  "status": "ALARMA_ACTIVADA",
  "activatedAt": "2026-06-13T14:00:00Z"
}
```

### 15.8 Estado del gateway

```
Topic: avimax/sync/{galponId}/gateway/status
QoS: 1
Retained: true
```
```json
{
  "event": "gateway_heartbeat",
  "galponId": 1,
  "gatewayId": "gw-01",
  "mqttConnected": true,
  "lastReadingAt": "2026-06-13T14:30:00Z",
  "totalReadings": 12450,
  "timestamp": "2026-06-13T14:31:00Z"
}
```

### 15.9 ACK del central (central → local)

```
Topic: avimax/ack/{galponId}/{eventType}/{entityId}
QoS: 1
```
```json
{
  "ack": true,
  "centralId": 101,
  "receivedAt": "2026-06-13T14:31:05Z"
}
```

---

## 16. Riesgos técnicos

| # | Riesgo | Severidad | Descripción |
|---|--------|-----------|-------------|
| 1 | **Lógica de criadora invertida** | CRÍTICA | Los umbrales semilla para criadoras son `temperatureOn=33, temperatureOff=30`. Con la lógica actual (`temp >= 33 → ON`), la criadora se activa cuando hace **calor**, no frío. Un calentador debe encenderse cuando hace frío. La lógica debería estar invertida o los umbrales deben ser bajos (ej. `temperatureOn=25, temperatureOff=28`). |
| 2 | **`ddl-auto: update` en producción** | ALTA | `spring.jpa.hibernate.ddl-auto: update` puede modificar el esquema automáticamente al deployar. En producción se recomienda `validate` con Flyway habilitado. |
| 3 | **Flyway deshabilitado** | ALTA | Existe V1–V11 pero `flyway.enabled: false`. El esquema se gestiona por Hibernate. Pérdida de control sobre migraciones, dificulta rollbacks y auditoría del esquema. |
| 4 | **`Flock.reduceCounts()` no tiene floor en cero** | ALTA | Si se registra mortalidad con más aves de las que quedan, los conteos quedan negativos. No hay validación que impida `maleCount < 0`. |
| 5 | **Un registro de mortalidad por día** | MEDIA | La restricción "un registro por parvada por día" puede ser muy limitante en operación real donde se registran bajas en diferentes momentos del día. |
| 6 | **Sin seguridad** | ALTA | No existe Spring Security ni autenticación. Cualquier host en la red local puede acceder a todos los endpoints, modificar programaciones, crear parvadas, etc. |
| 7 | **Sin sincronización con central** | ALTA | Toda la data generada offline no llega al central. Si el local falla o el hardware es reemplazado, los datos se pierden. |
| 8 | **Entidades retornadas directamente desde controllers** | MEDIA | `MortalityController` retorna atributos construidos manualmente de la entidad. `ControlController` retorna `ActuatorControlCommand` directamente. Riesgo de serialización de datos no deseados. |
| 9 | **Inconsistencia en injection style** | BAJA | `WeightService` usa `@Autowired` en campos; todos los demás usan constructor injection. Dificulta testing. |
| 10 | **`actuatorType` como String** | MEDIA | `ActuatorControlCommand.actuatorType` es `VARCHAR(20)` String, no un enum. Posibles valores inválidos. En el central es un enum `ActuatorType`. |
| 11 | **Sin validación de relación `temperatureOff < temperatureOn`** | MEDIA | No hay validación que asegure que `temperatureOff < temperatureOn`. Si se invierte accidentalmente, el actuador nunca cambia de estado o siempre cambia. |
| 12 | **`command VARCHAR(3)`** | BAJA | El campo `command` está limitado a 3 caracteres. "ON" y "OFF" caben, pero cualquier extensión futura (ej. "CYCLE", "PULSE") requeriría migración. |
| 13 | **Sin paginación en endpoints de consumo y mortalidad** | MEDIA | `GET /api/mortalidad` y `GET /api/consumo` devuelven listas completas sin paginación. Con parvadas largas puede ser problemático. |
| 14 | **`WeightController` sin `@Valid`** | MEDIA | `WeightController.createWeightRecord()` no tiene `@Valid` en el `@RequestBody`. Las validaciones se hacen en el service manualmente, sin mensajes de error estructurados. |
| 15 | **3 clientes MQTT independientes** | BAJA | Tres conexiones TCP al broker para un solo backend. Podría consolidarse en uno o dos, aunque no es un error funcional. |
| 16 | **Sin reintentos de publicación MQTT** | MEDIA | Si `publishStateChange()` falla, el comando queda en BD como creado pero el estado real del actuador puede divergir. |
| 17 | **`dispatchedAt = NULL` como indicador de pendiente** | MEDIA | Semántica implícita frágil. Si por algún bug `dispatchedAt` se asigna prematuramente, el comando queda oculto en la lista de pendientes. |
| 18 | **Sin soporte multi-galpón** | DISEÑO | El sistema entero asume single-galpon. Escalar a múltiples galpones requeriría refactor completo de entidades, repositorios y lógica de parvada activa. |

---

## 17. Recomendaciones para la siguiente fase

### Prioridad CRÍTICA

1. **Corregir la lógica de criadora:** Invertir la condición de evaluación para criadoras o cambiar los umbrales semilla a valores que tengan sentido para un calentador (`temperatureOn` debe ser la temperatura baja a partir de la cual SE ENCIENDE, por ejemplo `20°C`).

2. **Habilitar Flyway:** Cambiar `flyway.enabled: true` y `ddl-auto: validate`. Las migraciones V1–V11 ya están escritas; solo falta activarlas.

3. **Validar `temperatureOff < temperatureOn`** en `ConfigureExtractorProgrammingRequest`, `ConfigureCriadoraProgrammingRequest` y `ConfigureBombaProgrammingRequest`.

4. **Proteger endpoints críticos** con al menos Basic Auth o un token de API. El local está expuesto en red local, pero en producción agrícola hay riesgo de acceso no autorizado.

### Prioridad ALTA

5. **Implementar sincronización MQTT local → central** siguiendo la propuesta de la sección 15. Prioridad: lecturas, estados de actuadores, mortalidad.

6. **Agregar `galponId` como parámetro de configuración** en `application.yml` del local (sin modificar entidades). Incluirlo en los payloads MQTT de sincronización.

7. **Implementar `@EventListener` post-commit** para publicar al MQTT de sincronización después de guardar mortalidad, peso, consumo, y cambios de programación.

8. **Validar conteos de parvada en mortalidad:** Impedir que `reduceCounts()` lleve los conteos a negativo.

### Prioridad MEDIA

9. **Unificar injection style en `WeightService`:** Migrar de `@Autowired` a constructor injection.

10. **Agregar `@Valid` en `WeightController.createWeightRecord()`.**

11. **Paginación en mortalidad y consumo** para operaciones a largo plazo.

12. **Cambiar `actuatorType` a enum** en `ActuatorControlCommand` para consistencia con el central.

13. **Agregar endpoint de control manual** `POST /api/extractors/{id}/toggle` o similar para forzar estado sin depender de la evaluación automática.

14. **Definir modelo canónico de consumo** entre local y central (los campos son actualmente incompatibles: `totalConsumptionKg` vs `foodKg`/`waterLiters`).

### Prioridad BAJA

15. **Consolidar clientes MQTT** de 3 a 2 (ingestión + publicación/bridge).

16. **Documentar las APIs** con Springdoc/OpenAPI (`springdoc-openapi-starter-webmvc-ui`).

17. **Agregar reintentos en publicación MQTT** para evitar divergencia de estado actuador vs BD.

---

## 18. Checklist de pendientes

### Backend-java (local) — No modificar sin autorización

- [ ] Corregir lógica de criadora (¿inversión de condición o corrección de umbrales?)
- [ ] Habilitar Flyway (`flyway.enabled: true` + `ddl-auto: validate`)
- [ ] Agregar validación `temperatureOff < temperatureOn` en DTOs
- [ ] Agregar validación de conteos en `MortalityService` (no negativos)
- [ ] Agregar `@Valid` en `WeightController`
- [ ] Agregar `galponId` como configuración de entorno (para sync MQTT)
- [ ] Implementar endpoint de control manual de actuador
- [ ] Migrar `WeightService` a constructor injection
- [ ] Agregar paginación en mortalidad y consumo
- [ ] Implementar publicador MQTT de sincronización
- [ ] Definir modelo canónico de consumo (local vs central)

### avimax-central-backend — Alinear con local

- [ ] Suscribirse a `avimax/sync/{galponId}/readings` para recibir lecturas offline
- [ ] Suscribirse a `avimax/sync/{galponId}/mortality` para recibir mortalidad
- [ ] Suscribirse a `avimax/sync/{galponId}/weight` para recibir pesos
- [ ] Suscribirse a `avimax/sync/{galponId}/consumption` para recibir consumo
- [ ] Suscribirse a `avimax/sync/{galponId}/programming/+/+` para cambios de programación
- [ ] Suscribirse a `avimax/actuators/state` para estado real de actuadores
- [ ] Implementar módulo de alarmas si no existe (reglas + instancias + eventos)
- [ ] Alinear modelo de consumo (definir campos canónicos)
- [ ] Implementar `ActuatorControlState` o equivalente para tracking de estado actual
- [ ] Publicar ACKs a `avimax/ack/{galponId}/+/+`
- [ ] Implementar historial de programación en central

### Arquitectura / Infraestructura

- [ ] Definir y documentar `galponId` de cada instancia local en configuración
- [ ] Definir broker MQTT central (si el local usa broker local, se necesita bridge o broker compartido)
- [ ] Establecer política de retención de mensajes retenidos en el broker
- [ ] Plan de backup del PostgreSQL local (data generada offline en riesgo)
- [ ] Documentar protocolo de recuperación tras fallo del local (qué pasa con datos offline)
- [ ] Decisión: ¿TimescaleDB también en el central para sensor_readings?
- [ ] Agregar autenticación en ambos backends
- [ ] Configurar Last Will Testament (LWT) en el cliente MQTT del local para detectar desconexiones

---

*Documento generado el 2026-06-13 | Análisis de: `/home/leo/AviMaxBack/backend-java` | Sin modificaciones al código fuente*
