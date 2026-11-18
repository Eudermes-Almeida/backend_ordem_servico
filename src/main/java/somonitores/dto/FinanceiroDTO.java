package somonitores.dto;

import lombok.*;
import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FinanceiroDTO {

    private Long os;
    private LocalDate dataEntrada;
    private String fantasia;
    private Long codcli;
    private String equipamento;
    private String marca;
    private String modelo;
    private String totaliq;
    private LocalDate fechamento;
    private LocalDate dataFaturamento;
    private String codRemessa;
    private String codBordero;
    private String tipoPagamento;
    private String qtParcela;
    private String prazo;
    private LocalDate vencimento1;
    private String valorParcela1;
    private String statusParcela1;
    private LocalDate vencimento2;
    private String valorParcela2;
    private String statusParcela2;
    private LocalDate vencimento3;
    private String valorParcela3;
    private String statusParcela3;
    private String valorJaPago;
    private String statusGeral;
    private String jurosBordero;
    private String taxaBordero;
    private String iofDiario;
    private String tarifaBordero;
    private String custoBancarioTotal;
    private String valorLiquidoCreditado;
}
