# Probar el laboratorio con Bruno, paso a paso

[Bruno](https://www.usebruno.com) es un cliente de APIs gratuito y open source. Guarda las colecciones como archivos de texto: la colección del laboratorio está en la carpeta `bruno/` del repo, así que viaja con el código.

Antes de empezar, el laboratorio tiene que estar corriendo (sección 4 de la [guía local](guia-local.md)):

```bash
docker compose up --build -d
docker compose logs -f documents-api   # espera "OpenFGA listo"
```

---

## 1. Instalar Bruno

```bash
brew install --cask bruno
```

En Windows o Linux, descárgalo de https://www.usebruno.com/downloads.

## 2. Abrir la colección

1. Abre Bruno → **Open Collection**.
2. Elige la carpeta `bruno/` del repo. Aparece la colección **Identity Lab** con 6 carpetas numeradas.
3. Arriba a la derecha, en el selector de entornos, elige **local**.

El entorno `local` tiene estas variables:

| Variable | Valor | Para qué |
| --- | --- | --- |
| `baseUrl` | `http://localhost` | Proxy: front, exchange y API bajo el mismo origen |
| `entraUrl` | `http://localhost:8090/entra` | Mock de Entra ID |
| `user` | `alice` | Persona que inicia sesión: `alice`, `bob`, `carol` o `dave` |
| `tenant` / `organization` | `acme` / `acme-ventas` | Contexto que se pide en el exchange |
| `cookiePrefix` | `__Host-` | Prefijo de las cookies. Si pones `LAB_COOKIES_SECURE=false`, déjalo vacío |

## 3. Cómo maneja la colección las cookies

El servicio de exchange responde con tres cookies: `__Host-sid`, `__Host-at` y `XSRF-TOKEN`. Para que veas exactamente qué viaja (y para no depender de cómo trata Bruno las cookies `Secure` sobre `http://localhost`), la colección hace esto:

- Un **script de colección** (`collection.bru`) lee cada `Set-Cookie` y guarda los valores en las variables `sid`, `at` y `xsrf`.
- Un **header de colección** envía en cada petición `Cookie: __Host-sid={{sid}}; __Host-at={{at}}; XSRF-TOKEN={{xsrf}}` y `X-XSRF-TOKEN: {{xsrf}}`.

Para que Bruno no mande además sus propias cookies, desactiva en **Preferences → General** (o **Cookies**, según la versión) las opciones de guardar y enviar cookies automáticamente. Si no encuentras la opción, borra las cookies de `localhost` desde el ícono de cookies antes de empezar.

Para ver los valores en cualquier momento, abre las variables de la colección (ícono del ojo junto al selector de entorno): verás `entraToken`, `sid`, `at`, `xsrf`, etc.

## 4. Ejecutar paso a paso

Ejecuta las peticiones en orden, de arriba hacia abajo. Cada una tiene una pestaña **Docs** que explica qué demuestra y una pestaña **Tests** que valida la respuesta esperada (con `user = alice`).

### 01 Inicio de sesión

| Petición | Qué pasa | Qué mirar |
| --- | --- | --- |
| 01 Iniciar sesión en Entra (mock) | El mock emite el token de Entra para `{{user}}` | En **Timeline**, decodifica el token: `aud = token-exchange-service`, `oid`, `tid` |
| 02 JWT exchange | El token de Entra se usa por única vez: el servicio crea la sesión propia y emite el JWT propio | Los headers `Set-Cookie` y el `context` del cuerpo. Las variables `sid`, `at` y `xsrf` ya tienen valor |
| 03 Ver claims y tuplas contextuales | `GET /api/whoami` con la cookie del JWT propio | `claims` (usr, tid, org, teams, roles) y `contextual_tuples`, que es lo que llega a OpenFGA |

### 02 API documentos

| Petición | Esperado (alice) | Por qué |
| --- | --- | --- |
| 01 Listar documentos | 200 | Solo los de la organización activa que OpenFGA deja ver |
| 02 Leer documento 43 | 200 | alice es `member` del equipo sur |
| 03 Editar documento 42 | 200 | alice es `lead` del equipo norte → `editor` |
| 04 Editar documento 43 | 403 | En el equipo sur solo es `member` |
| 05 Leer documento 77 | 403 | Es de acme-marketing y el token es de acme-ventas |
| 06 Crear documento en el equipo norte | 201 | Escribe en OpenFGA las tuplas del recurso |
| 07 Leer el documento creado | 200 | |

Mientras ejecutas esta carpeta, deja abierto el log de cada decisión:

```bash
docker compose logs -f documents-api | grep Check
```

### 03 Sesión propia (sin volver a Entra)

| Petición | Esperado | Qué demuestra |
| --- | --- | --- |
| 01 Consultar la sesión | 200 | Usuario, contexto y organizaciones disponibles |
| 02 Cambiar a acme-marketing | 200 | Nuevo JWT con otra organización |
| 03 Leer documento 77 otra vez | 200 | Ahora sí llega la tupla de marketing |
| 04 Volver a acme-ventas | 200 | |
| 05 Renovar el JWT propio | 200 | Rota la sesión (cambia `sid`) y relee las membresías |

### 04 Servicio a servicio

| Petición | Esperado | Qué demuestra |
| --- | --- | --- |
| 01 Exchange servicio a servicio | 200 | El JWT propio se cambia por otro para `reports-api` con el claim `act` |

### 05 Casos negativos

| Petición | Esperado | Qué demuestra |
| --- | --- | --- |
| 01 Token de Entra contra la API | 401 | Las APIs solo aceptan el JWT propio |
| 02 y 03 Token para Graph → exchange | 200 y 401 | El token de Entra debe ser para el exchange (`aud`) |
| 04 y 05 Mallory → exchange | 200 y 403 | Identidad válida en Entra pero sin usuario interno |
| 06 Exchange a una organización ajena | 403 | `globex-main` es de otro tenant |
| 07 Audiencia no permitida | 400 | `invalid_target` |
| 08 POST sin header CSRF | 403 | Protección CSRF con cookie doble |
| 09 JWT propio alterado | 401 | Se cambió `sub` sin volver a firmar |

### 06 Llaves y cierre

| Petición | Esperado | Qué demuestra |
| --- | --- | --- |
| 01 JWKS del emisor propio | 200 | Llaves públicas que usan las APIs |
| 02 Rotar la llave de firma | 200 | Aparece un `kid` nuevo; el JWT vigente sigue funcionando |
| 03 Cerrar sesión | 204 | Revoca la sesión y borra las cookies |
| 04 Refresh después de cerrar sesión | 401 | Ya no hay sesión: hay que volver a Entra |

## 5. Ejecutar todo de una vez

Clic derecho sobre la colección → **Run**. El runner ejecuta las carpetas en orden y muestra qué tests pasan. Con `user = alice` todos deben pasar.

## 6. Probar con otras personas

Cambia `user` en el entorno, ejecuta **01/01** y **01/02** y repite la carpeta 02. Los tests están escritos para alice, así que con otra persona algunos fallarán a propósito. Esto es lo esperado:

| Petición | alice | bob (admin de ventas) | dave (miembro sin equipos) | carol (admin de marketing)* |
| --- | --- | --- | --- | --- |
| Leer 42 (equipo norte) | 200 | 200 | 403 | 403 |
| Editar 42 | 200 | 200 | 403 | 403 |
| Editar 43 (equipo sur) | 403 | 200 | 403 | 403 |
| Leer 44 (toda la organización) | 200 | 200 | 200 | 403 |
| Leer 77 (marketing) | 403 | 403 | 403 | 200 |
| Borrar 42 | 403 | 204 | 403 | 403 |

\* Para carol cambia `organization` a `acme-marketing` antes del exchange.

## 7. Experimento: cambiar una membresía

1. Con alice, ejecuta **02/03 Editar documento 42**: 200.
2. Saca a alice del equipo norte:
   ```bash
   docker compose exec postgres psql -U postgres -d exchange -c \
     "update team_memberships set status='INACTIVE' where user_id='alice' and team_id='acme-ventas-norte'"
   ```
3. Ejecuta otra vez **02/03**: sigue en 200, porque el JWT propio vigente aún trae el equipo.
4. Ejecuta **03/05 Renovar el JWT propio** y luego **02/03**: ahora 403.
5. Devuélvela al equipo:
   ```bash
   docker compose exec postgres psql -U postgres -d exchange -c \
     "update team_memberships set status='ACTIVE' where user_id='alice' and team_id='acme-ventas-norte'"
   ```

Así se ve en la práctica que los cambios de membresía se reflejan al renovar el token, no al instante.

## 8. Problemas comunes

| Síntoma | Solución |
| --- | --- |
| 401 `Token de Entra rechazado` en el exchange | El token del mock dura 1 hora: ejecuta otra vez **01/01** |
| 401 `invalid_session` | La sesión venció, se revocó o se cerró: ejecuta **01/01** y **01/02** |
| 403 `csrf` donde no lo esperabas | Bruno está mandando sus propias cookies: desactívalas (sección 3) y repite **01/02** |
| Las variables `sid`/`at` quedan vacías | Revisa que el entorno sea **local** y que el exchange haya respondido 200 |
| 503 `OpenFGA aún no está listo` | Espera el log `OpenFGA listo` de documents-api |
| Todo responde 502 | El proxy no alcanza los servicios: `docker compose ps` |
