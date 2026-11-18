package somonitores.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Um nobreak cujo banco de baterias está vencido (prospecção de troca de baterias).
 * Representa a ÚLTIMA manutenção válida do equipamento.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NobreakBateriaVencidaDTO {
    private Long ultimaOs;
    private Long codcli;
    private String fantasia;
    private String rzsocial;
    private String marca;
    private String modelo;
    private Integer potenciaVa;
    private String serial;
    private String patrimonio;
    /** "SERIAL" ou "MARCA+MODELO" */
    private String identificadoPor;
    /** data da última manutenção válida (yyyy-MM-dd) */
    private String ultimaManutencao;
    private String valorUltimaOs;
    private Integer mesesDesdeManutencao;
    private Integer qtManutencoes;
    private Integer qtPassagensTotal;
    private Integer qtPassagensModelo;

    // ------------------------------------------------ prospecção / telemarketing
    /** identificação do equipamento (usada para registrar o contato) */
    private String chaveEquip;
    /**
     * SEM_CONTATO, CLIENTE_CONTATADO (cliente contatado, equipamento não tratado),
     * ANDAMENTO ou REJEITADO. Só considera contatos feitos DEPOIS da última manutenção.
     */
    private String situacao;
    /** último contato com o CLIENTE (yyyy-MM-ddTHH:mm) e quem fez */
    private String clienteUltimoContato;
    private String clienteUltimoOperador;
    /** último resultado registrado para ESTE equipamento */
    private String equipResultado;
    private String equipUltimoContato;
    private String equipOperador;
    /** data de retorno combinada no contato deste equipamento (yyyy-MM-dd) */
    private String equipDataRetorno;
}
