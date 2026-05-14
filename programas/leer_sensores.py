#!/usr/bin/env python3
import json
import time
import signal
import sys
from datetime import datetime

from pymodbus.client import ModbusSerialClient

# MQTT es opcional por ahora
try:
    import paho.mqtt.client as mqtt
except ImportError:
    mqtt = None


# =========================
# CONFIGURACION GENERAL
# =========================
SERIAL_PORT = "/dev/ttyUSB0"
BAUDRATE = 9600
BYTESIZE = 8
PARITY = "N"
STOPBITS = 1
TIMEOUT = 1
POLL_INTERVAL = 5  # segundos

# Activa esto cuando ya quieras publicar al broker
ENABLE_MQTT = True

MQTT_BROKER = "localhost"   # Broker MQTT local
MQTT_PORT = 1883
MQTT_TOPIC = "avicola/galpon1/lecturas"
MQTT_CLIENT_ID = "raspi5-lector-01"

GATEWAY_ID = "raspi5-galpon-01"


# =========================
# SENSORES
# =========================
# Base para XY-MD03 (temperatura + humedad)
# En tu caso ya habías trabajado con ese sensor.
#
# Ojo:
# PyModbus maneja direcciones base 0. Si el manual del sensor numera desde 1,
# a veces el registro "1" en el manual es address=0 en el código.
# Aquí dejo address=1 porque es una configuración común en ejemplos de XY-MD03,
# pero si te devuelve valores corridos o error, prueba con address=0.
SENSORS = [
    {
        "name": "ambiente_1",
        "type": "xy_md03",
        "device_id": 1,
        "address": 1,
        "function": "input"
    },

    # Ejemplo futuro para otro sensor Modbus genérico:
    # {
    #     "name": "co2_1",
    #     "type": "generic_u16",
    #     "device_id": 2,
    #     "address": 0,
    #     "function": "holding",
    #     "field": "co2_ppm",
    #     "scale": 1.0,
    #     "signed": False,
    #     "unit": "ppm"
    # },
]


running = True
mqtt_client = None


def stop_program(signum, frame):
    global running
    print("\nCerrando programa...")
    running = False


signal.signal(signal.SIGINT, stop_program)
signal.signal(signal.SIGTERM, stop_program)


def to_signed_16(value: int) -> int:
    """Convierte un entero de 16 bits sin signo a signo."""
    return value - 65536 if value > 32767 else value


def create_modbus_client():
    return ModbusSerialClient(
        port=SERIAL_PORT,
        baudrate=BAUDRATE,
        bytesize=BYTESIZE,
        parity=PARITY,
        stopbits=STOPBITS,
        timeout=TIMEOUT
    )


# =========================
# MQTT
# =========================
def on_connect(client, userdata, flags, reason_code, properties):
    print(f"[MQTT] Conectado al broker. Código: {reason_code}")


def on_disconnect(client, userdata, disconnect_flags, reason_code, properties):
    print(f"[MQTT] Desconectado. Código: {reason_code}")


def create_mqtt_client():
    if mqtt is None:
        raise RuntimeError("No está instalada la librería paho-mqtt.")

    client = mqtt.Client(
        callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
        client_id=MQTT_CLIENT_ID
    )
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    client.connect(MQTT_BROKER, MQTT_PORT, 60)
    client.loop_start()
    return client


# =========================
# LECTURA DE SENSORES
# =========================
def read_registers(client, function: str, address: int, count: int, device_id: int):
    if function == "input":
        return client.read_input_registers(
            address=address,
            count=count,
            device_id=device_id
        )
    elif function == "holding":
        return client.read_holding_registers(
            address=address,
            count=count,
            device_id=device_id
        )
    else:
        raise ValueError(f"Función Modbus no válida: {function}")


def read_xy_md03(client, sensor_cfg: dict) -> dict:
    """
    Lee 2 registros:
    - temperatura
    - humedad
    Ajusta la escala típica /10.
    """
    rr = read_registers(
        client=client,
        function=sensor_cfg["function"],
        address=sensor_cfg["address"],
        count=2,
        device_id=sensor_cfg["device_id"]
    )

    if rr.isError():
        raise RuntimeError(f"Respuesta Modbus con error: {rr}")

    regs = rr.registers
    if len(regs) < 2:
        raise RuntimeError("No llegaron suficientes registros para XY-MD03.")

    temp_raw = to_signed_16(regs[0])
    hum_raw = regs[1]

    temperatura = temp_raw / 10.0
    humedad = hum_raw / 10.0

    return {
        "sensor": sensor_cfg["name"],
        "device_id": sensor_cfg["device_id"],
        "modelo": "XY-MD03",
        "temperatura_c": temperatura,
        "humedad_relativa": humedad,
        "unidad_temperatura": "°C",
        "unidad_humedad": "%"
    }


def read_generic_u16(client, sensor_cfg: dict) -> dict:
    """
    Lector genérico para sensores que entregan un valor en un solo registro.
    """
    rr = read_registers(
        client=client,
        function=sensor_cfg["function"],
        address=sensor_cfg["address"],
        count=1,
        device_id=sensor_cfg["device_id"]
    )

    if rr.isError():
        raise RuntimeError(f"Respuesta Modbus con error: {rr}")

    value = rr.registers[0]

    if sensor_cfg.get("signed", False):
        value = to_signed_16(value)

    value = value * sensor_cfg.get("scale", 1.0)

    return {
        "sensor": sensor_cfg["name"],
        "device_id": sensor_cfg["device_id"],
        "modelo": "GENERIC",
        sensor_cfg["field"]: value,
        "unidad": sensor_cfg.get("unit", "")
    }


def read_sensor(client, sensor_cfg: dict) -> dict:
    sensor_type = sensor_cfg["type"]

    if sensor_type == "xy_md03":
        return read_xy_md03(client, sensor_cfg)
    elif sensor_type == "generic_u16":
        return read_generic_u16(client, sensor_cfg)
    else:
        raise ValueError(f"Tipo de sensor no soportado: {sensor_type}")


def main():
    global mqtt_client

    print("Iniciando lector de sensores...")
    print(f"Puerto serial: {SERIAL_PORT}")

    modbus_client = create_modbus_client()

    if not modbus_client.connect():
        print("No se pudo abrir la conexión Modbus serial.")
        sys.exit(1)

    print("Conexión Modbus abierta correctamente.")

    if ENABLE_MQTT:
        mqtt_client = create_mqtt_client()

    try:
        while running:
            timestamp = datetime.now().isoformat(timespec="seconds")
            lecturas = []

            for sensor in SENSORS:
                try:
                    data = read_sensor(modbus_client, sensor)
                    lecturas.append(data)
                except Exception as e:
                    lecturas.append({
                        "sensor": sensor["name"],
                        "device_id": sensor["device_id"],
                        "error": str(e)
                    })

            payload = {
                "gateway_id": GATEWAY_ID,
                "timestamp": timestamp,
                "readings": lecturas
            }

            # Mostrar en consola
            print("\n==============================")
            print(f"[{timestamp}] Lecturas recibidas")
            print(json.dumps(payload, indent=2, ensure_ascii=False))

            # Publicar a MQTT si ya está activado
            if ENABLE_MQTT and mqtt_client is not None:
                result = mqtt_client.publish(
                    MQTT_TOPIC,
                    json.dumps(payload, ensure_ascii=False),
                    qos=1
                )
                result.wait_for_publish()
                print(f"[MQTT] Publicado en topic: {MQTT_TOPIC}")

            time.sleep(POLL_INTERVAL)

    finally:
        if mqtt_client is not None:
            mqtt_client.loop_stop()
            mqtt_client.disconnect()

        modbus_client.close()
        print("Conexiones cerradas.")


if __name__ == "__main__":
    main()
