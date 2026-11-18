package somonitores.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Um equipamento tratado em um contato de prospecção, com o resultado para ele.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ContatoEquipDTO {
    /** 'S|serial' ou 'M|codcli|marca|potencia' — mesma regra da prospecção */
    private String chaveEquip;
    private Long osReferencia;
    private String marca;
    private String modelo;
    private String serial;
    /** NAO_ATENDEU, PEDIU_RETORNO, INTERESSADO, ORCAMENTO_ENVIADO, COLETA_AGENDADA,
     *  SEM_INTERESSE, TROCOU_FORA, DESATIVADO, TELEFONE_ERRADO */
    private String resultado;
}
