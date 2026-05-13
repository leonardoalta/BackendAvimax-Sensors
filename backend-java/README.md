# AviMax Backend

Backend en Spring Boot para:

- crear y cerrar parvadas
- asegurar que solo exista una parvada activa
- consumir lecturas por MQTT
- guardar lecturas históricas en TimescaleDB

## Arquitectura base

- Spring Boot 3
- Java 17
- PostgreSQL + TimescaleDB
- MQTT con Eclipse Paho
- Flyway para migraciones

## Modelo de datos

### `flocks`

Guarda las parvadas del galpón.
Solo una puede estar activa porque existe un índice único parcial sobre `status = 'ACTIVE'`.

### `sensor_readings`

Guarda cada lectura recibida desde MQTT.
Se convierte en hypertable de TimescaleDB usando `recorded_at` como columna de tiempo.

### `extractors`, `criadoras`, `bombas`

Cada tipo de actuador es una entidad independiente.
Cada uno tiene su tabla de programación:

- `extractor_programming` con `temperatureOn/temperatureOff`
- `criadora_programming` con `temperatureOn/temperatureOff`
- `bomba_programming` con `temperatureOn/temperatureOff/workDurationSeconds`

## Endpoints iniciales

- `POST /api/flocks` crea una parvada activa
- `GET /api/flocks` lista todas las parvadas
- `GET /api/flocks/active` obtiene la activa
- `POST /api/flocks/{id}/close` cierra una parvada
- `GET /api/readings/latest` obtiene la última lectura
- `POST /api/extractors` crea extractor
- `GET /api/extractors` lista extractores + su programación
- `PUT /api/extractors/{id}/programming` crea/actualiza programación de extractor
- `POST /api/criadoras` crea criadora
- `GET /api/criadoras` lista criadoras + su programación
- `PUT /api/criadoras/{id}/programming` crea/actualiza programación de criadora
- `POST /api/bombas` crea bomba
- `GET /api/bombas` lista bombas + su programación
- `PUT /api/bombas/{id}/programming` crea/actualiza programación de bomba

## MQTT esperado

El backend acepta estas formas:

### Formato directo

```json
{
  "gateway_id": "raspi5-galpon-01",
  "timestamp": "2026-05-09T10:30:00Z",
  "temperature": 28.4,
  "humidity": 62.1,
  "nh3": 8.7
}
```

### Formato con readings

```json
{
  "gateway_id": "raspi5-galpon-01",
  "timestamp": "2026-05-09T10:30:00Z",
  "readings": [
    {
      "temperatura_c": 28.4,
      "humedad_relativa": 62.1,
      "nh3_ppm": 8.7
    }
  ]
}
```

## Siguientes pasos recomendados

1. Conectar el sensor NH3 real al payload del publicador.
2. Añadir endpoints de consultas por rango de tiempo.
3. Añadir dashboard agregado por parvada activa.
4. Migrar secretos y credenciales a variables de entorno.
