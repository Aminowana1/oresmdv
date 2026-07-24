# MDVHeadOres 1.1.1

Generador de vetas y nodos visuales mediante cabezas de jugador, con drops de MMOItems, poder de pico/hacha, experiencia opcional de MMOCore y seguimiento compacto por chunk.

## Rendimiento de 1.1.1

### Escaneo incremental

La auditoría periódica ya no recorre todos los chunks cargados en un único tick. Conserva una instantánea por mundo y la procesa con dos límites simultáneos:

```yaml
rescan-loaded-chunks-per-tick: 128
rescan-max-millis-per-tick: 1.0
```

Al alcanzar cualquiera de los dos límites, continúa en el tick siguiente. No carga chunks descargados ni recorre bloques; únicamente lee la máscara `LONG` del chunk y comprueba si hay bits pendientes.

### Recuperación dirigida

Los chunks rechazados por una cola llena se guardan en una cola compacta de reintentos:

```yaml
retry-chunks-per-tick: 64
retry-max-millis-per-tick: 0.5
max-retry-size: 30000
```

Así, el caso común se recupera sin esperar al siguiente escaneo global.

### Retirada al descargar

La cola principal y la de reintentos usan `LinkedHashMap`: mantienen orden FIFO, deduplican y permiten retirar en O(1) un chunk que se descarga. Cuando vuelve a cargarse, sus bits siguen pendientes y entra nuevamente por `ChunkLoadEvent`.

### Presupuesto de generación

```yaml
max-processing-millis-per-run: 1.5
max-dequeues-per-run: 16
```

La cola deja de iniciar trabajo adicional al agotar su presupuesto. Un chunk ya iniciado puede finalizar para no dejar una tirada a medias.

## Seguimiento por recurso

Cada chunk guarda una única máscara `LONG`. Cada recurso ocupa un bit estable:

```yaml
tracking-bit: 0
```

Reglas:

- bits permitidos: `0` a `62`;
- nunca cambiar ni reutilizar el bit de un recurso publicado;
- la tirada se marca aunque falle la probabilidad o no encuentre lugar;
- una excepción deja el bit pendiente;
- un recurso futuro recibe un bit nuevo y se tira una sola vez en chunks existentes.

## Migración desde 1.0.9

Los chunks existentes sin máscara se consideran procesados para:

```yaml
legacy-assumed-resources:
  - viridita
  - amatista_encantada
  - umbrita
  - nudo_vivo
  - tronco_corrupto
```

En esos chunks solo se tiran Nimbrel, Oricalco, Mithril, Nudo Rúnico, Savia Áurea y Nudo Primordial. Los chunks nuevos tiran todos los recursos activos.

## Protección

Los recursos marcados se protegen contra agua, lava, cubetas, físicas destructivas, explosiones, pistones y destrucción indirecta del soporte.

## Compilación

Requisitos:

- Java 21
- Maven 3.9+

```bash
mvn -B clean package
```

Resultado:

```text
target/MDVHeadOres-1.1.1.jar
```

También incluye `.github/workflows/build.yml` para GitHub Actions.

## Comandos

- `/mdvheadores reload`
- `/mdvheadores inspect`
- `/mdvheadores queue`
- `/mdvheadores generate <radio> [force]`

`queue` muestra cola principal, reintentos y progreso del escaneo incremental.
