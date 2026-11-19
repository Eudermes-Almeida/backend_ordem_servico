package somonitores.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import somonitores.dto.EquipamentoPublicoDTO;
import somonitores.dto.EtiquetaOsDTO;
import somonitores.dto.PassagemPublicaDTO;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ETIQUETA DO EQUIPAMENTO (QR Code) - regras decididas com o usuário em 19/11/2026:
 *  - Etiqueta só para OS FECHADA (é colada no equipamento na entrega).
 *  - O QR leva a um link com código aleatório (tabela OS_ETIQUETA), nunca o número da OS.
 *  - A página pública abre direto (sem login) e NUNCA mostra valores.
 *  - Histórico: OS do MESMO cliente com o mesmo serial (regra de serial da prospecção),
 *    mesmo tipo de equipamento e mesma marca (19/11: serial igual com marca diferente é outro
 *    aparelho); sem serial confiável, só a OS da etiqueta.
 *  - Garantias contadas do FECHAMENTO: peças/serviço 90 dias; baterias 180 dias, só se a OS
 *    trocou baterias = (BATERIAS preenchido e QT1 > 0) ou tarefa "BATERIA - SUBSTITUIÇÃO".
 */
@ApplicationScoped
public class EtiquetaService {

    public static final int GARANTIA_SERVICO_DIAS = 90;
    public static final int GARANTIA_BATERIA_DIAS = 180;
    private static final String TAREFA_TROCA_BATERIA = "BATERIA - SUBSTITUICAO";

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int TAMANHO_CODIGO = 12;
    private static final SecureRandom SORTEIO = new SecureRandom();

    @ConfigProperty(name = "etiqueta.url-base", defaultValue = "https://app.somonitores.tec.br/e/")
    String urlBase;

    @Inject
    EntityManager entityManager;

    /** Colunas lidas da OS (mesma ordem usada em montarPassagem). Nenhum valor em R$. */
    private static final String SELECT_OS =
            "SELECT o.OS, o.CODCLI, o.FANTASIA, o.EQUIPAMENTO, o.MARCA, o.MODELO, o.SERIAL, o.PATRIMONIO, " +   // 0-7
            "       o.STATUS, o.DATA, o.FECHAMENTO, " +                                                         // 8-10
            "       o.DEFEITO1, o.DEFEITO2, o.DEFEITO3, o.TAREFA1, o.TAREFA2, o.TAREFA3, " +                    // 11-16
            "       o.BATERIAS, o.QT1, o.MATERIAL2, o.QT2, o.MATERIAL3, o.QT3, o.OUTROSITENS, o.QTOUTROSITENS, " + // 17-24
            "       o.TECNICO, b.SERIAL_CHAVE, " +                                                              // 25-26
            "       CASE WHEN " + ProspeccaoService.serialNaoConfiavelSql("b.SERIAL_CHAVE") + " THEN 0 ELSE 1 END AS TEM_SERIAL " + // 27
            "FROM dbo.[OS$] o " +
            "CROSS APPLY (SELECT " + ProspeccaoService.serialLimpoSql("o.SERIAL") + " AS SERIAL_LIMPO) a " +
            "CROSS APPLY (SELECT " + ProspeccaoService.serialChaveSql("a.SERIAL_LIMPO") + " AS SERIAL_CHAVE) b ";

    // ------------------------------------------------------------------ etiqueta (uso interno)

    /**
     * Devolve os dados da etiqueta da OS, criando o código do QR na primeira impressão
     * (reimpressões usam o mesmo código).
     *
     * @throws IllegalArgumentException OS inexistente
     * @throws IllegalStateException    OS não está fechada
     */
    @Transactional
    public EtiquetaOsDTO gerarEtiqueta(Long os) {
        Object[] r = buscarOs(os);
        if (r == null) {
            throw new IllegalArgumentException("Ordem de serviço " + os + " não encontrada.");
        }
        if (!"FECHADA".equalsIgnoreCase(texto(r[8])) || r[10] == null) {
            throw new IllegalStateException("A etiqueta só pode ser impressa com a ordem de serviço FECHADA "
                    + "(situação atual: " + texto(r[8]) + ").");
        }

        String codigo = buscarCodigo(os);
        if (codigo == null) {
            codigo = criarCodigo(os);
        }

        PassagemPublicaDTO p = montarPassagem(r, os);
        return EtiquetaOsDTO.builder()
                .os(os)
                .codigo(codigo)
                .url(urlBase + codigo)
                .fechamento(p.getFechamento())
                .garantiaServicoAte(p.getGarantiaServicoAte())
                .garantiaBateriaAte(p.getGarantiaBateriaAte())
                .equipamento(texto(r[3]))
                .marca(texto(r[4]))
                .modelo(texto(r[5]))
                .serial(texto(r[6]))
                .fantasia(texto(r[2]))
                .build();
    }

    private String buscarCodigo(Long os) {
        List<?> l = entityManager.createNativeQuery("SELECT CODIGO FROM dbo.OS_ETIQUETA WHERE OS = :os")
                .setParameter("os", os)
                .getResultList();
        return l.isEmpty() ? null : l.get(0).toString();
    }

    private String criarCodigo(Long os) {
        for (int tentativa = 0; tentativa < 5; tentativa++) {
            String codigo = sortearCodigo();
            int existe = entityManager.createNativeQuery("SELECT 1 FROM dbo.OS_ETIQUETA WHERE CODIGO = :c")
                    .setParameter("c", codigo)
                    .getResultList().size();
            if (existe == 0) {
                entityManager.createNativeQuery("INSERT INTO dbo.OS_ETIQUETA (OS, CODIGO) VALUES (:os, :c)")
                        .setParameter("os", os)
                        .setParameter("c", codigo)
                        .executeUpdate();
                return codigo;
            }
        }
        throw new IllegalStateException("Não foi possível gerar o código da etiqueta. Tente novamente.");
    }

    private static String sortearCodigo() {
        StringBuilder sb = new StringBuilder(TAMANHO_CODIGO);
        for (int i = 0; i < TAMANHO_CODIGO; i++) {
            sb.append(ALFABETO.charAt(SORTEIO.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ página pública (QR Code)

    /** @return null quando o código não existe (etiqueta inválida ou apagada) */
    @SuppressWarnings("unchecked")
    public EquipamentoPublicoDTO consultarPorCodigo(String codigo) {
        if (codigo == null || !codigo.matches("[A-Za-z0-9]{6,20}")) {
            return null;
        }
        List<?> ids = entityManager.createNativeQuery("SELECT OS FROM dbo.OS_ETIQUETA WHERE CODIGO = :c")
                .setParameter("c", codigo)
                .getResultList();
        if (ids.isEmpty()) {
            return null;
        }
        Long osEtiqueta = ((Number) ids.get(0)).longValue();

        Object[] etiqueta = buscarOs(osEtiqueta);
        if (etiqueta == null) {
            return null;
        }
        boolean porSerial = toInt(etiqueta[27]) == 1;

        List<Object[]> linhas;
        if (porSerial) {
            linhas = entityManager.createNativeQuery(SELECT_OS +
                            "WHERE o.CODCLI = :codcli AND b.SERIAL_CHAVE = :serial " +
                            // mesmo serial com outro tipo/marca = outro aparelho (ou serial digitado errado)
                            "  AND UPPER(LTRIM(RTRIM(ISNULL(o.EQUIPAMENTO, '')))) = :equipamento " +
                            "  AND UPPER(LTRIM(RTRIM(ISNULL(o.MARCA, '')))) = :marca " +
                            "ORDER BY COALESCE(o.FECHAMENTO, o.DATA) DESC, o.OS DESC")
                    .setParameter("codcli", etiqueta[1])
                    .setParameter("serial", texto(etiqueta[26]))
                    .setParameter("equipamento", texto(etiqueta[3]).toUpperCase(Locale.ROOT))
                    .setParameter("marca", texto(etiqueta[4]).toUpperCase(Locale.ROOT))
                    .getResultList();
        } else {
            linhas = new ArrayList<>();
            linhas.add(etiqueta);
        }

        List<PassagemPublicaDTO> historico = new ArrayList<>(linhas.size());
        for (Object[] r : linhas) {
            historico.add(montarPassagem(r, osEtiqueta));
        }

        PassagemPublicaDTO ultima = historico.stream()
                .filter(p -> "FECHADA".equalsIgnoreCase(p.getStatus()) && p.getFechamento() != null)
                .max((a, b) -> a.getFechamento().compareTo(b.getFechamento()) != 0
                        ? a.getFechamento().compareTo(b.getFechamento())
                        : a.getOs().compareTo(b.getOs()))
                .orElse(null);

        // dados do equipamento: da OS mais recente (marca/modelo podem ter sido corrigidos depois)
        Object[] base = linhas.get(0);
        return EquipamentoPublicoDTO.builder()
                .cliente(texto(etiqueta[2]))
                .equipamento(texto(base[3]))
                .marca(texto(base[4]))
                .modelo(texto(base[5]))
                .serial(texto(etiqueta[6]))
                .patrimonio(texto(base[7]))
                .historicoPorSerial(porSerial)
                .ultimaManutencao(ultima)
                .historico(historico)
                .build();
    }

    // ------------------------------------------------------------------ apoio

    @SuppressWarnings("unchecked")
    private Object[] buscarOs(Long os) {
        List<Object[]> l = entityManager.createNativeQuery(SELECT_OS + "WHERE o.OS = :os")
                .setParameter("os", os)
                .getResultList();
        return l.isEmpty() ? null : l.get(0);
    }

    private static PassagemPublicaDTO montarPassagem(Object[] r, Long osEtiqueta) {
        Long os = ((Number) r[0]).longValue();
        String status = texto(r[8]);
        LocalDate fechamento = toData(r[10]);
        boolean fechada = "FECHADA".equalsIgnoreCase(status) && fechamento != null;
        boolean trocouBateria = trocouBateria(r);

        List<String> itens = new ArrayList<>();
        adicionarItem(itens, r[17], r[18], "Bateria ");
        adicionarItem(itens, r[19], r[20], "");
        adicionarItem(itens, r[21], r[22], "");
        adicionarItem(itens, r[23], r[24], "");

        return PassagemPublicaDTO.builder()
                .os(os)
                .entrada(toIso(toData(r[9])))
                .fechamento(fechada ? fechamento.toString() : toIso(fechamento))
                .status(status)
                .defeitos(naoVazios(r[11], r[12], r[13]))
                .servicos(naoVazios(r[14], r[15], r[16]))
                .itens(itens)
                .tecnico(texto(r[25]))
                .trocouBateria(trocouBateria)
                .garantiaServicoAte(fechada ? fechamento.plusDays(GARANTIA_SERVICO_DIAS).toString() : null)
                .garantiaBateriaAte(fechada && trocouBateria ? fechamento.plusDays(GARANTIA_BATERIA_DIAS).toString() : null)
                .osDaEtiqueta(os.equals(osEtiqueta))
                .build();
    }

    /** (BATERIAS preenchido e QT1 > 0) ou alguma tarefa = "BATERIA - SUBSTITUIÇÃO" */
    static boolean trocouBateria(Object[] r) {
        boolean pelaQuantidade = !texto(r[17]).isEmpty() && quantidade(r[18]) > 0;
        boolean pelaTarefa = TAREFA_TROCA_BATERIA.equals(normalizar(r[14]))
                || TAREFA_TROCA_BATERIA.equals(normalizar(r[15]))
                || TAREFA_TROCA_BATERIA.equals(normalizar(r[16]));
        return pelaQuantidade || pelaTarefa;
    }

    private static void adicionarItem(List<String> itens, Object nome, Object qt, String prefixo) {
        String n = texto(nome);
        if (n.isEmpty()) return;
        double q = quantidade(qt);
        if (q <= 0) return;
        String qtTexto = q == Math.rint(q) ? String.valueOf((long) q) : String.valueOf(q).replace('.', ',');
        // o campo BATERIAS às vezes já vem como "BATERIA 12V/7A"
        String descricao = normalizar(n).startsWith("BATERIA") ? n : prefixo + n;
        itens.add(qtTexto + " x " + descricao);
    }

    private static double quantidade(Object o) {
        String s = texto(o).replace(',', '.');
        if (s.isEmpty()) return 0;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** textos preenchidos, sem repetição e sem o marcador interno "VERIFICAR" */
    private static List<String> naoVazios(Object... valores) {
        List<String> l = new ArrayList<>();
        List<String> vistos = new ArrayList<>();
        for (Object v : valores) {
            String s = texto(v);
            String chave = normalizar(s);
            if (s.isEmpty() || chave.equals("VERIFICAR") || vistos.contains(chave)) continue;
            vistos.add(chave);
            l.add(s);
        }
        return l;
    }

    private static String normalizar(Object o) {
        String s = Normalizer.normalize(texto(o), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String texto(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private static int toInt(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static LocalDate toData(Object o) {
        if (o == null) return null;
        if (o instanceof Timestamp t) return t.toLocalDateTime().toLocalDate();
        if (o instanceof LocalDateTime ldt) return ldt.toLocalDate();
        if (o instanceof LocalDate ld) return ld;
        if (o instanceof java.util.Date d) return new Timestamp(d.getTime()).toLocalDateTime().toLocalDate();
        String s = o.toString();
        return s.length() >= 10 ? LocalDate.parse(s.substring(0, 10)) : null;
    }

    private static String toIso(LocalDate d) {
        return d == null ? null : d.toString();
    }
}
