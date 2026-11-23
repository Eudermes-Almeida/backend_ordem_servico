package somonitores.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pedido da frota do cliente pelo login externo: código do cliente + CPF/CNPJ digitado no login.
 * Vai no corpo (POST) para o documento não aparecer na URL nem em logs de acesso.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PedidoFrotaDTO {
    private Long codcli;
    private String documento;
}
