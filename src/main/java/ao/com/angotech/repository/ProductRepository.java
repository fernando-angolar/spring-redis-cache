package ao.com.angotech.repository;

import ao.com.angotech.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByCategoriaIgnoreCase(String categoria);
}
