# Rendimiento de MDVHeadOres 1.1.1

Perfil recomendado para hasta 50 jugadores explorando simultáneamente:

```yaml
generation-throttle:
  enabled: true
  delay-after-chunk-load-ticks: 40

  interval-ticks: 1
  chunks-per-interval: 8
  max-processing-millis-per-run: 1.5
  max-dequeues-per-run: 16

  max-queue-size: 15000
  skip-if-queue-full: true
  max-retry-size: 30000
  retry-chunks-per-tick: 64
  retry-max-millis-per-tick: 0.5

  rescan-loaded-chunks-interval-ticks: 1200
  rescan-loaded-chunks-per-tick: 128
  rescan-max-millis-per-tick: 1.0

  command-generate-uses-queue: true
```

## Límites por tick

- Generación: intenta no iniciar más trabajo después de 1.5 ms.
- Reintentos: hasta 64 entradas o 0.5 ms.
- Escaneo: hasta 128 chunks o 1.0 ms.
- Un chunk que ya empezó a generarse termina su tirada para no dejar un estado parcial.

## Exploración rápida

- `ChunkLoadEvent` registra/enfila el chunk.
- `ChunkUnloadEvent` lo retira en O(1) de la cola y de los reintentos.
- No se marcan bits al retirarlo.
- Al volver a cargar, se encola otra vez automáticamente.
- Una cola llena no pierde el chunk: entra en la cola compacta de reintentos.
- Si también se alcanzara el límite de reintentos, la auditoría incremental lo recupera mientras permanezca cargado o su siguiente carga lo vuelve a detectar.

## Escaneo de miles de chunks

La auditoría toma un mundo por vez y conserva una instantánea temporal de sus chunks cargados. No revisa bloques ni carga chunks descargados. Cada tick procesa solamente el lote permitido y continúa desde el mismo índice en el siguiente tick.

Con 20 000 chunks cargados y un techo de 128 por tick, el recorrido necesita al menos unos 157 ticks; el presupuesto de 1 ms puede alargarlo, pero evita un pico único.
