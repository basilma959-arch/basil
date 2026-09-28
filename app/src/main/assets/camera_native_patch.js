(()=>{
  'use strict';

  let webCameraStart = null;
  let scannedItem = null;
  let scannedCode = '';
  let scannerRestartTimer = null;

  const $ = (id)=>document.getElementById(id);

  function nativeAvailable(){
    return typeof window.AndroidBridge !== 'undefined' &&
      window.AndroidBridge &&
      typeof window.AndroidBridge.scanBarcode === 'function';
  }

  function startNativeScanner(){
    if(nativeAvailable()){
      try{
        window.AndroidBridge.scanBarcode();
        return true;
      }catch(e){}
    }
    return false;
  }

  function goStockSession(){
    if(typeof window.alBasilShowPage === 'function'){
      window.alBasilShowPage('baseStockPage','الجرد');
      return;
    }
    const page=$('baseStockPage');
    if(page){
      document.querySelectorAll('.section,.base-page').forEach(x=>x.classList.remove('active'));
      page.classList.add('active');
    }
  }

  function ensureScanEntryCard(){
    if($('nativeScanEntryCard')) return $('nativeScanEntryCard');
    const stock=$('baseStockPage');
    if(!stock) return null;
    const panel=stock.querySelector('.base-panel');
    if(!panel) return null;

    const card=document.createElement('div');
    card.id='nativeScanEntryCard';
    card.style.cssText='display:none;margin:0 0 16px;padding:18px;border:2px solid #14b8a6;border-radius:14px;background:linear-gradient(135deg,#ecfdf5,#ffffff);box-shadow:0 8px 24px rgba(20,184,166,.14)';
    card.innerHTML=`
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:10px">
        <span style="width:34px;height:34px;border-radius:9px;background:#0f766e;color:#fff;display:grid;place-items:center;font-size:18px">✓</span>
        <div style="font-size:13px;font-weight:900;color:#0f766e">الصنف الممسوح بالكاميرا</div>
      </div>
      <div id="nativeScanItemName" style="font-size:25px;line-height:1.45;font-weight:900;color:#172033;margin-bottom:8px">—</div>
      <div id="nativeScanItemCode" style="display:inline-block;font:700 13px Consolas,monospace;color:#334155;background:#f1f5f9;border:1px solid #dbe3ec;border-radius:8px;padding:7px 10px;margin-bottom:12px">—</div>
      <div id="nativeScanCurrentQty" style="font-size:13px;color:#475569;margin-bottom:14px"></div>
      <div style="display:flex;gap:10px;align-items:end;flex-wrap:wrap">
        <div class="field" style="min-width:160px;flex:1;max-width:280px">
          <label style="font-size:13px;font-weight:900;color:#334155">العدد / الكمية</label>
          <input id="nativeScanQty" type="number" min="0.001" step="0.001" value="1" inputmode="decimal" style="min-height:54px;font-size:22px;font-weight:900;text-align:center;border:2px solid #9fd7d1;border-radius:10px">
        </div>
        <button type="button" class="base-action primary" id="nativeScanRegister" style="min-height:52px;padding:10px 18px;font-size:14px">تسجيل الصنف</button>
        <button type="button" class="base-action" id="nativeScanAgain" style="min-height:52px;padding:10px 16px;font-size:13px">مسح باركود آخر</button>
      </div>
      <div id="nativeScanMessage" style="display:none;margin-top:12px;padding:9px 11px;border-radius:8px;background:#f0fdfa;font-size:12px;font-weight:800"></div>`;

    panel.insertBefore(card,panel.firstChild);
    $('nativeScanRegister').addEventListener('click',registerScannedItem);
    $('nativeScanAgain').addEventListener('click',()=>{
      scannedItem=null;
      scannedCode='';
      if(scannerRestartTimer) clearTimeout(scannerRestartTimer);
      scannerRestartTimer=setTimeout(()=>startNativeScanner(),80);
    });
    return card;
  }

  function currentActualFor(item){
    try{
      if(typeof getCurrentActual === 'function') return getCurrentActual(item.id);
    }catch(e){}
    try{
      if(db && db.current && db.current.actual){
        const v=db.current.actual[item.id];
        return v===undefined||v===null||v===''?null:Number(v);
      }
    }catch(e){}
    return null;
  }

  function findMaterialByBarcode(code){
    try{
      if(typeof db !== 'undefined' && db && Array.isArray(db.materials)){
        return db.materials.find(x=>String(x.barcode||'').trim()===code) || null;
      }
    }catch(e){}
    return null;
  }

  function showScannedItem(code){
    const clean=String(code||'').trim();
    if(!clean) return;

    const card=ensureScanEntryCard();
    goStockSession();
    if(!card) return;
    card.style.display='block';

    const item=findMaterialByBarcode(clean);
    scannedCode=clean;
    scannedItem=item;

    const msg=$('nativeScanMessage');
    if(msg){ msg.style.display='none'; msg.textContent=''; }

    if(!item){
      $('nativeScanItemName').textContent='باركود غير مسجل';
      $('nativeScanItemCode').textContent='Barcode: '+clean;
      $('nativeScanCurrentQty').innerHTML='<div style="padding:11px;border-radius:9px;background:#fff1f2;color:#9f1239;font-weight:800">لا يوجد صنف مطابق لهذا الباركود في دليل المواد.</div>';
      $('nativeScanQty').value='1';
      $('nativeScanQty').disabled=true;
      $('nativeScanRegister').disabled=true;
      $('nativeScanRegister').style.opacity='.5';
      $('nativeScanAgain').textContent='إعادة فتح الكاميرا';
      return;
    }

    const cur=currentActualFor(item);
    const curText=cur===null?'لم تسجل بعد':String(cur);
    $('nativeScanItemName').textContent=item.name||'مادة';
    $('nativeScanItemCode').textContent='Barcode: '+clean+(item.code?'  •  Code: '+item.code:'');
    $('nativeScanCurrentQty').innerHTML=`
      <div style="display:grid;grid-template-columns:1fr auto;gap:10px;align-items:center;padding:12px 14px;border-radius:10px;background:#f8fafc;border:1px solid #dfe7ef">
        <div><span style="display:block;font-size:11px;color:#64748b;margin-bottom:3px">الوحدة</span><b style="font-size:15px;color:#334155">${item.unit||'وحدة'}</b></div>
        <div style="text-align:center;min-width:105px;padding:8px 12px;border-radius:9px;background:#e6fffb;border:1px solid #a7e7df"><span style="display:block;font-size:10px;color:#0f766e;margin-bottom:2px">الكمية الحالية</span><b style="font-size:22px;color:#0f766e">${curText}</b></div>
      </div>`;
    $('nativeScanQty').disabled=false;
    $('nativeScanQty').value='1';
    $('nativeScanRegister').disabled=false;
    $('nativeScanRegister').style.opacity='1';
    $('nativeScanAgain').textContent='مسح باركود آخر';
    setTimeout(()=>{
      const qty=$('nativeScanQty');
      if(qty){ qty.focus(); try{qty.select();}catch(e){} }
    },100);
  }

  function setActualFor(item,value){
    if(typeof setCurrentActual === 'function'){
      setCurrentActual(item.id,value);
      return true;
    }
    try{
      if(!db.current) db.current={actual:{}};
      if(!db.current.actual) db.current.actual={};
      db.current.actual[item.id]=value;
      if(typeof saveDB === 'function') saveDB();
      return true;
    }catch(e){return false;}
  }

  function registerScannedItem(){
    if(!scannedItem) return;
    const qtyInput=$('nativeScanQty');
    const addQty=Number(qtyInput ? qtyInput.value : 0);
    if(!Number.isFinite(addQty) || addQty<=0){
      const msg=$('nativeScanMessage');
      if(msg){msg.style.display='block';msg.style.color='#b91c1c';msg.textContent='أدخل كمية صحيحة أكبر من صفر.';}
      if(qtyInput) qtyInput.focus();
      return;
    }

    const old=currentActualFor(scannedItem);
    const newQty=(old===null?0:Number(old)||0)+addQty;
    if(!setActualFor(scannedItem,newQty)){
      const msg=$('nativeScanMessage');
      if(msg){msg.style.display='block';msg.style.color='#b91c1c';msg.textContent='تعذر تسجيل الكمية.';}
      return;
    }

    try{ if(typeof renderCounting==='function') renderCounting(); }catch(e){}
    try{ if(typeof renderDashboard==='function') renderDashboard(); }catch(e){}

    const msg=$('nativeScanMessage');
    if(msg){msg.style.display='block';msg.style.color='#0f766e';msg.textContent='✓ تم تسجيل الصنف. يتم فتح الكاميرا للصنف التالي...';}

    scannedItem=null;
    scannedCode='';
    if(scannerRestartTimer) clearTimeout(scannerRestartTimer);
    scannerRestartTimer=setTimeout(()=>{
      startNativeScanner();
    },250);
  }

  function installNativeCameraBridge(){
    if(window.__alBasilNativeCameraFlowV3) return;
    window.__alBasilNativeCameraFlowV3=true;

    if(typeof window.startCamera === 'function') webCameraStart=window.startCamera;

    window.startCamera=function(){
      if(startNativeScanner()) return;
      if(webCameraStart) return webCameraStart.apply(this,arguments);
      const st=$('cameraStatus');
      if(st) st.textContent='تعذر تشغيل ماسح الباركود على هذا الجهاز.';
    };

    window.onNativeBarcodeScanned=function(code){
      showScannedItem(code);
    };

    window.onNativeBarcodeCancelled=function(){
      goStockSession();
      const card=ensureScanEntryCard();
      if(card){
        card.style.display='block';
        const msg=$('nativeScanMessage');
        if(msg){msg.style.display='block';msg.style.color='#64748b';msg.textContent='تم إلغاء المسح. يمكنك فتح الكاميرا مرة أخرى من زر «مسح باركود آخر».';}
      }
    };

    ensureScanEntryCard();
  }

  if(document.readyState==='loading'){
    document.addEventListener('DOMContentLoaded',()=>setTimeout(installNativeCameraBridge,0),{once:true});
  }else{
    setTimeout(installNativeCameraBridge,0);
  }
})();
