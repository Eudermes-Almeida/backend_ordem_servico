package somonitores.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cliente com nobreaks de banco de baterias vencido (tela "Interação por WhatsApp"):
 * mesmos critérios da prospecção, agrupados por cliente.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ClienteBateriaVencidaDTO {
    private Long codcli;
    private String fantasia;
    private String rzsocial;
    /** campo CONTATO do cadastro */
    private String contato;
    /** campo FONE do cadastro (rótulo "Whatsapp" na tela de cadastro), como foi digitado */
    private String whatsapp;
    /** nobreaks do cliente que atendem aos filtros */
    private Integer qtNobreaks;
    /** maior tempo sem manutenção entre esses nobreaks */
    private Integer mesesMaisAntigo;
    /** último contato por WhatsApp registrado na prospecção (yyyy-MM-ddTHH:mm) */
    private String ultimoWhatsapp;
    private String ultimoWhatsappOperador;
}
