package somonitores.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Um nobreak do cliente na página pública do QR, com a situação do banco de baterias.
 * NUNCA inclui valores (R$).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FrotaNobreakDTO {
    private String marca;
    private String modelo;
    /** serial "limpo" (sem texto/anotação colada); null quando não identifica o aparelho */
    private String serial;
    private String patrimonio;
    /** OS da última manutenção válida (FECHADA com valor) */
    private Long ultimaOs;
    /** data da última manutenção válida (yyyy-MM-dd) */
    private String ultimaManutencao;
    /** meses completos desde a última manutenção */
    private Integer meses;
    /** ATIVO (< 18 meses), ATENCAO (18 a < 24) ou VENCIDO (>= 24) */
    private String situacao;
}
