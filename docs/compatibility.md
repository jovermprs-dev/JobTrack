# Algoritmo de compatibilidad perfil ↔ oferta

Implementación: `backend/.../compatibility/CompatibilityCalculator.java` (clase pura, sin dependencias de BD). Es **determinista, gratuito y transparente**:
mismos datos ⇒ misma puntuación, y cada punto se puede explicar desde `GET /offers/{id}/compatibility`.

> La puntuación mide el **encaje entre tu perfil y los datos de la oferta**. No es una probabilidad de conseguir el puesto.

## Dimensiones y pesos

| Dimensión | Peso | Datos necesarios (ambos lados) | Cálculo (0…1) |
|---|---:|---|---|
| Competencias | 50 | `offer.skills` y `profile.skills` no vacíos | competencias requeridas presentes en el perfil ÷ requeridas |
| Experiencia | 15 | `offer.minYearsExperience` y `profile.yearsExperience` | 1 si `perfil ≥ mínimo` (o mínimo = 0); si no, `perfil ÷ mínimo` |
| Puesto deseado | 15 | `profile.desiredRoles` y título de la oferta | 1 si el título contiene un puesto deseado completo; 0,5 si comparten ≥ 50 % de sus palabras; 0 si no. Se toma el mejor |
| Modalidad | 10 | `offer.workMode` y `profile.preferredWorkMode` | 1 iguales; 0,5 si uno es híbrido; 0 remoto ↔ presencial |
| Ubicación | 10 | ubicación en oferta y perfil, y oferta **no** remota | 1 si una contiene a la otra (sin acentos ni mayúsculas); 0 si no |

## Puntuación

```
disponible = Σ peso de las dimensiones con datos en ambos lados
score      = round( Σ peso·valor (solo disponibles) / disponible × 100 )
```

- Una dimensión **sin datos** no puntúa ni penaliza: se excluye y el resto se reescala.
- Si `disponible < 40` ⇒ `score = null` y `sufficientInformation = false` («información insuficiente»). Nunca se devuelve un 0 por falta de datos.
- Ejemplo: solo competencias (50) → válido. Solo puesto + modalidad + ubicación (35) → insuficiente.

## Desconocido vs. ausente

- Si el perfil **no tiene competencias registradas**, no se afirma que falten: se informa en `unknown`.
- Solo cuando el perfil tiene competencias, las requeridas que no figuran se listan en `missing` («no registrada en tu perfil»).
- `0` es un valor real (experiencia mínima 0 = «no se pide»); `null` significa «no indicado».

## Normalización de competencias

Minúsculas, sin acentos y sin espacios, puntos, guiones ni barras: `Node.js` = `nodejs`, `Spring Boot` = `spring-boot`, `CI/CD` = `ci cd`.
No hay sinónimos (`JS` ≠ `JavaScript`): es una limitación conocida.

## Cuándo se recalcula

`job_offers.compatibility_score` se **persiste** (para ordenar y paginar en BD) y se recalcula al crear/editar/importar una oferta y,
para todas las ofertas del usuario, al guardar el perfil. El recálculo automático no modifica `updated_at`.

## Limitaciones

Sin sinónimos ni análisis semántico del texto libre; la descripción y los requisitos en texto no se interpretan (solo las competencias
etiquetadas); el nivel de idiomas no interviene; los pesos son fijos. Cubierto por `CompatibilityCalculatorTest`.
