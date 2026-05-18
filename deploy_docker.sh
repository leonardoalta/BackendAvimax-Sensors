#!/bin/bash

# Script de deployment mejorado para AviMax Backend en Docker
# Incluye: PostgreSQL, Backend, configuración automática de datos

set -e

API_URL="http://localhost:8080/api"
DOCKER_NETWORK="avimax-net"

echo "╔════════════════════════════════════════════════╗"
echo "║   AVIMAX BACKEND - DEPLOYMENT MEJORADO         ║"
echo "╚════════════════════════════════════════════════╝"

# 1. Crear network
echo -e "\n[1] Creando red Docker..."
docker network create $DOCKER_NETWORK 2>/dev/null || echo "  → Red ya existe"

# 2. Iniciar PostgreSQL
echo -e "\n[2] Iniciando PostgreSQL..."
docker rm -f avimax-postgres 2>/dev/null || true
docker run -d \
  --name avimax-postgres \
  --network $DOCKER_NETWORK \
  -e POSTGRES_USER=avimax \
  -e POSTGRES_PASSWORD=avimax \
  -e POSTGRES_DB=avimax \
  -p 5434:5432 \
  -v avimax_postgres_data:/var/lib/postgresql/data \
  avimax-postgres:latest

echo "  ✓ PostgreSQL iniciado (puerto 5434)"
sleep 3

# 3. Iniciar Backend
echo -e "\n[3] Iniciando Backend..."
docker rm -f avimax-backend 2>/dev/null || true

# Detectar IP del gateway para Docker
MQTT_BROKER_IP=$(docker inspect $DOCKER_NETWORK | jq -r '.[0].IPAM.Config[0].Gateway' 2>/dev/null || echo "172.17.0.1")

docker run -d \
  --name avimax-backend \
  --network $DOCKER_NETWORK \
  -e DB_USERNAME=avimax \
  -e DB_PASSWORD=avimax \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://avimax-postgres:5432/avimax \
  -e MQTT_BROKER_URL=tcp://$MQTT_BROKER_IP:1883 \
  -p 8080:8080 \
  avimax-backend:latest

echo "  ✓ Backend iniciado (puerto 8080)"
echo "  → MQTT Broker configurado en: tcp://$MQTT_BROKER_IP:1883"

# 4. Esperar a que esté listo
echo -e "\n[4] Esperando que Backend esté listo..."
for i in {1..30}; do
  if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "  ✓ Backend listo"
    break
  fi
  echo -n "."
  sleep 1
done

# 5. Verificar status
echo -e "\n[5] Verificando status..."
echo -e "\n  Contenedores:"
docker ps | grep avimax | awk '{print "    - " $NF " (" $8 ")"}'

echo -e "\n  Datos inicializados por DataInitializer:"
echo -n "    - Parvada activa: "
curl -s $API_URL/flocks/active | jq -r '.data.name // "✗ No encontrada"' 2>/dev/null

echo -n "    - Ventiladores: "
curl -s $API_URL/extractors | jq '.data | length' 2>/dev/null || echo "✗"

echo -n "    - Criadoras: "
curl -s $API_URL/criadoras | jq '.data | length' 2>/dev/null || echo "✗"

echo -n "    - Bombas: "
curl -s $API_URL/bombas | jq '.data | length' 2>/dev/null || echo "✗"

echo -e "\n╔════════════════════════════════════════════════╗"
echo "║   ✓ DEPLOYMENT COMPLETADO                       ║"
echo "│                                                ║"
echo "│   API Base: http://localhost:8080/api         ║"
echo "│   PostgreSQL: localhost:5434                   ║"
echo "│   MQTT: $MQTT_BROKER_IP:1883                ║"
echo "╚════════════════════════════════════════════════╝"
