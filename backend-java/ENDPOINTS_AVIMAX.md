# Endpoints disponibles - AviMax

Este documento resume los endpoints principales del backend y la estructura de lo que reciben y devuelven.

## Base URL

`http://localhost:8080`

---

## 1) Dashboard principal

### GET `/api/dashboard/principal`

**Para qué sirve**
- Devuelve el resumen principal para el dashboard.

**Recibe**
- Nada.

**Devuelve**
```json
{
  "data": {
    "galpon_id": 1,
    "parvada": {
      "parvada_id": 1,
      "fecha_ingreso": "2026-05-01",
      "edad_dias": 9,
      "aves_vivas": 95
    },
    "peso_actual": {
      "fecha_registro": "2026-05-15",
      "peso_promedio_kg": 0.365
    },
    "telemetria_actual": {
      "event_time": "2026-05-10T14:37:05.357676Z",
      "temperatura_c": 28.5,
      "humedad_relativa": 61.2,
      "nh3_ppm": 4.1
    },
    "telemetria_min_max_dia": {
      "temperatura_c": {
        "min": 27.8,
        "max": 30.1
      },
      "humedad_relativa": {
        "min": 58.0,
        "max": 64.2
      },
      "nh3_ppm": {
        "min": 2.0,
        "max": 6.4
      }
    }
  },
  "meta": {
    "generated_at": "2026-05-10T13:43:52.198049486-06:00",
    "status": "ok"
  }
}
```

---

## 2) Parvadas

### POST `/api/flocks`

**Para qué sirve**
- Crea una nueva parvada activa.

**Recibe**
```json
{
  "name": "Parvada 1",
  "totalBirds": 100,
  "maleCount": 50,
  "femaleCount": 50,
  "flockDate": "2026-05-01",
  "birdLot": "Lote A",
  "notes": "Parvada de prueba"
}
```

**Devuelve**
- La parvada creada.

### GET `/api/flocks`

**Para qué sirve**
- Lista todas las parvadas.

**Recibe**
- Nada.

**Devuelve**
- Lista de parvadas.

### GET `/api/flocks/active`

**Para qué sirve**
- Obtiene la parvada activa.

**Recibe**
- Nada.

**Devuelve**
- La parvada activa.
- Si no existe, responde 204 No Content.

### POST `/api/flocks/{id}/close`

**Para qué sirve**
- Cierra una parvada por id.

**Recibe**
- `id` en la ruta.

**Devuelve**
- La parvada cerrada.

---

## 3) Lecturas de sensores

> Nota: estas lecturas se guardan por suscripción al broker MQTT (`app.mqtt.topic`). No existe endpoint HTTP para insertar lecturas manualmente.

### GET `/api/readings/latest`

**Para qué sirve**
- Devuelve la última lectura de sensores.

**Recibe**
- Nada.

**Devuelve**
```json
{
  "id": 1,
  "flockId": 1,
  "gatewayId": "GW-01",
  "sourceTopic": "sensors/house1",
  "recordedAt": "2026-05-10T14:37:05.357676Z",
  "temperatureC": 28.5,
  "humidityPercent": 61.2,
  "nh3Ppm": 4.1
}
```

### GET `/api/readings/recent`

**Para qué sirve**
- Lista las lecturas recientes.

**Recibe**
- Nada.

**Devuelve**
- Lista de lecturas.

---

## 4) Control automático

### POST `/api/control/evaluate/latest`

**Para qué sirve**
- Evalúa la última lectura y genera comandos automáticos para los actuadores.

**Recibe**
- Nada.

**Devuelve**
```json
{
  "generated": 3,
  "pending": 3,
  "signals": []
}
```

### GET `/api/control/commands/pending`

**Para qué sirve**
- Lista comandos pendientes de enviar.

**Recibe**
- Nada.

**Devuelve**
```json
[
  {
    "id": 1,
    "actuatorType": "EXTRACTOR",
    "actuatorId": 1,
    "actuatorName": "Extractor 1",
    "command": "ON",
    "workDurationSeconds": 300,
    "reason": "Temperatura alta",
    "createdAt": "2026-05-10T14:00:00Z"
  }
]
```

### POST `/api/control/commands/{commandId}/dispatch`

**Para qué sirve**
- Marca un comando como despachado.

**Recibe**
- `commandId` en la ruta.

**Devuelve**
- El comando actualizado.

---

## 5) Extractores

### POST `/api/extractors`

**Para qué sirve**
- Crea un extractor.

**Recibe**
```json
{
  "name": "Extractor 1"
}
```

**Devuelve**
- El extractor creado.

### GET `/api/extractors`

**Para qué sirve**
- Lista extractores con su programación.

**Recibe**
- Nada.

**Devuelve**
- Lista de extractores.

### PUT `/api/extractors/{extractorId}/programming`

**Para qué sirve**
- Configura la programación de un extractor.

**Recibe**
```json
{
  "temperatureOn": 28.0,
  "temperatureOff": 25.0
}
```

**Devuelve**
- El extractor con la programación actualizada.

### GET `/api/extractors/{extractorId}/history`

**Para qué sirve**
- Muestra el historial de programación de un extractor.

**Recibe**
- `extractorId`
- `limit` opcional

**Devuelve**
- Lista de historial con nombre, tipo, temperaturas y fecha.

### GET `/api/extractors/history`

**Para qué sirve**
- Muestra el historial global de extractores.

**Recibe**
- `limit` opcional.

**Devuelve**
- Lista de historial.

---

## 6) Criadoras

### POST `/api/criadoras`

**Para qué sirve**
- Crea una criadora.

**Recibe**
```json
{
  "name": "Criadora 1"
}
```

**Devuelve**
- La criadora creada.

### GET `/api/criadoras`

**Para qué sirve**
- Lista criadoras con programación.

**Recibe**
- Nada.

**Devuelve**
- Lista de criadoras.

### PUT `/api/criadoras/{criadoraId}/programming`

**Para qué sirve**
- Configura la programación de una criadora.

**Recibe**
```json
{
  "temperatureOn": 33.0,
  "temperatureOff": 30.0
}
```

**Devuelve**
- La criadora actualizada.

### GET `/api/criadoras/{criadoraId}/history`

**Para qué sirve**
- Muestra el historial de programación de una criadora.

**Recibe**
- `criadoraId`
- `limit` opcional

**Devuelve**
- Lista de historial.

### GET `/api/criadoras/history`

**Para qué sirve**
- Muestra el historial global de criadoras.

**Recibe**
- `limit` opcional.

**Devuelve**
- Lista de historial.

---

## 7) Bombas

### POST `/api/bombas`

**Para qué sirve**
- Crea una bomba.

**Recibe**
```json
{
  "name": "Bomba 1"
}
```

**Devuelve**
- La bomba creada.

### GET `/api/bombas`

**Para qué sirve**
- Lista bombas con programación.

**Recibe**
- Nada.

**Devuelve**
- Lista de bombas.

### PUT `/api/bombas/{bombaId}/programming`

**Para qué sirve**
- Configura la programación de una bomba.

**Recibe**
```json
{
  "temperatureOn": 26.0,
  "temperatureOff": 24.0,
  "workDurationSeconds": 120
}
```

**Devuelve**
- La bomba actualizada.

### GET `/api/bombas/{bombaId}/history`

**Para qué sirve**
- Muestra el historial de programación de una bomba.

**Recibe**
- `bombaId`
- `limit` opcional

**Devuelve**
- Lista de historial.

### GET `/api/bombas/history`

**Para qué sirve**
- Muestra el historial global de bombas.

**Recibe**
- `limit` opcional.

**Devuelve**
- Lista de historial.

---

## 8) Mortalidad

### POST `/api/mortalidad`

**Para qué sirve**
- Crea un registro de mortalidad asociado a la parvada activa.

**Recibe**
```json
{
  "maleCount": 2,
  "femaleCount": 3,
  "observations": "Enfermedad respiratoria detectada"
}
```

**Devuelve**
```json
{
  "id": 1,
  "recordDate": "2026-05-10",
  "ageDays": 9,
  "maleCount": 2,
  "femaleCount": 3,
  "totalCount": 5,
  "observations": "Enfermedad respiratoria detectada",
  "createdAt": "2026-05-10T14:10:08.912002Z"
}
```

### GET `/api/mortalidad`

**Para qué sirve**
- Lista registros de mortalidad.

**Recibe**
- `from` opcional
- `to` opcional

**Devuelve**
- Lista de registros de mortalidad.

---

## 9) Peso

### POST `/api/peso`

**Para qué sirve**
- Crea un registro de peso de la parvada activa.

**Recibe**
```json
{
  "sampledBirdsCount": 20,
  "averageWeight": 350.5,
  "age": 10,
  "recordDate": "2026-05-10",
  "gender": "MALE",
  "location": "PANEL"
}
```

**Devuelve**
```json
{
  "id": 1,
  "flockId": 1,
  "sampledBirdsCount": 20,
  "averageWeight": 350.5,
  "age": 10,
  "recordDate": "2026-05-10",
  "gender": "MALE",
  "location": "PANEL",
  "createdAt": "2026-05-10T08:28:03.391057Z"
}
```

### GET `/api/peso`

**Para qué sirve**
- Lista todos los registros de peso.

**Recibe**
- Nada.

**Devuelve**
- Lista de registros.

### GET `/api/peso/flock/{flockId}`

**Para qué sirve**
- Lista registros de peso de una parvada.

**Recibe**
- `flockId`

**Devuelve**
- Lista de registros.

### GET `/api/peso/flock/{flockId}/gender/{gender}`

**Para qué sirve**
- Lista registros por parvada y sexo.

**Recibe**
- `flockId`
- `gender` = `male` o `female`

**Devuelve**
- Lista filtrada.

### GET `/api/peso/flock/{flockId}/latest/gender/{gender}`

**Para qué sirve**
- Obtiene el último registro de peso por parvada y sexo.

**Recibe**
- `flockId`
- `gender`

**Devuelve**
- Un solo registro.

### GET `/api/peso/latest/male`

**Para qué sirve**
- Obtiene el último peso de machos de la parvada activa.

**Recibe**
- Nada.

**Devuelve**
- Un solo registro.

### GET `/api/peso/latest/female`

**Para qué sirve**
- Obtiene el último peso de hembras de la parvada activa.

**Recibe**
- Nada.

**Devuelve**
- Un solo registro.

### GET `/api/peso/flock/{flockId}/range`

**Para qué sirve**
- Lista pesos por rango de fechas.

**Recibe**
- `flockId`
- `from`
- `to`

**Devuelve**
- Lista de registros en el rango.

---

## 10) Consumo

### POST `/api/consumo`

**Para qué sirve**
- Crea un registro de consumo total de alimento.
- Calcula automáticamente el consumo por ave.

**Recibe**
```json
{
  "age": 10,
  "recordDate": "2026-05-10",
  "totalConsumptionKg": 250.0
}
```

**Devuelve**
```json
{
  "id": 1,
  "flockId": 1,
  "age": 10,
  "recordDate": "2026-05-10",
  "totalConsumptionKg": 250.0,
  "birdsCountUsed": 95,
  "consumptionPerBirdKg": 2.6315789473684212,
  "createdAt": "2026-05-10T08:58:33.677327486-06:00"
}
```

### GET `/api/consumo`

**Para qué sirve**
- Lista todos los registros de consumo.

**Recibe**
- Nada.

**Devuelve**
- Lista de registros.

### GET `/api/consumo/flock/{flockId}`

**Para qué sirve**
- Lista registros de consumo por parvada.

**Recibe**
- `flockId`

**Devuelve**
- Lista de registros.

---

## Resumen rápido para el dashboard

Los endpoints más útiles para el dashboard principal son:

- `GET /api/dashboard/principal`
- `GET /api/flocks/active`
- `GET /api/readings/latest`
- `GET /api/peso/latest/male`
- `GET /api/peso/latest/female`
- `GET /api/mortalidad`
- `GET /api/consumo`

---

## Notas

- Los módulos de mortalidad, peso y consumo trabajan contra la parvada activa.
- Si no hay parvada activa, los endpoints que dependen de ella responderán con error.
- Los datos de telemetría pueden devolver valores nulos si aún no hay lecturas.
