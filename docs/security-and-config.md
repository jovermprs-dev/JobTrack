# Seguridad y configuración

> Estas medidas reducen riesgos habituales; **no** se afirma que el sistema sea «completamente seguro». Ver [límites](#límites-conocidos).

## Medidas implementadas

| Área | Medida | Dónde |
|---|---|---|
| Contraseñas | BCrypt (coste 12), 10–72 caracteres; nunca en logs ni respuestas; el hash se borra del principal de sesión | `SecurityConfig`, `AuthService`, `AppUserPrincipal` |
| Sesión | Al cambiar la contraseña se cierran las demás sesiones de la cuenta. Cookie `SESSION` HttpOnly, `SameSite=Lax`, `Secure` configurable, 8 h, almacenada en PostgreSQL (Spring Session JDBC); `changeSessionId` al iniciar sesión (anti fijación) | `SecurityConfig`, `AuthController` |
| CSRF | Cookie `XSRF-TOKEN` (legible por JS) + cabecera `X-XSRF-TOKEN` en toda escritura, incluido login/registro | `SecurityConfig` |
| Fuerza bruta | Máx. 5 intentos por email en 15 min → 429 (`Retry-After`); el intento se cuenta antes de comprobar la contraseña y de forma atómica. No usa la IP (las cabeceras de reenvío son falsificables; nginx descarta `Forwarded`). Mismo límite para adivinar la contraseña actual al cambiarla. En memoria y acotado (LRU 10 000): válido para una instancia | `LoginAttemptService` |
| Enumeración | Login con usuario inexistente o contraseña errónea devuelve la misma respuesta. (El registro sí revela que un email existe: limitación aceptada) | `ApiExceptionHandler` |
| Autorización | `userId` solo del `SecurityContext`; toda consulta filtra por `userId`; recurso ajeno ⇒ 404 | repositorios/servicios, `IsolationIntegrationTest` |
| Historial | Solo lo crea el servicio; el cliente no puede enviar estado anterior ni fecha | `OfferService.changeStatus` |
| Entrada | Límites de tamaño en login; `@Digits` en importes y años (evita números con escala absurda); Bean Validation en backend y formularios reactivos en frontend; URL solo `http(s)`; LIKE con comodines escapados; consultas parametrizadas (JPA/Criteria) | DTOs, `OfferSpecifications` |
| CSV | Prefijo `'` contra inyección de fórmulas al exportar; importación todo-o-nada, ≤ 1000 filas y 2 MB, UTF-8 estricto | `OfferCsvService` |
| Errores | `application/problem+json`; sin trazas ni detalles internos; 500 genérico | `ApiExceptionHandler` |
| Cabeceras | nginx: CSP estricta (`script-src 'self'`), `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`, `Permissions-Policy`; Spring: cabeceras por defecto | `frontend/nginx.conf` |
| Superficie | Actuator solo `health` (sin detalles) y no expuesto por nginx; Swagger/OpenAPI desactivados en `prod` | `application*.yml` |
| Contenedores | Backend y nginx sin root; BD solo en `127.0.0.1`; backend sin puerto publicado | Dockerfiles, `compose.yaml` |
| Secretos | `.env` en `.gitignore`; `.env.example` sin secretos; `POSTGRES_PASSWORD` obligatoria; CI ejecuta gitleaks | `.gitignore`, `.github/workflows/ci.yml` |

## Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `POSTGRES_PASSWORD` | sí | Contraseña de la BD (compose falla si falta). Usa un valor largo y aleatorio |
| `POSTGRES_DB`, `POSTGRES_USER` | no (`jobtrack`) | |
| `DB_HOST_PORT` | no (`55432`) | Puerto de la BD en `127.0.0.1` |
| `JOBTRACK_HTTP_PORT` | no (`8080`) | Puerto publicado de la aplicación |
| `JOBTRACK_BIND` | no (`0.0.0.0`) | Interfaz donde se publica la app; `127.0.0.1` tras un proxy TLS |
| `JOBTRACK_SECURE_COOKIES` | no (`true` en compose) | `true` bajo HTTPS (cookies `Secure`). Con `false` en un servidor público las cookies viajarían sin cifrar |
| `JOBTRACK_DB_URL/USER/PASSWORD` | solo fuera de compose | Perfil `prod` no tiene valores por defecto |
| `SERVER_PORT` | no | Puerto del backend (8081 en dev, 8080 en `prod`) |

Perfiles: sin perfil = desarrollo (cookies no `Secure`, Swagger activo, BD local `localhost:55432` con contraseña de desarrollo
**solo válida en local**); `prod` = sin Swagger, cookies `Secure` por defecto, sin credenciales por defecto. No existen usuarios de demostración.

## Definiciones de métricas

- **Candidaturas enviadas**: ofertas con `appliedDate` (se fija sola al pasar a «Candidatura enviada» si estaba vacía).
- **Procesos activos**: ofertas actualmente en `APPLIED`, `IN_PROCESS` o `INTERVIEW`.
- **Entrevistas / Ofertas recibidas**: ofertas distintas que **alguna vez** llegaron a `INTERVIEW` / `OFFER_RECEIVED` (según el historial).
- **Tasa de respuesta** = ofertas enviadas que alguna vez llegaron a `IN_PROCESS`, `INTERVIEW`, `OFFER_RECEIVED` o `REJECTED` ÷ ofertas enviadas.
  «Retirada» no cuenta como respuesta. Denominador = candidaturas enviadas; sin ninguna, no hay tasa (`null`). Con menos de 5 se marca `sufficientData=false` y la UI avisa.
- **Compatibilidad media**: media de las puntuaciones no nulas; se muestra cuántas ofertas tienen puntuación.
- **Evolución**: candidaturas por semana (lunes) de `appliedDate`, 12 semanas, rellenando con 0.
- **Empresas con más candidaturas**: top 5 por nº de candidaturas enviadas (sin distinguir mayúsculas).

## Límites conocidos

- Limitador de login en memoria (se pierde al reiniciar; no compartido entre réplicas) y por email: quien conozca un email puede bloquear los inicios de sesión de esa cuenta durante 15 min (denegación temporal, a cambio de no depender de la IP).
- Registro abierto sin verificación de email ni CAPTCHA; sin recuperación de contraseña por email.
- El registro no tiene límite de frecuencia (cada alta cuesta CPU de BCrypt); conviene un límite en el proxy en producción.
- Sin 2FA, sin auditoría de accesos, sin cifrado de datos en reposo más allá del disco del servidor.
- CSP permite `style-src 'unsafe-inline'` (Angular inyecta estilos de componente); los scripts no.
- Las dependencias no se analizan en cada build de forma exhaustiva (ver CI: `npm audit` y Dependabot).
- No se ha realizado una auditoría de seguridad externa.
