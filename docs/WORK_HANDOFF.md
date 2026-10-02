# IdleDex Companion — Handoff completo para ChatGPT Work

Atualizado em: 2026-10-02

## 1. Objetivo do projeto

Construir um aplicativo Android bonito, simples e intuitivo que abra o IdleDex no WebView e ajude a gerenciar a conta com automações seguras. O usuário não quer uma ferramenta técnica cheia de opções confusas: quer perfis prontos, telas claras e ações previsíveis.

Prioridade máxima: ações destrutivas ou financeiras (soltar, vender, usar item raro, evoluir, transferir, comprar etc.) devem ser fail-safe. Se o app não tiver certeza, NÃO deve executar a ação.

## 2. Repositório e branch que devem ser usados

Repositório público:
https://github.com/fillipe91/testecontrole

Clone:
https://github.com/fillipe91/testecontrole.git

Branch de desenvolvimento atual:
`idledex-companion-v1`

NÃO desenvolver na `main` sem solicitação explícita. Trabalhar na branch `idledex-companion-v1` ou criar uma branch filha para uma refatoração grande.

Projeto Android:
`idledex-mapper/`

Application ID atual:
`com.fillipe.idledexcompanion`

Namespace Java:
`com.fillipe.webmapper`

Versão atual:
`2.1.0` / `versionCode 23`

## 3. Arquivos principais

- `idledex-mapper/app/src/main/java/com/fillipe/webmapper/CompanionHomeActivity.java`
  - Tela inicial do Companion.
  - Entrada para o app principal e para Venda Segura.
  - Botão de atualização.

- `idledex-mapper/app/src/main/java/com/fillipe/webmapper/MainActivity.java`
  - Companion principal em WebView.
  - Box, regras, atalhos e lógica anterior de análise/soltura.

- `idledex-mapper/app/src/main/java/com/fillipe/webmapper/SafeSellActivity.java`
  - Implementação atual do modo Venda Segura.
  - IMPORTANTE: compila, mas a auditoria da Box está com bugs e NÃO deve ser considerada confiável ainda.

- `idledex-mapper/app/src/main/java/com/fillipe/webmapper/SaleSafetyRules.java`
  - Motor de proteção de venda.
  - Mantém as proteções fortes em Java e não apenas no JS/WebView.

- `idledex-mapper/app/src/main/java/com/fillipe/webmapper/SafetyRules.java`
  - Regras do Companion principal.

- `idledex-mapper/app/src/main/java/com/fillipe/webmapper/AppUpdater.java`
  - Consulta manifesto remoto e abre o APK novo para atualização.

- `idledex-mapper/app/src/main/AndroidManifest.xml`
  - Launcher atual é `CompanionHomeActivity`.

- `idledex-mapper/app/build.gradle`
  - compileSdk 35
  - minSdk 24
  - targetSdk 29
  - Java 17
  - versionCode 23
  - versionName 2.1.0

- `.github/workflows/idledex-mapper-apk.yml`
  - GitHub Actions compila APK release NÃO assinado.
  - Branch monitorada: `idledex-companion-v1`.
  - Artifact: `idledex-companion-v2-unsigned`.

- `releases/idledex-companion-update.json`
  - Manifesto lido pelo atualizador do app.

## 4. Último build confirmado

GitHub Actions run:
`36945022561`

Commit do código do modo Venda Segura que passou no build:
`ffa00d9e4bd82c7c504a48d8752e9ecceb126723`

Build do GitHub Actions: SUCCESS.

O manifesto de atualização foi atualizado depois para apontar para a v2.1.0.

## 5. APK atual e mapa estrutural

APK v2.1.0 no Google Drive:
https://drive.google.com/file/d/1smIAP46DA-brmiBwxbYSZVRlwZO2_phY/view?usp=drivesdk

Mapa profundo da interface IdleDex:
https://drive.google.com/file/d/1acDzs3fy06HmML-mRFIf4nzo2Vx4DRpF/view?usp=drivesdk

Nome do arquivo do mapa:
`idledex-deep-map-v1.json`

Esse mapa foi criado após navegação autenticada somente para leitura e documenta a estrutura observada do IdleDex: mapa principal, AUTO, Box/equipe, Pokémon, Pokédex, Inventário, Loja/Marketplace, Caçada, configurações, etc. Ele é referência estrutural, mas NÃO substitui nova inspeção da interface ao vivo, porque o site pode ter mudado ou os seletores podem ser diferentes no DOM real.

Site do jogo:
https://idledex.com/play

Wiki oficial para dados estáticos do jogo:
https://wiki.idledex.com/

## 6. Estado atual da Venda Segura

A v2.1.0 implementou uma primeira tentativa de Venda Segura, mas o usuário testou e relatou:

> “Ele não tá auditando box certinha, tem vários bugs.”

Portanto NÃO assumir que `SafeSellActivity.java` está correta só porque compila.

Problemas prováveis/áreas frágeis da implementação atual que precisam ser verificadas no site ao vivo:

- descoberta de cartões pelo texto exato `Detalhes`;
- uso de `parentElement.innerText` como assinatura do cartão;
- `modalRoot()` genérico pode escolher modal/painel errado;
- `semanticId()` depende de atributos como `data-pokemon-id`, `data-creature-id`, `data-instance-id`, `data-pokemon-uid`, `data-uid`, que podem não existir ou estar em outro nó;
- `collectTeams()` tenta achar headings como `Equipe`, `Equipe ativa`, `Party`, `Times salvos`, `Equipes salvas`, `Saved teams`; isso pode não corresponder à UI real;
- `parseStars()` tenta inferir estrelas de atributos, texto ou caracteres `★`, podendo confundir outras estrelas presentes no painel;
- a captura do nome da espécie por headings é heurística;
- a paginação da Box e o fechamento de detalhes são heurísticos;
- a ligação entre o Pokémon visto na Box e o item no painel `Vender` ainda não foi validada em aparelho real;
- o fluxo final de venda foi codificado com hipóteses de rótulos como `Vender`, `Anunciar`, `Confirmar` e precisa ser mapeado/testado;
- o app ainda não teve teste end-to-end real confiável do modo venda.

A prioridade do Work deve ser REMAPEAR o fluxo de Box/equipe/venda ao vivo antes de tentar ampliar a automação.

## 7. Requisitos de segurança obrigatórios da Venda Segura

O usuário quer vender somente Pokémon claramente fracos/excedentes. Estas proteções devem ser tratadas como HARD RULES. Se qualquer verificação falhar, cancelar.

NUNCA vender automaticamente:

1. Pokémon que esteja no time/equipe ativa.
2. Pokémon que esteja em QUALQUER time/equipe salva.
3. Shiny.
4. Lendário.
5. Mítico.
6. Ultra Beast/Ultracriatura.
7. Pokémon 4 estrelas ou 5 estrelas.
8. Pokémon de evento/especial.
9. Pokémon travado ou favoritado.
10. Pokémon com IV >= 135/186 por padrão (configurável para ser mais restritivo, nunca menos seguro sem confirmação clara).
11. Qualidade Excelente ou Excepcional.
12. Espécies protegidas pelo usuário; padrão atual inclui `Scyther` e `Scizor`.
13. Único exemplar de uma espécie.
14. Os 3 melhores exemplares de cada espécie por padrão.
15. Pokémon cuja identidade única não possa ser confirmada.
16. Pokémon cuja espécie, IV, estrelas, Shiny, raridade, time ou estado de trava/favorito não possam ser lidos com confiança.
17. Pokémon em que duas leituras consecutivas não coincidam.
18. Pokémon que não possa ser reencontrado pelo MESMO identificador imediatamente antes da confirmação final.

Regra-mãe:
**A ausência de evidência de que é valioso NÃO significa que pode ser vendido. O app precisa provar que é seguro vender.**

## 8. Fluxo de segurança desejado

O fluxo ideal é:

1. Abrir IdleDex e manter sessão local no WebView.
2. Ler primeiro todos os times ativos e salvos.
3. Criar um conjunto de IDs protegidos dos Pokémon usados nos times.
4. Auditar a Box inteira sem executar nenhuma ação.
5. Para cada Pokémon, capturar identificador estável + espécie + estrelas + IV + Shiny + raridade + evento + trava/favorito + outros campos relevantes.
6. Fazer duas leituras independentes do mesmo Pokémon.
7. Classificar como:
   - `PROTEGIDO`
   - `REVISAR`
   - `CANDIDATO À VENDA`
8. Mostrar ao usuário uma fila visual de candidatos ANTES de venda real.
9. O modo padrão sempre deve ser SIMULAÇÃO.
10. Para venda real, exigir ação explícita para armar por tempo limitado.
11. Antes de cada venda, reler todos os times e reencontrar exatamente o mesmo Pokémon por ID.
12. Reabrir o detalhe e refazer todas as proteções.
13. Se qualquer coisa tiver mudado, cancelar aquela venda.
14. Limitar a quantidade por ciclo (padrão atual: 3).
15. Manter botão grande `PARAR` que desarma tudo imediatamente.
16. Gerar log de auditoria e motivo para cada classificação.

## 9. O que o Work deve fazer AGORA

Objetivo imediato: corrigir a auditoria da Box e só depois corrigir o fluxo de venda.

Procedimento recomendado:

1. Abra `https://idledex.com/play` no navegador do Work.
2. Eu (usuário) faço o login manualmente quando necessário.
3. NÃO vender, soltar, comprar, evoluir, usar item ou executar outra ação destrutiva durante o mapeamento.
4. Inspecione profundamente a Box real e a área de Equipe/Times salvos.
5. Descubra seletores/atributos realmente estáveis e o identificador único real usado por cada Pokémon.
6. Abra diferentes Pokémon para verificar se a estrutura muda.
7. Verifique paginação, filtros, busca, detalhes, estrelas, IVs, Shiny, travado/favorito, raridade e evento.
8. Verifique como Pokémon de time aparecem na Box e se há IDs compartilhados entre time, Box e painel de venda.
9. Abra Mercado > Vender em modo somente leitura e mapeie como a lista de criaturas é construída e como o formulário identifica o Pokémon selecionado. Pare antes de qualquer botão que possa realmente anunciar/vender.
10. Compare a interface real com `idledex-deep-map-v1.json`.
11. Depois refatore `SafeSellActivity.java` para usar os seletores/IDs reais e reduzir heurísticas.
12. Se não existir um ID estável que permita provar identidade entre Box/time/venda, NÃO implemente venda automática com clique final. Nesse caso, deixar fila de revisão/manual até haver uma forma segura.
13. Melhorar a UI para mostrar claramente por que cada Pokémon foi protegido ou marcado para revisão/venda.
14. Compilar no GitHub Actions e corrigir todos os erros antes de entregar.
15. Não declarar o modo venda como seguro até validar primeiro em SIMULAÇÃO com a conta real.

## 10. Interface desejada

O app final deve ser muito intuitivo e bonito. Evitar telas técnicas.

Tela inicial sugerida:
- Jogar / Companion
- Venda Segura
- Automação
- Box
- Captura
- Mercado
- Pokédex
- Segurança
- Atualizações

Usar perfis simples:
- Segurança máxima
- Farm forte
- Caçar Shiny
- Personalizado

Configurações avançadas devem ficar escondidas em uma seção `Avançado`.

Para Venda Segura, mostrar cartões com:
- nome do Pokémon;
- estrelas;
- IV;
- qualidade;
- Shiny;
- time protegido ou não;
- motivo da decisão;
- selo verde/amarelo/vermelho.

Nunca deixar um botão de venda real parecer igual a um botão de auditoria/simulação.

## 11. Atualização do próprio app

O app possui botão `Verificar atualização`.

O atualizador lê:
`https://raw.githubusercontent.com/fillipe91/testecontrole/idledex-companion-v1/releases/idledex-companion-update.json`

Manifesto atual:
`releases/idledex-companion-update.json`

Ao publicar uma nova versão:
- aumentar `versionCode`;
- aumentar `versionName`;
- gerar o APK;
- assinar com a MESMA chave de release usada na v2.0.2/v2.1.0;
- enviar APK para o Drive;
- calcular SHA-256;
- atualizar `versionCode`, `versionName`, `apkUrl`, `sha256` e `notes` no manifesto.

O Android ainda pode exigir confirmação do usuário na tela de instalação; não tentar contornar proteção do sistema.

## 12. Assinatura do APK

A chave de assinatura NÃO está no repositório público e NÃO deve ser adicionada ao GitHub.

Ela está guardada na Library privada do usuário em:
- `/IdleDexCompanion/signing/idledex-companion-release.jks`
- `/IdleDexCompanion/signing/signing-password.txt`

Nunca imprimir a senha em logs, commits, mensagens públicas ou arquivos do repositório.

O workflow atual gera APK release não assinado. A assinatura final é feita fora do GitHub usando essa chave persistente, para que as versões possam atualizar por cima da v2.0.2/v2.1.0.

## 13. Estado conhecido do build Android

`app/build.gradle` atual:
- namespace `com.fillipe.webmapper`
- applicationId `com.fillipe.idledexcompanion`
- compileSdk 35
- minSdk 24
- targetSdk 29
- Java 17
- BuildConfig habilitado

O targetSdk 29 foi usado como solução de compatibilidade para sideload na versão atual. Se for modernizar targetSdk, faça isso conscientemente e valide instalação/assinatura/package installer em aparelho real antes de mudar a estratégia de release.

## 14. Histórico importante

Versões anteriores:
- v1.0.0: Companion inicial, foco em análise da Box.
- v2.0.0: painel novo, perfis, central de segurança e atualizador.
- v2.0.1/v2.0.2: correções de instalação/package/signature/sideload.
- v2.1.0: primeira implementação de Venda Segura, porém auditoria da Box está com bugs segundo teste real do usuário.

Não reaproveitar cegamente a lógica v2.1.0. O build passar não significa que os seletores do site estão corretos.

## 15. Critério de conclusão para a próxima versão

Só considerar a próxima versão pronta quando:

- auditoria da Box enumera corretamente Pokémon e páginas;
- identifica corretamente os Pokémon dos times ativos e salvos;
- diferencia com confiança Shiny, estrelas, IV, raridade, evento, trava/favorito;
- candidatos à venda são reproduzíveis em duas auditorias seguidas;
- nenhuma entrada com dados incompletos cai em candidato;
- a identidade do Pokémon é preservada entre Box e painel de venda;
- simulação mostra claramente o que aconteceria;
- nenhuma venda real é possível sem autorização explícita;
- qualquer divergência cancela a ação;
- build do GitHub Actions passa;
- instalação/atualização é testada no Android;
- o usuário testa SIMULAÇÃO antes de liberar venda real.

## 16. Instrução para o Work

Antes de editar código, leia esta documentação, abra a branch `idledex-companion-v1`, inspecione os arquivos principais e confira o mapa profundo. Depois faça o mapeamento ao vivo do IdleDex com login manual do usuário. Priorize corrigir a auditoria da Box e a identificação inequívoca dos Pokémon. Não execute transações destrutivas durante a investigação. Faça commits claros na branch e deixe o projeto compilando.
