# MDVHeadOres 1.3.1

## Editor paginado

El editor de loot nodes ahora soporta múltiples páginas. `loot-nodes.editor.rewards-per-page` controla cuántas recompensas se muestran por página (1-45, recomendado 45).

También se puede usar **SHIFT + click** sobre un item del inventario del jugador para añadirlo rápidamente sin consumirlo.

## Vanilla con metadata / libros encantados

MMOItems y MythicMobs siguen guardándose por sus IDs estables. Los objetos vanilla simples se guardan por `Material`. Los vanilla con metadata/componentes (por ejemplo `ENCHANTED_BOOK` con encantamientos almacenados, objetos vanilla encantados, pociones, mapas o nombres custom) se guardan como `CUSTOM_VANILLA` usando la serialización binaria del `ItemStack`.

No hay que cambiar `lootnodes.yml`: el editor escribe automáticamente `type: CUSTOM_VANILLA` e `item-data` cuando corresponde.
