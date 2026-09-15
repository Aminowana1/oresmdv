# MDVHeadOres 1.3.5

- Los MMOItems de loot nodes ya no se fuerzan todos a no identificados.
- Nueva whitelist global `loot-nodes.mmoitems.unidentified.types`; solo esos tipos (o hijos de un tipo configurado) salen sin identificar.
- `enabled: false` o `types: []` permiten desactivar completamente el no-identificado en loot nodes.
- El equipamiento sigue generándose desde templates para conservar modifiers aleatorios aunque salga identificado.
- Las cantidades de loot ahora se conservan como cantidades lógicas antes de construir los stacks, evitando truncar prematuramente cantidades grandes.
- Los inventarios reparten las cantidades entre tantos slots como sea posible hasta `max-slots`.
- Ejemplo: HILO x10 + 4 premios de una unidad con `max-slots: 12` = 8 slots de hilo + 4 slots de otras recompensas, colocados en posiciones aleatorias.
- No cambia la generación de chunks, tracking, Chunky ni la cola/throttle.

# MDVHeadOres 1.3.4

- PLAYER_HEAD: click derecho sobre una bolsa sin loot válido la elimina silenciosamente.
- PLAYER_HEAD: ahora se puede romper; suelta todo el loot restante y nunca dropea la cabeza.
- PLAYER_HEAD: si estaba abierta, al romperla cierra visores y evita duplicaciones.
- CHEST/BARREL: si no hay loot válido, abren vacíos y permanecen.
- DECORATED_POT: si no hay loot válido, permanece al click y al romperse no entrega premio.
- Eliminados los mensajes de "sin recompensas configuradas" durante interacción normal.
- Sin cambios de config respecto de 1.3.3.

# MDVHeadOres 1.3.3

- Corrige CHEST/BARREL que podían abrirse vacíos por actualizar un TileState capturado antes de insertar el loot.
- El loot físico se inserta después de persistir PDC y reacceder al inventario real; los slots siguen siendo aleatorios/dispersos.
- CHEST y BARREL ya no desaparecen automáticamente al quedar vacíos.
- DECORATED_POT ya no desaparece al reclamar con click derecho; queda marcada como saqueada y permanece hasta que un jugador la rompe.
- PLAYER_HEAD sigue siendo el único contenedor que desaparece automáticamente al vaciar su inventario virtual.
- CHEST/BARREL/DECORATED_POT pueden ser destruidos manualmente.
- Cada loot node vive en su propio YAML dentro de `lootnodes/`.
- Migración automática y no destructiva desde el viejo `lootnodes.yml`; el archivo anterior se conserva como respaldo.

# MDVHeadOres 1.3.2

## Fix de consumo de loot nodes vacíos

- Vasijas ya no desaparecen si `entries` está vacío o ninguna recompensa puede construirse.
- PLAYER_HEAD ya no abre/genera un inventario vacío ni se consume en ese caso.
- CHEST/BARREL ya no se marcan como generados cuando el roll devuelve 0 items.
- Si no hay recompensas válidas se informa al jugador y el nodo permanece intacto.
- Se mantiene el bloqueo anti doble-claim de vasijas una vez que sí existe un premio válido.

# Changelog

## 1.3.1
- Editor de loot nodes con múltiples páginas y navegación anterior/siguiente.
- SHIFT+click desde el inventario para añadir recompensas rápidamente, estilo MDVCrates.
- Soporte correcto para libros encantados y otros vanilla con metadata mediante `CUSTOM_VANILLA`.
- MMOItems y MythicMobs siguen guardándose por ID para recibir cambios futuros.


## 1.2.0

- Añade `lootnodes.yml` separado para bolsas/cabezas, cofres, barriles y vasijas.
- Añade editor in-game de tablas de loot con Vanilla, MMOItems y MythicMobs por referencia estable.
- Genera el contenido únicamente al primer acceso; la generación de chunks no construye items de loot.
- Distribuye el loot de inventarios en slots aleatorios y permite rolls mínimo/máximo, máximo de slots, pesos y recompensas repetibles.
- Las vasijas fuerzan una sola recompensa, aceptan click derecho o rotura y usan decoración aleatoria en sus cuatro caras.
- La colocación exige soporte sólido y espacio vertical: 1 bloque para cabeza/cofre/barril y 2 para vasija.
- Integra los loot nodes en la misma máscara por recurso para actualizar chunks antiguos sin repetir vetas/nodos previos.
- Añade compatibilidad de pregeneración mediante `ChunkPopulateEvent`, reutilizando la misma cola, deduplicación y throttle.
- Añade protección de loot nodes frente a hoppers, cofres dobles y las protecciones existentes de fluidos/explosiones/pistones/física.

## 1.1.1

- Sustituye el escaneo completo en un solo tick por una auditoría incremental con límite de chunks y milisegundos por tick.
- Añade una cola compacta de reintentos para chunks rechazados por cola llena.
- Retira en O(1) las entradas de chunks descargados mediante `ChunkUnloadEvent`.
- Evita acumular miles de entradas muertas durante teletransportes o exploración rápida.
- Añade presupuesto temporal y límite de extracciones para la cola principal.
- Evita escaneos solapados y reporta sus estadísticas en `/mdvheadores queue`.
- Limita avisos de cola llena para impedir spam de consola.
- Reduce lecturas PDC duplicadas durante carga, escaneo y encolado.
- Evita escrituras de máscara cuando no cambió.
- Elimina pequeñas asignaciones repetidas en generación de nodos y protección de soportes.

## 1.1.0

- Sustituye la marca global de chunk por una máscara `LONG` con un bit por recurso.
- Añade `tracking-bit` estable y único a cada veta y nodo.
- Marca la tirada aunque falle la probabilidad o no exista una posición válida.
- Permite que recursos añadidos posteriormente se generen una sola vez en chunks existentes.
- Añade migración configurable desde 1.0.9, suponiendo procesados Viridita, Amatista Encantada, Umbrita, Nudo Vivo y Tronco Corrupto.
- Recupera automáticamente chunks descargados antes de procesarse cuando vuelven a cargarse.
- Añade escaneo periódico de chunks cargados para recuperar rechazos por cola llena.
- Limita el vaciado de entradas descargadas por tick para evitar picos de CPU.
- Añade protección contra explosiones, pistones y físicas indirectas.
- Protege también el bloque de soporte de cabezas de suelo y pared frente a explosiones/pistones.
- Reduce el spam del debug con `debug-options.log-unloaded-chunks`.
- Amplía `/mdvheadores inspect` con el estado de la máscara del chunk.
- Refactoriza el proyecto en servicios, listeners, registro, seguimiento, cola y modelos separados.

## 1.0.9

- Protección contra agua y lava.

## 1.3.0
- Añadido `/mdvheadores loot spawn <id>` para generar un loot node exacto en la posición del administrador.
- Añadido `/mdvheadores head <ore|node> <id> [cantidad]` para obtener cabezas colocables que se convierten en recursos reales.
- Añadido autocompletado para los nuevos comandos e IDs.
- El equipamiento MMOItems obtenido desde loot nodes se genera desde el template con modifiers aleatorios y luego se vuelve no identificado.
- Los previews del editor permanecen identificados y estables.
