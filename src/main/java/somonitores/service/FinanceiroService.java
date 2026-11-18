package somonitores.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import somonitores.dto.FinanceiroDTO;
import somonitores.dto.OsDTO;
import somonitores.entity.DespesaEntity;
import somonitores.entity.FinanceiroEntity;
import somonitores.entity.OsEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@ApplicationScoped
public class FinanceiroService {

    @Inject
    EntityManager entityManager;

    @Transactional
    public FinanceiroDTO buscarFinanceiroPeloId(Long os) throws Exception {
        try {
            FinanceiroEntity financeiroEntity = entityManager.find(FinanceiroEntity.class, os);

            if (financeiroEntity == null) {
                return null;
            }

            return mapToDTO(financeiroEntity);
        } catch (NoResultException e) {
            throw new Exception("Registro Financeiro não encontrado");
        }
    }

    @Transactional
    public FinanceiroEntity criarFinanceiro(FinanceiroEntity financeiro) {
        if (financeiro == null) {
            throw new IllegalArgumentException("Financeiro não pode ser nulo");
        }
        entityManager.persist(financeiro);
        return financeiro; // Retorna a entidade persistida
    }

    @Transactional
    public List<FinanceiroDTO> buscarOsPeloCodcli(Long codcli) {
        if (codcli == null) {
            return Collections.emptyList();
        }

        // CORRIGIDO: Nome da classe da entidade corrigido para "FinanceiroEntity" (case-sensitive)
        String query = "SELECT obj FROM FinanceiroEntity obj WHERE obj.codcli = :codcli ORDER BY obj.os DESC";

        // CORRIGIDO: Tipagem da query alterada de OsEntity.class para FinanceiroEntity.class
        List<FinanceiroEntity> financeiroEntities = entityManager.createQuery(query, FinanceiroEntity.class)
                .setParameter("codcli", codcli)
                .getResultList();

        // CORRIGIDO: O .stream() deve ser chamado a partir da coleção "financeiroEntities" gerada pelo banco
        return financeiroEntities.stream()
                .filter(Objects::nonNull) // Filtra entidades nulas
                .map(FinanceiroService::mapToDTO) // Mapeia chamando o método estático da classe
                .collect(Collectors.toList());
    }

    @Transactional
    public Long buscarUltimaOsCadastrada() {
        String jpql = "SELECT obj.os FROM FinanceiroEntity obj ORDER BY obj.dataCriacao DESC, obj.os DESC";

        try {
            List<Long> resultados = entityManager.createQuery(jpql, Long.class)
                    .setMaxResults(1)
                    .getResultList();

            return resultados.isEmpty() ? null : resultados.get(0);

        } catch (Exception e) {
            e.printStackTrace(); // Printa o erro no console do Quarkus se o mapeamento falhar
            return null;
        }
    }


    @Transactional
    public List<FinanceiroDTO> buscarUltimasOs(int limite) {

        if (limite <= 0 || limite >= 1000) {
            return Collections.emptyList();
        }

        try {
            // CORRIGIDO: Alterado a ordenação de obj.os para obj.dataCriacao de forma decrescente
            String query = "SELECT obj FROM FinanceiroEntity obj ORDER BY obj.dataCriacao DESC";

            List<FinanceiroEntity> financeiroEntities = entityManager.createQuery(query, FinanceiroEntity.class)
                    .setMaxResults(limite) // Aplica o limite dinâmico de linhas (semelhante ao TOP do SQL Server)
                    .getResultList();


            return financeiroEntities.stream()
                    .filter(Objects::nonNull) // Filtra entidades nulas
                    .map(FinanceiroService::mapToDTO) // Mantém a referência estática correta
                    .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

//    @Transactional
//    public List<FinanceiroDTO> buscarPorPeriodoEStatus(LocalDate dataInicio, LocalDate dataFim, String status, String tipoData) {
//        if (dataInicio == null || dataFim == null || status == null || tipoData == null) {
//            return Collections.emptyList();
//        }
//
//        // 1. Determina qual coluna mapeada no Java será injetada com segurança na query
//        String colunaData;
//        switch (tipoData.toLowerCase()) {
//            case "fechamento":
//                colunaData = "obj.fechamento";
//                break;
//            case "faturamento":
//                colunaData = "obj.dataFaturamento";
//                break;
//            case "entrada":
//            default:
//                colunaData = "obj.dataEntrada";
//                break;
//        }
//
//        // 2. Monta a JPQL concatenando apenas a coluna validada internamente pelo Java (Seguro)
//        String jpql = "SELECT obj FROM FinanceiroEntity obj " +
//                "WHERE " + colunaData + " BETWEEN :dataInicio AND :dataFim " +
//                "AND (:status = 'TODOS' OR obj.statusGeral = :status) " +
//                "ORDER BY " + colunaData + " DESC";
//
//        try {
//            List<FinanceiroEntity> entidades = entityManager.createQuery(jpql, FinanceiroEntity.class)
//                    .setParameter("dataInicio", dataInicio)
//                    .setParameter("dataFim", dataFim)
//                    .setParameter("status", status.toUpperCase())
//                    .getResultList();
//
//            return entidades.stream()
//                    .filter(Objects::nonNull)
//                    .map(FinanceiroService::mapToDTO)
//                    .collect(Collectors.toList());
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return Collections.emptyList();
//        }
//    }


    @Transactional
    public List<FinanceiroDTO> buscarPorPeriodoEStatus(LocalDate dataInicio, LocalDate dataFim, String status, String tipoData) {
        if (dataInicio == null || dataFim == null || status == null || tipoData == null) {
            return Collections.emptyList();
        }

        String colunaData;
        switch (tipoData.toLowerCase()) {
            case "fechamento":
                colunaData = "obj.fechamento";
                break;
            case "faturamento":
                colunaData = "obj.dataFaturamento";
                break;
            case "entrada":
            default:
                colunaData = "obj.dataEntrada";
                break;
        }

        // ALTERAÇÃO: Adicionada ordenação condicional com CASE WHEN e desempate por ID (os)
        String jpql = "SELECT obj FROM FinanceiroEntity obj " +
                "WHERE " + colunaData + " BETWEEN :dataInicio AND :dataFim " +
                "AND (:status = 'TODOS' OR obj.statusGeral = :status) " +
                "ORDER BY " +
                "  CASE WHEN obj.statusGeral = 'NAO FATURADO' THEN 0 ELSE 1 END ASC, " +
                "  obj.dataCriacao DESC, " +
                "  obj.os DESC"; // Desempate de segurança para os dados do backup

        try {
            List<FinanceiroEntity> entidades = entityManager.createQuery(jpql, FinanceiroEntity.class)
                    .setParameter("dataInicio", dataInicio)
                    .setParameter("dataFim", dataFim)
                    .setParameter("status", status.toUpperCase())
                    .getResultList();

            return entidades.stream()
                    .filter(Objects::nonNull)
                    .map(FinanceiroService::mapToDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }


    @Transactional
    public List<FinanceiroEntity> atualizarEmLote(List<FinanceiroEntity> financeiroList) {
        if (financeiroList == null) {
            return Collections.emptyList();
        }

        List<FinanceiroEntity> atualizadas = new ArrayList<>();

        try {
            for (FinanceiroEntity entity : financeiroList) {
                if (entity.getOs() != null) {
                    FinanceiroEntity deVoltaDoBanco = entityManager.merge(entity);
                    atualizadas.add(deVoltaDoBanco);
                }
            }

            // Força o envio imediato de todas as alterações pendentes para o SQL Server
            entityManager.flush();

        } catch (Exception e) {
            e.printStackTrace(); // É importante logar o erro para diagnóstico no Quarkus
            return Collections.emptyList(); // Se der erro, retorna uma lista vazia de forma segura
        }

        return atualizadas;
    }

    @Transactional
    public List<FinanceiroEntity> verificarEAtualizarAtrasos() {
        LocalDate hoje = LocalDate.now();

        // 1. Busca os registros relevantes onde ao menos uma parcela está "EM DIA"
        // ou onde o statusGeral ainda é "EM DIA" mas pode precisar ir para "ATRAZADO"
        String jpql = "SELECT f FROM FinanceiroEntity f WHERE " +
                "f.statusParcela1 = 'EM DIA' OR " +
                "f.statusParcela2 = 'EM DIA' OR " +
                "f.statusParcela3 = 'EM DIA' OR " +
                "f.statusGeral = 'EM DIA'";

        List<FinanceiroEntity> listaParaVerificar = entityManager.createQuery(jpql, FinanceiroEntity.class).getResultList();
        List<FinanceiroEntity> modificados = new ArrayList<>();

        for (FinanceiroEntity f : listaParaVerificar) {
            boolean alterado = false;

            // --- BLOCO 1: ATUALIZAÇÃO INDIVIDUAL DAS PARCELAS ---

            // Parcela 1
            if ("EM DIA".equals(f.getStatusParcela1()) && f.getVencimento1() != null && f.getVencimento1().isBefore(hoje)) {
                f.setStatusParcela1("ATRAZADO");
                alterado = true;
            }

            // Parcela 2
            if ("EM DIA".equals(f.getStatusParcela2()) && f.getVencimento2() != null && f.getVencimento2().isBefore(hoje)) {
                f.setStatusParcela2("ATRAZADO");
                alterado = true;
            }

            // Parcela 3
            if ("EM DIA".equals(f.getStatusParcela3()) && f.getVencimento3() != null && f.getVencimento3().isBefore(hoje)) {
                f.setStatusParcela3("ATRAZADO");
                alterado = true;
            }

            // --- BLOCO 2: ATUALIZAÇÃO DO STATUS GERAL ---

            // Verifica se QUALQUER uma das parcelas está "ATRAZADO"
            boolean existeParcelaAtrasada = "ATRAZADO".equals(f.getStatusParcela1()) ||
                    "ATRAZADO".equals(f.getStatusParcela2()) ||
                    "ATRAZADO".equals(f.getStatusParcela3());

            if (existeParcelaAtrasada) {
                // Se houver atraso e o status geral não for ATRAZADO, atualiza
                if (!"ATRAZADO".equals(f.getStatusGeral())) {
                    f.setStatusGeral("ATRAZADO");
                    alterado = true;
                }
            } else {
                // Se nenhuma está atrasada, o status geral só continua "EM DIA" se o atual for diferente disso
                // (Isso protege registros que já estejam com status finais como "PAGO" ou "CANCELADO")
                if (f.getStatusGeral() == null || f.getStatusGeral().trim().isEmpty()) {
                    f.setStatusGeral("EM DIA");
                    alterado = true;
                }
            }

            // 3. SALVA CASO HOUVE MUDANÇA
            if (alterado) {
                FinanceiroEntity atualizado = entityManager.merge(f);
                modificados.add(atualizado);
            }
        }

        if (!modificados.isEmpty()) {
            entityManager.flush();
        }

        return modificados;
    }

    @Transactional
    public List<FinanceiroDTO> buscarPendentesBordero() throws Exception {
        try {
            String jpql = "SELECT f FROM FinanceiroEntity f " +
                    "WHERE f.codRemessa IS NOT NULL " +
                    "AND f.codBordero IS NULL " +
                    "AND f.tipoPagamento = 'BORDERO' " +
                    "AND f.dataFaturamento IS NOT NULL";

            List<FinanceiroEntity> entidades = entityManager.createQuery(jpql, FinanceiroEntity.class)
                    .getResultList();

            // Mapeia a lista de entidades para uma lista de DTOs usando o seu método mapToDTO
            return entidades.stream()
                    .map(FinanceiroService::mapToDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            throw new Exception("Erro ao buscar registros pendentes de borderô: " + e.getMessage());
        }
    }

    @Transactional
    public List<FinanceiroDTO> buscarBorderoPorData(String dataBordero) throws Exception {
        try {
            // JPQL filtrando pela string recebida e ordenando por dataCriacao decrescente (DESC)
            String jpql = "SELECT f FROM FinanceiroEntity f " +
                    "WHERE f.codBordero = :dataBordero " +
                    "ORDER BY f.dataCriacao DESC";

            List<FinanceiroEntity> entidades = entityManager.createQuery(jpql, FinanceiroEntity.class)
                    .setParameter("dataBordero", dataBordero)
                    .getResultList();

            // Mapeia a lista de entidades para DTOs usando o padrão do seu projeto
            return entidades.stream()
                    .map(FinanceiroService::mapToDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            throw new Exception("Erro ao buscar borderô para a data " + dataBordero + ": " + e.getMessage());
        }
    }

    @Transactional
    public List<FinanceiroDTO> buscarListaOsdeUmaRemessa(String dataRemessa) throws Exception {
        try {
            // JPQL filtrando pela string recebida e ordenando por dataCriacao decrescente (DESC)
            String jpql = "SELECT f FROM FinanceiroEntity f " +
                    "WHERE f.codRemessa = :dataRemessa " +
                    "ORDER BY f.dataCriacao DESC";

            List<FinanceiroEntity> entidades = entityManager.createQuery(jpql, FinanceiroEntity.class)
                    .setParameter("dataRemessa", dataRemessa)
                    .getResultList();

            // Mapeia a lista de entidades para DTOs usando o padrão do seu projeto
            return entidades.stream()
                    .map(FinanceiroService::mapToDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            throw new Exception("Erro ao buscar OS para esta remessa " + dataRemessa + ": " + e.getMessage());
        }
    }

    @Transactional
    public List<FinanceiroDTO> buscarListaTudoMenosPago() throws Exception {
        try {

            String jpql = "SELECT f FROM FinanceiroEntity f " +
                    "WHERE f.statusGeral != 'PAGO'";


            List<FinanceiroEntity> entidades = entityManager.createQuery(jpql, FinanceiroEntity.class)
                    .getResultList();

            // Mapeia a lista de entidades para DTOs usando o padrão do seu projeto
            return entidades.stream()
                    .map(FinanceiroService::mapToDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            throw new Exception("Erro ao buscar OS " + e.getMessage());
        }
    }

    @Transactional
    public void deletarOsEmFinanceiro(Long id) throws Exception {
        if (id == null) {
            throw new IllegalArgumentException("O ID da ordem de serviço não pode ser nulo");
        }

        // Busca a despesa existente no banco pelo ID
        FinanceiroEntity osExistente = entityManager.find(FinanceiroEntity.class, id);

        if (osExistente == null) {
            throw new Exception("Ordem de Serviço não encontrada com o código: " + id);
        }

        // Remove o objeto do contexto do Hibernate e do banco de dados
        entityManager.remove(osExistente);

        System.out.println("Ordem de serviço ID " + id + " removida com sucesso. ******************");
    }





    public static FinanceiroDTO mapToDTO(FinanceiroEntity entity) {
        if (entity == null) {
            return null;
        }

        FinanceiroDTO dto = new FinanceiroDTO();

        dto.setOs(entity.getOs());
        dto.setDataEntrada(entity.getDataEntrada());
        dto.setFantasia(entity.getFantasia());
        dto.setCodcli(entity.getCodcli());
        dto.setEquipamento(entity.getEquipamento());
        dto.setMarca(entity.getMarca());
        dto.setModelo(entity.getModelo());
        dto.setTotaliq(entity.getTotaliq());
        dto.setFechamento(entity.getFechamento());
        dto.setDataFaturamento(entity.getDataFaturamento());
        dto.setCodRemessa(entity.getCodRemessa());
        dto.setCodBordero(entity.getCodBordero());
        dto.setTipoPagamento(entity.getTipoPagamento());
        dto.setQtParcela(entity.getQtParcela());
        dto.setPrazo(entity.getPrazo());
        dto.setVencimento1(entity.getVencimento1());
        dto.setValorParcela1(entity.getValorParcela1());
        dto.setStatusParcela1(entity.getStatusParcela1());
        dto.setVencimento2(entity.getVencimento2());
        dto.setValorParcela2(entity.getValorParcela2());
        dto.setStatusParcela2(entity.getStatusParcela2());
        dto.setVencimento3(entity.getVencimento3());
        dto.setValorParcela3(entity.getValorParcela3());
        dto.setStatusParcela3(entity.getStatusParcela3());
        dto.setValorJaPago(entity.getValorJaPago());
        dto.setStatusGeral(entity.getStatusGeral());
        dto.setJurosBordero(entity.getJurosBordero());
        dto.setTaxaBordero(entity.getTaxaBordero());
        dto.setIofDiario(entity.getIofDiario());
        dto.setTarifaBordero(entity.getTarifaBordero());
        dto.setCustoBancarioTotal(entity.getCustoBancarioTotal());
        dto.setValorLiquidoCreditado(entity.getValorLiquidoCreditado());

        return dto;
    }
}
