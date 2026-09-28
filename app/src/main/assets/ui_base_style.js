(()=>{
  'use strict';

  let pendingItem=null;
  let pendingBarcode='';
  let baseProcess=null;
  let cameraReturnTimer=null;

  const $=(id)=>document.getElementById(id);
  const q=(sel,root=document)=>root.querySelector(sel);
  const qa=(sel,root=document)=>Array.from(root.querySelectorAll(sel));

  function safeRefresh(){
    try{ if(typeof refreshAll==='function') refreshAll(); }catch(e){}
    updateDashboardMeta();
  }
  function stopCam(){
    try{ if(typeof stopCamera==='function') stopCamera(); }catch(e){}
  }
  function title(txt){ const e=$('pageTitle'); if(e) e.textContent=txt||'AL BASIL'; }

  function showPage(id,label,opts={}){
    if(id!=='baseCameraPage' && !opts.keepCamera) stopCam();
    qa('.section,.base-page').forEach(el=>el.classList.remove('active'));
    const target=$(id);
    if(target) target.classList.add('active');
    title(label);
    safeRefresh();
    window.scrollTo({top:0,behavior:'smooth'});
  }
  window.alBasilShowPage=showPage;

  function addStyles(){
    if($('baseInspiredStyle')) return;
    const st=document.createElement('style');
    st.id='baseInspiredStyle';
    st.textContent=`
      :root{--ab-navy:#0b1830;--ab-teal:#0f766e;--ab-teal2:#14b8a6;--ab-blue:#1457b8;--ab-gold:#d4a84f;--ab-bg:#f3f6fa;--ab-line:#dfe5ed;--ab-ink:#1f2a3d;--ab-muted:#6c778a}
      html,body{background:var(--ab-bg)!important}.sidebar{display:none!important}.app{display:block!important}.main{max-width:none!important;width:100%!important;margin:0!important;padding:72px 12px 18px!important}
      .topbar{position:fixed!important;top:0!important;right:0!important;left:0!important;z-index:500!important;height:60px!important;margin:0!important;padding:0 12px!important;border-radius:0!important;border:0!important;background:linear-gradient(90deg,var(--ab-navy),#10284a)!important;box-shadow:0 3px 13px rgba(10,25,48,.28)!important;color:#fff!important;display:flex!important;align-items:center!important;justify-content:space-between!important}
      .topbar>div:first-child{display:flex;align-items:center;gap:10px}.topbar>div:first-child:before{content:'BM';width:38px;height:38px;display:grid;place-items:center;border-radius:10px;background:var(--ab-teal);border:1px solid var(--ab-gold);font:900 14px Arial;color:#fff}.topbar h1{font-size:18px!important;color:#fff!important;margin:0!important}.topbar .hint{display:none!important}.topmeta{display:none!important}.topbar-actions{display:flex!important;align-items:center!important;gap:6px!important;flex-wrap:nowrap!important}
      .top-calc-btn,.top-import-btn,.base-home-btn{min-height:38px!important;padding:8px 10px!important;border:1px solid rgba(255,255,255,.16)!important;border-radius:9px!important;background:rgba(255,255,255,.1)!important;color:#fff!important;box-shadow:none!important;font:700 12px Tahoma,Arial!important;display:inline-flex!important;align-items:center!important;gap:5px!important;white-space:nowrap!important;cursor:pointer}.top-import-btn{background:rgba(20,184,166,.22)!important}.base-home-btn{background:rgba(212,168,79,.18)!important}
      .section,.base-page{display:none!important}.section.active,.base-page.active{display:block!important}.base-page{max-width:1100px;margin:0 auto}
      .base-toolbar{min-height:52px;background:#fff;border:1px solid var(--ab-line);border-radius:10px 10px 0 0;padding:8px 10px;display:flex;align-items:center;justify-content:space-between;gap:10px;box-shadow:0 2px 7px rgba(15,23,42,.04)}.base-toolbar-title{display:flex;align-items:center;gap:9px;font-weight:800;color:var(--ab-ink);font-size:16px}.base-toolbar-icon{width:34px;height:34px;border-radius:7px;background:#e8f7f5;color:var(--ab-teal);display:grid;place-items:center;font-size:18px}.base-toolbar-actions{display:flex;gap:7px;flex-wrap:wrap}.base-action{border:0;border-radius:8px;min-height:38px;padding:8px 12px;font:700 12px Tahoma,Arial;cursor:pointer;background:#e9eef5;color:#334155}.base-action.primary{background:var(--ab-teal);color:#fff}.base-action.blue{background:var(--ab-blue);color:#fff}.base-action.gold{background:#fff5db;color:#8a5b00;border:1px solid #f1d38b}.base-action.bad{background:#fff0f0;color:#a51d1d}
      .base-panel{background:#fff;border:1px solid var(--ab-line);border-top:0;border-radius:0 0 10px 10px;padding:13px;margin-bottom:14px}.base-note{font-size:11px;color:var(--ab-muted);line-height:1.7}.base-section-title{font-size:13px;font-weight:800;color:#485468;margin:4px 0 8px}
      .base-home-hero{max-width:1100px;margin:0 auto 12px;background:#fff;border:1px solid var(--ab-line);border-radius:12px;overflow:hidden;box-shadow:0 3px 12px rgba(15,23,42,.05)}.base-home-head{background:linear-gradient(90deg,var(--ab-teal),#0e8d83);padding:15px 16px;color:#fff;display:flex;align-items:center;justify-content:space-between;gap:12px}.base-home-head h2{margin:0 0 3px;font-size:19px}.base-home-head p{margin:0;color:#d9fffb;font-size:11px}.base-home-logo{width:52px;height:52px;border-radius:13px;border:2px solid var(--ab-gold);display:grid;place-items:center;font:900 18px Arial;background:rgba(0,0,0,.12)}
      .base-module-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1px;background:var(--ab-line);border-top:1px solid var(--ab-line)}.base-module{background:#fff;min-height:82px;border:0;padding:13px 14px;display:flex;align-items:center;gap:13px;text-align:right;cursor:pointer;transition:.12s}.base-module:hover{background:#f8fbfd}.base-module-icon{width:46px;height:46px;flex:0 0 46px;border-radius:8px;background:#eef8f7;display:grid;place-items:center;color:var(--ab-teal);font-size:22px;border:1px solid #d7ebe8}.base-module.blue .base-module-icon{background:#eef5ff;color:var(--ab-blue);border-color:#d9e7fb}.base-module.gold .base-module-icon{background:#fff8e7;color:#9a6800;border-color:#f4e0ae}.base-module.dark .base-module-icon{background:#eef1f6;color:var(--ab-navy);border-color:#dde3eb}.base-module b{display:block;color:var(--ab-ink);font-size:14px;margin-bottom:3px}.base-module span{display:block;color:var(--ab-muted);font-size:10px;line-height:1.5}
      #dashboard{max-width:1100px;margin:0 auto}#dashboard>.grid4{grid-template-columns:repeat(4,minmax(0,1fr))!important;gap:8px!important;margin:0 0 10px!important}#dashboard>.grid4 .card{border-radius:9px!important;box-shadow:none!important;border:1px solid var(--ab-line)!important;padding:12px!important}#dashboard>.grid4 .v{font-size:21px!important;color:var(--ab-navy)}#dashboard>.card{border-radius:9px!important;box-shadow:none!important;border:1px solid var(--ab-line)!important;padding:12px!important}
      .base-form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.base-form-grid .field{background:#f8fafc;border:1px solid #e5eaf0;border-radius:8px;padding:9px}.base-form-grid input,.base-form-grid select{background:#fff!important}
      .base-method-list{display:grid;gap:8px}.base-method{width:100%;border:1px solid var(--ab-line);background:#fff;border-radius:9px;padding:13px;display:flex;align-items:center;gap:12px;text-align:right;cursor:pointer}.base-method:hover{border-color:#9bcfc9;background:#fbfefd}.base-method-icon{width:48px;height:48px;display:grid;place-items:center;border-radius:8px;background:#eaf8f6;color:var(--ab-teal);font-size:22px}.base-method:nth-child(2) .base-method-icon{background:#eef5ff;color:var(--ab-blue)}.base-method:nth-child(3) .base-method-icon{background:#fff5df;color:#9a6500}.base-method b{display:block;font-size:14px;color:var(--ab-ink);margin-bottom:3px}.base-method span{font-size:10px;color:var(--ab-muted)}
      .base-product-header{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-bottom:10px}.base-product-header .search{flex:1;min-width:220px}.base-product-header .group-search{min-width:180px}.table-wrap{border-radius:7px!important;border:1px solid var(--ab-line)!important;box-shadow:none!important}th{background:#eef2f6!important;color:#435066!important;font-size:11px!important}td{font-size:11px!important}tr:hover td{background:#f8fbfd!important}
      .base-floating-add{position:fixed;left:18px;bottom:18px;width:54px;height:54px;border-radius:50%;border:0;background:var(--ab-teal);color:#fff;font-size:27px;box-shadow:0 7px 20px rgba(15,118,110,.34);z-index:350;display:grid;place-items:center;cursor:pointer}
      .base-session-meta{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}.base-session-meta .field{background:#f8fafc;border:1px solid #e5eaf0;border-radius:8px;padding:9px}.base-summary-strip{display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin-top:10px}.base-summary-cell{background:#f8fafc;border:1px solid #e5eaf0;border-radius:8px;padding:10px}.base-summary-cell span{font-size:10px;color:var(--ab-muted);display:block}.base-summary-cell b{font-size:16px;color:var(--ab-navy);display:block;margin-top:3px}
      .base-camera-wrap{max-width:680px;margin:0 auto}.base-camera-wrap #cameraScanner{display:block!important;margin:0!important;border:1px solid var(--ab-line)!important;border-radius:10px!important;box-shadow:none!important;background:#fff!important}.base-camera-wrap .camera-box{aspect-ratio:3/4;max-height:72vh;border-radius:8px!important}.base-camera-status{background:#fff8e8;border:1px solid #f2dea5;border-radius:8px;padding:9px;margin-bottom:10px;font-size:11px;color:#775500}
      .base-confirm-card{max-width:680px;margin:0 auto;background:#fff;border:1px solid var(--ab-line);border-radius:10px;overflow:hidden}.base-confirm-head{background:#eef8f7;border-bottom:1px solid #d8ebe8;padding:12px 14px;color:var(--ab-teal);font-weight:800}.base-confirm-body{padding:14px}.base-confirm-name{font-size:18px;font-weight:900;color:var(--ab-ink);margin-bottom:6px}.base-confirm-code{font:12px Consolas,monospace;color:var(--ab-muted);margin-bottom:12px}.base-confirm-actions{display:flex;gap:8px;align-items:end;flex-wrap:wrap}.base-confirm-actions .field{min-width:150px;flex:1}.base-confirm-actions .base-action{min-height:42px}
      #countMethodSelector,.count-choice-note,#manualCameraSlot,#scanCameraSlot{display:none!important}#counting>.card:first-of-type{display:none!important}#counting{max-width:1100px;margin:0 auto}#counting .section-head:first-child{background:#fff!important;border:1px solid var(--ab-line)!important;border-radius:9px!important;padding:10px!important;box-shadow:none!important}.count-mode-panel{display:none!important}.count-summary-shell{display:none!important}
      #baseManualPage #countModeManual,#baseBarcodePage .scan-panel{display:block!important}.base-page #countModeManual{display:block!important}.base-page #countModeManual .count-mode-card{border-radius:9px!important;box-shadow:none!important;border:1px solid var(--ab-line)!important}.base-page .scan-panel{border-radius:9px!important;background:#fff!important;border:1px solid var(--ab-line)!important}
      #materials,#importer,#exporter,#reports,#settings{max-width:1100px;margin:0 auto}#materials>.card,#importer>.card,#settings .card,#exporter .card{border-radius:9px!important;box-shadow:none!important;border:1px solid var(--ab-line)!important}.export-cards{grid-template-columns:repeat(2,minmax(0,1fr))!important;gap:9px!important}.export-card p{min-height:auto!important}
      @media(max-width:820px){.main{padding:68px 8px 14px!important}.base-module-list{grid-template-columns:1fr}.base-module{min-height:70px}.base-module-icon{width:42px;height:42px;flex-basis:42px}.base-summary-strip{grid-template-columns:repeat(2,1fr)}#dashboard>.grid4{grid-template-columns:repeat(2,1fr)!important}.top-calc-btn,.top-import-btn,.base-home-btn{font-size:0!important;padding:8px!important;width:38px!important;justify-content:center!important}.top-calc-btn span,.top-import-btn span{font-size:18px!important}.base-form-grid,.base-session-meta{grid-template-columns:1fr}.export-cards{grid-template-columns:1fr!important}}
      @media(max-width:480px){.topbar h1{font-size:15px!important}.topbar>div:first-child:before{width:34px;height:34px;font-size:12px}.base-home-head{padding:12px}.base-home-logo{width:45px;height:45px;font-size:15px}.base-summary-strip{grid-template-columns:1fr 1fr}}
    `;
    document.head.appendChild(st);
  }

  function ensureTopActions(){
    const actions=q('.topbar-actions');
    if(!actions) return;
    if(!$('baseHomeBtn')){
      const b=document.createElement('button');
      b.type='button'; b.id='baseHomeBtn'; b.className='base-home-btn'; b.innerHTML='⌂ <span>الرئيسية</span>';
      b.onclick=()=>showPage('dashboard','الرئيسية');
      actions.insertBefore(b,actions.firstChild);
    }
  }

  function moduleButton(cls,icon,name,desc,fn){
    const b=document.createElement('button');
    b.type='button'; b.className='base-module '+(cls||'');
    b.innerHTML=`<span class="base-module-icon">${icon}</span><span><b>${name}</b><span>${desc}</span></span>`;
    b.onclick=fn;
    return b;
  }

  function rebuildDashboard(){
    const dash=$('dashboard'); if(!dash||$('baseHomeHero')) return;
    const hero=document.createElement('div'); hero.id='baseHomeHero'; hero.className='base-home-hero';
    hero.innerHTML=`<div class="base-home-head"><div><h2>AL BASIL</h2><p>نظام جرد المواد والمخزون • التقييم بسعر آخر شراء</p></div><div class="base-home-logo">BM</div></div><div class="base-module-list" id="baseModuleList"></div>`;
    dash.insertBefore(hero,dash.firstChild);
    const list=$('baseModuleList');
    list.append(
      moduleButton('', '▦','المنتجات / المواد','عرض المواد والبحث والتعديل',()=>showPage('materials','المواد')),
      moduleButton('blue','▣','الجرد','فتح جلسة الجرد الحالية أو بدء العمل عليها',()=>openStockSession()),
      moduleButton('', '⌁','وسيلة الجرد','يدوي، قارئ باركود، أو كاميرا الموبايل',()=>showPage('baseMethodPage','وسيلة الجرد')),
      moduleButton('gold','▧','كاميرا الباركود','شاشة مسح مستقلة للكاميرا الخلفية',()=>openCameraPage()),
      moduleButton('blue','⇩','استيراد المواد','استيراد Excel / CSV وتحديث المواد',()=>showPage('importer','استيراد المواد')),
      moduleButton('blue','⇧','تصدير ونسخ احتياطي','تصدير المواد والجرد والنسخ الاحتياطية',()=>showPage('exporter','التصدير')),
      moduleButton('dark','≣','التقارير','مراجعة آخر جرد والفروق',()=>showPage('reports','التقارير')),
      moduleButton('dark','⚙','الإعدادات','التثبيت والاستعادة وإدارة البيانات',()=>showPage('settings','الإعدادات'))
    );
  }

  function addSectionHeaders(){
    const map={
      materials:['▦','المنتجات / المواد','دليل المواد والبحث والتعديل'],
      importer:['⇩','استيراد المواد','إضافة وتحديث المواد من الملفات'],
      exporter:['⇧','التصدير والنسخ الاحتياطي','تصدير البيانات ونتائج الجرد'],
      reports:['≣','تقارير الجرد','مراجعة الفروق ونتائج آخر جرد'],
      settings:['⚙','الإعدادات','إعدادات التطبيق والبيانات']
    };
    Object.entries(map).forEach(([id,[ic,n,d]])=>{
      const s=$(id); if(!s||s.dataset.baseHeader==='1') return;
      s.dataset.baseHeader='1';
      const bar=document.createElement('div'); bar.className='base-toolbar';
      bar.innerHTML=`<div class="base-toolbar-title"><span class="base-toolbar-icon">${ic}</span><span>${n}<small style="display:block;font-size:10px;color:var(--ab-muted);font-weight:400;margin-top:2px">${d}</small></span></div><div class="base-toolbar-actions"><button class="base-action" type="button">⌂ الرئيسية</button></div>`;
      bar.querySelector('button').onclick=()=>showPage('dashboard','الرئيسية');
      s.insertBefore(bar,s.firstChild);
      if(id==='materials'){
        const fab=document.createElement('button'); fab.type='button'; fab.className='base-floating-add'; fab.title='مادة جديدة'; fab.textContent='+'; fab.onclick=()=>{try{clearMaterialForm()}catch(e){}; const t=$('mName'); if(t)t.focus();};
        s.appendChild(fab);
      }
    });
  }

  function buildStockPages(){
    const main=q('.main'); if(!main||$('baseStockPage')) return;

    const stock=document.createElement('section'); stock.id='baseStockPage'; stock.className='base-page';
    stock.innerHTML=`
      <div class="base-toolbar"><div class="base-toolbar-title"><span class="base-toolbar-icon">▣</span><span>الجرد<small style="display:block;font-size:10px;color:var(--ab-muted);font-weight:400">بيانات جلسة الجرد الحالية</small></span></div><div class="base-toolbar-actions"><button class="base-action" id="stockHome">⌂ الرئيسية</button></div></div>
      <div class="base-panel">
        <div class="base-form-grid">
          <div class="field"><label>رقم الجرد</label><input id="baseCountNo" readonly></div>
          <div class="field"><label>التاريخ</label><input id="baseCountDate" type="date"></div>
          <div class="field"><label>المخزن / الفرع</label><input id="baseCountLocation" placeholder="المخزن الرئيسي"></div>
          <div class="field"><label>القائم بالجرد</label><input id="baseCountUser"></div>
        </div>
        <div class="base-summary-strip">
          <div class="base-summary-cell"><span>إجمالي المواد</span><b id="baseTotalItems">0</b></div>
          <div class="base-summary-cell"><span>تم جردها</span><b id="baseCountedItems">0</b></div>
          <div class="base-summary-cell"><span>متبقي</span><b id="baseRemainingItems">0</b></div>
          <div class="base-summary-cell"><span>صافي فرق القيمة</span><b id="baseDiffValue">0.00</b></div>
        </div>
        <div style="display:flex;gap:8px;flex-wrap:wrap;margin-top:12px">
          <button class="base-action primary" id="stockChooseMethod">اختيار وسيلة الجرد</button>
          <button class="base-action blue" id="stockOpenManual">فتح الجرد اليدوي</button>
          <button class="base-action gold" id="stockOpenCamera">فتح الكاميرا</button>
          <button class="base-action" id="stockSave">حفظ جلسة الجرد</button>
        </div>
      </div>`;
    main.appendChild(stock);

    const methods=document.createElement('section'); methods.id='baseMethodPage'; methods.className='base-page';
    methods.innerHTML=`
      <div class="base-toolbar"><div class="base-toolbar-title"><span class="base-toolbar-icon">⌁</span><span>وسيلة الجرد<small style="display:block;font-size:10px;color:var(--ab-muted);font-weight:400">اختر طريقة إدخال الكميات</small></span></div><div class="base-toolbar-actions"><button class="base-action" id="methodHome">⌂ الرئيسية</button></div></div>
      <div class="base-panel"><div class="base-method-list">
        <button class="base-method" id="baseManualMethod"><span class="base-method-icon">✍</span><span><b>جرد يدوي</b><span>بحث عن المادة ثم إدخال الكمية يدويًا.</span></span></button>
        <button class="base-method" id="baseBarcodeMethod"><span class="base-method-icon">▥</span><span><b>قارئ باركود</b><span>استخدام قارئ USB / Bluetooth أو إدخال الباركود يدويًا.</span></span></button>
        <button class="base-method" id="baseCameraMethod"><span class="base-method-icon">▧</span><span><b>كاميرا الموبايل</b><span>مسح الباركود بالكاميرا ثم تأكيد الكمية والعودة للمسح تلقائيًا.</span></span></button>
      </div></div>`;
    main.appendChild(methods);

    const manual=document.createElement('section'); manual.id='baseManualPage'; manual.className='base-page';
    manual.innerHTML=`<div class="base-toolbar"><div class="base-toolbar-title"><span class="base-toolbar-icon">✍</span><span>الجرد اليدوي<small style="display:block;font-size:10px;color:var(--ab-muted);font-weight:400">إدخال الكميات يدويًا</small></span></div><div class="base-toolbar-actions"><button class="base-action" id="manualSession">بيانات الجرد</button><button class="base-action" id="manualHome">⌂ الرئيسية</button></div></div><div class="base-panel" id="baseManualHost"></div>`;
    main.appendChild(manual);

    const barcode=document.createElement('section'); barcode.id='baseBarcodePage'; barcode.className='base-page';
    barcode.innerHTML=`<div class="base-toolbar"><div class="base-toolbar-title"><span class="base-toolbar-icon">▥</span><span>قارئ الباركود<small style="display:block;font-size:10px;color:var(--ab-muted);font-weight:400">USB / Bluetooth أو إدخال يدوي</small></span></div><div class="base-toolbar-actions"><button class="base-action" id="barcodeMethods">وسيلة الجرد</button><button class="base-action" id="barcodeHome">⌂ الرئيسية</button></div></div><div class="base-panel" id="baseBarcodeHost"></div>`;
    main.appendChild(barcode);

    const camera=document.createElement('section'); camera.id='baseCameraPage'; camera.className='base-page';
    camera.innerHTML=`<div class="base-camera-wrap"><div class="base-toolbar"><div class="base-toolbar-title"><span class="base-toolbar-icon">▧</span><span>كاميرا الباركود<small style="display:block;font-size:10px;color:var(--ab-muted);font-weight:400">وجّه الكاميرا إلى باركود الصنف</small></span></div><div class="base-toolbar-actions"><button class="base-action" id="cameraMethods">وسيلة الجرد</button><button class="base-action" id="cameraHome">⌂ الرئيسية</button></div></div><div class="base-panel"><div class="base-camera-status">بعد قراءة الباركود ستنتقل إلى شاشة تسجيل الكمية، وبعد الحفظ ستعود الكاميرا تلقائيًا للصنف التالي.</div><div id="baseCameraHost"></div></div></div>`;
    main.appendChild(camera);

    const confirm=document.createElement('section'); confirm.id='baseConfirmPage'; confirm.className='base-page';
    confirm.innerHTML=`<div class="base-confirm-card"><div class="base-confirm-head">تسجيل الصنف في جلسة الجرد</div><div class="base-confirm-body"><div class="base-confirm-name" id="baseConfirmName">—</div><div class="base-confirm-code" id="baseConfirmCode">—</div><div class="base-note" id="baseConfirmInfo"></div><div class="base-confirm-actions"><div class="field"><label>الكمية</label><input id="baseConfirmQty" type="number" min="0.001" step="0.001" value="1"></div><button class="base-action primary" id="baseConfirmSave">تسجيل والعودة للكاميرا</button><button class="base-action" id="baseConfirmCancel">إلغاء</button></div></div></div>`;
    main.appendChild(confirm);

    $('stockHome').onclick=()=>showPage('dashboard','الرئيسية');
    $('stockChooseMethod').onclick=()=>showPage('baseMethodPage','وسيلة الجرد');
    $('stockOpenManual').onclick=()=>openManualPage();
    $('stockOpenCamera').onclick=()=>openCameraPage();
    $('stockSave').onclick=()=>{syncSessionToOriginal(); try{saveCountSession()}catch(e){alert('تعذر حفظ الجرد');} updateStockSummary();};
    $('methodHome').onclick=()=>showPage('dashboard','الرئيسية');
    $('baseManualMethod').onclick=()=>openManualPage();
    $('baseBarcodeMethod').onclick=()=>openBarcodePage();
    $('baseCameraMethod').onclick=()=>openCameraPage();
    $('manualSession').onclick=()=>openStockSession();
    $('manualHome').onclick=()=>showPage('dashboard','الرئيسية');
    $('barcodeMethods').onclick=()=>showPage('baseMethodPage','وسيلة الجرد');
    $('barcodeHome').onclick=()=>showPage('dashboard','الرئيسية');
    $('cameraMethods').onclick=()=>showPage('baseMethodPage','وسيلة الجرد');
    $('cameraHome').onclick=()=>showPage('dashboard','الرئيسية');
    $('baseConfirmSave').onclick=()=>savePendingAndReturn();
    $('baseConfirmCancel').onclick=()=>returnToCamera();

    ['baseCountDate','baseCountLocation','baseCountUser'].forEach(id=>{
      $(id).addEventListener('input',syncSessionToOriginal);
      $(id).addEventListener('change',syncSessionToOriginal);
    });
  }

  function moveOriginalControls(){
    const manual=$('countModeManual'); const manualHost=$('baseManualHost');
    if(manual&&manualHost&&!manualHost.contains(manual)){ manual.classList.add('active'); manualHost.appendChild(manual); }
    const scan=q('#countModeScan .scan-panel'); const scanHost=$('baseBarcodeHost');
    if(scan&&scanHost&&!scanHost.contains(scan)) scanHost.appendChild(scan);
    const cam=$('cameraScanner'); const camHost=$('baseCameraHost');
    if(cam&&camHost&&!camHost.contains(cam)) camHost.appendChild(cam);
  }

  function syncOriginalToSession(){
    const pairs=[['countNo','baseCountNo'],['countDate','baseCountDate'],['countLocation','baseCountLocation'],['countUser','baseCountUser']];
    pairs.forEach(([a,b])=>{const x=$(a),y=$(b); if(x&&y)y.value=x.value||'';});
  }
  function syncSessionToOriginal(){
    const pairs=[['baseCountDate','countDate'],['baseCountLocation','countLocation'],['baseCountUser','countUser']];
    pairs.forEach(([a,b])=>{const x=$(a),y=$(b); if(x&&y)y.value=x.value||'';});
  }

  function updateStockSummary(){
    let total=0,counted=0,diff=0;
    try{
      total=(db&&Array.isArray(db.materials))?db.materials.length:0;
      if(db&&db.current&&db.current.actual){counted=Object.values(db.current.actual).filter(v=>v!==null&&v!==''&&Number.isFinite(Number(v))).length;}
      if(typeof countRows==='function') diff=countRows().reduce((s,r)=>s+(Number(r.diffValue)||0),0);
    }catch(e){}
    const map={baseTotalItems:total,baseCountedItems:counted,baseRemainingItems:Math.max(0,total-counted),baseDiffValue:(Number(diff)||0).toFixed(2)};
    Object.entries(map).forEach(([id,v])=>{const e=$(id);if(e)e.textContent=v;});
  }
  function updateDashboardMeta(){
    updateStockSummary();
  }

  function openStockSession(){
    syncOriginalToSession(); updateStockSummary(); showPage('baseStockPage','الجرد');
  }
  function openManualPage(){
    stopCam();
    try{if(typeof renderCounting==='function')renderCounting();}catch(e){}
    showPage('baseManualPage','الجرد اليدوي');
    const inp=$('countSearch'); if(inp)setTimeout(()=>inp.focus(),80);
  }
  function openBarcodePage(){
    stopCam();
    showPage('baseBarcodePage','قارئ الباركود');
    const inp=$('barcodeScan'); if(inp)setTimeout(()=>inp.focus(),100);
  }
  function openCameraPage(){
    showPage('baseCameraPage','كاميرا الباركود',{keepCamera:true});
    const q1=$('phoneScanQty'); if(q1&&!q1.value)q1.value='1';
    setTimeout(()=>{try{ if(typeof startCamera==='function') startCamera(); }catch(e){}},120);
  }
  window.alBasilOpenCamera=openCameraPage;

  function interceptBarcode(){
    if(baseProcess) return;
    if(typeof window.processBarcodeInput!=='function') return;
    baseProcess=window.processBarcodeInput;
    window.processBarcodeInput=function(codeOverride){
      const cameraPage=$('baseCameraPage');
      const inCamera=cameraPage&&cameraPage.classList.contains('active');
      if(!inCamera) return baseProcess.apply(this,arguments);
      const code=String(codeOverride||'').trim();
      if(!code) return;
      let item=null;
      try{item=db.materials.find(x=>String(x.barcode||'').trim()===code);}catch(e){}
      if(!item){
        const status=$('cameraStatus'); if(status)status.innerHTML=`باركود غير مسجل: <b>${escapeHtml(code)}</b>`;
        return;
      }
      pendingItem=item; pendingBarcode=code;
      stopCam();
      $('baseConfirmName').textContent=item.name||'مادة';
      $('baseConfirmCode').textContent=`Barcode: ${code}${item.code?' • Code: '+item.code:''}`;
      let current=null; try{current=getCurrentActual(item.id);}catch(e){}
      $('baseConfirmInfo').textContent=`الوحدة: ${item.unit||'وحدة'} • الكمية المسجلة حاليًا: ${current===null?'لم تسجل بعد':current}`;
      const suggested=Number(($('phoneScanQty')||{}).value)||1;
      $('baseConfirmQty').value=suggested;
      showPage('baseConfirmPage','تسجيل الكمية');
      setTimeout(()=>$('baseConfirmQty')?.focus(),100);
    };
  }

  function escapeHtml(s){return String(s).replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));}

  function savePendingAndReturn(){
    if(!pendingItem){returnToCamera();return;}
    const qty=Math.max(.001,Number($('baseConfirmQty').value)||1);
    try{
      const cur=getCurrentActual(pendingItem.id);
      setCurrentActual(pendingItem.id,(cur===null?0:Number(cur)||0)+qty);
      if(typeof renderCounting==='function')renderCounting();
      if(typeof renderDashboard==='function')renderDashboard();
    }catch(e){alert('تعذر تسجيل الكمية');return;}
    pendingItem=null; pendingBarcode='';
    returnToCamera();
  }
  function returnToCamera(){
    pendingItem=null; pendingBarcode='';
    if(cameraReturnTimer)clearTimeout(cameraReturnTimer);
    showPage('baseCameraPage','كاميرا الباركود',{keepCamera:true});
    cameraReturnTimer=setTimeout(()=>{try{if(typeof startCamera==='function')startCamera();}catch(e){}},160);
  }

  function wireOriginalNavigation(){
    qa('.nav button[data-section]').forEach(b=>{
      const sec=b.dataset.section;
      b.addEventListener('click',(ev)=>{
        if(sec==='counting'){ev.preventDefault();openStockSession();}
      },true);
    });
  }

  function improveMaterials(){
    const s=$('materials'); if(!s) return;
    const filter=q('.materials-filters',s);
    if(filter&&!filter.classList.contains('base-product-header'))filter.classList.add('base-product-header');
  }

  function init(){
    addStyles(); ensureTopActions(); rebuildDashboard(); addSectionHeaders(); buildStockPages(); moveOriginalControls(); improveMaterials(); interceptBarcode(); wireOriginalNavigation();
    showPage('dashboard','الرئيسية');
    safeRefresh();
  }

  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true}); else init();
})();
