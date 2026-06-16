# Auditoría Completa: Sistema de Actuadores y MQTT
## AviMax — backend-java + avimax-central-backend

> Fecha: 2026-06-13  
> Rol: Arquitecto backend senior (Spring Boot 3, IoT, MQTT, PostgreSQL distribuido)  
> Constraint fijo: NO tocar archivos fuera de `avimax-central-backend/` excepto donde se indica explícitamente.

---

## TAREA 1 — Inventario de archivos relevantes

### backend-java (Raspberry Pi — edge)

| Archivo | Rol |
|---------|-----|
| `config/DataInitializer.java` | Crea actuadores y flock demo en startup — sin @Profile, siempre corre |
| `entity/Extractor.java` | Entidad local — id, name, enabled, createdAt. **Sin codeName** |
| `entity/Criadora.java` | Entidad local — id, name, enabled, createdAt. **Sin codeName** |
| `entity/Bomba.java` | Entidad local — id, name, enabled, createdAt. **Sin codeName** |
| `entity/LocalMqttOutboxMessage.java` | Outbox local — id, topic, payload, status (PENDING/SENT/FAILED/DEAD), attempts |
| `entity/ProcessedMqttCommand.java` | Idempotencia de comandos — PK = commandId |
| `entity/ProcessedProgrammingConfig.java` | Idempotencia de programming — PK = configId |
| `service/MqttCentralCommandListenerService.java` | Escucha `cmd`, resuelve actuador por **local DB ID**, publica respuesta |
| `service/MqttProgrammingListenerService.java` | Escucha `config/programming`, resuelve por **local DB ID**, publica ACK |
| `service/LocalSyncMqttPublisherService.java` | Publica sync/mortality, sync/weight, sync/consumption, sync/actuator-state |
| `service/LocalMqttOutboxService.java` | Retry outbox cada 10s, batch 50, PENDING→SENT\|FAILED→DEAD (20 intentos) |
| `service/ActuatorControlService.java` | `applyExternalCommand()` — lookup por `findById(actuatorId)` local |
| `resources/application.yml` | `app.galpon-id=${GALPON_ID:1}`, flyway disabled, ddl-auto=update |

### avimax-central-backend (servidor central)

| Archivo | Rol |
|---------|-----|
| `entity/Extractor.java` | Tiene `codeName` (EXT-01, EXT-02…) — **no se usa en MQTT** |
| `entity/Criadora.java` | Tiene `codeName` (CRI-01, CRI-02…) — **no se usa en MQTT** |
| `entity/Bomba.java` | Tiene `codeName` (BOM-01…) — **no se usa en MQTT** |
| `entity/ActuatorControlCommand.java` | Comando: actuatorId = central DB ID, sin codeName |
| `entity/ActuatorProgrammingSync.java` | Sync programming: actuatorId = central DB ID, sin codeName |
| `entity/SyncEvent.java` | Registra eventos recibidos de Raspberry — payload con bug `insertable=false` |
| `service/MqttCommandPublisherService.java` | Publica `{actuatorId: centralId}` — sin codeName en payload |
| `service/MqttProgrammingPublisherService.java` | Publica `{actuatorId: centralId}` — sin codeName en payload |
| `service/MqttIngestionService.java` | Listener principal: lecturas, respuestas, heartbeat, sync/ack |
| `service/MqttLocalSyncListenerService.java` | Listener `sync/#` — recibe localId, busca por él en central DB (bug doble) |
| `service/MqttProgrammingAckListenerService.java` | Listener ACK programming — completo ✅ |
| `service/ActuatorControlService.java` | `dispatch()` → publish con central ID; `onActuatorResponse()` matchea por commandId |
| `service/ActuatorProgrammingSyncService.java` | `createAndDispatch()` — usa central actuatorId |
| `service/ProvisioningService.java` | Crea extractores/criadoras/bombas con codeName en central DB |
| `resources/db/migration/V3__actuators.sql` | Schema: extractors(id, galpon_id, name, code_name, estado) |
| `resources/db/migration/V5__seed_demo_data.sql` | Seed: Extractor id=1 codeName='EXT-01' para galpon 1 |

---

## TAREA 2 — Diagnóstico de qué existe (20 preguntas)

| # | Pregunta | Estado | Evidencia en código |
|---|----------|--------|---------------------|
| 1 | ¿Existe outbox local en backend-java? | ✅ COMPLETO | `LocalMqttOutboxService` + `local_mqtt_outbox_messages` — retry 10s, batch 50, 20 intentos |
| 2 | ¿El outbox tiene estados PENDING/FAILED/DEAD? | ✅ COMPLETO | `LocalMqttOutboxMessage.status` → PENDING→SENT \| PENDING→FAILED→DEAD |
| 3 | ¿Existe ACK de comandos (EXECUTED)? | ✅ COMPLETO | `MqttCentralCommandListenerService` publica a `actuadores/respuestas` con `{commandId, status=EXECUTED}` |
| 4 | ¿Existe ACK de programming (APPLIED)? | ✅ COMPLETO | `MqttProgrammingListenerService` publica a `config/programming/ack` con `{configId, status=APPLIED}` |
| 5 | ¿Existe idempotencia para comandos? | ✅ COMPLETO | `ProcessedMqttCommand` — PK = commandId, rechaza duplicados |
| 6 | ¿Existe idempotencia para programming? | ✅ COMPLETO | `ProcessedProgrammingConfig` — PK = configId |
| 7 | ¿Existe SyncEvent en central? | ⚠️ PARCIAL | `SyncEvent` existe y se guarda, pero `payload` tiene `@Column(insertable=false)` → siempre NULL |
| 8 | ¿Central recibe sync de Raspberry? | ✅ COMPLETO | `MqttLocalSyncListenerService` suscrito a `sync/#` procesa 4 tipos de eventos |
| 9 | ¿Central recibe respuesta de comandos? | ✅ COMPLETO | `MqttIngestionService.processActuatorResponse()` → `actuatorControlService.onActuatorResponse()` |
| 10 | ¿Central recibe ACK de programming? | ✅ COMPLETO | `MqttProgrammingAckListenerService` — busca por configId, actualiza a APPLIED\|ERROR |
| 11 | ¿Existe `codeName` en entidades centrales? | ✅ EXISTE | `Extractor.codeName`, `Criadora.codeName`, `Bomba.codeName` — e.g. "EXT-01" |
| 12 | ¿`codeName` se incluye en payloads MQTT? | ❌ NO | `MqttCommandPublisherService` envía solo `actuatorId` (central DB ID) — sin codeName |
| 13 | ¿`codeName` existe en entidades locales? | ❌ NO | `Extractor`, `Criadora`, `Bomba` en backend-java no tienen campo codeName |
| 14 | ¿Existe mecanismo bootstrap/snapshot? | ❌ NO | No hay topic `bootstrap/request` ni `bootstrap/snapshot`, no hay listener ni publisher |
| 15 | ¿Raspberry solicita catálogo inicial a central? | ❌ NO | `DataInitializer` crea sus propios actuadores sin consultar a central |
| 16 | ¿Existe tabla ActuatorMapping? | ❌ NO | Ninguna migración Flyway en ninguno de los dos backends la crea |
| 17 | ¿`DataInitializer` tiene @Profile o condición? | ❌ NO | Es `@Component CommandLineRunner` sin @Profile — corre SIEMPRE |
| 18 | ¿Central tiene outbox para MQTT? | ❌ NO | `MqttCommandPublisherService.publishCommand()` lanza excepción en fallo, sin reintentos |
| 19 | ¿Existe retry de PENDING commands en central? | ❌ NO | `app.retry.*` en application.yml existe pero no hay `@Scheduled` que lo use |
| 20 | ¿Raspberry actualiza actuadores vía MQTT desde central? | ❌ NO | No hay listener en backend-java que reciba actualizaciones del catálogo |

---

## TAREA 3 — Evaluación del problema de IDs (10 preguntas)

### El escenario concreto

**Central** asigna autoincrement IDs globales a todos los actuadores de todos los galpones:
```
V5 seed: Extractor id=1  codeName='EXT-01'  galpon_id=1
API provisioning galpon 2: Extractor id=2  codeName='EXT-01'  galpon_id=2
                           Extractor id=3  codeName='EXT-02'  galpon_id=2
                           ...
                           Extractor id=13 codeName='EXT-12' galpon_id=2
```

**Local** (cada Raspberry) asigna autoincrement IDs locales e independientes:
```
DataInitializer galpon 1: Ventilador 1 id=1, Ventilador 2 id=2, ... Ventilador 12 id=12
DataInitializer galpon 2: Ventilador 1 id=1, Ventilador 2 id=2, ... Ventilador 12 id=12
```

| # | Pregunta | Respuesta | Evidencia |
|---|----------|-----------|-----------|
| 1 | ¿Los IDs de central y local son independientes? | ✅ SÍ, SIEMPRE | Ambos usan `IDENTITY`/autoincrement sin coordinación |
| 2 | ¿El payload MQTT incluye `codeName`? | ❌ NO | `payload.put("actuatorId", command.getActuatorId())` — solo el ID central |
| 3 | ¿Local resuelve el actuador por codeName? | ❌ NO | `actuatorControlService.applyExternalCommand(type, actuatorId, ...)` → `extractorRepository.findById(actuatorId)` |
| 4 | ¿El ID central puede coincidir con el local? | ⚠️ POR COINCIDENCIA | Galpon 1 con 1 extractor en central → id=1 central = id=1 local. Correcto por azar. |
| 5 | ¿Para galpon 2, el ID central coincide con el local? | ❌ NO | Central EXT-01 de galpon 2 = id=2, local Ventilador 1 de galpon 2 = id=1. OFF-BY-ONE garantizado. |
| 6 | ¿Qué actuador se controla si central envía `actuatorId=2` para galpon 2? | INCORRECTO | Local aplica a `id=2` = "Ventilador 2" pero la intención era "EXT-01" = Ventilador 1 |
| 7 | ¿`MqttLocalSyncListenerService` tiene el mismo bug en central? | ✅ DOBLE BUG | Recibe `localActuatorId` de Raspberry, hace `extractorRepository.findById(localActuatorId)` en DB central |
| 8 | ¿Hay algún fallback o validación que detecte el error? | ❌ NO | Si `findById()` devuelve empty, el error se silencia con log.warn |
| 9 | ¿El bug fue detectado en producción? | LATENTE | Galpon 1 funciona "por coincidencia" (1 extractor central, IDs sincronizan accidentalmente) |
| 10 | ¿Escalar a galpon 2 expone el bug garantizadamente? | ✅ SÍ | Con el primer provisioning de galpon 2, los IDs centrales empiezan en ≥2, locales en 1 |

### Riesgo concreto (severidad CRÍTICA)

```
Central envía:  { actuatorType: EXTRACTOR, actuatorId: 2, action: ON }
                   ↑ central DB id=2 = "EXT-01 del galpon 2"

Local recibe:   actuatorId=2 → extractorRepository.findById(2) → "Ventilador 2" (el 2do)
                   ↑ local DB id=2 = "Ventilador 2" del galpon 2

Resultado:      Se enciende el VENTILADOR 2 cuando la intención era VENTILADOR 1 (EXT-01)
                Responde "EXECUTED" — central cree que todo está bien
                El error es SILENCIOSO y CONFIRMADO como exitoso
```

---

## TAREA 4 — Clasificación del sistema actual

### **OPCIÓN B — Parcialmente implementado**

El sistema tiene infraestructura sólida (outbox, idempotencia, sync, ACKs) pero falta la pieza crítica que conecta correctamente la identidad de actuadores entre central y edge.

**Lo que FUNCIONA bien:**

| Componente | Estado |
|------------|--------|
| Outbox local (backend-java) | ✅ Producción-ready |
| Idempotencia comandos + programming | ✅ Producción-ready |
| ACK de comandos (EXECUTED) | ✅ Producción-ready |
| ACK de programming (APPLIED) | ✅ Producción-ready |
| Sync publisher (mortality/weight/consumption/actuator-state) | ✅ Producción-ready |
| Listener sync en central | ✅ Completo (bug en payload es menor) |
| Listener ACK programming en central | ✅ Producción-ready |
| `codeName` en entidades centrales | ✅ Existe |

**Lo que FALTA o está ROTO:**

| Gap | Impacto |
|-----|---------|
| `codeName` ausente en payload MQTT | 🔴 CRÍTICO — actuador incorrecto |
| `codeName` ausente en entidades locales | 🔴 CRÍTICO — no puede resolver por código |
| Sin bootstrap/snapshot | 🟠 ALTO — sin sincronía inicial de catálogo |
| `DataInitializer` sin @Profile | 🟠 ALTO — genera actuadores fantasma en producción |
| `MqttLocalSyncListenerService` busca local ID en central DB | 🟡 MEDIO — sync de estado queda huérfana |
| Sin outbox central | 🟡 MEDIO — fallo MQTT en central es silencioso |
| Sin retry de PENDING commands en central | 🟡 MEDIO — comandos quedan stuck |
| `SyncEvent.payload` con `insertable=false` | 🟡 MEDIO — payload siempre NULL |

---

## TAREA 5 y 6 — Arquitectura y solución técnica propuesta

### Principio de diseño

El `codeName` ya existe en central. Es el identificador semántico estable (`EXT-01`, `CRI-01`, `BOM-01`). La solución es **propagarlo al edge** mediante los payloads MQTT y usarlo como clave de resolución primaria.

**El local nunca debe depender de los IDs del central. Debe depender de codeName.**

### Modelo de datos propuesto

#### Cambios en `backend-java` (edge)

Agregar `codeName` a las tres entidades de actuadores:

```sql
-- Nueva migración: V2__add_codename_to_actuators.sql
ALTER TABLE extractors  ADD COLUMN IF NOT EXISTS code_name VARCHAR(20);
ALTER TABLE criadoras   ADD COLUMN IF NOT EXISTS code_name VARCHAR(20);
ALTER TABLE bombas      ADD COLUMN IF NOT EXISTS code_name VARCHAR(20);

CREATE UNIQUE INDEX IF NOT EXISTS idx_extractors_code_name ON extractors(code_name);
CREATE UNIQUE INDEX IF NOT EXISTS idx_criadoras_code_name  ON criadoras(code_name);
CREATE UNIQUE INDEX IF NOT EXISTS idx_bombas_code_name     ON bombas(code_name);
```

#### Cambios en `DataInitializer` (backend-java)

Asignar `codeName` al crear actuadores demo:

```java
// Extractors
for (int i = 1; i <= 12; i++) {
    Extractor e = new Extractor();
    e.setName("Ventilador " + i);
    e.setCodeName("EXT-" + String.format("%02d", i));  // EXT-01..EXT-12
    extractorRepository.save(e);
}
// Criadoras
for (int i = 1; i <= 5; i++) {
    Criadora c = new Criadora();
    c.setName("Criadora " + i);
    c.setCodeName("CRI-" + String.format("%02d", i));  // CRI-01..CRI-05
    criadoras.save(c);
}
// Bombas
for (int i = 1; i <= 2; i++) {
    Bomba b = new Bomba();
    b.setName("Bomba " + i);
    b.setCodeName("BOM-" + String.format("%02d", i));  // BOM-01..BOM-02
    bombaRepository.save(b);
}
```

### Topics MQTT (sin cambios)

Los topics existentes no cambian:

```
avicola/galpon/{galponId}/actuadores/cmd         ← central → edge
avicola/galpon/{galponId}/actuadores/respuestas  ← edge → central
avicola/galpon/{galponId}/config/programming     ← central → edge
avicola/galpon/{galponId}/config/programming/ack ← edge → central
avicola/galpon/{galponId}/sync/{tipo}            ← edge → central
```

### Payloads MQTT propuestos (cambios mínimos)

#### Comando de actuador (central → edge)
```json
{
  "commandId": 5,
  "galponId": 2,
  "gatewayId": 2,
  "actuatorType": "EXTRACTOR",
  "actuatorId": 2,
  "codeName": "EXT-01",
  "action": "ON",
  "reason": "temperature_alarm",
  "workDurationSeconds": null
}
```
> `codeName` se agrega como campo adicional. `actuatorId` se mantiene para compatibilidad.

#### Sync de programming (central → edge)
```json
{
  "configId": 3,
  "galponId": 2,
  "actuatorType": "EXTRACTOR",
  "actuatorId": 2,
  "codeName": "EXT-01",
  "temperatureOn": 28.0,
  "temperatureOff": 25.0,
  "workDurationSeconds": null
}
```

#### Sync de estado de actuador (edge → central)
```json
{
  "event": "ACTUATOR_STATE_CHANGED",
  "galponId": 1,
  "actuatorType": "EXTRACTOR",
  "actuatorId": 1,
  "codeName": "EXT-01",
  "actuatorName": "Ventilador 1",
  "state": "ON",
  "triggeredBy": "MQTT_COMMAND",
  "timestamp": "2026-06-13T10:30:00Z"
}
```

### Lógica de resolución en edge (backend-java)

```java
// ActuatorControlService.java — resolveExtractor()
private Optional<Extractor> resolveExtractor(Long actuatorId, String codeName) {
    // 1. Resolver por codeName primero (fuente de verdad)
    if (codeName != null && !codeName.isBlank()) {
        Optional<Extractor> byCode = extractorRepository.findByCodeName(codeName);
        if (byCode.isPresent()) return byCode;
        log.warn("[ACTUADOR] codeName={} no encontrado localmente, fallback a ID", codeName);
    }
    // 2. Fallback a ID local (compatibilidad y galpon 1 legacy)
    return extractorRepository.findById(actuatorId);
}
```

### Lógica de resolución en central (`MqttLocalSyncListenerService`)

```java
// El edge ya envía codeName en el sync, usar eso:
private void processActuatorState(JsonNode root, Long galponId) {
    String codeName   = root.path("codeName").asText(null);
    String actuatorType = root.path("actuatorType").asText("EXTRACTOR");
    String state       = root.path("state").asText();
    
    if (codeName != null && !codeName.isBlank()) {
        // Resolver por codeName en central DB
        switch (actuatorType) {
            case "EXTRACTOR" -> extractorRepository.findByCodeNameAndGalponId(codeName, galponId)
                    .ifPresent(e -> { e.setEstado(state); extractorRepository.save(e); });
            case "CRIADORA"  -> criadoraRepository.findByCodeNameAndGalponId(codeName, galponId)
                    .ifPresent(c -> { c.setEstado(state); criadoraRepository.save(c); });
            case "BOMBA"     -> bombaRepository.findByCodeNameAndGalponId(codeName, galponId)
                    .ifPresent(b -> { b.setEstado(state); bombaRepository.save(b); });
        }
    }
    // Crear SyncEvent de auditoría
    syncEventService.record(galponId, "ACTUATOR_STATE_CHANGED", root.toString());
}
```

---

## TAREA 7 — Estrategia incremental (sin romper flujos existentes)

### Fase 0 — Baseline (sin tocar nada)
- Documentar estado actual
- Agregar test con `mosquitto_pub` que demuestre el mismatch
- **No se implementa nada en código**

### Fase 1 — Agregar codeName al schema local (backend-java)

**Archivos a modificar:**
- `backend-java/.../entity/Extractor.java` — agregar campo `codeName`
- `backend-java/.../entity/Criadora.java` — agregar campo `codeName`
- `backend-java/.../entity/Bomba.java` — agregar campo `codeName`
- `backend-java/.../config/DataInitializer.java` — asignar EXT-01..EXT-12, etc.
- `backend-java/.../repository/ExtractorRepository.java` — agregar `findByCodeName(String)`
- `backend-java/.../repository/CriadoraRepository.java` — agregar `findByCodeName(String)`
- `backend-java/.../repository/BombaRepository.java` — agregar `findByCodeName(String)`

> `ddl-auto=update` crea la columna sin Flyway. Sin breaking changes.
> 
> **Constraint**: Estos archivos están en backend-java que tiene restricción de "no tocar". **La Fase 1 requiere autorización explícita del usuario para modificar backend-java.**

### Fase 2 — Central incluye `codeName` en payloads MQTT

**Archivos a modificar (solo avimax-central-backend):**
- `MqttCommandPublisherService.java` — agregar `payload.put("codeName", command.getCodeName())`
- `MqttProgrammingPublisherService.java` — agregar `payload.put("codeName", sync.getCodeName())`
- `ActuatorControlCommand.java` — agregar campo `codeName`
- `ActuatorProgrammingSync.java` — agregar campo `codeName`
- `ActuatorControlService.java` (central) — pasar codeName al crear comando/sync
- Migration: `V10__add_codename_to_commands.sql`

> Backend-java sigue ignorando el campo por ahora (no lo lee aún). Sin breaking changes.

### Fase 3 — Local resuelve por codeName primero

**Archivos a modificar (backend-java — requiere autorización):**
- `ActuatorControlService.java` (local) — `resolveExtractor(actuatorId, codeName)` con fallback
- `MqttCentralCommandListenerService.java` — leer `codeName` del payload, pasarlo a `applyExternalCommand`
- `MqttProgrammingListenerService.java` — leer `codeName` del payload, pasarlo a `applyProgramming`

> Flujos existentes con `codeName=null` (payloads legacy) siguen usando ID como fallback.

### Fase 4 — Local envía `codeName` en sync

**Archivos a modificar (backend-java — requiere autorización):**
- `LocalSyncMqttPublisherService.java` — incluir `codeName` en payload de `publishActuatorStateChanged()`
- `ActuatorControlService.java` (local) — recuperar codeName al construir el evento sync

### Fase 5 — Central corrige resolución por codeName en sync

**Archivos a modificar (solo avimax-central-backend):**
- `MqttLocalSyncListenerService.java` — usar codeName para lookup en central DB en vez de actuatorId
- Agregar `findByCodeNameAndGalponId()` en repositorios centrales

> Corrige el doble bug identificado: `processActuatorState()` deja de buscar local ID en central DB.

### Fase 6 — Bootstrap inicial (opcional, alta prioridad en multi-galpon)

**Topic nuevo:** `avicola/galpon/{id}/bootstrap/request` y `avicola/galpon/{id}/bootstrap/snapshot`

**Archivos a modificar (backend-java — requiere autorización):**
- Nuevo `BootstrapRequestPublisherService.java` — publica petición en `@PostConstruct`
- Nuevo listener de snapshot que recibe catálogo y hace upsert local

**Archivos a modificar (avimax-central-backend):**
- Nuevo `BootstrapSnapshotListenerService.java` — suscrito a `/bootstrap/request`, publica snapshot completo

### Fase 7 — Outbox central + retry de PENDING commands

**Solo avimax-central-backend:**
- `CentralMqttOutboxMessage.java` — nueva entidad
- `CentralMqttOutboxService.java` — `@Scheduled` cada 15s usando `app.retry.*` ya configurado
- `ActuatorControlService.java` — retry de PENDING commands cuando gateway reconecta

---

## TAREA 8 — Pruebas de aceptación

### 8.1 Verificar el bug actual (antes de implementar)

```bash
# Terminal 1: suscribirse a respuestas locales
mosquitto_sub -h localhost -t "avicola/galpon/2/actuadores/respuestas" -v

# Terminal 2: enviar comando con actuatorId=2 (que en central = EXT-01 de galpon2)
mosquitto_pub -h localhost \
  -t "avicola/galpon/2/actuadores/cmd" \
  -m '{"commandId":99,"galponId":2,"actuatorType":"EXTRACTOR","actuatorId":2,"action":"ON"}'

# Verificar en local cuál actuador se activó:
curl http://localhost:8080/api/galpones/1/actuadores | jq '.[] | select(.id==2)'
# Resultado esperado: Ventilador 2 ON (BUG: debería ser Ventilador 1 = EXT-01)
```

### 8.2 Verificar resolución por codeName (después de Fase 1-3)

```bash
# Verificar que local tiene codeName
curl http://localhost:8080/api/galpones/1/actuadores | jq '.[0]'
# Esperado: { "id":1, "name":"Ventilador 1", "codeName":"EXT-01", ... }

# Enviar comando con codeName
mosquitto_pub -h localhost \
  -t "avicola/galpon/2/actuadores/cmd" \
  -m '{"commandId":100,"galponId":2,"actuatorType":"EXTRACTOR","actuatorId":2,"codeName":"EXT-01","action":"ON"}'

# Verificar: ahora debe activarse Ventilador 1 (id=1 local, codeName=EXT-01)
```

### 8.3 Verificar idempotencia

```bash
# Publicar el mismo commandId dos veces
mosquitto_pub -h localhost -t "avicola/galpon/1/actuadores/cmd" \
  -m '{"commandId":101,"galponId":1,"actuatorType":"EXTRACTOR","actuatorId":1,"codeName":"EXT-01","action":"ON"}'
mosquitto_pub -h localhost -t "avicola/galpon/1/actuadores/cmd" \
  -m '{"commandId":101,"galponId":1,"actuatorType":"EXTRACTOR","actuatorId":1,"codeName":"EXT-01","action":"ON"}'

# Verificar en DB local:
psql -U avimax -d avimax -c "SELECT * FROM processed_mqtt_commands WHERE command_id=101;"
# Esperado: 1 fila, status=EXECUTED, no duplicados
```

### 8.4 Verificar outbox local en fallo MQTT

```bash
# Detener broker MQTT
docker stop mosquitto

# Enviar comando de programación
curl -X PUT http://localhost:8080/api/programming/extractors/1 \
  -H 'Content-Type: application/json' \
  -d '{"temperatureOn":29,"temperatureOff":26}'

# Verificar que quedó en outbox
psql -U avimax -d avimax -c "SELECT * FROM local_mqtt_outbox_messages WHERE status='PENDING';"

# Reiniciar broker
docker start mosquitto

# Esperar 10s — verificar retry automático
sleep 12
psql -U avimax -d avimax -c "SELECT status FROM local_mqtt_outbox_messages ORDER BY created_at DESC LIMIT 1;"
# Esperado: status=SENT
```

### 8.5 Verificar sync de estado en central

```bash
# Publicar sync de actuador desde edge simulado
mosquitto_pub -h centralla -t "avicola/galpon/1/sync/actuator-state" \
  -m '{"event":"ACTUATOR_STATE_CHANGED","galponId":1,"actuatorType":"EXTRACTOR","actuatorId":1,"codeName":"EXT-01","state":"ON","triggeredBy":"MQTT_COMMAND"}'

# Verificar en central DB
psql -U avimax -d avimax_central -c \
  "SELECT estado FROM extractors WHERE code_name='EXT-01' AND galpon_id=1;"
# Esperado: estado='ON'
```

### 8.6 SQL de diagnóstico rápido

```sql
-- Ver actuadores locales con/sin codeName
SELECT id, name, code_name, enabled FROM extractors ORDER BY id;

-- Ver últimos comandos procesados
SELECT command_id, actuator_type, actuator_id, action, status, first_processed_at
FROM processed_mqtt_commands ORDER BY first_processed_at DESC LIMIT 10;

-- Ver outbox pendiente
SELECT id, message_type, topic, status, attempts, last_error
FROM local_mqtt_outbox_messages WHERE status IN ('PENDING','FAILED') ORDER BY created_at;

-- Ver sync events en central
SELECT id, galpon_id, event_type, status, created_at, payload
FROM sync_events ORDER BY created_at DESC LIMIT 10;

-- Ver comandos stuck en central (PENDING sin respuesta)
SELECT id, galpon_id, actuator_type, actuator_id, action, status, created_at, dispatched_at
FROM actuator_control_commands WHERE status='PENDING'
AND created_at < now() - interval '5 minutes';
```

---

## Resumen ejecutivo

| Aspecto | Estado |
|---------|--------|
| Infraestructura MQTT | ✅ Sólida — outbox, retry, idempotencia, ACKs, sync |
| Comunicación edge→central | ✅ Funcional |
| Comunicación central→edge | ⚠️ Funciona pero resuelve actuador INCORRECTO |
| Resolución de actuadores | 🔴 BUG CRÍTICO — ID central ≠ ID local en casi todos los escenarios |
| Multi-galpon ready | ❌ NO — el bug se vuelve certero con galpon 2 en adelante |
| Bootstrap inicial | ❌ NO existe |
| Outbox en central | ❌ NO existe |

**Prioridad máxima:** Implementar Fase 2 (central incluye codeName en payload) + Fase 3 (local resuelve por codeName). Son los cambios mínimos para corregir el bug crítico.

**Bloqueador:** La Fase 1 y 3 requieren modificar `backend-java`. Dado el constraint "NO tocar backend-java", **se necesita autorización explícita** para proceder con esas fases.

La Fase 2 (solo `avimax-central-backend`) se puede implementar ya, sin riesgo, como preparación.
