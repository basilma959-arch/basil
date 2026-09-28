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

  function esc(v){
    return String(v==null?'':v).replace(/[&<>"']/g,m=>({
      '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'
    }[m]));
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
    card.style.cssText='display:none;margin:0 0 14px;padding:14px;border:2px solid #14b8a6;border-radius:10px;background:linear-gradient(135deg,#ecfdf5,#fff);box-shadow:0 6px 18px rgba(20,184,166,.10)';
    card.innerHTML=`
      <div style="font-size:12px;font-weight:900;color:#0f766e;margin-bottom:8px">الصنف الممسوح بالكاميرا</div>
      <div id="nativeScanItemName" style="font-size:19px;font-weight:900;color:#1f2a3d;margin-bottom:5px">—</div>
      <div id="nativeScanItemCode" style="font:12px Consolas,monospace;color:#64748b;margin-bottom:8px">—</div>
      <div id="nativeScanCurrentQty" style="font-size:11px;color:#64748b;margin-bottom:10px"></div>
      <div style="display:flex;gap:8px;align-items:end;flex-wrap:wrap">
        <div class="field" style="min-width:150px;flex:1;max-width:240px">
          <label>العدد / الكمية</label>
          <input id="nativeScanQty" type="number" min="0.001" step="0.001" value="1" inputmode="decimal">
        </div>
        <button type="button" class="base-action primary" id="nativeScanRegister" style="min-height:44px">تسجيل الصنف</button>
        <button type="button" class="base-action" id="nativeScanAgain" style="min-height:44px">مسح باركود آخر</button>
      </div>
      <div id="nativeScanMessage" style="display:none;margin-top:10px;font-size:11px;font-weight:700"></div>`;

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
      $('nativeScanCurrentQty').textContent='لا يوجد صنف مطابق لهذا الباركود في دليل المواد.';
      $('nativeScanQty').value='1';
      $('nativeScanQty').disabled=true;
      $('nativeScanRegister').disabled=true;
      $('nativeScanRegister').style.opacity='.5';
      $('nativeScanAgain').textContent='إعادة فتح الكاميرا';
      return;
    }

    const cur=currentActualFor(item);
    $('nativeScanItemName').textContent=item.name||'مادة';
    $('nativeScanItemCode').textContent='Barcode: '+clean+(item.code?' • Code: '+item.code:'');
    $('nativeScanCurrentQty').textContent='الوحدة: '+(item.unit||'وحدة')+' • الكمية المسجلة حاليًا: '+(cur===null?'لم تسجل بعد':cur);
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
    if(msg){msg.style.display='block';msg.style.color='#0f766e';msg.textContent='تم تسجيل الصنف. يتم فتح الكاميرا للصنف التالي...';}

    scannedItem=null;
    scannedCode='';
    if(scannerRestartTimer) clearTimeout(scannerRestartTimer);
    scannerRestartTimer=setTimeout(()=>{
      startNativeScanner();
    },250);
  }

  function installNativeCameraBridge(){
    if(window.__alBasilNativeCameraFlowV2) return;
    window.__alBasilNativeCameraFlowV2=true;

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
