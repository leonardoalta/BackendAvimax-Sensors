# Sensor Readings Service

Microservicio independiente para lectura y almacenamiento de sensores ambientales: **Temperatura**, **Humedad** y **NH3**.

## Características

- API REST para insertar y consultar lecturas de sensores
- Almacenamiento en PostgreSQL (BD `avimax`)
- Entidades JPA para cada tipo de sensor
- Endpoints por ubicación/galpon
- Ejecución en puerto **8081** (independiente del backend principal en 8080)

## Estructura

```
sensor-readings-service/
├── pom.xml
├── src/main/java/com/avimax/sensors/
│   ├── SensorReadingsServiceApplication.java
│   ├── entity/
│   │   ├── TemperatureReading.java
│   │   ├── HumidityReading.java
│   │   └── NH3Reading.java
│   ├── repository/
│   │   ├── TemperatureReadingRepository.java
│   │   ├── HumidityReadingRepository.java
│   │   └── NH3ReadingRepository.java
│   ├── controller/
│   │   ├── TemperatureController.java
│   │   ├── HumidityController.java
│   │   └── NH3Controller.java
│   └── dto/
│       ├── CreateTemperatureReadingRequest.java
│       ├── CreateHumidityReadingRequest.java
│       └── CreateNH3ReadingRequest.java
└── src/main/resources/
    └── application.yml
```

## Construcción y Ejecución

### Build

```bash
cd sensor-readings-service
mvn clean package -DskipTests
```

### Run

```bash
DB_PASSWORD=avimax java -jar target/sensor-readings-service-0.0.1-SNAPSHOT.jar
```

Aplicación disponible en: `http://localhost:8081`

## Endpoints

### Temperatura

- **POST** `/api/temperature` — Insertar lectura
  ```json
  { "value": 28.5, "location": "galpon1" }
  ```
- **GET** `/api/temperature` — Listar todas
- **GET** `/api/temperature/location/{location}` — Por ubicación
- **GET** `/api/temperature/{id}` — Por ID

### Humedad

- **POST** `/api/humidity` — Insertar lectura
  ```json
  { "value": 75.0, "location": "galpon1" }
  ```
- **GET** `/api/humidity` — Listar todas
- **GET** `/api/humidity/location/{location}` — Por ubicación
- **GET** `/api/humidity/{id}` — Por ID

### NH3 (Amoníaco)

- **POST** `/api/nh3` — Insertar lectura
  ```json
  { "value": 8.5, "location": "galpon1" }
  ```
- **GET** `/api/nh3` — Listar todas
- **GET** `/api/nh3/location/{location}` — Por ubicación
- **GET** `/api/nh3/{id}` — Por ID

## Configuración

Archivo: `src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/avimax
    username: ${DB_USERNAME:avimax}
    password: ${DB_PASSWORD:avimax}
server:
  port: 8081
```

## Base de Datos

Las tablas se crean automáticamente en BD `avimax`:
- `temperature_readings`
- `humidity_readings`
- `nh3_readings`

Cada tabla tiene campos: `id`, `value`, `location`, `recorded_at`.

## Isolamiento

Este proyecto es **completamente independiente** del backend principal (`backend-java`):
- Carpeta separada: `/home/leo/AviMaxBack/sensor-readings-service`
- Puerto diferente: **8081** (vs 8080 del principal)
- No interfiere con ningún código existente

## Próximos Pasos

- Integrar suscripción a topics MQTT para recibir datos de sensores directamente
- Agregar endpoints de estadísticas (mín/máx/promedio)
- Documentación Swagger/OpenAPI
