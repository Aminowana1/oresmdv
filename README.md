# MDVHeadOres 1.0.6

Optimización de generación para MDVCRAFT.

## Cambios principales

- Generación de chunks por cola/throttle.
- Evita procesar todos los ores/nodos justo en el ChunkLoadEvent.
- Config nueva `generation-throttle`.
- Cache de perfiles/texturas de cabezas para no recrear `PlayerProfile` por cada cabeza.
- Mantiene soporte de XP de MMOCore de 1.0.5.
- `/mdvheadores generate` puede usar cola para no lagear.

## Config nueva para agregar si ya tienes carpeta creada

```yaml
generation-throttle:
  enabled: true
  delay-after-chunk-load-ticks: 40
  interval-ticks: 4
  chunks-per-interval: 1
  max-queue-size: 10000
  skip-if-queue-full: true
  command-generate-uses-queue: true
```

Para máxima estabilidad con jugadores explorando, deja `chunks-per-interval: 1`.

Si el mundo se queda sin suficientes minerales porque los jugadores exploran muy rápido y la cola crece demasiado, puedes subir `chunks-per-interval` a 2, pero 1 es lo más barato.
