# Superciga — versão para teste no GitHub

Aplicativo local em Python/Tkinter para auxiliar na montagem de itens, importação de linhas/tabelas copiadas do i9 e geração de texto/script de conferência para cotação externa no CIGA Obras.

## Como testar no Windows

1. Baixe o repositório em **Code > Download ZIP**.
2. Extraia o ZIP.
3. Abra a pasta `Superciga`.
4. Dê duplo clique em `run_superciga_windows.bat`.

Ou rode pelo terminal:

```bat
cd Superciga
python main.py
```

## Dependências

O app usa bibliotecas padrão do Python para a versão leve. Para futuras importações XLSX/geração de EXE, instale:

```bat
python -m pip install -r requirements.txt
```

## Fluxo de uso

1. Abra o i9 e copie uma linha ou tabela.
2. No Superciga, clique em **Colar tabela/linha**.
3. Pesquise/selecione o item.
4. Use **Copiar item**, **Copiar código**, **Copiar descrição** ou gere o script na aba **CIGA / Script**.
5. Para orçamento local, informe grupo e quantidade e clique em **Adicionar**.

## Observação

Esta versão publicada no GitHub é uma versão executável de teste. O app salva dados localmente em `Superciga/data/superciga.db`, criado automaticamente ao rodar.
