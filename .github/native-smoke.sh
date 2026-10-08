#!/usr/bin/env bash
# Exercita os caminhos relevantes pra reflexao (login, CRUD, validacao, 404, ask,
# actuator). Usado tanto sob o agente de rastreamento quanto no binario native.
B=http://localhost:8080
code() { curl -s -m 10 -o /dev/null -w '%{http_code}' "$@" || echo 000; }
for i in $(seq 1 120); do curl -sf -m 3 $B/actuator/health >/dev/null 2>&1 && break; sleep 1; done
R="health=$(code $B/actuator/health)"
R="$R login_errado=$(code -X POST -H 'Content-Type: application/json' -d '{"username":"admin","password":"errada"}' $B/auth/login)"
TOKEN=$(curl -s -m 10 -X POST -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' $B/auth/login | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
R="$R login_ok=$([ -n "$TOKEN" ] && echo 200 || echo FALHA)"
A="Authorization: Bearer $TOKEN"
R="$R post=$(code -X POST -H "$A" -H 'Content-Type: application/json' -d '{"nome":"Estado Teste","sigla":"ZZ"}' $B/estado)"
ID=$(curl -s -m 10 "$B/estado/paginado?busca=Teste" | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)
R="$R paginado=$(code "$B/estado/paginado?busca=Teste")"
R="$R get=$(code $B/estado/${ID:-1})"
R="$R put=$(code -X PUT -H "$A" -H 'Content-Type: application/json' -d '{"nome":"Estado Teste 2","sigla":"ZY"}' $B/estado/${ID:-1})"
R="$R invalido=$(code -X POST -H "$A" -H 'Content-Type: application/json' -d '{"nome":"x","sigla":"ZZZ"}' $B/estado)"
R="$R inexistente=$(code $B/estado/999999)"
R="$R sem_auth=$(code -X POST -H 'Content-Type: application/json' -d '{"nome":"Estado Teste","sigla":"ZZ"}' $B/estado)"
R="$R delete=$(code -X DELETE -H "$A" $B/estado/${ID:-1})"
R="$R ask=$(code -X POST -H 'Content-Type: application/json' -d '{"question":"oi"}' $B/ask)"
R="$R prom=$(code $B/actuator/prometheus)"
echo "$R"
