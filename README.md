# MDVHeadOres 1.0.9

Plugin de MDVCraft para generar vetas y nodos visuales mediante cabezas de jugador, con drops MMOItems y experiencia opcional de MMOCore.

## Corrección 1.0.9: protección contra líquidos

Las cabezas internas que representan vetas y nodos ya no pueden ser destruidas usando agua o lava.

La protección cubre:

- flujo natural de agua y lava hacia una veta o nodo;
- vaciado directo de cubetas sobre el bloque protegido;
- actualizaciones físicas provocadas por líquidos sobre cabezas marcadas por MDVHeadOres.

La protección solo reconoce cabezas con las claves persistentes propias del plugin. Las cabezas decorativas normales no se modifican. Cuando el recurso se rompe correctamente con la herramienta correspondiente, el líquido puede ocupar el espacio después.

Configuración:

```yaml
resource-protection:
  prevent-fluid-destruction: true
  fluids:
    - WATER
    - LAVA
```

## Compilación con Maven

Requisitos:

- Java 21
- Maven 3.9 o superior

```bash
mvn -B clean package
```

El JAR queda en:

```text
target/MDVHeadOres-1.0.9.jar
```

## GitHub Actions

El proyecto incluye `.github/workflows/build.yml`. Al subirlo a una rama `main` o `master`, GitHub compila el plugin con Java 21 y deja el JAR en los artefactos de la ejecución.

## Comandos

- `/mdvheadores reload`
- `/mdvheadores inspect`
- `/mdvheadores queue`
- `/mdvheadores generate`

Permiso administrativo: `mdvheadores.admin`.
