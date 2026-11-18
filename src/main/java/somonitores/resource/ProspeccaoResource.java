package somonitores.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import somonitores.dto.ContatoProspeccaoDTO;
import somonitores.dto.HistoricoEquipamentoDTO;
import somonitores.dto.NobreakBateriaVencidaDTO;
import somonitores.service.ProspeccaoService;

import java.time.LocalDate;
import java.util.List;

@Path("/prospeccao")
public class ProspeccaoResource {

    @Inject
    ProspeccaoService prospeccaoService;

    /**
     * Ex.: GET /prospeccao/nobreaksBateriaVencida?meses=24&potenciaMinima=5000
     *      GET /prospeccao/nobreaksBateriaVencida?codcli=219&meses=12
     * codcli, potenciaMinima e potenciaMaxima são opcionais. Retorna lista vazia quando não há resultado.
     */
    @GET
    @Path("/nobreaksBateriaVencida")
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Prospecção", description = "Nobreaks com banco de baterias vencido")
    public Response buscarNobreaksBateriaVencida(@QueryParam("codcli") Long codcli,
                                                 @QueryParam("meses") Integer meses,
                                                 @QueryParam("potenciaMinima") Integer potenciaMinima,
                                                 @QueryParam("potenciaMaxima") Integer potenciaMaxima) {
        int mesesEfetivo = (meses == null) ? 24 : meses;
        if (mesesEfetivo < 1 || mesesEfetivo >= ProspeccaoService.ANOS_LIMITE * 12) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Prazo inválido: informe entre 1 e " + (ProspeccaoService.ANOS_LIMITE * 12 - 1) + " meses.")
                    .build();
        }
        if (potenciaMinima != null && potenciaMaxima != null && potenciaMinima > potenciaMaxima) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("A potência mínima não pode ser maior que a potência máxima.")
                    .build();
        }
        try {
            List<NobreakBateriaVencidaDTO> lista =
                    prospeccaoService.buscarNobreaksBateriaVencida(codcli, mesesEfetivo, potenciaMinima, potenciaMaxima);
            return Response.ok(lista).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro ao buscar nobreaks com bateria vencida: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Registra um contato do telemarketing com o cliente e os equipamentos tratados.
     * POST /prospeccao/contato
     * { codcli, operador, canal: TELEFONE|WHATSAPP, pessoaContatada, observacao, dataRetorno: yyyy-MM-dd,
     *   equipamentos: [ { chaveEquip, osReferencia, marca, modelo, serial, resultado } ] }
     */
    @POST
    @Path("/contato")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Prospecção", description = "Registra contato do telemarketing")
    public Response registrarContato(ContatoProspeccaoDTO dto) {
        try {
            return Response.status(Response.Status.CREATED)
                    .entity(prospeccaoService.registrarContato(dto))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro ao gravar o contato: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Contatos já feitos com o cliente (mais recentes primeiro).
     * GET /prospeccao/contatos/219
     */
    @GET
    @Path("/contatos/{codcli}")
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Prospecção", description = "Contatos do telemarketing com o cliente")
    public Response listarContatos(@PathParam("codcli") Long codcli) {
        try {
            List<ContatoProspeccaoDTO> lista = prospeccaoService.listarContatos(codcli);
            return Response.ok(lista).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro ao buscar os contatos: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Contatos feitos no período, para acompanhar o telemarketing.
     * GET /prospeccao/contatosPeriodo?inicio=2026-11-15&fim=2026-11-18&operador=FULANO (operador opcional)
     */
    @GET
    @Path("/contatosPeriodo")
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Prospecção", description = "Contatos do telemarketing por período")
    public Response listarContatosPeriodo(@QueryParam("inicio") String inicio,
                                          @QueryParam("fim") String fim,
                                          @QueryParam("operador") String operador) {
        LocalDate dataInicio;
        LocalDate dataFim;
        try {
            dataInicio = LocalDate.parse(inicio);
            dataFim = LocalDate.parse(fim);
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Informe as datas no formato yyyy-MM-dd (inicio e fim).")
                    .build();
        }
        if (dataFim.isBefore(dataInicio)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("A data final não pode ser anterior à data inicial.")
                    .build();
        }
        try {
            return Response.ok(prospeccaoService.listarContatosPeriodo(dataInicio, dataFim, operador)).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro ao buscar os contatos do período: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Ex.: GET /prospeccao/historicoEquipamento/53326
     * Todas as passagens do equipamento ao qual a OS pertence.
     */
    @GET
    @Path("/historicoEquipamento/{os}")
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Prospecção", description = "Histórico de passagens de um equipamento")
    public Response buscarHistoricoEquipamento(@PathParam("os") Long os) {
        try {
            List<HistoricoEquipamentoDTO> lista = prospeccaoService.buscarHistoricoEquipamento(os);
            return Response.ok(lista).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro ao buscar histórico do equipamento: " + e.getMessage())
                    .build();
        }
    }
}
