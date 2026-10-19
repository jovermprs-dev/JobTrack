# Contrato de la API (`/api/v1`)

Documentación interactiva (solo dev): `http://localhost:8081/swagger-ui.html`, esquema en `/v3/api-docs`.

## Convenciones

- JSON UTF-8. Fechas `YYYY-MM-DD`; instantes ISO-8601 UTC.
- Autenticación por cookie de sesión. Peticiones que modifican estado requieren la cabecera `X-XSRF-TOKEN` (valor de la cookie `XSRF-TOKEN`).
- Errores en formato RFC 9457 (`application/problem+json`):
  ```json
  { "type":"about:blank","title":"Validation failed","status":400,"detail":"...","instance":"/api/v1/offers",
    "errors":[{"field":"title","message":"must not be blank"}] }
  ```
  Códigos: `400` validación · `401` sin sesión · `403` CSRF · `404` no existe **o no es tuyo** · `409` conflicto (email duplicado) · `429` demasiados intentos.
- Paginación: `?page=0&size=20&sort=<campo>,<asc|desc>` → `{content, page, size, totalElements, totalPages}`.

## Auth

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/auth/register` | `{email, password(≥10), displayName}` → 201 `User`, inicia sesión |
| POST | `/auth/login` | `{email, password}` → 200 `User` |
| POST | `/auth/logout` | 204 |
| GET | `/auth/me` | 200 `User` / 401 |
| PUT | `/auth/password` | `{currentPassword, newPassword}` → 204 |

`User = {id, email, displayName, demo}`. `demo` es `true` solo en el [modo demo](security-and-config.md#modo-demo-instancia-pública-de-solo-lectura): instancia pública de solo lectura donde todo
método distinto de `GET`/`HEAD`/`OPTIONS` (incluidos `/auth/*` salvo `GET /auth/me`) responde `403`, y no hace falta iniciar sesión.

## Ofertas

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/offers` | Filtros: `q` (texto en puesto/empresa/descripción/skills), `status` (repetible), `company`, `location`, `workMode`, `favorite`. `sort`: `detectedDate`, `company`, `title`, `compatibilityScore`, `updatedAt` |
| POST | `/offers` | Crea (201). Estado inicial `SAVED` si no se indica |
| GET | `/offers/{id}` | Detalle |
| PUT | `/offers/{id}` | Reemplaza campos editables (no cambia `status`) |
| DELETE | `/offers/{id}` | 204 |
| PATCH | `/offers/{id}/favorite` | `{favorite: boolean}` |
| POST | `/offers/{id}/status` | `{status, note?}` → oferta actualizada; añade historial |
| GET | `/offers/{id}/history` | Lista cronológica inversa de cambios |
| GET | `/offers/{id}/compatibility` | Desglose de compatibilidad |
| GET | `/offers/export` | CSV UTF-8 (con BOM) de todas las ofertas del usuario. Columnas: `title,company,url,location,work_mode,published_date,detected_date,applied_date,salary_min,salary_max,currency,contract_type,working_hours,min_years_experience,skills(;),requirements,notes,description,status,favorite,compatibility_score` |
| POST | `/offers/import?dryRun=true` | `multipart/form-data` `file` (UTF-8, ≤ 2 MB, ≤ 1000 filas). `dryRun=true` (por defecto) valida y devuelve vista previa; `dryRun=false` importa. Todo o nada: con errores devuelve `422` y no guarda nada. Respuesta: `{dryRun,totalRows,validRows,imported,errors:[{row,field,message}],preview[]}` |

`Offer`: `id, title, company, description, url, location, workMode, publishedDate, detectedDate, appliedDate, salaryMin, salaryMax, currency, contractType, workingHours, minYearsExperience, skills[], requirements, notes, status, favorite, compatibilityScore, createdAt, updatedAt`.

`Compatibility`: `{score: 0-100|null, sufficientInformation, dimensions:[{key, label, weight, available, earned, detail}], positives[], missing[], unknown[], disclaimer}`.

## Perfil

`GET /profile` (200; campos vacíos si no existe) · `PUT /profile`. Cada guardado recalcula la compatibilidad de todas las ofertas del usuario.

`Profile`: `degree, experienceSummary, yearsExperience, skills[], languages[{language,level}], location, preferredWorkMode, desiredRoles[], expectedSalary, salaryCurrency, preferences`.

## Estadísticas

`GET /stats` →
```
{ totals:{saved, applied, active, interviews, offersReceived, favorites},
  byStatus:[{status,count}],
  applicationsOverTime:[{weekStart,count}],      // últimas 12 semanas, rellenas con 0
  responseRate:{value|null, responded, applied, sufficientData},
  topCompanies:[{company,count}],
  averageCompatibility:{value|null, offersScored},
  topCompatible:[{id,title,company,score}],
  recentActivity:[{offerId,title,company,fromStatus,toStatus,note,changedAt}] }
```
Definiciones en [security-and-config.md](security-and-config.md#definiciones-de-métricas); algoritmo en [compatibility.md](compatibility.md).

## Salud

`GET /actuator/health` (público, sin detalles).
