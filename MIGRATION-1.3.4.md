# Migración a MDVHeadOres 1.3.4

No requiere cambios en `config.yml` ni en los YAML de `lootnodes/`.

## Cambios de interacción

- `PLAYER_HEAD`: si no puede producir loot, click derecho o rotura la elimina sin dropear la cabeza.
- `PLAYER_HEAD`: al romper una bolsa con loot, suelta todo el contenido restante al suelo y elimina la cabeza.
- `CHEST`/`BARREL`: sin loot válido simplemente se abren/permanecen vacíos.
- `DECORATED_POT`: sin loot válido permanece al hacer click; si se rompe no entrega recompensa.
- No se muestran mensajes al jugador por tablas vacías o referencias inválidas.
