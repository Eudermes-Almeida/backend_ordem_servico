package somonitores.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
@Entity // 1. OBRIGATÓRIO do JPA
@Table(name = "FINANCEIRO") // 2. OBRIGATÓRIO: Aponta para a tabela exata do SQL Server
@Data // 3. OBRIGATÓRIO do Lombok: Cria os Getters e Setters implicitamente
@EqualsAndHashCode(callSuper = false)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FinanceiroEntity {
    @Id
    private Long os;
    private LocalDate dataEntrada;
    private String fantasia;
    private Long  codcli;
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
    @Column(name = "DataCriacao", insertable = false, updatable = false)
    private LocalDateTime dataCriacao;
}


