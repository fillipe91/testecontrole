const {test}=require('node:test');const assert=require('node:assert/strict');const vm=require('node:vm');const fs=require('node:fs');
const source=fs.readFileSync(__dirname+'/../app/src/main/assets/box-reader.js','utf8');
function reader(nodes){const context={module:{exports:{}},document:{querySelectorAll:()=>nodes}};vm.runInNewContext(source,context);return context.module.exports.IdleBoxReader;}
function control(label,attrs={},props={}){return {tagName:'BUTTON',getAttribute:name=>name==='aria-label'?label:attrs[name]??null,...props};}
test('paused resume action is recognized',()=>assert.equal(reader([control('Ativar modo automático')]).assertAutoPaused(),true));
test('native unchecked checkbox with old label is recognized',()=>assert.equal(reader([control('Desativar modo automático',{}, {tagName:'INPUT',type:'checkbox',checked:false})]).assertAutoPaused(),true));
test('ARIA false confirms paused',()=>assert.equal(reader([control('Desativar modo automático',{'aria-checked':'false'})]).assertAutoPaused(),true));
test('running checkbox blocks audit',()=>assert.throws(()=>reader([control('Desativar modo automático',{'aria-checked':'true'})]).assertAutoPaused(),/AUTO está ligado/));
test('missing or ambiguous control is unknown, never paused',()=>{assert.equal(reader([]).autoState(),'unknown');assert.equal(reader([control('Ativar modo automático'),control('Ativar modo automático')]).assertAutoPaused(),false);});
test('missing checked attributes never means paused',()=>assert.equal(reader([control('Desativar modo automático')]).assertAutoPaused(),false));
test('contradictory states remain unknown',()=>assert.equal(reader([control('Ativar modo automático',{'aria-checked':'true'})]).autoState(),'unknown'));
