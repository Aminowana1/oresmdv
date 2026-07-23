# Pruebas manuales de MDVHeadOres 1.0.9

1. Generar o localizar una veta `PLAYER_HEAD` marcada por el plugin.
2. Colocar agua al lado para que intente fluir hacia la cabeza.
   - La cabeza debe permanecer.
   - El agua no debe ocupar ese bloque.
3. Vaciar una cubeta directamente sobre la veta o nodo.
   - El evento debe cancelarse.
   - La cabeza debe permanecer.
4. Repetir con un nodo `PLAYER_WALL_HEAD`.
5. Repetir con lava si está incluida en `resource-protection.fluids`.
6. Probar una cabeza decorativa normal.
   - El plugin no debe protegerla.
7. Romper la veta o nodo con la herramienta y poder correctos.
   - Debe entregar el drop y XP normales.
   - Tras desaparecer el recurso, el líquido puede avanzar al espacio libre.
8. Ejecutar `/mdvheadores reload` y repetir las pruebas.
