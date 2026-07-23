# Changelog

## 1.0.9

- Corrige la destrucción de vetas y nodos al entrar agua o lava en el bloque de cabeza.
- Cancela el flujo de líquidos hacia recursos marcados por MDVHeadOres.
- Cancela cubetas vaciadas directamente sobre esos recursos.
- Añade una protección física limitada a actualizaciones causadas por líquidos.
- Añade `resource-protection.prevent-fluid-destruction` y `resource-protection.fluids`.
- Mantiene intactas las cabezas decorativas que no pertenecen al plugin.
- Actualiza el proyecto Maven y el flujo de compilación para Java 21.
