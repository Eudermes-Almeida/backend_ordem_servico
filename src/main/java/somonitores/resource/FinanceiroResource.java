package somonitores.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import somonitores.dto.FinanceiroDTO;
import somonitores.entity.FinanceiroEntity;
import somonitores.service.FinanceiroService;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;



@Path("/financeiro")
public class FinanceiroResource {

    @Inject
    FinanceiroService financeiroService;

    @GET
    @Path("/{os}")
    @Tag(name = "Busca financeiro por OS", description = "Busca registro financeiro por ID da Ordem de Serviço")
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Sucesso", content = @Content(schema = @Schema(implementation = FinanceiroDTO.class))),
            @APIResponse(responseCode = "404", description = "Registro não encontrado")
    })

    public Response buscarPorId(@PathParam("os") Long os) {
        try {
            // Chama o service que retorna o DTO ou null
            FinanceiroDTO financeiroDTO = financeiroService.buscarFinanceiroPeloId(os);

            if (financeiroDTO == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Registro não encontrado para a OS: " + os)
                        .build();
            }

            return Response.ok(financeiroDTO).build();

        } catch (Exception e) {
            // Trata a Exception que o Service lança
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro ao buscar registro: " + e.getMessage())
                    .build();
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Persiste um novo registro financeiro", description = "Cria um novo registro na tabela FINANCEIRO")
    public Response criarNovoFinanceiro(FinanceiroDTO dto) {
        try {
            FinanceiroEntity entity = mapToEntity(dto);
            FinanceiroEntity persistido = financeiroService.criarFinanceiro(entity);
            return Response.status(Response.Status.CREATED).entity(persistido).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Erro ao criar registro financeiro: " + e.getMessage())
                    .build();
        }
    }


    @GET
    @Path("/codcli")
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Busca Ordens de Servico pelo codigo do cliente", description = "Busca Ordens de Servico pelo codigo do cliente")
    public Response buscarOsPorCodcli(@QueryParam("codcli") Long codcli) {
        try {
            // CORRIGIDO: Tipo alterado de List<OsDTO> para List<FinanceiroDTO>
            List<FinanceiroDTO> ordens = financeiroService.buscarOsPeloCodcli(codcli);

            if (ordens.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Nenhuma ordem de servico para o cliente com o codcli " + codcli)
                        .build();
            }

            return Response.ok(ordens).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro ao buscar ordens de servico: " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/ultima-os")
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Busca o id da ultima OS cadastrada no financeiro", description = "Busca o id da ultima OS cadastrada na tabela FINANCEIRO por ordem de criação")
    public Response buscarUltimaOs() {
        try {
            Long ultimaOs = financeiroService.buscarUltimaOsCadastrada();

            if (ultimaOs == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Nenhum registro financeiro cadastrado.")
                        .build();
            }

            return Response.ok(ultimaOs).build(); // Retorna puramente o número do ID (Long)
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro ao buscar ultima OS cadastrada no financeiro: " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/ultimasos")
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Busca Ultimas ordens de serviço - o parametro limite -define quantas serão trazidas", description = "Busca Ordens de Serviço com um limite definido")
    public Response buscaUltimasOs(@QueryParam("limite") int limite) {
        try {
            List<FinanceiroDTO> ordens = financeiroService.buscarUltimasOs(limite);

            if (ordens.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("O limite de ultimas OS deve ser maior que zero e menor que 1.000 " + limite)
                        .build();
            }

            return Response.ok(ordens).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro ao buscar ordens de serviço: " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/filtro-avancado")
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Busca registros financeiros filtrados", description = "Busca registros por período dinâmico de datas e status financeiro")
    public Response buscarPorPeriodoEStatus(
            @QueryParam("dataInicio") String dataInicioStr,
            @QueryParam("dataFim") String dataFimStr,
            @QueryParam("status") String status,
            @QueryParam("tipoData") String tipoData) { // Quarto parâmetro adicionado
        try {
            LocalDate dataInicio = LocalDate.parse(dataInicioStr);
            LocalDate dataFim = LocalDate.parse(dataFimStr);

            List<FinanceiroDTO> registros = financeiroService.buscarPorPeriodoEStatus(dataInicio, dataFim, status, tipoData);

            if (registros.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Nenhum registro financeiro encontrado para os filtros aplicados.")
                        .build();
            }

            return Response.ok(registros).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro ao realizar busca avançada: " + e.getMessage())
                    .build();
        }
    }

    @PUT
    @Path("/atualizar-lote")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Atualiza registros financeiros em lote", description = "Atualiza uma lista de ordens de serviço financeiras de uma única vez")
    public Response atualizarFinanceiroEmLote(List<FinanceiroDTO> dtos) {
        try {
            if (dtos == null || dtos.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("A lista de ordens para atualização não pode estar vazia.")
                        .build();
            }

            // Converte a lista de DTOs recebida do Angular em entidades Java
            List<FinanceiroEntity> entidades = dtos.stream()
                    .map(FinanceiroResource::mapToEntity)
                    .collect(Collectors.toList());

            // Chama o service para processar a atualização no banco de dados
            List<FinanceiroEntity> atualizadas = financeiroService.atualizarEmLote(entidades);

            return Response.ok(atualizadas).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro ao atualizar registros em lote: " + e.getMessage())
                    .build();
        }
    }


    @PUT
    @Path("/verificar-atrasos")
    @Tag(name = "Verifica parcelas vencidas", description = "Varre o banco de dados e altera o status para ATRAZADO caso a data de vencimento tenha expirado")
    public Response verificarEAtualizarAtrasos() {
        try {
            List<FinanceiroEntity> atualizados = financeiroService.verificarEAtualizarAtrasos();

            if (atualizados.isEmpty()) {
                return Response.ok("Nenhum registro financeiro precisou de atualização hoje.").build();
            }

            return Response.ok(atualizados).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro ao processar rotina de verificação de atrasos: " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/pendentes-bordero")
    @Tag(name = "Busca financeiros pendentes de borderô", description = "Lista registros onde codRemessa e dataFaturamento não são nulos, codBordero é nulo e tipoPagamento é BORDERO")
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Sucesso",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(type = SchemaType.ARRAY, implementation = FinanceiroDTO.class))),
            @APIResponse(responseCode = "204", description = "Nenhum registro encontrado"),
            @APIResponse(responseCode = "500", description = "Erro interno do servidor")
    })

    public Response buscarPendentesBordero() {
        try {
            List<FinanceiroDTO> listaDTO = financeiroService.buscarPendentesBordero();

            if (listaDTO == null || listaDTO.isEmpty()) {
                return Response.status(Response.Status.NO_CONTENT).build();
            }

            return Response.ok(listaDTO).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro ao listar registros: " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/bordero")
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Busca a lista de borderô de um dia específico", description = "Busca a lista de borderô de um dia específico")
    public Response buscarBorderoEspecifico(@QueryParam("data") String dataBordero) { // 1. RECEBE A DATA DA URL
        try {
            // 2. VALIDAÇÃO: Garante que a requisição não venha sem data
            if (dataBordero == null || dataBordero.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("O parâmetro 'data' é obrigatório. Exemplo: /bordero?data=2026-05-22")
                        .build();
            }

            // 3. PASSA O PARÂMETRO PARA O SERVICE (Resolve o erro de compilação)
            List<FinanceiroDTO> listaBorderoEspecifico = financeiroService.buscarBorderoPorData(dataBordero);

            // 4. Verifica se a lista é nula OU se está vazia
            if (listaBorderoEspecifico == null || listaBorderoEspecifico.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Nenhum registro financeiro cadastrado para esta data.")
                        .build();
            }

            // 5. Retorna a lista completa convertida automaticamente para JSON
            return Response.ok(listaBorderoEspecifico).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro ao buscar bordero para esta data: " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/remessa")
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Busca a lista de OS d uma remessa", description = "Busca a lista de OS d uma remessa")
    public Response buscaListaDEOsDeUmaRemessa(@QueryParam("remessa") String dataRemessa) { // 1. RECEBE A DATA DA URL
        try {
            // 2. VALIDAÇÃO: Garante que a requisição não venha sem data
            if (dataRemessa == null || dataRemessa.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("O parâmetro 'data' é obrigatório. Exemplo: /bordero?data=2026-05-22")
                        .build();
            }

            // 3. PASSA O PARÂMETRO PARA O SERVICE (Resolve o erro de compilação)
            List<FinanceiroDTO> listaRemessa = financeiroService.buscarListaOsdeUmaRemessa(dataRemessa);

            // 4. Verifica se a lista é nula OU se está vazia
            if (listaRemessa == null || listaRemessa.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Nenhuma OS encontrada para esta remessa")
                        .build();
            }

            // 5. Retorna a lista completa convertida automaticamente para JSON
            return Response.ok(listaRemessa).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro ao buscar OS para esta remessa para esta data: " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/tudomenospago")
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Traz tudo, com excecao dos pagos", description = "Traz tudo, com excecao dos pagos")
    public Response buscaListaTotalMenosPagos() {
        try {

            List<FinanceiroDTO> listaTudoMenosPago = financeiroService.buscarListaTudoMenosPago();

            // 4. Verifica se a lista é nula OU se está vazia
            if (listaTudoMenosPago == null || listaTudoMenosPago.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Nenhuma OS encontrada para esta remessa")
                        .build();
            }

            // 5. Retorna a lista completa convertida automaticamente para JSON
            return Response.ok(listaTudoMenosPago).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro ao buscar OS para esta remessa para esta data: " + e.getMessage())
                    .build();
        }
    }


    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Remove uma ordem de serviço do financeiro", description = "Remove uma ordem de serviço do financeiro")
    public Response deletarOsEmFinanceiro(@PathParam("id") Long id) {
        try {
            System.out.println("Solicitação de exclusão para o ID: " + id + " ******************");

            financeiroService.deletarOsEmFinanceiro(id);

            // Retorna 204 No Content quando a exclusão é bem-sucedida
            return Response.noContent().build();

        } catch (Exception e) {
            // Verifica se a mensagem de erro indica que o registro não existe
            if (e.getMessage() != null && e.getMessage().contains("Ordem de serviço não encontrada")) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Ordem de serviço não encontrada com o código: " + id)
                        .build();
            }

            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Erro ao deletar ordem de serviço: " + e.getMessage())
                    .build();
        }
    }






    public static FinanceiroEntity mapToEntity(FinanceiroDTO dto) {
        if (dto == null) return null;

        FinanceiroEntity entity = new FinanceiroEntity();

        entity.setOs(dto.getOs());
        entity.setDataEntrada(dto.getDataEntrada());
        entity.setFantasia(dto.getFantasia() != null ? dto.getFantasia().toUpperCase() : null);
        entity.setCodcli(dto.getCodcli());
        entity.setEquipamento(dto.getEquipamento() != null ? dto.getEquipamento().toUpperCase() : null);
        entity.setMarca(dto.getMarca() != null ? dto.getMarca().toUpperCase() : null);
        entity.setModelo(dto.getModelo() != null ? dto.getModelo().toUpperCase() : null);
        entity.setTotaliq(dto.getTotaliq());
        entity.setFechamento(dto.getFechamento());
        entity.setDataFaturamento(dto.getDataFaturamento());
        entity.setCodRemessa(dto.getCodRemessa());
        entity.setCodBordero(dto.getCodBordero());
        entity.setTipoPagamento(dto.getTipoPagamento() != null ? dto.getTipoPagamento().toUpperCase() : null);
        entity.setQtParcela(dto.getQtParcela());
        entity.setPrazo(dto.getPrazo());
        entity.setVencimento1(dto.getVencimento1());
        entity.setValorParcela1(dto.getValorParcela1());
        entity.setStatusParcela1(dto.getStatusParcela1() != null ? dto.getStatusParcela1().toUpperCase() : null);
        entity.setVencimento2(dto.getVencimento2());
        entity.setValorParcela2(dto.getValorParcela2());
        entity.setStatusParcela2(dto.getStatusParcela2() != null ? dto.getStatusParcela2().toUpperCase() : null);
        entity.setVencimento3(dto.getVencimento3());
        entity.setValorParcela3(dto.getValorParcela3());
        entity.setStatusParcela3(dto.getStatusParcela3() != null ? dto.getStatusParcela3().toUpperCase() : null);
        entity.setValorJaPago(dto.getValorJaPago());
        entity.setStatusGeral(dto.getStatusGeral() != null ? dto.getStatusGeral().toUpperCase() : null);
        entity.setJurosBordero(dto.getJurosBordero());
        entity.setTaxaBordero(dto.getTaxaBordero());
        entity.setIofDiario(dto.getIofDiario());
        entity.setTarifaBordero(dto.getTarifaBordero());
        entity.setCustoBancarioTotal(dto.getCustoBancarioTotal());
        entity.setValorLiquidoCreditado(dto.getValorLiquidoCreditado());

        return entity;
    }
}
