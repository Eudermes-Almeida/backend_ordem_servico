package somonitores.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import somonitores.dto.EquipamentoPublicoDTO;
import somonitores.dto.FrotaClienteDTO;
import somonitores.dto.PedidoFrotaDTO;
import somonitores.service.EtiquetaService;

/**
 * Página pública aberta pelo QR Code da etiqueta (sem login).
 *  - GET /publico/equipamento/{codigo}        última manutenção + histórico do equipamento, SEM valores
 *  - GET /publico/equipamento/{codigo}/frota  nobreaks do cliente com a situação do banco de baterias
 *  - POST /publico/cliente/frota              idem, para o login externo (confere código + CPF/CNPJ)
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

    /** Nobreaks do cliente da etiqueta com a situação do banco de baterias (ativo / atenção / vencido). */
    @GET
    @Path("/equipamento/{codigo}/frota")
    @Produces(MediaType.APPLICATION_JSON)
    public Response consultarFrota(@PathParam("codigo") String codigo) {
        try {
            FrotaClienteDTO dto = etiquetaService.consultarFrota(codigo);
            if (dto == null) {
                return EtiquetaResource.erro(Response.Status.NOT_FOUND, "Etiqueta não encontrada.");
            }
            return Response.ok(dto).build();
        } catch (Exception e) {
            e.printStackTrace();
            return EtiquetaResource.erro(Response.Status.INTERNAL_SERVER_ERROR, "Não foi possível consultar os nobreaks agora.");
        }
    }

    /**
     * Nobreaks do cliente pelo link fixo da mensagem de WhatsApp (página /c/{codigo}).
     * GET /publico/cliente/{codigo}/frota
     */
    @GET
    @Path("/cliente/{codigo}/frota")
    @Produces(MediaType.APPLICATION_JSON)
    public Response consultarFrotaPorLinkCliente(@PathParam("codigo") String codigo) {
        try {
            FrotaClienteDTO dto = etiquetaService.consultarFrotaPorLinkCliente(codigo);
            if (dto == null) {
                return EtiquetaResource.erro(Response.Status.NOT_FOUND, "Link não encontrado.");
            }
            return Response.ok(dto).build();
        } catch (Exception e) {
            e.printStackTrace();
            return EtiquetaResource.erro(Response.Status.INTERNAL_SERVER_ERROR, "Não foi possível consultar os nobreaks agora.");
        }
    }

    /**
     * Nobreaks do cliente para a lista do LOGIN EXTERNO.
     * Corpo: {"codcli": 1142, "documento": "CPF ou CNPJ digitado no login"}.
     * 403 quando o documento não confere com o cadastro (mesma resposta para cliente inexistente).
     */
    @POST
    @Path("/cliente/frota")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response consultarFrotaCliente(PedidoFrotaDTO pedido) {
        try {
            FrotaClienteDTO dto = pedido == null ? null
                    : etiquetaService.consultarFrotaCliente(pedido.getCodcli(), pedido.getDocumento());
            if (dto == null) {
                return EtiquetaResource.erro(Response.Status.FORBIDDEN, "Código do cliente ou CPF/CNPJ não conferem.");
            }
            return Response.ok(dto).build();
        } catch (Exception e) {
            e.printStackTrace();
            return EtiquetaResource.erro(Response.Status.INTERNAL_SERVER_ERROR, "Não foi possível consultar os nobreaks agora.");
        }
    }
}
