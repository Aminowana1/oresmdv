# Migración a MDVHeadOres 1.3.5

No hay cambios obligatorios en los YAML individuales de `lootnodes/`.

Para controlar qué categorías MMOItems salen sin identificar, añade a `config.yml`:

```yaml
loot-nodes:
  mmoitems:
    unidentified:
      enabled: true
      types:
        - SWORD
        - DAGGER
        - SPEAR
        - HAMMER
        - GAUNTLET
        - WHIP
        - STAFF
        - BOW
        - CROSSBOW
        - ARMOR
        - TOOL
        - ARMAS_MAGICAS
        - SOPORTE_MAGICO
```

Quita de `types` cualquier categoría que quieras entregar identificada. Por ejemplo, no incluyas `CONSUMABLE` si consumibles deben salir normales.

Si la sección no existe, 1.3.5 conserva el comportamiento de compatibilidad de 1.3.4 para evitar cambiar un servidor al actualizar. En cuanto definas explícitamente `types`, esa lista manda. `types: []` significa que ninguno sale sin identificar.

`max-slots` no cambia de formato. Desde 1.3.5 se usa también como objetivo de dispersión: el plugin intenta ocupar todos los slots posibles, hasta ese máximo, dividiendo las cantidades entre varios stacks.
