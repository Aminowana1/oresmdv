# Pruebas recomendadas para MDVHeadOres 1.1.0

## 1. Compilación

```bash
mvn -B clean package
```

## 2. Migración de chunk existente

1. Instala 1.1.0 con la lista de recursos legados incluida.
2. Carga un chunk que ya existía con 1.0.9.
3. Usa `/mdvheadores inspect` dentro del chunk.
4. Debe comenzar con los cinco bits legados y completar únicamente las tiradas nuevas.
5. Al terminar debe mostrar todos los recursos activos registrados.

Con los bits de la configuración entregada:

```text
máscara legada: 199
máscara completa: 2047
```

## 3. Chunk nuevo

1. Explora terreno nunca generado.
2. El chunk debe tirar los 11 recursos activos una sola vez.
3. Recárgalo varias veces y confirma que no aparecen copias nuevas.

## 4. Chunk descargado antes de procesarse

1. Usa un delay alto temporalmente.
2. Teletranspórtate a terreno nuevo y aléjate antes de que la cola lo procese.
3. El chunk debe contarse como aplazado, no como completado.
4. Vuelve a cargarlo.
5. Debe reingresar en la cola y completar sus tiradas pendientes.

## 5. Cola llena

1. Reduce temporalmente `max-queue-size`.
2. Carga más chunks que el límite.
3. Deja algunos chunks cargados.
4. El escaneo periódico debe recuperarlos cuando haya espacio.

## 6. Protección

Probar sobre una veta y un nodo:

- flujo de agua;
- flujo de lava;
- cubeta directa;
- TNT;
- creeper;
- pistón empujando la cabeza;
- pistón moviendo su bloque de soporte;
- romper el bloque de soporte manualmente.

La rotura normal con herramienta adecuada debe seguir funcionando y entregar drop/XP/evento.

## 7. Recurso nuevo futuro

1. Añade un recurso con un `tracking-bit` libre.
2. No lo añadas a `legacy-assumed-resources`.
3. Recarga el plugin.
4. Un chunk ya completado debe tirar solo ese recurso nuevo.
5. Una segunda carga no debe repetir la tirada.
