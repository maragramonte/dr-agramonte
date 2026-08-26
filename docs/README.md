# Documentación — Dr. Agramonte

El **README principal** ([../README.md](../README.md)) cubre qué es el proyecto, cómo
arrancarlo, la API y las decisiones técnicas. Aquí está todo lo demás: la memoria del
proyecto, los diagramas y las guías de detalle.

---

## Proyecto

| Documento | Contenido |
|-----------|-----------|
| [EVOLUCION.md](EVOLUCION.md) | Cómo creció el repositorio, fase a fase, y qué problema resolvió cada una |
| [BASE-DE-DATOS.md](BASE-DE-DATOS.md) | Esquema tabla a tabla, diagrama relacional, integridad referencial, concurrencia y preguntas frecuentes |
| [SINCRONIZACION-CITAS.md](SINCRONIZACION-CITAS.md) | Una sola fuente de verdad entre «Mis citas», el panel y la base de datos |
| [DOCKER-TROUBLESHOOTING.md](DOCKER-TROUBLESHOOTING.md) | Arranque de Docker, errores 502 y healthchecks |

## Memoria (LaTeX)

| Archivo | Contenido |
|---------|-----------|
| [Memoria-TFG-Dr-Agramonte.tex](Memoria-TFG-Dr-Agramonte.tex) | Memoria completa, alineada con el código real |
| [INSTRUCCIONES-MEMORIA-LATEX.md](INSTRUCCIONES-MEMORIA-LATEX.md) | Requisitos de formato y cómo compilar |
| [checklist-rubricas.tex](checklist-rubricas.tex) | Checklist de cumplimiento (anexo A) |
| `tikz-*.tex` | Diagramas vectoriales: arquitectura, modelo E-R, flujo de reserva, capas del frontend y paleta |
| [diagrama-er.svg](diagrama-er.svg) · [diagrama-er.png](diagrama-er.png) | Modelo entidad-relación exportado, para presentaciones |

## Defensa del proyecto

| Documento | Contenido |
|-----------|-----------|
| [PRESENTACION-DEFENSA.md](PRESENTACION-DEFENSA.md) | Ficha esencial: pitch de 60 s, números clave e ideas fuerza |
| [DEFENSA-ENSAYO.md](DEFENSA-ENSAYO.md) | Guion hablado cronometrado (15 min) y banco de preguntas |
| [Defensa-TFG-PowerPoint-Guion.md](Defensa-TFG-PowerPoint-Guion.md) | Contenido diapositiva a diapositiva |
| [GUIA-DEMO.md](GUIA-DEMO.md) | Flujo de la demo en vivo y qué hacer si algo se cae |

---

## Compilar la memoria

```powershell
cd docs
pdflatex Memoria-TFG-Dr-Agramonte.tex
pdflatex Memoria-TFG-Dr-Agramonte.tex
```

Dos pasadas: la primera genera el índice y las referencias, la segunda las coloca.
Salida: `Memoria-TFG-Dr-Agramonte.pdf`. Requiere MiKTeX o TeX Live; también funciona
subiendo la carpeta `docs/` a [Overleaf](https://www.overleaf.com/).

---

## Coherencia memoria ↔ código

La memoria describe **solo lo implementado**. Lo que no está construido va en «líneas
futuras», nunca en el cuerpo. Es la regla que mantiene el documento defendible.

Si cambias el flujo de reservas, el panel o el esquema, actualiza en el mismo momento:

- El [README principal](../README.md), si cambia la API o la puesta en marcha.
- [SINCRONIZACION-CITAS.md](SINCRONIZACION-CITAS.md), si cambian los endpoints o `syncMisCitas()`.
- [BASE-DE-DATOS.md](BASE-DE-DATOS.md) y el diagrama E-R, si añades una migración.
- La memoria LaTeX, y recompílala.
