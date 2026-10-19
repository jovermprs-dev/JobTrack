# JobTrack — guía operativa

App fullstack para gestionar la búsqueda de empleo. Angular 22 (`frontend/`) + Spring Boot 4 / Java 21 (`backend/`) + PostgreSQL 16.
Docs: `docs/` (arquitectura, modelo de datos, API, seguridad). Progreso: `ROADMAP.md`.

## Comandos

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH   # este equipo: java por defecto es 18
docker compose up -d --build            # stack completo → http://localhost:8080 (nginx)
cd backend && ./mvnw verify             # compila + tests (Testcontainers requiere Docker en marcha)
cd frontend && npm ci && npm test -- --watch=false && npm run build
cd backend && ./mvnw spring-boot:run    # dev; necesita Postgres: docker compose up -d db
cd frontend && npm start                # dev con proxy a :8080
```

## Convenciones

- Backend por paquetes de funcionalidad (`auth`, `offer`, `profile`, `compatibility`, `stats`); DTO = `record`; esquema SOLO vía Flyway.
- **Seguridad**: el `userId` sale del `SecurityContext`, jamás del cliente. Todo repositorio de datos privados filtra por `userId`; recurso ajeno ⇒ 404.
- Sesión por cookie HttpOnly + CSRF; nada de tokens en localStorage. No debilitar protecciones para que pasen tests.
- «Desconocido» = `NULL`; no confundir con `0`/`false`. No inventar datos.
- Frontend: componentes standalone, signals, formularios reactivos, SCSS con variables de `styles/_tokens.scss`. Sin librerías de UI/gráficos (SVG propio).
- Tests nuevos con cada funcionalidad; no desactivar ni borrar tests para que pase el build.

## Verificación mínima antes de dar algo por hecho

`./mvnw verify` (backend) y `npm test` + `npm run build` (frontend) en verde; para cambios de UI, arrancar y comprobar en navegador.
No commitear `.env` ni secretos. No publicar/desplegar sin autorización del usuario.
