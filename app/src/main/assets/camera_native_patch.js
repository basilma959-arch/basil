(()=>{
  'use strict';

  let webCameraStart = null;

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

  function installNativeCameraBridge(){
    if(window.__alBasilNativeCameraInstalled) return;
    window.__alBasilNativeCameraInstalled = true;

    if(typeof window.startCamera === 'function') webCameraStart = window.startCamera;

    window.startCamera = function(){
      if(startNativeScanner()) return;
      if(webCameraStart) return webCameraStart.apply(this, arguments);
      const st=document.getElementById('cameraStatus');
      if(st) st.textContent='تعذر تشغيل ماسح الباركود على هذا الجهاز.';
    };

    window.onNativeBarcodeScanned = function(code){
      const clean=String(code||'').trim();
      if(!clean) return;
      const st=document.getElementById('cameraStatus');
      if(st) st.innerHTML='تمت قراءة الباركود: <b>'+clean.replace(/[&<>"']/g,m=>({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"}[m]))+'</b>';
      if(typeof window.processBarcodeInput === 'function'){
        window.processBarcodeInput(clean);
      }
    };

    window.onNativeBarcodeCancelled = function(){
      const st=document.getElementById('cameraStatus');
      if(st) st.textContent='تم إلغاء المسح. اضغط تشغيل الكاميرا للمحاولة مرة أخرى.';
    };

    const oldOpen=window.alBasilOpenCamera;
    if(typeof oldOpen==='function'){
      window.alBasilOpenCamera=function(){
        oldOpen.apply(this,arguments);
      };
    }
  }

  if(document.readyState==='loading'){
    document.addEventListener('DOMContentLoaded',()=>setTimeout(installNativeCameraBridge,0),{once:true});
  }else{
    setTimeout(installNativeCameraBridge,0);
  }
})();
