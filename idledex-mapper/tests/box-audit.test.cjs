const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const driver=fs.readFileSync(__dirname+'/../app/src/main/assets/box-audit.js','utf8');
async function simulate(options={}){
 let page=1, selected='', pinned=false, round=1, reads=0;const events=[];
 const ids=()=>page===1?['a','b']:[options.duplicate?'a':'c'];
 const card=id=>({id,identitySource:'data-creature',species:id,speciesKey:id,stars:id==='a'?4:1,quality:'Regular',rarity:'common',shiny:false,locked:null,event:null,special:null,listed:false,inTeam:false,level:5,sprite:id});
 const detailButton={textContent:'Detalhes',click(){pinned=!pinned;}};
 const root={querySelectorAll(selector){if(selector==='button')return [detailButton];return ids().map(id=>({getAttribute:()=>id,click(){selected=id;}}));},querySelector(selector){if(selector.includes('Página anterior'))return {click(){page--;round=2;}};if(selector.includes('Próxima página'))return {disabled:false,click(){page++;}};if(selector.includes('.eb-slot.sel'))return {getAttribute:()=>selected};return null;}};
 const R={box:()=>root,page:()=>({number:page,pages:2,total:3,ids:ids()}),teams:()=>({active:[],saved:[],complete:false}),assertAutoPaused(){if(options.auto)throw Error('AUTO ligado');},assertUnfiltered(){},card,detail(id){reads++;if(options.detailError)throw Error('Detalhe inválido');return {...card(id),locked:false,iv:options.changedIV&&round===2?99:80,ivs:[10,10,10,10,20,20],nature:'Hardy',detailVerified:true};},signature:p=>JSON.stringify([p.id,p.iv,p.locked,p.detailVerified])};
 const context={window:{IdleBoxReader:R},location:{origin:'https://idledex.com',pathname:'/play'},document:{querySelector:s=>s==='.eb-modal'?root:pinned?{}:null},IdleSell:{isStopped:()=>!!options.cancel,report:(token,kind,json)=>events.push({kind,...JSON.parse(json)})},setTimeout:fn=>fn()};
 vm.runInNewContext(driver,context);await context.window.IdleBoxAudit.start('test');return {events,reads,complete:events.find(e=>e.kind==='complete')};
}
test('two-page simulation compares cards separately from enriched details',async()=>{const s=await simulate();assert.equal(s.complete.complete,true);assert.equal(s.complete.rows.length,3);assert.ok(s.complete.rows.every(r=>r.consistent));assert.equal(s.reads,4);});
test('changed IV in second reading never counts as consistent',async()=>{const s=await simulate({changedIV:true});assert.equal(s.complete.rows.find(r=>r.pokemon.id==='b').consistent,false);});
test('duplicate identity aborts the collection',async()=>{const s=await simulate({duplicate:true});assert.equal(s.complete,undefined);assert.ok(s.events.some(e=>e.kind==='error'));});
test('detail failure remains uncertain',async()=>{const s=await simulate({detailError:true});assert.equal(s.complete.rows.find(r=>r.pokemon.id==='b').consistent,false);});
test('AUTO running aborts before reading',async()=>{const s=await simulate({auto:true});assert.equal(s.complete,undefined);assert.equal(s.reads,0);});
test('stop aborts before reading',async()=>{const s=await simulate({cancel:true});assert.equal(s.complete,undefined);assert.equal(s.reads,0);});
