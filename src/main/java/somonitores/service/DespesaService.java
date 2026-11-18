package somonitores.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import somonitores.dto.DespesaDTO;
import somonitores.entity.DespesaEntity;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@ApplicationScoped
public class DespesaService {

    @Inject
    EntityManager entityManager;

    @Transactional
    public List<DespesaDTO> buscaDespesasInicioMes() {
        // SQL Nativo buscando fixamente pelo valor 'SIM'
        // Usei UPPER para garantir que encontre mesmo se estiver 'sim' ou 'Sim' no banco
        String sql = "SELECT * FROM DESPESAS WHERE UPPER(iniciomes) = 'SIM' ORDER BY vencimento ASC";


        System.out.println("Buscando todas as despesas de INÍCIO DE MÊS (SIM) ******************");

        // Executando a consulta e mapeando para a Entity
        @SuppressWarnings("unchecked")
        List<DespesaEntity> despesaEntities = entityManager.createNativeQuery(sql, DespesaEntity.class)
                .getResultList();

        // Mapeando as entidades para DTO usando o Stream
        return despesaEntities.stream()
                .filter(Objects::nonNull)
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    private DespesaDTO mapToDTO(DespesaEntity entity) {
        return DespesaDTO.builder()
                .id(entity.getId())
                .descricao(entity.getDescricao())
                .categoria(entity.getCategoria())
                .vencimento(entity.getVencimento()) // Certifique-se que é LocalDate
                .valor(entity.getValor())
                .status(entity.getStatus())
                .iniciomes(entity.getIniciomes())
                .build();
    }

    @Transactional
    public DespesaEntity criarDespesa(DespesaEntity despesa) {
        if (despesa == null) {
            throw new IllegalArgumentException("A despesa não pode ser nula");
        }

        // O EntityManager cuida do ID automático (Identity) definido no SQL Server
        entityManager.persist(despesa);

        return despesa; // Retorna a despesa com o ID gerado pelo banco
    }

    @Transactional
    public void deletarDespesa(Long id) throws Exception {
        if (id == null) {
            throw new IllegalArgumentException("O ID da despesa não pode ser nulo");
        }

        // Busca a despesa existente no banco pelo ID
        DespesaEntity despesaExistente = entityManager.find(DespesaEntity.class, id);

        if (despesaExistente == null) {
            throw new Exception("Despesa não encontrada com o código: " + id);
        }

        // Remove o objeto do contexto do Hibernate e do banco de dados
        entityManager.remove(despesaExistente);

        System.out.println("Despesa ID " + id + " removida com sucesso. ******************");
    }

    @Transactional
    public DespesaEntity atualizarDespesa(Long id, DespesaEntity despesaAtualizada) throws Exception {
        if (id == null || despesaAtualizada == null) {
            throw new IllegalArgumentException("O ID e os dados da despesa não podem ser nulos");
        }

        // Busca a despesa existente no banco de dados
        DespesaEntity despesaExistente = entityManager.find(DespesaEntity.class, id);

        if (despesaExistente == null) {
            throw new Exception("Despesa não encontrada com o código: " + id);
        }

        // Atualiza os campos da entidade gerenciada pelo Hibernate
        despesaExistente.setDescricao(despesaAtualizada.getDescricao());
        despesaExistente.setCategoria(despesaAtualizada.getCategoria());
        despesaExistente.setVencimento(despesaAtualizada.getVencimento());
        despesaExistente.setValor(despesaAtualizada.getValor());
        despesaExistente.setStatus(despesaAtualizada.getStatus());
        despesaExistente.setIniciomes(despesaAtualizada.getIniciomes());

        // O EntityManager sincroniza automaticamente as alterações no commit da transação
        return despesaExistente;
    }

    @Transactional
    public void salvarListaDespesas(List<DespesaEntity> despesas) {
        if (despesas == null || despesas.isEmpty()) {
            throw new IllegalArgumentException("A lista de despesas não pode estar vazia");
        }

        for (DespesaEntity despesa : despesas) {
            // Garantimos que o ID seja nulo para que o SQL Server gere novos IDs (novos registros)
            despesa.setId(null);

            entityManager.persist(despesa);
        }

        // O Hibernate enviará todos os comandos ao banco ao final deste método
        System.out.println("Lote de " + despesas.size() + " despesas persistido com sucesso! ******************");
    }

    @Transactional
    public List<DespesaDTO> buscaPorIntervaloVencimento(LocalDate dataInicio, LocalDate dataFim) {
        // SQL Nativo usando parâmetros posicionais (?1 e ?2)
        // O ORDER BY garante que a lista venha organizada por data
        String sql = "SELECT * FROM DESPESAS WHERE vencimento BETWEEN ?1 AND ?2 AND iniciomes = 'NAO' ORDER BY vencimento ASC";


        System.out.println("Iniciando busca de despesas por período: " + dataInicio + " até " + dataFim + " **********");

        try {
            @SuppressWarnings("unchecked")
            List<DespesaEntity> despesaEntities = entityManager.createNativeQuery(sql, DespesaEntity.class)
                    .setParameter(1, dataInicio)
                    .setParameter(2, dataFim)
                    .getResultList();

            // Mapeando as entidades para DTO usando o seu método mapToDTO existente
            return despesaEntities.stream()
                    .filter(Objects::nonNull)
                    .map(this::mapToDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Erro ao executar consulta de intervalo: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    @Transactional
    public List<DespesaDTO> buscaPorIntervaloVencimentoCategoriaStatus(LocalDate dataInicio, LocalDate dataFim, String categoria, String status) {

        // 1. Base da Query
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM DESPESAS WHERE vencimento BETWEEN ?1 AND ?2 AND iniciomes = 'NAO'"
        );

        // 2. Filtros condicionais (Categorias Especiais e Comuns)
        if (categoria != null && !"todos".equalsIgnoreCase(categoria)) {
            if ("CASA e IGREJA".equalsIgnoreCase(categoria)) {
                sql.append(" and categoria in ('CASA', 'IGREJA')");
            } else if ("EMPRESA".equalsIgnoreCase(categoria)) {
                // Nova condição para EMPRESA
                sql.append(" and categoria in ('ADMINISTRATIVA', 'EMPRESTIMO', 'ALUGUEL', 'BANCARIA', 'BATERIA', 'IMPOSTO', 'INSUMOS', 'SALARIO', 'VEICULO')");
            } else {
                sql.append(" and categoria = :categoria");
            }
        }

        // Adicione o filtro de status aqui caso queira filtrar por ele no SQL também
        if (status != null && !"todos".equalsIgnoreCase(status)) {
            sql.append(" and status = :status");
        }

        // 3. Ordenação final
        sql.append(" ORDER BY vencimento ASC");

        try {
            var query = entityManager.createNativeQuery(sql.toString(), DespesaEntity.class)
                    .setParameter(1, dataInicio)
                    .setParameter(2, dataFim);

            // 5. Setar parâmetros (Pular se for um dos combos fixos)
            boolean isCombo = "CASA e IGREJA".equalsIgnoreCase(categoria) || "EMPRESA".equalsIgnoreCase(categoria);

            if (categoria != null && !"TODOS".equalsIgnoreCase(categoria) && !isCombo) {
                query.setParameter("categoria", categoria);
            }

            if (status != null && !"TODOS".equalsIgnoreCase(status)) {
                query.setParameter("status", status);
            }

            @SuppressWarnings("unchecked")
            List<DespesaEntity> despesaEntities = query.getResultList();

            return despesaEntities.stream()
                    .filter(Objects::nonNull)
                    .map(this::mapToDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Erro na consulta: " + e.getMessage());
            return Collections.emptyList();
        }
    }





}
