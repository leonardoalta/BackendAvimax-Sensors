# Extracción de Datos Históricos: Temperatura, Humedad y Amoniaco

## Descripción General

El módulo de **datos históricos** permite consultar y visualizar registros históricos de sensores (temperatura, humedad y amoniaco) a través de una interfaz de tablas. Los datos se obtienen del backend mediante la API de lecturas (`/api/readings`).

---

## Estructura de Datos

### ReadingRecord
Representa un registro individual de lectura de sensor:

```c
typedef struct {
    int    id;                      /* ID único del registro */
    char   gatewayId[64];           /* ID de la puerta de enlace */
    char   sensor[64];              /* Identificador del sensor */
    char   deviceId[64];            /* ID del dispositivo */
    char   timestamp[64];           /* Marca de tiempo ISO-8601 UTC (ej: 2025-06-03T10:30:45Z) */
    double temperatura_c;           /* Temperatura en °C */
    double humedad_relativa;        /* Humedad relativa en % */
    double nh3_ppm;                 /* Concentración de amoníaco en ppm */
    char   raw_payload[512];        /* Payload bruto (opcional) */
} ReadingRecord;
```

### ReadingList
Contenedor de múltiples registros y metadatos:

```c
typedef struct {
    ReadingRecord *records;         /* Array de registros (heap-allocated) */
    int            count;           /* Número de registros obtenidos */
    int            meta_total;      /* Total de registros disponibles (para paginación) */
    ApiResult      result;          /* Código de error y mensaje */
} ReadingList;
```

---

## Endpoint API

### GET /api/readings

**URL Base:** `${AVIMAX_API_URL}/api/readings`

**Parámetros Query String:**

| Parámetro | Tipo | Obligatorio | Descripción |
|-----------|------|-------------|-------------|
| `start` | ISO-8601 | Opcional | Marca de tiempo de inicio (ej: `2025-06-02T00:00:00Z`) |
| `end` | ISO-8601 | Opcional | Marca de tiempo de fin (ej: `2025-06-03T23:59:59Z`) |
| `variable` | string | Opcional | `temperatura`, `humedad`, o `nh3` |
| `gateway` | string | Opcional | Filtro por ID de puerta de enlace |
| `sensor` | string | Opcional | Filtro por identificador de sensor |
| `page` | int | Opcional | Número de página (base-0, por defecto 0) |
| `size` | int | Opcional | Registros por página (por defecto 200) |
| `sort` | string | Opcional | Criterio de ordenamiento (ej: `timestamp,desc`) |

### Ejemplo de URL

```
http://localhost:8080/api/readings?start=2025-06-02T00:00:00Z&end=2025-06-03T23:59:59Z&variable=temperatura&size=200&sort=timestamp,desc
```

---

## Formato de Respuesta Esperado

El servidor devuelve un JSON con la siguiente estructura:

```json
{
  "data": [
    {
      "id": 1,
      "gatewayId": "gateway-001",
      "sensor": "DHT22-01",
      "deviceId": "device-001",
      "timestamp": "2025-06-03T10:30:45Z",
      "temperatura_c": 28.5,
      "humedad_relativa": 65.2,
      "nh3_ppm": 2.15,
      "raw_payload": "{...}"
    },
    {
      "id": 2,
      "gatewayId": "gateway-001",
      "sensor": "DHT22-01",
      "deviceId": "device-001",
      "timestamp": "2025-06-03T10:29:45Z",
      "temperatura_c": 28.3,
      "humedad_relativa": 64.8,
      "nh3_ppm": 2.12,
      "raw_payload": "{...}"
    }
  ],
  "meta": {
    "page": 0,
    "size": 200,
    "total": 5432
  }
}
```

**Notas:**
- El servidor puede devolver `null` para campos no disponibles
- El timestamp siempre debe estar en formato **ISO-8601 UTC**
- Si hay un error, el JSON contendrá un objeto `error` con `code` y `message`

---

## Función de Cliente API

### Firma

```c
int api_get_readings(const char *start, const char *end,
                     const char *variable, const char *gateway,
                     const char *sensor, int page, int size,
                     const char *sort, ReadingList *out);
```

### Parámetros

| Parámetro | Descripción |
|-----------|-------------|
| `start` | Timestamp de inicio en ISO-8601 (ej: "2025-06-02T00:00:00Z") o NULL |
| `end` | Timestamp de fin en ISO-8601 (ej: "2025-06-03T23:59:59Z") o NULL |
| `variable` | Una de: "temperatura", "humedad", "nh3" o NULL |
| `gateway` | ID de puerta de enlace o NULL |
| `sensor` | ID de sensor o NULL |
| `page` | Número de página (0-based) o -1 para ignorar |
| `size` | Registros por página (> 0) o 0 para ignorar |
| `sort` | Criterio de ordenamiento (ej: "timestamp,desc") o NULL |
| `out` | Puntero a `ReadingList` donde se almacenan resultados |

### Retorno

- **1** si la solicitud fue exitosa (verifica `out->result.error_code == API_ERR_NONE`)
- **0** si falló (consulta `out->result.error_code` y `out->result.error_message`)

### Ejemplo de Uso

```c
ReadingList readings = {0};
api_get_readings(
    "2025-06-02T00:00:00Z",  /* start */
    "2025-06-03T23:59:59Z",  /* end */
    "temperatura",           /* variable */
    NULL,                    /* gateway */
    NULL,                    /* sensor */
    0,                       /* page */
    200,                     /* size */
    "timestamp,desc",        /* sort */
    &readings
);

if (readings.result.error_code == API_ERR_NONE) {
    printf("Se obtuvieron %d registros de %d totales\n", readings.count, readings.meta_total);
    for (int i = 0; i < readings.count; i++) {
        printf("Temp: %.1f°C, Humedad: %.1f%%, NH3: %.2fppm (ts: %s)\n",
               readings.records[i].temperatura_c,
               readings.records[i].humedad_relativa,
               readings.records[i].nh3_ppm,
               readings.records[i].timestamp);
    }
    api_readings_free(&readings);  /* Libera memoria */
} else {
    printf("Error: %s\n", readings.result.error_message);
}
```

---

## Extracción en `historicos_view.c`

### Flujo de Obtención de Datos

#### 1. **Inicio del Hilo de Fetch** (`historicos_fetch_thread`)

La función se ejecuta en un hilo separado para no bloquear la UI:

```c
static gpointer historicos_fetch_thread(gpointer data) {
    HistFetch *hf = data;
    if (!hf->valid) return NULL;  /* Verificar cancelación */
    
    /* Calcular rango de 24 horas atrás */
    time_t now = time(NULL);
    struct tm tm_now;
    gmtime_r(&now, &tm_now);
    
    char end[64], start[64];
    strftime(end, sizeof(end), "%Y-%m-%dT%H:%M:%SZ", &tm_now);
    tm_now.tm_mday -= 1;
    mktime(&tm_now);
    strftime(start, sizeof(start), "%Y-%m-%dT%H:%M:%SZ", &tm_now);

    /* Llamar a la API */
    api_get_readings(
        start, end,
        strcmp(hf->metric,"temp")==0 ? "temperatura" :
        (strcmp(hf->metric,"hum")==0 ? "humedad" : "nh3"),
        NULL, NULL, 0, 200, "timestamp,desc", &hf->list
    );
    
    /* Retorno seguro si todavía es válido */
    if (hf->valid) g_idle_add(historicos_fetch_done_cb, hf);
    else { api_readings_free(&hf->list); g_free(hf); }
    return NULL;
}
```

**Puntos clave:**
- Se ejecuta en **thread separado** usando `g_thread_new()`
- Calcula automáticamente el rango de 24 horas previas
- Verifica el flag `valid` antes de llamar a la API
- Planifica el callback de finalización via `g_idle_add()`

#### 2. **Callback de Finalización** (`historicos_fetch_done_cb`)

Se ejecuta en el hilo principal (UI thread) cuando termina la fetch:

```c
static gboolean historicos_fetch_done_cb(gpointer data) {
    HistFetch *hf = data;
    gboolean cancelled = (!hf->valid || !hf->ctx);
    
    if (!cancelled) {
        HistCtx *ctx = hf->ctx;
        gtk_list_store_clear(ctx->store);
        
        if (hf->list.result.error_code == API_ERR_NONE) {
            gtk_widget_hide(ctx->status_box);
            
            /* Llenar tabla con los registros */
            for (int i = 0; i < hf->list.count; i++) {
                ReadingRecord *r = &hf->list.records[i];
                GtkTreeIter iter;
                
                /* Formatear timestamp y valor */
                char datebuf[64];
                snprintf(datebuf, sizeof(datebuf), "%s", r->timestamp);
                
                char valbuf[64];
                if (strcmp(hf->metric, "temp") == 0)
                    snprintf(valbuf, sizeof(valbuf), "%.1f", r->temperatura_c);
                else if (strcmp(hf->metric, "hum") == 0)
                    snprintf(valbuf, sizeof(valbuf), "%.1f", r->humedad_relativa);
                else
                    snprintf(valbuf, sizeof(valbuf), "%.2f", r->nh3_ppm);
                
                /* Insertar en tabla */
                gtk_list_store_append(ctx->store, &iter);
                gtk_list_store_set(ctx->store, &iter,
                                   COL_DATE, datebuf,
                                   COL_VALUE, valbuf,
                                   COL_UNIT, ctx->unit,
                                   -1);
            }
            
            if (hf->list.count == 0) {
                gtk_label_set_text(GTK_LABEL(ctx->status_lbl), "Sin datos disponibles");
                gtk_widget_show(ctx->status_box);
            }
        } else {
            /* Error en la API */
            gtk_label_set_text(GTK_LABEL(ctx->status_lbl), hf->list.result.error_message);
            gtk_widget_show(ctx->status_box);
        }
    }
    
    api_readings_free(&hf->list);
    g_free(hf);
    return G_SOURCE_REMOVE;
}
```

**Puntos clave:**
- Ejecuta en el **hilo principal** (seguro para GTK)
- Verifica `valid` y `ctx` para evitar acceso a memoria liberada
- Parsea valores según la métrica (`temp`, `hum`, `nh3`)
- Limpia memoria con `api_readings_free()` cuando termina

---

## Variables Soportadas

El cliente soporta tres variables históricas:

| Variable (C) | Variable (API) | Unidad | Campo en ReadingRecord |
|--------------|----------------|--------|----------------------|
| `"temp"` | `"temperatura"` | °C | `temperatura_c` |
| `"hum"` | `"humedad"` | % | `humedad_relativa` |
| `"nh3"` | `"nh3"` | ppm | `nh3_ppm` |

---

## Manejo de Errores

### Códigos de Error (ApiErrorCode)

```c
typedef enum {
    API_ERR_NONE = 0,           /* Éxito */
    API_ERR_INVALID_INPUT,      /* Parámetros inválidos */
    API_ERR_NOT_FOUND,          /* Recurso no encontrado */
    API_ERR_BUSINESS_RULE,      /* Violación de regla de negocio */
    API_ERR_SERVER_ERROR,       /* Error 5xx del servidor */
    API_ERR_NETWORK,            /* Error de conexión */
    API_ERR_PARSE               /* Error en parseo de JSON */
} ApiErrorCode;
```

### Estructura de Resultado

```c
typedef struct {
    int          http_status;           /* Código HTTP (200, 400, 500, etc.) */
    ApiErrorCode error_code;            /* Código de error estándar */
    char         error_message[256];    /* Mensaje de error descriptivo */
} ApiResult;
```

### Ejemplo de Manejo

```c
if (readings.result.error_code != API_ERR_NONE) {
    fprintf(stderr, "Error [%d]: %s\n", readings.result.http_status, 
            readings.result.error_message);
}
```

---

## Integración de Vistas

Las vistas históricas se registran en `main_window.c`:

| Vista | Métrica | Variable (C) | Página |
|-------|---------|-------------|--------|
| Temperatura | temperatura | `"temp"` | `"historicos_temp"` |
| Humedad | humedad | `"hum"` | `"historicos_hum"` |
| Amoniaco | nh3 | `"nh3"` | `"historicos_nh3"` |

Cada vista crea automáticamente un hilo de fetch cuando se muestra y cancela al navegar atrás.

---

## Notas de Implementación

1. **Thread Safety:** El fetch se cancela seteando `HistFetch.valid = FALSE` en el destructor de la vista, previniendo actualizaciones de UI después de liberación de memoria.

2. **Rango Automático:** Las vistas le dan por defecto 24 horas de histórico; esto puede extenderse modificando los parámetros `start`/`end` en `historicos_fetch_thread()`.

3. **Paginación:** El código usa `page=0, size=200` por defecto. Para grandes conjuntos, implementar controles de paginación en la UI.

4. **Timestamps:** Los timestamps se envían/reciben en ISO-8601 UTC. Para mostrar en hora local, se requiere conversión en la UI callback.

---

## Próximas Mejoras

- [ ] Agregar controles de rango de fechas personalizadas
- [ ] Implementar paginación con botones Anterior/Siguiente
- [ ] Convertir timestamps a zona horaria local
- [ ] Agregar exportación a CSV
- [ ] Graficar datos con GnuPlot o similar
