# API de AviMax para Frontend

Documento pensado para facilitar la implementación del frontend.

## Base URL

`http://localhost:8080`

## Convenciones

- Todas las rutas usan JSON.
- Los endpoints de lectura de sensores **no reciben datos por HTTP**. Las lecturas llegan por suscripción al broker MQTT.
- Cuando un endpoint devuelve lista, normalmente está ordenado de más reciente a más antiguo.
- Si no existe información, algunos endpoints pueden devolver `204 No Content` o una lista vacía.

---

# 1) Dashboard principal

## GET `/api/dashboard/principal`

### Qué hace
Devuelve el resumen principal para la pantalla de inicio.

### Recibe
Nada.

### Devuelve
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
      "event_time": "2026-05-10T18:00:00Z",
      "temperatura_c": 27.8,
      "humedad_relativa": 58.5,
      "nh3_ppm": 2.0
    },
    "telemetria_min_max_dia": {
      "temperatura_c": {
        "min": 27.5,
        "max": 30.1
      },
      "humedad_relativa": {
        "min": 55.0,
        "max": 64.2
      },
      "nh3_ppm": {
        "min": 0.0,
        "max": 6.4
      }
    }
  },
  "meta": {
    "generated_at": "2026-05-10T14:37:52.509635425-06:00",
    "status": "ok"
  }
}
```

### Uso en frontend
Ideal para:
- tarjetas resumen
- indicadores en dashboard
- bloques de estado actual
- min/max del día

---

# 2) Parvadas

## POST `/api/flocks`

### Qué hace
Crea una nueva parvada activa.

### Recibe
```json
{
  "name": "TestFlock",
  "totalBirds": 100,
  "maleCount": 50,
  "femaleCount": 50,
  "flockDate": "2026-05-01",
  "birdLot": "LoteA",
  "notes": "prueba"
}
```

### Respuesta
Parvada creada.

## GET `/api/flocks`

### Qué hace
Lista todas las parvadas.

### Respuesta
Lista de parvadas.

## GET `/api/flocks/active`

### Qué hace
Devuelve la parvada activa.

### Respuesta
Una parvada o `204 No Content`.

## POST `/api/flocks/{id}/close`

### Qué hace
Cierra una parvada.

### Recibe
- `id` en la ruta

### Respuesta
La parvada cerrada.

---

# 3) Lecturas de sensores

> Importante: estas lecturas se guardan por suscripción al broker MQTT. El frontend **no debe enviar lecturas por HTTP**.

## GET `/api/readings/latest`

### Qué hace
Devuelve la lectura más reciente.

### Respuesta
```json
{
  "id": 123,
  "flockId": 1,
  "gatewayId": "gw1",
  "sourceTopic": "avicola/galpon1/lecturas",
  "recordedAt": "2026-05-10T18:00:00Z",
  "temperatureC": 27.8,
  "humidityPercent": 58.5,
  "nh3Ppm": 2.0
}
```

## GET `/api/readings/recent`

### Qué hace
Devuelve las lecturas recientes.

### Respuesta
Lista de lecturas.

---

# 4) Control automático

## POST `/api/control/evaluate/latest`

### Qué hace
Evalúa la última lectura y genera comandos para los actuadores.

### Recibe
Nada.

### Respuesta
```json
{
  "generated": 3,
  "pending": 3,
  "signals": []
}
```

## GET `/api/control/commands/pending`

### Qué hace
Lista los comandos pendientes de ejecución.

### Respuesta
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

## POST `/api/control/commands/{commandId}/dispatch`

### Qué hace
Marca un comando como enviado.

### Recibe
- `commandId` en la ruta

### Respuesta
El comando actualizado.

---

# 5) Extractores

## POST `/api/extractors`

### Qué hace
Crea un extractor.

### Recibe
```json
{
  "name": "Extractor 1"
}
```

### Respuesta
Extractor creado.

## GET `/api/extractors`

### Qué hace
Lista extractores con su programación.

### Respuesta
Lista de extractores.

## PUT `/api/extractors/{extractorId}/programming`

### Qué hace
Actualiza la programación de un extractor.

### Recibe
```json
{
  "temperatureOn": 30,
  "temperatureOff": 28
}
```

### Respuesta
Extractor actualizado.

## GET `/api/extractors/{extractorId}/history`

### Qué hace
Historial de programación de un extractor.

### Parámetros
- `extractorId`
- `limit` opcional

### Respuesta
Lista de historial.

## GET `/api/extractors/history`

### Qué hace
Historial global de extractores.

### Parámetros
- `limit` opcional

### Respuesta
Lista de historial.

---

# 6) Criadoras

## POST `/api/criadoras`

### Qué hace
Crea una criadora.

### Recibe
```json
{
  "name": "Criadora 1"
}
```

### Respuesta
Criadora creada.

## GET `/api/criadoras`

### Qué hace
Lista criadoras con programación.

### Respuesta
Lista de criadoras.

## PUT `/api/criadoras/{criadoraId}/programming`

### Qué hace
Actualiza la programación de una criadora.

### Recibe
```json
{
  "temperatureOn": 33,
  "temperatureOff": 30
}
```

### Respuesta
Criadora actualizada.

## GET `/api/criadoras/{criadoraId}/history`

### Qué hace
Historial de programación de una criadora.

### Parámetros
- `criadoraId`
- `limit` opcional

### Respuesta
Lista de historial.

## GET `/api/criadoras/history`

### Qué hace
Historial global de criadoras.

### Parámetros
- `limit` opcional

### Respuesta
Lista de historial.

---

# 7) Bombas

## POST `/api/bombas`

### Qué hace
Crea una bomba.

### Recibe
```json
{
  "name": "Bomba 1"
}
```

### Respuesta
Bomba creada.

## GET `/api/bombas`

### Qué hace
Lista bombas con programación.

### Respuesta
Lista de bombas.

## PUT `/api/bombas/{bombaId}/programming`

### Qué hace
Actualiza la programación de una bomba.

### Recibe
```json
{
  "temperatureOn": 26,
  "temperatureOff": 24,
  "workDurationSeconds": 120
}
```

### Respuesta
Bomba actualizada.

## GET `/api/bombas/{bombaId}/history`

### Qué hace
Historial de programación de una bomba.

### Parámetros
- `bombaId`
- `limit` opcional

### Respuesta
Lista de historial.

## GET `/api/bombas/history`

### Qué hace
Historial global de bombas.

### Parámetros
- `limit` opcional

### Respuesta
Lista de historial.

---

# 8) Mortalidad

## POST `/api/mortalidad`

### Qué hace
Crea el registro diario de mortalidad de la parvada activa.

### Recibe
```json
{
  "maleCount": 2,
  "femaleCount": 3,
  "observations": "Enfermedad respiratoria"
}
```

### Respuesta
```json
{
  "id": 1,
  "recordDate": "2026-05-10",
  "ageDays": 9,
  "maleCount": 2,
  "femaleCount": 3,
  "totalCount": 5,
  "observations": "Enfermedad respiratoria",
  "createdAt": "2026-05-10T14:10:08.912002Z"
}
```

### Reglas
- `recordDate` se calcula automáticamente como hoy.
- `ageDays` se calcula automáticamente desde `flockDate`.
- sólo se permite un registro por parvada y día.

## GET `/api/mortalidad`

### Qué hace
Lista registros de mortalidad.

### Parámetros
- `from` opcional
- `to` opcional

Ejemplo:
- `/api/mortalidad?from=2026-05-01&to=2026-05-10`

### Respuesta
Lista de registros de mortalidad.

---

# 9) Peso

## POST `/api/peso`

### Qué hace
Crea un registro de peso para la parvada activa.

### Recibe
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

### Respuesta
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

## GET `/api/peso`

### Qué hace
Lista todos los registros de peso.

### Respuesta
Lista de registros.

## GET `/api/peso/flock/{flockId}`

### Qué hace
Lista pesos por parvada.

### Parámetros
- `flockId`

### Respuesta
Lista de registros.

## GET `/api/peso/flock/{flockId}/gender/{gender}`

### Qué hace
Lista pesos por parvada y sexo.

### Parámetros
- `flockId`
- `gender` = `male` o `female`

### Respuesta
Lista filtrada.

## GET `/api/peso/flock/{flockId}/latest/gender/{gender}`

### Qué hace
Trae el último peso por parvada y sexo.

### Parámetros
- `flockId`
- `gender`

### Respuesta
Un registro.

## GET `/api/peso/latest/male`

### Qué hace
Último peso de machos de la parvada activa.

### Respuesta
Un registro.

## GET `/api/peso/latest/female`

### Qué hace
Último peso de hembras de la parvada activa.

### Respuesta
Un registro.

## GET `/api/peso/flock/{flockId}/range`

### Qué hace
Lista pesos por rango de fechas.

### Parámetros
- `flockId`
- `from`
- `to`

### Respuesta
Lista de registros.

---

# 10) Consumo

## POST `/api/consumo`

### Qué hace
Registra consumo total y calcula consumo por ave.

### Recibe
```json
{
  "age": 10,
  "recordDate": "2026-05-10",
  "totalConsumptionKg": 120.0
}
```

### Respuesta
```json
{
  "id": 1,
  "flockId": 1,
  "age": 10,
  "recordDate": "2026-05-10",
  "totalConsumptionKg": 120,
  "birdsCountUsed": 95,
  "consumptionPerBirdKg": 1.263157894736842,
  "createdAt": "2026-05-10T14:58:33.677327Z"
}
```

## GET `/api/consumo`

### Qué hace
Lista registros de consumo.

### Respuesta
Lista de registros.

## GET `/api/consumo/flock/{flockId}`

### Qué hace
Lista consumos por parvada.

### Parámetros
- `flockId`

### Respuesta
Lista de registros.

---

# Recomendación para frontend

Los endpoints más importantes para construir la pantalla principal son:

- `GET /api/dashboard/principal`
- `GET /api/flocks/active`
- `GET /api/readings/latest`
- `GET /api/peso/latest/male`
- `GET /api/peso/latest/female`
- `GET /api/mortalidad`
- `GET /api/consumo`

## Flujo de datos sugerido

1. Cargar dashboard principal.
2. Mostrar parvada activa.
3. Pintar última telemetría.
4. Pintar peso actual.
5. Pintar mortalidad y consumo recientes.
6. Refrescar lecturas y dashboard cada cierto tiempo.

---

# Notas finales

- Las lecturas de sensores llegan por MQTT, no por HTTP.
- El backend usa la parvada activa para mortalidad, consumo y dashboard.
- Si no hay parvada activa, esos módulos pueden responder con error o vacío.
