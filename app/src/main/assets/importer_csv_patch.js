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
      const isXlsx=String(name).toLowerCase().endsWith('.xlsx');
      el.style.display='block';
      el.style.borderColor=isXlsx?'#7ccfc3':'#cbd5e1';
      el.style.background=isXlsx?'#ecfdf5':'#f8fafc';
      el.innerHTML='<b>الملف المختار:</b> '+String(name).replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]))+(isXlsx?' <span style="display:inline-block;margin-right:6px;padding:2px 7px;border-radius:999px;background:#0f766e;color:#fff;font-size:10px">XLSX موصى به</span>':'');
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
      if(name.endsWith('.xlsx')){
        matrix=await parseXLSX(await readFileBuffer(file));
      }else if(name.endsWith('.csv')||name.endsWith('.txt')||name.endsWith('.tsv')){
        const buffer=await readFileBuffer(file);
        matrix=parseCsvRobust(decodeText(buffer));
      }else if(name.endsWith('.xls')){
        throw new Error('صيغة XLS القديمة تحتاج تحويلها إلى XLSX أو CSV.');
      }else if(name.endsWith('.pdf')){
        throw new Error('استيراد PDF غير مدعوم حاليًا.');
      }else{
        throw new Error('صيغة غير مدعومة. الأفضل XLSX، ويمكن أيضًا CSV أو TXT/TSV.');
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

  function buildHelpCard(){
    const card=document.createElement('div');
    card.id='xlsxImportHelp';
    card.style.cssText='margin:0 0 14px;padding:14px 15px;border:1px solid #b7ddd8;border-radius:12px;background:linear-gradient(135deg,#f0fdfa,#f8fbff);color:#334155;line-height:1.8;font-size:12px';
    card.innerHTML=`
      <div style="display:flex;align-items:center;gap:9px;margin-bottom:8px">
        <span style="display:grid;place-items:center;width:34px;height:34px;border-radius:9px;background:#0f766e;color:#fff;font-weight:900">X</span>
        <div><b style="display:block;font-size:14px;color:#0f5f59">الامتداد الموصى به: XLSX</b><span style="font-size:10px;color:#64748b">الأفضل لملفات Excel العربية والحفاظ على الأعمدة كما هي.</span></div>
      </div>
      <div style="font-weight:800;margin-bottom:4px">طريقة تجهيز الملف في Excel:</div>
      <div>1) افتح ملف المواد في Excel.</div>
      <div>2) اختر <b>حفظ باسم / Save As</b>.</div>
      <div>3) اختر <b>Excel Workbook (*.xlsx)</b>.</div>
      <div>4) تأكد أن الصف الأول يحتوي أسماء الأعمدة، وأهمها <b>اسم الصنف</b> و<b>اخر شراء</b>.</div>
      <div style="margin-top:7px;padding-top:7px;border-top:1px dashed #b9d9d5"><b>داخل التطبيق:</b> اضغط «اختيار ملف XLSX / CSV للاستيراد» ← اختر الملف ← انتظر ظهور المعاينة ← اضغط «اعتماد الاستيراد».</div>`;
    return card;
  }

  function installImporterUX(){
    const section=document.getElementById('importer');
    const fileInput=document.getElementById('importFile');
    const drop=document.getElementById('dropzone');
    if(!section||!fileInput||!drop||document.getElementById('chooseImportFileBtn')) return;

    // Prefer XLSX in the Android/file chooser while retaining CSV/TXT compatibility.
    fileInput.setAttribute('accept','.xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,.csv,text/csv,.txt,.tsv,text/plain');

    const help=buildHelpCard();
    drop.parentNode.insertBefore(help,drop);

    const choose=document.createElement('button');
    choose.type='button';
    choose.id='chooseImportFileBtn';
    choose.innerHTML='<span style="font-size:24px">⇧</span><span><b style="display:block;font-size:16px">اختيار ملف XLSX / CSV للاستيراد</b><small style="display:block;margin-top:3px;opacity:.9">XLSX هو الاختيار الموصى به</small></span>';
    choose.style.cssText='width:100%;min-height:72px;margin:0 0 12px;border:0;border-radius:13px;padding:12px 16px;background:linear-gradient(135deg,#0f766e,#1457b8);color:#fff;font-family:Tahoma,Arial;display:flex;align-items:center;justify-content:center;gap:12px;box-shadow:0 8px 20px rgba(15,118,110,.20);cursor:pointer';
    choose.addEventListener('click',()=>fileInput.click());

    const selected=document.createElement('div');
    selected.id='selectedImportFileName';
    selected.style.cssText='display:none;margin:0 0 12px;padding:10px 12px;border:1px solid #9fd7d1;border-radius:9px;background:#ecfdf5;color:#0f5f59;font-size:12px';

    drop.parentNode.insertBefore(choose,drop);
    drop.parentNode.insertBefore(selected,drop);
    drop.innerHTML='<b>أو اضغط داخل هذه المساحة لاختيار الملف</b><span class="hint">XLSX موصى به • CSV / TXT / TSV مدعومة أيضًا</span>';

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
      status.textContent='ابدأ بالضغط على زر «اختيار ملف XLSX / CSV للاستيراد» بالأعلى.';
    }
  }

  loadImportFile=loadImportFileFixed;
  window.loadImportFile=loadImportFileFixed;

  if(document.readyState==='loading'){
    document.addEventListener('DOMContentLoaded',installImporterUX,{once:true});
  }else{
    installImporterUX();
  }
})();
