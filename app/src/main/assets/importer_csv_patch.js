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

  async function loadImportFileFixed(file){
    pendingImport=[];
    importBtn.disabled=true;
    importPreview.innerHTML='';
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

  // Replace the original importer while preserving the rest of AL BASIL logic.
  loadImportFile=loadImportFileFixed;
  window.loadImportFile=loadImportFileFixed;
})();
