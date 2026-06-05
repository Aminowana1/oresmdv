# MDVHeadOres

Plugin pequeño para MDVCraft/Purpur/Paper 1.21.6.

Hace que en chunks nuevos aparezcan vetas visuales como `PLAYER_HEAD` con textura tomada desde un bloque de MMOItems, por ejemplo `VETACRISTALVE`.

Cuando el jugador rompe esa cabeza, el plugin cancela el drop vanilla y ejecuta un comando de MMOItems para dar el material configurado, por ejemplo `CRISTALVERDE`.

## Por qué no usa customblocks de MMOItems

Los custom blocks de MMOItems se ven como mushroom/callampa sin resource pack. Este plugin no usa esos bloques colocados: coloca una cabeza vanilla real con la textura del `skull-texture.value` del bloque de MMOItems.

## Instalación

1. Compilar el plugin con Maven:

```bash
mvn package
```

2. Copiar el jar generado:

```txt
target/MDVHeadOres-1.0.0.jar
```

a:

```txt
/plugins/MDVHeadOres-1.0.0.jar
```

3. Reiniciar el servidor.

4. Editar:

```txt
/plugins/MDVHeadOres/config.yml
```

5. Usar:

```txt
/mdvheadores reload
```

## Config para Viridita

Ya viene preparada para:

```txt
Bloque MMOItems: VETACRISTALVE
Drop MMOItems: CRISTALVERDE
```

El plugin lee la textura desde:

```txt
/plugins/MMOItems/item/block.yml
```

Busca:

```yml
VETACRISTALVE:
  base:
    skull-texture:
      value: ...
```

## Comandos

```txt
/mdvheadores reload
```

Recarga la config.

```txt
/mdvheadores generate 4
```

Genera vetas en chunks cargados alrededor del jugador en radio 4 chunks. Respeta la probabilidad y evita chunks ya marcados.

```txt
/mdvheadores generate 4 force
```

Prueba fuerte: ignora la marca del chunk y la probabilidad. Úsalo solo para test.

## Permiso

```txt
mdvheadores.admin
```

## Nota importante

Esto no hace que MMOItems customblocks maneje el drop directamente, porque la veta colocada es una `PLAYER_HEAD` vanilla, no un customblock de MMOItems. El item sí lo entrega MMOItems mediante comando:

```txt
mi give MATERIAL CRISTALVERDE <jugador> 1
```


## Compilar sin instalar Java ni Maven usando GitHub Actions

Este ZIP ya incluye:

```txt
.github/workflows/build.yml
```

Pasos:
1. Crea un repositorio en GitHub.
2. Sube todos los archivos de esta carpeta.
3. Entra a la pestaña `Actions`.
4. Abre `Build MDVHeadOres`.
5. Pulsa `Run workflow`.
6. Cuando termine, descarga el artifact `MDVHeadOres-jar`.
7. Dentro estará el `.jar` para subir a `/plugins/`.
