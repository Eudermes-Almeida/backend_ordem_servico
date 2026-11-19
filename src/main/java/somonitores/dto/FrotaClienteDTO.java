package somonitores.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Nobreaks do cliente da etiqueta (página pública do QR), com a situação dos bancos de baterias.
 * Mesmos critérios da prospecção; só aparelhos com manutenção nos últimos 6 anos.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FrotaClienteDTO {
    private String cliente;
    /** código do cliente */
    private Long codcli;
    private int ativos;
    private int atencao;
    private int vencidos;
    /** ordenados: vencidos, atenção, ativos (mais antigos primeiro) */
    private List<FrotaNobreakDTO> nobreaks;
}
