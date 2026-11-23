/* =====================================================================================
   LINK DO CLIENTE (campanha "Interação por WhatsApp") - criação da tabela
   Banco: OSCLI35 (SQL Server). Pode rodar mais de uma vez: só cria se não existir.

   Cada cliente que recebeu a mensagem ganha um CODIGO aleatório fixo (12 letras/números).
   O link da mensagem abre <site>/c/CODIGO = status das baterias de todos os nobreaks do cliente.
   O código do cliente nunca aparece no link (ninguém consegue "adivinhar" outro cliente).
   Para invalidar um link: apagar a linha dele nesta tabela (na próxima mensagem sai um novo).
   ===================================================================================== */

IF OBJECT_ID('dbo.CLIENTE_LINK', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.CLIENTE_LINK (
        ID        BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_CLIENTE_LINK PRIMARY KEY,
        CODCLI    BIGINT      NOT NULL,
        CODIGO    VARCHAR(20) COLLATE Latin1_General_CS_AS NOT NULL,
        CRIADO_EM DATETIME    NOT NULL CONSTRAINT DF_CLIENTE_LINK_CRIADO_EM DEFAULT GETDATE(),
        CONSTRAINT UQ_CLIENTE_LINK_CODCLI UNIQUE (CODCLI),
        CONSTRAINT UQ_CLIENTE_LINK_CODIGO UNIQUE (CODIGO)
    );
END;

/* conferência */
SELECT COUNT(*) AS LINKS_CADASTRADOS FROM dbo.CLIENTE_LINK;
