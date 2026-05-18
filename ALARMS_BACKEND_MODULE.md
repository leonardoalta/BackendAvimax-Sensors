# Módulo Backend de Alarmas - AviMax

## 1) Arquitectura del módulo

Arquitectura en capas (sin control de actuadores):

- **API REST**: gestión de reglas y ciclo de vida de alarmas.
- **Dominio Alarmas**: evaluación de reglas, anti-duplicación, transición de estados.
- **Persistencia**: reglas, alarmas, eventos e estado de evaluación por regla.
- **Integración MQTT**:
  - **Entrada**: el backend ya recibe lecturas ambientales desde MQTT.
  - **Salida**: el módulo publica eventos de alarma a `avimax/galpon1/alertas`.

Componentes implementados:

- `AlarmController`
- `AlarmService`
- `AlarmEvaluationService`
- `AlarmMqttPublisherService`
- Entidades: `AlarmRule`, `Alarm`, `AlarmEvent`, `AlarmRuleState`
- Repositorios: `AlarmRuleRepository`, `AlarmRepository`, `AlarmEventRepository`, `AlarmRuleStateRepository`

## 2) Flujo completo

1. Usuario crea/edita regla por REST.
2. Regla queda persistida en `alarm_rules`.
3. Llegan lecturas por MQTT y se guardan como `sensor_readings`.
4. `AlarmEvaluationService` consulta reglas activas.
5. Evalúa condición por regla (temperatura/humedad/amoniaco).
6. Si la condición se mantiene por `tiempo_minimo_segundos`, crea alarma.
7. Guarda alarma en `alarms` y evento `ALARMA_ACTIVADA` en `alarm_events`.
8. Publica evento MQTT a `avimax/galpon1/alertas`.
9. Dashboard/3D se suscriben al tópico.
10. Cuando vuelve a rango normal, se marca `RESUELTA` y publica `ALARMA_RESUELTA`.

## 3) Diseño SQL

Archivo: `backend-java/src/main/resources/db/migration/V11__alarms_module.sql`

Tablas:

- `alarm_rules`
- `alarms`
- `alarm_events`
- `alarm_rule_states`

## 4) Modelo lógico de entidades

- **AlarmRule** (configuración): variable, condición, umbral, severidad, mínimo tiempo.
- **Alarm** (instancia): snapshot de la regla al activar + estado actual.
- **AlarmEvent** (historial): transición de estados y descripción.
- **AlarmRuleState** (runtime): desde cuándo se cumple condición y último valor evaluado.

Relaciones:

- `AlarmRule 1..N Alarm`
- `Alarm 1..N AlarmEvent`
- `AlarmRule 1..1 AlarmRuleState`

## 5) Reglas de negocio

- Solo evalúa reglas `active = true`.
- Solo variables: `TEMPERATURA`, `HUMEDAD`, `AMONIACO`.
- Condiciones soportadas: `MAYOR`, `MAYOR_IGUAL`, `MENOR`, `MENOR_IGUAL`, `IGUAL`.
- Si existe alarma abierta (`ACTIVA` o `RECONOCIDA`) para la misma regla, **no crea otra**.
- Eventos MQTT solo en transiciones:
  - `ALARMA_ACTIVADA`
  - `ALARMA_RECONOCIDA`
  - `ALARMA_RESUELTA`
  - `ALARMA_CERRADA`

## 6) Endpoints REST

Base: `/api/alarms`

- `POST /rules` → crear regla
- `GET /rules` → listar reglas
- `PUT /rules/{ruleId}` → editar regla
- `PATCH /rules/{ruleId}/active` → activar/desactivar regla
- `GET /active` → alarmas activas (ACTIVA, RECONOCIDA)
- `GET /history` → historial de alarmas
- `POST /{alarmId}/acknowledge` → reconocer alarma
- `POST /{alarmId}/close` → cerrar alarma
- `GET /{alarmId}/events` → historial de eventos de una alarma

## 7) Lógica evaluación MQTT

Implementada en `AlarmEvaluationService`:

- Obtiene reglas activas.
- Extrae valor según variable.
- Evalúa condición vs umbral.
- Actualiza `AlarmRuleState` (`met_since`, `last_value`).
- Si se cumple tiempo mínimo y no hay alarma abierta: activa alarma.
- Si deja de cumplirse y hay alarma abierta: resuelve alarma.

## 8) Anti-duplicación

Implementado en `AlarmService.createActivationIfNeeded(...)`:

- Busca alarma abierta por `rule_id` y estados `{ACTIVA, RECONOCIDA}`.
- Si existe, no crea nueva.
- Resultado: evita spam de alarmas y eventos repetidos.

## 9) Publicación MQTT

Implementado en `AlarmMqttPublisherService`:

- Topic: `avimax/galpon1/alertas`
- Broker: el mismo de `app.mqtt.broker-url`
- Publica solo en transiciones (activada/reconocida/resuelta/cerrada)

## 10) Payload JSON (entrada/salida)

### Crear regla (entrada REST)

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

### MQTT `ALARMA_ACTIVADA`

```json
{
  "tipoEvento": "ALARMA_ACTIVADA",
  "idAlarma": 15,
  "idRegla": 3,
  "nombre": "Amoniaco crítico",
  "variable": "amoniaco",
  "valorDetectado": 26.5,
  "umbral": 25.0,
  "unidad": "ppm",
  "condicion": "mayor_igual",
  "severidad": "critica",
  "mensaje": "Nivel crítico de amoniaco detectado en el galpón.",
  "estado": "activa",
  "fechaHora": "2026-05-13T22:30:00Z"
}
```

### MQTT `ALARMA_RESUELTA`

```json
{
  "tipoEvento": "ALARMA_RESUELTA",
  "idAlarma": 15,
  "idRegla": 3,
  "nombre": "Amoniaco crítico",
  "variable": "amoniaco",
  "valorDetectado": 26.5,
  "umbral": 25.0,
  "unidad": "ppm",
  "condicion": "mayor_igual",
  "severidad": "critica",
  "mensaje": "La condición de alarma volvió a un rango normal.",
  "estado": "resuelta",
  "fechaHora": "2026-05-13T22:45:00Z"
}
```

## 11) Estructura de carpetas recomendada

En la base actual del proyecto:

- `controller/AlarmController.java`
- `service/AlarmService.java`
- `service/AlarmEvaluationService.java`
- `service/AlarmMqttPublisherService.java`
- `entity/Alarm*.java`
- `repository/Alarm*.java`
- `dto/*Alarm*.java`
- `db/migration/V11__alarms_module.sql`

## 12) Pseudocódigo implementable

```text
onSensorReading(reading):
  rules = activeRules()
  for each rule in rules:
    value = extract(reading, rule.variable)
    if value is null: continue

    met = compare(value, rule.condition, rule.threshold)
    state = findOrCreateRuleState(rule)

    if met:
      if state.conditionMet == false:
        state.metSince = reading.time
      state.conditionMet = true
      state.lastValue = value

      if noOpenAlarm(rule):
        if elapsed(state.metSince, reading.time) >= rule.minimumDurationSeconds:
          alarm = createAlarm(rule, value)
          saveEvent(alarm, ALARMA_ACTIVADA)
          publishMQTT(ALARMA_ACTIVADA)

    else:
      state.conditionMet = false
      state.metSince = null
      if openAlarm(rule):
        alarm.status = RESUELTA
        saveEvent(alarm, ALARMA_RESUELTA)
        publishMQTT(ALARMA_RESUELTA)
```

---

### Nota de integración

Este módulo no ejecuta comandos sobre ventiladores/criadoras/bombas. Solo detecta, registra y publica eventos de alarmas.
