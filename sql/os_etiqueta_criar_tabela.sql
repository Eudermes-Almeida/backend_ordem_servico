/* =====================================================================================
   ETIQUETA DO EQUIPAMENTO (QR CODE) - criação da tabela
   Banco: OSCLI35 (SQL Server). Pode rodar mais de uma vez: só cria se não existir.

   Cada OS que teve a etiqueta impressa ganha um CODIGO aleatório (12 letras/números).
   O QR Code da etiqueta abre https://app.somonitores.tec.br/e/CODIGO
   O número da OS nunca aparece no link (ninguém consegue "adivinhar" outra etiqueta).
   Para invalidar uma etiqueta: apagar a linha dela nesta tabela.
   ===================================================================================== */

IF OBJECT_ID('dbo.OS_ETIQUETA', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.OS_ETIQUETA (
        ID        BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_OS_ETIQUETA PRIMARY KEY,
        OS        BIGINT      NOT NULL,
        CODIGO    VARCHAR(20) COLLATE Latin1_General_CS_AS NOT NULL,
        CRIADO_EM DATETIME    NOT NULL CONSTRAINT DF_OS_ETIQUETA_CRIADO_EM DEFAULT GETDATE(),
        CONSTRAINT UQ_OS_ETIQUETA_OS     UNIQUE (OS),
        CONSTRAINT UQ_OS_ETIQUETA_CODIGO UNIQUE (CODIGO)
    );
END;

/* conferência */
SELECT COUNT(*) AS ETIQUETAS_CADASTRADAS FROM dbo.OS_ETIQUETA;
