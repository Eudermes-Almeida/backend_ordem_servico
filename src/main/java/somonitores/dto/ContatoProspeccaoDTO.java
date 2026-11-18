package somonitores.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Um contato do telemarketing com o cliente (ligação ou WhatsApp),
 * com os equipamentos tratados naquele contato.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ContatoProspeccaoDTO {
    private Long id;
    private Long codcli;
    /** nome fantasia do cliente (preenchido pelo servidor nas consultas) */
    private String fantasia;
    /** yyyy-MM-ddTHH:mm (preenchido pelo servidor) */
    private String dataHora;
    private String operador;
    /** TELEFONE ou WHATSAPP */
    private String canal;
    private String pessoaContatada;
    private String observacao;
    /** yyyy-MM-dd (opcional) */
    private String dataRetorno;
    @Builder.Default
    private List<ContatoEquipDTO> equipamentos = new ArrayList<>();
}
