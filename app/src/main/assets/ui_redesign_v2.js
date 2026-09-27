(()=>{
  let pendingCameraItem=null;
  let pendingCameraCode='';
  let selectedCountMode='manual';
  let baseProcessBarcode=null;

  function byId(id){return document.getElementById(id)}
  function pageTitle(title){const el=byId('pageTitle');if(el)el.textContent=title}

  function stopCameraSafe(){
    try{if(typeof cameraRunning!=='undefined'&&cameraRunning&&typeof stopCamera==='function')stopCamera()}catch(e){}
  }

  function goPage(id,title,opts={}){
    if(id!=='cameraPage'&&!opts.keepCamera)stopCameraSafe();
    document.querySelectorAll('.section,.al-page').forEach(s=>s.classList.remove('active'));
    const target=byId(id);
    if(target)target.classList.add('active');
    pageTitle(title||'AL BASIL');
    document.querySelectorAll('.nav button[data-section]').forEach(b=>b.classList.toggle('active',b.dataset.section===id));
    try{if(typeof refreshAll==='function')refreshAll()}catch(e){}
    updateSessionSummary();
    window.scrollTo({top:0,behavior:'smooth'});
  }
  window.alBasilGo=goPage;

  function addStyles(){
    if(byId('alBasilRedesignV2Style'))return;
    const st=document.createElement('style');
    st.id='alBasilRedesignV2Style';
    st.textContent=`
      :root{--surface:#ffffff;--soft:#f7f9fc;--navy:#0c1830;--teal:#0f766e;--cyan:#14b8a6;--blue:#2563eb;--gold:#d4a84f}
      .sidebar{display:none!important}.app{grid-template-columns:1fr!important}.main{max-width:1480px;width:100%;margin:0 auto;padding:18px!important}
      .topbar{border-radius:0 0 18px 18px;padding:11px 12px!important;margin:0 -4px 18px!important;box-shadow:0 8px 24px rgba(15,23,42,.06)}
      .topbar-actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.top-home-btn{display:inline-flex;align-items:center;gap:7px;border:0;border-radius:12px;padding:10px 13px;background:var(--navy);color:#fff;font:800 13px Tahoma,Arial;cursor:pointer;box-shadow:0 5px 15px rgba(12,24,48,.18);white-space:nowrap}
      .al-page{display:none}.al-page.active{display:block;animation:alFade .18s ease}.section.active{animation:alFade .18s ease}@keyframes alFade{from{opacity:.35;transform:translateY(4px)}to{opacity:1;transform:none}}
      .dash-hero{background:linear-gradient(135deg,#0c1830 0%,#0f766e 68%,#14b8a6 100%);color:#fff;border-radius:24px;padding:24px;display:flex;justify-content:space-between;align-items:center;gap:18px;margin-bottom:16px;box-shadow:0 18px 42px rgba(15,118,110,.16)}
      .dash-hero h2{margin:0 0 8px;font-size:28px}.dash-hero p{margin:0;color:#d8f4ef;line-height:1.8}.dash-brand{width:74px;height:74px;flex:0 0 74px;border:2px solid var(--gold);border-radius:21px;display:grid;place-items:center;font:900 25px Arial;background:rgba(0,0,0,.18);box-shadow:inset 0 0 0 6px rgba(255,255,255,.05)}
      .dash-nav-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:13px;margin:14px 0 17px}.dash-nav-card{border:1px solid #e1e8f1;background:#fff;border-radius:19px;padding:18px;cursor:pointer;text-align:right;min-height:135px;box-shadow:0 7px 22px rgba(15,23,42,.05);transition:.16s ease}.dash-nav-card:hover{transform:translateY(-3px);box-shadow:0 12px 30px rgba(15,23,42,.09)}.dash-nav-card .ico{width:48px;height:48px;border-radius:14px;display:grid;place-items:center;font-size:23px;background:#ecfdf5;margin-bottom:13px}.dash-nav-card.blue .ico{background:#eff6ff}.dash-nav-card.gold .ico{background:#fff7ed}.dash-nav-card.dark .ico{background:#eef2ff}.dash-nav-card b{display:block;font-size:16px;margin-bottom:5px}.dash-nav-card span{display:block;color:var(--muted);font-size:11px;line-height:1.7}
      #dashboard>.grid4{margin-top:4px}#dashboard>.grid4 .card{border-radius:18px}
      .page-shell{max-width:1120px;margin:0 auto}.page-hero{background:#fff;border:1px solid var(--line);border-radius:22px;padding:20px;box-shadow:0 8px 28px rgba(15,23,42,.055);margin-bottom:14px}.page-hero h2{margin:0 0 7px;font-size:23px}.page-hero p{margin:0;color:var(--muted);line-height:1.8;font-size:12px}
      .session-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.session-grid .field{background:#f8fafc;border:1px solid #e5eaf1;border-radius:14px;padding:12px}.session-grid .field input{background:#fff}.session-actions{display:flex;gap:9px;flex-wrap:wrap;margin-top:15px}.session-actions .btn{min-height:46px}
      .method-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}.method-card{border:1px solid #e1e8f1;background:#fff;border-radius:22px;padding:22px;cursor:pointer;text-align:right;box-shadow:0 8px 26px rgba(15,23,42,.055);transition:.16s ease}.method-card:hover{transform:translateY(-3px);border-color:#9ccfc9}.method-card .method-icon{width:62px;height:62px;border-radius:18px;display:grid;place-items:center;font-size:30px;margin-bottom:15px;background:#ecfdf5}.method-card:nth-child(2) .method-icon{background:#eff6ff}.method-card:nth-child(3) .method-icon{background:#fff7ed}.method-card h3{margin:0 0 7px;font-size:18px}.method-card p{margin:0;color:var(--muted);font-size:12px;line-height:1.8}
      .camera-page-shell{max-width:760px;margin:0 auto}.camera-page-shell #cameraScanner{display:block!important;margin:0!important;border:1px solid #dfe7ef!important;border-top:4px solid #ea580c!important;border-radius:22px!important;box-shadow:0 12px 35px rgba(15,23,42,.08)!important}.camera-page-shell .camera-box{aspect-ratio:3/4;max-height:68vh}.camera-page-toolbar{display:flex;gap:8px;justify-content:space-between;align-items:center;margin-bottom:12px}.camera-page-toolbar .btn{min-height:42px}
      .pending-scan-card{display:none;border:2px solid #14b8a6;background:linear-gradient(135deg,#ecfdf5,#fff);border-radius:18px;padding:16px;margin-bottom:13px;box-shadow:0 8px 24px rgba(20,184,166,.12)}.pending-scan-card.active{display:block}.pending-scan-card h3{margin:0 0 5px}.pending-item-line{font-size:17px;font-weight:900;margin:8px 0}.pending-barcode{font-family:Consolas,monospace;color:#475569}.pending-actions{display:flex;gap:8px;align-items:end;flex-wrap:wrap;margin-top:12px}.pending-actions .field{min-width:130px;max-width:180px}.pending-actions .btn{min-height:42px}
      #countMethodSelector,.count-choice-note,#manualCameraSlot,#scanCameraSlot{display:none!important}.camera-page-shell #cameraScanner .section-head{margin-bottom:10px}
      #counting .card:first-of-type{display:none}#counting[data-al-mode="manual"] #countModeManual{display:block!important}#counting[data-al-mode="manual"] #countModeScan{display:none!important}#counting[data-al-mode="barcode"] #countModeManual{display:none!important}#counting[data-al-mode="barcode"] #countModeScan{display:block!important}#counting[data-al-mode="barcode"] #cameraScanner{display:none!important}
      #counting .section-head:first-child{background:#fff;border:1px solid var(--line);border-radius:18px;padding:14px;box-shadow:0 6px 20px rgba(15,23,42,.04)}
      .al-inline-nav{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:12px}.al-inline-nav .btn{min-height:40px}
      @media(max-width:980px){.dash-nav-grid{grid-template-columns:repeat(2,1fr)}.method-grid{grid-template-columns:1fr}}
      @media(max-width:640px){.main{padding:10px!important}.dash-hero{padding:18px;border-radius:20px}.dash-hero h2{font-size:22px}.dash-brand{width:60px;height:60px;flex-basis:60px;border-radius:17px}.dash-nav-grid{grid-template-columns:1fr 1fr;gap:9px}.dash-nav-card{min-height:118px;padding:14px}.dash-nav-card .ico{width:42px;height:42px;margin-bottom:9px}.session-grid{grid-template-columns:1fr}.topbar