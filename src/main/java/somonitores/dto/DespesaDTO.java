package somonitores.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DespesaDTO {
    private Long id;
    private String descricao;
    private String categoria;
    private LocalDate vencimento;
    private String valor;
    private String status;
    private String iniciomes;
}
