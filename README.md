# JobTrack

Aplicación web para **organizar, gestionar y analizar tu búsqueda de empleo**: guarda ofertas, sigue cada candidatura con su historial,
calcula la compatibilidad entre tu perfil y cada oferta y consulta estadísticas calculadas con tus propios datos.

Proyecto de portfolio: Angular 22 + Spring Boot 4 (Java 21) + PostgreSQL 16, con Docker Compose, CI en GitHub Actions y pruebas de integración contra PostgreSQL real.

## Funcionalidades

- Registro, inicio/cierre de sesión (cookie de sesión HttpOnly + CSRF) y cambio de contraseña.
- **Ofertas**: CRUD completo, búsqueda, filtros combinables (estado, empresa, ubicación, modalidad, favoritas), orden, paginación en el backend.
- **Estados** (8) con **historial** automático (estado anterior, nuevo, fecha y nota) que el cliente no puede falsificar.
- **Favoritas**, **perfil profesional** y **compatibilidad perfil ↔ oferta** determinista y explicada ([algoritmo](docs/compatibility.md)).
- **Panel** y **estadísticas** con datos reales (tasa de respuesta con denominador explícito, evolución semanal, empresas, compatibilidad media).
- **Importar / exportar CSV** (vista previa, todo-o-nada, protección contra inyección de fórmulas).
- Interfaz responsive y accesible (teclado, etiquetas, contraste), estados de carga / vacío / error y confirmación de acciones destructivas.

## Puesta en marcha (Docker Compose)

Requisitos: Docker con Compose v2 (Docker Desktop o Docker Engine). No hace falta instalar PostgreSQL, Java ni Node.

```bash
git clone <url-del-repositorio> jobtrack && cd jobtrack
cp .env.example .env
# Edita .env y pon una POSTGRES_PASSWORD larga y aleatoria (p. ej. openssl rand -hex 24)
docker compose up -d --build
```

Abre **http://localhost:8080**, crea una cuenta y empieza. El primer arranque compila las imágenes (unos minutos).
Los datos persisten en el volumen `jobtrack_db-data`. Para parar: `docker compose down` (añade `-v` solo si quieres **borrar** los datos).

## Desarrollo local

Requisitos: JDK 21, Node 24, Docker (para PostgreSQL y las pruebas).

```bash
docker compose up -d db                          # PostgreSQL en 127.0.0.1:55432
cd backend && ./mvnw spring-boot:run             # API en http://localhost:8081 (Swagger: /swagger-ui.html)
cd frontend && npm ci && npm start               # SPA en http://localhost:4200 (proxy /api → :8081)
```

> En macOS con varios JDK, exporta antes `JAVA_HOME` a un JDK 21 (`/usr/libexec/java_home -v 21`).
> El `compose.yaml` publica la BD en un puerto alto de loopback (55432) para no chocar con un PostgreSQL local en 5432/5433.

## Pruebas

```bash
cd backend  && ./mvnw verify                      # unitarias + integración (Testcontainers: requiere Docker en marcha)
cd frontend && npm test -- --watch=false          # Vitest
cd frontend && npm run build                      # build de producción
docker compose up -d --build && cd e2e && npm ci && npx playwright install chromium && npm test   # E2E en navegador real
```

Estrategia y alcance: [docs/testing.md](docs/testing.md).

## Estructura

```
backend/    API Spring Boot (paquetes por funcionalidad: auth, offer, profile, compatibility, stats)
frontend/   SPA Angular + nginx (sirve la SPA y reenvía /api)
e2e/        Recorridos de aceptación con Playwright
docs/       Arquitectura, modelo de datos, API, seguridad, algoritmo, pruebas, despliegue
deploy/     Caddyfile para HTTPS en un VPS
compose.yaml · compose.prod.yaml (Caddy) · compose.traefik.yaml (Traefik compartido) · .env.example · .github/workflows/ci.yml
```

## Documentación

[Arquitectura y decisiones](docs/architecture.md) · [Modelo de datos](docs/data-model.md) · [Contrato de la API](docs/api.md) ·
[Seguridad y configuración](docs/security-and-config.md) · [Compatibilidad](docs/compatibility.md) · [Pruebas](docs/testing.md) ·
[Despliegue en VPS](docs/deployment.md) · [Progreso](ROADMAP.md)

## Resolución de problemas

| Síntoma | Causa / solución |
|---|---|
| `POSTGRES_PASSWORD` obligatoria al hacer `docker compose up` | Falta `.env`: `cp .env.example .env` y edítalo |
| `Cannot connect to the Docker daemon` | Arranca Docker Desktop / el servicio de Docker |
| `port is already allocated` / `address already in use` | Cambia `JOBTRACK_HTTP_PORT` o `DB_HOST_PORT` en `.env` |
| `./mvnw verify` falla con `Could not find a valid Docker environment` | Las pruebas de integración necesitan Docker en marcha |
| `error: invalid target release: 21` | `java -version` no es 21: exporta `JAVA_HOME` a un JDK 21 |
| Tras iniciar sesión vuelve al login (en un servidor) | Con HTTPS usa `JOBTRACK_SECURE_COOKIES=true`; con HTTP debe ser `false` |
| Código 403 en operaciones de escritura (desarrollo) | Accede por `localhost:4200` (proxy) para que la cookie `XSRF-TOKEN` llegue al navegador |
| La BD arranca vacía tras cambiar la contraseña | La contraseña solo se aplica al crear el volumen; usa `docker compose down -v` (borra datos) o cámbiala con `ALTER USER` |

## Limitaciones conocidas

Sin login con Google ni recuperación de contraseña por email; registro abierto sin verificación; limitador de login en memoria; la
compatibilidad no entiende sinónimos ni texto libre; sin scraping de portales (las ofertas se introducen a mano o por CSV). Detalle en
[seguridad](docs/security-and-config.md#límites-conocidos).
