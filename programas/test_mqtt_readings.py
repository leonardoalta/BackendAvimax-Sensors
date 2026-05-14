#!/usr/bin/env python3
"""
Script de prueba para verificar que la suscripción MQTT funciona correctamente.
Simula lecturas de sensores y verifica que se guarden en la base de datos.
"""

import json
import time
import paho.mqtt.client as mqtt
import requests
from datetime import datetime

# Configuración
MQTT_BROKER = "localhost"
MQTT_PORT = 1883
MQTT_TOPIC = "avicola/galpon1/lecturas"
MQTT_CLIENT_ID = "test-lector-01"
BACKEND_API = "http://localhost:8080/api/readings/recent"
GATEWAY_ID = "raspi5-galpon-01"

# Variables de estado
mqtt_connected = False
messages_sent = 0

def on_connect(client, userdata, flags, rc, properties=None):
    global mqtt_connected
    mqtt_connected = (rc == 0)
    if mqtt_connected:
        print(f"✓ Conectado a MQTT broker en {MQTT_BROKER}:{MQTT_PORT}")
    else:
        print(f"✗ Error al conectar al MQTT broker. Código: {rc}")

def on_disconnect(client, userdata, disconnect_flags, rc, properties=None):
    global mqtt_connected
    mqtt_connected = False
    print(f"Desconectado del MQTT broker. Código: {rc}")

def publish_test_reading(client, index, temperature, humidity, nh3=0.0):
    """Publica un mensaje de lectura de sensor al MQTT."""
    global messages_sent
    
    payload = {
        "gateway_id": GATEWAY_ID,
        "timestamp": datetime.now().isoformat(),
        "readings": [
            {
                "sensor": "ambiente_1",
                "device_id": 1,
                "modelo": "XY-MD03",
                "temperatura_c": temperature,
                "humedad_relativa": humidity,
                "nh3_ppm": nh3,
                "unidad_temperatura": "°C",
                "unidad_humedad": "%"
            }
        ]
    }
    
    result = client.publish(
        MQTT_TOPIC,
        json.dumps(payload, ensure_ascii=False),
        qos=1
    )
    result.wait_for_publish(timeout=5)
    messages_sent += 1
    print(f"📤 Mensaje {index} publicado: {temperature}°C, {humidity}% HR, {nh3} ppm NH3")
    return result.rc == 0

def check_backend_readings(expected_count=None):
    """Verifica que las lecturas se hayan guardado en el backend."""
    try:
        response = requests.get(BACKEND_API, timeout=5)
        if response.status_code == 200:
            readings = response.json()
            print(f"✓ Backend respondió con {len(readings)} lecturas recientes")
            if readings:
                latest = readings[0]
                print(f"  📊 Última lectura: {latest['temperatureC']}°C, {latest['humidityPercent']}% HR")
            return len(readings)
        else:
            print(f"✗ Error del backend: código {response.status_code}")
            return 0
    except requests.exceptions.ConnectionError:
        print("✗ No se puede conectar al backend (http://localhost:8080)")
        return 0
    except Exception as e:
        print(f"✗ Error verificando backend: {e}")
        return 0

def main():
    global mqtt_connected
    
    print("=" * 60)
    print("TEST DE SUSCRIPCIÓN MQTT DE LECTURAS DE SENSORES")
    print("=" * 60)
    print()
    
    # Crear cliente MQTT
    client = mqtt.Client(
        callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
        client_id=MQTT_CLIENT_ID
    )
    
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    
    # Conectar al MQTT
    print(f"Conectando a MQTT broker en {MQTT_BROKER}:{MQTT_PORT}...")
    try:
        client.connect(MQTT_BROKER, MQTT_PORT, 60)
        client.loop_start()
    except Exception as e:
        print(f"✗ Error conectando a MQTT: {e}")
        return
    
    # Esperar a que se conecte
    wait_time = 0
    while not mqtt_connected and wait_time < 10:
        time.sleep(0.5)
        wait_time += 0.5
    
    if not mqtt_connected:
        print("\n✗ No se pudo conectar al MQTT broker después de 10 segundos")
        return
    
    time.sleep(1)
    
    # Publicar mensajes de prueba
    print("\nPublicando mensajes de prueba...")
    print("-" * 60)
    
    test_cases = [
        (28.5, 65.0, 5.2, "Condiciones normales"),
        (30.2, 72.3, 8.1, "Temperatura elevada"),
        (26.1, 58.5, 2.0, "Temperatura baja"),
        (29.0, 68.0, 6.5, "Dentro de rango normal"),
    ]
    
    initial_count = check_backend_readings()
    time.sleep(1)
    print()
    
    for i, (temp, humidity, nh3, description) in enumerate(test_cases, 1):
        print(f"\n[Caso {i}] {description}")
        success = publish_test_reading(client, i, temp, humidity, nh3)
        if success:
            print("  ✓ Publicado exitosamente")
        else:
            print("  ✗ Error al publicar")
        time.sleep(2)  # Esperar a que se procese
    
    # Verificar que se guardaron en el backend
    print("\n" + "-" * 60)
    print("Verificando que las lecturas se guardaron en el backend...")
    time.sleep(2)
    
    final_count = check_backend_readings()
    new_readings = final_count - initial_count
    
    print()
    print("=" * 60)
    print("RESULTADO DEL TEST")
    print("=" * 60)
    print(f"Mensajes MQTT publicados: {messages_sent}")
    print(f"Nuevas lecturas en backend: {new_readings}")
    
    if new_readings > 0:
        print("✓ TEST EXITOSO - La suscripción MQTT funciona correctamente")
    else:
        print("✗ TEST FALLIDO - No se detectaron nuevas lecturas en backend")
        print("\nPosibles causas:")
        print("1. El MqttIngestionService no está suscrito al topic correcto")
        print("2. El backend no está conectado al MQTT broker")
        print("3. Hay un error procesando los mensajes MQTT")
    
    # Limpiar
    client.loop_stop()
    client.disconnect()
    print()

if __name__ == "__main__":
    main()
