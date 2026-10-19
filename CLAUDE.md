# JobTrack — guía operativa

App fullstack para gestionar la búsqueda de empleo. Angular 22 (`frontend/`) + Spring Boot 4 / Java 21 (`backend/`) + PostgreSQL 16.
Docs: `docs/` (arquitectura, modelo de datos, API, seguridad). Progreso: `ROADMAP.md`.

## Comandos

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH   # este equipo: java por defecto es 18
docker compose up -d --build            # stack completo → http://localhost:8080 (nginx); requiere .env (cp .env.example .env)
cd backend && ./mvnw verify             # compila + tests (Testcontainers requiere Docker en marcha)
cd frontend && npm ci && npm test -- --watch=false && npm run build
cd backend && ./mvnw spring-boot:run    # dev en :8081; necesita Postgres: docker compose up -d db (127.0.0.1:55432)
cd frontend && npm start                # dev en :4200 con proxy /api → :8081
cd e2e && npm test                      # E2E Playwright contra http://localhost:8080 (stack levantado)
```

## Convenciones

- Backend por paquetes de funcionalidad (`auth`, `offer`, `profile`, `compatibility`, `stats`); DTO = `record`; esquema SOLO vía Flyway.
- **Seguridad**: el `userId` sale del `SecurityContext`, jamás del cliente. Todo repositorio de datos privados filtra por `userId`; recurso ajeno ⇒ 404.
- Sesión por cookie HttpOnly + CSRF; nada de tokens en localStorage. No debilitar protecciones para que pasen tests.
- «Desconocido» = `NULL`; no confundir con `0`/`false`. No inventar datos.
- Frontend: componentes standalone, signals, formularios reactivos, SCSS con variables de `styles/_tokens.scss`. Sin librerías de UI/gráficos (SVG propio).
- **Modo demo** (`JOBTRACK_DEMO_MODE=true`, `com.jobtrack.demo`): instancia pública de solo lectura; el servidor rechaza con 403 todo método que no sea de lectura. No añadas rutas que eludan ese filtro ni lo conviertas en una lista por ruta; la interfaz solo oculta controles.
- Tests nuevos con cada funcionalidad; no desactivar ni borrar tests para que pase el build.

## Estructura

`backend/src/main/java/com/jobtrack/{auth,offer,profile,compatibility,stats,common,config}` · `frontend/src/app/{core,shared,layout,features}` · `docs/` · `e2e/` · `deploy/`.
Tests de integración: `backend/src/test/.../support/TestApi` (flujo CSRF real). Docker Desktop: `open -a Docker`.

## Verificación mínima antes de dar algo por hecho

`./mvnw verify` (backend) y `npm test` + `npm run build` (frontend) en verde; para cambios de UI, arrancar y comprobar en navegador.
No commitear `.env` ni secretos. No publicar/desplegar sin autorización del usuario.
