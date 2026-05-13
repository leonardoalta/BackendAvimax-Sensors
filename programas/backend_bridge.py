from flask import Flask, jsonify
import threading
import json
import paho.mqtt.client as mqtt

# =========================
# CONFIG
# =========================
MQTT_BROKER = "localhost"
MQTT_PORT = 1883
MQTT_TOPIC = "avicola/galpon1/lecturas"
MQTT_CLIENT_ID = "backend-avimax-01"

HTTP_HOST = "0.0.0.0"
HTTP_PORT = 5000

# =========================
# ESTADO EN MEMORIA
# =========================
state_lock = threading.Lock()

dashboard_state = {
    "diaLote": 1,
    "temperatura": 0.0,
    "humedad": 0.0,
    "amoniaco": 0.0
}

ventilacion_state = []
criadoras_state = []
bombas_state = []

app = Flask(__name__)


# =========================
# MQTT
# =========================
def on_connect(client, userdata, flags, reason_code, properties=None):
    print(f"[MQTT] Conectado al broker con código: {reason_code}")
    client.subscribe(MQTT_TOPIC, qos=1)
    print(f"[MQTT] Suscrito a: {MQTT_TOPIC}")


def on_disconnect(client, userdata, disconnect_flags, reason_code, properties=None):
    print(f"[MQTT] Desconectado del broker. Código: {reason_code}")


def on_message(client, userdata, msg):
    global dashboard_state

    try:
        payload = msg.payload.decode("utf-8")
        data = json.loads(payload)

        readings = data.get("readings", [])
        if not readings:
            print("[MQTT] Payload sin readings")
            return

        first = readings[0]

        temperatura = float(first.get("temperatura_c", 0.0))
        humedad = float(first.get("humedad_relativa", 0.0))

        # Por ahora no tienes sensor NH3 real en ese payload
        amoniaco = 0.0

        new_dashboard = {
            "diaLote": 1,
            "temperatura": temperatura,
            "humedad": humedad,
            "amoniaco": amoniaco
        }

        with state_lock:
            dashboard_state = new_dashboard

        print("[MQTT] Dashboard actualizado:", dashboard_state)

    except Exception as e:
        print("[MQTT] Error procesando mensaje:", e)


def start_mqtt():
    client = mqtt.Client(
        callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
        client_id=MQTT_CLIENT_ID
    )
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    client.on_message = on_message

    client.connect(MQTT_BROKER, MQTT_PORT, 60)
    client.loop_forever()


# =========================
# HTTP API
# =========================
@app.get("/api/dashboard")
def get_dashboard():
    with state_lock:
        return jsonify(dashboard_state)


@app.get("/api/ventilacion")
def get_ventilacion():
    return jsonify(ventilacion_state)


@app.get("/api/criadoras")
def get_criadoras():
    return jsonify(criadoras_state)


@app.get("/api/bombas")
def get_bombas():
    return jsonify(bombas_state)


@app.get("/health")
def health():
    return jsonify({"status": "ok"})


if __name__ == "__main__":
    mqtt_thread = threading.Thread(target=start_mqtt, daemon=True)
    mqtt_thread.start()

    print(f"[HTTP] Backend disponible en http://{HTTP_HOST}:{HTTP_PORT}")
    app.run(host=HTTP_HOST, port=HTTP_PORT, debug=False)

