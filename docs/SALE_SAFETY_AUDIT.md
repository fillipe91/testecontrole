# IdleDex Companion 2.2.0 — auditoria somente leitura

## Interface observada

Na interface autenticada de 2026-10-02, a Box tem `326/400` no início da inspeção e mostra 327 cartões ao fim enquanto a coleta automática estava ocorrendo. São 18 cartões por página e 19 páginas naquele estado. Filtros: busca; ordem; Shiny; Região, Nature, Tipo, Evolução, Cadeado, Evento, Raridade, Qualidade e IV mínimo. Uma auditoria precisa da Box inteira, sem filtros nem busca, e falha se o total ou qualquer página mudar.

Cartões, slots de equipe e itens têm `<button data-creature="…">`; esse ID é a identidade individual confirmada na Box e na equipe ativa. `data-dropbox` distingue cartões da Box; ausência desse atributo no slot, dentro de `EQUIPE & BOX`, indica slot ativo. O cartão contém `title`/`.eb-name`, `.eb-sprite` (número da espécie na URL), `.eb-mark-tl [data-stars]`, classes `rarity-*`, marcador `shiny`, e status `Travado pelo dono`, `Seu inicial` ou `À venda no mercado`. Detalhes abrem pelo botão `Detalhes` e aparecem como `.ct-tooltip[data-pinned=true]`; `.ct-meta` mostra IV total, `.ct-stats .ct-iv` os seis IVs e `.ct-nature` a Nature. A leitura só é aceita se espécie, espécie numérica, estrelas, nível e soma dos seis IVs conferirem.

O cartão dos times salvos (`.preset-card`) expõe nomes/slots e sprites, mas nenhum ID individual `data-creature`. Um slot pode indicar `À venda no mercado`, sem revelar qual instância foi anunciada. Não se presume identidade por espécie, posição do slot ou assinatura textual.

A tela Loja > Vender é mercado entre jogadores. Os cartões `.market-card` exibem espécie, nível, raridade, IV/Nature e botões `Detalhes`/`Vender`; no DOM observado não expõem `data-creature`. Esse identificador não pode ser correlacionado com a Box pelo cartão do mercado.

O NPC Jessie, no Laboratório do Professor, compra por silver nas categorias comum (5), robusto (10), formidável (25) e pseudo-lendário (50). Jessie mostra cartões `.pick-cell` com nome, nível, qualidade, raridade e IV, mas não expõe `data-creature`. Há filtro de nome, ordenação, nota máxima, IV até, paginação, marcar página/tudo e um botão de venda que consome a seleção. Em 215 cartões comuns foram observadas 12 páginas; robusto tinha 66 e formidável 35. A lista permite selecionar qualidade excelente, então filtro/critério visual do NPC não constitui salvaguarda suficiente. Os itens não foram marcados nem vendidos.

## Decisão de segurança

A interface confirma um ID confiável dentro de Box/equipe, mas não em times salvos, Mercado ou Jessie. A versão mantém a análise como somente leitura: nenhum fluxo seleciona ou vende no NPC. As proteções ausentes (favorito/evento/especial em alguns cartões) permanecem desconhecidas, não falsas. Sem identidade entre superfícies, todos os casos sem motivo de proteção explícito são `REVISAR`; um candidato pode surgir apenas com IDs verificáveis em todos os destinos e todas as leituras completas.

`SaleSafetyRules.finalGate()` nega sempre venda real até existir contrato de identidade verificável no NPC e nos times salvos. O Companion legado também mantém a soltura desativada enquanto a mesma lacuna existir. Simulação começa sempre ativa e a versão 2.1.0 não pode conservar estado `armed` após atualização.
