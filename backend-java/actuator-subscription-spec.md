# Especificación detallada: Proceso de suscripción al Broker WebSocket
Fecha: 11-05-2026

Propósito
- Documentar paso a paso cómo suscribirse al broker WebSocket, qué tópicos se deben escuchar, el formato exacto de los mensajes entrantes, y cómo mapearlos al estado de actuadores del simulador.

Audiencia
- Desarrolladores Frontend que integran la UI con el broker.
- Equipos que construyen el backend/controlador que publica estados.

Resumen del flujo
1. El cliente (UI) abre una conexión WebSocket (WSS si está en producción) al endpoint proporcionado por el broker.
2. El cliente se autentica si procede (token JWT en `Sec-WebSocket-Protocol` o query param). 
3. Tras apertura: el cliente debe solicitar el estado inicial (topic `avimax/request/state`) o esperar un `avimax/actuators/state` retenido.
4. El cliente se suscribe lógicamente (filtrado por `topic` en el envelope JSON) a los mensajes relevantes: `avimax/actuators/state` (bulk) y `avimax/actuator/{type}/{n}/state` (unit).
5. El cliente normaliza los mensajes y actualiza el `ActuatorContext` con arrays: `fans[10]`, `heaters[5]`, `pumps[2]`.

Conexión
- URL por defecto (frontend): `ws://localhost:8080/ws` en desarrollo.
- En producción: `wss://<host>/ws`.
- Si tu broker está en el puerto `1883`, eso normalmente significa MQTT sobre TCP.
  - El navegador no puede suscribirse directamente a un socket TCP MQTT puro.
  - Para el frontend necesitas una de estas dos opciones:
    1. Un listener WebSocket en el broker/servidor (por ejemplo `ws://<host>:9001/ws` o `ws://<host>:8080/ws`).
    2. Un backend puente que se conecte a MQTT en `1883` y reenvíe los mensajes al frontend por WebSocket.
- Autenticación recomendada: JWT en `Sec-WebSocket-Protocol` o en la query string `?token=...` (preferir Sec-WebSocket-Protocol).
- Reintentos: backoff exponencial (500ms → 30s). Al reconectar, solicitar estado completo.

Topics (convención)
- Unidad (estado por actuador): `avimax/actuator/{type}/{n}/state`
  - `{type}` ∈ `fan|heater|pump`
  - `{n}`: número humano 1-based (E1→1, C1→1, B1→1)
- Estado agregado (bulk): `avimax/actuators/state` — contiene arrays para cada tipo
- Solicitud de estado: cliente -> `avimax/request/state`
- Comandos (opcional): cliente -> `avimax/actuator/command`
- Ack (opcional): `avimax/ack/{commandId}`

Formato de mensajes (envelope)
- El broker envía y el cliente recibe un objeto JSON llamado "envelope":

```json
{
  "topic": "avimax/actuator/fan/3/state",
  "payload": { ... }
}
```

Se recomienda usar siempre `topic` en el envelope (aunque payload también puede incluirlo).

Schemas

1) Mensaje unitario (estado de una unidad)

Topic ejemplo: `avimax/actuator/fan/5/state`

Payload (requeridos y opcionales):

```json
{
  "type": "fan",         // "fan"|"heater"|"pump"
  "number": 5,            // 1-based index (útil para etiquetas E5)
  "index": 4,             // 0-based index (útil para mapear arrays)
  "label": "E5",        // legible (opcional)
  "state": true,          // boolean: true = ON, false = OFF
  "source": "controller-01", // identificador del emisor (opcional)
  "timestamp": "2026-05-11T12:34:56.789Z"
}
```

Notas:
- `index` permite asignar directamente en React: `setFanStates(prev => prev.map((v,i)=> i===index?state:v))`.

2) Mensaje bulk (estado completo)

Topic: `avimax/actuators/state`

Payload:

```json
{
  "fans": [true, false, ...],    // longitud 10
  "heaters": [true,false,...],   // longitud 5
  "pumps": [true,false],         // longitud 2
  "timestamp": "...",
  "source": "controller-01"
}
```

Validaciones esperadas por el cliente:
- `fans.length === 10`, `heaters.length === 5`, `pumps.length === 2`. Si la longitud no coincide, loggear y rechazar (o usar fallback).

3) Mensaje de comando (desde UI hacia broker)

Topic: `avimax/actuator/command`

Payload ejemplo:

```json
{
  "commandId": "uuid-123",
  "action": "set",           // "set" | "toggle" | "setAll"
  "target": { "type": "fan", "index": 2 },
  "value": true,
  "origin": "ui-frontend",
  "timestamp": "..."
}
```

Comportamiento esperado del controlador/broker:
- Validar permisos del `origin` antes de aplicar cambios físicos.
- Emitir el `unit` update y/o `bulk` update cuando cambie el estado.
- (Opcional) publicar `avimax/ack/{commandId}` con resultado.

Suscripción en el Frontend (paso a paso)
1. Abrir conexión WebSocket usando `brokerAdapter.connect()`.
2. Esperar evento `onOpen` (open listener) y entonces:
   - enviar `{ topic: "avimax/request/state", payload: { origin: "ui-frontend" } }` para pedir estado inicial, OR
   - suscribirse a `avimax/actuators/state` y `avimax/actuator/*/+/state` dependiendo de las capacidades del broker.
3. Registrar callback para `onMessage` o usar `adapter.subscribe(topic, cb)`. Normalizar envelopes como sigue:
   - Si `envelope.topic === 'avimax/actuators/state'` → actualizar arrays completos.
   - Si `envelope.topic` coincide con `/avimax/actuator/{type}/{n}/state` → extraer `type` y `index` y aplicar `unit update`.
4. Al reconectar: solicitar `avimax/request/state` y reemplazar arrays (bulk) de forma atómica para evitar inconsistencias visuales.

Código de ejemplo (pseudocódigo usando `brokerAdapter` / `actuatorService` del repo)

```js
// adapter ya manejado por actuatorService en este repo
const service = createActuatorService({ url: 'ws://localhost:8080/ws' });
service.subscribe((event)=>{
  if(event.type === 'bulk') {
    setFanStates(event.fans);
    setHeaterStates(event.heaters);
    setPumpStates(event.pumps);
    return;
  }
  if(event.type === 'unit'){
    if(event.type === 'fan') setFanStates(prev => prev.map((v,i)=> i===event.index?event.state:v));
  }
});
service.start();
```

Formato exacto que el cliente debe aceptar
- Envelope JSON con `topic` y `payload`.
- `payload` para `unit` debe contener al menos: `type`, `index`, `state`.
- `payload` para `bulk` debe contener arrays con las longitudes definidas.

Compatibilidad con MQTT en `1883`
- Si el backend publica por MQTT clásico, el frontend no debe intentar abrir `ws://localhost:1883`.
- En ese caso el backend debe publicar los mismos `topic` y `payload` de este documento, pero reenviados por WebSocket.
- Los tópicos siguen siendo los mismos:
  - `avimax/actuators/state`
  - `avimax/actuator/{type}/{n}/state`
  - `avimax/request/state`
  - `avimax/actuator/command`

Recomendaciones de robustez
- Debounce/throttle: si el broker envía ráfagas, agrupar actualizaciones (pero para actuadores ON/OFF normalmente no es alta frecuencia).
- Reemplazo atómico: al recibir `bulk` usar `setState` para reemplazar todo el array en una sola operación.
- Reconexión: al reconectar solicitar `avimax/request/state` para sincronizar el estado.
- Logs: loggear `topic` y `payload` si payload inválido para facilitar debugging.

Seguridad
- Usar `wss://` en producción.
- Autenticación: JWT en `Sec-WebSocket-Protocol` o query string (preferir `Sec-WebSocket-Protocol`).
- Autorización: solo backend/controller puede aceptar comandos que afecten hardware. UI envía `command` que será validado por backend.

Test y dev
- En desarrollo usar `scripts/mock-broker.js` incluido en este repo. Ejemplos:
  - `node ./scripts/send-toggle-fans.js` → envía toggles de ejemplo.
  - `node ./scripts/set-fans-off.js` → envía `set=false` para primeros 5 fans y pide estado.

Checklist para integrar en la UI
- [ ] Abrir WS y manejar reconexiones.
- [ ] Suscribirse a `avimax/actuators/state` y `avimax/actuator/{type}/{n}/state`.
- [ ] Normalizar mensajes a `unit` y `bulk` y actualizar `ActuatorContext`.
- [ ] En reconexión solicitar `avimax/request/state`.
- [ ] Implementar envío de comandos `avimax/actuator/command` desde UI cuando el usuario interaccione.

Ejemplos concretos (resumidos)
- Unit (E5 encendido):
  - Topic: `avimax/actuator/fan/5/state`
  - Payload: `{ "type":"fan", "number":5, "index":4, "state":true, "timestamp":"..." }`
- Bulk:
  - Topic: `avimax/actuators/state`
  - Payload: `{ "fans":[true,false,...], "heaters":[...], "pumps":[...], "timestamp":"..." }`

Fin del documento.

*** Archivo generado automáticamente por la integración frontend/broker — si quieres, lo adapto a otro formato (Markdown + JSON Schema separado) o añado ejemplos de código concretos en React.
