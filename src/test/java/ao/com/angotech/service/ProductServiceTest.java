package ao.com.angotech.service;


import ao.com.angotech.dto.ProductDTO;
import ao.com.angotech.entity.Product;
import ao.com.angotech.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private RedisConnectionFactory redisConnectionFactory;

    @Mock
    private RedisConnection redisConnection;

    @Mock
    private Cursor<byte[]> cursor;

    @Mock
    private Cache cache;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, redisTemplate, cacheManager);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void buscarPorId_deveRetornarDoRedisQuandoHit() {
        ProductDTO cached = buildDTO(1L);
        when(valueOperations.get("product::1")).thenReturn(cached);

        ProductDTO result = productService.buscarPorId(1L);

        assertEquals(1L, result.id());
        verify(productRepository, never()).findById(any());
    }

    @Test
    void buscarPorId_deveBuscarNoBancoQuandoMissEPopularRedis() {
        Product product = buildEntity(2L);
        when(valueOperations.get("product::2")).thenReturn(null);
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));

        ProductDTO result = productService.buscarPorId(2L);

        assertEquals(2L, result.id());
        verify(productRepository, times(1)).findById(2L);
        verify(valueOperations, times(1)).set(eq("product::2"), any(ProductDTO.class));
    }

    @Test
    void buscarPorCategoria_deveMapearListaDeProdutos() {
        when(productRepository.findByCategoriaIgnoreCase("Eletrônicos"))
                .thenReturn(List.of(buildEntity(3L), buildEntity(4L)));

        List<ProductDTO> result = productService.buscarPorCategoria("Eletrônicos");

        assertEquals(2, result.size());
    }

    @Test
    void atualizarProduto_devePersistirEAtualizarCacheManual() {
        Product existing = buildEntity(5L);
        ProductDTO update = new ProductDTO(
                5L,
                "Produto Atualizado",
                "Descrição 5",
                new BigDecimal("99.90"),
                "Eletrônicos",
                10,
                LocalDateTime.now()
        );

        when(productRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductDTO result = productService.atualizarProduto(5L, update);

        assertEquals("Produto Atualizado", result.nome());
        verify(valueOperations).set(eq("product::5"), any(ProductDTO.class));
    }

    @Test
    void limparTodoCache_deveLimparCachesEChavesManuaisDoRedis() {
        when(cacheManager.getCacheNames()).thenReturn(Set.of("product", "products"));
        when(cacheManager.getCache("product")).thenReturn(cache);
        when(cacheManager.getCache("products")).thenReturn(cache);
        when(redisTemplate.getConnectionFactory()).thenReturn(redisConnectionFactory);
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.scan(any())).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn("product::10".getBytes(StandardCharsets.UTF_8));

        productService.limparTodoCache();

        verify(cache, times(2)).clear();
        verify(redisTemplate).delete("product::10");
    }

    @Test
    void obterEstatisticasCache_deveRetornarContadores() {
        when(cacheManager.getCacheNames()).thenReturn(Set.of("product", "products"));
        when(valueOperations.get("product::7")).thenReturn(buildDTO(7L));
        productService.buscarPorId(7L);

        when(valueOperations.get("product::8")).thenReturn(null);
        when(productRepository.findById(8L)).thenReturn(Optional.of(buildEntity(8L)));
        productService.buscarPorId(8L);

        Map<String, Object> stats = productService.obterEstatisticasCache();

        assertEquals(1L, stats.get("hits"));
        assertEquals(1L, stats.get("misses"));
        assertNotNull(stats.get("hitRatePercent"));
        assertTrue(stats.containsKey("cachesConfigurados"));
    }

    private Product buildEntity(Long id) {
        Product p = new Product();
        p.setId(id);
        p.setNome("Produto " + id);
        p.setDescricao("Descrição " + id);
        p.setPreco(new BigDecimal("99.90"));
        p.setCategoria("Eletrônicos");
        p.setStock(10);
        p.setDataCriacao(LocalDateTime.now());
        return p;
    }

    private ProductDTO buildDTO(Long id) {
        return new ProductDTO(
                id,
                "Produto " + id,
                "Descrição " + id,
                new BigDecimal("99.90"),
                "Eletrônicos",
                10,
                LocalDateTime.now()
        );
    }


}
