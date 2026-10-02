/* DOM-only reader, observed 2026-10-02. No network, framework state or credentials. */
(function (scope) {
  'use strict';
  const text = e => e ? e.textContent.trim() : '';
  const visible = e => !!e && e.getBoundingClientRect().width > 0 && e.getBoundingClientRect().height > 0;
  function box() {
    const all = [...document.querySelectorAll('.eb-modal')].filter(visible);
    if (all.length !== 1) throw Error('Abra Equipe & Box. Painel único não encontrado.');
    return all[0];
  }
  function assertAutoPaused() {
    const toggles=[...document.querySelectorAll('[aria-label="Desativar modo automático"]')];
    if(toggles.length!==1) throw Error('Não foi possível confirmar o estado do AUTO. Pause-o manualmente antes da auditoria.');
    const e=toggles[0], checked=e.checked===true || e.getAttribute('aria-checked')==='true' || e.getAttribute('data-state')==='checked';
    if(checked) throw Error('O AUTO está ligado e pode mudar a Box durante a leitura. Pause-o manualmente e tente novamente.');
  }
  function page() {
    const root = box(), p = text(root.querySelector('.eb-pager-info')).match(/^(\d+)\s*\/\s*(\d+)$/);
    const cap = text([...root.querySelectorAll('.eb-title')].find(e => /^Box\s*\(/.test(text(e)))).match(/Box\s*\((\d+)\/(\d+)\)/);
    if (!cap) throw Error('Contagem total da Box não reconhecida.');
    const cards = [...root.querySelectorAll('.eb-boxdrop button[data-creature]')];
    if (!p && +cap[1] > 18) throw Error('Paginação não reconhecida.');
    return {number:p ? +p[1] : 1, pages:p ? +p[2] : 1, total:+cap[1], ids:cards.map(e=>e.getAttribute('data-creature'))};
  }
  function teams() {
    const root=box(), shelf=root.querySelector('.preset-shelf');
    const active=[...root.querySelectorAll('.eb-row:not(.eb-boxdrop) button[data-creature]')].map(e=>e.getAttribute('data-creature'));
    if(active.some(id=>!id) || new Set(active).size!==active.length) throw Error('IDs da equipe ativa ausentes ou repetidos.');
    const saved=shelf ? [...shelf.querySelectorAll('.preset-card')].map(c=>({
      name:text(c.querySelector('.preset-name')),
      sprites:[...c.querySelectorAll('.preset-sprite')].filter(e=>e.style.backgroundImage).map(e=>({title:e.title||'',sprite:e.style.backgroundImage})),
      missing:text(c.querySelector('.preset-missing'))
    })) : null;
    // The current saved-team DOM has NO instance IDs. Do not fabricate membership.
    return {active, saved, complete:false, reason:'Times salvos não expõem IDs individuais; vínculo não confirmado.'};
  }
  function card(id) {
    // data-creature is the stable instance ID and can be rendered once in the
    // active party and again in the Box. The Box card is the canonical record.
    const root=box(), matches=[...root.querySelectorAll('.eb-boxdrop button[data-creature]')].filter(e=>e.getAttribute('data-creature')===id);
    if(matches.length!==1 || !id) throw Error('ID ausente ou duplicado na página.');
    const e=matches[0], name=e.querySelector('.eb-name'), star=e.querySelector('.eb-mark-tl [data-stars]'), sprite=e.querySelector('.eb-sprite');
    const image=sprite?.style.backgroundImage||'', dex=image.match(/\/sprites\/poke\/(\d+)(s?)\.png/);
    const rarity=[...(name?.classList||[])].find(c=>/^rarity-(common|uncommon|rare|epic|pseudo|legendary|mythical|ultra)/.test(c));
    const flags=[...e.querySelectorAll('[aria-label],[title]')].map(n=>n.getAttribute('aria-label')||n.getAttribute('title')||'');
    return {id, identitySource:'data-creature', species:text(name), speciesKey:dex?dex[1]:null,
      sprite:image, stars:star && /^\d$/.test(star.getAttribute('data-stars')) ? +star.getAttribute('data-stars'):null,
      quality:star?.getAttribute('title')||null, rarity:rarity?.replace('rarity-','')||null,
      shiny:dex ? (e.classList.contains('shiny')||dex[2]==='s') : null,
      locked:flags.some(s=>/Travado pelo dono|Seu inicial|travado até/i.test(s))?true:null,
      favorite:null, event:null, special:null, // not observable: unknown is NOT false
      listed:flags.some(s=>s==='À venda no mercado'),
      inTeam:teams().active.includes(id), level:Number(text(e.querySelector('.eb-sub')).match(/^Lv(\d+)/)?.[1])||null,
      iv:null, ivs:null, nature:null, detailVerified:false, crossSurfaceIdentity:false};
  }
  function detail(id) {
    const p=card(id), root=box(), selected=[...root.querySelectorAll('.eb-slot.sel[data-creature]')];
    const tips=[...document.querySelectorAll('.ct-tooltip[data-pinned="true"]')].filter(visible);
    if(selected.length!==1 || selected[0].getAttribute('data-creature')!==id || tips.length!==1) throw Error('Detalhe não vinculado à seleção atual.');
    const tip=tips[0], n=tip.querySelector('.ct-namebox .rarity-name'), s=tip.querySelector('.ct-quality-head [data-stars]');
    const ivText=text(tip.querySelector('.ct-meta')), iv=ivText.match(/IVs\s*(\d+)\s*\/186/);
    const rows=[...tip.querySelectorAll('.ct-stats .ct-stat:not(.ct-stat-head)')];
    const ivs=rows.map(r=>Number(text(r.querySelector('.ct-iv'))));
    const level=Number(text(tip.querySelector('.ct-lv')).match(/Lv\s*(\d+)/)?.[1]);
    const labels=rows.map(r=>text(r.querySelector('.ct-stat-label')));
    if(text(n)!==p.species || Number(s?.getAttribute('data-stars'))!==p.stars || level!==p.level ||
       labels.join(',')!=='HP,Atk,Def,SpA,SpD,Spe' || ivs.some(v=>!Number.isInteger(v)||v<0||v>31) ||
       !iv || ivs.reduce((a,b)=>a+b,0)!==Number(iv[1])) throw Error('Atributos do detalhe não conferem com o cartão.');
    const actionNames=[...root.querySelectorAll('button')].map(text);
    if(actionNames.filter(t=>t==='Travar').length===1 && !actionNames.includes('Destravar')) p.locked=false;
    if(actionNames.filter(t=>t==='Destravar').length===1) p.locked=true;
    return {...p,iv:+iv[1],ivs,nature:text(tip.querySelector('.ct-nature')),detailVerified:true};
  }
  function signature(p) {
    return JSON.stringify([p.id,p.species,p.speciesKey,p.stars,p.quality,p.rarity,p.shiny,p.locked,p.favorite,p.event,p.special,p.listed,p.inTeam,p.iv,p.ivs,p.nature,p.detailVerified]);
  }
  function assertUnfiltered() {
    const root=box(), controls=root.querySelector('.eb-box-controls');
    if(!controls || [...controls.querySelectorAll('input')].some(e=>e.value.trim()) || controls.querySelector('.eb-shiny-toggle')?.getAttribute('aria-pressed')!=='false' ||
       controls.querySelector('[data-testid="bulk-toggle"]')?.getAttribute('aria-pressed')!=='false') throw Error('Limpe busca, Shiny e seleção múltipla antes de auditar.');
    const panel=controls.querySelector('[data-testid="creature-filters-panel"]');
    if(!panel) throw Error('Expanda Filtros para confirmar que a Box está sem filtros.');
    const filters=[...panel.querySelectorAll('[role="combobox"]')];
    if(filters.length!==9 || filters.some(e=>text(e.querySelector('.ui-select-value'))!=='Todas')) throw Error('Limpe todos os filtros antes de auditar.');
    if(text(controls.querySelector('[aria-label="Ordenar a Box"] .ui-select-value'))!=='Ordem da Box') throw Error('Escolha Ordem da Box antes de auditar.');
  }
  scope.IdleBoxReader={box,page,teams,card,detail,signature,assertUnfiltered,assertAutoPaused};
})(typeof module==='object' ? module.exports : window);
