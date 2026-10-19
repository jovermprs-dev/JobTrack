# Roadmap y progreso

Leyenda: ✅ verificado · 🔄 en curso · ⬜ pendiente

| Fase | Estado | Resultado verificado |
|---|---|---|
| 0 Inspección y planificación | ✅ | Docs iniciales, git, `.gitignore`, versiones fijadas |
| 1 Esqueleto ejecutable | ✅ | Spring Boot + Flyway + PostgreSQL + Angular + Docker Compose; healthchecks en verde |
| 2 Backend y seguridad | ✅ | 47 pruebas (unitarias + integración con PostgreSQL real) en verde |
| 3 Frontend funcional | ✅ | 63 pruebas Vitest en verde; build de producción sin avisos |
| 4 Calidad y experiencia | ✅ | E2E 15/15 en Chromium (escritorio y móvil); CSV; datos y sesión sobreviven a reinicios |
| 5 Entrega profesional | ✅ | Instalación desde clon limpio siguiendo el README + E2E 15/15; CI y docs; revisiones de seguridad y accesibilidad aplicadas |
| 6 Publicación y despliegue | ✅ | Repositorio en GitHub (privado); CI en el runner con backend, frontend y E2E en verde; desplegado en https://jobtrack.sergiojover.dev (VPS tras Traefik, TLS de Let's Encrypt); registro, sesión por cookie `Secure` y logout verificados por HTTPS |

## Pendiente / ideas futuras

- Despliegue continuo: hoy se actualiza a mano (copiar el código y `build` + `up -d`, ver `docs/deployment.md`). Un workflow de GitHub Actions por SSH lo automatizaría.
- Copia de seguridad programada de la BD en el VPS (el `pg_dump` está documentado, pero no hay cron).
- Verificación de email y recuperación de contraseña (requiere un servicio de correo).
- Limitador de login compartido entre réplicas; límite de frecuencia en el registro.
- Sinónimos de competencias en la compatibilidad; recordatorios de seguimiento.

## Cómo reanudar el trabajo

1. `docker compose up -d --build` (o ver README para desarrollo local; en este equipo exportar `JAVA_HOME` a un JDK 21).
2. Verificar: `cd backend && ./mvnw verify`, `cd frontend && npm test -- --watch=false && npm run build`, `cd e2e && npm test`.
3. Leer `CLAUDE.md` (convenciones) y `docs/`.

## Fuera de alcance (v1)

Login con Google, recuperación de contraseña por email, scraping de portales.
