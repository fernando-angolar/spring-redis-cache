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
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

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
        String cacheKey = PRODUCT_KEY_PREFIX + id;
        Object cachedValue = redisTemplate.opsForValue().get(cacheKey);

        if (cachedValue instanceof ProductDTO productDTO) {
            logger.info("HIT Redis para chave {}", cacheKey);
            return productDTO;
        }

        logger.info("MISS Redis para chave {}", cacheKey);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Produto não encontrado para id=" + id));

        ProductDTO dto = toDTO(product);
        redisTemplate.opsForValue().set(cacheKey, dto);
        return dto;
    }

    @Cacheable(value = "products")
    public List<ProductDTO> listarTodos() {
        return productRepository.findAll()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Cacheable(value = "products", key = "#categoria")
    public List<ProductDTO> buscarPorCategoria(String categoria) {
        return productRepository.findByCategoriaIgnoreCase(categoria)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @CacheEvict(value = "products", allEntries = true)
    public ProductDTO criarProduto(ProductDTO productDTO) {
        Product entity = toEntity(productDTO);

        if (entity.getDataCriacao() == null) {
            entity.setDataCriacao(LocalDateTime.now());
        }

        Product saved = productRepository.save(entity);
        redisTemplate.delete(PRODUCT_KEY_PREFIX + saved.getId());
        return toDTO(saved);
    }

    @Caching(
            put = @CachePut(value = "product", key = "#id"),
            evict = @CacheEvict(value = "products", allEntries = true)
    )
    public ProductDTO atualizarProduto(Long id, ProductDTO productDTO) {
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
    }

    public void limparTodoCache() {
        for (String cacheName : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }

        try (Cursor<byte[]> cursor = redisTemplate.getConnectionFactory()
                .getConnection()
                .scan(ScanOptions.scanOptions().match(PRODUCT_KEY_PREFIX + "*").count(1000).build())) {
            while (cursor.hasNext()) {
                redisTemplate.delete(new String(cursor.next(), StandardCharsets.UTF_8));
            }
        } catch (Exception ex) {
            logger.warn("Falha ao limpar chaves manuais do Redis: {}", ex.getMessage());
        }
    }

    public Map<String, Object> obterEstatisticasCache() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        double hitRate = total == 0 ? 0 : ((double) hits / total) * 100;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("hits", hits);
        stats.put("misses", misses);
        stats.put("totalConsultas", total);
        stats.put("hitRatePercent", hitRate);
        stats.put("cachesConfigurados", cacheManager.getCacheNames());

        return stats;
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
