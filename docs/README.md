# Documentación académica — Dr. Agramonte

La **historia completa del proyecto**, arranque técnico, API, Twilio y guía de demo están en el README principal del repositorio:

**[../README.md](../README.md)**

Este directorio concentra la **memoria del TFG**, los **diagramas LaTeX** y guías operativas.

---

## Contenido de `docs/`

| Archivo | Para qué sirve |
|---------|----------------|
| [SINCRONIZACION-CITAS.md](SINCRONIZACION-CITAS.md) | **Control Mis citas ↔ Panel ↔ BD** (pruebas, fallos, Postman) |
| [DOCKER-TROUBLESHOOTING.md](DOCKER-TROUBLESHOOTING.md) | Arranque Docker, 502, healthchecks |
| [Memoria-TFG-Dr-Agramonte.tex](Memoria-TFG-Dr-Agramonte.tex) | Memoria en LaTeX (alineada con el código real, mayo 2026) |
| [INSTRUCCIONES-MEMORIA-LATEX.md](INSTRUCCIONES-MEMORIA-LATEX.md) | Cómo compilar el PDF (`pdflatex`) |
| [checklist-rubricas.tex](checklist-rubricas.tex) | Checklist 25 % / 40 % / 35 % (rúbricas módulo) |
| [tikz-paleta.tex](tikz-paleta.tex) | Colores teal del CSS (`#0F766E`, `#14B8A6`) |
| [tikz-diagrama-arquitectura.tex](tikz-diagrama-arquitectura.tex) | Diagrama Docker + nginx + Spring + PostgreSQL |
| [tikz-diagrama-er.tex](tikz-diagrama-er.tex) | Modelo entidad-relación |
| [tikz-diagrama-reserva.tex](tikz-diagrama-reserva.tex) | Flujo reserva + concurrencia |
| [tikz-diagrama-frontend.tex](tikz-diagrama-frontend.tex) | Capas frontend y PWA |
| [Boceto WEBAPP.tex](Boceto%20WEBAPP.tex) | Boceto inicial (especificación funcional antigua) |

---

## Compilar la memoria

```powershell
cd docs
pdflatex Memoria-TFG-Dr-Agramonte.tex
pdflatex Memoria-TFG-Dr-Agramonte.tex
```

Salida: `Memoria-TFG-Dr-Agramonte.pdf`

Requiere MiKTeX o TeX Live. También puedes subir la carpeta `docs/` a [Overleaf](https://www.overleaf.com/).

---

## Coherencia memoria ↔ código

La memoria describe **solo lo implementado**: PostgreSQL, Spring Boot, Twilio en Java, PWA, Docker.  
No incluye MongoDB, Redis ni worker Node (no están en el repositorio).

Si cambias el código de reservas o del panel, actualiza:

- [README raíz](../README.md) — sección **3.1** y línea de tiempo (Fase 7).  
- Este archivo [SINCRONIZACION-CITAS.md](SINCRONIZACION-CITAS.md) si cambian endpoints o `syncMisCitas`.  
- La memoria LaTeX si el tribunal debe leer el flujo invitado/sincronización.
