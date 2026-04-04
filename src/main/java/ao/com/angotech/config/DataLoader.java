package ao.com.angotech.config;

import ao.com.angotech.entity.Product;
import ao.com.angotech.repository.ProductRepository;
import com.github.javafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

@Configuration
public class DataLoader {

    private static final Logger logger = LoggerFactory.getLogger(DataLoader.class);

    @Bean
    @ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
    CommandLineRunner loadData(ProductRepository repository,
                               @Value("${app.seed.count:100000}") int totalRegistros,
                               @Value("${app.seed.batch-size:1000}") int batchSize) {
        return args -> {
            long existingCount = repository.count();
            if (existingCount > 0) {
                logger.info("Seed ignorado: já existem {} produtos no banco.", existingCount);
                return;
            }

            Faker faker = new Faker(new Locale("pt-BR"));
            Random random = new Random();
            List<Product> buffer = new ArrayList<>(batchSize);

            logger.info("Iniciando seed de {} produtos em lotes de {}...", totalRegistros, batchSize);

            for (int i = 1; i <= totalRegistros; i++) {
                Product p = new Product();
                p.setNome(faker.commerce().productName());
                p.setDescricao(faker.lorem().sentence());
                p.setPreco(new BigDecimal(faker.commerce().price()));
                p.setCategoria(faker.commerce().department());
                p.setStock(random.nextInt(1000));
                p.setDataCriacao(LocalDateTime.now());

                buffer.add(p);

                if (buffer.size() == batchSize) {
                    repository.saveAll(buffer);
                    buffer.clear();
                }

                if (i % 5000 == 0) {
                    logger.info("Progresso seed: {} registros preparados.", i);
                }
            }

            if (!buffer.isEmpty()) {
                repository.saveAll(buffer);
            }

            long totalNoBanco = repository.count();
            logger.info("Seed finalizado. Total de produtos no banco: {}", totalNoBanco);
        };
    }

}
