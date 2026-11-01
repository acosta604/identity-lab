# Léeme primero: en qué orden leer la documentación

Este orden va de lo general a lo concreto: primero entiendes la idea, luego la corres, después la pruebas y al final lees el código. Los tiempos son aproximados.

Ten abierto el **[glosario](docs/glosario.md)** durante toda la lectura: cuando aparezca un término que no conoces (OIDC, `iss`, `aud`, tupla contextual, `__Host-`…), búscalo ahí.

---

## Etapa 1: entender la idea (30 min)

| # | Qué leer | Para qué |
| --- | --- | --- |
| 1 | [README.md](README.md), hasta "Componentes" | La idea en una frase, el diagrama funcional y la secuencia del flujo. |
| 2 | [docs/arquitectura-funcional.png](docs/arquitectura-funcional.png) | Sigue los pasos 1 a 8 con el dedo. Es el mapa para todo lo demás. |
| 3 | [docs/glosario.md](docs/glosario.md), secciones 2 a 5 | OAuth/OIDC, JWT, claims y token exchange: el vocabulario mínimo. |
| 4 | [README.md](README.md), "Claims del token propio" | Qué trae el JWT propio y cómo cada claim se vuelve una tupla contextual. |
| 5 | [docs/glosario.md](docs/glosario.md), secciones 6 a 8 | Sesión y cookies, multi-tenant y OpenFGA. |

Al terminar deberías poder explicar: por qué Entra solo se usa al iniciar sesión, qué hace el servicio de exchange y por qué las membresías no se guardan en OpenFGA.

## Etapa 2: el modelo de autorización (20 min)

| # | Qué leer | Para qué |
| --- | --- | --- |
| 6 | [openfga/model.fga](openfga/model.fga) | Tipos y relaciones. Lee cada `define` en voz alta: "editor es el dueño, o el lead del equipo, o…". |
| 7 | [openfga/seed-tuples.txt](openfga/seed-tuples.txt) | Lo único que se guarda en OpenFGA: estructura y recursos. |
| 8 | [openfga/store.fga.yaml](openfga/store.fga.yaml) | Casos de prueba del modelo: cada `tuples` dentro de un test hace de tupla contextual. |

## Etapa 3: correrlo en local (45 a 60 min la primera vez)

| # | Qué leer | Para qué |
| --- | --- | --- |
| 9 | [docs/guia-local.md](docs/guia-local.md), secciones 1 a 4 | Instalar herramientas, revisar la configuración y levantar todo con Docker. |
| 10 | [docs/guia-local.md](docs/guia-local.md), sección 5.1 | Primera prueba desde el navegador. |

## Etapa 4: probarlo a fondo (45 min)

| # | Qué leer | Para qué |
| --- | --- | --- |
| 11 | [docs/bruno.md](docs/bruno.md) | Recorrer el flujo petición por petición en Bruno, con otras personas y con el experimento de membresías. |
| 12 | [docs/guia-local.md](docs/guia-local.md), secciones 5.3 a 6 | Lo mismo con curl, las pruebas automáticas y cómo observar logs, base de datos y Playground. |
| 13 | [scripts/e2e.sh](scripts/e2e.sh) | Funciona como especificación ejecutable: cada `expect` es una regla del diseño. |

## Etapa 5: leer el código (1 a 2 horas)

Lee cada servicio de adentro hacia afuera, como manda la arquitectura limpia. Antes, repasa la sección 9 del [glosario](docs/glosario.md) y la sección "Arquitectura limpia" del [README](README.md).

**token-exchange-service** (`co.com.lab`):

| # | Archivo | Qué vas a ver |
| --- | --- | --- |
| 14 | `domain/model/.../session/Session.java` y `SessionToken.java` | Reglas de la sesión propia: vigencia, rotación y reuso. |
| 15 | `domain/model/.../accesscontext/AccessContext.java` | El contexto que termina en los claims. |
| 16 | Los `gateways/` de `domain/model` | Lo que el dominio necesita del mundo exterior. |
| 17 | `domain/usecase/.../tokenexchange/TokenExchangeUseCase.java` | **El corazón**: los dos caminos del exchange. |
| 18 | `domain/usecase/.../session/RefreshSessionUseCase.java` | Refresh sin Entra, rotación y relectura de membresías. |
| 19 | `infrastructure/driven-adapters/entra-token-adapter` | Cómo se valida el token de Entra (`trusted_issuers`). |
| 20 | `infrastructure/driven-adapters/internal-jwt-adapter/.../InternalJwtAdapter.java` | Cómo se arman y firman los claims propios. |
| 21 | `infrastructure/entry-points/api-rest` | Endpoints, cookies y CSRF. |
| 22 | `infrastructure/driven-adapters/jdbc-repository/.../db/migration` | Tablas y datos semilla. |

**documents-api** (`co.com.lab`):

| # | Archivo | Qué vas a ver |
| --- | --- | --- |
| 23 | `domain/model/.../principal/Principal.java` | **Claims a tuplas contextuales** (`membershipRelationships`). |
| 24 | `domain/model/.../document/Document.java` | Las tuplas que sí se guardan (`resourceRelationships`). |
| 25 | `domain/usecase/.../document/*UseCase.java` | Cada acción pide una relación a OpenFGA. |
| 26 | `infrastructure/driven-adapters/openfga-adapter` | El JSON exacto del Check con tuplas contextuales. |
| 27 | `infrastructure/entry-points/api-rest/.../security` | Validación del JWT propio y paso de claims a `Principal`. |

**Pruebas** (`src/test` de `model` y `usecase` en ambos servicios): muestran cada regla con ejemplos, sin Spring ni base de datos.

## Etapa 6: conectarlo con el trabajo

| # | Qué leer | Para qué |
| --- | --- | --- |
| 28 | [README.md](README.md), "Pasar a Entra real" | Qué cambia cuando el emisor es Entra de verdad (spoiler: solo configuración). |
| 29 | [Documento de diseño](https://claude.ai/artifact/5K3gExyeevSN4ZjE91Pcbh) | Decisiones, alternativas y casos de prueba. Se escribió antes del scaffold: para la estructura de carpetas manda este repo. |

---

### Mapa rápido de archivos de documentación

| Archivo | Contenido |
| --- | --- |
| `LEEME-PRIMERO.md` | Este archivo: el orden de lectura. |
| `README.md` | Visión general, componentes, arquitectura limpia, datos de prueba y claims. |
| `docs/arquitectura-funcional.png` (`.excalidraw`) | Diagrama funcional (editable en excalidraw.com). |
| `docs/glosario.md` | Todos los términos técnicos. |
| `docs/guia-local.md` | Instalación, configuración, correr (Docker o IntelliJ), probar, observar y problemas comunes. |
| `docs/bruno.md` | Pruebas paso a paso con Bruno. |
| `http/lab.http` | Las mismas pruebas para el cliente HTTP de IntelliJ. |
| `scripts/e2e.sh` | Pruebas automáticas de punta a punta. |
