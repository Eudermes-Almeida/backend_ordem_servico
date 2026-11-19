/* =====================================================================================
   NOBREAKS COM BANCO DE BATERIAS VENCIDO
   Versão validada em 18/11/2026 (sessão com Claude Code). Banco: OSCLI35 (SQL Server).

   REGRAS DE NEGÓCIO
   - Passagem válida (= nobreak consertado): OS com STATUS = 'FECHADA', FECHAMENTO preenchido
     e TOTALIQ > 0. Mesmo que a bateria não tenha sido trocada nessa OS, já foi trocada antes,
     então conta como referência para os 24 meses.
   - Vale só a ÚLTIMA passagem válida de cada equipamento; as anteriores são ignoradas.
   - Vencido: última manutenção há @meses (24) ou mais E há menos de @anosLimite (6) anos
     (acima disso considera-se que as baterias já foram trocadas por outro meio).
   - Identificação do equipamento:
       * com serial -> pelo serial, comparado entre TODOS os clientes (se o nobreak passou
         depois por outro cliente, aparece só nesse outro cliente);
       * sem serial / serial genérico / anotação no serial -> cliente + marca + potência.
     Etiquetas internas como 'ADM 4', 'ADM 5', 'ADM NOBREAK 01' CONTAM como serial.
   - Potência: o texto do MODELO é convertido em VA (ex.: '10 KVA' = 10000) para filtrar faixa.
   - @codcli = NULL pesquisa todos os clientes.

   ATENÇÃO AO COLAR NO SSMS: usar apenas comentários /* */ (comentários com dois traços
   quebravam o script quando as quebras de linha se perdiam ao colar).

   - Serial com texto colado (19/11/2026): se o serial começa com 6 ou mais números e logo
     depois vem texto (3 ou mais letras seguidas), vale só a parte numérica do começo.
     Ex.: '123456789012EMAIL@EXEMPLO.COM' -> '123456789012'.
     Sufixos curtos de fábrica continuam: '006244-B', '99195211MX', '1983700174E2'.
     Código de barras EAN-13 ('789' + 10 números) não é cortado (é código do produto).

   PENDÊNCIAS CONHECIDAS
   - Cliente com vários nobreaks iguais sem serial: são agrupados como um só (cliente+marca+potência).
   ===================================================================================== */

/* ===================== PARÂMETROS ===================== */
DECLARE @codcli           BIGINT = NULL;   /* NULL = todos os clientes | ex.: 219 = só esse cliente */
DECLARE @meses            INT    = 24;     /* idade mínima: bateria vencida */
DECLARE @anosLimite       INT    = 6;      /* idade máxima: acima disso, já foi trocada por fora */
DECLARE @potenciaMinimaVA INT    = 10000;  /* ex.: 5000 = 5 KVA e maiores (NULL = sem limite) */
DECLARE @potenciaMaximaVA INT    = NULL;   /* ex.: 10000 = até 10 KVA (NULL = sem limite) */
/*  500 VA=500 | 600 VA=600 | 700 VA=700 | 800 VA=800 | 1 KVA=1000 | 1,2 KVA=1200 | 1,3 KVA=1300
    1,4 KVA=1400 | 1,5 KVA=1500 | 1,8 KVA=1800 | 2 KVA=2000 | 2,2 KVA=2200 | 2,4 KVA=2400
    3 KVA=3000 | 3,2 KVA=3200 | 3,5 KVA=3500 | 3/4 HP=3750 | 4 KVA=4000 | 5 KVA=5000
    6 KVA=6000 | 8 KVA=8000 | 10 KVA=10000 | 12 KVA=12000 | 15 KVA=15000 | 20 KVA=20000 | 30 KVA=30000 */

DECLARE @hoje             DATE = CAST(GETDATE() AS DATE);
DECLARE @manutencaoAte    DATE = DATEADD(MONTH, -@meses,      @hoje);
DECLARE @manutencaoDepois DATE = DATEADD(YEAR,  -@anosLimite, @hoje);

/* 1º RESULTADO: filtros aplicados */
SELECT
    ISNULL(CAST(@codcli AS VARCHAR(20)), 'TODOS')                AS CLIENTE,
    @manutencaoDepois                                            AS MANUTENCAO_DEPOIS_DE,
    @manutencaoAte                                               AS MANUTENCAO_ATE,
    ISNULL(CAST(@potenciaMinimaVA AS VARCHAR(10)), 'SEM LIMITE') AS POTENCIA_MIN_VA,
    ISNULL(CAST(@potenciaMaximaVA AS VARCHAR(10)), 'SEM LIMITE') AS POTENCIA_MAX_VA;

/* 2º RESULTADO: nobreaks com banco de baterias vencido */
WITH base AS (
    SELECT
        o.OS, o.CODCLI, o.FANTASIA, o.MARCA, o.MODELO, o.SERIAL, o.PATRIMONIO,
        o.STATUS, o.FECHAMENTO, o.TOTALIQ,
        UPPER(LTRIM(RTRIM(ISNULL(o.MARCA, '')))) AS MARCA_NORM,
        m.MODELO_COMPACTO,
        CASE
            WHEN m.MODELO_COMPACTO = '1/2HP'    THEN 3600
            WHEN m.MODELO_COMPACTO = '3/4HP'    THEN 3750
            WHEN m.MODELO_COMPACTO LIKE '%KVA%' THEN CASE WHEN n.NUM > 100 THEN n.NUM ELSE n.NUM * 1000 END
            WHEN m.MODELO_COMPACTO LIKE '%VA%'  THEN n.NUM
            WHEN n.NUM IS NOT NULL              THEN CASE WHEN n.NUM < 100 THEN n.NUM * 1000 ELSE n.NUM END
        END AS POTENCIA_VA,
        UPPER(REPLACE(REPLACE(REPLACE(REPLACE(LTRIM(RTRIM(ISNULL(o.SERIAL, ''))),
              ' ', ''), '/', ''), '-', ''), '.', '')) AS SERIAL_LIMPO,
        CASE WHEN o.STATUS = 'FECHADA'
               AND o.FECHAMENTO IS NOT NULL
               AND TRY_CONVERT(DECIMAL(18,2), REPLACE(CAST(o.TOTALIQ AS VARCHAR(30)), ',', '.')) > 0
             THEN 1 ELSE 0 END AS VALIDA
    FROM dbo.[OS$] o
    CROSS APPLY (SELECT UPPER(REPLACE(REPLACE(LTRIM(RTRIM(ISNULL(o.MODELO, ''))), ' ', ''), '.', ',')) AS MODELO_COMPACTO) m
    CROSS APPLY (SELECT CASE WHEN m.MODELO_COMPACTO LIKE '%HP%' THEN NULL
                             ELSE TRY_CONVERT(DECIMAL(12,3),
                                  REPLACE(REPLACE(REPLACE(m.MODELO_COMPACTO, 'KVA', ''), 'VA', ''), ',', '.'))
                        END AS NUM) n
    WHERE o.EQUIPAMENTO = 'NOBREAK'
),
identificados AS (
    SELECT
        b.*,
        s.SERIAL_CHAVE,
        ISNULL(CAST(CAST(b.POTENCIA_VA AS INT) AS VARCHAR(20)), b.MODELO_COMPACTO) AS MODELO_CHAVE,
        CASE
            WHEN s.SERIAL_CHAVE IN ('', 'SN', 'NA', 'NT', 'X', 'XX', 'XXX')
              OR s.SERIAL_CHAVE NOT LIKE '%[^0]%'
              OR s.SERIAL_CHAVE LIKE '%NAO%'
              OR s.SERIAL_CHAVE LIKE '%NÃO%'
              OR s.SERIAL_CHAVE LIKE '%INFO%'
              OR s.SERIAL_CHAVE LIKE '%ILEGIVEL%'
              OR s.SERIAL_CHAVE LIKE 'SEM%'
              OR s.SERIAL_CHAVE LIKE '%VERIFICAR%'
              OR s.SERIAL_CHAVE LIKE '%TROCADO%'
              OR s.SERIAL_CHAVE LIKE '%BATERIA%'
              OR s.SERIAL_CHAVE LIKE 'LOJA%'
            THEN 0 ELSE 1
        END AS TEM_SERIAL
    FROM base b
    CROSS APPLY (SELECT CASE WHEN b.SERIAL_LIMPO LIKE '[0-9][0-9][0-9][0-9][0-9][0-9]%'
                              AND SUBSTRING(b.SERIAL_LIMPO, PATINDEX('%[^0-9]%', b.SERIAL_LIMPO), 3) LIKE '[A-Z][A-Z][A-Z]'
                              AND NOT (b.SERIAL_LIMPO LIKE '789%' AND PATINDEX('%[^0-9]%', b.SERIAL_LIMPO) = 14)
                             THEN LEFT(b.SERIAL_LIMPO, PATINDEX('%[^0-9]%', b.SERIAL_LIMPO) - 1)
                             ELSE b.SERIAL_LIMPO
                        END AS SERIAL_CHAVE) s
),
chaveados AS (
    SELECT
        i.*,
        CASE WHEN i.TEM_SERIAL = 1
             THEN 'S|' + i.SERIAL_CHAVE
             ELSE 'M|' + CAST(i.CODCLI AS VARCHAR(20)) + '|' + i.MARCA_NORM + '|' + i.MODELO_CHAVE
        END AS CHAVE_EQUIP
    FROM identificados i
),
ultima AS (
    SELECT
        c.*,
        ROW_NUMBER() OVER (PARTITION BY c.CHAVE_EQUIP
                           ORDER BY c.VALIDA DESC, c.FECHAMENTO DESC, c.OS DESC) AS rn,
        SUM(c.VALIDA) OVER (PARTITION BY c.CHAVE_EQUIP)                          AS QT_MANUTENCOES,
        COUNT(*)      OVER (PARTITION BY c.CHAVE_EQUIP)                          AS QT_PASSAGENS_TOTAL,
        COUNT(*)      OVER (PARTITION BY c.CODCLI, c.MARCA_NORM, c.MODELO_CHAVE) AS QT_PASSAGENS_MODELO
    FROM chaveados c
)
SELECT
    u.OS                                 AS ULTIMA_OS,
    u.CODCLI,
    u.FANTASIA,
    u.MARCA,
    u.MODELO,
    CAST(u.POTENCIA_VA AS INT)           AS POTENCIA_VA,
    u.SERIAL,
    u.PATRIMONIO,
    CASE WHEN u.TEM_SERIAL = 1 THEN 'SERIAL' ELSE 'MARCA+MODELO' END AS IDENTIFICADO_POR,
    u.FECHAMENTO                         AS ULTIMA_MANUTENCAO,
    u.TOTALIQ                            AS VALOR_ULTIMA_OS,
    DATEDIFF(MONTH, u.FECHAMENTO, @hoje) AS MESES_DESDE_MANUTENCAO,
    u.QT_MANUTENCOES,
    u.QT_PASSAGENS_TOTAL,
    u.QT_PASSAGENS_MODELO
FROM ultima u
WHERE u.rn = 1
  AND u.VALIDA = 1
  AND (@codcli IS NULL OR u.CODCLI = @codcli)
  AND u.FECHAMENTO < DATEADD(DAY, 1, @manutencaoAte)
  AND u.FECHAMENTO > @manutencaoDepois
  AND (@potenciaMinimaVA IS NULL OR u.POTENCIA_VA >= @potenciaMinimaVA)
  AND (@potenciaMaximaVA IS NULL OR u.POTENCIA_VA <= @potenciaMaximaVA)
ORDER BY u.FANTASIA, u.FECHAMENTO ASC;
