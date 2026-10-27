# Reporte de contraste (ET-UI-001, criterio CA-02)

Relación calculada con la luminancia relativa de WCAG 2.1 sobre los valores de `colors.xml`.

| Combinación implementada | Relación | Criterio WCAG | Mínimo | Resultado |
|---|---|---|---|---|
| Botón principal: texto Azul sobre Naranja (HAL-01 opción A) | 5.39:1 | 1.4.3 | 4.5:1 | Cumple |
| Texto blanco sobre Azul (cabeceras, nav activa, toolbar) | 15.39:1 | 1.4.3 | 4.5:1 | Cumple |
| Texto naranja 'GO!' sobre Azul | 5.39:1 | 1.4.3 | 4.5:1 | Cumple |
| Título Azul sobre Gris Claro (fondo) | 14.68:1 | 1.4.3 | 4.5:1 | Cumple |
| Título Azul sobre tarjeta Blanca | 15.39:1 | 1.4.3 | 4.5:1 | Cumple |
| Cuerpo Gris Asfalto sobre Gris Claro | 7.18:1 | 1.4.3 | 4.5:1 | Cumple |
| Cuerpo Gris Asfalto sobre tarjeta Blanca | 7.53:1 | 1.4.3 | 4.5:1 | Cumple |
| Nav inactiva: Gris Asfalto sobre Blanco | 7.53:1 | 1.4.3 | 4.5:1 | Cumple |
| Borde naranja de estación seleccionada sobre Blanco (no textual) | 2.86:1 | 1.4.11 | 3.0:1 | **NO CUMPLE** (ver nota) |
| Borde de campo en reposo Gris Asfalto sobre Gris Claro (no textual) | 7.18:1 | 1.4.11 | 3.0:1 | Cumple |
| Borde de error Rojo sobre Gris Claro (no textual; nunca texto) | 3.94:1 | 1.4.11 | 3.0:1 | Cumple |

Combinaciones exentas o no usadas:

- Texto inactivo `#718096` sobre fondo inactivo `#CBD5E0`: exento (control deshabilitado, apartado 6.2).
- Blanco sobre Naranja (2.86:1): NO se usa en ningún texto; es la combinación del hallazgo HAL-01.
- El Rojo `#E53E3E` solo se usa como borde; el texto de error va en Gris Asfalto.

Pendiente CA-06: prueba de legibilidad en exteriores con evidencia fotográfica (requiere dispositivo físico).

## Nota sobre el borde naranja

El apartado 7.2 de ET-UI-001 ordena el borde `#FF6B00` para la estación seleccionada, y ese color sobre blanco da 2.86:1 (< 3:1 de WCAG 1.4.11). Se implementó tal cual se especifica y se acompañó **siempre** de la etiqueta de texto "Estación seleccionada" (Azul sobre Blanco, 15.39:1), por lo que el estado no depende del color (1.4.1). Se recomienda al propietario resolverlo junto con HAL-01 (por ejemplo, borde Azul Confianza con acento naranja).
