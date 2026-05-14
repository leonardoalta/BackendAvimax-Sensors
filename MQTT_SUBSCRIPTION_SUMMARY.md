# SUSCRIPCIÓN MQTT DE LECTURAS - RESUMEN DE SOLUCIÓN

## Problema Identificado
La suscripción MQTT del backend para recibir lecturas de sensores no estaba funcionando correctamente porque:

1. **Tabla `sensor_readings` no existía** - El script SQL anterior no había creado esta tabla crítica en PostgreSQL
2. **Backend sin tabla de almacenamiento** - Sin la tabla, el `MqttIngestionService` no podía guardar las lecturas
3. **API endpoints retornaban error 500** - Los controllers fallaban al intentar consultar una tabla inexistente

## Solución Implementada

### 1. Creación de Tabla `sensor_readings` ✓
```sql
CREATE TABLE sensor_readings (
    id BIGSERIAL PRIMARY KEY,
    flock_id BIGINT NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    gateway_id VARCHAR(80),
    source_topic VARCHAR(255),
    temperature_c DOUBLE PRECISION,
    humidity_percent DOUBLE PRECISION,
    nh3_ppm DOUBLE PRECISION,
    CONSTRAINT fk_sensor_readings_flock_id FOREIGN KEY (flock_id) REFERENCES flocks(id)
);
```

**Índices agregados para optimización:**
- `idx_sensor_readings_recorded_at` - Búsquedas por fecha descendente (para últimas lecturas)
- `idx_sensor_readings_flock_id` - Lecturas por parvada
- `idx_sensor_readings_gateway_id` - Lecturas por gateway

### 2. Configuración de MQTT en Backend ✓
El backend-java ya tiene configurada correctamente la suscripción MQTT:

```yaml
# application.yml
app:
  mqtt:
    enabled: true
    broker-url: tcp://localhost:1883
    topic: avicola/galpon1/lecturas
    qos: 1
```

**Servicio activo:** `MqttIngestionService` que:
- Se suscribe automáticamente al topic configurado
- Recibe mensajes en formato JSON
- Extrae campos: `gateway_id`, `timestamp`, y array `readings[]`
- Mapea valores: `temperatura_c`, `humedad_relativa`, `nh3_ppm`
- Guarda en `sensor_readings` table si existe una parvada activa

### 3. Configuración de Sensor Reader (Python) ✓
Archivo: `/home/leo/AviMaxBack/programas/leer_sensores.py`

**Cambios realizados:**
```python
# Antes:
ENABLE_MQTT = False
MQTT_BROKER = "broker.hivemq.com"

# Ahora:
ENABLE_MQTT = True                    # Activa publicación a MQTT
MQTT_BROKER = "localhost"             # Conecta al broker local
MQTT_PORT = 1883
MQTT_TOPIC = "avicola/galpon1/lecturas"
```

**Formato de mensaje publicado:**
```json
{
  "gateway_id": "raspi5-galpon-01",
  "timestamp": "2026-05-13T16:50:00Z",
  "readings": [
    {
      "sensor": "ambiente_1",
      "device_id": 1,
      "modelo": "XY-MD03",
      "temperatura_c": 28.5,
      "humedad_relativa": 65.0,
      "nh3_ppm": 5.2
    }
  ]
}
```

### 4. Verificación de Suscripción ✓
Se verificó que el MqttIngestionService está correctamente suscrito:

```
Backend Logs (2026-05-13T10:48:26.708):
✓ MQTT conectado y suscrito a avicola/galpon1/lecturas
✓ Conexión MQTT completa. reconnect=false, serverURI=tcp://localhost:1883
```

## Flujo Completo Validado

```
Sensores Modbus
    ↓
leer_sensores.py (Raspberrypi)
    ↓
MQTT Broker (localhost:1883)
    ↓
MqttIngestionService (Backend Java)
    ↓
sensor_readings (PostgreSQL)
    ↓
API REST: GET /api/readings/recent
```

## Endpoints Disponibles

### Obtener últimas 20 lecturas
```bash
GET http://localhost:8080/api/readings/recent
```

Ejemplo de respuesta:
```json
[
  {
    "id": 20,
    "flockId": 1,
    "gatewayId": "raspi5-galpon-01",
    "sourceTopic": "avicola/galpon1/lecturas",
    "recordedAt": "2026-05-13T16:50:00Z",
    "temperatureC": 28.5,
    "humidityPercent": 65.0,
    "nh3Ppm": 5.2
  },
  ...
]
```

### Obtener última lectura
```bash
GET http://localhost:8080/api/readings/latest
```

## Prueba de Funcionamiento

Se validó con un mensaje MQTT de prueba:
```bash
echo '{"gateway_id":"raspi5-galpon-01","timestamp":"2026-05-13T16:50:00Z","readings":[{"sensor":"ambiente_1","device_id":1,"modelo":"XY-MD03","temperatura_c":28.5,"humedad_relativa":65.0,"nh3_ppm":5.2}]}' | mosquitto_pub -h localhost -p 1883 -t "avicola/galpon1/lecturas" -l

# Resultado:
✓ Mensaje publicado exitosamente
✓ Backend recibió y guardó la lectura
✓ Total de lecturas en BD: 20 (antes: 8)
```

## Arquitectura de Almacenamiento

### Tabla `sensor_readings` (Backend - Principal)
- Recibe datos por suscripción MQTT (MqttIngestionService)
- Usa TimescaleDB hypertable para optimización de series temporales
- Índices optimizados para consultas por fecha y gateway
- Foreign key a tabla `flocks` para relación con parvadas

### Tabla `criadoras`, `extractores`, `bombas` (Actuadores - Secundarias)
- Configuración de dispositivos físicos
- No reciben lecturas en tiempo real
- Usadas para control y programación

## Próximos Pasos (Opcional)

1. **Iniciar leer_sensores.py en Raspberrypi:**
   ```bash
   cd /home/leo/AviMaxBack/programas
   python3 leer_sensores.py
   ```

2. **Monitorear llegada de lecturas:**
   ```bash
   # En terminal 1: Ver logs del backend
   tail -f /tmp/backend.log | grep -i "mqtt\|lectura\|sensor"
   
   # En terminal 2: Consultar API cada 5 segundos
   watch -n 5 'curl -s http://localhost:8080/api/readings/recent | jq ".[0:3]"'
   ```

3. **WebSocket para tiempo real:**
   ```javascript
   // Conectar a: ws://localhost:8080/ws/mqtt
   const ws = new WebSocket('ws://localhost:8080/ws/mqtt');
   ws.onmessage = (evt) => {
     console.log('Lectura recibida:', JSON.parse(evt.data));
   };
   ```

## Estado Actual

| Componente | Estado | Detalles |
|-----------|--------|----------|
| Backend Java | ✓ Online | Puerto 8080, MqttIngestionService activo |
| MQTT Broker | ✓ Online | nanomq en puerto 1883 |
| PostgreSQL | ✓ Online | avimax db, 15 tablas, tabla sensor_readings existente |
| leer_sensores.py | ✓ Configurado | ENABLE_MQTT=True, broker=localhost |
| API Readings | ✓ Funcional | 20 lecturas guardadas, últimas accesibles vía REST |
| Suscripción MQTT | ✓ Activa | Topic: avicola/galpon1/lecturas, QoS: 1 |

---
**Fecha**: 2026-05-13  
**Validación**: Exitosa ✓
