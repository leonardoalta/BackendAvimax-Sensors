# AviMax - Guía de Integración Frontend para Alarmas

Este documento define únicamente la integración por REST API para el frontend.

El módulo de alarmas:

- permite crear y administrar reglas
- lista alarmas activas e historial
- expone eventos detallados por alarma
- permite reconocer y cerrar alarmas
- **no controla actuadores**

---

## 1. Base URL

```text
http://localhost:8080/api/alarms
```

---

## 2. Estados y eventos soportados

### Estados de alarma

- `activa`
- `reconocida`
- `resuelta`
- `cerrada`

### Tipos de evento

- `ALARMA_ACTIVADA`
- `ALARMA_RECONOCIDA`
- `ALARMA_RESUELTA`
- `ALARMA_CERRADA`

### Reglas de negocio importantes

- Si ya existe una alarma activa para la misma regla, **no se crea otra**.
- El frontend **no** debe generar alarmas manualmente.
- El frontend solo debe consumir reglas, alarmas e historial, y opcionalmente publicar acciones de reconocer/cerrar.

---

## 3. Modelos de datos

### 3.1. Regla de alarma

Respuesta del backend:

```json
{
  "idRegla": 1,
  "nombre": "Amoniaco crítico",
  "variableMonitoreada": "amoniaco",
  "condicion": "mayor_igual",
  "umbral": 25,
  "unidad": "ppm",
  "tiempoMinimoSegundos": 120,
  "severidad": "critica",
  "mensaje": "Nivel crítico de amoniaco detectado en el galpón.",
  "activa": true,
  "fechaCreacion": "2026-05-13T22:00:09.55902639-06:00",
  "fechaActualizacion": "2026-05-13T22:00:09.55902639-06:00"
}
```

### 3.2. Alarma generada

Respuesta del backend:

```json
{
  "idAlarma": 1,
  "idRegla": 1,
  "nombreRegla": "Amoniaco crítico",
  "variable": "amoniaco",
  "valorDetectado": 26.5,
  "umbral": 25,
  "unidad": "ppm",
  "condicion": "mayor_igual",
  "severidad": "critica",
  "mensaje": "Nivel crítico de amoniaco detectado en el galpón.",
  "estado": "activa",
  "fechaActivacion": "2026-05-13T22:30:00Z",
  "fechaReconocimiento": null,
  "fechaResolucion": null,
  "fechaCierre": null
}
```

### 3.3. Evento de alarma

Respuesta del backend:

```json
{
  "idEvento": 1,
  "idAlarma": 1,
  "tipoEvento": "ALARMA_ACTIVADA",
  "estadoAnterior": null,
  "estadoNuevo": "activa",
  "descripcion": "Condición de alarma cumplida durante el tiempo mínimo configurado",
  "fechaEvento": "2026-05-13T22:30:00Z"
}
```

---

## 4. Catálogos de valores

### Variable monitoreada

- `temperatura`
- `humedad`
- `amoniaco`

### Condición

- `mayor`
- `mayor_igual`
- `menor`
- `menor_igual`
- `igual`

### Severidad

- `baja`
- `media`
- `alta`
- `critica`

### Unidad

- `°C`
- `%`
- `ppm`

---

## 5. Endpoints REST

### 5.1. Crear regla

**POST** `/api/alarms/rules`

#### Body

```json
{
  "nombre": "Amoniaco crítico",
  "variable": "AMONIACO",
  "condicion": "MAYOR_IGUAL",
  "umbral": 25,
  "unidad": "ppm",
  "tiempoMinimoSegundos": 120,
  "severidad": "CRITICA",
  "mensaje": "Nivel crítico de amoniaco detectado en el galpón.",
  "activa": true
}
```

#### Respuesta

`201 Created`

```json
{
  "idRegla": 1,
  "nombre": "Amoniaco crítico",
  "variableMonitoreada": "amoniaco",
  "condicion": "mayor_igual",
  "umbral": 25,
  "unidad": "ppm",
  "tiempoMinimoSegundos": 120,
  "severidad": "critica",
  "mensaje": "Nivel crítico de amoniaco detectado en el galpón.",
  "activa": true,
  "fechaCreacion": "2026-05-13T22:00:09.55902639-06:00",
  "fechaActualizacion": "2026-05-13T22:00:09.55902639-06:00"
}
```

---

### 5.2. Listar reglas

**GET** `/api/alarms/rules`

#### Respuesta

```json
[
  {
    "idRegla": 1,
    "nombre": "Amoniaco crítico",
    "variableMonitoreada": "amoniaco",
    "condicion": "mayor_igual",
    "umbral": 25,
    "unidad": "ppm",
    "tiempoMinimoSegundos": 120,
    "severidad": "critica",
    "mensaje": "Nivel crítico de amoniaco detectado en el galpón.",
    "activa": true,
    "fechaCreacion": "2026-05-13T22:00:09.55902639-06:00",
    "fechaActualizacion": "2026-05-13T22:00:09.55902639-06:00"
  }
]
```

---

### 5.3. Editar regla

**PUT** `/api/alarms/rules/{ruleId}`

#### Body

```json
{
  "nombre": "Amoniaco crítico actualizado",
  "variable": "AMONIACO",
  "condicion": "MAYOR_IGUAL",
  "umbral": 30,
  "unidad": "ppm",
  "tiempoMinimoSegundos": 180,
  "severidad": "CRITICA",
  "mensaje": "Nivel crítico actualizado."
}
```

#### Respuesta

`200 OK`

```json
{
  "idRegla": 1,
  "nombre": "Amoniaco crítico actualizado",
  "variableMonitoreada": "amoniaco",
  "condicion": "mayor_igual",
  "umbral": 30,
  "unidad": "ppm",
  "tiempoMinimoSegundos": 180,
  "severidad": "critica",
  "mensaje": "Nivel crítico actualizado.",
  "activa": true,
  "fechaCreacion": "2026-05-13T22:00:09.55902639-06:00",
  "fechaActualizacion": "2026-05-13T22:05:00.00000000-06:00"
}
```

---

### 5.4. Activar o desactivar regla

**PATCH** `/api/alarms/rules/{ruleId}/active`

#### Body

```json
{
  "activa": false
}
```

#### Respuesta

`200 OK`

```json
{
  "idRegla": 1,
  "nombre": "Amoniaco crítico",
  "variableMonitoreada": "amoniaco",
  "condicion": "mayor_igual",
  "umbral": 25,
  "unidad": "ppm",
  "tiempoMinimoSegundos": 120,
  "severidad": "critica",
  "mensaje": "Nivel crítico de amoniaco detectado en el galpón.",
  "activa": false,
  "fechaCreacion": "2026-05-13T22:00:09.55902639-06:00",
  "fechaActualizacion": "2026-05-13T22:06:00.00000000-06:00"
}
```

---

### 5.5. Consultar alarmas activas

**GET** `/api/alarms/active`

Devuelve alarmas en estado:

- `activa`
- `reconocida`

#### Uso frontend

- panel de alertas activas
- badge rojo / contador
- notificaciones inmediatas

#### Respuesta

```json
[
  {
    "idAlarma": 1,
    "idRegla": 1,
    "nombreRegla": "Amoniaco crítico",
    "variable": "amoniaco",
    "valorDetectado": 26.5,
    "umbral": 25,
    "unidad": "ppm",
    "condicion": "mayor_igual",
    "severidad": "critica",
    "mensaje": "Nivel crítico de amoniaco detectado en el galpón.",
    "estado": "activa",
    "fechaActivacion": "2026-05-13T22:30:00Z",
    "fechaReconocimiento": null,
    "fechaResolucion": null,
    "fechaCierre": null
  }
]
```

---

### 5.6. Consultar historial de alarmas

**GET** `/api/alarms/history`

Devuelve todas las alarmas creadas, ordenadas por activación descendente.

#### Uso frontend

- pantalla de historial
- filtros por estado, severidad, regla, fecha
- auditoría de eventos

#### Respuesta

```json
[
  {
    "idAlarma": 1,
    "idRegla": 1,
    "nombreRegla": "Amoniaco crítico",
    "variable": "amoniaco",
    "valorDetectado": 26.5,
    "umbral": 25,
    "unidad": "ppm",
    "condicion": "mayor_igual",
    "severidad": "critica",
    "mensaje": "Nivel crítico de amoniaco detectado en el galpón.",
    "estado": "resuelta",
    "fechaActivacion": "2026-05-13T22:30:00Z",
    "fechaReconocimiento": null,
    "fechaResolucion": "2026-05-13T22:45:00Z",
    "fechaCierre": null
  }
]
```

---

### 5.7. Consultar eventos de una alarma

**GET** `/api/alarms/{alarmId}/events`

#### Uso frontend

- timeline de eventos
- detalle expandido de cada alarma
- trazabilidad completa

#### Respuesta

```json
[
  {
    "idEvento": 2,
    "idAlarma": 1,
    "tipoEvento": "ALARMA_RESUELTA",
    "estadoAnterior": "activa",
    "estadoNuevo": "resuelta",
    "descripcion": "La condición de alarma volvió a un rango normal.",
    "fechaEvento": "2026-05-13T22:45:00Z"
  },
  {
    "idEvento": 1,
    "idAlarma": 1,
    "tipoEvento": "ALARMA_ACTIVADA",
    "estadoAnterior": null,
    "estadoNuevo": "activa",
    "descripcion": "Condición de alarma cumplida durante el tiempo mínimo configurado",
    "fechaEvento": "2026-05-13T22:30:00Z"
  }
]
```

---

### 5.8. Reconocer alarma

**POST** `/api/alarms/{alarmId}/acknowledge`

#### Efecto

- cambia estado a `reconocida`
- registra evento `ALARMA_RECONOCIDA`
- publica MQTT `ALARMA_RECONOCIDA`

#### Respuesta

```json
{
  "idAlarma": 1,
  "idRegla": 1,
  "nombreRegla": "Amoniaco crítico",
  "variable": "amoniaco",
  "valorDetectado": 26.5,
  "umbral": 25,
  "unidad": "ppm",
  "condicion": "mayor_igual",
  "severidad": "critica",
  "mensaje": "Nivel crítico de amoniaco detectado en el galpón.",
  "estado": "reconocida",
  "fechaActivacion": "2026-05-13T22:30:00Z",
  "fechaReconocimiento": "2026-05-13T22:40:00Z",
  "fechaResolucion": null,
  "fechaCierre": null
}
```

---

### 5.9. Cerrar alarma

**POST** `/api/alarms/{alarmId}/close`

#### Efecto

- cambia estado a `cerrada`
- registra evento `ALARMA_CERRADA`
- publica MQTT `ALARMA_CERRADA`

#### Respuesta

```json
{
  "idAlarma": 1,
  "idRegla": 1,
  "nombreRegla": "Amoniaco crítico",
  "variable": "amoniaco",
  "valorDetectado": 26.5,
  "umbral": 25,
  "unidad": "ppm",
  "condicion": "mayor_igual",
  "severidad": "critica",
  "mensaje": "Nivel crítico de amoniaco detectado en el galpón.",
  "estado": "cerrada",
  "fechaActivacion": "2026-05-13T22:30:00Z",
  "fechaReconocimiento": "2026-05-13T22:40:00Z",
  "fechaResolucion": null,
  "fechaCierre": "2026-05-13T22:50:00Z"
}
```

---

## 6. Manejo de UI recomendado

### Pantalla 1: Reglas de alarma

Componentes:

- tabla de reglas
- botón crear regla
- modal editar regla
- switch activar/desactivar

Campos a mostrar:

- nombre
- variable
- condición
- umbral
- unidad
- tiempo mínimo
- severidad
- estado activo
- fecha creación

### Pantalla 2: Alarmas activas

Componentes:

- tarjetas o tabla
- color por severidad
- badge de estado
- botón reconocer
- botón cerrar
- detalle de alarma

### Pantalla 3: Historial

Componentes:

- tabla con filtros
- exportación si se desea
- panel lateral o modal con eventos

### Pantalla 4: Detalle de alarma

Mostrar:

- datos de la alarma
- timeline de eventos
- estado actual
- fechas de activación / reconocimiento / resolución / cierre

---

## 7. Manejo de errores

Posibles respuestas del backend:

- `400 Bad Request` → datos inválidos
- `404 Not Found` → regla o alarma inexistente
- `409 Conflict` → acción inválida para el estado actual
- `500 Internal Server Error` → error de backend

### Casos típicos

- reconocer una alarma que no está `activa` → `409`
- cerrar una alarma ya cerrada → `409`
- editar una regla inexistente → `404`

---

## 8. Recomendación de integración frontend

### Estado sugerido en UI

- `rules[]`
- `activeAlarms[]`
- `history[]`
- `selectedAlarmEvents[]`
- `lastAlarmEvent`

### Estrategia recomendada

1. Cargar reglas al iniciar.
2. Cargar alarmas activas.
3. Cargar historial solo cuando el usuario entre a esa pantalla.
4. Suscribirse al tópico MQTT de alertas para refrescar automáticamente.
5. Tras recibir `ALARMA_ACTIVADA` o `ALARMA_RESUELTA`, refrescar alarmas activas e historial.

---

## 9. Ejemplo de contrato de componentes frontend

### Componente `AlarmRulesTable`

- consume `GET /api/alarms/rules`
- permite crear/editar/activar/desactivar reglas

### Componente `ActiveAlarmsPanel`

- consume `GET /api/alarms/active`
- escucha MQTT `avimax/galpon1/alertas`
- refresca lista al llegar eventos

### Componente `AlarmHistoryTable`

- consume `GET /api/alarms/history`
- permite abrir detalle por alarma

### Componente `AlarmEventTimeline`

- consume `GET /api/alarms/{alarmId}/events`
- muestra orden cronológico descendente

---

## 10. Notas finales

- Todas las fechas vienen en formato ISO-8601.
- Los strings de catálogo se entregan en minúsculas en la API de respuestas.
- El frontend no debe asumir que una alarma activa se repite en cada lectura: solo hay una por regla mientras siga activa.
- El módulo está separado del control de ventiladores, criadoras y bombas.

---

## 11. Resumen corto para integradores

- Reglas: `GET/POST/PUT/PATCH /api/alarms/rules...`
- Alarmas activas: `GET /api/alarms/active`
- Historial: `GET /api/alarms/history`
- Eventos de una alarma: `GET /api/alarms/{alarmId}/events`
- Acciones: `POST /api/alarms/{alarmId}/acknowledge` y `POST /api/alarms/{alarmId}/close`

