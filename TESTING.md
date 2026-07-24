# Pruebas recomendadas para MDVHeadOres 1.1.1

## 1. Compilación

```bash
mvn -B clean package
```

## 2. Escaneo incremental con miles de chunks

1. Mantén cargados muchos chunks o usa varios jugadores de prueba.
2. Ejecuta `/mdvheadores reload`.
3. Consulta periódicamente `/mdvheadores queue`.
4. `Escaneo incremental` debe aparecer activo y aumentar gradualmente.
5. No debe procesar todos los chunks en un único tick.
6. Un nuevo intervalo no debe crear un segundo escaneo si el anterior sigue activo.

## 3. Chunk descargado en cola

1. Aumenta temporalmente `delay-after-chunk-load-ticks`.
2. Explora rápido y aléjate.
3. `Retirados al descargar` debe aumentar.
4. La cola no debe llenarse de miles de chunks ya descargados.
5. Regresa al área: los chunks pendientes deben volver a entrar por su carga normal.

## 4. Cola llena y reintentos

1. Reduce temporalmente `max-queue-size`.
2. Carga más chunks que el límite sin alejarte.
3. `/mdvheadores queue` debe mostrar entradas en `Reintentos`.
4. A medida que se libera la cola principal, `Recuperados` debe aumentar.
5. No debe ser necesario esperar al escaneo de 60 segundos.

## 5. Presupuestos

Probar estos valores conservadores:

```yaml
rescan-loaded-chunks-per-tick: 128
rescan-max-millis-per-tick: 1.0
retry-chunks-per-tick: 64
retry-max-millis-per-tick: 0.5
max-processing-millis-per-run: 1.5
max-dequeues-per-run: 16
```

Usar Spark durante exploración y comprobar que MDVHeadOres no produce picos periódicos importantes.

## 6. Seguimiento

- Chunk legado: máscara inicial `199`, luego `2047` al completar los seis recursos nuevos.
- Chunk nuevo: comienza en `0` y termina en `2047`.
- Un chunk descargado antes de procesarse no debe recibir bits falsos.

## 7. Protecciones

Probar agua, lava, cubeta, TNT, creeper, pistón, pistón pegajoso y destrucción del soporte.
