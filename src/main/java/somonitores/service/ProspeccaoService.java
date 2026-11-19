package somonitores.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import somonitores.dto.ContatoEquipDTO;
import somonitores.dto.ContatoProspeccaoDTO;
import somonitores.dto.HistoricoEquipamentoDTO;
import somonitores.dto.NobreakBateriaVencidaDTO;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PROSPECÇÃO - nobreaks com banco de baterias vencido.
 *
 * Regras (validadas com o usuário em 18/11/2026; ver SQL/nobreaks_bateria_vencida.sql):
 *  - Passagem válida (= consertado): OS FECHADA, com FECHAMENTO e TOTALIQ > 0.
 *  - Vale só a ÚLTIMA passagem válida de cada equipamento.
 *  - Vencido: última manutenção há "meses" ou mais E há menos de ANOS_LIMITE anos.
 *  - Equipamento identificado pelo serial (entre todos os clientes); sem serial / serial
 *    genérico / anotação no serial -> cliente + marca + potência.
 *  - Potência: texto do MODELO convertido em VA (10 KVA = 10000).
 */
@ApplicationScoped
public class ProspeccaoService {

    /** acima disso considera que as baterias já foram trocadas por outro meio */
    public static final int ANOS_LIMITE = 6;

    @Inject
    EntityManager entityManager;

    /**
     * Serial usado para identificar o equipamento (19/11/2026): quando o serial começa com
     * 6 ou mais números e logo em seguida vem texto (3 ou mais letras seguidas), fica só a
     * parte numérica do começo. Ex.: '123456789012EMAIL@EXEMPLO.COM' -> '123456789012'.
     * Sufixos curtos de fábrica continuam: '006244B', '99195211MX', '1983700174E2', '4801011P07525'.
     * Não corta código de barras EAN-13 ('789' + 10 números), que é código do produto e não serial.
     *
     * @param x expressão SQL com o serial já limpo (sem espaços, '/', '-', '.' e em maiúsculas)
     */
    static String serialChaveSql(String x) {
        String posTexto = "PATINDEX('%[^0-9]%', " + x + ")";
        return "CASE WHEN " + x + " LIKE '[0-9][0-9][0-9][0-9][0-9][0-9]%' " +
               "      AND SUBSTRING(" + x + ", " + posTexto + ", 3) LIKE '[A-Z][A-Z][A-Z]' " +
               "      AND NOT (" + x + " LIKE '789%' AND " + posTexto + " = 14) " +
               "     THEN LEFT(" + x + ", " + posTexto + " - 1) " +
               "     ELSE " + x + " END";
    }

    /** Serial limpo de uma OS, como expressão SQL (sem espaços, '/', '-', '.' e em maiúsculas). */
    static String serialLimpoSql(String colunaSerial) {
        return "UPPER(REPLACE(REPLACE(REPLACE(REPLACE(LTRIM(RTRIM(ISNULL(" + colunaSerial + ", ''))), " +
               "' ', ''), '/', ''), '-', ''), '.', ''))";
    }

    /**
     * Condição SQL verdadeira quando o serial (já passado por serialChaveSql) NÃO identifica o
     * equipamento: vazio, só zeros, genérico (SN/NA/NT/X) ou anotação (NAO, SEM..., BATERIA...).
     */
    static String serialNaoConfiavelSql(String x) {
        return "(" + x + " IN ('', 'SN', 'NA', 'NT', 'X', 'XX', 'XXX') " +
               "  OR " + x + " NOT LIKE '%[^0]%' " +
               "  OR " + x + " LIKE '%NAO%' " +
               "  OR " + x + " LIKE N'%NÃO%' " +
               "  OR " + x + " LIKE '%INFO%' " +
               "  OR " + x + " LIKE '%ILEGIVEL%' " +
               "  OR " + x + " LIKE 'SEM%' " +
               "  OR " + x + " LIKE '%VERIFICAR%' " +
               "  OR " + x + " LIKE '%TROCADO%' " +
               "  OR " + x + " LIKE '%BATERIA%' " +
               "  OR " + x + " LIKE 'LOJA%')";
    }

    /**
     * Converte a chave gravada em PROSPECCAO_CONTATO_EQUIP para a regra atual do serial,
     * para que contatos gravados antes da regra continuem ligados ao equipamento.
     */
    private static String chaveContatoSql(String col) {
        return "CASE WHEN " + col + " LIKE 'S|%' " +
               "     THEN 'S|' + " + serialChaveSql("SUBSTRING(" + col + ", 3, 4000)") +
               "     ELSE " + col + " END";
    }

    /**
     * CTEs que identificam cada equipamento (CHAVE_EQUIP) e numeram suas passagens.
     * Inclui TODAS as OS de nobreak (para contagens e histórico); a validade fica em VALIDA.
     */
    private static final String CTE_EQUIPAMENTOS =
            "WITH base AS ( " +
            "    SELECT " +
            "        o.OS, o.CODCLI, o.FANTASIA, o.RZSOCIAL, o.MARCA, o.MODELO, o.SERIAL, o.PATRIMONIO, " +
            "        o.STATUS, o.DATA, o.FECHAMENTO, o.TOTALIQ, o.TAREFA1, o.BATERIAS, o.QT1, o.TECNICO, " +
            "        UPPER(LTRIM(RTRIM(ISNULL(o.MARCA, '')))) AS MARCA_NORM, " +
            "        m.MODELO_COMPACTO, " +
            "        CASE " +
            "            WHEN m.MODELO_COMPACTO = '1/2HP'    THEN 3600 " +
            "            WHEN m.MODELO_COMPACTO = '3/4HP'    THEN 3750 " +
            "            WHEN m.MODELO_COMPACTO LIKE '%KVA%' THEN CASE WHEN n.NUM > 100 THEN n.NUM ELSE n.NUM * 1000 END " +
            "            WHEN m.MODELO_COMPACTO LIKE '%VA%'  THEN n.NUM " +
            "            WHEN n.NUM IS NOT NULL              THEN CASE WHEN n.NUM < 100 THEN n.NUM * 1000 ELSE n.NUM END " +
            "        END AS POTENCIA_VA, " +
            "        " + serialLimpoSql("o.SERIAL") + " AS SERIAL_LIMPO, " +
            "        CASE WHEN o.STATUS = 'FECHADA' " +
            "               AND o.FECHAMENTO IS NOT NULL " +
            "               AND TRY_CONVERT(DECIMAL(18,2), REPLACE(CAST(o.TOTALIQ AS VARCHAR(30)), ',', '.')) > 0 " +
            "             THEN 1 ELSE 0 END AS VALIDA " +
            "    FROM dbo.[OS$] o " +
            "    CROSS APPLY (SELECT UPPER(REPLACE(REPLACE(LTRIM(RTRIM(ISNULL(o.MODELO, ''))), ' ', ''), '.', ',')) AS MODELO_COMPACTO) m " +
            "    CROSS APPLY (SELECT CASE WHEN m.MODELO_COMPACTO LIKE '%HP%' THEN NULL " +
            "                             ELSE TRY_CONVERT(DECIMAL(12,3), " +
            "                                  REPLACE(REPLACE(REPLACE(m.MODELO_COMPACTO, 'KVA', ''), 'VA', ''), ',', '.')) " +
            "                        END AS NUM) n " +
            "    WHERE o.EQUIPAMENTO = 'NOBREAK' " +
            "), " +
            "identificados AS ( " +
            "    SELECT b.*, s.SERIAL_CHAVE, " +
            "        ISNULL(CAST(CAST(b.POTENCIA_VA AS INT) AS VARCHAR(20)), b.MODELO_COMPACTO) AS MODELO_CHAVE, " +
            "        CASE WHEN " + serialNaoConfiavelSql("s.SERIAL_CHAVE") + " THEN 0 ELSE 1 END AS TEM_SERIAL " +
            "    FROM base b " +
            "    CROSS APPLY (SELECT " + serialChaveSql("b.SERIAL_LIMPO") + " AS SERIAL_CHAVE) s " +
            "), " +
            "chaveados AS ( " +
            "    SELECT i.*, " +
            "        CASE WHEN i.TEM_SERIAL = 1 " +
            "             THEN 'S|' + i.SERIAL_CHAVE " +
            "             ELSE 'M|' + CAST(i.CODCLI AS VARCHAR(20)) + '|' + i.MARCA_NORM + '|' + i.MODELO_CHAVE " +
            "        END AS CHAVE_EQUIP " +
            "    FROM identificados i " +
            "), " +
            "ultima AS ( " +
            "    SELECT c.*, " +
            "        ROW_NUMBER() OVER (PARTITION BY c.CHAVE_EQUIP " +
            "                           ORDER BY c.VALIDA DESC, c.FECHAMENTO DESC, c.OS DESC) AS rn, " +
            "        SUM(c.VALIDA) OVER (PARTITION BY c.CHAVE_EQUIP)                          AS QT_MANUTENCOES, " +
            "        COUNT(*)      OVER (PARTITION BY c.CHAVE_EQUIP)                          AS QT_PASSAGENS_TOTAL, " +
            "        COUNT(*)      OVER (PARTITION BY c.CODCLI, c.MARCA_NORM, c.MODELO_CHAVE) AS QT_PASSAGENS_MODELO " +
            "    FROM chaveados c " +
            ") ";

    /**
     * Lista os nobreaks com banco de baterias vencido.
     *
     * @param codcli           cliente (null = todos os clientes)
     * @param meses            idade mínima da última manutenção, em meses
     * @param potenciaMinimaVa potência mínima em VA (null = sem limite)
     * @param potenciaMaximaVa potência máxima em VA (null = sem limite)
     */
    @SuppressWarnings("unchecked")
    public List<NobreakBateriaVencidaDTO> buscarNobreaksBateriaVencida(Long codcli, int meses,
                                                                       Integer potenciaMinimaVa,
                                                                       Integer potenciaMaximaVa) {
        LocalDate hoje = LocalDate.now();
        // última manutenção ATÉ esta data (inclusive)  -> usa "< dia seguinte"
        LocalDate manutencaoAteExclusivo = hoje.minusMonths(meses).plusDays(1);
        // e DEPOIS desta data (menos de ANOS_LIMITE anos)
        LocalDate manutencaoDepois = hoje.minusYears(ANOS_LIMITE);

        StringBuilder sql = new StringBuilder(CTE_EQUIPAMENTOS)
                .append("SELECT ")
                .append("    u.OS, u.CODCLI, u.FANTASIA, u.RZSOCIAL, u.MARCA, u.MODELO, ")
                .append("    CAST(u.POTENCIA_VA AS INT) AS POTENCIA_VA, u.SERIAL, u.PATRIMONIO, ")
                .append("    CASE WHEN u.TEM_SERIAL = 1 THEN 'SERIAL' ELSE 'MARCA+MODELO' END AS IDENTIFICADO_POR, ")
                .append("    u.FECHAMENTO, u.TOTALIQ, ")
                .append("    DATEDIFF(MONTH, u.FECHAMENTO, GETDATE()) AS MESES_DESDE_MANUTENCAO, ")
                .append("    u.QT_MANUTENCOES, u.QT_PASSAGENS_TOTAL, u.QT_PASSAGENS_MODELO, u.CHAVE_EQUIP ")
                .append("FROM ultima u ")
                .append("WHERE u.rn = 1 ")
                .append("  AND u.VALIDA = 1 ")
                .append("  AND u.FECHAMENTO < :manutencaoAteExclusivo ")
                .append("  AND u.FECHAMENTO > :manutencaoDepois ");

        // filtros opcionais: só entram no SQL quando informados
        if (codcli != null) {
            sql.append("  AND u.CODCLI = :codcli ");
        }
        if (potenciaMinimaVa != null) {
            sql.append("  AND u.POTENCIA_VA >= :potenciaMinimaVa ");
        }
        if (potenciaMaximaVa != null) {
            sql.append("  AND u.POTENCIA_VA <= :potenciaMaximaVa ");
        }
        sql.append("ORDER BY u.FANTASIA, u.FECHAMENTO ASC");

        Query query = entityManager.createNativeQuery(sql.toString())
                .setParameter("manutencaoAteExclusivo", manutencaoAteExclusivo)
                .setParameter("manutencaoDepois", manutencaoDepois);
        if (codcli != null) {
            query.setParameter("codcli", codcli);
        }
        if (potenciaMinimaVa != null) {
            query.setParameter("potenciaMinimaVa", potenciaMinimaVa);
        }
        if (potenciaMaximaVa != null) {
            query.setParameter("potenciaMaximaVa", potenciaMaximaVa);
        }

        List<Object[]> linhas = query.getResultList();
        List<NobreakBateriaVencidaDTO> resultado = new ArrayList<>(linhas.size());
        for (Object[] r : linhas) {
            resultado.add(NobreakBateriaVencidaDTO.builder()
                    .ultimaOs(toLong(r[0]))
                    .codcli(toLong(r[1]))
                    .fantasia(toStr(r[2]))
                    .rzsocial(toStr(r[3]))
                    .marca(toStr(r[4]))
                    .modelo(toStr(r[5]))
                    .potenciaVa(toInt(r[6]))
                    .serial(toStr(r[7]))
                    .patrimonio(toStr(r[8]))
                    .identificadoPor(toStr(r[9]))
                    .ultimaManutencao(toDataIso(r[10]))
                    .valorUltimaOs(toStr(r[11]))
                    .mesesDesdeManutencao(toInt(r[12]))
                    .qtManutencoes(toInt(r[13]))
                    .qtPassagensTotal(toInt(r[14]))
                    .qtPassagensModelo(toInt(r[15]))
                    .chaveEquip(toStr(r[16]))
                    .build());
        }
        enriquecerComContatos(resultado);
        return resultado;
    }

    // ------------------------------------------------------------------ contatos (telemarketing)

    public static final Set<String> RESULTADOS_ANDAMENTO = Set.of(
            "NAO_ATENDEU", "PEDIU_RETORNO", "INTERESSADO", "ORCAMENTO_ENVIADO", "COLETA_AGENDADA");
    public static final Set<String> RESULTADOS_REJEITADO = Set.of(
            "SEM_INTERESSE", "TROCOU_FORA", "DESATIVADO", "TELEFONE_ERRADO");
    public static final Set<String> CANAIS = Set.of("TELEFONE", "WHATSAPP");

    /**
     * Preenche a situação de prospecção de cada equipamento.
     * Só valem contatos feitos a partir da última manutenção do equipamento
     * (se o equipamento voltou ao laboratório depois, o ciclo recomeça).
     * Se as tabelas de contato ainda não existirem, a prospecção funciona sem a situação.
     */
    @SuppressWarnings("unchecked")
    private void enriquecerComContatos(List<NobreakBateriaVencidaDTO> lista) {
        if (lista.isEmpty()) return;

        Map<Long, Object[]> ultimoContatoCliente = new HashMap<>();
        Map<String, Object[]> ultimoContatoEquip = new HashMap<>();
        try {
            List<Object[]> clientes = entityManager.createNativeQuery(
                    "SELECT x.CODCLI, x.DATA_HORA, x.OPERADOR FROM ( " +
                    "  SELECT c.CODCLI, c.DATA_HORA, c.OPERADOR, " +
                    "         ROW_NUMBER() OVER (PARTITION BY c.CODCLI ORDER BY c.DATA_HORA DESC, c.ID DESC) AS rn " +
                    "  FROM dbo.PROSPECCAO_CONTATO c) x " +
                    "WHERE x.rn = 1").getResultList();
            for (Object[] r : clientes) ultimoContatoCliente.put(toLong(r[0]), r);

            List<Object[]> equipamentos = entityManager.createNativeQuery(
                    "SELECT x.CHAVE_EQUIP, x.RESULTADO, x.DATA_HORA, x.OPERADOR, x.DATA_RETORNO FROM ( " +
                    "  SELECT k.CHAVE_EQUIP, e.RESULTADO, c.DATA_HORA, c.OPERADOR, c.DATA_RETORNO, " +
                    "         ROW_NUMBER() OVER (PARTITION BY k.CHAVE_EQUIP ORDER BY c.DATA_HORA DESC, e.ID DESC) AS rn " +
                    "  FROM dbo.PROSPECCAO_CONTATO_EQUIP e " +
                    "  CROSS APPLY (SELECT " + chaveContatoSql("e.CHAVE_EQUIP") + " AS CHAVE_EQUIP) k " +
                    "  JOIN dbo.PROSPECCAO_CONTATO c ON c.ID = e.CONTATO_ID) x " +
                    "WHERE x.rn = 1").getResultList();
            for (Object[] r : equipamentos) ultimoContatoEquip.put(toStr(r[0]), r);
        } catch (Exception e) {
            System.out.println("Prospecção: tabelas de contato indisponíveis (" + e.getMessage() + ")");
            lista.forEach(n -> n.setSituacao("SEM_CONTATO"));
            return;
        }

        for (NobreakBateriaVencidaDTO n : lista) {
            LocalDate ultimaManutencao = n.getUltimaManutencao() != null ? LocalDate.parse(n.getUltimaManutencao()) : null;
            n.setSituacao("SEM_CONTATO");

            Object[] cli = ultimoContatoCliente.get(n.getCodcli());
            if (cli != null && contatoValeParaCiclo(cli[1], ultimaManutencao)) {
                n.setClienteUltimoContato(toDataHoraIso(cli[1]));
                n.setClienteUltimoOperador(toStr(cli[2]));
                n.setSituacao("CLIENTE_CONTATADO");
            }

            Object[] eq = n.getChaveEquip() != null ? ultimoContatoEquip.get(n.getChaveEquip()) : null;
            if (eq != null && contatoValeParaCiclo(eq[2], ultimaManutencao)) {
                String resultadoEquip = toStr(eq[1]);
                n.setEquipResultado(resultadoEquip);
                n.setEquipUltimoContato(toDataHoraIso(eq[2]));
                n.setEquipOperador(toStr(eq[3]));
                n.setEquipDataRetorno(toDataIso(eq[4]));
                n.setSituacao(RESULTADOS_REJEITADO.contains(resultadoEquip) ? "REJEITADO" : "ANDAMENTO");
            }
        }
    }

    /** o contato só vale se foi feito no mesmo dia ou depois da última manutenção */
    private static boolean contatoValeParaCiclo(Object dataHoraContato, LocalDate ultimaManutencao) {
        String iso = toDataIso(dataHoraContato);
        if (iso == null) return false;
        if (ultimaManutencao == null) return true;
        return !LocalDate.parse(iso).isBefore(ultimaManutencao);
    }

    /** Grava um contato e os equipamentos tratados nele. Retorna o contato gravado. */
    @Transactional
    public ContatoProspeccaoDTO registrarContato(ContatoProspeccaoDTO dto) {
        validarContato(dto);

        Object idGerado = entityManager.createNativeQuery(
                "INSERT INTO dbo.PROSPECCAO_CONTATO (CODCLI, OPERADOR, CANAL, PESSOA_CONTATADA, OBSERVACAO, DATA_RETORNO) " +
                "OUTPUT INSERTED.ID " +
                "VALUES (:codcli, :operador, :canal, NULLIF(:pessoa, ''), NULLIF(:observacao, ''), " +
                "        TRY_CONVERT(DATE, NULLIF(:dataRetorno, ''), 23))")
                .setParameter("codcli", dto.getCodcli())
                .setParameter("operador", dto.getOperador().trim())
                .setParameter("canal", dto.getCanal())
                .setParameter("pessoa", textoOuVazio(dto.getPessoaContatada()))
                .setParameter("observacao", textoOuVazio(dto.getObservacao()))
                .setParameter("dataRetorno", textoOuVazio(dto.getDataRetorno()))
                .getSingleResult();
        Long contatoId = toLong(idGerado);

        for (ContatoEquipDTO e : dto.getEquipamentos()) {
            entityManager.createNativeQuery(
                    "INSERT INTO dbo.PROSPECCAO_CONTATO_EQUIP " +
                    "(CONTATO_ID, CHAVE_EQUIP, OS_REFERENCIA, MARCA, MODELO, SERIAL, RESULTADO) " +
                    "VALUES (:contatoId, :chave, :os, NULLIF(:marca, ''), NULLIF(:modelo, ''), NULLIF(:serial, ''), :resultado)")
                    .setParameter("contatoId", contatoId)
                    .setParameter("chave", e.getChaveEquip())
                    .setParameter("os", e.getOsReferencia() != null ? e.getOsReferencia() : 0L)
                    .setParameter("marca", cortar(textoOuVazio(e.getMarca()), 60))
                    .setParameter("modelo", cortar(textoOuVazio(e.getModelo()), 60))
                    .setParameter("serial", cortar(textoOuVazio(e.getSerial()), 200))
                    .setParameter("resultado", e.getResultado())
                    .executeUpdate();
        }

        return listarContatos(dto.getCodcli()).stream()
                .filter(c -> contatoId.equals(c.getId()))
                .findFirst()
                .orElse(dto);
    }

    private void validarContato(ContatoProspeccaoDTO dto) {
        if (dto == null || dto.getCodcli() == null) {
            throw new IllegalArgumentException("Informe o cliente do contato.");
        }
        if (dto.getOperador() == null || dto.getOperador().isBlank()) {
            throw new IllegalArgumentException("Informe o operador (atendente) que fez o contato.");
        }
        if (dto.getCanal() == null || !CANAIS.contains(dto.getCanal())) {
            throw new IllegalArgumentException("Canal inválido: use TELEFONE ou WHATSAPP.");
        }
        if (dto.getDataRetorno() != null && !dto.getDataRetorno().isBlank()) {
            try {
                LocalDate.parse(dto.getDataRetorno());
            } catch (Exception e) {
                throw new IllegalArgumentException("Data de retorno inválida.");
            }
        }
        if (dto.getEquipamentos() == null) {
            dto.setEquipamentos(new ArrayList<>());
        }
        for (ContatoEquipDTO e : dto.getEquipamentos()) {
            if (e.getChaveEquip() == null || e.getChaveEquip().isBlank()) {
                throw new IllegalArgumentException("Equipamento sem identificação.");
            }
            if (e.getResultado() == null
                    || !(RESULTADOS_ANDAMENTO.contains(e.getResultado()) || RESULTADOS_REJEITADO.contains(e.getResultado()))) {
                throw new IllegalArgumentException("Resultado inválido para o equipamento " + e.getMarca() + " " + e.getModelo() + ".");
            }
        }
    }

    private static final String SELECT_CONTATOS =
            "SELECT c.ID, c.CODCLI, c.DATA_HORA, c.OPERADOR, c.CANAL, c.PESSOA_CONTATADA, c.OBSERVACAO, c.DATA_RETORNO, " +
            "       " + chaveContatoSql("e.CHAVE_EQUIP") + " AS CHAVE_EQUIP, e.OS_REFERENCIA, e.MARCA, e.MODELO, e.SERIAL, e.RESULTADO, cli.NOMEFANTASIA " +
            "FROM dbo.PROSPECCAO_CONTATO c " +
            "LEFT JOIN dbo.PROSPECCAO_CONTATO_EQUIP e ON e.CONTATO_ID = c.ID " +
            "LEFT JOIN dbo.[CLIENTE$] cli ON cli.CODCLI = c.CODCLI ";

    /** Contatos do cliente (mais recentes primeiro), com os equipamentos tratados em cada um. */
    @SuppressWarnings("unchecked")
    public List<ContatoProspeccaoDTO> listarContatos(Long codcli) {
        List<Object[]> linhas = entityManager.createNativeQuery(
                SELECT_CONTATOS +
                "WHERE c.CODCLI = :codcli " +
                "ORDER BY c.DATA_HORA DESC, c.ID DESC, e.ID")
                .setParameter("codcli", codcli)
                .getResultList();
        return agruparContatos(linhas);
    }

    /**
     * Contatos feitos no período (datas inclusivas), para acompanhar o trabalho do telemarketing.
     * operador = null -> todos.
     */
    @SuppressWarnings("unchecked")
    public List<ContatoProspeccaoDTO> listarContatosPeriodo(LocalDate inicio, LocalDate fim, String operador) {
        boolean filtraOperador = operador != null && !operador.isBlank();
        Query query = entityManager.createNativeQuery(
                SELECT_CONTATOS +
                "WHERE c.DATA_HORA >= :inicio AND c.DATA_HORA < :fimExclusivo " +
                (filtraOperador ? "AND c.OPERADOR = :operador " : "") +
                "ORDER BY c.DATA_HORA DESC, c.ID DESC, e.ID")
                .setParameter("inicio", inicio)
                .setParameter("fimExclusivo", fim.plusDays(1));
        if (filtraOperador) {
            query.setParameter("operador", operador.trim());
        }
        return agruparContatos(query.getResultList());
    }

    /** Agrupa as linhas (contato x equipamento) em contatos com sua lista de equipamentos. */
    private List<ContatoProspeccaoDTO> agruparContatos(List<Object[]> linhas) {
        Map<Long, ContatoProspeccaoDTO> porId = new LinkedHashMap<>();
        for (Object[] r : linhas) {
            Long id = toLong(r[0]);
            ContatoProspeccaoDTO contato = porId.computeIfAbsent(id, k -> ContatoProspeccaoDTO.builder()
                    .id(id)
                    .codcli(toLong(r[1]))
                    .fantasia(toStr(r[14]))
                    .dataHora(toDataHoraIso(r[2]))
                    .operador(toStr(r[3]))
                    .canal(toStr(r[4]))
                    .pessoaContatada(toStr(r[5]))
                    .observacao(toStr(r[6]))
                    .dataRetorno(toDataIso(r[7]))
                    .build());
            if (r[8] != null) {
                Long os = toLong(r[9]);
                contato.getEquipamentos().add(ContatoEquipDTO.builder()
                        .chaveEquip(toStr(r[8]))
                        .osReferencia(os != null && os != 0L ? os : null)
                        .marca(toStr(r[10]))
                        .modelo(toStr(r[11]))
                        .serial(toStr(r[12]))
                        .resultado(toStr(r[13]))
                        .build());
            }
        }
        return new ArrayList<>(porId.values());
    }

    private static String textoOuVazio(String s) {
        return s == null ? "" : s.trim();
    }

    private static String cortar(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    /**
     * Histórico completo (todas as passagens, qualquer status) do equipamento ao qual a OS pertence,
     * usando a mesma regra de identificação da prospecção.
     */
    @SuppressWarnings("unchecked")
    public List<HistoricoEquipamentoDTO> buscarHistoricoEquipamento(Long os) {
        String sql = CTE_EQUIPAMENTOS +
                "SELECT u.OS, u.CODCLI, u.FANTASIA, u.DATA, u.FECHAMENTO, u.STATUS, u.TOTALIQ, " +
                "       u.MARCA, u.MODELO, u.SERIAL, u.TAREFA1, u.BATERIAS, u.QT1, u.TECNICO, u.VALIDA " +
                "FROM ultima u " +
                "WHERE u.CHAVE_EQUIP = (SELECT TOP 1 x.CHAVE_EQUIP FROM chaveados x WHERE x.OS = :os) " +
                "ORDER BY COALESCE(u.FECHAMENTO, u.DATA) DESC, u.OS DESC";

        List<Object[]> linhas = entityManager.createNativeQuery(sql)
                .setParameter("os", os)
                .getResultList();

        List<HistoricoEquipamentoDTO> resultado = new ArrayList<>(linhas.size());
        for (Object[] r : linhas) {
            resultado.add(HistoricoEquipamentoDTO.builder()
                    .os(toLong(r[0]))
                    .codcli(toLong(r[1]))
                    .fantasia(toStr(r[2]))
                    .data(toDataIso(r[3]))
                    .fechamento(toDataIso(r[4]))
                    .status(toStr(r[5]))
                    .totaliq(toStr(r[6]))
                    .marca(toStr(r[7]))
                    .modelo(toStr(r[8]))
                    .serial(toStr(r[9]))
                    .tarefa1(toStr(r[10]))
                    .baterias(toStr(r[11]))
                    .qt1(toStr(r[12]))
                    .tecnico(toStr(r[13]))
                    .valida(toInt(r[14]) != null && toInt(r[14]) == 1)
                    .build());
        }
        return resultado;
    }

    // ------------------------------------------------------------------ conversões

    private static String toStr(Object o) {
        return o == null ? null : o.toString().trim();
    }

    private static Long toLong(Object o) {
        return o instanceof Number n ? n.longValue() : null;
    }

    private static Integer toInt(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }

    /** devolve yyyy-MM-ddTHH:mm */
    private static String toDataHoraIso(Object o) {
        if (o == null) return null;
        LocalDateTime ldt = null;
        if (o instanceof Timestamp t) ldt = t.toLocalDateTime();
        else if (o instanceof LocalDateTime l) ldt = l;
        else if (o instanceof java.util.Date d) ldt = new Timestamp(d.getTime()).toLocalDateTime();
        if (ldt != null) return ldt.withSecond(0).withNano(0).toString().substring(0, 16);
        String s = o.toString().replace(' ', 'T');
        return s.length() >= 16 ? s.substring(0, 16) : s;
    }

    /** aceita Timestamp / LocalDateTime / Date vindos do driver e devolve yyyy-MM-dd */
    private static String toDataIso(Object o) {
        if (o == null) return null;
        if (o instanceof Timestamp t) return t.toLocalDateTime().toLocalDate().toString();
        if (o instanceof LocalDateTime ldt) return ldt.toLocalDate().toString();
        if (o instanceof LocalDate ld) return ld.toString();
        if (o instanceof java.util.Date d) return new Timestamp(d.getTime()).toLocalDateTime().toLocalDate().toString();
        String s = o.toString();
        return s.length() >= 10 ? s.substring(0, 10) : s;
    }
}
