package somonitores.service;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cache em memória da "frota" de nobreaks (última manutenção válida de cada equipamento),
 * usada na página pública do QR (botão "Nobreaks deste cliente").
 *
 * Por quê: a identificação dos equipamentos (regra de serial da prospecção) precisa passar por
 * TODAS as OS de nobreak do banco (o aparelho pode ter ido para outro cliente) e leva ~13 s no
 * SQL Server — demais para quem está no celular. O cálculo é feito ao ligar o backend e refeito
 * em segundo plano quando passa de {@link #VALIDADE_MS}; a consulta do cliente responde na hora.
 */
@ApplicationScoped
public class FrotaCache {

    /** idade máxima dos dados antes de recalcular (em segundo plano) */
    static final long VALIDADE_MS = 10 * 60 * 1000L;

    @Inject
    FrotaCarregador carregador;

    private final ExecutorService fila = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "frota-cache");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean atualizando = new AtomicBoolean(false);

    private volatile Map<Long, List<Object[]>> porCliente;
    private volatile long carregadoEm;

    void aoIniciar(@Observes StartupEvent ev) {
        atualizarEmSegundoPlano();
    }

    void aoParar(@Observes ShutdownEvent ev) {
        fila.shutdownNow();
    }

    /**
     * Linhas da frota do cliente (colunas em {@link FrotaCarregador#SELECT_FROTA}).
     * Na primeira vez (cache vazio) espera o cálculo; depois responde na hora.
     */
    public List<Object[]> doCliente(Long codcli) {
        Map<Long, List<Object[]>> dados = porCliente;
        if (dados == null) {
            dados = carregarAgora();
        } else if (System.currentTimeMillis() - carregadoEm > VALIDADE_MS) {
            atualizarEmSegundoPlano();
        }
        return dados.getOrDefault(codcli, Collections.emptyList());
    }

    private synchronized Map<Long, List<Object[]>> carregarAgora() {
        if (porCliente == null) {
            guardar(carregador.carregar());
        }
        return porCliente;
    }

    private void atualizarEmSegundoPlano() {
        if (!atualizando.compareAndSet(false, true)) {
            return;   // já tem um cálculo em andamento
        }
        fila.submit(() -> {
            try {
                guardar(carregador.carregar());
            } catch (Exception e) {
                // banco ainda subindo, etc.: tenta de novo na próxima consulta
                System.out.println("Frota (cache): não foi possível atualizar agora (" + e.getMessage() + ")");
            } finally {
                atualizando.set(false);
            }
        });
    }

    private void guardar(Map<Long, List<Object[]>> novo) {
        porCliente = novo;
        carregadoEm = System.currentTimeMillis();
    }
}
