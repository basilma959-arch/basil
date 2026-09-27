(()=>{
  function installImportTopButton(){
    const actions=document.querySelector('.topbar-actions');
    if(!actions||document.getElementById('topImportBtn'))return;
    const calc=actions.querySelector('.top-calc-btn');
    const btn=document.createElement('button');
    btn.type='button';
    btn.id='topImportBtn';
    btn.className='top-calc-btn top-import-btn';
    btn.title='استيراد المواد';
    btn.innerHTML='<span>⇩</span> استيراد المواد';
    btn.addEventListener('click',()=>{
      const nav=document.querySelector('.nav button[data-section="importer"]');
      if(nav)nav.click();
    });
    if(calc)actions.insertBefore(btn,calc);else actions.prepend(btn);
  }

  function installStyles(){
    if(document.getElementById('alBasilUiPatchStyle'))return;
    const style=document.createElement('style');
    style.id='alBasilUiPatchStyle';
    style.textContent=`
      .topbar-actions{display:flex;align-items:center;gap:9px;flex-wrap:wrap;justify-content:flex-end}
      .top-import-btn{background:linear-gradient(135deg,#1d4ed8,#0f766e)!important;box-shadow:0 5px 15px rgba(29,78,216,.24)!important}
      .top-import-btn:hover{transform:translateY(-1px)}
      @media(max-width:640px){.topbar-actions{gap:6px}.top-import-btn,.top-calc-btn{padding:9px 10px!important;font-size:11px!important}}
    `;
    document.head.appendChild(style);
  }

  function init(){installStyles();installImportTopButton();}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
})();
