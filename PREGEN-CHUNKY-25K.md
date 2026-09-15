# Perfil recomendado para pregenerar 25.000 bloques de radio con Chunky

Para una pregeneración masiva NO se recomienda aumentar la cola a cientos de miles.
MDVHeadOres 1.3.6 puede procesar cada chunk directamente en `ChunkPopulateEvent`, antes de que Chunky lo descargue.

## Antes de iniciar Chunky

En `plugins/MDVHeadOres/config.yml`:

```yaml
chunky-compatibility:
  enabled: true
  listen-chunk-populate: true
  direct-process-on-populate: true
```

La sección `generation-throttle` puede quedarse con los valores normales. Los chunks nuevos que pasan por `ChunkPopulateEvent` se procesan directamente y no dependen de una cola gigante.

Ejecuta después:

```text
/mdvheadores reload
```

o reinicia el servidor.

## Chunky

Para worldborder cuadrado de 25.000 bloques de radio:

```text
/worldborder center 0 0
/worldborder set 50000
/chunky world world
/chunky shape square
/chunky center 0 0
/chunky radius 25000
/chunky start
```

Para revisar MDVHeadOres durante la tarea:

```text
/mdvheadores queue
```

Con el modo directo, la cola debe permanecer pequeña y `Procesados` debe aumentar.

## Al terminar Chunky

```yaml
chunky-compatibility:
  enabled: true
  listen-chunk-populate: true
  direct-process-on-populate: false
```

Luego `/mdvheadores reload` o reinicia.

## Verificación final

Visita varios chunks pregenerados y usa `/mdvheadores inspect` mirando cualquier bloque dentro del chunk. La salida debe mostrar todas las tiradas activas completadas para ese chunk.
