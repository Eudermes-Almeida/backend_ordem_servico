package somonitores.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcula a frota de nobreaks de TODOS os clientes com os critérios da prospecção:
 * última passagem válida (OS FECHADA com valor) de cada equipamento, identificado pela regra de
 * serial, com manutenção há menos de {@link ProspeccaoService#ANOS_LIMITE} anos.
 * Usado por {@link FrotaCache} (inclusive fora de uma requisição, por isso @ActivateRequestContext).
 */
@ApplicationScoped
public class FrotaCarregador {

    /** colunas: 0 CODCLI, 1 OS, 2 MARCA, 3 MODELO, 4 SERIAL, 5 SERIAL_CHAVE, 6 TEM_SERIAL, 7 PATRIMONIO, 8 FECHAMENTO */
    static final String SELECT_FROTA = ProspeccaoService.CTE_EQUIPAMENTOS +
            "SELECT u.CODCLI, u.OS, u.MARCA, u.MODELO, u.SERIAL, u.SERIAL_CHAVE, u.TEM_SERIAL, u.PATRIMONIO, u.FECHAMENTO " +
            "FROM ultima u " +
            "WHERE u.rn = 1 AND u.VALIDA = 1 " +
            "  AND u.FECHAMENTO > :depois " +
            "ORDER BY u.CODCLI, u.FECHAMENTO ASC, u.OS ASC";

    @Inject
    EntityManager entityManager;

    @ActivateRequestContext
    @SuppressWarnings("unchecked")
    public Map<Long, List<Object[]>> carregar() {
        long inicio = System.currentTimeMillis();
        List<Object[]> linhas = entityManager.createNativeQuery(SELECT_FROTA)
                .setParameter("depois", LocalDate.now().minusYears(ProspeccaoService.ANOS_LIMITE))
                .getResultList();
        Map<Long, List<Object[]>> porCliente = new HashMap<>();
        for (Object[] r : linhas) {
            if (!(r[0] instanceof Number n)) continue;
            porCliente.computeIfAbsent(n.longValue(), k -> new ArrayList<>()).add(r);
        }
        System.out.println("Frota (cache): " + linhas.size() + " nobreaks de " + porCliente.size()
                + " clientes calculados em " + (System.currentTimeMillis() - inicio) + " ms");
        return porCliente;
    }
}
