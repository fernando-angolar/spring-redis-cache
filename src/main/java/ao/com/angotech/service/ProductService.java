package ao.com.angotech.service;


import ao.com.angotech.dto.ProductDTO;
import ao.com.angotech.entity.Product;
import ao.com.angotech.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);
    private static final String PRODUCT_KEY_PREFIX = "product::";

    private final ProductRepository productRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public ProductService(ProductRepository productRepository, RedisTemplate<String, Object> redisTemplate) {
        this.productRepository = productRepository;
        this.redisTemplate = redisTemplate;
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
