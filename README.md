# MDVHeadOres

Plugin para MDVCraft / Purpur 1.21.6.

Genera minerales como `PLAYER_HEAD` reales usando la textura tomada desde MMOItems.

## v1.0.3

Cambios principales:

- Al romper la veta, el item de MMOItems se dropea físicamente en el mundo.
- Silk Touch no afecta la veta: siempre dropea el item configurado, no la cabeza.
- Cada veta puede requerir poder mínimo de pico con `required-pickaxe-power`.
- El plugin intenta leer el poder de pico desde MMOItems (`pickaxe-power` / `MMOITEMS_PICKAXE_POWER`).
- También puede leer el poder desde el lore si aparece como `Poder de pico: X`.
- Los picos vanilla pueden tener poder básico si `vanilla-pickaxes-have-power: true`.

## Comandos

```txt
/mdvheadores reload
/mdvheadores generate <radio> [force]
```

Ejemplo de prueba:

```txt
/mdvheadores reload
/mdvheadores generate 4 force
```

## Config principal

```yml
required-pickaxe-power: 1
drop-naturally: true
ignore-silk-touch: true
```

El drop se construye usando MMOItems por reflexión. Si por algún motivo no se puede construir el item, se usa `default-fallback-command` como respaldo.

## Compilar con GitHub Actions

Este proyecto incluye:

```txt
.github/workflows/build.yml
```

Pasos:

1. Sube el contenido del ZIP a la raíz del repositorio.
2. Entra a `Actions`.
3. Ejecuta `Build MDVHeadOres`.
4. Descarga el artifact `MDVHeadOres-jar`.
5. Sube el `.jar` a `/plugins/`.


## Cambios v1.0.3

- Evita generar cabezas pegadas a minerales vanilla como lapislázuli, diamante, hierro, redstone, etc.
- Aplica actualización física al colocar las cabezas para reducir bloques fantasma/actualizaciones raras.
- Añade `/mdvheadores inspect` para mirar un bloque y confirmar si realmente es una veta marcada por el plugin.

Si ya existe una config antigua, añade estas opciones dentro de cada veta:

```yml
avoid-near-vanilla-ores: true
avoid-near-materials: []
apply-physics-on-place: true
```


## v1.0.4 - Nodos de árboles

Esta versión añade `tree-nodes`, recursos visuales como PLAYER_WALL_HEAD pegados al costado de troncos.

El poder requerido se lee desde el lore del hacha/item:

```yaml
- '&3 &7■ &fPoder de Hacha: 2'
```

Ejemplo de config:

```yaml
tree-nodes:
  brote_resinoso:
    enabled: true
    mmoitems-block-id: "BROTERESINOSO"
    drop-id: "BROTERESINOSO"
    drop-type: "MATERIAL"
    drop-amount: 1
    texture-from-mmoitems: true
    name-from-mmoitems: true
    worlds:
      - world
    attach-to:
      - OAK_LOG
      - SPRUCE_LOG
    min-y: 50
    max-y: 120
    chunk-chance: 0.12
    nodes-per-chunk: 1
    required-axe-power: 2
    only-on-surface-logs: true
    apply-physics-on-place: true
    prevent-vanilla-drops: true
    ignore-silk-touch: true
    drop-naturally: true
    break-sound: "BLOCK_WOOD_BREAK"
    fail-sound: "BLOCK_NOTE_BLOCK_BASS"
```
