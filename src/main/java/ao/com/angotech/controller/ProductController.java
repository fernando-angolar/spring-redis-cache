package ao.com.angotech.controller;


import ao.com.angotech.dto.ProductDTO;
import ao.com.angotech.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/produtos")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ResponseEntity<List<ProductDTO>> listarTodos() {
        return ResponseEntity.ok(productService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(productService.buscarPorId(id));
    }

    @GetMapping("/categoria/{categoria}")
    public ResponseEntity<List<ProductDTO>> buscarPorCategoria(@PathVariable String categoria) {
        return ResponseEntity.ok(productService.buscarPorCategoria(categoria));
    }

    @PostMapping
    public ResponseEntity<ProductDTO> criarProduto(@RequestBody ProductDTO productDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.criarProduto(productDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductDTO> atualizarProduto(@PathVariable Long id, @RequestBody ProductDTO productDTO) {
        return ResponseEntity.ok(productService.atualizarProduto(id, productDTO));
    }

    @DeleteMapping("/cache")
    public ResponseEntity<Map<String, String>> limparTodoCache() {
        productService.limparTodoCache();
        return ResponseEntity.ok(Map.of("message", "Cache limpo com sucesso"));
    }

    @GetMapping("/cache/stats")
    public ResponseEntity<Map<String, Object>> obterEstatisticasCache() {
        return ResponseEntity.ok(productService.obterEstatisticasCache());
    }
}
