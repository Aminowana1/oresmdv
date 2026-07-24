# Changelog

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
