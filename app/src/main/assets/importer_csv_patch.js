(()=>{
  'use strict';

  function readFileBuffer(file){
    return new Promise((resolve,reject)=>{
      const r=new FileReader();
      r.onload=()=>resolve(r.result);
      r.onerror=()=>reject(r.error||new Error('تعذر قراءة الملف'));
      r.readAsArrayBuffer(file);
    });
  }

  function decodeText(buffer){
    const bytes=new Uint8Array(buffer);
    if(bytes.length>=2 && bytes[0]===0xFF && bytes[1]===0xFE){
      return new TextDecoder('utf-16le').decode(bytes.subarray(2));
    }
    if(bytes.length>=2 && bytes[0]===0xFE && bytes[1]===0xFF){
      try{return new TextDecoder('utf-16be').decode(bytes.subarray(2));}catch(e){}
    }
    let start=0;
    if(bytes.length>=3 && bytes[0]===0xEF && bytes[1]===0xBB && bytes[2]===0xBF) start=3;
    let text=new TextDecoder('utf-8').decode(bytes.subarray(start));
    if(text.includes('\uFFFD')){
      try{
        const alt=new TextDecoder('windows-1256').decode(bytes);
        if(!alt.includes('\uFFFD')) text=alt;
      }catch(e){}
    }
    return text.replace(/^\uFEFF/,'');
  }

  function detectDelimiter(text){
    const first=(String(text).split(/\r?\n/,1)[0]||'');
    const candidates=[',',';','\t','|'];
    let best=',',bestCount=-1;
    for(const d of candidates){
      const count=first.split(d).length-1;
      if(count>bestCount){best=d;bestCount=count;}
    }
    return bestCount>0?best:',';
  }

  function parseCsvRobust(text){
    text=String(text||'').replace(/^\uFEFF/,'');
    const delim=detectDelimiter(text);
    const rows=[];
    let row=[],field='',quoted=false;
    for(let i=0;i<text.length;i++){
      const c=text[i],next=text[i+1];
      if(c==='"'){
        if(quoted && next==='"'){field+='"';i++;}
        else quoted=!quoted;
      }else if(c===delim && !quoted){
        row.push(field);field='';
      }else if((c==='\n'||c==='\r') && !quoted){
        if(c==='\r'&&next==='\n') i++;
        row.push(field);field='';
        if(row.some(v=>String(v).trim()!=='')) rows.push(row);
        row=[];
      }else{
        field+=c;
      }
    }
    row.push(field);
    if(row.some(v=>String(v).trim()!=='')) rows.push(row);
    return rows;
  }

  function setSelectedFileName(name){
    const el=document.getElementById('selectedImportFileName');
    if(!el) return;
    if(name){
      el.style.display='block';
      el.innerHTML='<b>الملف المختار:</b> '+String(name).replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]));
    }else{
      el.style.display='none';
      el.textContent='';
    }
  }

  async function loadImportFileFixed(file){
    pendingImport=[];
    importBtn.disabled=true;
    importPreview.innerHTML='';
    setSelectedFileName(file&&file.name?file.name:'');
    importStatus.textContent='جاري قراءة '+file.name+' ...';
    try{
      let matrix;
      const name=String(file.name||'').toLowerCase();
      if(name.endsWith('.csv')||name.endsWith('.txt')||name.endsWith('.tsv')){
        const buffer=await readFileBuffer(file);
        matrix=parseCsvRobust(decodeText(buffer));
      }else if(name.endsWith('.xlsx')){
        matrix=await parseXLSX(await readFileBuffer(file));
      }else if(name.endsWith('.xls')){
        throw new Error('صيغة XLS القديمة تحتاج تحويلها إلى XLSX أو CSV.');
      }else if(name.endsWith('.pdf')){
        throw new Error('استيراد PDF غير مدعوم حاليًا.');
      }else{
        throw new Error('صيغة غير مدعومة. استخدم CSV أو XLSX أو TXT/TSV.');
      }

      if(!matrix||matrix.length<2) throw new Error('الملف فارغ أو لا يحتوي صفوف بيانات.');
      pendingImport=mapImportedMatrix(matrix);
      if(!pendingImport.length) throw new Error('لم يتم العثور على مواد صالحة. تأكد أن الصف الأول يحتوي أسماء الأعمدة.');
      importStatus.textContent=`تمت قراءة ${pendingImport.length} مادة من ${file.name}. راجع المعاينة ثم اضغط اعتماد الاستيراد.`;
      importBtn.disabled=false;
      renderImportPreview();
    }catch(e){
      importStatus.textContent='تعذر قراءة الملف: '+(e&&e.message?e.message:e);
      importPreview.innerHTML='';
      importBtn.disabled=true;
    }finally{
      try{importFile.value='';}catch(e){}
    }
  }

  function installImporterUX(){
    const section=document.getElementById('importer');
    const fileInput=document.getElementById('importFile');
    const drop=document.getElementById('dropzone');
    if(!section||!fileInput||!drop||document.getElementById('chooseImportFileBtn')) return;

    const choose=document.createElement('button');
    choose.type='button';
    choose.id='chooseImportFileBtn';
    choose.innerHTML='<span style="font-size:22px">⇧</span><span><b style="display:block;font-size:16px">اختيار ملف CSV / Excel للاستيراد</b><small style="display:block;margin-top:3px;opacity:.86">اضغط هنا لاختيار الملف من الهاتف</small></span>';
    choose.style.cssText='width:100%;min-height:70px;margin:0 0 12px;border:0;border-radius:13px;padding:12px 16px;background:linear-gradient(135deg,#0f766e,#1457b8);color:#fff;font-family:Tahoma,Arial;display:flex;align-items:center;justify-content:center;gap:12px;box-shadow:0 8px 20px rgba(15,118,110,.20);cursor:pointer';
    choose.addEventListener('click',()=>fileInput.click());

    const selected=document.createElement('div');
    selected.id='selectedImportFileName';
    selected.style.cssText='display:none;margin:0 0 12px;padding:10px 12px;border:1px solid #9fd7d1;border-radius:9px;background:#ecfdf5;color:#0f5f59;font-size:12px';

    drop.parentNode.insertBefore(choose,drop);
    drop.parentNode.insertBefore(selected,drop);
    drop.innerHTML='<b>أو اضغط داخل هذه المساحة لاختيار الملف</b><span class="hint">CSV / XLSX / TXT / TSV</span>';

    const templateBtn=Array.from(section.querySelectorAll('button')).find(b=>String(b.getAttribute('onclick')||'').includes('downloadTemplate'));
    if(templateBtn){
      templateBtn.textContent='تنزيل نموذج CSV – اختياري';
      templateBtn.style.background='#eef2f6';
      templateBtn.style.color='#475569';
      templateBtn.style.border='1px solid #d7dee8';
      templateBtn.style.fontWeight='700';
    }

    const status=document.getElementById('importStatus');
    if(status && status.textContent.trim()==='لم يتم اختيار ملف.'){
      status.textContent='ابدأ بالضغط على زر «اختيار ملف CSV / Excel للاستيراد» بالأعلى.';
    }
  }

  // Replace the original importer while preserving the rest of AL BASIL logic.
  loadImportFile=loadImportFileFixed;
  window.loadImportFile=loadImportFileFixed;

  if(document.readyState==='loading'){
    document.addEventListener('DOMContentLoaded',installImporterUX,{once:true});
  }else{
    installImporterUX();
  }
})();
