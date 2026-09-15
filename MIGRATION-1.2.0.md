# Migración 1.1.1 → 1.2.0

MDVHeadOres 1.2.0 conserva el sistema actual de vetas, nodos de árboles, máscara por recurso y cola de generación. La novedad es un tercer grupo de recursos: **loot nodes**.

## 1. Añadir estas opciones a `config.yml`

No reemplaces tus valores actuales de `generation-throttle`. Solo añade:

```yaml
loot-nodes:
  enabled: true
  file: "lootnodes.yml"
  editor:
    enabled: true

chunky-compatibility:
  enabled: true
  listen-chunk-populate: true
```

## 2. `lootnodes.yml`

Al arrancar la versión nueva, el plugin crea `lootnodes.yml` si no existe. Cada definición necesita un `tracking-bit` único. En la configuración actual los bits 0-10 ya pertenecen a vetas/nodos, por lo que los ejemplos comienzan en 11.

No reutilices ni cambies un `tracking-bit` después de publicar un nodo en el mundo.

Se recomienda preparar un nodo con `enabled: false`, recargar, editar su tabla y recién después activarlo:

```text
/mdvheadores loot list
/mdvheadores loot editor bolsa_abandonada
/mdvheadores reload
```

El editor también abre definiciones desactivadas.

## 3. Chunks antiguos

No hay que borrar ni reiniciar el tracking. Al activar un loot node con un bit nuevo, los chunks ya existentes conservan los bits de los recursos anteriores y quedan pendientes únicamente para el bit nuevo. Al cargarse/visitarse entran en la misma cola incremental existente.

## 4. Chunky

`ChunkPopulateEvent` registra también los chunks pregenerados en la misma cola de MDVHeadOres. No existe un segundo generador ni una segunda cola. La deduplicación evita procesar dos veces un chunk si también llegó por `ChunkLoadEvent`.

Si Chunky descarga un chunk antes de que llegue su turno, MDVHeadOres no lo mantiene forzado en memoria: el bit queda pendiente y se procesa en una carga posterior. Esto evita que una pregeneración grande dispare el uso de memoria.

## 5. Loot diferido

El contenido no se construye durante la generación del terreno:

- `CHEST`/`BARREL`: se crea al primer acceso y queda guardado en el inventario vanilla.
- `PLAYER_HEAD`: se crea al primer acceso y queda persistido en el PDC del nodo.
- `DECORATED_POT`: elige exactamente una recompensa al romperla o hacer click derecho y la dropea.

Por eso cambiar un MMOItem o MythicItem actualiza las **tiradas futuras**. Los contenedores que ya generaron su contenido conservan el resultado que les tocó.
