# Loot Nodes — MDVHeadOres 1.2.0

## Tipos de contenedor

- `PLAYER_HEAD`: bolsa/cofrecito con textura. Abre inventario virtual de 9, 18, 27, 36, 45 o 54 slots.
- `CHEST`: cofre vanilla de 27 slots.
- `BARREL`: barril vanilla de 27 slots.
- `DECORATED_POT`: vasija con las cuatro caras decoradas aleatoriamente con Pottery Sherds de Minecraft 1.21.6. No abre inventario: siempre elige una sola recompensa y la suelta al romper/click derecho.

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

`rolls` decide cuántas selecciones intenta hacer. `max-slots` limita cuántos stacks distintos puede producir. `merge-same-items` intenta fusionar resultados iguales antes de ocupar otro slot.

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
- Al quedar vacío, el loot node desaparece.
- Hoppers no extraen ni introducen items.
- No se puede formar un cofre doble con un cofre de loot.
- Las protecciones existentes contra pistones, líquidos, explosiones y física también reconocen los loot nodes.
- Una vasija marca su premio como reclamado antes de soltarlo para impedir doble entrega por eventos del mismo tick.
