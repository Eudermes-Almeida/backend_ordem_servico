package somonitores.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Uma ordem de serviço do equipamento, como aparece na página pública do QR Code.
 * NUNCA inclui valores (R$).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PassagemPublicaDTO {
    private Long os;
    /** data de entrada (yyyy-MM-dd) */
    private String entrada;
    /** data de fechamento (yyyy-MM-dd) */
    private String fechamento;
    private String status;
    private List<String> defeitos;
    private List<String> servicos;
    /** peças/baterias trocadas, ex.: "2 x Bateria 12V/7A" */
    private List<String> itens;
    private String tecnico;
    private boolean trocouBateria;
    /** só para OS fechada: fechamento + 90 dias (yyyy-MM-dd) */
    private String garantiaServicoAte;
    /** só para OS fechada que trocou baterias: fechamento + 180 dias (yyyy-MM-dd) */
    private String garantiaBateriaAte;
    /** true na OS a que pertence a etiqueta lida */
    private boolean osDaEtiqueta;
}
