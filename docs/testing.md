# Estrategia de pruebas

| Capa | Herramienta | Qué cubre | Comando |
|---|---|---|---|
| Backend unitarias | JUnit 5, AssertJ | Algoritmo de compatibilidad (`CompatibilityCalculatorTest`) | `./mvnw test` |
| Backend integración | `@SpringBootTest` + MockMvc + **PostgreSQL 16 real (Testcontainers)** + Flyway | Auth, CSRF, sesión, validación, CRUD, filtros/paginación/orden, historial, perfil→compatibilidad, estadísticas, CSV, **aislamiento entre usuarios** | `./mvnw verify` |
| Frontend | Vitest (runner de Angular) + jsdom + `HttpTestingController` | Servicios, guards, interceptor, formularios y validación, filtros/paginación, estados de carga/vacío/error, métricas, gráficos | `npm test -- --watch=false` |
| E2E | Playwright (Chromium) contra el stack de Docker | Los 15 recorridos de `e2e/run.mjs` en un navegador real (incluye móvil y errores de consola/CSP) | `cd e2e && npm ci && npx playwright install chromium && npm test` |

Decisiones:

- Las pruebas de integración usan PostgreSQL real, no H2, para ejercitar las mismas migraciones y restricciones que producción.
  Requieren Docker en marcha. Sin Docker, las pruebas de integración **no** se pueden ejecutar (solo `CompatibilityCalculatorTest`).
- El cliente de pruebas (`TestApi`) reproduce el flujo real de la SPA: obtiene la cookie `XSRF-TOKEN` y la reenvía; no usa atajos que desactiven CSRF.
- Cada prueba crea usuarios con email único, por lo que comparten contenedor sin interferir.
- Los criterios de aceptación 1–8 están cubiertos tanto por integración (API) como por E2E (UI). El 9 (instalación limpia) se verifica siguiendo el README.
