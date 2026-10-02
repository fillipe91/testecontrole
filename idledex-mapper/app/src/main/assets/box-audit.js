/* Read-only audit driver. Strict click allowlist: Box, filters, card, details, pager. */
(function(){
  'use strict';
  if(location.origin!=='https://idledex.com' || location.pathname!=='/play')return;
  if(window.IdleBoxAudit)return;
  const R=window.IdleBoxReader, sleep=ms=>new Promise(r=>setTimeout(r,ms));
  let running=false, cancelled=false, token='';
  const check=()=>{if(cancelled||IdleSell.isStopped(token))throw Error('Auditoria parada. Resultados parciais não autorizam venda.');};
  const send=(kind,payload)=>IdleSell.report(token,kind,JSON.stringify(payload));
  async function waitFor(fn,message){for(let i=0;i<40;i++){check();try{if(fn())return;}catch(e){}await sleep(100);}throw Error(message);}
  function details(){const b=[...R.box().querySelectorAll('button')].filter(e=>e.textContent.trim()==='Detalhes');if(b.length!==1)throw Error('Ação Detalhes ambígua.');return b[0];}
  async function read(id){
    check();const e=[...R.box().querySelectorAll('.eb-boxdrop button[data-creature]')].find(e=>e.getAttribute('data-creature')===id);
    if(!e)throw Error('ID mudou ou desapareceu.');
    if(document.querySelector('.ct-tooltip[data-pinned="true"]')){details().click();await sleep(120);}
    e.click();await waitFor(()=>R.box().querySelector('.eb-slot.sel')?.getAttribute('data-creature')===id,'Seleção não confirmou o ID.');
    details().click();await waitFor(()=>!!document.querySelector('.ct-tooltip[data-pinned="true"]'),'Detalhe não abriu.');await sleep(180);
    const p=R.detail(id);details().click();await waitFor(()=>!document.querySelector('.ct-tooltip[data-pinned="true"]'),'Detalhe não fechou.');return p;
  }
  async function firstPage(){for(let i=0;i<250 && R.page().number>1;i++){check();let n=R.page().number;R.box().querySelector('[aria-label="Página anterior"]').click();await waitFor(()=>R.page().number===n-1,'Página anterior não respondeu.');}if(R.page().number!==1)throw Error('Primeira página não confirmada.');}
  async function pass(round){
    R.assertAutoPaused();await firstPage();R.assertUnfiltered();const initial=R.page(), team=R.teams(), rows=[], ids=new Set();
    const add=async id=>{if(ids.has(id))throw Error('ID repetido entre páginas/equipe.');ids.add(id);check();
      let p;try{p=await read(id);}catch(e){check();p=R.card(id);p.readError=String(e.message||e);}
      rows.push(p);send('item',{round,pokemon:p});};
    const pageSize=initial.ids.length;
    if(initial.total>0 && pageSize===0)throw Error('Box informa Pokémon, mas não mostra cartões na primeira página.');
    if(initial.ids.some(id=>!id) || new Set(initial.ids).size!==initial.ids.length)throw Error('ID ausente ou repetido nos cartões da Box.');
    for(let n=1;n<=initial.pages;n++){
      check();R.assertAutoPaused();R.assertUnfiltered();const current=R.page();
      if(current.number!==n || current.total!==initial.total || current.pages!==initial.pages)throw Error('Coleção mudou durante a leitura. Refaça a auditoria.');
      const expected=Math.min(pageSize,initial.total-(n-1)*pageSize);
      if(current.ids.length!==expected)throw Error('Quantidade de cartões diverge da página.');
      if(current.ids.some(id=>!id) || new Set(current.ids).size!==current.ids.length)throw Error('ID ausente ou repetido nos cartões da Box.');
      for(const id of current.ids)await add(id);
      send('progress',{round,page:n,pages:initial.pages,total:rows.length});
      if(n<initial.pages){const next=R.box().querySelector('[aria-label="Próxima página"]');if(!next||next.disabled)throw Error('Paginação terminou antes do total.');next.click();await waitFor(()=>R.page().number===n+1,'Próxima página não respondeu.');}
    }
    const end=R.page();
    if(end.total!==initial.total || rows.length!==initial.total || team.active.some(id=>!ids.has(id)) || JSON.stringify(team)!==JSON.stringify(R.teams()))
      throw Error('Box mudou ou um Pokémon da equipe ativa não corresponde a um ID da Box.');
    return {rows,team,total:initial.total,pages:initial.pages};
  }
  async function start(runToken){
    if(running)return;running=true;cancelled=false;token=runToken;
    try{
      R.assertAutoPaused();
      if(!document.querySelector('.eb-modal')){const b=[...document.querySelectorAll('button')].filter(e=>e.textContent.trim()==='Box');if(b.length!==1)throw Error('Abra a Box manualmente.');b[0].click();await waitFor(()=>!!document.querySelector('.eb-modal'),'Box não abriu.');}
      const filters=R.box().querySelector('[data-testid="creature-filters-toggle"]');if(filters?.getAttribute('aria-expanded')==='false'){filters.click();await sleep(150);}
      R.assertUnfiltered();send('status',{message:'Leitura 1 de 2 · verificando cada ID'});
      const a=await pass(1);send('status',{message:'Leitura 2 de 2 · reabrindo cada Pokémon'});const b=await pass(2);
      const first=new Map(a.rows.map(p=>[p.id,p]));
      const stable=a.total===b.total && a.rows.length===b.rows.length && JSON.stringify(a.team)===JSON.stringify(b.team) && b.rows.every(p=>first.has(p.id)&&R.signature(first.get(p.id))===R.signature(p));
      check();send('complete',{rows:b.rows.map(p=>({pokemon:p,consistent:!!first.has(p.id)&&R.signature(first.get(p.id))===R.signature(p)})),complete:stable,teams:b.team,pages:b.pages,total:b.total});
    }catch(e){send('error',{message:String(e.message||e)});}finally{running=false;}
  }
  window.IdleBoxAudit={start,stop:()=>{cancelled=true;}};
})();
