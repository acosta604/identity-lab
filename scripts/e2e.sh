#!/usr/bin/env bash
# Pruebas de punta a punta del laboratorio. Requiere: docker compose up, curl y jq.
#   ./scripts/e2e.sh
# Cada usuario tiene su propio "cookie jar" (como si fueran navegadores distintos).
set -uo pipefail
cd "$(dirname "$0")/.."

BASE=http://localhost
ENTRA=http://localhost:8090/entra
GRANT=urn:ietf:params:oauth:grant-type:token-exchange
TOKEN_TYPE=urn:ietf:params:oauth:token-type:access_token
WORK=$(mktemp -d)
PASS=0
FAIL=0
trap 'rm -rf "$WORK"' EXIT

ok()   { PASS=$((PASS + 1)); printf '  \033[32mOK\033[0m   %s\n' "$1"; }
bad()  { FAIL=$((FAIL + 1)); printf '  \033[31mFAIL\033[0m %s (esperado %s, obtenido %s)\n' "$1" "$2" "$3"; }
expect() { [[ "$3" == "$2" ]] && ok "$1" || bad "$1" "$2" "$3"; }
psql_exchange() { docker compose exec -T postgres psql -U postgres -d exchange -qtAc "$1"; }

# 1) "Iniciar sesion en Entra": el mock emite un token con los claims de infra/mock-entra.
entra_token() { # $1 = client_id que identifica a la persona en el mock; $2 = issuer (opcional)
  curl -s -X POST "${2:-$ENTRA}/token" -d grant_type=client_credentials -d "client_id=$1" \
       -d client_secret=lab -d scope=token-exchange-service | jq -r .access_token
}

# 2) Exchange: token de Entra -> sesion propia + JWT propio en cookies.
exchange() { # $1 = jar; $2 = token de Entra; resto = parametros extra (-d tenant=... etc.)
  local jar=$1 token=$2; shift 2
  touch "$jar"
  curl -s -o "$WORK/body" -w '%{http_code}' -c "$jar" -b "$jar" -X POST "$BASE/token/exchange" \
       -H "X-XSRF-TOKEN: $(xsrf "$jar")" \
       -d "grant_type=$GRANT" -d "subject_token=$token" -d "subject_token_type=$TOKEN_TYPE" "$@"
}

xsrf() { [[ -f "$1" ]] && awk '$6=="XSRF-TOKEN"{print $7}' "$1" | tail -1; }
jwt_payload() { local p; p=$(cut -d. -f2 <<<"$1" | tr '_-' '/+'); while (( ${#p} % 4 )); do p+="="; done; base64 -d <<<"$p"; }
cookie() { awk -v n="$2" '$6==n{print $7}' "$1" | tail -1; }

api() { # $1 = jar; $2 = metodo; $3 = ruta; $4 = json (opcional)
  touch "$1"
  local args=(-s -o "$WORK/body" -w '%{http_code}' -c "$1" -b "$1" -X "$2" "$BASE$3" -H "X-XSRF-TOKEN: $(xsrf "$1")")
  [[ -n "${4:-}" ]] && args+=(-H 'Content-Type: application/json' -d "$4")
  curl "${args[@]}"
}

ALICE=$WORK/alice BOB=$WORK/bob DAVE=$WORK/dave

echo "== Inicio de sesion y exchange"
code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/documents" -H "Authorization: Bearer $(entra_token alice)")
expect "1. el token de Entra no sirve para llamar APIs" 401 "$code"

code=$(exchange "$WORK/x" "$(entra_token graph-alice)")
expect "2. token de Entra para otra audiencia (Graph) se rechaza" 401 "$code"

code=$(exchange "$WORK/x" "$(entra_token alice http://localhost:8090/otro)")
expect "3. token de un emisor no confiable se rechaza" 401 "$code"

code=$(exchange "$WORK/x" "$(entra_token otro-tenant-alice)")
expect "3b. token de otro tenant de Azure se rechaza" 401 "$code"

code=$(exchange "$WORK/x" "$(entra_token mallory)")
expect "4. identidad de Entra sin usuario interno: 403" 403 "$code"

code=$(exchange "$WORK/x" "$(entra_token alice)" -d tenant=acme -d organization=globex-main)
expect "5. organizacion de otro tenant: 403" 403 "$code"

code=$(exchange "$WORK/x" "$(entra_token alice)" -d audience=graph)
expect "5b. audiencia no permitida: 400" 400 "$code"

code=$(exchange "$ALICE" "$(entra_token alice)" -d tenant=acme -d organization=acme-ventas)
expect "6. alice hace el exchange a acme-ventas" 200 "$code"
teams=$(jq -c '[.context.teams[] | .id + ":" + .role]' "$WORK/body")
expect "6b. los claims traen sus equipos con rol" '["acme-ventas-norte:lead","acme-ventas-sur:member"]' "$teams"

exchange "$BOB" "$(entra_token bob)" >/dev/null
exchange "$DAVE" "$(entra_token dave)" >/dev/null

echo "== Autorizacion con OpenFGA y tuplas contextuales"
expect "7. alice (lead norte) edita document:42" 200 "$(api "$ALICE" PUT /api/documents/42 '{"title":"Plan Norte v2"}')"
expect "8. alice (member sur) NO edita document:43" 403 "$(api "$ALICE" PUT /api/documents/43 '{"title":"x"}')"
expect "8b. pero si lo lee" 200 "$(api "$ALICE" GET /api/documents/43)"
expect "9. dave (org member) lee document:44 de la organizacion" 200 "$(api "$DAVE" GET /api/documents/44)"
expect "9b. dave NO lee document:42 del equipo norte" 403 "$(api "$DAVE" GET /api/documents/42)"
expect "10. alice en acme-ventas NO lee document:77 (marketing)" 403 "$(api "$ALICE" GET /api/documents/77)"
expect "10b. alice cambia a acme-marketing" 200 "$(api "$ALICE" POST /session/context '{"tenant":"acme","organization":"acme-marketing"}')"
expect "10c. ahora si lee document:77" 200 "$(api "$ALICE" GET /api/documents/77)"
expect "10d. y ya no lee document:42" 403 "$(api "$ALICE" GET /api/documents/42)"
api "$ALICE" POST /session/context '{"organization":"acme-ventas"}' >/dev/null
expect "11. bob (org admin) borra document:42" 204 "$(api "$BOB" DELETE /api/documents/42)"

code=$(api "$ALICE" POST /api/documents '{"title":"Nuevo","body":"...","teamId":"acme-ventas-norte"}')
expect "11b. alice crea un documento en su equipo" 201 "$code"
new_id=$(jq -r .id "$WORK/body")
expect "11c. dave no ve el documento del equipo" 403 "$(api "$DAVE" GET "/api/documents/$new_id")"

echo "== Cambios de membresia, sesion y CSRF"
psql_exchange "update team_memberships set status='INACTIVE' where user_id='alice' and team_id='acme-ventas-norte'"
expect "12. con el token viejo alice aun edita su documento nuevo (owner)" 200 "$(api "$ALICE" PUT "/api/documents/$new_id" '{"title":"v2"}')"
expect "12b. refresh relee membresias" 200 "$(api "$ALICE" POST /session/refresh)"
expect "12c. el token nuevo ya no trae el equipo norte" '["acme-ventas-sur"]' "$(api "$ALICE" GET /api/whoami >/dev/null; jq -c '[.claims.teams[].id]' "$WORK/body")"
psql_exchange "update team_memberships set status='ACTIVE' where user_id='alice' and team_id='acme-ventas-norte'"

docker compose stop mock-entra >/dev/null 2>&1
expect "13. con Entra apagado el refresh sigue funcionando" 200 "$(api "$ALICE" POST /session/refresh)"
expect "13b. y el cambio de organizacion tambien" 200 "$(api "$ALICE" POST /session/context '{"organization":"acme-ventas"}')"
docker compose start mock-entra >/dev/null 2>&1

at=$(cookie "$ALICE" __Host-at)
tampered="${at%%.*}.$(echo '{"sub":"bob","tid":"acme"}' | base64 | tr -d '=\n' | tr '/+' '_-').${at##*.}"
code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/documents" -H "Authorization: Bearer $tampered")
expect "14. token propio con claims alterados: 401" 401 "$code"

code=$(curl -s -o /dev/null -w '%{http_code}' -b "$ALICE" -X POST "$BASE/session/refresh")
expect "15. POST con cookies y sin X-XSRF-TOKEN: 403" 403 "$code"

cp "$ALICE" "$WORK/alice-viejo"                       # el atacante copia la cookie...
api "$ALICE" POST /session/refresh >/dev/null           # ...alice la usa y se rota
expect "16. reusar la cookie de sesion ya rotada: 401" 401 "$(api "$WORK/alice-viejo" POST /session/refresh)"
expect "16b. ...y la sesion de alice quedo revocada" 401 "$(api "$ALICE" POST /session/refresh)"

exchange "$DAVE" "$(entra_token dave)" >/dev/null
psql_exchange "update sessions set last_seen_at = now() - interval '2 hours' where user_id='dave' and revoked_at is null"
expect "17. sesion inactiva: 401, hay que volver a Entra" 401 "$(api "$DAVE" POST /session/refresh)"

echo "== Servicio a servicio"
exchange "$BOB" "$(entra_token bob)" >/dev/null
code=$(curl -s -o "$WORK/body" -w '%{http_code}' -X POST "$BASE/token/exchange" -d "grant_type=$GRANT" \
       -d "subject_token=$(cookie "$BOB" __Host-at)" -d audience=reports-api -d actor=documents-api)
expect "18. token propio -> token para reports-api" 200 "$code"
act=$(jwt_payload "$(jq -r .access_token "$WORK/body")" | jq -r .act.sub)
expect "18b. el nuevo token lleva act" "service:documents-api" "$act"

echo
echo "Resultado: $PASS OK, $FAIL fallidas"
[[ $FAIL -eq 0 ]]
