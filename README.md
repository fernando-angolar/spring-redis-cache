# Spring Redis Cache (Product API)

Projeto de estudo com Spring Boot + Redis focado em cache na prática.

## Stack
- Java 17+
- Spring Boot
- Spring Data JPA
- Spring Cache
- Redis
- PostgreSQL
- Docker Compose

## Como rodar
1. Suba a infraestrutura:
   ```bash
   docker compose up -d
   ```
2. (Opcional) Monitorar comandos Redis:
   ```bash
   redis-cli MONITOR
   ```
3. Rode a aplicação:
   ```bash
   mvn spring-boot:run
   ```

## Configurações importantes
- `spring.cache.type=redis`
- Seed (faker):
  - `app.seed.enabled=true`
  - `app.seed.count=100000`
  - `app.seed.batch-size=1000`

## Estratégias de cache implementadas
- **Cache Aside** manual por ID em `buscarPorId`:
  - Tenta Redis (`product::{id}`)
  - MISS => consulta banco e preenche cache
- **Cache de lista** com `@Cacheable("products")`
- **Invalidação**:
  - `criarProduto`: limpa cache de listas
  - `atualizarProduto`: atualiza cache individual e limpa listas
- **TTL**:
  - `product`: 3 min
  - `products`: 1 min
- **Namespacing**:
  - `product::{id}`

## Eviction no Redis
No `docker-compose.yml`, o Redis está com política:
- `maxmemory-policy allkeys-lru`
- `maxmemory 256mb`

Isso prioriza remoção de chaves menos recentemente usadas quando memória atingir limite.

## Endpoints
Base: `/api/produtos`

- `GET /api/produtos`
- `GET /api/produtos/{id}`
- `GET /api/produtos/categoria/{categoria}`
- `POST /api/produtos`
- `PUT /api/produtos/{id}`
- `DELETE /api/produtos/cache`
- `GET /api/produtos/cache/stats`

## Exemplos curl
```bash
curl -X GET http://localhost:8080/api/produtos/1

curl -X GET http://localhost:8080/api/produtos/categoria/Eletrônicos

curl -X POST http://localhost:8080/api/produtos \
  -H "Content-Type: application/json" \
  -d '{
    "nome":"Teclado Mecânico",
    "descricao":"Switch blue",
    "preco":299.90,
    "categoria":"Periféricos",
    "stock":50,
    "dataCriacao":"2026-04-04T10:00:00"
  }'

curl -X PUT http://localhost:8080/api/produtos/1 \
  -H "Content-Type: application/json" \
  -d '{
    "id":1,
    "nome":"Teclado Mecânico Pro",
    "descricao":"Switch brown",
    "preco":349.90,
    "categoria":"Periféricos",
    "stock":45,
    "dataCriacao":"2026-04-04T10:00:00"
  }'

curl -X DELETE http://localhost:8080/api/produtos/cache
curl -X GET http://localhost:8080/api/produtos/cache/stats
```

## Como validar performance e expiração (TTL)
1. Chame `GET /api/produtos/{id}` (1ª vez): deve ocorrer MISS.
2. Chame novamente o mesmo endpoint: deve ocorrer HIT e menor tempo nos logs.
3. Aguarde o TTL expirar (ex.: 3 min para `product`) e chame novamente:
   - esperado: MISS e novo acesso ao banco.
4. Para listas, repita com `GET /api/produtos/categoria/{categoria}` e aguarde 1 min.

## Postman
Consulte também o arquivo [`POSTMAN_TEST_PLAN.md`](POSTMAN_TEST_PLAN.md) para roteiro completo de comparação.
