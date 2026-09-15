# MDVHeadOres 1.3.0 - Actualización

## Compatibilidad

- No cambia el formato de `config.yml`.
- No cambia el formato de `lootnodes.yml`.
- No cambia los tracking-bit existentes.
- No borra ni regenera recursos ya colocados.
- Basta reemplazar el JAR y reiniciar completamente el servidor.

## Nuevos comandos

### Generar un loot node exacto en tu posición

```text
/mdvheadores loot spawn <id>
```

El comando coloca el nodo en el bloque de los pies del administrador usando las mismas reglas físicas de soporte y espacio que la generación natural. No consume probabilidades ni modifica el tracking-bit del chunk. Puede usarse con nodos desactivados para pruebas.

Ejemplo:

```text
/mdvheadores loot spawn bolsa_abandonada
```

### Obtener una cabeza colocable de un recurso

```text
/mdvheadores head ore <id> [cantidad]
/mdvheadores head node <id> [cantidad]
```

También se puede omitir `ore/node` si el ID es único:

```text
/mdvheadores head nimbrel 8
```

La cabeza lleva una marca administrativa. Al colocarla se convierte en un recurso real de MDVHeadOres: conserva ID, drop, poder de herramienta, XP de MMOCore y protecciones normales.

## Loot de MMOItems

Las recompensas MMOItems de equipamiento que salen de loot nodes ahora siguen este flujo:

1. Se genera una instancia nueva desde el `MMOItemTemplate`.
2. MMOItems tira sus modifiers aleatorios configurados, si el item dispone de ellos.
3. Esa instancia ya aleatorizada se convierte en item no identificado.
4. Al identificarla posteriormente se revelan los modifiers que quedaron guardados en esa tirada.

Esto se aplica a armas, armaduras, accesorios, herramientas y tipos de equipamiento personalizados (incluyendo `ARMAS_MAGICAS` y `SOPORTE_MAGICO`). Materiales, consumibles, vanilla y MythicMobs conservan su comportamiento normal.

El editor de loot mantiene previews normales para que editar recompensas siga siendo cómodo.
