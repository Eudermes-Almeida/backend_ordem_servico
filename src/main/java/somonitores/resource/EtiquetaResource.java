package somonitores.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import somonitores.service.EtiquetaService;

/**
 * Etiqueta do equipamento (QR Code).
 *  - POST /etiqueta/os/{os}           (sistema interno) cria/reaproveita o código e devolve os dados da etiqueta
 *  - GET  /publico/equipamento/{cod}  ver EquipamentoPublicoResource
 */
@Path("/etiqueta")
@Tag(name = "Etiqueta", description = "Etiqueta com QR Code e histórico público do equipamento")
public class EtiquetaResource {

    @Inject
    EtiquetaService etiquetaService;

    @POST
    @Path("/os/{os}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response gerarEtiqueta(@PathParam("os") Long os) {
        try {
            return Response.ok(etiquetaService.gerarEtiqueta(os)).build();
        } catch (IllegalArgumentException e) {
            return erro(Response.Status.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            return erro(Response.Status.CONFLICT, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return erro(Response.Status.INTERNAL_SERVER_ERROR, "Erro ao gerar a etiqueta: " + e.getMessage());
        }
    }

    static Response erro(Response.Status status, String mensagem) {
        return Response.status(status)
                .type(MediaType.TEXT_PLAIN + ";charset=UTF-8")
                .entity(mensagem)
                .build();
    }
}
