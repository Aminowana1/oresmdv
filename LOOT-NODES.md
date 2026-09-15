# Loot Nodes — MDVHeadOres 1.3.5

## Tipos de contenedor

- `PLAYER_HEAD`: bolsa/cofrecito con textura. Abre inventario virtual de 9, 18, 27, 36, 45 o 54 slots.
- `CHEST`: cofre vanilla de 27 slots.
- `BARREL`: barril vanilla de 27 slots.
- `DECORATED_POT`: vasija con las cuatro caras decoradas aleatoriamente con Pottery Sherds de Minecraft 1.21.6. No abre inventario: siempre elige una sola recompensa y la suelta al romper/click derecho. Al reclamarla por click derecho la vasija permanece en el mundo; solo desaparece si un jugador la rompe.

El loot de los inventarios se distribuye en slots aleatorios, no desde la esquina superior izquierda.

## Colocación

Todos los loot nodes requieren un bloque sólido de soporte debajo.

- `PLAYER_HEAD`, `CHEST` y `BARREL`: 1 bloque vertical libre/reemplazable.
- `DECORATED_POT`: 2 bloques verticales libres/reemplazables.

Solo se eliminan los materiales de `generation.replaceable-space`. Si hay piedra, construcción u otro sólido obstruyendo cualquiera de los espacios requeridos, ese intento falla. Si no encuentra posición dentro de `placement-attempts`, el nodo no aparece en ese chunk.

## Tabla de loot

```yaml
loot:
  rolls:
    min: 3
    max: 6
  max-slots: 6
  merge-same-items: true
  entries: {}
```

`rolls` decide cuántas selecciones intenta hacer. `max-slots` es el máximo de slots que el loot intentará ocupar. Desde 1.3.5, después de resolver las recompensas, las cantidades se reparten para aprovechar tantos slots como sea posible hasta ese límite. Por ejemplo, HILO x10 + cuatro premios de una unidad con `max-slots: 12` usa 8 slots para el hilo y 4 para los otros premios. Los slots finales se barajan antes de insertarse en el inventario.

`merge-same-items` controla si tiradas idénticas se suman primero como una misma recompensa lógica; incluso si se fusionan, la cantidad resultante puede volver a separarse físicamente en varios slots durante la distribución.

Las vasijas fuerzan internamente `rolls: 1/1` y `max-slots: 1`.

## Editor

```text
/mdvheadores loot list
/mdvheadores loot editor <id>
```

Pon un item en el cursor y haz click en un espacio vacío del editor. El item del cursor no se consume.

El plugin guarda referencias, no una copia congelada del item:

```yaml
# Vanilla
type: VANILLA
material: IRON_INGOT

# MMOItems
type: MMOITEM
item-type: MATERIAL
item-id: PIEDRA_LUNAR

# MythicMobs
type: MYTHICMOBS
item-id: ESENCIA_LUNAR
```

Cada entrada permite cambiar desde la UI:

- peso/probabilidad relativa;
- cantidad mínima y máxima;
- repetible sí/no.

En el panel principal se ajustan también los rolls mínimo/máximo, máximo de slots y combinación de items iguales.

## Persistencia y protección

El loot es compartido entre jugadores. Un contenedor abierto no vuelve a tirar su tabla.

- Los cofres/barriles usan su inventario vanilla.
- Las bolsas/cabezas guardan su inventario virtual en PDC.
- Solo `PLAYER_HEAD` desaparece automáticamente al quedar vacío.
- `CHEST`, `BARREL` y `DECORATED_POT` nunca desaparecen por saqueo; permanecen hasta que un jugador los rompe.
- Hoppers no extraen ni introducen items.
- No se puede formar un cofre doble con un cofre de loot.
- Las protecciones existentes contra pistones, líquidos, explosiones y física también reconocen los loot nodes.
- Una vasija marca su premio como reclamado antes de soltarlo para impedir doble entrega por eventos del mismo tick.

## Administración 1.3.0

### Generar un nodo sin buscarlo

```text
/mdvheadores loot spawn <id>
```

Lo coloca exactamente en el bloque donde están los pies del jugador. Debe existir un bloque sólido debajo y el espacio debe ser válido para el tipo de contenedor. Esta colocación administrativa no altera el tracking del chunk.

### MMOItems: modifiers y no-identificado

Cuando una entrada `MMOITEM` corresponde a equipamiento, MDVHeadOres genera una instancia real del template para que MMOItems aplique sus modifiers aleatorios. Esa parte sigue siendo independiente del estado identificado/no identificado.

Desde 1.3.5, solo los tipos configurados en `loot-nodes.mmoitems.unidentified.types` (o sus tipos padre configurados) se convierten a no identificados. Los demás se entregan identificados. Por ejemplo, si `CONSUMABLE` no aparece en la lista, las pociones/comidas MMOItems salen identificadas aunque el equipamiento siga teniendo modifiers aleatorios.

## Editor paginado y vanilla con metadata (1.3.1)

`/mdvheadores loot editor <id>` soporta múltiples páginas. Usa **SHIFT+click** en un objeto de tu inventario para añadir una copia rápidamente, o el método de cursor + slot vacío.

Los MMOItems/MythicMobs se guardan por ID. Los vanilla simples se guardan por Material. Los vanilla con metadata/componentes (incluidos los libros encantados) se guardan como `CUSTOM_VANILLA` para preservar exactamente sus encantamientos y demás datos.


## Archivos individuales (1.3.3)

Cada loot node vive en su propio archivo:

```text
plugins/MDVHeadOres/lootnodes/
  bolsa_abandonada.yml
  vasija_antigua.yml
  cofre_antiguo.yml
```

El ID se toma de `id:` (o del nombre del archivo si no existe). El editor guarda únicamente el YAML del nodo editado.

Al actualizar desde 1.3.2 o anterior, el viejo `lootnodes.yml` se divide automáticamente en archivos individuales sin borrarlo; queda como respaldo.

## Cofres/barriles físicos (1.3.3)

El PDC se persiste antes de insertar los premios y luego se reaccede al inventario real del bloque. Esto evita que un `BlockState` anterior sobrescriba el inventario y deje el cofre vacío. Los premios se colocan en slots vacíos barajados aleatoriamente.


## Comportamiento de contenedores (1.3.4)

- `PLAYER_HEAD`: click derecho abre el inventario; si no hay loot válido, desaparece. Al romperla, suelta el loot restante y nunca dropea la cabeza.
- `CHEST` / `BARREL`: permanecen aunque estén vacíos.
- `DECORATED_POT`: permanece después de reclamar; si no hay premio válido, el click no hace nada. Solo desaparece al romperse.


## 1.3.5 - Distribución y categorías no identificadas

```yaml
loot-nodes:
  mmoitems:
    unidentified:
      enabled: true
      types:
        - SWORD
        - DAGGER
        - ARMOR
        - ARMAS_MAGICAS
```

- `enabled: false`: todos los MMOItems salen identificados.
- `types: []`: también deja todos identificados.
- Los nombres se comparan sin distinguir mayúsculas/minúsculas.
- Si MMOItems expone jerarquía de tipos, una categoría padre también cubre sus hijos/subtipos.
- El preview del editor sigue siendo normal/identificado.
