# Especificación: Broker WebSocket para estado de actuadores — AviMax

Fecha: 11-05-2026

Resumen
- Propósito: definir los tópicos, formatos de mensaje y comportamientos esperados del sistema de mensajería (WebSocket) que informará qué actuador está activo/inactivo. Esta especificación permite implementar `brokerAdapter` y `actuatorService` sin tocar el render 3D.
- Alcance: mensajes de telemetría de estado y comandos básicos (opcional). Seguridad, reconexión, ejemplos y mapeo de actuadores incluidos.

1. Terminología y convenciones
- Actuador: dispositivo visible en el simulador (Extractor, Criadora, Bomba).
- ID lógico: índice entero (0-based) usado internamente para arrays de estado.
- Etiquetas de usuario: E1..E10, C1..C5, B1..B2.
- Envelope WebSocket: cada frame enviado por broker o cliente tendrá la forma JSON:

```json
{ "topic": "<string>", "payload": { ... }, "meta": { "timestamp": 1670000000 } }
```

Nota: aunque usamos WebSockets puros, el campo `topic` facilita suscripciones lógicas.

2. Mapeo de actuadores
- Extractores (fans): 10 unidades
  - Etiquetas: E1..E10
  - Índices internos (fanStates): 0..9
  - Tópicos sugeridos por unidad: `avimax/actuator/fan/{index}/state` (index 1-based en tópicos humanos; ver convención abajo)

- Criadoras (heaters): 5 unidades
  - Etiquetas: C1..C5
  - Índices internos (heaterStates): 0..4
  - Tópicos: `avimax/actuator/heater/{index}/state`

- Bombas (pumps): 2 unidades
  - Etiquetas: B1..B2
  - Índices internos (pumpStates): 0..1
  - Tópicos: `avimax/actuator/pump/{index}/state`

3. Convención de nombres de topic
- Topic por unidad (estado): `avimax/actuator/{type}/{n}/state`
  - `{type}` ∈ `fan|heater|pump`
  - `{n}`: número humano 1-based (E1→1, E10→10)
- Topic agregado (estado completo): `avimax/actuators/state` con payload agregando arrays.
- Topic de comandos (opcional control remoto desde UI/backend): `avimax/actuator/command`
- Topic de respuesta/ack: `avimax/ack/{messageId}` (para confirms opcionales)

4. Esquema de mensajes
A. Mensaje unitario de estado (enviado por broker cada vez que cambia un actuador)

Envelope:
```json
{
  "topic": "avimax/actuator/fan/3/state",
  "payload": {
    "type": "fan",
    "number": 3,
    "index": 2,
    "label": "E3",
    "state": true,
    "source": "controller-01",
    "timestamp": "2026-05-11T12:34:56.789Z"
  }
}
```
- `number`: 1-based (útil para humanos/visualización)
- `index`: 0-based (útil para mapear directamente en arrays React)
- `state`: boolean (true = ON, false = OFF)

B. Mensaje agregado (estado completo)

Topic: `avimax/actuators/state`

Payload ejemplo:
```json
{
  "topic": "avimax/actuators/state",
  "payload": {
    "fans": [true, true, false, ...],
    "heaters": [true, false, ...],
    "pumps": [false, true],
    "timestamp": "2026-05-11T12:35:00.000Z",
    "source": "controller-01"
  }
}
```
- Longitudes esperadas: fans.length === 10, heaters.length === 5, pumps.length === 2.

C. Mensaje de comando (opcional)

Topic: `avimax/actuator/command`
Payload ejemplo:
```json
{
  "topic": "avimax/actuator/command",
  "payload": {
    "commandId": "uuid-123",
    "action": "set",           // "set" | "toggle" | "setAll"
    "target": { "type": "fan", "index": 2 },
    "value": true,
    "origin": "ui-frontend",
    "timestamp": "..."
  }
}
```
- El broker/servidor debe validar permisos antes de ejecutar comandos.
- Respuesta/ack debe publicarse en `avimax/ack/uuid-123` con resultado.

5. JSON Schema (resumen)
- Unidad (unit state):
```json
{
  "type": "object",
  "required": ["type","number","index","state"],
  "properties": {
    "type": { "type": "string" },
    "number": { "type": "integer" },
    "index": { "type": "integer" },
    "label": { "type": "string" },
    "state": { "type": "boolean" },
    "timestamp": { "type": "string", "format": "date-time" }
  }
}
```

- Estado agregado: objetos con arrays de booleanos con tamaños fijos.

6. Recomendaciones de comportamiento y flujo
- Al conectar un cliente (UI), flujo recomendado:
  1. Establecer conexión WebSocket WSS.
  2. Autenticar (ver sección Seguridad). Si autenticación ok → suscribir (lógica local: aceptar todos los `topic` del envelope).
  3. Solicitar estado inicial: enviar mensaje `{ topic: "avimax/request/state", payload: { origin: "ui-frontend" } }` o pedir vía REST `GET /api/actuators/state` si existe.
  4. Subscribirse a `avimax/actuators/state` y a `avimax/actuator/+/+/state` (si el broker soporta wildcard) — si no, cliente debe aceptar todos los envelopes y filtrar por `topic`.
  5. Actualizar `ActuatorContext` con mensaje agregado o unitario.

- Mecanismo de confirmación (opcional): cada comando incluye `commandId`; el controlador publica ack en `avimax/ack/{commandId}` con { success: true/false, error?: string }.

- Latencia/fiabilidad: WebSocket no garantiza QoS; si se requiere fiabilidad, usar broker MQTT sobre WebSockets y usar `retain`/`last-will` o un topic de estado agregado periódicamente.

7. Reconexión y heartbeat
- Usar `ping/pong` nativo del WebSocket o enviar `topic: "avimax/heartbeat"` cada T segundos.
- En caso de desconexión: reconectar con backoff exponencial (ej. 500ms, 1s, 2s, 4s, máximo 30s).
- Al reconectar: solicitar estado completo (ver paso 6.3).

8. Seguridad
- Siempre WSS (TLS).
- Autenticación: token JWT enviado en el `Sec-WebSocket-Protocol` o como query param `?token=...` al abrir la conexión. Preferente: usar sec-websocket-protocol.
- Autorización: actor UI vs controller: solo controllers pueden publicar cambios origines `controller-*`; UI puede publicar `command` pero control backend debe validar antes de emitir estado real.
- Validar tamaño de mensajes, rate-limiting y saneamiento.

9. Retenciones, LWT y estado persistente
- Si el backend usa un broker con características (MQTT), usar messages retenidos (`retain`) para `avimax/actuators/state` para que nuevos clientes obtengan el último estado automáticamente.
- Last-Will: `avimax/controller/{id}/lwt` para indicar pérdida de controlador.

10. Ejemplos concretos
A. Notificación de encendido extractor 5 (E5):
```json
{
  "topic": "avimax/actuator/fan/5/state",
  "payload": {
    "type": "fan",
    "number": 5,
    "index": 4,
    "label": "E5",
    "state": true,
    "source": "controller-01",
    "timestamp": "2026-05-11T12:45:00.000Z"
  }
}
```

B. Estado agregado (post arranque o reconexión):
```json
{
  "topic": "avimax/actuators/state",
  "payload": {
    "fans": [true,true,false,true,false,true,true,false,true,true],
    "heaters": [true,false,true,false,true],
    "pumps": [false,true],
    "timestamp": "2026-05-11T12:45:01.000Z",
    "source": "controller-01"
  }
}
```

C. Comando desde UI para apagar E3:
```json
{
  "topic": "avimax/actuator/command",
  "payload": {
    "commandId": "cmd-789",
    "action": "set",
    "target": { "type": "fan", "index": 2 },
    "value": false,
    "origin": "ui-frontend",
    "timestamp": "2026-05-11T12:46:00.000Z"
  }
}
```

11. Contratos para la implementación (responsabilidades)
- `brokerAdapter`:
  - Abrir/cerrar conexión WSS.
  - Reintentos con backoff, envio de token si procede.
  - Emitir eventos `onOpen`, `onMessage(envelope)`, `onClose`, `onError`.
- `actuatorService`:
  - Parsear envelopes y normalizar a: `unitUpdate({ type, index, state, timestamp })` o `bulkUpdate({ fans, heaters, pumps, timestamp })`.
  - Exponer API: `start()`, `stop()`, `subscribe(callback)` (callback recibe normalized events).
- `ActuatorContext`:
  - Mantener arrays `fanStates[10]`, `heaterStates[5]`, `pumpStates[2]`.
  - Aplicar actualizaciones provenientes del `actuatorService`.
  - Exponer acciones `toggleFan(i)`, `setFanStates(array)`, etc., y opcionalmente `sendCommand(command)` que delega al `brokerAdapter`.

12. Notas de integración (para React)
- Al recibir `unitUpdate` use `setState(prev => prev.map((v,i) => i===index?state:v))` para conservar referencias.
- Preferir recibir `bulkUpdate` al reconectar para reemplazar arrays de forma atómica.
- Evitar actualizar estado React con mensajes de alta frecuencia sin throttle; usar `requestAnimationFrame` o `debounce` si se reciben ráfagas (aunque el estado de encendido/apagado suele ser esporádico).

13. Checklist para entrega
- [ ] Implementar `src/services/brokerAdapter.js` (WSS + backoff + auth)
- [ ] Implementar `src/services/actuatorService.js` (normalización y eventos)
- [ ] Crear `src/contexts/ActuatorContext.jsx` y `src/hooks/useActuators.js`
- [ ] Refactor mínimo en `src/components/simulador3d/Simulador3DCanvas.jsx` para consumir contexto
- [ ] Tests básicos (mocks de WS)

14. Preguntas abiertas
- ¿Deseas soportar comandos desde el UI (control remoto), o solo recepción de estado? (Actualmente especificado como opcional)
- ¿Prefieres autenticación JWT en `Sec-WebSocket-Protocol` o query param? Recomiendo `Sec-WebSocket-Protocol`.

---
Documento generado por: equipo de integración AviMax — especificación para desarrolladores front/backend. Si quieres, implemento ahora los archivos base (`brokerAdapter`, `actuatorService`, `ActuatorContext`) usando este contrato.
