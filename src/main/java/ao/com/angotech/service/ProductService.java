package ao.com.angotech.service;


import ao.com.angotech.dto.ProductDTO;
import ao.com.angotech.entity.Product;
import ao.com.angotech.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);
    // Namespacing de chaves: facilita debug, limpeza seletiva e evita colisões.
    // Exemplo: product::1
    private static final String PRODUCT_KEY_PREFIX = "product::";

    private final ProductRepository productRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheManager cacheManager;

    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    public ProductService(ProductRepository productRepository,
                          RedisTemplate<String, Object> redisTemplate,
                          CacheManager cacheManager) {
        this.productRepository = productRepository;
        this.redisTemplate = redisTemplate;
        this.cacheManager = cacheManager;
    }


    public ProductDTO buscarPorId(Long id) {
        StopWatch stopWatch = new StopWatch("ProductService.buscarPorId");
        stopWatch.start();
        try {
            // Cache Aside (manual):
            // 1) tenta Redis primeiro
            // 2) em MISS busca no banco
            // 3) salva no Redis para próximas leituras
            String cacheKey = PRODUCT_KEY_PREFIX + id;
            Object cachedValue = redisTemplate.opsForValue().get(cacheKey);

            if (cachedValue instanceof ProductDTO productDTO) {
                cacheHits.incrementAndGet();
                logger.info("HIT Redis para chave {}", cacheKey);
                return productDTO;
            }

            cacheMisses.incrementAndGet();
            logger.info("MISS Redis para chave {}", cacheKey);
            Product product = productRepository.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Produto não encontrado para id=" + id));

            ProductDTO dto = toDTO(product);
            // TTL aplicado aqui vem da configuração geral do Redis/eviction + limpeza por atualização.
            redisTemplate.opsForValue().set(cacheKey, dto);
            return dto;
        } finally {
            stopWatch.stop();
            logExecutionTime("buscarPorId", stopWatch);
        }
    }

    @Cacheable(value = "products")
    public List<ProductDTO> listarTodos() {
        StopWatch stopWatch = new StopWatch("ProductService.listarTodos");
        stopWatch.start();
        try {
            return productRepository.findAll()
                    .stream()
                    .map(this::toDTO)
                    .toList();
        } finally {
            stopWatch.stop();
            logExecutionTime("listarTodos", stopWatch);
        }
    }

    @Cacheable(value = "products", key = "#categoria")
    public List<ProductDTO> buscarPorCategoria(String categoria) {
        StopWatch stopWatch = new StopWatch("ProductService.buscarPorCategoria");
        stopWatch.start();
        try {
            return productRepository.findByCategoriaIgnoreCase(categoria)
                    .stream()
                    .map(this::toDTO)
                    .toList();
        } finally {
            stopWatch.stop();
            logExecutionTime("buscarPorCategoria", stopWatch);
        }
    }

    @CacheEvict(value = "products", allEntries = true)
    public ProductDTO criarProduto(ProductDTO productDTO) {
        StopWatch stopWatch = new StopWatch("ProductService.criarProduto");
        stopWatch.start();
        try {
            Product entity = toEntity(productDTO);

            if (entity.getDataCriacao() == null) {
                entity.setDataCriacao(LocalDateTime.now());
            }

            Product saved = productRepository.save(entity);
            redisTemplate.delete(PRODUCT_KEY_PREFIX + saved.getId());
            return toDTO(saved);
        } finally {
            stopWatch.stop();
            logExecutionTime("criarProduto", stopWatch);
        }
    }

    @Caching(
            put = @CachePut(value = "product", key = "#id"),
            evict = @CacheEvict(value = "products", allEntries = true)
    )
    public ProductDTO atualizarProduto(Long id, ProductDTO productDTO) {
        StopWatch stopWatch = new StopWatch("ProductService.atualizarProduto");
        stopWatch.start();
        try {
            Product existing = productRepository.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Produto não encontrado para id=" + id));

            existing.setNome(productDTO.nome());
            existing.setDescricao(productDTO.descricao());
            existing.setPreco(productDTO.preco());
            existing.setCategoria(productDTO.categoria());
            existing.setStock(productDTO.stock());

            if (productDTO.dataCriacao() != null) {
                existing.setDataCriacao(productDTO.dataCriacao());
            }

            Product saved = productRepository.save(existing);
            ProductDTO updatedDTO = toDTO(saved);
            redisTemplate.opsForValue().set(PRODUCT_KEY_PREFIX + id, updatedDTO);
            return updatedDTO;
        } finally {
            stopWatch.stop();
            logExecutionTime("atualizarProduto", stopWatch);
        }
    }

    public void limparTodoCache() {
        StopWatch stopWatch = new StopWatch("ProductService.limparTodoCache");
        stopWatch.start();
        try {
            // Eviction programático para manutenção: limpa caches geridos pelo Spring Cache.
            for (String cacheName : cacheManager.getCacheNames()) {
                Cache cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    cache.clear();
                }
            }

            RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
            if (connectionFactory == null) {
                logger.warn("RedisConnectionFactory não disponível para limpeza de chaves manuais.");
                return;
            }

            try (RedisConnection connection = connectionFactory.getConnection();
                 Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match(PRODUCT_KEY_PREFIX + "*").count(1000).build())) {
                while (cursor.hasNext()) {
                    redisTemplate.delete(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            } catch (Exception ex) {
                logger.warn("Falha ao limpar chaves manuais do Redis: {}", ex.getMessage());
            }
        } finally {
            stopWatch.stop();
            logExecutionTime("limparTodoCache", stopWatch);
        }
    }

    public Map<String, Object> obterEstatisticasCache() {
        StopWatch stopWatch = new StopWatch("ProductService.obterEstatisticasCache");
        stopWatch.start();
        try {
            long hits = cacheHits.get();
            long misses = cacheMisses.get();
            long total = hits + misses;
            double hitRate = total == 0 ? 0 : ((double) hits / total) * 100;

            Long totalKeys = 0L;
            try {
                var keys = redisTemplate.keys("*");
                totalKeys = keys == null ? 0L : (long) keys.size();
            } catch (Exception ex) {
                logger.warn("Não foi possível calcular total de chaves no Redis: {}", ex.getMessage());
            }

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("hits", hits);
            stats.put("misses", misses);
            stats.put("totalConsultas", total);
            stats.put("hitRatePercent", hitRate);
            stats.put("cachesConfigurados", cacheManager.getCacheNames());
            stats.put("totalChavesRedis", totalKeys);

            return stats;
        } finally {
            stopWatch.stop();
            logExecutionTime("obterEstatisticasCache", stopWatch);
        }
    }

    private void logExecutionTime(String metodo, StopWatch stopWatch) {
        logger.info("Tempo de execução {}: {} ms", metodo, stopWatch.getTotalTimeMillis());
    }

    private ProductDTO toDTO(Product product) {
        return new ProductDTO(
                product.getId(),
                product.getNome(),
                product.getDescricao(),
                product.getPreco(),
                product.getCategoria(),
                product.getStock(),
                product.getDataCriacao()
        );
    }

    private Product toEntity(ProductDTO dto) {
        return new Product(
                dto.id(),
                dto.nome(),
                dto.descricao(),
                dto.preco(),
                dto.categoria(),
                dto.stock(),
                dto.dataCriacao()
        );
    }

}
