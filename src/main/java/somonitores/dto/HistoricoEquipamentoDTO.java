package somonitores.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Uma passagem (ordem de serviço) de um equipamento pelo laboratório.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HistoricoEquipamentoDTO {
    private Long os;
    private Long codcli;
    private String fantasia;
    /** data de entrada (yyyy-MM-dd) */
    private String data;
    /** data de fechamento (yyyy-MM-dd) */
    private String fechamento;
    private String status;
    private String totaliq;
    private String marca;
    private String modelo;
    private String serial;
    private String tarefa1;
    private String baterias;
    private String qt1;
    private String tecnico;
    /** true = OS fechada com valor > 0 (conta como manutenção / troca de baterias) */
    private Boolean valida;
}
