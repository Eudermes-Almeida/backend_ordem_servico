package somonitores.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dados para imprimir a etiqueta (50 x 30 mm) de uma OS fechada.
 * Uso interno (sistema da loja).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EtiquetaOsDTO {
    private Long os;
    /** código aleatório que vai no QR Code */
    private String codigo;
    /** link completo gravado no QR Code */
    private String url;
    /** data de fechamento (yyyy-MM-dd) */
    private String fechamento;
    /** fechamento + 90 dias (yyyy-MM-dd) */
    private String garantiaServicoAte;
    /** fechamento + 180 dias (yyyy-MM-dd); null quando a OS não trocou baterias */
    private String garantiaBateriaAte;
    private String equipamento;
    private String marca;
    private String modelo;
    private String serial;
    private String fantasia;
}
