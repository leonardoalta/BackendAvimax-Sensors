# AviMax — Consumo de históricos desde el frontend

Este documento explica **cómo debe consumir el frontend** los históricos de:
- temperatura
- humedad
- amoniaco

No modifica el backend; solo describe el uso correcto de la API ya disponible.

---

## 1) Endpoint principal para históricos

### `GET /api/readings`

Este es el endpoint que debe usar el frontend para consultar históricos filtrados por:
- rango de fechas
- variable: temperatura, humedad o nh3
- gateway
- sensor
- paginación
- ordenamiento

### URL base
```text
http://<HOST>:8080/api/readings
```

### Parámetros de consulta
| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---:|---|
| `start` | string ISO-8601 | No | Fecha/hora inicial del rango |
| `end` | string ISO-8601 | No | Fecha/hora final del rango |
| `variable` | string | No | `temperatura`, `humedad` o `nh3` |
| `gateway` | string | No | Filtra por gateway |
| `sensor` | string | No | Filtra por sensor |
| `page` | number | No | Página, inicia en 0 |
| `size` | number | No | Cantidad de registros por página |
| `sort` | string | No | Ejemplo: `recordedAt,desc` |

---

## 2) Formato de respuesta esperado

El frontend debe esperar una respuesta así:

```json
{
  "data": [
    {
      "id": 6709,
      "flockId": 1,
      "gatewayId": "raspi5-galpon-01",
      "sensor": "avicola/galpon1/lecturas",
      "deviceId": "avicola/galpon1/lecturas",
      "timestamp": "2026-06-02T21:54:48.701858Z",
      "temperatura_c": 26.5,
      "humedad_relativa": 64.1,
      "nh3_ppm": 0.0
    }
  ],
  "status": 200
}
```

### Campos importantes
- `timestamp`: fecha de la lectura
- `temperatura_c`: temperatura en °C
- `humedad_relativa`: humedad en %
- `nh3_ppm`: amoniaco en ppm
- `gatewayId`: identifica la Raspberry/gateway
- `sensor` y `deviceId`: identifican el origen de la lectura

---

## 3) Cómo consultar por cada variable

Aunque el endpoint trae la lectura completa, el frontend puede usar el parámetro `variable` para enfocar la consulta en una métrica específica.

### Temperatura
```text
GET /api/readings?variable=temperatura
```

### Humedad
```text
GET /api/readings?variable=humedad
```

### Amoniaco
```text
GET /api/readings?variable=nh3
```

> Recomendación: si la pantalla muestra las tres métricas juntas, puede omitirse `variable` y usar un solo request.

---

## 4) Ejemplos de consumo desde el frontend

### A) Usando `fetch`

```javascript
async function getHistoricos({ start, end, variable, gateway, sensor, page = 0, size = 20 }) {
  const params = new URLSearchParams();

  if (start) params.append('start', start);
  if (end) params.append('end', end);
  if (variable) params.append('variable', variable);
  if (gateway) params.append('gateway', gateway);
  if (sensor) params.append('sensor', sensor);
  params.append('page', page);
  params.append('size', size);
  params.append('sort', 'recordedAt,desc');

  const response = await fetch(`http://192.168.1.88:8080/api/readings?${params.toString()}`);
  if (!response.ok) {
    throw new Error(`Error HTTP ${response.status}`);
  }

  return await response.json();
}
```

### B) Ejemplo para React

```javascript
import { useEffect, useState } from 'react';

function HistoricosTemperatura() {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    async function load() {
      try {
        setLoading(true);
        const start = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
        const end = new Date().toISOString();

        const response = await fetch(
          `http://192.168.1.88:8080/api/readings?start=${start}&end=${end}&variable=temperatura&page=0&size=50&sort=recordedAt,desc`
        );

        const json = await response.json();
        setData(json.data || []);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    }

    load();
  }, []);

  if (loading) return <p>Cargando...</p>;
  if (error) return <p>Error: {error}</p>;

  return (
    <table>
      <thead>
        <tr>
          <th>Fecha</th>
          <th>Temperatura</th>
        </tr>
      </thead>
      <tbody>
        {data.map((row) => (
          <tr key={row.id}>
            <td>{row.timestamp}</td>
            <td>{row.temperatura_c}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
```

---

## 5) Cómo mostrar cada histórico en el frontend

### Pantalla de temperatura
- Consultar `variable=temperatura`
- Mostrar `timestamp` y `temperatura_c`
- Opcionalmente mostrar `gatewayId` y `sensor`

### Pantalla de humedad
- Consultar `variable=humedad`
- Mostrar `timestamp` y `humedad_relativa`

### Pantalla de amoniaco
- Consultar `variable=nh3`
- Mostrar `timestamp` y `nh3_ppm`

---

## 6) Recomendaciones de UI

- Usar un selector de rango de fechas
- Usar paginación si hay muchos registros
- Ordenar por fecha descendente (`recordedAt,desc`)
- Mostrar el valor con su unidad:
  - temperatura → `°C`
  - humedad → `%`
  - amoniaco → `ppm`
- Si no hay datos, mostrar un mensaje claro:
  - `Sin datos disponibles para el rango seleccionado`

---

## 7) Verificación rápida

### Temperatura
```text
GET /api/readings?variable=temperatura&page=0&size=20&sort=recordedAt,desc
```

### Humedad
```text
GET /api/readings?variable=humedad&page=0&size=20&sort=recordedAt,desc
```

### Amoniaco
```text
GET /api/readings?variable=nh3&page=0&size=20&sort=recordedAt,desc
```

---

## 8) Estado del broker y sensores

Para que el dashboard pueda saber si los sensores dejaron de publicar o si el backend perdió la suscripción al broker, usar:

### `GET /api/status/mqtt`

Ejemplo:

```text
GET /api/status/mqtt
```

Respuesta esperada:

```json
{
  "connected": true,
  "subscribedTopic": "avicola/galpon1/lecturas",
  "lastMessageReceivedAt": "2026-06-04T10:30:15.123456Z",
  "totalMessagesReceived": 1234,
  "connectionStatus": "CONNECTED",
  "lastError": null,
  "lastErrorAt": null,
  "brokerUrl": "tcp://192.168.1.100:1883"
}
```

### Cómo interpretar el estado

- `connected: true` → el backend está suscrito y conectado al broker.
- `connected: false` → el backend no pudo conectarse o perdió la conexión.
- `lastMessageReceivedAt` → última vez que llegó una lectura.
- `totalMessagesReceived` → contador total de mensajes procesados.
- `lastError` → último error registrado al conectar o recibir mensajes.

### Regla recomendada para el dashboard

Si `connected = false`, mostrar alerta roja inmediata.

Si `connected = true` pero `lastMessageReceivedAt` es muy viejo, mostrar alerta amarilla de posibles sensores caídos.

Ejemplo de criterio:

$$
	ext{alerta} = \text{NOW} - \text{lastMessageReceivedAt} > 5\ \text{minutos}
$$

### Endpoint de salud

```text
GET /api/status/health
```

Usarlo para mostrar un estado general:

- `UP` → sistema funcionando
- `DEGRADED` → hay conexión previa, pero ya no llegan datos
- `DOWN` → no llegan lecturas y el broker no está disponible
- `ERROR` → fallo al consultar el estado

### Ejemplo de mapping en frontend

```javascript
function mapMqttStatus(status) {
  if (!status) return { color: 'gray', label: 'Sin estado' };

  if (!status.connected) {
    return { color: 'red', label: 'Broker desconectado' };
  }

  if (status.lastMessageReceivedAt) {
    const last = new Date(status.lastMessageReceivedAt).getTime();
    const diffMinutes = (Date.now() - last) / 60000;

    if (diffMinutes > 5) {
      return { color: 'yellow', label: 'Sin lecturas recientes' };
    }
  }

  return { color: 'green', label: 'Sensores activos' };
}
```

### Ejemplo con `fetch`

```javascript
async function getMqttStatus() {
  const response = await fetch('http://192.168.1.88:8080/api/status/mqtt');
  if (!response.ok) throw new Error(`Error HTTP ${response.status}`);
  return await response.json();
}
```

---

## 9) Ejemplo con el dato real que ya devuelve la API

La API ya responde con un objeto como este:

```json
{
  "data": {
    "id": 6709,
    "flockId": 1,
    "gatewayId": "raspi5-galpon-01",
    "sensor": "avicola/galpon1/lecturas",
    "deviceId": "avicola/galpon1/lecturas",
    "timestamp": "2026-06-02T21:54:48.701858Z",
    "temperatura_c": 26.5,
    "humedad_relativa": 64.1,
    "nh3_ppm": 0.0
  },
  "status": 200
}
```

Por tanto, el frontend debe leer:
- `data.temperatura_c`
- `data.humedad_relativa`
- `data.nh3_ppm`
- `data.timestamp`

---

## 9) Resumen corto para integrar

Para consultar históricos desde el frontend:
1. Llamar a `GET /api/readings`
2. Enviar `start`, `end`, `variable`, `page`, `size` y `sort`
3. Leer la lista en `response.data`
4. Pintar:
   - `temperatura_c` para temperatura
   - `humedad_relativa` para humedad
   - `nh3_ppm` para amoniaco
5. Mostrar `timestamp` como eje temporal

---

## 10) Nota importante

Si el frontend consulta `http://localhost:8080`, eso solo funciona en la misma máquina donde corre el backend.

Si el frontend está en otra computadora, debe usar la IP de la Raspberry, por ejemplo:
```text
http://192.168.1.88:8080/api/readings
```

