# Migración 1.1.0 → 1.1.1

No cambia ningún `tracking-bit` ni la máscara guardada en los chunks.

Reemplazar los límites de procesamiento por:

```yaml
interval-ticks: 1
chunks-per-interval: 8

rescan-loaded-chunks-per-tick: 128
rescan-max-millis-per-tick: 1.0
retry-chunks-per-tick: 64
retry-max-millis-per-tick: 0.5
max-retry-size: 30000
max-processing-millis-per-run: 1.5
max-dequeues-per-run: 16
```

Se conserva:

```yaml
rescan-loaded-chunks-interval-ticks: 1200
```

No hace falta borrar datos, regenerar chunks ni cambiar la lista `legacy-assumed-resources`.
