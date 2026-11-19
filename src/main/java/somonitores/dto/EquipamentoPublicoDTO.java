package somonitores.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Página pública aberta pelo QR Code da etiqueta: equipamento, última manutenção e histórico.
 * Histórico = OS do MESMO cliente com o mesmo serial; sem serial confiável, só a OS da etiqueta.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EquipamentoPublicoDTO {
    private String cliente;
    /** código do cliente */
    private Long codcli;
    private String equipamento;
    private String marca;
    private String modelo;
    private String serial;
    private String patrimonio;
    /** false quando o serial não identifica o equipamento (histórico = só a OS da etiqueta) */
    private boolean historicoPorSerial;
    /** estado do banco de baterias do aparelho: ATIVO / ATENCAO / VENCIDO (null = sem manutenção válida) */
    private String bateriaSituacao;
    /** data da última manutenção válida (OS FECHADA com valor) - yyyy-MM-dd */
    private String bateriaUltimaManutencao;
    /** meses completos desde a última manutenção válida */
    private Integer bateriaMeses;
    /** OS da última manutenção válida */
    private Long bateriaOs;
    /** última manutenção concluída (OS fechada mais recente); null se nenhuma */
    private PassagemPublicaDTO ultimaManutencao;
    /** todas as OS do equipamento, da mais recente para a mais antiga */
    private List<PassagemPublicaDTO> historico;
}
