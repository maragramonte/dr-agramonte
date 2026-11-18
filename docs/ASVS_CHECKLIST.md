# Checklist ASVS

Requisitos de [OWASP ASVS 4.0.3](https://github.com/OWASP/ASVS/tree/v4.0.3/4.0/en) que
afectan a este proyecto, con su estado y dónde se comprueba. Los identificadores son los de
la 4.0.3. La 5.0 renumeró los capítulos y todavía no he hecho la correspondencia.

No están todos los requisitos: faltan los que no aplican (OAuth, subida de ficheros, MFA
por OTP, servicios SOAP...) y los de nivel 3. Los riesgos `Rn` remiten al
[modelo de amenazas](THREAT_MODEL.md#5-riesgos-abiertos).

**Estado:** ✅ cumplido · ⚠️ parcial · ❌ pendiente · — no aplica hoy

Abreviaturas de rutas: `main/` = `backend/src/main/java/com/saludybienestar/agramonte/`,
`test/` = `backend/src/test/java/com/saludybienestar/agramonte/`.

## V1 Arquitectura

| Id | Requisito | Estado | Evidencia | Nota |
|---|---|---|---|---|
| 1.1.2 | Hay un modelo de amenazas | ⚠️ | [THREAT_MODEL.md](THREAT_MODEL.md) | Existe, pero nada obliga a revisarlo cuando cambia el diseño |
| 1.4.1 | El control de acceso se aplica en un punto de confianza del servidor | ✅ | [main/config/SecurityConfig.java](../backend/src/main/java/com/saludybienestar/agramonte/config/SecurityConfig.java) | |

## V2 Autenticación

| Id | Requisito | Estado | Evidencia | Nota |
|---|---|---|---|---|
| 2.1.1 | Contraseñas de 12 caracteres como mínimo | ❌ | [main/dto/request/ActivacionRequest.java](../backend/src/main/java/com/saludybienestar/agramonte/dto/request/ActivacionRequest.java) (`min = 6`) | R13 |
| 2.1.7 | Se rechazan contraseñas filtradas | ❌ | | |
| 2.2.1 | Freno a la fuerza bruta | ⚠️ | [main/security/RateLimitFilter.java](../backend/src/main/java/com/saludybienestar/agramonte/security/RateLimitFilter.java), [test/security/RateLimitFilterTest.java](../backend/src/test/java/com/saludybienestar/agramonte/security/RateLimitFilterTest.java), [test/controller/AuthControllerRateLimitWebMvcTest.java](../backend/src/test/java/com/saludybienestar/agramonte/controller/AuthControllerRateLimitWebMvcTest.java) | Solo por IP (R6) |
| 2.3.1 | Códigos de activación aleatorios y de vida corta | ✅ | [main/security/TokenOpaco.java](../backend/src/main/java/com/saludybienestar/agramonte/security/TokenOpaco.java) (32 bytes de `SecureRandom`), [main/service/ActivacionCuentaService.java](../backend/src/main/java/com/saludybienestar/agramonte/service/ActivacionCuentaService.java) (30 min), [test/integration/ActivacionCuentaIntegrationTest.java](../backend/src/test/java/com/saludybienestar/agramonte/integration/ActivacionCuentaIntegrationTest.java) | |
| 2.4.1 | Contraseñas guardadas con una función de derivación con sal | ✅ | `SecurityConfig.passwordEncoder()` | BCrypt |
| 2.4.4 | BCrypt con coste 10 o más | ✅ | `SecurityConfig.passwordEncoder()` | Coste 12 |

## V3 Sesión

| Id | Requisito | Estado | Evidencia | Nota |
|---|---|---|---|---|
| 3.2.1 | Token nuevo en cada autenticación | ✅ | [main/service/AuthService.java](../backend/src/main/java/com/saludybienestar/agramonte/service/AuthService.java) | |
| 3.2.2 | Tokens con 64 bits de entropía como mínimo | ✅ | `TokenOpaco` (256 bits); JWT firmado con HS256 | |
| 3.3.1 | Cerrar sesión invalida el token | ❌ | | R1 |
| 3.3.2 | Hay que volver a autenticarse cada cierto tiempo | ✅ | `jwt.expiration` en [application-postgres.yml](../backend/src/main/resources/application-postgres.yml) (24 h) | Nivel 1 pide 30 días como máximo |
| 3.3.3 | Opción de cerrar las demás sesiones al cambiar la contraseña | ❌ | | R1 |
| 3.4.1–3.4.3 | Cookies de sesión con `Secure`, `HttpOnly` y `SameSite` | — | | Hoy no hay cookies |
| 3.5.3 | Tokens sin estado firmados y protegidos contra manipulación | ⚠️ | [main/security/JwtProvider.java](../backend/src/main/java/com/saludybienestar/agramonte/security/JwtProvider.java), [test/security/JwtProviderTest.java](../backend/src/test/java/com/saludybienestar/agramonte/security/JwtProviderTest.java) (firma distinta, caducado, basura) | Falta un test de `alg: none` |

## V4 Control de acceso

| Id | Requisito | Estado | Evidencia | Nota |
|---|---|---|---|---|
| 4.1.1 | Se aplica en el servidor | ✅ | `SecurityConfig.reglasDeAcceso` | |
| 4.1.2 | El usuario no puede manipular los atributos que deciden el acceso | ✅ | [main/security/JwtAuthFilter.java](../backend/src/main/java/com/saludybienestar/agramonte/security/JwtAuthFilter.java) lee el rol de la BD; los controladores usan el principal | |
| 4.1.3 | Mínimo privilegio y denegación por defecto | ✅ | `anyRequest().denyAll()`; test `rutaNoDeclarada_conSesion_403` en [test/controller/CitaControllerWebMvcTest.java](../backend/src/test/java/com/saludybienestar/agramonte/controller/CitaControllerWebMvcTest.java) | |
| 4.1.5 | El control de acceso falla de forma segura | ✅ | [test/security/JwtAuthFilterTest.java](../backend/src/test/java/com/saludybienestar/agramonte/security/JwtAuthFilterTest.java) (cuenta desactivada → anónima) | |
| 4.2.1 | Protección frente a IDOR | ✅ | `CitaService.cancelarCita`, `CitaController.agendaDe`; tests `cancelarCita_noPropietario_lanza403` ([CitaServiceTest](../backend/src/test/java/com/saludybienestar/agramonte/service/CitaServiceTest.java)), `agendaPacientes_medicoPideLaDeOtro_recibeLaSuya` (CitaControllerWebMvcTest), `agendaDeOtroMedico_noSeFiltraNingunDato` ([ReservaPublicaIntegrationTest](../backend/src/test/java/com/saludybienestar/agramonte/integration/ReservaPublicaIntegrationTest.java)), `tokenDeUnCorreo_noAbreLaCitaDeOtro` ([AccesoCitaPorCorreoIntegrationTest](../backend/src/test/java/com/saludybienestar/agramonte/integration/AccesoCitaPorCorreoIntegrationTest.java)) | Falta un test HTTP con dos pacientes reales; el 403 delata el id (R8) |
| 4.2.2 | Protección CSRF | ✅ | Comentario y `csrf.disable()` en `SecurityConfig`; test `apiSinCookiesNiSesion` en ReservaPublicaIntegrationTest | Sin cookies no hay CSRF. Hay que reactivarlo si entra una cookie |
| 4.3.1 | Las interfaces de administración usan MFA | ❌ | | R14 |

## V5 Validación y codificación

| Id | Requisito | Estado | Evidencia | Nota |
|---|---|---|---|---|
| 5.1.3 | Validación de la entrada en el servidor | ✅ | `@Valid` y anotaciones en `main/dto/request/` | |
| 5.3.3 | Codificación de salida según contexto contra XSS | ✅ | [frontend/js/modules/html.js](../frontend/js/modules/html.js) | |
| 5.3.4 | Consultas parametrizadas | ✅ | Spring Data JPA; no hay SQL nativo concatenado en `main/` | |

## V6 Criptografía

| Id | Requisito | Estado | Evidencia | Nota |
|---|---|---|---|---|
| 6.3.1 | Números aleatorios de un generador criptográfico | ✅ | `TokenOpaco`, [main/security/TokenAccesoCita.java](../backend/src/main/java/com/saludybienestar/agramonte/security/TokenAccesoCita.java) | |
| 6.4.1 | Secretos fuera del código | ⚠️ | [docker-compose.prod.yml](../docker-compose.prod.yml) exige `JWT_SECRET` y `MEDICO_PASSWORD`; `JwtProvider.comprobarSecreto` avisa del secreto de desarrollo | Variables de entorno, sin gestor de secretos |

## V7 Errores y logs

| Id | Requisito | Estado | Evidencia | Nota |
|---|---|---|---|---|
| 7.1.1 | No se registran credenciales ni tokens | ⚠️ | Ningún token de sesión, cita ni activación se escribe en el log | El token del bot de Telegram puede colarse en un error (R15) |
| 7.1.2 | No se registran datos personales sensibles | ⚠️ | Ids y valores enmascarados en todos los `log.info/warn` | En debug sale el texto del aviso (R9); las trazas pueden llevar valores (R10) |
| 7.4.1 | Mensaje de error genérico al cliente | ✅ | `GlobalExceptionHandler.handleGeneric`, [test/controller/GlobalExceptionHandlerTest.java](../backend/src/test/java/com/saludybienestar/agramonte/controller/GlobalExceptionHandlerTest.java) | |

## V8 Protección de datos

| Id | Requisito | Estado | Evidencia | Nota |
|---|---|---|---|---|
| 8.2.1 | Cabeceras anti caché en respuestas con datos sensibles | ✅ | Cabeceras por defecto de Spring Security, que `SecurityConfig` no desactiva | Sin test |
| 8.2.2 | El almacenamiento del navegador no guarda datos sensibles | ❌ | [frontend/js/modules/auth-ui.js](../frontend/js/modules/auth-ui.js) guarda JWT, email, nombre y teléfono | R2 |
| 8.3.1 | Los datos sensibles no viajan en la query string | ✅ | `X-Cita-Token` en cabecera; enlaces del correo con el token en el fragmento ([main/service/CorreoSaliente.java](../backend/src/main/java/com/saludybienestar/agramonte/service/CorreoSaliente.java)) | |
| 8.3.2 | El usuario puede exportar o borrar sus datos | ❌ | | A mano, por el formulario de contacto |
| 8.3.3 | Información clara y consentimiento | ✅ | [frontend/privacidad.html](../frontend/privacidad.html); aceptación guardada por cita (V15) y test `reservaPublica_sinAceptarPrivacidad_400` | |
| 8.3.5 | Se registra el acceso a datos sensibles | ❌ | | R16 |
| 8.3.8 | Los datos personales tienen plazo de conservación | ❌ | | No hay borrado automático. Ver [THREAT_MODEL.md § 6](THREAT_MODEL.md#6-datos-personales-rgpd) |

## V9 Comunicaciones

| Id | Requisito | Estado | Evidencia | Nota |
|---|---|---|---|---|
| 9.1.1 | TLS en todas las conexiones del cliente | ✅ | [Caddyfile](../Caddyfile) | |

## V11 Lógica de negocio

| Id | Requisito | Estado | Evidencia | Nota |
|---|---|---|---|---|
| 11.1.4 | Freno a la automatización de operaciones de negocio | ⚠️ | `RateLimitFilter` en la reserva sin cuenta | `POST /api/citas` sin límite ni tope por paciente (R5) |
| 11.1.6 | Sin condiciones de carrera en las operaciones críticas | ✅ | `SELECT ... FOR UPDATE` en [main/repository/HorarioRepository.java](../backend/src/main/java/com/saludybienestar/agramonte/repository/HorarioRepository.java); prueba concurrente con `CyclicBarrier` en ReservaPublicaIntegrationTest | |

## V14 Configuración

| Id | Requisito | Estado | Evidencia | Nota |
|---|---|---|---|---|
| 14.2.1 | Componentes al día y sin vulnerabilidades conocidas | ✅ | [.github/dependabot.yml](../.github/dependabot.yml), [.github/workflows/seguridad.yml](../.github/workflows/seguridad.yml), [.github/workflows/semgrep.yml](../.github/workflows/semgrep.yml) | |
| 14.4.3 | Content-Security-Policy | ✅ | `Caddyfile`, [frontend/nginx.conf](../frontend/nginx.conf) | `'unsafe-inline'` solo en estilos |
| 14.4.4 | `X-Content-Type-Options: nosniff` | ✅ | `Caddyfile` | |
| 14.4.5 | HSTS | ✅ | `Caddyfile` | |
| 14.5.3 | CORS con lista cerrada de orígenes | ✅ | `SecurityConfig.corsConfigurationSource` | Solo orígenes de desarrollo; en producción, mismo origen |
