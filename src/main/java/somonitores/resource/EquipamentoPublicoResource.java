package somonitores.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import somonitores.dto.EquipamentoPublicoDTO;
import somonitores.service.EtiquetaService;

/**
 * Página pública aberta pelo QR Code da etiqueta (sem login).
 *  - GET /publico/equipamento/{codigo}  última manutenção + histórico do equipamento, SEM valores
 */
@Path("/publico")
@Tag(name = "Etiqueta", description = "Etiqueta com QR Code e histórico público do equipamento")
public class EquipamentoPublicoResource {

    @Inject
    EtiquetaService etiquetaService;

    @GET
    @Path("/equipamento/{codigo}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response consultarEquipamento(@PathParam("codigo") String codigo) {
        try {
            EquipamentoPublicoDTO dto = etiquetaService.consultarPorCodigo(codigo);
            if (dto == null) {
                return EtiquetaResource.erro(Response.Status.NOT_FOUND, "Etiqueta não encontrada.");
            }
            return Response.ok(dto).build();
        } catch (Exception e) {
            e.printStackTrace();
            // página pública: não expõe detalhes internos
            return EtiquetaResource.erro(Response.Status.INTERNAL_SERVER_ERROR, "Não foi possível consultar o equipamento agora.");
        }
    }
}
