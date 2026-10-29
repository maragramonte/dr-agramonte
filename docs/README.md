# Documentación: Dr. Agramonte

El **README principal** ([../README.md](../README.md)) presenta el proyecto y enlaza lo
esencial. Aquí está el detalle, organizado por para qué sirve cada documento.

---

## Entender el proyecto

| Documento | Contenido |
|-----------|-----------|
| [ARQUITECTURA.md](ARQUITECTURA.md) | Cómo está construido: frontend, backend por capas, base de datos, flujo de una petición, autenticación, concurrencia, notificaciones e infraestructura |
| [DECISIONES-TECNICAS.md](DECISIONES-TECNICAS.md) | Por qué se eligió cada solución: contexto, alternativas, motivos, limitaciones y evidencia en el código |
| [EVOLUCION.md](EVOLUCION.md) | Cómo creció el repositorio, fase a fase, y qué problema resolvió cada una |

## Referencia técnica

| Documento | Contenido |
|-----------|-----------|
| [API.md](API.md) | Catálogo de endpoints, formas de autenticación y códigos de estado |
| [CONFIGURACION.md](CONFIGURACION.md) | Variables de entorno, canales de aviso, cuenta del médico y alta del bot de Telegram |
| [BASE-DE-DATOS.md](BASE-DE-DATOS.md) | Esquema tabla a tabla, diagrama relacional, integridad, concurrencia y preguntas frecuentes |
| [SINCRONIZACION-CITAS.md](SINCRONIZACION-CITAS.md) | Una sola fuente de verdad entre «Mis citas», el panel y la base de datos |

## Calidad y operación

| Documento | Contenido |
|-----------|-----------|
| [PRUEBAS-Y-CALIDAD.md](PRUEBAS-Y-CALIDAD.md) | Qué se comprueba y cómo, qué hace el CI y qué **no** queda cubierto |
| [DESPLIEGUE-VPS.md](DESPLIEGUE-VPS.md) | Puesta en producción en un VPS propio: Caddy y HTTPS, DNS, copias de seguridad y actualizaciones |
| [DOCKER-TROUBLESHOOTING.md](DOCKER-TROUBLESHOOTING.md) | Arranque de Docker, errores 502 y healthchecks |

## Enseñar y defender el proyecto

| Documento | Contenido |
|-----------|-----------|
| [DEMO.md](DEMO.md) | Cómo está hecha la demo pública, qué no puede demostrar y el guion del vídeo pendiente |
| [CAPTURAS.md](CAPTURAS.md) | La aplicación pantalla a pantalla, en imágenes |
| [DEFENSA-TECNICA.md](DEFENSA-TECNICA.md) | Guía para explicar y defender el proyecto en una entrevista, con repreguntas |

---

## Memoria (LaTeX)

| Archivo | Contenido |
|---------|-----------|
| [Memoria-Dr-Agramonte.tex](Memoria-Dr-Agramonte.tex) | Memoria completa, alineada con el código real |
| `tikz-*.tex` | Diagramas vectoriales: arquitectura, modelo E-R, flujo de reserva, capas del frontend y paleta |
| [diagrama-er.svg](diagrama-er.svg) · [diagrama-er.png](diagrama-er.png) | Modelo entidad-relación exportado, para presentaciones |

### Compilar la memoria

```powershell
cd docs
pdflatex Memoria-Dr-Agramonte.tex
pdflatex Memoria-Dr-Agramonte.tex
```

Dos pasadas: la primera genera el índice y las referencias, la segunda las coloca.
Salida: `Memoria-Dr-Agramonte.pdf`. Requiere MiKTeX o TeX Live; también funciona subiendo la
carpeta `docs/` a [Overleaf](https://www.overleaf.com/).

**Formato:** el preámbulo fija Helvetica a 12 pt (sustituto métrico de Arial; para Arial real
haría falta XeLaTeX), interlineado 1,15, márgenes de 3 cm y texto justificado. Las figuras y
tablas van numeradas y con leyenda, integradas en el texto.

---

## Mantener la documentación coherente

La regla de fondo: **se documenta solo lo implementado**. Lo que no está construido va en
«líneas futuras» o marcado como pendiente, nunca descrito como si funcionara.

Si cambias el flujo de reservas, el panel o el esquema, actualiza en el mismo momento:

| Si cambias… | Actualiza |
|-------------|-----------|
| La API | [API.md](API.md) y la colección de [`postman/`](../postman/README.md) |
| El esquema o una migración | [BASE-DE-DATOS.md](BASE-DE-DATOS.md) y el diagrama E-R |
| Los endpoints o `syncMisCitas()` | [SINCRONIZACION-CITAS.md](SINCRONIZACION-CITAS.md) |
| Las variables de entorno | [CONFIGURACION.md](CONFIGURACION.md) y las dos plantillas `.env*.example` |
| Una decisión de diseño | [DECISIONES-TECNICAS.md](DECISIONES-TECNICAS.md), con su evidencia |
| Las pruebas o el CI | [PRUEBAS-Y-CALIDAD.md](PRUEBAS-Y-CALIDAD.md), incluido el recuento |
| **La interfaz** | Regenera las capturas: `./scripts/capturar-pantallas.sh` |
| Cualquiera de lo anterior | La memoria LaTeX, y recompílala |

> Las dieciséis imágenes de `docs/capturas/` y `docs/demo-*.png` las genera
> [`scripts/capturar-pantallas.sh`](../scripts/capturar-pantallas.sh) contra la demo
> construida. No las edites a mano: se regeneran en un comando y así no se quedan viejas en
> silencio.
