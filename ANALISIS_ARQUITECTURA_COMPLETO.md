# ANÁLISIS INTEGRAL DEL SISTEMA AVIMAX
## Arquitectura, Base de Datos, Backend, Raspberry y Dashboard

**Fecha:** 9 de junio de 2026  
**Autoría:** Análisis Exhaustivo de Codebase  
**Estado:** Documento profesional completo

---

## 1. RESUMEN GENERAL DEL SISTEMA

### ¿Qué hace el sistema AviMax?

AviMax es una **plataforma IoT para monitoreo y control de galpones avícolas**. Actualmente funciona en modo **local** (un galpón) pero está diseñada para escalar a **multi-galpón con servidor central**.

El sistema realiza:
- **Lectura de sensores**: Temperatura, humedad, amoniaco desde Modbus/Raspberry Pi
- **Publicación MQTT**: Envío de datos a broker central
- **Ingesta de eventos**: Backend recibe y persiste lecturas
- **Gestión de parvadas**: Ciclo de vida completo (apertura, cierre, históricos)
- **Control de actuadores**: Ventiladores (Extractores), calefactores (Criadoras), bombas de agua
- **Programación automática**: Programas que ejecutan actuadores según reglas
- **Gestión de alertas**: Reglas de alarma basadas en valores de sensores
- **Dashboard:** Visualización en tiempo real del estado del galpón
- **Históricos**: Base de datos con registros de:
  - Lecturas de sensores
  - Mortalidad de aves
  - Peso de aves
  - Consumo de agua/alimento
  - Programación de actuadores

### Tecnologías principales

| Componente | Tecnología | Detalles |
|-----------|-----------|---------|
| **Backend API** | Java 17 + Spring Boot 3.4 | REST, JPA, Transactional |
| **BD (Persistencia)** | PostgreSQL 15+ | Flyway migrations |
| **MQTT Broker** | No especificado (EMQX recomendado)  | QoS 1 |
| **Raspberry Pi** | Python 3 + Modbus | Lectura sensores XY-MD03 |
| **Dashboard** | No encontrado - React/Vue recomendado | WebSocket opcional |
| **Contenedorización** | Docker + Docker Compose | Multi-contenedor |
| **Gateway/Broker Local** | Paho MQTT Client | En Raspberry Pi |

### Estructura modular

```
┌─────────────────────────────────────┐
│        APIMAX (AviMax Backend)      │
│  Puerto 8080 - Spring Boot 3.4      │
├─────────────────────────────────────┤
│ Controladores (12)                  │
│ Servicios (16)                      │
│ Entidades (26)                      │
│ Repositorios (21)                   │
├─────────────────────────────────────┤
│    PostgreSQL (Flyway migrations)   │
│  (flocks, sensor_readings, alarms)  │
└─────────────────────────────────────┘
        ↑↓ MQTT
┌─────────────────────────────────────┐
│   Raspberry Pi + Broker Local       │
│  (Python: leer_sensores.py)         │
├─────────────────────────────────────┤
│ Sensores Modbus XY-MD03             │
│ Modbus Serial (/dev/ttyUSB0)        │
└─────────────────────────────────────┘
        ↑↓ REST/WebSocket
┌─────────────────────────────────────┐
│    Dashboard (NO IMPLEMENTADO)      │
│ Recomendado: React/Vue              │
│ URL: http://localhost:3000          │
└─────────────────────────────────────┘
```

---

## 2. ARQUITECTURA ACTUAL DETECTADA

### Flujo de datos (Actual - Servidor Local)

```
Sensor (XY-MD03)
    ↓
Raspberry Pi (Serial /dev/ttyUSB0)
    ↓
Modbus RTU (PyModbus)
    ↓
MQTT Publish (avicola/galpon1/lecturas)
    ↓
Backend Spring Boot
    │
    ├─→ Validate JSON payload
    │
    ├─→ Extract: gateway_id, temperature, humidity, nh3, timestamp
    │
    ├─→ SensorReadingService.saveIfActiveFlock()
    │   ├─ Find Flock with status=ACTIVE
    │   ├─ Create SensorReading entity
    │   └─ Save to PostgreSQL
    │
    ├─→ AlarmEvaluationService (triggers rules)
    │
    └─→ ActuatorControlService (queue commands)
    ↓
PostgreSQL (sensor_readings table)
    ↓
REST API /api/readings (Frontend consumes)
    ↓
Dashboard (Visualización)
```

### MQTT Topic Actual

**Topic:** `avicola/galpon1/lecturas`

**Publicador:** Raspberry Pi (leer_sensores.py)

**Suscriptor:** Backend Spring Boot (MqttIngestionService)

**Frequency:** 5 segundos (configurable en `POLL_INTERVAL`)

**Payload esperado:**
```json
{
  "gateway_id": "raspi5-galpon-01",
  "timestamp": "2026-06-05T12:00:30Z",
  "readings": [
    {
      "temperatura_c": 28.5,
      "humedad_relativa": 65.2,
      "nh3_ppm": 12.3
    }
  ]
}
```

### Flujo de control de actuadores

```
Lectura llega → AlarmEvaluationService
  ↓
Check reglas activas
  ↓
Si condición cumple → ActuatorControlService.evaluateAndQueue()
  ↓
Create ActuatorControlCommand (PENDING)
  ↓
Backend expone /api/control/commands/pending
  ↓
Raspberry Pi polls endpoint
  ↓
Ejecuta comando (ej: apagar ventilador)
  ↓
Actualiza estado en BD (comando → EXECUTED/FAILED)
```

---

## 3. ENDPOINTS DETECTADOS DEL BACKEND

### 3.1 Endpoints por Controlador

| Módulo | Método | Ruta Completa | Controlador | Servicio | Uso |
|--------|--------|---------------|-------------|----------|-----|
| **Status** | GET | `/api/status/mqtt` | StatusController | (directo) | Monitorear conexión MQTT |
| **Status** | GET | `/api/status/health` | StatusController | (directo) | Salud general sistema |
| **Flocks** | POST | `/api/flocks` | FlockController | FlockService | Crear parvada |
| **Flocks** | GET | `/api/flocks` | FlockController | FlockService | Listar todas |
| **Flocks** | GET | `/api/flocks/active` | FlockController | FlockService | Parvada activa actual |
| **Flocks** | POST | `/api/flocks/{id}/close` | FlockController | FlockService | Cerrar parvada |
| **Readings** | GET | `/api/readings` | ReadingController | SensorReadingService | Histórico filtrable |
| **Readings** | GET | `/api/readings/latest` | ReadingController | SensorReadingService | Última lectura |
| **Readings** | GET | `/api/readings/recent` | ReadingController | SensorReadingService | Últimas 20 |
| **Extractors** | POST | `/api/extractors` | ExtractorController | ExtractorService | Crear extractor |
| **Extractors** | GET | `/api/extractors` | ExtractorController | ExtractorService | Listar extractores |
| **Extractors** | PUT | `/api/extractors/{id}/programming` | ExtractorController | ExtractorService | Program ventilador |
| **Extractors** | GET | `/api/extractors/{id}/history` | ExtractorController | ExtractorService | Histórico 1 |
| **Extractors** | GET | `/api/extractors/history` | ExtractorController | ExtractorService | Histórico todos |
| **Criadoras** | POST | `/api/criadoras` | CriadoraController | CriadoraService | Crear calefactor |
| **Criadoras** | GET | `/api/criadoras` | CriadoraController | CriadoraService | Listar calefactores |
| **Criadoras** | PUT | `/api/criadoras/{id}/programming` | CriadoraController | CriadoraService | Program calefactor |
| **Criadoras** | GET | `/api/criadoras/{id}/history` | CriadoraController | CriadoraService | Histórico 1 |
| **Criadoras** | GET | `/api/criadoras/history` | CriadoraController | CriadoraService | Histórico todos |
| **Bombas** | POST | `/api/bombas` | BombaController | BombaService | Crear bomba |
| **Bombas** | GET | `/api/bombas` | BombaController | BombaService | Listar bombas |
| **Bombas** | PUT | `/api/bombas/{id}/programming` | BombaController | BombaService | Program bomba |
| **Bombas** | GET | `/api/bombas/{id}/history` | BombaController | BombaService | Histórico 1 |
| **Bombas** | GET | `/api/bombas/history` | BombaController | BombaService | Histórico todos |
| **Control** | POST | `/api/control/evaluate/latest` | ControlController | ActuatorControlService | Evaluar última lectura |
| **Control** | GET | `/api/control/commands/pending` | ControlController | ActuatorControlService | Pendientes ejecución |
| **Control** | POST | `/api/control/commands/{id}/dispatch` | ControlController | ActuatorControlService | Confirmar ejecución |
| **Weight** | POST | `/api/weight` | WeightController | WeightService | Registrar peso |
| **Weight** | GET | `/api/weight` | WeightController | WeightService | Listar pesos |
| **Weight** | GET | `/api/weight/flock/{flockId}` | WeightController | WeightService | Pesos x parvada |
| **Weight** | GET | `/api/weight/flock/{flockId}/gender/{gender}` | WeightController | WeightService | Pesos x parvada y sexo |
| **Weight** | GET | `/api/weight/flock/{flockId}/latest/gender/{gender}` | WeightController | WeightService | Último x sexo |
| **Weight** | GET | `/api/weight/latest/male` | WeightController | WeightService | Último peso machos |
| **Weight** | GET | `/api/weight/latest/female` | WeightController | WeightService | Último peso hembras |
| **Weight** | GET | `/api/weight/flock/{flockId}/range` | WeightController | WeightService | Rango de fechas |
| **Consumption** | POST | `/api/consumption` | ConsumptionController | ConsumptionService | Registrar consumo |
| **Consumption** | GET | `/api/consumption` | ConsumptionController | ConsumptionService | Listar consumos |
| **Consumption** | GET | `/api/consumption/flock/{flockId}` | ConsumptionController | ConsumptionService | Consumo x parvada |
| **Mortality** | POST | `/api/mortality` | MortalityController | MortalityService | Registrar mortalidad |
| **Mortality** | GET | `/api/mortality` | MortalityController | MortalityService | Listar mortalidad |
| **Mortality** | GET | `/api/mortality/flock/{flockId}` | MortalityController | MortalityService | Mortalidad x parvada |
| **Alarms** | POST | `/api/alarms/rules` | AlarmController | AlarmService | Crear regla alarma |
| **Alarms** | GET | `/api/alarms/rules` | AlarmController | AlarmService | Listar reglas |
| **Alarms** | PUT | `/api/alarms/rules/{ruleId}` | AlarmController | AlarmService | Editar regla |
| **Alarms** | PATCH | `/api/alarms/rules/{ruleId}/active` | AlarmController | AlarmService | Activar/desactivar |
| **Alarms** | GET | `/api/alarms/active` | AlarmController | AlarmService | Alarmas activas |
| **Alarms** | GET | `/api/alarms/history` | AlarmController | AlarmService | Histórico alarmas |
| **Alarms** | GET | `/api/alarms/{alarmId}/events` | AlarmController | AlarmService | Eventos alarma |
| **Alarms** | POST | `/api/alarms/{alarmId}/acknowledge` | AlarmController | AlarmService | Reconocer alarma |
| **Alarms** | POST | `/api/alarms/{alarmId}/close` | AlarmController | AlarmService | Cerrar alarma |
| **Dashboard** | GET | `/api/dashboard/principal` | DashboardController | DashboardService | Vista principal |

### 3.2 Parámetros detallados por endpoint crítico

#### POST `/api/flocks` - Crear parvada
```json
{
  "nombre": "Parvada Lote 001",
  "totalBirds": 10000,
  "maleCount": 3000,
  "femaleCount": 7000,
  "flockDate": "2026-06-05",
  "birdLot": "LT-2026-AVIMAX",
  "notes": "Observaciones opcionales"
}
```
**Respuesta:** `201 Created` + `FlockResponse`

#### GET `/api/readings?page=0&size=20&start=2026-06-01&end=2026-06-05&variable=TEMPERATURA&gateway=raspi5-galpon-01&sensor=ambiente_1`

**Parámetros Query:**
- `page`: Número de página (default 0)
- `size`: Registros por página (default 100, máx 1000)
- `start`: ISO-8601 timestamp
- `end`: ISO-8601 timestamp
- `variable`: TEMPERATURA, HUMEDAD, NH3
- `gateway`: ID del gateway
- `sensor`: Parte del topic MQTT

**Respuesta:** Paginado con metadatos

#### PUT `/api/alarms/rules/{ruleId}` - Editar regla
```json
{
  "nombre": "Temp Alta Crítica",
  "variable": "TEMPERATURA",
  "condicion": "MAYOR_QUE",
  "umbral": 35.0,
  "unidad": "°C",
  "tiempoMinimoSegundos": 300,
  "severidad": "ALTA",
  "mensaje": "Temperatura por encima de límite crítico"
}
```

#### PATCH `/api/alarms/rules/{ruleId}/active` - Activar/Desactivar
```json
{
  "activa": false
}
```

---

## 4. ENTIDADES JPA DETECTADAS

### 4.1 Tabla de entidades

| Entidad | Tabla BD | PK | Relaciones | Campos Clave | Propósito |
|---------|----------|----|-----------| ------------|-----------|
| **Flock** | `flocks` | `id` (BIGINT) | 1:many→SensorReading, Mortality, Weight, Consumption | name, totalBirds, maleCount, femaleCount, flockDate, birdLot, status (ENUM), startedAt, endedAt | Representa ciclo de vida de parvada |
| **SensorReading** | `sensor_readings` | `id` (BIGINT) | many:1→Flock | recordedAt, gatewayId, sourceTopic, temperatureC, humidityPercent, nh3Ppm, flockId | Lectura puntual de sensores |
| **MortalityRecord** | `mortality_records` | `id` (BIGINT) | many:1→Flock | recordDate, ageDays, maleCount, femaleCount, totalCount, observations, createdAt, flockId | Registro de mortalidad diaria |
| **WeightRecord** | `weight_records` | `id` (BIGINT) | many:1→Flock | sampledBirdsCount, averageWeight, age, recordDate, gender (ENUM), location (ENUM), createdAt, flockId | Peso muestreo |
| **ConsumptionRecord** | `consumption_records` | `id` (BIGINT) | many:1→Flock | consumptionDate, waterLiters, foodKg, createdAt, flockId | Consumo agua/alimento |
| **Extractor** | `extractors` | `id` (BIGINT) | 1:many→ExtractorProgramming, History | name, codeName | Ventilador |
| **ExtractorProgramming** | `extractor_programming` | `id` (BIGINT) | many:1→Extractor | temperatureThreshold, humidity, trigger | Programa/regla del ventilador |
| **ExtractorProgrammingHistory** | `extractor_programming_history` | `id` (BIGINT) | many:1→Extractor | changeType, onTemperature, status, changedAt | Histórico cambios |
| **Criadora** | `criadoras` | `id` (BIGINT) | 1:many→CriadoraProgramming, History | name, codeName | Calefactor |
| **CriadoraProgramming** | `criadora_programming` | `id` (BIGINT) | many:1→Criadora | temperatureThreshold, onHumidity, trigger | Programa calefactor |
| **CriadoraProgrammingHistory** | `criadora_programming_history` | `id` (BIGINT) | many:1→Criadora | changeType, onTemperature, status, changedAt | Histórico cambios |
| **Bomba** | `bombas` | `id` (BIGINT) | 1:many→BombaProgramming, History | name, codeName | Bomba agua |
| **BombaProgramming** | `bomba_programming` | `id` (BIGINT) | many:1→Bomba | frequencyHours, durationMinutes, trigger | Programa bomba |
| **BombaProgrammingHistory** | `bomba_programming_history` | `id` (BIGINT) | many:1→Bomba | changeType, onTemperature, status, changedAt | Histórico cambios |
| **ActuatorControlCommand** | `actuator_control_commands` | `id` (BIGINT) | many:1→ActuatorType | actuatorType, actuatorId, action, status, createdAt | Comando ejecución |
| **AlarmRule** | `alarm_rules` | `id` (BIGINT) | 1:many→Alarm | name, variable (ENUM), conditionType (ENUM), threshold, severity (ENUM), message, active, createdAt, updatedAt | Regla de alarma |
| **Alarm** | `alarms` | `id` (BIGINT) | many:1→AlarmRule, 1:many→AlarmEvent | ruleName, variable, detectedValue, threshold, severity, message, status (ENUM), activatedAt, acknowledgedAt, resolvedAt, closedAt, ruleId | Instancia de alarma activada |
| **AlarmEvent** | `alarm_events` | `id` (BIGINT) | many:1→Alarm | eventType (ENUM), previousStatus, newStatus, description, eventAt | Evento alarma |
| **AlarmRuleState** | `alarm_rule_states` | `id` (BIGINT) | many:1→AlarmRule | lastTriggeredAt, triggeredCount, duration, ruleId | Estado evaluación regla |

### 4.2 Enumeraciones

```java
FlockStatus: ACTIVE, CLOSED
AlarmVariable: TEMPERATURA, HUMEDAD, NH3
AlarmCondition: MAYOR_QUE, MENOR_QUE, IGUAL_A, RANGO
AlarmSeverity: BAJA, MEDIA, ALTA, CRÍTICA
AlarmStatus: ACTIVA, RECONOCIDA, RESUELTA, CERRADA
AlarmEventType: ALARMA_ACTIVADA, ALARMA_RECONOCIDA, ALARMA_RESUELTA, ALARMA_CERRADA
Gender: MALE, FEMALE, MIXED
WeightLocation: GALLERA, COMEDERO, NIDALES, OTRA
ActuatorType: EXTRACTOR, CRIADORA, BOMBA
CommandStatus: PENDING, EXECUTED, FAILED
```

### 4.3 Modelo Lógico de BD (Actual)

```
┌──────────────────────────────────────────────────────────────┐
│                         FLOCKS                               │
│  id (PK) | name | totalBirds | status | startedAt | endedAt │
└──────────────────────┬───────────────────────────────────────┘
                       │
          ┌────────────┼────────────┬──────────────┐
          │            │            │              │
          ▼            ▼            ▼              ▼
    ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
    │SENSOR_   │ │MORTALITY │ │WEIGHT_   │ │CONSUMP-  │
    │READINGS  │ │_RECORDS  │ │RECORDS   │ │TION_REC. │
    └──────────┘ └──────────┘ └──────────┘ └──────────┘
    
┌────────────────────────────────────────────────────────────┐
│                    ACTUATORS                               │
│  EXTRACTORS | CRIADORAS | BOMBAS                           │
│     ↓              ↓           ↓                            │
│  Programming | Programming | Programming                   │
│     ↓              ↓           ↓                            │
│  History     | History     | History                      │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│                    ALARMS                                  │
│      ALARM_RULES (reglas maestras)                         │
│             ↓                                              │
│         ALARMS (instancias)                                │
│             ↓                                              │
│      ALARM_EVENTS (eventos)                                │
│      ALARM_RULE_STATES (estado eval)                       │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│              CONTROL (Automatización)                      │
│      ACTUATOR_CONTROL_COMMANDS (cola de ejecución)        │
│      ACTUATOR_CONTROL_STATE (estado actual)                │
└────────────────────────────────────────────────────────────┘
```

---

## 5. REPOSITORIOS DETECTADOS

### 5.1 Tabla de Repositorios

| Repositorio | Entidad | ID Type | Métodos Personalizados | Para qué módulo |
|------------|---------|---------|----------------------|-----------------|
| `FlockRepository` | Flock | Long | existsByStatus(), findByStatus(), findFirstByStatus(), findAllByOrderByStartedAtDesc() | Gestión parvadas |
| `SensorReadingRepository` | SensorReading | Long | findTopByOrderByRecordedAtDesc(), findTop20ByOrderByRecordedAtDesc(), findAll(Spec, Pageable) | Históricos, filtros |
| `MortalityRecordRepository` | MortalityRecord | Long | findByFlockIdOrderByRecordDateDesc() | Mortalidad |
| `WeightRecordRepository` | WeightRecord | Long | findByFlockIdAndGender(), findLatestByFlockAndGender(), findByFlockIdAndRecordDateBetween() | Pesos |
| `ConsumptionRecordRepository` | ConsumptionRecord | Long | findByFlockIdOrderByConsumptionDateDesc() | Consumo |
| `ExtractorRepository` | Extractor | Long | findAll() | Extractores |
| `ExtractorProgrammingRepository` | ExtractorProgramming | Long | findByExtractorId() | Programas extractores |
| `ExtractorProgrammingHistoryRepository` | ExtractorProgrammingHistory | Long | findByExtractorIdOrderByChangedAtDesc() | Histórico extractores |
| `CriadoraRepository` | Criadora | Long | findAll() | Criadoras |
| `CriadoraProgrammingRepository` | CriadoraProgramming | Long | findByCriadoraId() | Programas criadoras |
| `CriadoraProgrammingHistoryRepository` | CriadoraProgrammingHistory | Long | findByCriadoraIdOrderByChangedAtDesc() | Histórico criadoras |
| `BombaRepository` | Bomba | Long | findAll() | Bombas |
| `BombaProgrammingRepository` | BombaProgramming | Long | findByBombaId() | Programas bombas |
| `BombaProgrammingHistoryRepository` | BombaProgrammingHistory | Long | findByBombaIdOrderByChangedAtDesc() | Histórico bombas |
| `AlarmRuleRepository` | AlarmRule | Long | findByActiveTrueOrderByCreatedAtDesc(), findAllByOrderByCreatedAtDesc() | Reglas alarmas |
| `AlarmRepository` | Alarm | Long | findByStatusInOrderByActivatedAtDesc(), findTopByRuleIdAndStatusIn(), findAllByOrderByActivatedAtDesc() | Alarmas activas/históricas |
| `AlarmEventRepository` | AlarmEvent | Long | findByAlarmIdOrderByEventAtDesc() | Eventos alarmas |
| `AlarmRuleStateRepository` | AlarmRuleState | Long | findByRuleId() | Estado evaluación |
| `ActuatorControlStateRepository` | ActuatorControlState | Long | findByActuatorTypeAndActuatorId() | Estado actual actuador |

---

## 6. SERVICIOS Y LÓGICA DE NEGOCIO

### 6.1 Tabla de Servicios

| Servicio | Responsabilidad | Entidades | Key Methods | Lógica importante |
|----------|-----------------|-----------|-------------|-------------------|
| **FlockService** | Ciclo de vida parvadas | Flock | createActiveFlock(), closeFlock(), getActiveFlock() | Solo 1 parvada ACTIVE al tiempo |
| **SensorReadingService** | Persistencia lecturas | SensorReading, Flock | saveIfActiveFlock(), getReadingsWithFilters() | Filtra por parvada activa |
| **MortalityService** | Registros mortalidad | MortalityRecord, Flock | register(), getByFlock() | Calcula ageDays auto |
| **WeightService** | Registros peso | WeightRecord, Flock | register(), getByFlock(), getByGender() | Filtra por sexo, rango fechas |
| **ConsumptionService** | Consumo agua/alimento | ConsumptionRecord, Flock | register(), getByFlock() | Acumula consumo |
| **AlarmEvaluationService** | Evaluación reglas | AlarmRule, Alarm, SensorReading | evaluate(SensorReading) | Compara valor vs threshold |
| **AlarmService** | Gestión alarmas | AlarmRule, Alarm, AlarmEvent | createRule(), updateRule(), createActivationIfNeeded(), resolveOpenAlarm() | CRUD completo |
| **ActuatorControlService** | Generación comandos | ActuatorControlCommand | evaluateAndQueue(SensorReading) | Cola FIFO pendientes |
| **ExtractorService** | Gestión ventiladores | Extractor, ExtractorProgramming | updateProgramming(), getHistory() | Modifica programa |
| **CriadoraService** | Gestión calefactores | Criadora, CriadoraProgramming | updateProgramming(), getHistory() | Modifica programa |
| **BombaService** | Gestión bombas | Bomba, BombaProgramming | updateProgramming(), getHistory() | Modifica programa |
| **MqttIngestionService** | Consumo MQTT | SensorReading, Flock | messageArrived(), processPayload() | Parsea JSON, filtro parvada |
| **MqttActuatorPublisherService** | Publicación comandos | ActuatorControlCommand | publishCommand() | Publica en topics |
| **AlarmMqttPublisherService** | Publicación alarmas | Alarm, AlarmEvent | publishEvent() | MQTT eventos alarmas |
| **DashboardService** | Datos dashboard | Múltiples | getDashboardPrincipal() | Agrega datos principales |
| **MqttWebSocketBridgeService** | WebSocket en vivo | Variadas | broadcast() | Tiempo real dashboard |

---

## 7. FLUJO COMPLETO DE GUARDADO DE REGISTROS

### 7.1 Lectura de Sensores

```
1. Raspberry Pi (leer_sensores.py)
   ├─ Lee ModBus XY-MD03 cada 5 segundos
   ├─ Extrae: temperatura, humedad, nh3
   └─ Crea JSON payload

2. Publica MQTT
   └─ Topic: avicola/galpon1/lecturas
   └─ QoS: 1

3. Backend (MqttIngestionService.messageArrived())
   ├─ Deserializa JSON
   ├─ Extrae: gateway_id, timestamp, temperature, humidity, nh3
   └─ Llama: SensorReadingService.saveIfActiveFlock()

4. SensorReadingService.saveIfActiveFlock()
   ├─ Encuentra Flock con status=ACTIVE
   ├─ Crea SensorReading entity
   ├─ Guardado en postgresql (INSERT)
   ├─ Llama: AlarmEvaluationService.evaluate(reading)
   └─ Llama: ActuatorControlService.evaluateAndQueue(reading)

5. PostgreSQL
   └─ INSERT INTO sensor_readings 
      (flock_id, recorded_at, gateway_id, source_topic, temperature_c, humidity_percent, nh3_ppm)
      VALUES (3, '2026-06-05T12:00:30Z', 'raspi5-galpon-01', 'avicola/galpon1/lecturas', 28.5, 65.2, 12.3)

6. Alarmas que se disparan
   ├─ Si temperatura > threshold → create Alarm
   ├─ Generate AlarmEvent
   └─ Publica MQTT avicola/galpon1/alarmas

7. Comandos de control
   ├─ Si humedad baja → encender extractor
   ├─ Create ActuatorControlCommand (PENDING)
   └─ REST API /api/control/commands/pending para Raspberry
```

### 7.2 Registro de Mortalidad

```
1. Frontend POST /api/mortality
   {
     "flockId": 3,
     "maleCount": 2,
     "femaleCount": 1,
     "observations": "Encontradas muertas en comedero"
   }

2. MortalityController.recordMortality()
   └─ MortalityService.register(request)

3. MortalityService.register()
   ├─ Encuentra Flock por ID
   ├─ Calcula ageDays = HOY - flockDate
   ├─ Crea MortalityRecord
   └─ Guardado BD

4. PostgreSQL
   └─ INSERT INTO mortality_records 
      (flock_id, record_date, age_days, male_count, female_count, total_count, observations)

5. Opcional: Actualiza totalBirds en Flock
   └─ Si lógica de negocio indica restar

6. Frontend consume GET /api/mortality/flock/{flockId}
   └─ Muestra histórico mortalidad
```

### 7.3 Registro de Peso

```
1. Frontend POST /api/weight
   {
     "flockId": 3,
     "sampledBirdsCount": 50,
     "averageWeight": 1850,
     "gender": "MALE",
     "location": "GALLERA"
   }

2. WeightService.register()
   ├─ Calcula age desde Flock
   ├─ recordDate = HOY
   ├─ Crea WeightRecord
   └─ Guardado BD

3. PostgreSQL
   └─ INSERT INTO weight_records
      (flock_id, sampled_birds_count, average_weight, age, record_date, gender, location, created_at)

4. Frontend consulta tendencias
   └─ GET /api/weight/flock/{flockId}/range?start=...&end=...
   └─ Gráfica de evolución peso
```

### 7.4 Alertas (Alarms)

```
1. Lectura de sensor llega al backend

2. AlarmEvaluationService.evaluate(reading)
   ├─ Obtiene AlarmRules activas
   ├─ Para cada regla:
   │  ├─ Compara reading.temperatureC con rule.threshold
   │  ├─ Si MAYOR_QUE: 28.5 > 35 → NO DISPARA
   │  ├─ Si ya existe Alarm ABIERTA para esta regla → NO CREA
   │  └─ Si NO existe → createActivationIfNeeded()
   │
   └─ AlarmService.createActivationIfNeeded()
      ├─ Crea Alarm con status=ACTIVA
      ├─ recordedAt=NOW
      ├─ Guardado BD
      ├─ Register AlarmEvent (ALARMA_ACTIVADA)
      └─ PublishEvent MQTT avicola/galpon1/alarmas

3. PostgreSQL
   ├─ INSERT INTO alarms (rule_id, status, activated_at, ...)
   └─ INSERT INTO alarm_events (alarm_id, event_type, ...)

4. Reconocimiento manual
   └─ POST /api/alarms/{alarmId}/acknowledge
      ├─ Cambio status → RECONOCIDA
      ├─ AlarmEvent tipo ALARMA_RECONOCIDA
      └─ DB update

5. Cierre/Resolución
   └─ Cuando condición resuelve
      ├─ status → RESUELTA o CERRADA
      └─ Event registrado
```

### 7.5 Consumo (Agua/Alimento)

```
1. Frontend POST /api/consumption
   {
     "flockId": 3,
     "consumptionDate": "2026-06-05",
     "waterLiters": 450.5,
     "foodKg": 120.3
   }

2. ConsumptionService.register()
   ├─ Validación: flockId existe
   ├─ Crea ConsumptionRecord
   └─ Guardado BD

3. Frontend consulta
   └─ GET /api/consumption/flock/{flockId}
   └─ Gráfica consumo diario
```

---

## 8. LÓGICA DE CREACIÓN DE PARVADA (FLOCK)

### 8.1 Ciclo de vida completo

```
┌─────────────────────────────────────────────────────────┐
│  CREACIÓN DE PARVADA (POST /api/flocks)                 │
├─────────────────────────────────────────────────────────┤
│  Request:                                               │
│  {                                                      │
│    "nombre": "Lote A - Mayo 2026",                     │
│    "totalBirds": 10000,                                │
│    "maleCount": 3000,                                  │
│    "femaleCount": 7000,                                │
│    "flockDate": "2026-05-01",                          │
│    "birdLot": "LT-2026-01",                            │
│    "notes": "Genética: Ross 308"                       │
│  }                                                      │
│                                                         │
│  Validaciones:                                          │
│  ✓ totalBirds = maleCount + femaleCount                │
│  ✓ No existe otra parvada ACTIVE                       │
│  ✓ Todos los campos obligatorios presentes             │
│                                                         │
│  Resultado:                                             │
│  - INSERT INTO flocks con status=ACTIVE                │
│  - startedAt = NOW                                      │
│  - endedAt = NULL                                       │
│  - Returns: FlockResponse (id, nombre, ...)            │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│  PARVADA ACTIVA (Recibe lecturas, mortalidad, peso)    │
├─────────────────────────────────────────────────────────┤
│  Mientras status=ACTIVE:                                │
│  - SensorReadings se asocian a flock_id                 │
│  - MortalityRecords se crean con flock_id               │
│  - WeightRecords se crean con flock_id                  │
│  - Alarmas se evalúan contra lecturas                   │
│  - Actuadores actúan según programación                │
│  - Consumo se registra con flock_id                     │
│                                                         │
│  Edad de la parvada:                                    │
│  ageDays = DAYS_BETWEEN(flockDate, TODAY)             │
│  (Se recalcula en cada registro)                        │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│  CIERRE DE PARVADA (POST /api/flocks/{id}/close)       │
├─────────────────────────────────────────────────────────┤
│  Acción:                                                │
│  - UPDATE flocks SET status='CLOSED', endedAt=NOW       │
│                                                         │
│  Resultado:                                             │
│  - Parvada anterior historizada                        │
│  - Nueva parvada ya ACTIVE (si existe)                 │
│  - Nuevas lecturas van a nueva parvada                 │
│                                                         │
│  Históricos:                                            │
│  - Todas las sensor_readings de parvada anterior       │
│    se mantienen asociadas (flock_id no cambia)         │
│  - Mismo para mortality, weight, consumption           │
└─────────────────────────────────────────────────────────┘
```

### 8.2 Relaciones de Parvada

```
Flock (1)
  ├── (1:many) SensorReadings → Lecturas en tiempo real
  ├── (1:many) MortalityRecords → Histórico mortalidad
  ├── (1:many) WeightRecords → Histórico pesos
  ├── (1:many) ConsumptionRecords → Histórico consumo
  └── (Implícito) AlarmRules → Evalúa lecturas de esta parvada
```

### 8.3 Datos que se muestran en Dashboard

| Dato | Cálculo | Fuente | Actualización |
|------|---------|--------|---------------|
| **Día de lote** | DAYS_BETWEEN(flockDate, TODAY) | Flock.flockDate | Real-time |
| **Aves totales** | Flock.totalBirds - SUM(total_count) | Flock + MortalityRecords | Con cada mortalidad |
| **Temperatura actual** | Última lectura | SensorReading.temperatureC | Cada 5 sec (MQTT) |
| **Humedad actual** | Última lectura | SensorReading.humidityPercent | Cada 5 sec (MQTT) |
| **NH3 actual** | Última lectura | SensorReading.nh3Ppm | Cada 5 sec (MQTT) |
| **Ultimo peso promedio** | WeightRecord.averageWeight | Última WeightRecord | Manual |
| **Alarms activas** | COUNT(Alarm WHERE status IN (ACTIVA, RECONOCIDA)) | Alarm table | Con cada regla disparada |
| **Mortalidad del día** | SUM(MortalityRecord WHERE recordDate=TODAY) | MortalityRecord | Manual |

---

## 9. FLUJO LÓGICO DEL PROGRAMA DE RASPBERRY

### 9.1 Script: `leer_sensores.py`

```python
# CONFIGURACIÓN
MQTT_BROKER = "localhost"       # Local (recomendado: cambiar a central.example.com)
MQTT_PORT = 1883
MQTT_TOPIC = "avicola/galpon1/lecturas"
MQTT_CLIENT_ID = "raspi5-lector-01"
GATEWAY_ID = "raspi5-galpon-01"

SERIAL_PORT = "/dev/ttyUSB0"    # Modbus serial
BAUDRATE = 9600
POLL_INTERVAL = 5               # Cada 5 segundos

SENSORS = [
  {
    "name": "ambiente_1",
    "type": "xy_md03",
    "device_id": 1,             # ID Modbus del sensor
    "address": 1,               # Registro base
    "function": "input"
  }
]

# FLUJO PRINCIPAL
while running:
  1_. Lee sensor Modbus
      client = ModbusSerialClient(..., /dev/ttyUSB0)
      result = client.read_input_registers(address=1, count=3, device_id=1)
      → Retorna [temp_raw, humedad_raw, nh3_raw]

  2_. Parsea valores
      temperatura_c = parse_xy_md03_temp(temp_raw)
      humedad_relativa = parse_xy_md03_humidity(humedad_raw)
      nh3_ppm = parse_nh3(nh3_raw)

  3_. Crea payload JSON
      {
        "gateway_id": "raspi5-galpon-01",
        "timestamp": "2026-06-05T12:00:30Z",
        "readings": [
          {
            "temperatura_c": 28.5,
            "humedad_relativa": 65.2,
            "nh3_ppm": 12.3
          }
        ]
      }

  4_. Publica MQTT
      mqtt_client.publish(
        topic="avicola/galpon1/lecturas",
        payload=json_payload,
        qos=1
      )

  5_. Sleep 5 segundos
      time.sleep(5)

# MANEJO DE ERRORES
- Si error ModBus → log warning, continue (no publica)
- Si error MQTT → librería paho auto-reconecta
- Si sensor desconectado → lecturas null
- No guarda datos localmente (es un simple puente)
```

### 9.2 Script: `backend_bridge.py` (Puente HTTP local)

```python
# EXPONE API HTTP para compatibilidad legacy
@app.get("/api/dashboard")
  → Retorna estado en memoria (temperatura, humedad, etc)

@app.get("/api/ventilacion", "/api/criadoras", "/api/bombas")
  → Estados vacíos (no integrados)

# CONECTA A MQTT LOCAL
MQTT_BROKER = "localhost"
MQTT_TOPIC = "avicola/galpon1/lecturas"

on_message():
  - Extrae primer reading
  - Actualiza dashboard_state en memoria
  - No persiste a BD (eso lo hace Spring Boot)
```

### 9.3 Cambios necesarios para Multi-Galpón

**Actualmente:**
```
gateway_id = "raspi5-galpon-01"  (Fijo)
topic = "avicola/galpon1/lecturas"  (Fijo)
broker = "localhost"  (Local)
```

**Recomendado Multi-Galpón:**
```
# Cada Raspberry tendría configuración diferente

# Galpon 1:
gateway_id = "raspi5-galpon-01"
topic = "avicola/galpon/01/lecturas"
broker = "central-server.example.com"

# Galpon 2:
gateway_id = "raspi5-galpon-02"
topic = "avicola/galpon/02/lecturas"
broker = "central-server.example.com"

# Etc...

# Agregaciones:
- table gateways (gateway_id, galpon_id, descripción)
- table galpones (id, nombre, ubicación, responsable)
- SensorReading.galpon_id (desnormalización o relación)
```

---

## 10. TOPICS MQTT DETECTADOS

### 10.1 Topics actuales

| Topic | Publicador | Suscriptor | Payload | Frecuencia | Uso |
|-------|-----------|-----------|---------|-----------|-----|
| `avicola/galpon1/lecturas` | Raspberry (leer_sensores.py) | Backend (MqttIngestionService) | {"gateway_id": "...", "timestamp": "...", "readings": [{"temperatura_c": 28.5, "humedad_relativa": 65.2, "nh3_ppm": 12.3}]} | Cada 5 seg | Ingesta datos sensores |
| `avicola/galpon1/alarmas` | Backend (AlarmMqttPublisherService) | WebSocket (opcional) | {"alarmId": 1, "ruleId": 5, "severity": "ALTA", "message": "Temp > 35", "status": "ACTIVA", "activatedAt": "2026-06-05T12:00:30Z"} | Por evento | Publicación alertas |
| `avicola/galpon1/actuadores/estado` | Backend (MqttActuatorPublisherService) | Raspberry | {"actuatorType": "EXTRACTOR", "actuatorId": 1, "action": "ON", "commandId": 123} | Por cambio | Comandos control |
| (No encontrado) | N/A | N/A | N/A | N/A | Telemetría estado gateway |

### 10.2 Estructura recomendada Multi-Galpón

```
# LECTURAS DE SENSORES
avicola/galpon/{galpon_id}/lecturas
  → {"gateway_id": "...", "temperatura_c": 28.5, ...}

# ALERTAS
avicola/galpon/{galpon_id}/alarmas
  → {"severity": "ALTA", "message": "...", ...}

# ESTADO CONEXIÓN
avicola/galpon/{galpon_id}/estado
  → {"status": "connected", "gateway": "raspi5-01", "uptime": 3600}

# COMANDOS A ACTUADORES
avicola/galpon/{galpon_id}/actuadores/extractores/cmd
  → {"extractorId": 1, "action": "ON"}

avicola/galpon/{galpon_id}/actuadores/criadoras/cmd
  → {"criadoraId": 1, "action": "ON", "temperature": 25.0}

avicola/galpon/{galpon_id}/actuadores/bombas/cmd
  → {"bombaId": 1, "action": "ON"}

# RESPUESTAS
avicola/galpon/{galpon_id}/actuadores/respuestas
  → {"commandId": 123, "status": "EXECUTED", "timestamp": "..."}
```

---

## 11. ESTRUCTURA ACTUAL DE BASE DE DATOS

### 11.1 File: `V1__init.sql`

```sql
CREATE TABLE flocks (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  total_birds INT NOT NULL,
  male_count INT NOT NULL,
  female_count INT NOT NULL,
  flock_date DATE NOT NULL,
  bird_lot VARCHAR(80) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  started_at TIMESTAMP WITH TIME ZONE NOT NULL,
  ended_at TIMESTAMP WITH TIME ZONE
);
CREATE UNIQUE INDEX uq_flocks_single_active 
  ON flocks (status) WHERE status = 'ACTIVE';

CREATE TABLE sensor_readings (
  id BIGSERIAL PRIMARY KEY,
  flock_id BIGINT NOT NULL REFS flocks(id),
  recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
  gateway_id VARCHAR(80),
  source_topic VARCHAR(255),
  temperature_c DOUBLE,
  humidity_percent DOUBLE,
  nh3_ppm DOUBLE
);

CREATE TABLE mortality_records (
  id BIGSERIAL PRIMARY KEY,
  flock_id BIGINT NOT NULL REFS flocks(id),
  record_date DATE NOT NULL,
  age_days INT NOT NULL,
  male_count INT NOT NULL,
  female_count INT NOT NULL,
  total_count INT NOT NULL,
  observations VARCHAR(1000),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE weight_records (
  id BIGSERIAL PRIMARY KEY,
  flock_id BIGINT NOT NULL REFS flocks(id),
  sampled_birds_count INT NOT NULL,
  average_weight DOUBLE NOT NULL,  -- gramos
  age INT NOT NULL,  -- días
  record_date DATE NOT NULL,
  gender VARCHAR(20) NOT NULL,  -- MALE, FEMALE, MIXED
  location VARCHAR(30) NOT NULL,  -- GALLERA, COMEDERO, etc
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Tablas de actuadores y históricos
CREATE TABLE extractors (...);
CREATE TABLE extractor_programming (...);
CREATE TABLE extractor_programming_history (...);

CREATE TABLE criadoras (...);
CREATE TABLE criadora_programming (...);
CREATE TABLE criadora_programming_history (...);

CREATE TABLE bombas (...);
CREATE TABLE bomba_programming (...);
CREATE TABLE bomba_programming_history (...);

-- Tabla de alarmas
CREATE TABLE alarm_rules (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  variable VARCHAR(20) NOT NULL,  -- TEMPERATURA, HUMEDAD, NH3
  condition_type VARCHAR(20) NOT NULL,  -- MAYOR_QUE, MENOR_QUE, etc
  threshold DOUBLE NOT NULL,
  unit VARCHAR(10) NOT NULL,
  minimum_duration_seconds INT NOT NULL,
  severity VARCHAR(20) NOT NULL,  -- BAJA, MEDIA, ALTA
  message VARCHAR(500) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

CREATE TABLE alarms (
  id BIGSERIAL PRIMARY KEY,
  rule_id BIGINT NOT NULL REFS alarm_rules(id),
  status VARCHAR(20) NOT NULL,  -- ACTIVA, RECONOCIDA, RESUELTA, CERRADA
  activated_at TIMESTAMP,
  acknowledged_at TIMESTAMP,
  resolved_at TIMESTAMP,
  closed_at TIMESTAMP
);

CREATE TABLE alarm_events (
  id BIGSERIAL PRIMARY KEY,
  alarm_id BIGINT NOT NULL REFS alarms(id),
  event_type VARCHAR(30) NOT NULL,  -- ALARMA_ACTIVADA, etc
  previous_status VARCHAR(20),
  new_status VARCHAR(20),
  description VARCHAR(500),
  event_at TIMESTAMP
);
```

### 11.2 Tablas para dashboard

| Tabla | Consultas típicas |
|-------|------------------|
| `sensor_readings` | SELECT * WHERE recorded_at BETWEEN ? AND ? ORDER BY recorded_at DESC LIMIT 1000 |
| `alarms` | SELECT * WHERE status IN ('ACTIVA', 'RECONOCIDA') ORDER BY activated_at DESC |
| `flocks` | SELECT * WHERE status = 'ACTIVE' |
| `weight_records` | SELECT AVG(average_weight), COUNT(*) GROUPED BY gender, recordDate |
| `mortality_records` | SELECT * WHERE recordDate = TODAY |
| `actuator_*_history` | SELECT * ORDER BY changed_at DESC LIMIT 100 |

### 11.3 Tablas para históricos

Todas guardan datos históricos (no se borran):
- `sensor_readings` → Histórico completo de lecturas
- `mortality_records` → Histórico mortalidad por parvada
- `weight_records` → Histórico pesos+evolución
- `*_programming_history` → Cambios en programación
- `alarms`, `alarm_events` → Completo ciclo alarmas

---

## 12. DISEÑO RECOMENDADO PARA SERVIDOR CENTRAL MULTI-GALPÓN

### 12.1 Nuevas tablas

```sql
-- ESTRUCTURA MULTI-GALPÓN
CREATE TABLE galpones (
  id BIGSERIAL PRIMARY KEY,
  codigo VARCHAR(50) UNIQUE NOT NULL,  -- "G-01", "G-02"
  nombre VARCHAR(120) NOT NULL,
  ubicacion VARCHAR(255),
  responsable VARCHAR(100),
  capacidad_aves INT,
  activo BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

CREATE TABLE gateways (
  id BIGSERIAL PRIMARY KEY,
  galpon_id BIGINT NOT NULL REFS galpones(id),
  gateway_id VARCHAR(80) UNIQUE NOT NULL,  -- "raspi5-galpon-01"
  nombre VARCHAR(120),
  tipo VARCHAR(50),  -- "RASPBERRY_PI", "ORANGE_PI", etc
  ip_address INET,
  estado VARCHAR(50),  -- "CONECTADO", "OFFLINE", "ERRORES"
  ultima_conexion TIMESTAMP,
  created_at TIMESTAMP
);

CREATE TABLE sensores (
  id BIGSERIAL PRIMARY KEY,
  gateway_id BIGINT NOT NULL REFS gateways(id),
  codigo VARCHAR(100) UNIQUE NOT NULL,  -- "T-01", "H-01", "NH3-01"
  nombre VARCHAR(120),
  tipo VARCHAR(50),  -- "TEMPERATURA", "HUMEDAD", "NH3", "PRESION"
  ubicacion VARCHAR(255),  -- "Entrada", "Centro", "Salida"
  unidad VARCHAR(20),  -- "°C", "%", "ppm"
  rango_min DOUBLE,
  rango_max DOUBLE,
  calibracion_offset DOUBLE DEFAULT 0,
  estado VARCHAR(50),  -- "FUNCIONANDO", "ERROR", "SIN_DATOS"
  ultima_lectura TIMESTAMP,
  created_at TIMESTAMP
);

-- MODIFICAR
ALTER TABLE flocks ADD COLUMN galpon_id BIGINT REFS galpones(id);

ALTER TABLE sensor_readings ADD COLUMN galpon_id BIGINT REFS galpones(id);
ALTER TABLE sensor_readings ADD COLUMN sensor_id BIGINT REFS sensores(id);
ALTER TABLE sensor_readings ADD COLUMN gateway_id BIGINT REFS gateways(id);

-- Índices para performance
CREATE INDEX idx_readings_galpon_time ON sensor_readings(galpon_id, recorded_at DESC);
CREATE INDEX idx_readings_sensor_time ON sensor_readings(sensor_id, recorded_at DESC);

ALTER TABLE mortality_records ADD COLUMN galpon_id BIGINT REFS galpones(id);
ALTER TABLE weight_records ADD COLUMN galpon_id BIGINT REFS galpones(id);
ALTER TABLE alarm_rules ADD COLUMN galpon_id BIGINT DEFAULT NULL REFS galpones(id);
  -- NULL = regla global; NOT NULL = regla específica galpón

-- Control distribuido
CREATE TABLE actuator_control_states (
  id BIGSERIAL PRIMARY KEY,
  actuator_type VARCHAR(50) NOT NULL,  -- "EXTRACTOR", "CRIADORA", "BOMBA"
  actuator_id BIGINT NOT NULL,
  galpon_id BIGINT NOT NULL REFS galpones(id),
  current_state VARCHAR(50),  -- "ON", "OFF", "ERROR"
  updated_at TIMESTAMP
);

-- Webhook/notificaciones
CREATE TABLE alertas_config (
  id BIGSERIAL PRIMARY KEY,
  galpon_id BIGINT NOT NULL REFS galpones(id),
  canal VARCHAR(50),  -- "EMAIL", "SMS", "WEBHOOK", "PUSH"
  destino VARCHAR(255),  -- dirección email, número, URL
  creado_por VARCHAR(100),
  created_at TIMESTAMP
);
```

### 12.2 Cambios a entidades Java

```java
// Agregar a Flock.java
@ManyToOne
@JoinColumn(name = "galpon_id", nullable = false)
private Galpon galpon;

// Agregar a SensorReading.java
@ManyToOne
@JoinColumn(name = "galpon_id", nullable = false)
private Galpon galpon;

@ManyToOne
@JoinColumn(name = "gateway_id")
private Gateway gateway;

@ManyToOne
@JoinColumn(name = "sensor_id")
private Sensor sensor;

// Agregar a AlarmRule.java
@ManyToOne
@JoinColumn(name = "galpon_id")
private Galpon galpon;  // null = global

// Nuevas entidades
@Entity
public class Galpon { id, codigo, nombre, responsable, capacidad, activo }

@Entity
public class Gateway { id, galpon, gateway_id, nombre, tipo, ip_address, estado }

@Entity
public class Sensor { id, gateway, codigo, nombre, tipo, ubicacion, unidad, rango }
```

### 12.3 Endpoints nuevos/modificados

```
# Galpones
GET    /api/galpones               → Listar todos
POST   /api/galpones               → Crear
GET    /api/galpones/{id}          → Detalle
PUT    /api/galpones/{id}          → Editar
GET    /api/galpones/{id}/status   → Estado actual

# Gateways
GET    /api/galpones/{id}/gateways → Gateways por galpón
POST   /api/gateways               → Registrar gateway
GET    /api/gateways/{id}/sensors  → Sensores conectados

# Lecturas multi-galpón
GET    /api/lecturas?galpon_id=1&start=...&end=...   → Histórico por galpón
GET    /api/lecturas/ultimo?galpon_id=1              → Última lectura
GET    /api/comparativa?galpon_ids=1,2,3             → Comparar galpones

# Alertas multi-galpón
GET    /api/alertas?galpon_id=1               → Por galpón
GET    /api/alertas?estado=ACTIVA             → Activas de todos

# Dashboard central
GET    /api/dashboard/resumen                 → Resumen todos galpones
GET    /api/dashboard/galpones                → Estado cada galpón
GET    /api/dashboard/alertas-criticas       → Top críticas
```

---

## 13. ENDPOINTS NECESARIOS PARA DASHBOARD WEB

### 13.1 Dashboard General (Home)

```
GET /api/dashboard/general
Respuesta:
{
  "totalGalpones": 5,
  "galpones": [
    {
      "id": 1,
      "nombre": "Galpón 1",
      "statusFlock": "ACTIVE",
      "edadParvada": 15,
      "temperatura": 28.5,
      "humedad": 65.2,
      "alertasActivas": 2,
      "mortalidadHoy": 3,
      "ultimaActividad": "2026-06-05T12:05:30Z"
    },
    ...
  ],
  "alertasCriticas": 5,
  "lecturasSinDatos": 1,
  "ultimaActualizacion": "2026-06-05T12:05:30Z"
}
```

### 13.2 Detalle de Galpón

```
GET /api/galpones/{galponId}/detalle
Respuesta:
{
  "id": 1,
  "codigo": "G-01",
  "nombre": "Galpón Principal",
  "responsable": "Juan Pérez",
  "capacidad": 10000,
  "parvadaActiva": {
    "id": 3,
    "nombre": "Lote Mayo 2026",
    "dia": 15,
    "totalAves": 9950,  (10000 - muertas)
    "fechaInicio": "2026-05-21",
    "pesoPromedio": 850,
    "pesoPromediMachos": 950,
    "pesoPromediHembras": 780
  },
  "sensores": [
    { "id": 1, "nombre": "Temp Ambiente", "valor": 28.5, "estado": "OK" },
    { "id": 2, "nombre": "Humedad", "valor": 65.2, "estado": "OK" },
    { "id": 3, "nombre": "NH3", "valor": 12.3, "estado": "WARNING" }
  ],
  "actuadores": [
    { "id": 1, "tipo": "EXTRACTOR", "nombre": "Ventilador Principal", "estado": "ON" },
    { "id": 2, "tipo": "CRIADORA", "nombre": "Calefactor", "estado": "OFF" },
    { "id": 3, "tipo": "BOMBA", "nombre": "Bomba Agua", "estado": "ON" }
  ],
  "gateway": {
    "id": 1,
    "codigo": "raspi5-galpon-01",
    "estado": "CONECTADO",
    "ultimaData": "2026-06-05T12:05:30Z"
  }
}
```

### 13.3 Últimas Lecturas por Galpón

```
GET /api/galpones/{galponId}/lecturas/ultimas?limit=50
Respuesta:
[
  {
    "timestamp": "2026-06-05T12:05:30Z",
    "temperatura": 28.5,
    "humedad": 65.2,
    "nh3": 12.3,
    "gateway": "raspi5-galpon-01"
  },
  ...
]
```

### 13.4 Histórico Filtrable

```
GET /api/galpones/{galponId}/lecturas?
    start=2026-06-01&end=2026-06-05&
    variable=TEMPERATURA&
    page=0&size=1000
Respuesta:
{
  "data": [...],
  "meta": { "page": 0, "size": 1000, "total": 5000 }
}
```

### 13.5 Alertas

```
GET /api/alertas?galpon_id={id}&estado=ACTIVA
Respuesta:
[
  {
    "id": 1,
    "regla": "Temperatura Alta",
    "severidad": "ALTA",
    "mensaje": "Temperatura > 35°C",
    "valor": 36.2,
    "threshold": 35.0,
    "activadaEn": "2026-06-05T11:45:00Z",
    "duracion": "20 minutos",
    "reconocida": false
  },
  ...
]
```

### 13.6 Parvada Activa

```
GET /api/galpones/{galponId}/parvada/activa
Respuesta:
{
  "id": 3,
  "nombre": "Lote Mayo 2026",
  "dia": 15,
  "totalAves": 9950,
  "maleCount": 2985,
  "femaleCount": 6965,
  "fechaInicio": "2026-05-21",
  "birdLot": "LT-2026-01",
  "pesoPromedio": 850,
  "consumoAguaHoy": 450.5,
  "consumoAlimentoHoy": 120.3,
  "mortalidadHoy": 2,
  "mortalidadTotal": 50
}
```

### 13.7 Historial de Parvadas

```
GET /api/galpones/{galponId}/parvadas
Respuesta:
[
  {
    "id": 3,
    "nombre": "Lote Mayo 2026",
    "status": "ACTIVE",
    "iniciada": "2026-05-21T08:00:00Z",
    "cerrada": null,
    "dias": 15,
    "totalAves": 9950,
    "mortalidadTotal": 50,
    "pesoPromedio": 850
  },
  {
    "id": 2,
    "nombre": "Lote Abril 2026",
    "status": "CLOSED",
    "iniciada": "2026-04-10T08:00:00Z",
    "cerrada": "2026-05-20T16:30:00Z",
    "dias": 41,
    "totalAves": 9700,
    "mortalidadTotal": 300,
    "pesoPromedio": 2100
  }
]
```

### 13.8 Mortalidad

```
GET /api/galpones/{galponId}/mortalidad?start=2026-06-01&end=2026-06-05
Respuesta:
[
  {
    "fecha": "2026-06-05",
    "dia": 15,
    "machos": 1,
    "hembras": 1,
    "total": 2,
    "observaciones": "Encontradas en comedero"
  },
  ...
]
```

### 13.9 Peso

```
GET /api/galpones/{galponId}/peso/evolucion?start=2026-06-01&end=2026-06-05
Respuesta:
{
  "machos": [
    { "fecha": "2026-06-01", "edad": 11, "peso": 800 },
    { "fecha": "2026-06-03", "edad": 13, "peso": 820 },
    { "fecha": "2026-06-05", "edad": 15, "peso": 850 }
  ],
  "hembras": [
    { "fecha": "2026-06-01", "edad": 11, "peso": 750 },
    { "fecha": "2026-06-03", "edad": 13, "peso": 765 },
    { "fecha": "2026-06-05", "edad": 15, "peso": 780 }
  ]
}
```

### 13.10 Reportes Comparativos

```
GET /api/reportes/comparativa?galpon_ids=1,2,3&fecha=2026-06-05
Respuesta:
{
  "fecha": "2026-06-05",
  "galpones": [
    {
      "galponId": 1,
      "nombre": "G-01",
      "temperatura": 28.5,
      "humedad": 65.2,
      "pesoPromedio": 850,
      "mortalidadDia": 2,
      "alertasActivas": 1
    },
    {
      "galponId": 2,
      "nombre": "G-02",
      "temperatura": 27.8,
      "humedad": 62.5,
      "pesoPromedio": 840,
      "mortalidadDia": 3,
      "alertasActivas": 0
    },
    ...
  ]
}
```

---

## 14. DATOS NECESARIOS PARA DASHBOARD (POR PANTALLA)

### 14.1 Dashboard General

**Endpoint:** `GET /api/dashboard/general`

**Datos:**
- Resumen de todos los galpones (estado, temp, humedad, parvada activa)
- Alertas críticas globales
- Gateways offline
- Tendencia última hora

**Componentes visuales:**
- Tarjetas resumen por galpón (4 en grid)
- Tabla alertas activas (severidad → color)
- Indicadores de estado gateways
- Gráfica tendencia temperatura global

### 14.2 Detalle Galpón

**Endpoint:** `GET /api/galpones/{id}/detalle`

**Datos:**
- Estado actual sensores (temp, humedad, nh3)
- Estado actuadores (encendidos/apagados)
- Parvada activa (día, edad, aves vivas)
- Últimas lecturas

**Componentes visuales:**
- 3 números grandes: Temp, Humedad, NH3
- Tabla actuadores (nombre, estado, última acción)
- Tarjeta parvada actual
- Gráfica última hora

### 14.3 Vista Sensores

**Endpoint:** `GET /api/galpones/{id}/sensores`

**Datos:**
- Estado de cada sensor
- Último valor
- Rango normal
- Alertas asociadas

**Componentes visuales:**
- Tabla sensores (nombre, valor, estado, alerta)
- Gráficas sensor individual (seleccionable)
- Timeline de cambios

### 14.4 Vista Alertas

**Endpoint:** `GET /api/alertas?galpon_id={id}`

**Datos:**
- Alertas activas (severidad, regla, valor, duración)
- Histórico últimas 48h
- Estadísticas (alertas/hora, top reglas)

**Componentes visuales:**
- Lista alertas (color por severidad)
- Botones: Reconocer, Cerrar, Ver regla
- Gráfica alertas por hora
- Tablas estadísticas

### 14.5 Vista Parvadas

**Endpoint:** `GET /api/galpones/{id}/parvadas`

**Datos:**
- Parvada activa (día, aves, peso)
- Histórico parvadas cerradas
- Métricas cada parvada

**Componentes visualis:**
- Tarjeta parvada actual (grande)
- Tabla histórico parvadas cerradas
- Comparator: parvada anterior vs actual

### 14.6 Vista Mortalidad

**Endpoint:** `GET /api/galpones/{id}/mortalidad?start=...&end=...`

**Datos:**
- Mortalidad diaria (machos, hembras, total)
- Tasa mortalidad % (muertas / total)
- Observaciones

**Componentes visuales:**
- Tabla mortalidad diaria
- Gráfica barras mortalidad acumulada
- Indicador tasa % hoy

### 14.7 Vista Peso

**Endpoint:** `GET /api/galpones/{id}/peso/evolucion`

**Datos:**
- Evolución peso machos/hembras
- Comparación vs estándar raza
- Muestreos registrados

**Componentes visuales:**
- Gráfica líneas: peso machos vs hembras
- Tablas: últimos muestreos por sexo
- Banda % desviación estándar

### 14.8 Vista Reportes

**Endpoints:**
- `GET /api/reportes/comparativa?galpon_ids=1,2,3`
- `GET /api/reportes/rendimiento?galpon_id={id}&fecha={fecha}`

**Datos:**
- Comparativa entre galpones (temperatura, humedad, peso, mortalidad)
- Indicadores rendimiento

**Componentes visuales:**
- Tablas comparativas (multi-fila, multi-columna)
- Gráficas radar (comparar galpones)
- Tarjetas KPI

### 14.9 Vista Administración

**Endpoints:**
- `GET /api/galpones`
- `GET /api/galpones/{id}/gateways`
- `GET /api/galpones/{id}/sensores`
- `POST /api/alarms/rules`
- `PUT /api/alarms/rules/{id}`

**Datos:**
- Listado galpones, gateways, sensores
- CRUD reglas de alarma
- Configuración notificaciones

**Componentes visuales:**
- Tablas CRUD (Add, Edit, Delete)
- Formularios:
  - Crear galpón (nombre, responsable, capacidad)
  - Crear sensor (tipo, ubicación, rango)
  - Crear regla alarma (variable, condición, threshold, severidad)
- Validaciones inline

---

## 15. FLUJO RECOMENDADO DE DATOS PARA SISTEMA CENTRAL

### 15.1 Arquitectura de flujo

```
┌────────────────────────────────────────────────────────────────────┐
│                   DATACENTER / SERVIDOR CENTRAL                     │
│                    (AWS/DigitalOcean/On-Premise)                    │
├────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  MQTT Central Broker (EMQX)                                  │  │
│  │  Topic Structure:                                            │  │
│  │  - avicola/galpon/{id}/lecturas                             │  │
│  │  - avicola/galpon/{id}/alarmas                              │  │
│  │  - avicola/galpon/{id}/actuadores/cmd                       │  │
│  │  - avicola/galpon/{id}/actuadores/respuestas                │  │
│  │  - avicola/gateway/{gateway_id}/estado                      │  │
│  └────────────────────┬─────────────────────────────────────────┘  │
│                       │                                             │
│                       ▼                                             │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Backend Spring Boot (Multi-instancia)                       │  │
│  │  Réplicas: api-backend-1, api-backend-2, api-backend-3      │  │
│  │  LB: nginx/haproxy                                           │  │
│  │                                                              │  │
│  │  Servicios:                                                  │  │
│  │  - MqttIngestionService (consume todos los topics)           │  │
│  │  - SensorReadingService (guarda sensor_readings)             │  │
│  │  - AlarmEvaluationService (evalúa reglas)                    │  │
│  │  - DashboardService (agrega vista central)                   │  │
│  │  - ReportService (reportes multi-galpón)                     │  │
│  │                                                              │  │
│  │  REST API (/api/galpones, /api/alertas, /api/reportes)      │  │
│  │  WebSocket /ws/dashboard (tiempo real)                       │  │
│  └────────────────────┬──────────────────────┬─────────────────┘  │
│                       │                      │                     │
│                       ▼                      ▼                     │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  PostgreSQL (HA setup)                                       │  │
│  │  - Primary / Replica / Backup                               │  │
│  │  - Tablas: flocks, sensor_readings, alarms, etc             │  │
│  │  - Índices optimizados para queries por galpon/hora         │  │
│  │  - Retention policy: sensores 90 días, resto indefinido    │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Cache (Redis)                                               │  │
│  │  - Últimas lecturas por galpon (cache 5 min)                │  │
│  │  - Estados actuadores (cache 1 min)                         │  │
│  │  - Sesiones dashboard usuarios                              │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Frontend (Node.js / React)                                  │  │
│  │  Réplicas en CDN                                             │  │
│  └──────────────────────────────────────────────────────────────┘  │
├────────────────────────────────────────────────────────────────────┤
│                   WAN / INTERNET                                    │
│                      (VPN/TLS)                                      │
├────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────┬─────────────────────┬─────────────────┐   │
│  │   GALPÓN 1          │   GALPÓN 2          │   GALPÓN 3      │   │
│  │   (Raspberry Pi)    │   (Raspberry Pi)    │   (O. Pi 5)    │   │
│  │ raspi5-galpon-01    │ raspi5-galpon-02    │opi5-galpon-03  │   │
│  │                     │                     │                 │   │
│  │ ┌────────────────┐  │ ┌────────────────┐  │ ┌────────────┐  │   │
│  │ │ Sensores       │  │ │ Sensores       │  │ │ Sensores   │  │   │
│  │ │ XY-MD03 x3     │  │ │ XY-MD03 x3     │  │ │ XY-MD03 x2 │  │   │
│  │ │ Modbus RTU     │  │ │ Modbus RTU     │  │ │ Modbus     │  │   │
│  │ └────────┬────────┘  │ └────────┬────────┘  │ └────────┬───┘  │   │
│  │          │           │          │           │          │      │   │
│  │ ┌────────▼────────┐  │ ┌────────▼────────┐  │ ┌────────▼──┐   │   │
│  │ │leer_sensores.py │  │ │leer_sensores.py │  │ │leer_sens.py   │   │
│  │ │pub MQTT         │  │ │pub MQTT         │  │ │pub MQTT   │   │   │
│  │ └────────┬────────┘  │ └────────┬────────┘  │ └────────┬──┘   │   │
│  │          │           │          │           │          │      │   │
│  │ Actuadores:         │ Actuadores:         │ Actuadores:       │   │
│  │ - Extractores       │ - Extractores       │ - Extractores     │   │
│  │ - Criadoras         │ - Criadoras         │ - Criadoras       │   │
│  │ - Bombas            │ - Bombas            │ - Bombas          │   │
│  └─────────┬───────────┘ └─────────┬──────────┘ └────────┬────────┘   │
│            │                       │                     │            │
│            └───────────────────────┼─────────────────────┘            │
│                                    │                                  │
│                  ┌─────────────────▼──────────────┐                  │
│                  │ Firewall / VPN Gateway         │                  │
│                  │ puerto MQTT 8883 (TLS)         │                  │
│                  └─────────────────┬──────────────┘                  │
│                                    │                                  │
│                           (Internet / VPN)                            │
│                                    │                                  │
│                            (hacia datacenter)                         │
└────────────────────────────────────────────────────────────────────┘
```

### 15.2 Secuencia de flujo (paso a paso)

```
1. LECTURA SENSOR (Cada 5 seg)
   Raspberry Pi /dev/ttyUSB0 (ModBus)
      ↓
   PyModbus read_input_registers()
      ↓
   Parse JSON: {gateway_id, timestamp, readings[temp, humedad, nh3]}
      ↓
   MQTT Publish: avicola/galpon/01/lecturas

2. RECEPCIÓN EN BROKER CENTRAL
   EMQX broker recibe mensaje
      ↓
   Verifica credenciales (cliente: raspi5-galpon-01)
      ↓
   Almacena en persistent queue (en caso de momentáneo error)
      ↓
   Distribuye a suscriptores:
      - Backend Spring Boot (subscriber 1)
      - WebSocket bridge (subscriber 2, para clients)
      - Archive service (subscriber 3, opcional)

3. PROCESAMIENTO EN BACKEND
   MqttIngestionService.messageArrived()
      ├─ Deserializa JSON
      ├─ Extrae: gateway_id="raspi5-galpon-01", temp=28.5, humedad=65.2, nh3=12.3
      ├─ Parsea timestamp o usa NOW()
      └─ Llama: SensorReadingService.saveIfActiveFlock()
            ├─ Obtiene Flock con status=ACTIVE para galpon_id=1
            ├─ Enriquece: galpon_id, gateway_id, sensor_id (si mapea)
            ├─ Crea SensorReading entity
            └─ jdbcTemplate.insert() → PostgreSQL
               INSERT INTO sensor_readings (flock_id, galpon_id, gateway_id, ...)
                   VALUES (3, 1, 1, 1, NOW(), '2026-06-05T12:00:30Z', 28.5, 65.2, 12.3)

4. EVALUACIÓN DE ALARMAS (Async)
   AlarmEvaluationService.evaluate(reading)
      ├─ Obtiene AlarmRules activas (donde galpon_id IN (1, NULL))
      │  [Regla global "Temp > 35" (galpon_id=NULL), Regla "Temp > 32" (galpon_id=1)]
      ├─ Para cada regla:
      │  ├─ Compara: reading.temperatureC (28.5) vs rule.threshold (35 o 32)
      │  ├─ Si NO cumple condición → no dispara
      │  ├─ Si SÍ cumple Y existe Alarm abierta → ignore (prevent dupes)
      │  └─ Si SÍ cumple Y NO existe → createActivation
      │        ├─ INSERT INTO alarms (rule_id, status='ACTIVA', triggered_at)
      │        ├─ INSERT INTO alarm_events (alarm_id, event_type='ALARMA_ACTIVADA', ...)
      │        └─ MQTT pub: avicola/galpon/01/alarmas { severity: "ALTA", ... }
      └─ Publicação WebSocket para clientes (tiempo real)

5. EMISIÓN DE COMANDOS DE CONTROL (Async)
   ActuatorControlService.evaluateAndQueue(reading)
      ├─ Obtiene ExtractorProgramming, CriadoraProgramming, etc.
      ├─ Evalúa condiciones (ej: si humidity < 40% → encender extractor)
      ├─ Si condición → INSERT INTO actuator_control_commands (status='PENDING')
      ├─ Código en BD: acción="ON", actuator_type="EXTRACTOR", actuator_id=1
      └─ Raspberry Pi polling GET /api/control/commands/pending cada 30 seg
            ├─ Obtiene: [{ commandId: 123, action: "ON", ... }]
            ├─ Ejecuta: relay GPIO.output(17, True)  → enciende ventilador
            ├─ Confirma: POST /api/control/commands/123/dispatch { status: "EXECUTED" }
            └─ Backend actualiza: UPDATE actuator_control_commands SET status='EXECUTED'

6. CONSUMO EN DASHBOARD
   Frontend (React/Vue)
      ├─ GET /api/dashboard/general
      │  ← {galpones[{id, temp, humedad, alertas}], alertasCriticas}
      │
      ├─ GET /api/galpones/1/lecturas/ultimas
      │  ← [{timestamp, temp, humedad, nh3}, ...]
      │
      ├─ WebSocket /ws/dashboard
      │  → Recibe broadcast de nuevas lecturas en tiempo real
      │
      └─ Mostrar gráficas, tablas, indicadores en vivo

7. PERSISTENCIA
   PostgreSQL (primaria)
      ├─ Escribe todas las inserciones
      ├─ Réplica replica datos
      ├─ Backup diario a S3 / NFS
      └─ Retention: sensor_readings 90 días, resto indefinido

8. CACHE (Redis)
   Datos frecuentes cacheados para velocidad
      ├─ Últimas lecturas por galpon: cache 5 min
      ├─ Temperatura actual: cache 30 seg
      ├─ Alertas activas: cache 1 min
      └─ Sesiones usuarios: cache session duration
```

---

## 16. MEJORAS NECESARIAS EN CÓDIGO ACTUAL

### 16.1 Base de Datos

| Problema | Recomendación | Prioridad |
|----------|---------------|-----------|
| No existe tabla `galpones` | Crear tabla + relaciones | **CRÍTICA** |
| No existe tabla `gateways` | Crear tabla + status | **CRÍTICA** |
| No existe tabla `sensores` | Crear tabla + mapeo | **ALTA** |
| Índices falta en sensor_readings | Agregar índices (galpon_id, flock_id, recorded_at DESC) | **ALTA** |
| No hay particionamiento | Particionar por fecha si > 100M rows | **MEDIA** |
| No hay vacío/mantenimiento | Programar VACUUM, ANALYZE | **MEDIA** |
| No hay monitoreo alertas BD | Configurar PgAdmin, Prometheus | **BAJA** |

### 16.2 Backend

| Problema | Recomendación | Prioridad |
|----------|---------------|-----------|
| MQTT sin autenticación | Agregar credenciales MQTT broker | **CRÍTICA** |
| Sin rate limiting en API | Implementar @RateLimiter | **ALTA** |
| Sin paginación en /api/readings | Implementar Pageable genérico | **ALTA** |
| No hay búsqueda de sensores | Crear SensorService + endpoints | **ALTA** |
| No hay filtros por galpon | Actualizar todas las queries | **CRÍTICA** |
| Sin validaciones de entrada | Agregar @Validated, @NotNull, etc | **MEDIA** |
| Sin compresión de respuestas | Agregar @GzipEncoding | **MEDIA** |
| No hay API key security | Implementar JWT o API keys | **CRÍTICA** |
| WebSocket no implementado | Crear estompbroker para tiempo real | **MEDIA** |
| No hay async processing | Convertir a reactive (Project Reactor) | **BAJA** |

### 16.3 Raspberry

| Problema | Recomendación | Prioridad |
|----------|---------------|-----------|
| Hardcoded broker "localhost" | Usar variables de entorno | **ALTA** |
| Sin reintentos de conexión | Agregar backoff exponencial | **MEDIA** |
| Sin buffering local | Guardar en SQLite si MQTT falla | **MEDIA** |
| Sin configuración remota | Crear endpoint config pull | **BAJA** |
| Sin heartbeat | Agregar ping/keep-alive | **BAJA** |
| Scripts en /home/leo | Mejorar path, usar Systemd unit | **MEDIA** |
| Sin logs centralizados | Enviar a Loki / ELK si escala | **BAJA** |

### 16.4 MQTT

| Problema | Recomendación | Prioridad |
|----------|---------------|-----------|
| Solo 1 topic | Implementar estructura multi-galpon | **CRÍTICA** |
| QoS=1 siempre | Evaluar QoS 0 para datos, 1 para comandos | **MEDIA** |
| Sin tópicos de estado | Agregar `avicola/galpon/{id}/estado` | **MEDIA** |
| Sin tópicos de comandos | Agregar `avicola/galpon/{id}/actuadores/cmd` | **ALTA** |
| Sin Last Will Testament | Implementar para detectar desconexiones | **MEDIA** |

### 16.5 Seguridad

| Problema | Recomendación | Prioridad |
|----------|---------------|-----------|
| Sin autenticación | Implementar JWT + roles | **CRÍTICA** |
| Sin encriptación MQTT | Usar MQTT TLS (puerto 8883) | **CRÍTICA** |
| Sin CORS configurado | Restringir origins a frontend | **ALTA** |
| Contraseñas hardcoded | Usar Vault / Secrets Manager | **ALTA** |
| Sin auditoría | Registrar cambios críticos (alarms, actuadores) | **MEDIA** |
| Sin validación schema JSON | Usar JSON Schema en broker | **MEDIA** |

### 16.6 Dashboard

| Problema | Recomendación | Prioridad |
|----------|---------------|-----------|
| No implementado | Crear desde cero (React/Vue) | **CRÍTICA** |
| Sin WebSocket | Implementar /ws para tiempo real | **ALTA** |
| Sin gráficas | Usar Chart.js, ApexCharts | **ALTA** |
| Sin responsividad | Diseño mobile-first + Tailwind | **MEDIA** |
| Sin notificaciones | Agregar toast/snackbar + PWA | **MEDIA** |
| Sin caché local | IndexedDB para offline mode | **BAJA** |

### 16.7 Escalabilidad

| Problema | Recomendación | Prioridad |
|----------|---------------|-----------|
| BD sin sharding | Particionar si > 10 galpones | **BAJA** |
| Sin load balancer | Agregar nginx/HAProxy | **MEDIA** |
| Backend monolito | Mantener así por ahora (< 50 galpones) | **BAJA** |
| Sin caching distribuido | Usar Redis si > 5 backends | **MEDIA** |
| Sin CDN | Cloudflare si múltiples regiones | **BAJA** |

### 16.8 Mantenimiento

| Problema | Recomendación | Prioridad |
|----------|---------------|-----------|
| Sin logs estructurados | Cambiar a SLF4J + Logback JSON | **MEDIA** |
| Sin monitoreo | Implementar Prometheus + Grafana | **MEDIA** |
| Sin alertas operacionales | PagerDuty / OpsGenie | **MEDIA** |
| Sin CI/CD | GitHub Actions + Sonarqube | **ALTA** |
| Dockerfile no optimizado | Multi-stage build | **MEDIA** |
| Sin versionamiento | Usar tags git semántico | **MEDIA** |

---

## 17. RIESGOS TÉCNICOS

### 17.1 Tabla de riesgos

| Riesgo | Descripción | Impacto | Probabilidad | Mitigación |
|--------|-------------|---------|-------------|-----------|
| **Pérdida datos sensores** | Si MQTT falla sin persistencia, se pierden lecturas | ALTO | MEDIA | Buffering local en Raspberry; Queue persistente MQTT |
| **Múltiples parvadas activas** | Sin validación en BD, pueden existir 2 parvadas ACTIVE | ALTO | BAJA | Índice UNIQUE con WHERE status=ACTIVE |
| **Desalineación de datos** | Alarms creadas sin galpon_id, lecturas sin mapping | CRÍTICO | MEDIA | Migraciones escalonadas; validar integridad |
| **Sensor mal identificado** | Gateway publica con gateway_id incorrecto | MEDIA | MEDIA | Validar en backend; rechazar unknowns |
| **Timestamp no confiable** | Raspberry Sin NTP, timestamp incorrecto | MEDIA | BAJA | Usar timestamp del broker; validar rango |
| **Actuadores stuck** | Comando no ejecuta, BD queda con status PENDING | MEDIA | BAJA | Timeout 5 min; marcar FAILED |
| **Alarma silenciosa** | Regla mal configurada, no dispara cuando debería | CRÍTICO | BAJA | Testing; validar ranges en UI |
| **BD sobrecargada** | 100M+ rows, queries lentas | MEDIA | BAJA | Particionamiento; purga datos old |
| **Fuga de memoria backend** | Memory leak en loops MQTT | ALTO | MEDIA | Heap monitoring; restartos programados |
| **Conexión MQTT inestable** | Reconexiones frecuentes | MEDIA | MEDIA | Backoff exponencial; heartbeat |
| **Cascada de alarmas** | Regla dispara N veces, flood DB | MEDIA | BAJA | Grouping; deduplicación; throttle |
| **No hay rollback** | Cambios en BD sin script rollback | CRÍTICO | BAJA | Versionamiento Flyway; tests en staging |

---

## 18. PROPUESTA FINAL DE ARQUITECTURA

### 18.1 Arquitectura Conceptual Multi-Galpón

```
┌──────────────────────────────────────────────────────────────────────┐
│                    AVIMAX CENTRAL - ARQUITECTURA V2                  │
└──────────────────────────────────────────────────────────────────────┘

LAYER 1: IOT SENSORS (Edge)
  ┌─────────────┬──────────────┬──────────────┐
  │ Galpón 1    │ Galpón 2     │ Galpón N     │
  │ Raspi5      │ Raspi5       │ O.Pi 5       │
  │ Sensores    │ Sensores     │ Sensores     │
  │ Actuadores  │ Actuadores   │ Actuadores   │
  └─────────────┴──────────────┴──────────────┘

LAYER 2: CONNECTIVITY (Transport)
  ┌──────────────────────────────────────────┐
  │ MQTT Central Broker (EMQX)               │
  │ - Persiste mensajes                      │
  │ - Autenticación TLS                      │
  │ - Topic hierarchía: galpon/{id}/...      │
  └──────────────────────────────────────────┘

LAYER 3: BACKEND (Application Logic)
  ┌──────────────────────────────────────────┐
  │ Spring Boot 3.4 (HA)                     │
  │ - MqttIngestionService (listener)        │
  │ - SensorReadingService (persistence)     │
  │ - AlarmEvaluationService (rules engine)  │
  │ - DashboardService (aggregation)         │
  │ - ReportService (BI)                     │
  │ REST API + WebSocket                     │
  └──────────────────────────────────────────┘

LAYER 4: DATA (Persistence)
  ┌──────────────────────────────────────────┐
  │ PostgreSQL (High Availability)           │
  │ - Primary / Standby / Read replicas      │
  │ - Backup daily to S3                     │
  │ - Tablespace: galpones, sensores, flocks│
  └──────────────────────────────────────────┘

LAYER 5: CACHE (Speed)
  ┌──────────────────────────────────────────┐
  │ Redis (session + realtime data)          │
  │ - Last readings (5 min TTL)              │
  │ - Active alarms (1 min TTL)              │
  │ - User sessions                          │
  └──────────────────────────────────────────┘

LAYER 6: FRONTEND (Presentation)
  ┌──────────────────────────────────────────┐
  │ React.js + Tailwind + Socket.io          │
  │ Dashboard responsivo multi-galpón        │
  │ Gráficas (ApexCharts), notificaciones    │
  │ PWA para offline (limitado)              │
  └──────────────────────────────────────────┘

LAYER 7: OPERATIONS
  ┌──────────────────────────────────────────┐
  │ Prometheus + Grafana (monitoring)        │
  │ ELK / Loki (logging)                     │
  │ GitHub Actions (CI/CD)                   │
  │ Sentry (error tracking)                  │
  └──────────────────────────────────────────┘
```

### 18.2 Componentes principales

| Componente | Responsabilidad | Tecnología | HA |
|-----------|-----------------|-----------|-----|
| **MQTT Broker** | Ingesta datos sensores | EMQX 5.0+ | Clustering |
| **Backend API** | Lógica aplicación | Spring Boot 3.4 | 3+ replicas |
| **PostgreSQL** | Persistencia | PG 15 | Primary/Standby |
| **Redis** | Cache + WebSocket | Redis 7 | Sentinel |
| **Frontend** | Dashboard web | React 18 | CDN |
| **Monitoring** | Observabilidad | Prometheus + Grafana | Stack |

### 18.3 Flujo de datos detallado

```
Sensor → Modbus → Raspberry Python
         ↓
MQTT Publish (avicola/galpon/{id}/lecturas)
         ↓
EMQX persiste en queue
         ↓
Backend subscrip + consume
         ↓
        ├→ SensorReadingService.save()
        │  └→ PostgreSQL INSERT
        │     └→ Redis SET (cache)
        │
        ├→ AlarmEvaluationService.evaluate()
        │  ├→ Reglas global + galpon-specific
        │  ├→ Si dispara → create Alarm
        │  └→ MQTT pub (avicola/galpon/{id}/alarmas)
        │     └→ WebSocket broadcast
        │
        └→ ActuatorControlService.queue()
           ├→ INSERT actuator_control_commands
           └→ Raspberry poll → ejecuta
              └→ POST /api/control/.../dispatch
```

### 18.4 Segmentación de datos

```
Galpón 1 (100 L/día):
  - Flock (activa) → 3 SensorReadings/min = 4320 lecturas/día
  - Alarmas: 0-100/día
  - Comandos: 50-500/día
  
Servidor Central (5 galpones):
  - ~20K lecturas/día
  - ~500 alarmas/día
  - ~1500 comandos/día
  - ~50K registros/mes
  
Escalabilidad:
  - 10 galpones: ~1M lecturas/mes
  - 50 galpones: ~5M lecturas/mes (particionamiento recomendado)
  - 100+ galpones: Microservicios + sharding
```

---

## 19. PLAN DE IMPLEMENTACIÓN POR FASES

### FASE 0: Preparación (1-2 semanas)

**Tareas:**
- [ ] Provisionar infraestructura cloud (AWS/DigitalOcean)
- [ ] Configurar EMQX broker central con TLS
- [ ] Crear BD PostgreSQL con tablas galpones, gateways, sensores
- [ ] Crear repositorio privado (GitHub)
- [ ] Setup CI/CD pipelines

### FASE 1: Adaptar Backend a Multi-Galpón (2-3 semanas)

**Tareas:**
- [ ] Crear entidades: Galpon, Gateway, Sensor
- [ ] Actualizar SensorReading con foreign keys
- [ ] Agregar columna `galpon_id` a flocks, alarms, rules
- [ ] Modificar MqttIngestionService para procesar multiple topics
- [ ] Actualizar Repositories con queries por galpon_id
- [ ] Implementar filtros en controllers
- [ ] Agregar validaciones de integridad
- [ ] Runear migraciones Flyway
- [ ] Tests unitarios + integración

**Endpoints nuevos:**
```
POST   /api/galpones
GET    /api/galpones
GET    /api/galpones/{id}
POST   /api/galpones/{id}/gateways
GET    /api/galpones/{id}/sensores
```

### FASE 2: Conectar Raspberry al Servidor Central (1-2 semanas)

**Tareas:**
- [ ] Actualizar `leer_sensores.py`:
  - [ ] Hostconf MQTT_BROKER = "central.example.com"
  - [ ] Credenciales (username/password)
  - [ ] Topic dinámico: `avicola/galpon/{galpon_id}/lecturas`
- [ ] Actualizar `backend_bridge.py` si aplica
- [ ] Testing en Raspberry con broker central
- [ ] Validar certificados TLS
- [ ] Agregar retry logic
- [ ] Deployment a Raspberry

### FASE 3: Endpoints Dashboard Central (2-3 semanas)

**Tareas:**
- [ ] GET /api/dashboard/general
- [ ] GET /api/galpones/{id}/detalle
- [ ] GET /api/galpones/{id}/lecturas (filtrable)
- [ ] GET /api/alertas (multi-galpon)
- [ ] GET /api/galpones/{id}/parvadas
- [ ] GET /api/galpones/{id}/mortalidad
- [ ] GET /api/galpones/{id}/peso/evolucion
- [ ] GET /api/reportes/comparativa
- [ ] Tests end-to-end

### FASE 4: Frontend Dashboard (3-4 semanas)

**Tareas:**
- [ ] Setup React project (Vite)
- [ ] Instalación librerías (Socket.io, ApexCharts, Tailwind)
- [ ] Diseño layout principal (grid 5 galpones)
- [ ] Páginas:
  - [ ] Dashboard general
  - [ ] Detalle galpón
  - [ ] Sensores
  - [ ] Alertas
  - [ ] Parvadas
  - [ ] Reportes
  - [ ] Admin
- [ ] WebSocket conexión tiempo real
- [ ] Gráficas + tablas
- [ ] Validaciones frontend
- [ ] PWA (opcional)
- [ ] Deploy a CDN

### FASE 5: Seguridad + Monitoring (2 semanas)

**Tareas:**
- [ ] JWT + roles implementados
- [ ] MQTT TLS enabledensus
- [ ] API rate limiting
- [ ] CORS configurado
- [ ] Secrets en Vault
- [ ] Prometheus metrics
- [ ] Grafana dashboards
- [ ] ELK logging
- [ ] Security testing (OWASP)
- [ ] Penetration testing

### FASE 6: Optimización + Docs (1-2 semanas)

**Tareas:**
- [ ] DB índices + particionamiento
- [ ] Caché Redis tuneado
- [ ] Backend async queries
- [ ] Frontend lazy loading
- [ ] API documentation (Swagger)
- [ ] Runbooks operacionales
- [ ] Training usuarios
- [ ] Backup + recovery procedures

### FASE 7: Despliegue Producción (1 semana)

**Tareas:**
- [ ] Infraestructura HA (load balancer, auto-scaling)
- [ ] Blue-Green deployment
- [ ] Rollback procedures
- [ ] SLA / uptime agreements
- [ ] Support 24/7 rotación
- [ ] Go-live con clientes
- [ ] Monitoreo intenso Fase 1 (dos semanas)

---

## 20. ENTREGA FINAL ESPERADA

### 20.1 Tabla: Endpoints Actuales vs Nuevos

| Módulo | Actual | Nuevo | Notas |
|--------|--------|-------|-------|
| **Status** | 2 | 2 | Sin cambios (MQTT, Health) |
| **Flocks** | 4 | 4 | Agregar galpon_id FK |
| **Readings** | 3 | 5 | Agregar filtros galpon, sensor |
| **Actuadores** | 14 | 14 | Agregar galpon_id FK |
| **Alarms** | 10 | 10 | Agregar galpon_id FK |
| **Galpones** | 0 | 5 | **NUEVO** (CRUD) |
| **Gateways** | 0 | 5 | **NUEVO** (CRUD) |
| **Sensors** | 0 | 5 | **NUEVO** (CRUD) |
| **Dashboard** | 1 | 10 | **NUEVO** (general, comparativas, reportes) |
| **Reportes** | 0 | 5 | **NUEVO** (BI) |
| **TOTAL** | 48 | 73 | +25 endpoints |

### 20.2 Tabla: Entidades Actuales vs Nuevas

| Entidad | Estado | Cambios |
|---------|--------|---------|
| Flock | Existente | +galpon_id FK |
| SensorReading | Existente | +galpon_id, +sensor_id, +gateway_id |
| MortalityRecord | Existente | +galpon_id |
| WeightRecord | Existente | +galpon_id |
| ConsumptionRecord | Existente | +galpon_id |
| Extractor/Criadora/Bomba | Existente | +galpon_id (opcional) |
| AlarmRule | Existente | +galpon_id (NULL = global) |
| Alarm | Existente | Sin cambios|
| **Galpon** | **NUEVO** | Tabla principal |
| **Gateway** | **NUEVO** | Tabla conectividad |
| **Sensor** | **NUEVO** | Tabla definición sensores |
| **ActuatorControlState** | **NUEVO** | Estado actual actuadores |
| **AlertasConfig** | **NUEVO** | Configuración notificaciones |
| **TOTAL** | 13 existentes + 5 nuevas | 18 entidades |

### 20.3 Tabla: Base de Datos Actual vs Recomendada

**Actual:**
- Tablas: 18
- Registros (orden magnitud): ~ 100K
- Tamaño: ~ 500 MB
- Usuarios: 1 (local)
- Galpones: 1

**Recomendada (Multi-Galpón):**
- Tablas: 25 (+7 nuevas)
- Registros: ~ 100M (con 50 galpones)
- Tamaño: ~ 50-100 GB
- Usuarios: 10-50 (con roles)
- Galpones: 5-100

**Cambios BD:**
- [ ] Agregar galpones, gateways, sensores
- [ ] Agregar índices (galpon_id, created_at DESC)
- [ ] Particionar sensor_readings por fecha
- [ ] Crear vistas para históricos
- [ ] Replicación + Backup
- [ ] Políticas retención datos

### 20.4 Topics MQTT Recomendados (Estructura Final)

```
avicola/galpon/{id}/lecturas              (Sensor → Server)
avicola/galpon/{id}/lecturas/respuesta    (Server → optional ACK)

avicola/galpon/{id}/alarmas               (Server → Clients)

avicola/galpon/{id}/actuadores/cmd        (Server → Gateway)
avicola/galpon/{id}/actuadores/respuestas (Gateway → Server)

avicola/gateway/{gateway_id}/estado       (Gateway → Server heartbeat)
avicola/gateway/{gateway_id}/config       (Server → Gateway config pull)

avicola/server/alertas                    (Broadcast críticas)
```

### 20.5 Flujo Actual vs Propuesto

**Actual:**
```
Sensor → Raspi (local) → MQTT (local) → Backend (local) → BD → Dashboard (local)
```

**Propuesto:**
```
Sensor → Raspi (remote) 
         ↓
       MQTT (central, TLS)
         ↓
    Backend (HA, 3+ replicas)
         ├→ PostgreSQL (PG Primary/Standby)
         ├→ Redis (cache)
         └→ WebSocket (realtime)
         ↓
    Frontend (React, CDN)
    + Admin (usuarios, galpones, sensores, alarmas)
    + Monitoreo (Prometheus + Grafana)
    + Logs (ELK / Loki)
```

### 20.6 Recomendaciones para Adaptar Raspberry

**Cambios archivo `leer_sensores.py`:**

```python
# ANTES
MQTT_BROKER = "localhost"
MQTT_TOPIC = "avicola/galpon1/lecturas"
GATEWAY_ID = "raspi5-galpon-01"

# DESPUÉS
import os
MQTT_BROKER = os.getenv('MQTT_BROKER', 'localhost')
MQTT_PORT = int(os.getenv('MQTT_PORT', 8883))  # TLS
MQTT_USER = os.getenv('MQTT_USER', 'raspi01')
MQTT_PASS = os.getenv('MQTT_PASS', 'secure_pwd')
MQTT_CA_CERT = os.getenv('MQTT_CA_CERT', '/etc/mqtt/ca.crt')

GALPON_ID = os.getenv('GALPON_ID', '01')
GATEWAY_ID = os.getenv('GATEWAY_ID', 'raspi5-galpon-01')
MQTT_TOPIC = f"avicola/galpon/{GALPON_ID}/lecturas"

# Conexión TLS
client.tls_set(
    ca_certs=MQTT_CA_CERT,
    certfile=None,
    keyfile=None,
    cert_reqs=mqtt.ssl.CERT_REQUIRED,
    tls_version=mqtt.ssl.PROTOCOL_TLSv1_2,
    ciphers=None
)
client.username_pw_set(MQTT_USER, MQTT_PASS)
client.connect(MQTT_BROKER, MQTT_PORT, 60)
```

**Deployment Raspberry:**

```bash
# .env.galpon1
MQTT_BROKER=broker.avimax.example.com
MQTT_PORT=8883
MQTT_USER=raspi01
MQTT_PASS=<secure_pwd_from_vault>
GALPON_ID=01
GATEWAY_ID=raspi5-galpon-01

# Systemd unit (/etc/systemd/system/avimax-sensor.service)
[Unit]
Description=AviMax Sensor Reader
After=network-online.target

[Service]
Type=simple
User=pi
WorkingDirectory=/home/pi/avimax
EnvironmentFile=/home/pi/avimax/.env.galpon1
ExecStart=/usr/bin/python3 /home/pi/avimax/leer_sensores.py
Restart=on-failure
RestartSec=30s

[Install]
WantedBy=multi-user.target

# Enable
systemctl enable avimax-sensor.service
systemctl start avimax-sensor.service
```

### 20.7 Lista Priorizada de Tareas Técnicas

**🔴 CRÍTICAS (Semana 1-2):**
1. Crear tabla `galpones` + FK en flocks
2. Adaptar MqttIngestionService para multi-topic
3. Actualizar SensorReading queries por galpon_id
4. Implementar autenticación JWT
5. Configurar MQTT TLS en broker

**🟠 ALTAS (Semana 3-4):**
6. Crear endpoints /api/galpones CRUD
7. Implementar Dashboard general
8. Crear Sensor + Gateway entidades
9. Agregar rate limiting API
10. WebSocket para alertas tiempo real

**🟡 MEDIAS (Semana 5-6):**
11. Reportes comparativos
12. Dashboard individual galpón (gráficas)
13. Implementar caché Redis
14. Aggregations en frontend
15. Notificaciones email/SMS

**🟢 BAJAS (Semana 7+):**
16. PWA offline mode
17. Particionamiento BD
18. Auto-scaling backend
19. Disaster recovery procedures
20. Advanced BI (machine learning)

### 20.8 Resumen Ejecución

| Aspecto | Actual | Recomendado | Esfuerzo |
|--------|--------|------------|---------|
| **Galpones soportados** | 1 | 50+ | +80 horas |
| **Endpoints** | 48 | 73 | +30 horas |
| **Tablas BD** | 18 | 25 | +20 horas |
| **Frontend** | Nulo | Completo | +120 horas |
| **Seguridad** | Básica | Producción | +40 horas |
| **Monitoreo** | Nulo | Completo | +30 horas |
| **TOTAL** | - | - | ~320 horas / 2 meses |

---

## CONCLUSIÓN

El sistema **AviMax** actualmente funciona como un prototipo local exitoso para **1 galpón**. Su arquitectura es sólida (Spring Boot, PostgreSQL, MQTT) pero **NO está preparada para multi-galpón**.

**Para escalar a servidor central:**

1. ✅ **Base técnica es buena** → Reutilizar Spring Boot, migraciones Flyway
2. ⚠️ **Requiere refactoring** → Agregar capas de multi-tenancy (galpon_id)
3. ⚠️ **Dashboard NO existe** → Crear desde cero (React recomendado)
4. ⚠️ **Seguridad pendiente** → JWT, TLS, rate limiting
5. ⚠️ **Deployment no automatizado** → Setup CI/CD + Docker

**Cronograma realista:** 2-3 meses con equipo dedicado (1-2 desarrolladores senior).

**Costo estimado:** $5-10K USD (infraestructura + desarrollo).

---

**FIN DEL ANÁLISIS**

---

## APÉNDICES

### A.1 - Referencias de Tecnologías

- **Spring Boot 3.4**: https://spring.io/projects/spring-boot
- **PostgreSQL 15**: https://www.postgresql.org/docs/15/
- **EMQX 5.0**: https://www.emqx.io/
- **Paho MQTT**: https://eclipse.dev/paho/
- **Flyway**: https://flywaydb.org/
- **React 18**: https://react.dev/
- **Tailwind CSS**: https://tailwindcss.com/

### A.2 - Recursos Adicionales

- Código fuente del backend: `/home/leo/AviMaxBack/backend-java/`
- Scripts Raspberry: `/home/leo/AviMaxBack/programas/`
- Migraciones BD: `/home/leo/AviMaxBack/backend-java/src/main/resources/db/migration/`
- Documentación existente:
  - `ALARMS_BACKEND_MODULE.md`
  - `FRONTEND_HISTORICOS_CONSUMO.md`
  - `GESTION_PARVADAS_PROCESS.md`

### A.3 - Contacto / Soporte

Para consultas técnicas o clarificaciones sobre este análisis, revisar:
- Logs del backend: `/home/leo/AviMaxBack/avimax.log`
- Código fuente Java: `src/main/java/com/avimax/backend/`
- Configuración: `src/main/resources/application.yml`
