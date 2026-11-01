# Glosario

Términos técnicos del laboratorio, agrupados por tema. Cada término dice qué es y, cuando aplica, **dónde aparece en el lab**.

**Contenido:** [1. Conceptos base](#1-conceptos-base) · [2. OAuth 2.0, OIDC y Entra ID](#2-oauth-20-oidc-y-entra-id) · [3. JWT](#3-jwt) · [4. Claims del JWT](#4-claims-del-jwt) · [5. JWT exchange (RFC 8693)](#5-jwt-exchange-rfc-8693) · [6. Sesión y cookies](#6-sesión-y-cookies) · [7. Multi-tenant](#7-multi-tenant) · [8. Autorización y OpenFGA](#8-autorización-y-openfga) · [9. Arquitectura limpia (scaffold)](#9-arquitectura-limpia-scaffold) · [10. Spring y librerías](#10-spring-y-librerías) · [11. Infraestructura y herramientas](#11-infraestructura-y-herramientas) · [12. Códigos HTTP y errores](#12-códigos-http-y-errores)

---

## 1. Conceptos base

| Término | Qué es | En el lab |
| --- | --- | --- |
| **Autenticación** (AuthN) | Comprobar *quién eres*. | La hace Entra ID, una sola vez, al iniciar sesión. |
| **Autorización** (AuthZ) | Decidir *qué puedes hacer* sobre un recurso. | La decide OpenFGA en cada petición a la API. |
| **Identidad** | El conjunto de datos que representa a una persona en un sistema. | Externa: el `oid` de Entra. Interna: el usuario de la tabla `users`. |
| **IdP** (Identity Provider) | Sistema que autentica personas y emite tokens que lo prueban. | Microsoft Entra ID (en el lab, el mock). |
| **Token** | Credencial digital que se presenta en cada petición en lugar de usuario y contraseña. | Token de Entra, JWT propio (`at`) y secreto de sesión (`sid`). |
| **Bearer token** | Token que da acceso a quien lo porte, como dinero en efectivo. Viaja en `Authorization: Bearer <token>`. | Llamadas servicio a servicio. |
| **Principal** | La entidad autenticada que hace la petición. | `Principal` en documents-api: usuario + tenant + organización + equipos + roles. |
| **Confianza (trust)** | Aceptar tokens de un emisor porque se verificó su firma con sus llaves públicas. | El exchange confía en Entra; las APIs confían solo en el exchange. |
| **Mínimo privilegio** | Dar solo los permisos necesarios. | El token de Entra solo sirve para el exchange; el JWT propio solo para su audiencia. |

## 2. OAuth 2.0, OIDC y Entra ID

| Término | Qué es | En el lab |
| --- | --- | --- |
| **OAuth 2.0** | Estándar (RFC 6749) para que una aplicación obtenga tokens de acceso a una API sin manejar la contraseña del usuario. | Base de todo el flujo. |
| **OIDC** (OpenID Connect) | Capa sobre OAuth 2.0 que agrega la autenticación de la persona: `id_token`, claims estándar y descubrimiento. | El login contra el mock de Entra. |
| **Microsoft Entra ID** | El IdP de Microsoft (antes Azure Active Directory). | Solo se usa para iniciar sesión. |
| **mock-oauth2-server** | Servidor OAuth2/OIDC de prueba (open source, de NAV) que imita a un IdP real. | Contenedor `mock-entra` en el puerto 8090. |
| **Tenant de Azure** | La instancia de Entra ID de una empresa. Se identifica con el claim `tid` del token de Entra. | `lab-azure-tenant`. No confundir con el tenant del negocio (sección 7). |
| **App registration** | Registro de una aplicación en Entra: le da un `client_id` y define qué scopes expone. | En Entra real: una para el front y otra que exponga el scope del exchange. |
| **App ID URI** | Identificador de la API registrada en Entra (por ejemplo `api://token-exchange-service`); es el `aud` de los tokens para esa API. | `audience` en `trusted_issuers`. |
| **client_id** | Identificador de la aplicación cliente ante el IdP. | En el mock, `alice`, `bob`… simulan personas distintas. |
| **client_secret** | Contraseña de una aplicación confidencial (de backend). Un front (SPA) no la tiene. | `lab` en el mock (no se valida). |
| **Scope** | Permiso que pide el cliente; define para qué API es el token. | `token-exchange-service`. |
| **Grant type** | La forma de obtener un token. | `client_credentials` (scripts), `authorization_code` (front) y `token-exchange` (exchange). |
| **Authorization Code Flow** | Flujo de login en el navegador: el IdP devuelve un `code` que la app canjea por tokens. | El front (`web/index.html`) contra el mock. |
| **PKCE** | Extensión del flujo anterior que impide usar un `code` robado. Obligatoria para SPAs. | La aplica `oidc-client-ts` en el front. |
| **Client Credentials** | Grant de servicio a servicio, sin persona. | Solo lo usan Bruno, curl y e2e para simular el login sin navegador. |
| **id_token** | JWT de OIDC que describe quién inició sesión; es para el cliente, no para APIs. | No se usa: se usa el access token de Entra. |
| **Access token** | Token para llamar a una API. | De Entra (solo para el exchange) y el propio (para las APIs). |
| **Refresh token** | Token de larga vida para pedir nuevos access tokens sin volver a iniciar sesión. | Reemplazado por la sesión propia (`__Host-sid`) y `POST /session/refresh`. |
| **MFA** | Autenticación multifactor. | La aplicaría Entra real; el mock no. |
| **SSO** (Single Sign-On) | Iniciar sesión una vez y entrar a varias aplicaciones. | Entra puede volver a autenticar sin pedir clave si su sesión sigue viva. |
| **Discovery** (`/.well-known/openid-configuration`) | Documento JSON donde un emisor publica su `issuer`, endpoints y `jwks_uri`. | Lo publican el mock y el exchange (`/token/.well-known/openid-configuration`). |
| **Issuer** (emisor) | Quien firma y emite un token. Su URL es el claim `iss`. | Mock: `http://localhost:8090/entra`. Propio: `http://localhost/token`. |
| **Emisor de confianza** | Issuer cuyos tokens se aceptan como `subject_token`. | Tabla `trusted_issuers`. |

## 3. JWT

| Término | Qué es | En el lab |
| --- | --- | --- |
| **JWT** (JSON Web Token) | Token con tres partes en Base64URL separadas por puntos: `header.payload.firma`. Cualquiera puede leerlo; solo el emisor puede firmarlo. | Token de Entra y token propio. |
| **JWS** | JWT firmado (lo normal). Garantiza integridad y origen, no confidencialidad. | Los dos tokens. |
| **JWE** | JWT cifrado: el contenido no se puede leer. | No se usa. |
| **Base64URL** | Codificación de texto segura para URLs (sin `+`, `/` ni `=`). No es cifrado. | Por eso `whoami` o `base64 -d` muestran el contenido. |
| **Header** | Primera parte del JWT: cómo está firmado. | `alg`, `kid`, `typ`. |
| **Payload** | Segunda parte: los claims. | Sección 4. |
| **Firma** | Tercera parte: prueba criptográfica de que el emisor creó el token y nadie lo modificó. | Si alguien cambia un claim, la firma deja de coincidir (caso "JWT alterado"). |
| **Claim** | Un dato dentro del payload (par nombre-valor). | Sección 4. |
| **`alg`** | Algoritmo de firma. | `RS256`. |
| **RS256** | Firma RSA con SHA-256: se firma con la llave privada y se verifica con la pública. | Llaves de 2048 bits. |
| **`kid`** (Key ID) | Identifica con qué llave se firmó, para buscar la pública correcta en el JWKS. | Cambia al rotar llaves. |
| **`typ`** | Tipo de token. | `JWT`. |
| **Llave privada / pública** | La privada firma (secreta, solo el emisor); la pública verifica (se publica). | La privada del exchange está en `signing_keys` (en real: KMS / Key Vault). |
| **JWK** | Una llave en formato JSON. | Cada entrada de `keys` del JWKS. |
| **JWKS** (JWK Set) | Lista pública de llaves de un emisor, en `jwks_uri`. | `/token/.well-known/jwks.json`; documents-api lo descarga y cachea. |
| **Rotación de llaves** | Cambiar la llave de firma periódicamente; la anterior se mantiene publicada hasta que vencen sus tokens. | `POST /token/admin/rotate-keys`. |
| **Validar un JWT** | Verificar firma (con el JWKS) y claims: `iss`, `aud`, `exp`, `nbf` y los propios. | `EntraTokenAdapter` (token de Entra) y `SecurityConfig` de documents-api (token propio). |

## 4. Claims del JWT

### 4.1 Claims registrados (estándar, RFC 7519)

| Claim | Significado | Token de Entra | Token propio |
| --- | --- | --- | --- |
| **`iss`** (issuer) | Quién emitió el token. | `http://localhost:8090/entra` | `http://localhost/token` |
| **`sub`** (subject) | A quién representa el token. | Id de la persona en Entra. | Id del usuario interno (`alice`). |
| **`aud`** (audience) | Para quién es el token. Una API debe rechazar tokens que no sean para ella. | `token-exchange-service` | `documents-api` (o `reports-api`) |
| **`exp`** (expiration) | Cuándo vence (segundos desde 1970). | 1 hora en el mock | 5 a 10 minutos |
| **`nbf`** (not before) | No es válido antes de esta fecha. | | Igual a `iat` |
| **`iat`** (issued at) | Cuándo se emitió. | ✓ | ✓ |
| **`jti`** (JWT ID) | Identificador único del token; sirve para auditar o revocar uno puntual. | ✓ | UUID |

### 4.2 Claims de Entra ID

| Claim | Significado |
| --- | --- |
| **`oid`** (object id) | Id inmutable de la persona en Entra. **Es el que se usa para vincular** con el usuario interno, nunca el correo. |
| **`tid`** (tenant id) | Tenant de Azure que emitió el token. El exchange valida que sea el de la empresa (`expected_tid`). |
| **`preferred_username`** | Usuario de inicio de sesión (normalmente el correo). Puede cambiar; solo para mostrar. |
| **`name`** | Nombre para mostrar. |
| **`scp` / `roles`** | Scopes o roles de aplicación de Entra. El lab no los usa: los roles salen de la base propia. |

### 4.3 Claims propios (definidos por el equipo)

Propuesta del laboratorio; los nombres viven en `ClaimNames` (exchange) y `JwtPrincipalMapper` (documents-api).

| Claim | Ejemplo | Significado | Tupla contextual que genera |
| --- | --- | --- | --- |
| **`usr`** | `{"name":"Alice","email":"alice@lab.local"}` | Datos para mostrar y auditar. | Ninguna |
| **`tid`** | `acme` | **Tenant del negocio** activo. Mismo nombre que el de Entra, pero otro significado y otro emisor. | `user:alice member tenant:acme` |
| **`org`** | `{"id":"acme-ventas","role":"member"}` | Organización activa y rol en ella. | `user:alice member organization:acme-ventas` |
| **`teams`** | `[{"id":"acme-ventas-norte","role":"lead"}]` | Equipos de esa organización, con rol. | `user:alice lead team:acme-ventas-norte` |
| **`roles`** | `["admin"]` | Roles a nivel tenant. | `user:x admin tenant:acme` |
| **`idp`** | `http://localhost:8090/entra` | Quién autenticó a la persona. | Ninguna |
| **`sid`** | UUID | Sesión propia a la que pertenece el token. | Ninguna |
| **`token_use`** | `access` | Tipo de token propio; las APIs exigen `access`. | Ninguna |
| **`cv`** | `1` | Versión del esquema de claims, para evolucionarlo sin romper APIs. | Ninguna |
| **`act`** | `{"sub":"service:documents-api"}` | Actor (RFC 8693): qué servicio actúa en nombre de la persona. Puede anidarse. | Ninguna |

**Regla de diseño:** los claims describen *contexto* (quién, en qué tenant, organización y equipos, con qué rol), nunca *permisos sobre recursos*. Los permisos los calcula OpenFGA.

## 5. JWT exchange (RFC 8693)

| Término | Qué es | En el lab |
| --- | --- | --- |
| **Token exchange** | Cambiar un token por otro: otro emisor, otra audiencia o otros claims. Estándar RFC 8693. | `POST /token/exchange`. |
| **Servicio de exchange** | El servicio propio que recibe el token de Entra y emite el token interno. | `token-exchange-service`. |
| **`grant_type`** | En un exchange: `urn:ietf:params:oauth:grant-type:token-exchange`. | Obligatorio. |
| **`subject_token`** | El token que se entrega para cambiarlo. | El de Entra (login) o el propio (servicio a servicio). |
| **`subject_token_type`** | Tipo del subject token (`...:access_token` o `...:jwt`). | Opcional. |
| **`audience`** | Para qué API se pide el nuevo token. Debe estar permitida. | Tabla `allowed_audiences`. |
| **`tenant`, `organization`** | Parámetros propios: el contexto que se quiere activar. | Si no se envían, se toma la primera organización de la persona. |
| **`actor`** | Parámetro propio: nombre del servicio que pide el token en servicio a servicio. | Se convierte en el claim `act`. |
| **`issued_token_type`** | En la respuesta: tipo del token emitido. | `...:access_token`. |
| **`expires_in`** | En la respuesta: segundos de vida del token emitido. | 600 o 300 según la audiencia. |
| **Vincular identidad** (subject link) | Relacionar la identidad externa (emisor + `oid`) con el usuario interno. | Tabla `subject_links`. |
| **Delegación** | Un servicio actúa en nombre de la persona y el token lo dice (`act`). | Exchange servicio a servicio. |
| **Suplantación** (impersonation) | Un servicio actúa como la persona sin dejar rastro. | Lo que se evita usando `act`. |

## 6. Sesión y cookies

| Término | Qué es | En el lab |
| --- | --- | --- |
| **Sesión propia** | Estado de inicio de sesión que mantiene el servicio propio, independiente de Entra. | Tabla `sessions` y cookie `__Host-sid`. |
| **Cookie** | Dato que el servidor guarda en el navegador y que este reenvía solo en cada petición al mismo sitio. | `__Host-sid`, `__Host-at`, `XSRF-TOKEN`. |
| **`HttpOnly`** | La cookie no se puede leer desde JavaScript; protege contra robo por XSS. | `sid` y `at` sí; `XSRF-TOKEN` no (el front debe leerla). |
| **`Secure`** | La cookie solo viaja por HTTPS (los navegadores aceptan `http://localhost`). | Todas. |
| **`SameSite`** | Controla si la cookie viaja en peticiones que vienen de otro sitio. `Strict`: nunca; `Lax`: solo navegación GET; `None`: siempre. | `sid`: Strict; `at`: Lax. |
| **`Path` / `Domain`** | A qué rutas y dominios se envía la cookie. | `Path=/` y sin `Domain`. |
| **`Max-Age`** | Segundos de vida de la cookie; `0` la borra. | Logout envía `Max-Age=0`. |
| **Prefijo `__Host-`** | Nombre que obliga al navegador a exigir `Secure`, `Path=/` y sin `Domain`. Evita que un subdominio la sobrescriba. | `__Host-sid`, `__Host-at`. |
| **Secreto de sesión** | Valor aleatorio de la cookie `sid` (`id.secreto`). En la base solo se guarda su hash. | `SessionToken`. |
| **Hash (SHA-256)** | Huella de un dato que no permite recuperarlo. | `secret_hash` en `sessions`. |
| **Rotación de sesión** | Cambiar el secreto en cada refresh. | `POST /session/refresh`. |
| **Detección de reuso** | Si llega un secreto que ya fue rotado, alguien copió la cookie: se revoca la sesión entera. | `previous_hash`. |
| **Inactividad** (idle timeout) | Cierre de la sesión si no se usa durante un tiempo. | 30 minutos. |
| **Vida absoluta** | Duración máxima de la sesión aunque se use. | 12 horas. |
| **Revocar** | Invalidar una sesión antes de que venza. | `revoked_at`. |
| **CSRF** | Ataque en el que otro sitio hace que el navegador envíe una petición con tus cookies. | Protegido con double submit. |
| **Double submit cookie** | Defensa CSRF: el front copia la cookie `XSRF-TOKEN` en el header `X-XSRF-TOKEN`; otro sitio no puede leerla. | `CsrfInterceptor`. |
| **XSS** | Ataque que ejecuta JavaScript malicioso en tu página. | `HttpOnly` evita que robe los tokens. |
| **Mismo origen** | Mismo esquema, dominio y puerto. Las cookies y el navegador se comportan distinto entre orígenes. | Todo bajo `http://localhost` gracias al proxy. |
| **CORS** | Mecanismo para permitir peticiones entre orígenes distintos. | No hace falta: mismo origen. |

## 7. Multi-tenant

| Término | Qué es | En el lab |
| --- | --- | --- |
| **Multi-tenant** | Una misma plataforma atiende a varios clientes (tenants) con sus datos separados. | Tenants `acme` y `globex`. |
| **Tenant** (del negocio) | Cliente o unidad de aislamiento de datos. Claim propio `tid`. | Tabla `tenants`. Distinto del tenant de Azure. |
| **Organización** | Subdivisión dentro de un tenant. Claim `org`. | `acme-ventas`, `acme-marketing`. |
| **Equipo** | Subdivisión de una organización. Claim `teams`. | `acme-ventas-norte`, `acme-ventas-sur`. |
| **Membresía** | Que una persona pertenezca a un tenant, organización o equipo con un rol. | Tablas `org_memberships`, `team_memberships`, `tenant_roles`. |
| **Rol** | Nivel de la membresía. Vocabulario cerrado igual a las relaciones del modelo. | `member`, `admin`, `lead`. |
| **Contexto activo** | El tenant y la organización con los que la persona trabaja en este momento. Uno por token. | `POST /session/context` lo cambia. |
| **Aislamiento** | Que un tenant no vea datos de otro. | Filtro SQL por `tenant_id` (404) + OpenFGA (403). |

## 8. Autorización y OpenFGA

| Término | Qué es | En el lab |
| --- | --- | --- |
| **RBAC** | Control de acceso por roles (admin puede X). | Los roles alimentan las relaciones. |
| **ABAC** | Control de acceso por atributos (hora, red…). | Extensión opcional con condiciones. |
| **ReBAC** | Control de acceso por relaciones (es editor porque es líder del equipo dueño del documento). | El modelo de OpenFGA. |
| **Zanzibar** | Sistema de autorización de Google en el que se basa OpenFGA. | |
| **OpenFGA** | Servidor open source de autorización ReBAC (proyecto de la CNCF). | Contenedor `openfga`, puertos 8080 y 3000. |
| **Store** | Contenedor de un modelo y sus tuplas. | `identity-lab`. |
| **Modelo de autorización** | Define tipos y relaciones, y cómo se derivan unas de otras. | `openfga/model.fga` (y `model.json`, el mismo en JSON). |
| **Tipo** (`type`) | Clase de objeto. | `user`, `tenant`, `organization`, `team`, `document`. |
| **Relación** (`relation`) | Vínculo posible entre un usuario y un objeto. | `member`, `admin`, `lead`, `owner`, `viewer`, `editor`, `can_delete`. |
| **Tupla** | Hecho concreto: `usuario relación objeto`. | `team:acme-ventas-norte team document:42`. |
| **Tuplas guardadas** | Las que se escriben y persisten en OpenFGA. | Estructura y recursos (`openfga/seed-tuples.txt`). |
| **Tuplas contextuales** | Tuplas que se envían solo dentro de un Check y no se guardan. | Las membresías de la persona, sacadas del JWT propio. |
| **Relación directa** (`[user]`) | La relación se puede asignar con una tupla. | `define owner: [user]`. |
| **Relación calculada** (`or editor`) | Se deriva de otra relación del mismo objeto. | `viewer: ... or editor`. |
| **`from`** (tuple-to-userset) | Se hereda de un objeto relacionado. | `lead from team`: el lead del equipo del documento. |
| **Check** | Pregunta "¿el usuario U tiene la relación R sobre el objeto O?". Respuesta: `allowed` true o false. | Cada lectura, edición o borrado. |
| **ListObjects** | Pregunta "¿sobre qué objetos tiene U la relación R?". | No se usa; el listado filtra con Check. |
| **Condiciones** (CEL) | Reglas con contexto en el Check (hora, IP…). | Extensión opcional. |
| **`authorization_model_id`** | Versión del modelo que se usa en un Check. | La resuelve `FgaBootstrap` al arrancar. |
| **Playground** | Interfaz web de OpenFGA para ver el modelo y probar Checks. | http://localhost:3000 |
| **`fga` CLI** | Herramienta de línea de comandos de OpenFGA. | `fga model test --tests openfga/store.fga.yaml`. |
| **Consistencia al renovar** | Un cambio de membresía se refleja cuando se emite un JWT nuevo, no al instante. | Por eso el JWT propio vive 5 a 10 minutos. |

## 9. Arquitectura limpia (scaffold)

| Término | Qué es | En el lab |
| --- | --- | --- |
| **Arquitectura limpia** | Organización en capas donde las reglas de negocio no dependen de frameworks ni de infraestructura; las dependencias apuntan hacia el dominio. | Los dos servicios. |
| **Scaffold Clean Architecture** | Plugin de Gradle de Bancolombia (`co.com.bancolombia.cleanArchitecture`) que genera y valida esa estructura. | Versión 5.0.2. |
| **`domain/model`** | Entidades, value objects, reglas y *gateways* (interfaces). Java puro. | `Session`, `Principal`, `AccessContext`… |
| **Gateway** | Interfaz que el dominio necesita y que implementa la infraestructura (puerto de salida). | `SessionRepository`, `AuthorizationGateway`… |
| **`domain/usecase`** | Casos de uso: orquestan el dominio y los gateways. Sin anotaciones de Spring. | `TokenExchangeUseCase`, `GetDocumentUseCase`… |
| **Driven adapter** | Implementación de un gateway contra una tecnología. | `jdbc-repository`, `internal-jwt-adapter`, `entra-token-adapter`, `openfga-adapter`. |
| **Entry point** | Por donde entra el mundo exterior (HTTP, colas…). | `api-rest`: controladores, cookies, CSRF. |
| **`app-service`** | Aplicación Spring Boot que ensambla todo. | `MainApplication`, `UseCasesConfig`, `DomainConfig`. |
| **`UseCasesConfig`** | Registra como beans las clases que terminan en `UseCase`. | Convención del scaffold. |
| **Puertos y adaptadores** | Otro nombre de la misma idea (arquitectura hexagonal). | Gateway = puerto; adapter = adaptador. |

## 10. Spring y librerías

| Término | Qué es | En el lab |
| --- | --- | --- |
| **Spring Boot** | Framework para crear aplicaciones Java listas para correr. | Lo trae el plugin del scaffold. |
| **Spring MVC** | Módulo web de Spring basado en servlets (no reactivo). | `reactive=false`. |
| **Spring Security Resource Server** | Valida tokens Bearer (JWT) en una API. | documents-api. |
| **`JwtDecoder` / `NimbusJwtDecoder`** | Componente que descarga el JWKS, verifica la firma y aplica validadores. | En las dos APIs. |
| **`BearerTokenResolver`** | Decide de dónde sacar el token. | Header `Authorization` o cookie `__Host-at`. |
| **Nimbus JOSE + JWT** | Librería Java para crear, firmar y validar JWT. | Firma del token propio. |
| **`JdbcClient`** | API de Spring para SQL directo. | Adaptadores JDBC. |
| **Flyway** | Herramienta de migraciones de base de datos versionadas (`V1__`, `V2__`). | Crea tablas y datos semilla al arrancar. |
| **`HandlerInterceptor`** | Código de Spring MVC que corre antes de cada controlador. | `CsrfInterceptor`. |
| **`RestClient`** | Cliente HTTP de Spring. | Llamadas a la API de OpenFGA. |
| **Java 25** | Versión LTS de Java usada en el lab, igual que en `ms_identity`. | Records, `sealed`, `switch` con patrones. |
| **Gradle** | Herramienta de compilación de los proyectos del scaffold. | `gradle test`, `gradle :app-service:bootJar`. |

## 11. Infraestructura y herramientas

| Término | Qué es | En el lab |
| --- | --- | --- |
| **Docker / contenedor** | Empaqueta una aplicación con lo que necesita para correr igual en cualquier máquina. | Cada servicio es un contenedor. |
| **Docker Compose** | Define y levanta varios contenedores juntos. | `docker-compose.yml` y `docker-compose.local.yml`. |
| **Colima** | Alternativa gratuita a Docker Desktop en macOS. | |
| **Proxy inverso** | Servidor que recibe las peticiones y las reparte a los servicios internos. | Caddy en el puerto 80. |
| **Caddy** | Servidor web y proxy inverso. | `infra/proxy/Caddyfile`. |
| **PostgreSQL** | Base de datos relacional. | Bases `exchange`, `documents`, `openfga`. |
| **Bruno** | Cliente de APIs open source que guarda colecciones como archivos. | Carpeta `bruno/`. |
| **Prueba e2e** (de punta a punta) | Prueba que recorre el sistema completo como un usuario real. | `scripts/e2e.sh`. |
| **Prueba unitaria** | Prueba de una pieza aislada, sin infraestructura. | Pruebas de `model` y `usecase`. |
| **Auditoría** | Registro de eventos de seguridad. | Tabla `exchange_audit`. |
| **Outbox** | Patrón para que escribir en la base y publicar un cambio (por ejemplo, a OpenFGA) sea consistente. | Pendiente para el diseño real; anotado en `CreateDocumentUseCase`. |
| **KMS / Key Vault** | Servicio que guarda llaves privadas y firma sin exponerlas. | En real reemplaza la tabla `signing_keys`. |

## 12. Códigos HTTP y errores

| Código | Significado | Cuándo aparece en el lab |
| --- | --- | --- |
| **200 OK** | Éxito. | |
| **201 Created** | Recurso creado. | Crear documento. |
| **204 No Content** | Éxito sin cuerpo. | Borrar documento, cerrar sesión. |
| **400 `invalid_request`** | Petición mal formada. | Falta `subject_token` o `grant_type` incorrecto. |
| **400 `invalid_target`** | Audiencia no permitida. | `audience=graph`. |
| **401 `invalid_token`** | Token inválido, vencido, alterado o de un emisor no confiable. | Token de Entra contra la API, token para Graph. |
| **401 `invalid_session`** | Sesión inexistente, vencida, revocada o reusada. Borra las cookies. | Refresh tras logout o con cookie robada. |
| **403 `access_denied`** | Identidad válida pero sin permiso. | Sin vínculo, sin membresía o Check denegado. |
| **403 `csrf`** | Falta o no coincide `X-XSRF-TOKEN`. | POST con cookies sin header. |
| **404** | No existe o es de otro tenant (no se revela). | Documento de `globex`. |
| **502** | El proxy no alcanza el servicio. | Servicio caído. |
| **503** | Dependencia no lista. | OpenFGA aún no inicializado. |
