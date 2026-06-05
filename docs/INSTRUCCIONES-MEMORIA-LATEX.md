# Memoria del TFG — requisitos y compilación

> **Índice de documentación:** [docs/README.md](README.md)
> **Historia y estado del proyecto:** [README.md](../README.md)

Este archivo recoge **cómo debe ser la memoria** (formato y estructura que exige el
centro) y **cómo compilar** el PDF. La memoria en LaTeX
(`Memoria-TFG-Dr-Agramonte.tex`) ya está alineada con estos requisitos.

---

## 1. Formato obligatorio

| Requisito | Norma | Estado en la plantilla |
|-----------|-------|------------------------|
| Entrega | **PDF** (no Word) | ✅ `pdflatex` genera PDF |
| Extensión | **35–65 páginas** (sin contar portada, índice, anexos, bibliografía ni imágenes/tablas/gráficos) | ⚠️ **Revisar al compilar**: ampliar redacción si no llega |
| Numeración | Todas las páginas numeradas **excepto la portada** | ✅ `titlepage` sin número; resto con `fancyhdr` |
| Fuente | **Arial 12**; títulos 14 o 16 | ✅ Helvetica (sustituto métrico de Arial) a 12 pt; para Arial exacto usar Overleaf/XeLaTeX |
| Interlineado | **1,15** | ✅ `\setstretch{1.15}` |
| Márgenes | **3 cm** en los cuatro lados | ✅ `geometry` 3 cm |
| Alineación | **Justificada** a izquierda y derecha | ✅ por defecto en `report` |
| Notas, figuras, tablas, gráficos | Tamaño **10** | ✅ `\captionsetup{font=footnotesize}` |

**Imágenes / tablas / gráficos:** integradas en el texto (no al final), con resolución
adecuada, **numeradas y con leyenda**. Si no son propias, citar la fuente en la leyenda
y en la bibliografía. No cuentan en el total de páginas.

---

## 2. Estructura mínima (en este orden)

1. **Portada** — título, autora, centro, ciclo, curso, tutores. Sin número de página.
2. **Resumen** — ideas clave y conclusiones (se redacta al final).
3. **Índice** — capítulos y subapartados con su página.
4. **Justificación** — por qué y para qué del proyecto.
5. **Objetivos** — metas específicas y alcanzables.
6. **Desarrollo**
   - **5.1 Contextualización del sector** — descripción del sector + riesgos laborales + medidas preventivas + EPIs.
   - **5.2 Desarrollo principal** — contenido central (arquitectura, backend, frontend, seguridad, despliegue, pruebas).
7. **Conclusiones** — ligadas a los objetivos + aprendizajes + reflexión.
8. **Bibliografía** — **normas APA**; toda fuente citada debe aparecer y viceversa.
9. **Anexos** *(opcional)* — material complementario (checklist, manual, capturas).

> La plantilla ya sigue este orden. **Cero plagio**: cualquier texto copiado supone
> suspenso directo.

---

## 3. Defensa oral (tenerlo en cuenta al redactar)

- Duración total **30 min** (exposición + preguntas).
- Soporte visual obligatorio (PowerPoint, Canva…), claro y con poco texto.
- **No leer** la memoria ni las diapositivas; ensayar y controlar el tiempo.
- Se valora: claridad, vocabulario técnico, orden e interacción con el tribunal.

---

## 4. Compilar

```powershell
cd docs
pdflatex Memoria-TFG-Dr-Agramonte.tex
pdflatex Memoria-TFG-Dr-Agramonte.tex
```

Requiere MiKTeX o TeX Live, o [Overleaf](https://www.overleaf.com/). Dos pasadas para
resolver índice y referencias.

## 5. Diagramas

Ver `tikz-diagrama-*.tex` y `tikz-paleta.tex` (colores alineados con
`frontend/css/dr-agramonte.css`).
