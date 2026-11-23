package somonitores.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Link público fixo do cliente (status das baterias de todos os nobreaks): <site>/c/CODIGO */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LinkClienteDTO {
    private Long codcli;
    private String codigo;
    private String url;
}
