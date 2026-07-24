# MDVHeadOres 1.1.0

Generador de vetas y nodos visuales mediante cabezas de jugador, con drops de MMOItems, poder de pico/hacha y experiencia opcional de MMOCore.

## Novedades de 1.1.0

### Tiradas registradas por recurso

Cada chunk guarda una sola máscara `LONG` en su Persistent Data Container. Cada recurso ocupa un bit:

```yaml
tracking-bit: 0
```

Con 11 recursos, el seguimiento usa 8 bytes de datos numéricos por chunk, más la sobrecarga normal del PDC. No se guarda una cadena larga ni una entrada independiente por mineral.

Reglas importantes:

- cada `tracking-bit` debe ser único;
- admite bits de `0` a `62`;
- no se debe cambiar el bit de un recurso después de publicar el mundo;
- una tirada se marca aunque la probabilidad falle o no encuentre una posición válida;
- al añadir un recurso nuevo con un bit nuevo, los chunks existentes lo tiran una sola vez al volver a cargarse;
- los recursos ya registrados no se vuelven a generar.

### Migración desde 1.0.9

Los chunks existentes sin máscara nueva se consideran procesados para:

```yaml
legacy-assumed-resources:
  - viridita
  - amatista_encantada
  - umbrita
  - nudo_vivo
  - tronco_corrupto
```

Por tanto, en esos chunks solo se tiran los recursos nuevos. Los chunks realmente nuevos comienzan con máscara vacía y tiran todos los recursos activos.

La marca antigua `generated_chunk` se lee durante la migración y después se elimina, dejando únicamente la máscara compacta.

### Recuperación de chunks aplazados

Un chunk descargado antes de llegar a su turno:

- no recibe ninguna marca falsa;
- conserva sus bits pendientes;
- vuelve a entrar en la cola cuando se carga de nuevo;
- también puede recuperarse mediante el escaneo periódico de chunks cargados.

La cola limita cuántas entradas descargadas descarta por tick, evitando picos cuando hay miles de entradas antiguas.

### Protección ampliada

Los recursos marcados están protegidos contra:

- agua y lava;
- cubetas vaciadas directamente;
- físicas que romperían la cabeza;
- explosiones de bloques y entidades;
- pistones y pistones pegajosos;
- destrucción indirecta del bloque que sostiene una cabeza de suelo o pared.

Las cabezas decorativas normales no se modifican.

## Estructura del proyecto

El código fue dividido por responsabilidad:

```text
config/      carga y validación
model/       definiciones inmutables
tracking/    máscara por chunk
 generation/ cola y generadores
listener/    rotura, chunks y protección
service/     MMOItems, MMOCore y poder de herramientas
command/     comandos administrativos
api/event/   evento público de rotura
```

## Compilación

Requisitos:

- Java 21
- Maven 3.9+

```bash
mvn -B clean package
```

Resultado:

```text
target/MDVHeadOres-1.1.0.jar
```

El flujo `.github/workflows/build.yml` compila automáticamente con Java 21.

## Comandos

- `/mdvheadores reload`
- `/mdvheadores inspect`
- `/mdvheadores queue`
- `/mdvheadores generate <radio> [force]`

`inspect` muestra también cuántos bits de recursos están registrados en el chunk.

Permiso: `mdvheadores.admin`.
