# Migración a MDVHeadOres 1.3.6

No hay cambios destructivos ni cambios de tracking bits.

Para una pregeneración masiva con Chunky, añade temporalmente:

```yaml
chunky-compatibility:
  enabled: true
  listen-chunk-populate: true
  direct-process-on-populate: true
```

Con esta opción, cada chunk se procesa durante `ChunkPopulateEvent` antes de que pueda descargarse. No hace falta aumentar la cola a cientos de miles.

Al terminar Chunky:

```yaml
chunky-compatibility:
  direct-process-on-populate: false
```

Después ejecuta `/mdvheadores reload` o reinicia el servidor.
