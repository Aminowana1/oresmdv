# Migración de 1.0.9 a 1.1.0

## Secciones globales nuevas

Añadir `generation-tracking`, las tres protecciones nuevas, el escaneo periódico y las opciones de debug del `config.yml` incluido.

## Bits asignados

```text
0  viridita
1  amatista_encantada
2  umbrita
3  nimbrel
4  oricalco
5  mithril
6  nudo_vivo
7  tronco_corrupto
8  nudo_runico
9  savia_aurea
10 nudo_primordial
```

No reutilizar ni cambiar estos números. El próximo recurso debe empezar en `tracking-bit: 11`.

## Primera carga

No hace falta ejecutar un comando global de regeneración. Los chunks se actualizan de forma progresiva cuando:

- se cargan por un jugador;
- ya estaban cargados durante el arranque o `/reload`;
- son encontrados por el escaneo periódico.

Los cinco recursos antiguos se consideran ya tirados en chunks existentes. Solo se procesan los seis nuevos.
