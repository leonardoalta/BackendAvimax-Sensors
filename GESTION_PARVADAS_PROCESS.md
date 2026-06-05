# Gestión de Parvadas - Proceso Ejecutado

**Fecha:** 5 de junio de 2026  
**Hora de Ejecución:** 09:01:22 UTC  
**Usuario:** Backend Testing

---

## Resumen del Proceso

Se ejecutó un ciclo completo de **rotación de parvadas**:
1. ✅ Cerrar parvada activa anterior
2. ✅ Crear nueva parvada activa
3. ✅ Verificar transición correcta

---

## Paso 1: Obtener Parvada Activa Anterior

**Comando:**
```bash
curl -s http://localhost:8080/api/flocks/active | python3 -m json.tool
```

**Respuesta:**
```json
{
    "id": 2,
    "name": "Parvada Prueba 2",
    "totalBirds": 10000,
    "maleCount": 5000,
    "femaleCount": 5000,
    "flockDate": "2026-06-05",
    "birdLot": "LOTE-20260605-001",
    "notes": null,
    "status": "ACTIVE",
    "startedAt": "2026-06-05T08:54:47.735621Z",
    "endedAt": null
}
```

| Campo | Valor |
|-------|-------|
| **ID** | 2 |
| **Nombre** | Parvada Prueba 2 |
| **Total de Aves** | 10,000 |
| **Estado** | ACTIVE |
| **Inicio** | 2026-06-05 08:54:47 |

---

## Paso 2: Cerrar Parvada Anterior (ID: 2)

**Comando:**
```bash
curl -X POST http://localhost:8080/api/flocks/2/close | python3 -m json.tool
```

**Respuesta:**
```json
{
    "id": 2,
    "name": "Parvada Prueba 2",
    "totalBirds": 10000,
    "maleCount": 5000,
    "femaleCount": 5000,
    "flockDate": "2026-06-05",
    "birdLot": "LOTE-20260605-001",
    "notes": null,
    "status": "CLOSED",
    "startedAt": "2026-06-05T08:54:47.735621Z",
    "endedAt": "2026-06-05T09:01:13.973932Z"
}
```

| Campo | Antes | Después |
|-------|-------|---------|
| **Status** | ACTIVE | **CLOSED** ✓ |
| **Ended At** | null | 2026-06-05 09:01:13 |
| **Duración** | ~6.5 minutos | Cerrada |

---

## Paso 3: Crear Nueva Parvada Activa

**Comando:**
```bash
curl -X POST http://localhost:8080/api/flocks \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Parvada Nueva - 05-06-2026",
    "totalBirds": 8000,
    "maleCount": 4000,
    "femaleCount": 4000,
    "flockDate": "2026-06-05",
    "birdLot": "LOTE-NEW-20260605-001"
  }'
```

**Respuesta:**
```json
{
    "id": 3,
    "name": "Parvada Nueva - 05-06-2026",
    "totalBirds": 8000,
    "maleCount": 4000,
    "femaleCount": 4000,
    "flockDate": "2026-06-05",
    "birdLot": "LOTE-NEW-20260605-001",
    "notes": null,
    "status": "ACTIVE",
    "startedAt": "2026-06-05T03:01:22.547853065-06:00",
    "endedAt": null
}
```

| Campo | Valor |
|-------|-------|
| **ID** | 3 |
| **Nombre** | Parvada Nueva - 05-06-2026 |
| **Total de Aves** | 8,000 |
| **Estado** | ACTIVE ✓ |
| **Inicio** | 2026-06-05 09:01:22 |

---

## Paso 4: Verificación - Estado de Todas las Parvadas

**Comando:**
```bash
curl -s http://localhost:8080/api/flocks | python3 -m json.tool
```

**Respuesta:**
```json
[
    {
        "id": 3,
        "name": "Parvada Nueva - 05-06-2026",
        "status": "ACTIVE",
        "startedAt": "2026-06-05T09:01:22.547853Z",
        "endedAt": null
    },
    {
        "id": 2,
        "name": "Parvada Prueba 2",
        "status": "CLOSED",
        "startedAt": "2026-06-05T08:54:47.735621Z",
        "endedAt": "2026-06-05T09:01:13.973932Z"
    },
    {
        "id": 1,
        "name": "Lote Avícola 01",
        "status": "CLOSED",
        "startedAt": "2026-05-13T06:10:01.357868Z",
        "endedAt": "2026-06-05T08:54:24.859212Z"
    }
]
```

### Estado del Sistema:
- ✅ **Parvada ACTIVE:** ID 3 (Nueva)
- ✅ **Parvada CLOSED:** ID 2 (Anterior)
- ✅ **Parvada CLOSED:** ID 1 (Histórica)

---

## Resultados

### ✅ Verificaciones Completadas

| Verificación | Resultado | Detalles |
|--------------|-----------|----------|
| **Una sola parvada ACTIVE** | ✅ PASS | Solo ID 3 tiene status ACTIVE |
| **Parvada anterior cerrada** | ✅ PASS | ID 2 cambió a CLOSED con endedAt |
| **Nueva parvada creada** | ✅ PASS | ID 3 con status ACTIVE recién creada |
| **Históricos preservados** | ✅ PASS | Parvada ID 1 mantiene sus datos |
| **Transición sin errores** | ✅ PASS | Todos los endpoints respondieron 200 OK |

---

## Impacto en Lecturas MQTT

Desde este momento:

### 📊 Antes (Parvada ID 2)
```
Lecturas MQTT → SensorReadingService.saveIfActiveFlock()
         ↓
    Asociadas a flock_id = 2
         ↓
    Persistidas en BD con timestamp anterior a 09:01:13
```

### 📊 Después (Parvada ID 3)
```
Lecturas MQTT → SensorReadingService.saveIfActiveFlock()
         ↓
    Asociadas a flock_id = 3
         ↓
    Persistidas en BD con timestamp a partir de 09:01:22
```

---

## Consultas de Históricos

### Obtener lecturas de la parvada actual (ID 3)
```bash
curl "http://localhost:8080/api/readings"
```

### Obtener lecturas de la parvada anterior (ID 2)
```bash
curl "http://localhost:8080/api/readings?start=2026-06-05&end=2026-06-05"
```

### Obtener por variable específica (temperatura)
```bash
curl "http://localhost:8080/api/readings?variable=temperature&start=2026-06-05&end=2026-06-05"
```

---

## Validaciones Automáticas

El sistema implementa las siguientes validaciones:

### ✓ Control de Validación en `FlockService.createActiveFlock()`
```java
// Solo permite crear si NO existe una parvada ACTIVE
if (flockRepository.existsByStatus(FlockStatus.ACTIVE)) {
    throw ResponseStatusException(CONFLICT, 
        "Ya existe una parvada activa. Debes cerrarla antes de crear otra.");
}

// Valida que totalBirds = maleCount + femaleCount
if (!request.totalBirds().equals(request.maleCount() + request.femaleCount())) {
    throw ResponseStatusException(BAD_REQUEST, 
        "La cantidad total debe ser igual a machos + hembras");
}
```

### ✓ Persistencia Automática en `SensorReadingService.saveIfActiveFlock()`
```java
Optional<Flock> activeFlock = flockRepository.findByStatus(FlockStatus.ACTIVE);
if (activeFlock.isPresent()) {
    reading.setFlock(activeFlock.get());  // ← Asocia automáticamente
    sensorReadingRepository.save(reading);
} else {
    logger.warn("Lectura recibida pero no existe una parvada activa. Se omite.");
}
```

---

## Estado de Sensores

### Verificar Conexión MQTT
```bash
curl -s http://localhost:8080/api/status/mqtt | python3 -m json.tool
```

### Verificar Salud del Sistema
```bash
curl -s http://localhost:8080/api/status/health | python3 -m json.tool
```

---

## Conclusión

✅ **Proceso completado exitosamente**

- Transición de parvadas: Fluida y sin errores
- Datos históricos: Preservados en sus respectivas parvadas
- Nuevas lecturas: Asociadas automáticamente a parvada ID 3
- Sistema: Listo para continuar recopilando datos

**Próximos pasos:**
1. Verificar que los sensores continúan enviando datos MQTT
2. Monitorear `/api/status/mqtt` para validar conectividad
3. Consultar `/api/readings` para verificar persistencia
