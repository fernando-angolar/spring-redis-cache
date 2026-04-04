# Plano de Teste de Performance e Comparação (Postman)

## Pré-requisitos
1. Subir infraestrutura:
    - `docker compose up -d`
2. Iniciar aplicação Spring Boot.
3. Opcional para carga de dados:
    - `app.seed.enabled=true`
    - `app.seed.count=100000`
4. Abrir monitor do Redis em outro terminal:
    - `redis-cli MONITOR`

## 1) GET por ID sem cache vs com cache
1. Execute `GET /api/produtos/{id}` pela primeira vez.
2. Verifique nos logs do app:
    - `MISS Redis para chave product::{id}`
    - tempo do método `buscarPorId`.
3. Execute o mesmo request novamente.
4. Verifique nos logs:
    - `HIT Redis para chave product::{id}`
    - tempo menor em `buscarPorId`.
5. Compare os tempos das duas execuções no Postman e no log da aplicação.

## 2) GET por categoria com lista grande (com e sem cache)
1. Execute `GET /api/produtos/categoria/{categoria}` (1ª vez).
2. Verifique tempo no Postman e log de `buscarPorCategoria`.
3. Execute o mesmo endpoint novamente.
4. Compare o tempo de resposta: a 2ª chamada deve reduzir por uso de cache (`@Cacheable`).

## 3) PUT e invalidação de cache
1. Execute `PUT /api/produtos/{id}` alterando campos do produto.
2. Em seguida execute:
    - `GET /api/produtos/{id}` para validar atualização
    - `GET /api/produtos` e `GET /api/produtos/categoria/{categoria}` para repopular cache de lista.
3. Verifique se os dados retornam atualizados (indicando invalidação e recarga corretas).

## 4) Endpoint de manutenção
1. Execute `DELETE /api/produtos/cache`.
2. Refaça `GET /api/produtos/{id}` e confirme novo `MISS` nos logs.
3. Consulte `GET /api/produtos/cache/stats` para conferir hit/miss acumulados.

## Coleção sugerida no Postman
- `GET {{baseUrl}}/api/produtos`
- `GET {{baseUrl}}/api/produtos/{{id}}`
- `GET {{baseUrl}}/api/produtos/categoria/{{categoria}}`
- `POST {{baseUrl}}/api/produtos`
- `PUT {{baseUrl}}/api/produtos/{{id}}`
- `DELETE {{baseUrl}}/api/produtos/cache`
- `GET {{baseUrl}}/api/produtos/cache/stats`
