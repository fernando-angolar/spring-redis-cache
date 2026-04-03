package ao.com.angotech.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductDTO(

        Long id,
        String nome,
        String descricao,
        BigDecimal preco,
        String categoria,
        Integer stock,
        LocalDateTime dataCriacao

) {
}
