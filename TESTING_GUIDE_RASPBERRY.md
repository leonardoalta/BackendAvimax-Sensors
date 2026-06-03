# AviMax — Guía Práctica de Pruebas en Raspberry Pi

Documento con pasos, comandos y cómo documentar resultados para cada tipo de prueba.

---

## 1) Prueba de configuración del entorno

**Objetivo:** Verificar que todos los servicios estén activos, puertos mapeados y accesibles.

### Pasos:
```bash
# 1a) Verificar contenedores corriendo
sudo docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Ports}}\t{{.Status}}"

# 1b) Esperar que vea: avimax-backend, avimax-postgres y broker (nanomq/mosquitto)
# STATUS debe decir "Up"

# 1c) Verificar puertos escuchando
sudo ss -ltnp | grep -E ':(8080|5434|1883|9001)'

# 1d) Salida esperada:
# 0.0.0.0:8080  (backend)
# 0.0.0.0:5434  (postgres)
# 0.0.0.0:1883  (MQTT broker TCP)
# 0.0.0.0:9001  (MQTT broker WebSocket - opcional)

# 1e) Health del backend
curl -sS http://localhost:8080/actuator/health | jq .

# Esperado: { "status": "UP", "components": {...} }

# 1f) Verificar conectividad MQTT
mosquitto_sub -h 127.0.0.1 -p 1883 -t '$SYS/#' -C 1

# Si ves línea de $SYS → broker está vivo
```

### Documentar:
```
Fecha: [hoy]
Hora inicio: [hora]
- [ ] docker ps muestra 3 contenedores UP
- [ ] ss -ltnp muestra 4 puertos (8080, 5434, 1883, 9001 opt)
- [ ] curl /actuator/health → "UP"
- [ ] mosquitto_sub conecta a broker
Resultado: ✓ PASÓ / ✗ FALLÓ
Observaciones: [notas]
```

---

## 2) Prueba de operación sin conexión

**Objetivo:** Verificar que el sistema funciona localmente sin Internet.

### Pasos:
```bash
# 2a) Desconectar Internet (manual o con iptables)
# Opción 1: desconectar WiFi/Ethernet físicamente
# Opción 2: bloquear con iptables:
#   sudo iptables -I OUTPUT -d 8.8.8.8 -j DROP  (bloquea DNS/Internet)

# 2b) Verificar navegador accede a frontend local
curl -sS http://localhost:3000 | head -20
# O abrir en navegador: http://localhost:3000

# 2c) Verificar backend funciona sin Internet
curl -sS http://localhost:8080/actuator/health | jq .

# 2d) Verificar DB responde
sudo docker exec -it avimax-postgres psql -U avimax -d avimax -c "SELECT NOW();"

# 2e) Publicar lectura MQTT local (simular sensor)
mosquitto_pub -h 127.0.0.1 -p 1883 -t avicola/galpon1/lecturas -m '{"gateway_id":"test","timestamp":"2026-06-01T12:00:00","readings":[{"sensor":"ambiente_1","temperatura_c":28.5,"humedad_relativa":60.0}]}'

# 2f) Verificar lectura guardada en BD
sudo docker exec -it avimax-postgres psql -U avimax -d avimax -c "SELECT COUNT(*) FROM sensor_readings WHERE created_at > NOW() - INTERVAL '1 minute';"

# Esperado: count >= 1

# 2g) Reconectar Internet (opcional)
# sudo iptables -D OUTPUT -d 8.8.8.8 -j DROP
```

### Documentar:
```
Fecha: [hoy]
Hora desconexión: [hora]
Duración: [minutos]
- [ ] Frontend accesible (http://localhost:3000)
- [ ] Backend health OK
- [ ] BD responde
- [ ] Publicar MQTT → lectura guardada en BD
- [ ] Dashboar.d muestra datos
Resultado: ✓ PASÓ / ✗ FALLÓ
Observaciones: [comportamiento sin Internet]
```

---

## 3) Prueba de usabilidad

**Objetivo:** Una persona puede usar el sistema sin capacitación avanzada.

### Pasos:
```bash
# 3a) Acceder dashboard (simulación usuario)
# Abrir navegador: http://localhost:3000

# 3b) Checklist de usabilidad:
# - [ ] Interfaz carga sin errores
# - [ ] Puedo ver sensores (temperatura, humedad, amoniaco)
# - [ ] Puedo identificar claramente las alarmas activas
# - [ ] Hay botones visibles para apagar/encender actuadores
# - [ ] El menú principal está organizado lógicamente
# - [ ] Los gráficos son legibles
# - [ ] Las unidades están indicadas (°C, %, ppm, etc.)

# 3c) Registrar datos (ejemplo: crear lectura manual)
# Si existe endpoint POST para manual entry:
curl -X POST http://localhost:8080/api/readings \
  -H "Content-Type: application/json" \
  -d '{
    "gatewayId": "manual-test",
    "sensor": "demo",
    "temperatura_c": 27.3,
    "humedad_relativa": 65.5,
    "nh3_ppm": 10.2
  }' | jq .

# 3d) Verificar que el dato aparece en el dashboard en < 5 segundos
```

### Documentar:
```
Fecha: [hoy]
Evaluador: [nombre]
Dispositivo: [PC/tablet/mobile]
- Usuario pudo: acceder ✓✗, ver sensores ✓✗, identificar alarmas ✓✗, controlar actuadores ✓✗
- Interfaz clara: Sí/No
- Unidades visibles: Sí/No
- Tiempo para comprender: [segundos]
Resultado: ✓ PASÓ / ✗ FALLÓ
Comentarios de usabilidad: [feedback]
```

---

## 4) Prueba de rendimiento básico

**Objetivo:** Medir tiempos de respuesta, CPU, memoria bajo carga MQTT.

### Pasos:
```bash
# 4a) Baseline: recursos sin carga
# Terminal 1:
watch -n 1 'sudo docker stats avimax-backend avimax-postgres --no-stream'

# 4b) Terminal 2: obtener tiempos de respuesta (latencia API)
for i in {1..10}; do
  time curl -sS http://localhost:8080/api/readings/latest > /dev/null
done
# Notar tiempo en "real"

# 4c) Via MQTT: simular 100 lecturas en 10 segundos
# Terminal 3: suscribirse (para verificar que llegan)
mosquitto_sub -h 127.0.0.1 -p 1883 -t avicola/galpon1/lecturas -v &

# Terminal 2: publicar 100 lecturas (ráfaga)
for i in {1..100}; do
  mosquitto_pub -h 127.0.0.1 -p 1883 -t avicola/galpon1/lecturas \
    -m "{\"gateway_id\":\"stress-$i\",\"timestamp\":\"2026-06-01T12:00:00\",\"readings\":[{\"sensor\":\"s$i\",\"temperatura_c\":$((20+RANDOM%15)).$((RANDOM%100)),\"humedad_relativa\":$((40+RANDOM%40)).$((RANDOM%100))}]}" &
  sleep 0.1
done
wait

# 4d) Medir respuesta a query grande
time curl -sS "http://localhost:8080/api/readings?start=2026-05-01T00:00:00Z&end=2026-06-01T00:00:00Z&limit=10000" > /tmp/large_response.json

# 4e) Ver si hubo drops/errores en logs
sudo docker logs --tail 100 avimax-backend | grep -i error

# 4f) Contar lecturas guardadas post-stress
sudo docker exec -it avimax-postgres psql -U avimax -d avimax -c "SELECT COUNT(*) FROM sensor_readings WHERE created_at > NOW() - INTERVAL '5 minutes';"
```

### Documentar:
```
Fecha: [hoy]
Sistema: Raspberry Pi [modelo] / Specs: [CPU cores, RAM]

Baseline (sin carga):
- CPU: [%]
- Memoria: [MB]
- Latencia API (curl /api/readings/latest): [ms promedio]

Después de 100 lecturas MQTT (10s):
- CPU pico: [%]
- Memoria pico: [MB]
- Latencia API: [ms]
- Lecturas perdidas: [N]
- Errores en logs: [Sí/No]

Query grande (10k registros):
- Tiempo: [ms]
- Tamaño respuesta: [MB]

Resultado: ✓ ACEPTABLE / ⚠ DEGRADADO / ✗ CRÍTICO
Notas: [comportamiento bajo carga]
```

---

## 5) Prueba de estabilidad (24~48 horas)

**Objetivo:** Sistema mantiene operatividad sin caídas en tiempo prolongado.

### Pasos:
```bash
# 5a) Iniciar monitoreo continuo
# Terminal 1: health check cada 5 minutos
while true; do
  echo "[$(date)] Health: $(curl -s http://localhost:8080/actuator/health | jq -r '.status')"
  sleep 300
done > /tmp/stability_check.log &

# 5b) Simular lecturas periódicas (cada 10 min)
# Terminal 2: loop publicador MQTT
while true; do
  TEMP=$((20 + RANDOM % 15))
  HUM=$((40 + RANDOM % 40))
  NH3=$((RANDOM % 30))
  mosquitto_pub -h 127.0.0.1 -p 1883 -t avicola/galpon1/lecturas \
    -m "{\"gateway_id\":\"stability-test\",\"timestamp\":\"$(date -u +%Y-%m-%dT%H:%M:%S)\",\"readings\":[{\"sensor\":\"ambiente_1\",\"temperatura_c\":$TEMP.$((RANDOM%100)),\"humedad_relativa\":$HUM.$((RANDOM%100)),\"nh3_ppm\":$NH3}]}"
  sleep 600
done > /tmp/mqtt_stress.log &

# 5c) Monitoreo diario de recursos
watch -n 3600 'echo "[$(date)] CPU/MEM:" && sudo docker stats --no-stream' >> /tmp/resources.log &

# 5d) Tras 24-48 horas, analizar logs
echo "=== STABILITY REPORT ==="
echo "Health checks:"
grep "Health:" /tmp/stability_check.log | tail -5
echo ""
echo "MQTT publishes:"
wc -l /tmp/mqtt_stress.log
echo ""
echo "Backend logs (últimos 50 errores):"
sudo docker logs avimax-backend | grep -i error | tail -50
echo ""
echo "DB connectivity:"
sudo docker exec -it avimax-postgres psql -U avimax -d avimax -c "SELECT COUNT(*) FROM sensor_readings WHERE created_at > NOW() - INTERVAL '48 hours';"
```

### Documentar:
```
Fecha inicio: [hoy]
Fecha fin: [fecha + tiempo]
Duración: [48 horas]

Eventos registrados:
- Reinicios: [N]
- Caídas/restarts: [N]
- Errores de BD: [N]
- Errores de MQTT: [N]
- Errores de red: [N]

Health checks completados: [N%]
Lecturas MQTT exitosas: [N]
Lecturas en BD: [N]
Pérdida de datos: [N]

Resultado: ✓ ESTABLE / ⚠ INESTABLE / ✗ CRÍTICO FALLOS
Recomendaciones: [ajustes si aplica]
```

---

## 6) Prueba de integridad de datos

**Objetivo:** Datos MQTT → BD → Dashboard coinciden exactamente.

### Pasos:
```bash
# 6a) Generar lectura de prueba conocida
PAYLOAD='{"gateway_id":"integrity-test-001","timestamp":"2026-06-01T12:00:00Z","readings":[{"sensor":"probe_1","temperatura_c":25.5,"humedad_relativa":55.5,"nh3_ppm":8.5}]}'

# 6b) Publicar y registrar timestamp MQTT
echo "Publicando: $PAYLOAD"
TIME_SENT=$(date +%s%N)
mosquitto_pub -h 127.0.0.1 -p 1883 -t avicola/galpon1/lecturas -m "$PAYLOAD"
TIME_SENT_MS=$((TIME_SENT / 1000000))
echo "Sent at Unix ms: $TIME_SENT_MS"

# 6c) Esperar 2 segundos y verificar en BD
sleep 2
sudo docker exec -it avimax-postgres psql -U avimax -d avimax -c \
  "SELECT id, temperature_c, humidity_relativa, nh3_ppm, created_at FROM sensor_readings WHERE gateway_id='integrity-test-001' ORDER BY created_at DESC LIMIT 1;"

# 6d) Comparar:
# - Temperatura debe ser 25.5
# - Humedad debe ser 55.5
# - Amoniaco debe ser 8.5
# - created_at cercano a TIME_SENT (< 5 segundos diferencia)

# 6e) Verificar en dashboard/API
curl -s "http://localhost:8080/api/readings/latest?gateway=integrity-test-001&sensor=probe_1" | jq '.data | {temperatura_c, humedad_relativa, nh3_ppm}'

# 6f) Repetir 5-10 veces con valores diferentes
for i in {1..5}; do
  TEMP=$((20 + i))
  mosquitto_pub -h 127.0.0.1 -p 1883 -t avicola/galpon1/lecturas \
    -m "{\"gateway_id\":\"integrity-test-$i\",\"timestamp\":\"$(date -u +%Y-%m-%dT%H:%M:%S)Z\",\"readings\":[{\"sensor\":\"probe_$i\",\"temperatura_c\":$TEMP.0,\"humedad_relativa\":50.0,\"nh3_ppm\":5.0}]}"
  sleep 1
done

# Validar todos en BD
sudo docker exec -it avimax-postgres psql -U avimax -d avimax -c \
  "SELECT gateway_id, temperature_c FROM sensor_readings WHERE gateway_id LIKE 'integrity-test-%' ORDER BY created_at DESC LIMIT 10;"
```

### Documentar:
```
Fecha: [hoy]
Payload enviado (ejemplo):
{"gateway_id":"integrity-test-001","timestamp":"2026-06-01T12:00:00Z","readings":[{"sensor":"probe_1","temperatura_c":25.5,"humedad_relativa":55.5,"nh3_ppm":8.5}]}

Resultados por lectura:
| # | MQTT T | BD T | MQTT H | BD H | MQTT NH3 | BD NH3 | Latencia | Match |
|----|--------|------|--------|------|----------|--------|----------|-------|
| 1  | 25.5   | 25.5 | 55.5   | 55.5 | 8.5      | 8.5    | 2s       | ✓     |
| 2  | 21.0   | 21.0 | 50.0   | 50.0 | 5.0      | 5.0    | 1.5s     | ✓     |
| .. | ..     | ..   | ..     | ..   | ..       | ..     | ..       | ..    |

Resultado: ✓ TODO COINCIDE / ⚠ DISCREPANCIAS / ✗ PÉRDIDA DE DATOS
Observaciones: [diferencias si aplican]
```

---

## 7) Prueba de claridad visual

**Objetivo:** Interfaz es clara, legible, fácil de interpretar.

### Pasos:
```bash
# 7a) Captura de pantalla del dashboard principal
# Usar: Print Screen / Screenshot / gnome-screenshot
gnome-screenshot -f /tmp/dashboard_main_$(date +%s).png

# 7b) Checklist visual en dashboard:
# - [ ] Título legible y claro
# - [ ] Sensores (T, H, NH3) con valores grandes y unidades
# - [ ] Gráficas con eje X/Y etiquetados
# - [ ] Alarmas activas destacadas (color rojo/naranja)
# - [ ] Actuadores (ventiladores, criadoras) visibles
# - [ ] Botones ON/OFF claros
# - [ ] Sin texto truncado o superpuesto
# - [ ] Colores accesibles (contraste suficiente)
# - [ ] Responsive (probado en móvil/tablet)

# 7c) Captura de página de alarmas
# Navegar a /alarms o /history
gnome-screenshot -f /tmp/dashboard_alarms_$(date +%s).png

# 7d) Captura de gráfico histórico
gnome-screenshot -f /tmp/dashboard_history_$(date +%s).png

# 7e) Probar en navegador móvil (si disponible)
# Abrir http://192.168.1.88:3000 desde tablet/teléfono
```

### Documentar:
```
Fecha: [hoy]
Navegador(es) probado(s): Chrome [v], Firefox [v], Safari [v]
Dispositivos: PC, Tablet, Mobile

Dashboard principal:
- Legibilidad: ✓ Excelente / ◐ Aceptable / ✗ Pobre
- Organización: ✓ Claro / ◐ Confuso / ✗ Muy confuso
- Colores: ✓ Buen contraste / ✗ Difícil leer
- Responsive: ✓ Sí / ✗ No

Página de alarmas:
- [mismos items]

Página de histórico:
- [mismos items]

Capturas: [archivos guardados]
Resultado: ✓ CLARA Y LEGIBLE / ⚠ MEJORABLE / ✗ CONFUSA
Sugerencias: [cambios recomendados]
```

---

## 8) Prueba de evidencia y trazabilidad

**Objetivo:** Documentar resultados de todas las pruebas para auditoría.

### Pasos:
```bash
# 8a) Crear directorio de evidencia
mkdir -p /tmp/avimax_test_evidence_$(date +%Y%m%d)
cd /tmp/avimax_test_evidence_$(date +%Y%m%d)

# 8b) Logs de servicios
sudo docker logs avimax-backend > backend_logs.txt 2>&1
sudo docker logs avimax-postgres > postgres_logs.txt 2>&1
sudo docker ps -a > containers_status.txt 2>&1

# 8c) Configuración sistema
uname -a > system_info.txt
df -h > disk_usage.txt
free -h > memory_info.txt
sudo docker network ls > networks.txt
sudo docker inspect -f '{{.Name}} {{.HostConfig.RestartPolicy.Name}}' $(sudo docker ps -aq) > restart_policies.txt

# 8d) Prueba de conectividad
echo "=== Connectivity Test ===" >> connectivity_test.txt
curl -sS http://localhost:8080/actuator/health >> connectivity_test.txt
mosquitto_sub -h 127.0.0.1 -p 1883 -t '$SYS/broker/version' -C 1 >> connectivity_test.txt

# 8e) Query de datos de ejemplo
sudo docker exec -it avimax-postgres psql -U avimax -d avimax -c \
  "SELECT COUNT(*) as total_readings, MAX(created_at) as last_reading FROM sensor_readings;" \
  > db_status.txt

# 8f) Resumen de pruebas (agregar manualmente)
cat > TEST_SUMMARY.md << 'EOF'
# AviMax Testing Summary
**Date:** [fecha]
**Raspberry Model:** [modelo]
**Tester:** [nombre]

## Test Results
| Test | Status | Duration | Notes |
|------|--------|----------|-------|
| Configuration | PASS | 5min | All services up |
| Offline Op | PASS | 30min | OK without internet |
| Usability | PASS | 20min | Clear interface |
| Performance | PASS | 15min | <100ms latency |
| Stability | PASS | 48h | 100% uptime |
| Data Integrity | PASS | 30min | All data matched |
| Visual Clarity | PASS | 20min | Good contrast |

## Issues Found
- [Issue 1] [Severity] [Impact]

## Recommendations
- [Rec 1]
- [Rec 2]
EOF

# 8g) Comprimir evidencia
cd ..
tar -czf avimax_test_evidence_$(date +%Y%m%d_%H%M%S).tar.gz avimax_test_evidence_$(date +%Y%m%d)

# 8h) Mostrar archivos
ls -lh /tmp/avimax_test_evidence_$(date +%Y%m%d)/
```

### Documentar:
```
Paquete de evidencia:
- /tmp/avimax_test_evidence_[YYYYMMDD]/
  ├── backend_logs.txt
  ├── postgres_logs.txt
  ├── containers_status.txt
  ├── system_info.txt
  ├── disk_usage.txt
  ├── memory_info.txt
  ├── connectivity_test.txt
  ├── db_status.txt
  ├── TEST_SUMMARY.md
  └── [capturas de pantalla PNG]

Archivo comprimido: avimax_test_evidence_[YYYYMMDD_HHMMSS].tar.gz

Completado: [Sí/No]
Almacenado en: [ruta]
```

---

## Resumen: Checklist de pruebas

```
Día [fecha]:
- [ ] 1. Configuración entorno ✓/✗ [comentario]
- [ ] 2. Operación offline ✓/✗ [comentario]
- [ ] 3. Usabilidad ✓/✗ [comentario]
- [ ] 4. Rendimiento básico ✓/✗ [comentario]
- [ ] 5. Estabilidad 24-48h ✓/✗ [comentario]
- [ ] 6. Integridad de datos ✓/✗ [comentario]
- [ ] 7. Claridad visual ✓/✗ [comentario]
- [ ] 8. Evidencia guardada ✓/✗ [ruta]

Resultado general: ✓ TODO PASÓ / ⚠ PASÓ CON NOTAS / ✗ FALLOS CRÍTICOS
```
