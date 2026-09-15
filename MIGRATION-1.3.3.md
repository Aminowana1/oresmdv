# Migración a MDVHeadOres 1.3.3

## Loot nodes por archivo

La configuración activa pasa a `plugins/MDVHeadOres/lootnodes/*.yml`, un archivo por nodo.

Si existe el viejo `plugins/MDVHeadOres/lootnodes.yml`, al iniciar/recargar 1.3.3 se copian automáticamente los nodos que todavía no tengan archivo individual. El archivo combinado NO se borra y queda como respaldo.

La config recomendada es:

```yaml
loot-nodes:
  enabled: true
  directory: "lootnodes"
  legacy-file: "lootnodes.yml"
  editor:
    enabled: true
    rewards-per-page: 45
```

La clave antigua `loot-nodes.file` sigue siendo aceptada como nombre del archivo legado para compatibilidad.

## Persistencia

- PLAYER_HEAD: desaparece al quedar vacío.
- CHEST/BARREL: nunca desaparecen automáticamente; solo al romperlos.
- DECORATED_POT: click derecho entrega una sola recompensa y deja la vasija; romperla también puede reclamar el premio si estaba pendiente y destruye la vasija.
