package somonitores.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import somonitores.dto.DespesaDTO;
import somonitores.entity.DespesaEntity;
import somonitores.service.DespesaService;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

@Path("/despesa")
public class DespesaResource {

    @Inject
    DespesaService despesaService;

    @GET
    @Path("/iniciomes")
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Busca despesas de início de mês", description = "Retorna todas as despesas marcadas como SIM na coluna iniciomes")
    public Response buscaDespesasInicioMes() { // Removido o @QueryParam
        try {
            // Log fixo, já que agora a busca é padronizada para "SIM"
            System.out.println("Iniciando busca automática de despesas de INÍCIO DE MÊS ******************");

            // Chamada do service sem parâmetros, conforme a nova implementação
            List<DespesaDTO> despesas = despesaService.buscaDespesasInicioMes();

            if (despesas.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Nenhuma despesa de início de mês foi encontrada no banco de dados.")
                        .build();
            }

            return Response.ok(despesas).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro interno ao processar a busca de despesas: " + e.getMessage())
                    .build();
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Persiste uma nova despesa no banco", description = "Cria um novo registro de despesa")
    public Response criarNovaDespesa(DespesaDTO despesaDTO) {
        try {
            // Converte DTO para Entity
            DespesaEntity despesaEntity = mapToEntity(despesaDTO);

            // Salva a despesa usando o serviço
            DespesaEntity despesaPersistida = despesaService.criarDespesa(despesaEntity);

            return Response.status(Response.Status.CREATED)
                    .entity(despesaPersistida) // Retorna a entidade criada com status 201
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Erro ao criar nova despesa: " + e.getMessage())
                    .build();
        }
    }

    // Método auxiliar para converter DTO em Entity
    private DespesaEntity mapToEntity(DespesaDTO dto) {
        return DespesaEntity.builder()
                .id(dto.getId())
                .descricao(dto.getDescricao())
                .categoria(dto.getCategoria())
                .vencimento(dto.getVencimento()) // Lembrar de usar LocalDate
                .valor(dto.getValor())
                .status(dto.getStatus())
                .iniciomes(dto.getIniciomes())
                .build();
    }

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Remove uma despesa existente", description = "Remove uma despesa do banco de dados pelo ID")
    public Response deletarDespesa(@PathParam("id") Long id) {
        try {
            System.out.println("Solicitação de exclusão para o ID: " + id + " ******************");

            despesaService.deletarDespesa(id);

            // Retorna 204 No Content quando a exclusão é bem-sucedida
            return Response.noContent().build();

        } catch (Exception e) {
            // Verifica se a mensagem de erro indica que o registro não existe
            if (e.getMessage() != null && e.getMessage().contains("Despesa não encontrada")) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Despesa não encontrada com o código: " + id)
                        .build();
            }

            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Erro ao deletar despesa: " + e.getMessage())
                    .build();
        }
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Atualiza uma despesa existente", description = "Atualiza os dados de uma despesa no banco de dados")
    public Response atualizarDespesa(@PathParam("id") Long id, DespesaDTO despesaDTO) {
        try {
            // Transforma o DTO em Entity usando o método auxiliar que já temos
            DespesaEntity despesaEntity = mapToEntity(despesaDTO);

            // Atualiza a despesa usando o serviço
            DespesaEntity despesa = despesaService.atualizarDespesa(id, despesaEntity);

            return Response.ok(despesa) // Retorna a despesa atualizada com status 200
                    .build();
        } catch (Exception e) {
            // Verifica se a mensagem de erro contém informações sobre não encontrado
            if (e.getMessage() != null && e.getMessage().contains("Despesa não encontrada")) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Despesa não encontrada com o código: " + id)
                        .build();
            }
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Erro ao atualizar despesa: " + e.getMessage())
                    .build();
        }
    }
    @POST
    @Path("/lote")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Persiste várias despesas", description = "Salva uma lista de despesas de uma só vez")
    public Response criarDespesasEmLote(List<DespesaDTO> listaDTO) {
        try {
            // Converte a lista de DTOs para lista de Entities
            List<DespesaEntity> listaEntities = listaDTO.stream()
                    .map(this::mapToEntity)
                    .collect(Collectors.toList());

            // Chama o service para persistir todos
            despesaService.salvarListaDespesas(listaEntities);

            return Response.status(Response.Status.CREATED)
                    .entity("Sucesso: " + listaEntities.size() + " despesas registradas.")
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Erro ao salvar lote de despesas: " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/vencimento-intervalo")
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Busca despesas por vencimento", description = "Retorna despesas dentro de um intervalo de datas de vencimento")
    public Response buscaDespesasPorIntervalo(@QueryParam("inicio") String inicio,
                                              @QueryParam("fim") String fim) {
        try {
            // Converte as Strings para LocalDate (espera formato ISO: 2024-01-01)
            LocalDate dataInicio = LocalDate.parse(inicio);
            LocalDate dataFim = LocalDate.parse(fim);

            List<DespesaDTO> despesas = despesaService.buscaPorIntervaloVencimento(dataInicio, dataFim);

            if (despesas.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Nenhuma despesa encontrada para o período informado.")
                        .build();
            }

            return Response.ok(despesas).build();
        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Formato de data inválido. Use o formato ISO: YYYY-MM-DD")
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro ao processar busca: " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/vencimento-intervalo-categoria-status")
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Busca despesas por vencimento", description = "Retorna despesas dentro de um intervalo de datas de vencimento")
    public Response buscaDespesasPorIntervalo(
            @QueryParam("inicio") String inicio,
            @QueryParam("fim") String fim,
            @QueryParam("categoria") String categoria,
            @QueryParam("status") String status) {

        try {
            // Validação básica para evitar NullPointerException no parse
            if (inicio == null || fim == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Os parâmetros 'inicio' e 'fim' são obrigatórios.")
                        .build();
            }

            // Converte as Strings para LocalDate
            LocalDate dataInicio = LocalDate.parse(inicio);
            LocalDate dataFim = LocalDate.parse(fim);

            // Se categoria ou status vierem nulos da URL, tratamos como "TODOS"
            String cat = (categoria != null) ? categoria : "TODOS";
            String stat = (status != null) ? status : "TODOS";

            List<DespesaDTO> despesas = despesaService.buscaPorIntervaloVencimentoCategoriaStatus(dataInicio, dataFim, cat, stat);

            // Retornamos 200 OK mesmo se a lista estiver vazia (padrão REST para filtros)
            return Response.ok(despesas).build();

        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Formato de data inválido. Use YYYY-MM-DD")
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro interno: " + e.getMessage())
                    .build();
        }
    }



}
