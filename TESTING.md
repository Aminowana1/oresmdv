# Pruebas recomendadas para MDVHeadOres 1.3.0

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


## 8. Loot nodes

1. Deja una definición con `enabled: false` y confirma que aparece en `/mdvheadores loot list`.
2. Abre `/mdvheadores loot editor <id>` y añade un item vanilla, un MMOItem y un MythicItem.
3. Comprueba en el YAML individual dentro de `lootnodes/` que se guardan `Material`, `item-type + item-id` e `item-id` respectivamente.
4. Activa la definición y recarga.
5. En un chunk nuevo o pendiente, el nodo debe quedar sobre soporte sólido.
6. Cofre/barril/cabeza necesitan un espacio vertical; vasija debe fallar si no dispone de dos.
7. El loot del cofre/cabeza debe quedar disperso aleatoriamente y no volver a tirarse al reabrir.
8. Una vasija debe soltar exactamente una entrada al romperla o hacer click derecho.
9. Vaciar un contenedor debe eliminarlo.
10. Probar hopper, TNT, pistón y colocar otro cofre al lado del cofre de loot.

## 9. Chunks antiguos y Chunky

1. Usa un chunk antiguo con los bits 0-10 ya procesados.
2. Activa un loot node con bit 11 y visita/carga el chunk.
3. Solo el bit 11 debe quedar pendiente/procesarse; las vetas y nodos anteriores no deben duplicarse.
4. Ejecuta una pregeneración con Chunky y observa `/mdvheadores queue`: los chunks deben pasar por la misma cola de MDVHeadOres.
5. La pregeneración no debe crear una segunda cola ni mantener forzados chunks descargados.
6. Para pregeneraciones masivas, activa `chunky-compatibility.direct-process-on-populate: true`.
7. Durante ese modo, `/mdvheadores queue` debe mantenerse prácticamente vacío mientras `Procesados` aumenta.
8. Detén Chunky, visita varios chunks pregenerados y usa `/mdvheadores inspect`: deben mostrar todos los bits activos completados.


## Administración 1.3.0

1. `/mdvheadores loot spawn <id>` debe colocar el loot node en el bloque de los pies si hay soporte y espacio válidos.
2. El spawn manual no debe marcar el tracking-bit del chunk ni consumir la probabilidad natural.
3. `/mdvheadores head ore <id>` y `/mdvheadores head node <id>` deben entregar cabezas colocables.
4. Al colocar una cabeza administrativa, `/mdvheadores inspect` debe detectarla como veta/nodo real.
5. Al romperla debe respetar potencia de herramienta, drops y XP normales.
6. Un MMOItem de equipamiento obtenido desde loot node debe salir con la tirada de modifiers de MMOItems y no identificado.
7. El preview del editor debe seguir siendo estable/identificado y no consumir tiradas aleatorias.

## Editor y libros encantados 1.3.1

1. Crea/usa un loot node y abre `/mdvheadores loot editor <id>`.
2. Añade más de 45 recompensas usando SHIFT+click desde el inventario. Comprueba navegación anterior/siguiente y que al editar/eliminar se regrese a la página correcta.
3. Añade un `ENCHANTED_BOOK` con uno o más encantamientos almacenados. Cierra, ejecuta `/mdvheadores reload` y vuelve a abrir el editor: el preview debe conservar exactamente los encantamientos.
4. Fuerza/genera el loot node y comprueba que el libro obtenido conserva los mismos encantamientos.
5. Comprueba que MMOItems siguen apareciendo en el YAML individual dentro de `lootnodes/` como `type: MMOITEM` + `item-type` + `item-id`, y MythicMobs como `type: MYTHICMOBS` + `item-id`.
6. Comprueba que un vanilla simple sigue guardándose solo como `type: VANILLA` + `material`, mientras que el libro encantado usa `type: CUSTOM_VANILLA` + `item-data`.
