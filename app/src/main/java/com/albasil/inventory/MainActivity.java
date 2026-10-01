package com.albasil.bm;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends Activity {
    private static final int REQ_IMPORT_XML = 3001;
    private static final int REQ_EXPORT_XML = 3002;
    private static final int REQ_CAMERA = 49374;

    private static final int BLUE = Color.rgb(54, 78, 183);
    private static final int NAVY = Color.rgb(16, 38, 98);
    private static final int LIGHT = Color.rgb(246, 247, 250);
    private static final int CARD = Color.rgb(236, 238, 241);
    private static final int PINK = Color.rgb(255, 55, 126);
    private static final int GOLD = Color.rgb(194, 142, 20);

    private final List<MaterialItem> materials = new ArrayList<>();
    private final List<InventorySession> inventories = new ArrayList<>();
    private SharedPreferences prefs;
    private LinearLayout root;
    private InventorySession currentInventory;
    private boolean reopenScannerAfterSave = false;
    private InventorySession pendingExportInventory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(Color.rgb(10,18,35));
        prefs = getSharedPreferences("albasil_bm_native", MODE_PRIVATE);
        loadData();
        showHome();
    }

    private void baseScreen(String title, boolean showBack) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(LIGHT);
        setContentView(root);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(8), dp(16), dp(8));
        bar.setBackgroundColor(BLUE);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        TextView titleView = text(title, 24, Color.WHITE, true);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        titleView.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        bar.addView(titleView, tLp);

        if (showBack) {
            Button back = smallButton("←", Color.TRANSPARENT, Color.WHITE);
            back.setTextSize(34);
            back.setOnClickListener(v -> showHome());
            bar.addView(back, new LinearLayout.LayoutParams(dp(70), ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            TextView tools = text("ⓘ   🔧   ?", 24, Color.WHITE, false);
            tools.setGravity(Gravity.CENTER);
            bar.addView(tools, new LinearLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    private void showHome() {
        currentInventory = null;
        baseScreen("الباسل BM", false);
        LinearLayout body = verticalBody();
        body.setPadding(dp(20), dp(30), dp(20), dp(20));

        body.addView(menuButton("📄", "جردة جديدة", Color.rgb(16,163,74), v -> createInventoryDialog()));
        body.addView(space(14));
        body.addView(menuButton("📋", "الجردات", Color.rgb(255,165,0), v -> showInventories()));
        body.addView(space(14));
        body.addView(menuButton("▦", "المواد", BLUE, v -> showMaterials()));
        body.addView(space(34));
        body.addView(menuButton("✕", "خروج", Color.RED, v -> finish()));
    }

    private void createInventoryDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(10), dp(22), dp(6));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView nLab = text("اسم الجردة", 22, Color.BLACK, false);
        EditText name = input("اكتب اسم الجردة");
        TextView dLab = text("التاريخ", 22, Color.BLACK, false);
        EditText date = input(new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(new Date()));
        date.setInputType(InputType.TYPE_CLASS_DATETIME);
        box.addView(nLab); box.addView(name); box.addView(dLab); box.addView(date);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setView(box)
                .setNegativeButton("إلغاء الأمر", null)
                .setPositiveButton("موافق", null)
                .create();
        dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String nm = name.getText().toString().trim();
            if (nm.isEmpty()) { name.setError("أدخل اسم الجردة"); return; }
            InventorySession s = new InventorySession();
            s.id = UUID.randomUUID().toString();
            s.name = nm;
            s.date = date.getText().toString().trim();
            inventories.add(0, s);
            saveData();
            dlg.dismiss();
            showInventory(s);
        }));
        dlg.show();
        name.requestFocus();
    }

    private void showInventories() {
        baseScreen("الجردات", true);
        LinearLayout body = verticalBody();
        body.setPadding(dp(16), dp(18), dp(16), dp(18));
        if (inventories.isEmpty()) {
            TextView empty = text("لا توجد جردات مسجلة", 19, Color.GRAY, false);
            empty.setGravity(Gravity.CENTER);
            body.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(120)));
        }
        for (InventorySession s : inventories) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), 0, dp(14), dp(14));
            card.setBackground(makeBordered(Color.WHITE, BLUE, 2, 8));
            TextView head = text("سبتمبر   (1.0)    ˄", 21, Color.WHITE, true);
            head.setPadding(dp(12), dp(8), dp(12), dp(8));
            head.setBackgroundColor(BLUE);
            card.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
            TextView nm = text(s.date + " - " + s.name, 21, Color.BLACK, false);
            nm.setGravity(Gravity.RIGHT); nm.setPadding(0, dp(8), 0, 0);
            card.addView(nm);
            TextView sub = text("عدد المواد: " + countMaterialLines(s) + "     عدد المجموعات: " + countGroups(), 15, Color.GRAY, false);
            sub.setGravity(Gravity.RIGHT);
            card.addView(sub);
            card.setOnClickListener(v -> showInventory(s));
            card.setOnLongClickListener(v -> { inventoryMenu(s); return true; });
            body.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(145)));
            body.addView(space(12));
        }
        Button back = wideButton("←     عودة", CARD, Color.BLACK);
        back.setOnClickListener(v -> showHome());
        body.addView(back);
    }

    private void inventoryMenu(InventorySession s) {
        new AlertDialog.Builder(this)
                .setTitle(s.name)
                .setItems(new String[]{"فتح", "تصدير XML", "حذف الجردة"}, (d, which) -> {
                    if (which == 0) showInventory(s);
                    else if (which == 1) exportInventory(s);
                    else {
                        new AlertDialog.Builder(this).setMessage("حذف الجردة؟")
                                .setNegativeButton("إلغاء", null)
                                .setPositiveButton("حذف", (a,b)->{ inventories.remove(s); saveData(); showInventories(); })
                                .show();
                    }
                }).show();
    }

    private void showMaterials() {
        baseScreen("المواد", true);
        LinearLayout body = verticalBody();
        body.setPadding(dp(20), dp(28), dp(20), dp(20));
        body.addView(menuButton("📁", "استيراد من ملف XML", GOLD, v -> chooseXml()));
        body.addView(space(16));
        body.addView(menuButton("⊖", "حذف المواد", Color.RED, v -> confirmDeleteMaterials()));
        body.addView(space(16));
        TextView count = text("عدد المواد الحالية: " + materials.size() + "     المجموعات: " + countGroups(), 16, Color.DKGRAY, false);
        count.setGravity(Gravity.CENTER);
        body.addView(count, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(55)));
        body.addView(space(16));
        Button back = wideButton("←     عودة", CARD, Color.BLACK); back.setOnClickListener(v -> showHome()); body.addView(back);
    }

    private void chooseXml() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/xml", "application/xml", "text/plain", "application/octet-stream"});
        startActivityForResult(i, REQ_IMPORT_XML);
    }

    private void confirmDeleteMaterials() {
        new AlertDialog.Builder(this).setTitle("حذف المواد")
                .setMessage("سيتم حذف جميع المواد. الجردات المسجلة ستظل موجودة.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حذف", (d,w)->{ materials.clear(); saveData(); showMaterials(); })
                .show();
    }

    private void showInventory(InventorySession session) {
        currentInventory = session;
        baseScreen(session.date + " - " + session.name, true);
        LinearLayout body = verticalBody();
        body.setPadding(dp(16), dp(12), dp(16), dp(18));

        TextView help = text("طريقة إدخال الجرد: اكتب الباركود أو استخدم الكاميرا، ثم أدخل الكمية", 15, Color.GRAY, false);
        help.setGravity(Gravity.RIGHT); body.addView(help);

        LinearLayout scanRow = new LinearLayout(this);
        scanRow.setOrientation(LinearLayout.HORIZONTAL);
        scanRow.setGravity(Gravity.CENTER_VERTICAL);
        scanRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        EditText barcode = input("اكتب الباركود...");
        barcode.setSingleLine(true);
        barcode.setInputType(InputType.TYPE_CLASS_TEXT);
        scanRow.addView(barcode, new LinearLayout.LayoutParams(0, dp(58), 1f));
        Button cam = smallButton("▥", PINK, Color.WHITE);
        cam.setTextSize(25);
        cam.setOnClickListener(v -> startScanner());
        scanRow.addView(cam, new LinearLayout.LayoutParams(dp(70), dp(58)));
        body.addView(scanRow);
        barcode.setOnEditorActionListener((v, actionId, event) -> {
            String code = barcode.getText().toString().trim();
            if (!code.isEmpty()) { handleBarcode(code, false); barcode.setText(""); return true; }
            return false;
        });
        barcode.setOnKeyListener((v,key,e)->{
            if(key==KeyEvent.KEYCODE_ENTER && e.getAction()==KeyEvent.ACTION_UP){
                String code=barcode.getText().toString().trim();
                if(!code.isEmpty()){handleBarcode(code,false); barcode.setText("");}
                return true;
            }
            return false;
        });

        Map<String,List<MaterialItem>> grouped = new LinkedHashMap<>();
        for (MaterialItem m : materials) grouped.computeIfAbsent(m.group.isEmpty()?"بدون مجموعة":m.group, k->new ArrayList<>()).add(m);
        if (grouped.isEmpty()) {
            TextView no = text("لا توجد مواد. استورد ملف XML أولًا من شاشة المواد.", 18, Color.GRAY, false);
            no.setGravity(Gravity.CENTER); body.addView(no, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(140)));
        }
        for (Map.Entry<String,List<MaterialItem>> en : grouped.entrySet()) {
            TextView gh = text(en.getKey()+"   (1.0)    ˄", 20, Color.WHITE, false);
            gh.setPadding(dp(12), dp(8), dp(12), dp(8)); gh.setBackgroundColor(BLUE);
            body.addView(gh, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
            for (MaterialItem m : en.getValue()) {
                Double q = session.counts.get(m.key());
                LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(12), dp(8), dp(12), dp(8)); row.setBackgroundColor(Color.WHITE);
                TextView n = text(m.name, 18, q==null?Color.DKGRAY:PINK, true); n.setGravity(Gravity.RIGHT); row.addView(n);
                TextView info = text("الكمية: "+(q==null?"0":fmt(q))+"    الوحدة: "+m.unit+"    الباركود: "+m.barcode, 14, Color.GRAY, false);
                info.setGravity(Gravity.RIGHT); row.addView(info);
                row.setOnClickListener(v -> quantityDialog(m, false));
                body.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(74)));
            }
        }
        Button back = wideButton("←     عودة", CARD, Color.BLACK); back.setOnClickListener(v -> showInventories()); body.addView(back);
    }

    private void startScanner() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 9001);
            return;
        }
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
        integrator.setPrompt("وجّه الكاميرا إلى باركود الصنف");
        integrator.setBeepEnabled(true);
        integrator.setOrientationLocked(true);
        integrator.initiateScan();
    }

    private void handleBarcode(String code, boolean fromCamera) {
        MaterialItem found = null;
        for (MaterialItem m : materials) {
            if (code.equals(m.barcode) || code.equals(m.code)) { found = m; break; }
        }
        if (found == null) {
            Toast.makeText(this, "باركود غير مسجل: " + code, Toast.LENGTH_LONG).show();
            if (fromCamera) delayedReopenScanner();
            return;
        }
        quantityDialog(found, fromCamera);
    }

    private void quantityDialog(MaterialItem m, boolean fromCamera) {
        if (currentInventory == null) return;
        EditText qty = input("");
        qty.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Double existing = currentInventory.counts.get(m.key());
        qty.setText(existing == null ? "1" : fmt(existing));
        qty.selectAll();
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(24),dp(10),dp(24),dp(6));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.addView(text(m.name, 20, PINK, true));
        box.addView(text("الباركود: "+m.barcode+"   الوحدة: "+m.unit, 14, Color.GRAY, false));
        box.addView(text("الكمية", 18, Color.BLACK, false)); box.addView(qty);
        AlertDialog dlg = new AlertDialog.Builder(this).setView(box).setNegativeButton("إلغاء", null).setPositiveButton("تسجيل", null).create();
        dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                double q = Double.parseDouble(qty.getText().toString().trim());
                currentInventory.counts.put(m.key(), q);
                saveData(); vibrate();
                dlg.dismiss();
                if (fromCamera) delayedReopenScanner(); else showInventory(currentInventory);
            } catch (Exception e) { qty.setError("أدخل كمية صحيحة"); }
        }));
        dlg.setOnCancelListener(d -> { if (fromCamera) delayedReopenScanner(); });
        dlg.show();
        qty.requestFocus();
    }

    private void delayedReopenScanner() {
        reopenScannerAfterSave = true;
        root.postDelayed(() -> { reopenScannerAfterSave = false; if (currentInventory != null) startScanner(); }, 350);
    }

    private void importXml(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            doc.getDocumentElement().normalize();
            Map<String,String> groups = new HashMap<>();
            NodeList gs = doc.getElementsByTagName("G");
            for (int i=0;i<gs.getLength();i++) {
                Element g=(Element)gs.item(i);
                groups.put(child(g,"gPtr"), child(g,"GroupName"));
            }
            List<MaterialItem> imported = new ArrayList<>();
            NodeList ms = doc.getElementsByTagName("M");
            for (int i=0;i<ms.getLength();i++) {
                Element e=(Element)ms.item(i);
                MaterialItem m=new MaterialItem();
                m.id = val(child(e,"mptr"), UUID.randomUUID().toString());
                m.code = child(e,"MatCode");
                m.name = child(e,"MatName");
                m.barcode = child(e,"MatBarCode");
                m.unit = val(child(e,"MatUnity"), "وحدة");
                m.group = val(groups.get(child(e,"MatGroupGuid")), "بدون مجموعة");
                m.purchasePrice = parse(child(e,"PurchasePrice"));
                if (!m.name.isEmpty() || !m.code.isEmpty()) imported.add(m);
            }
            if (imported.isEmpty()) throw new Exception("لم يتم العثور على مواد في الملف");
            materials.clear(); materials.addAll(imported); saveData();
            Toast.makeText(this, "تم استيراد " + imported.size() + " مادة", Toast.LENGTH_LONG).show();
            showMaterials();
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("تعذر الاستيراد").setMessage(e.getMessage()).setPositiveButton("حسنًا", null).show();
        }
    }

    private void exportInventory(InventorySession s) {
        pendingExportInventory = s;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/xml");
        i.putExtra(Intent.EXTRA_TITLE, "AL_BASIL_BM_"+safeName(s.name)+"_"+s.date+".xml");
        startActivityForResult(i, REQ_EXPORT_XML);
    }

    private void writeInventoryXml(Uri uri, InventorySession s) {
        try (OutputStream out=getContentResolver().openOutputStream(uri)) {
            StringBuilder x=new StringBuilder();
            x.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            x.append("<ALBASILStockTake name=\"").append(xml(s.name)).append("\" date=\"").append(xml(s.date)).append("\">\n");
            for(MaterialItem m:materials){
                Double q=s.counts.get(m.key()); if(q==null) continue;
                x.append("  <Item><Code>").append(xml(m.code)).append("</Code><Barcode>").append(xml(m.barcode)).append("</Barcode><Name>").append(xml(m.name)).append("</Name><Qty>").append(fmt(q)).append("</Qty></Item>\n");
            }
            x.append("</ALBASILStockTake>");
            out.write(x.toString().getBytes(StandardCharsets.UTF_8)); out.flush();
            Toast.makeText(this,"تم تصدير الجرد",Toast.LENGTH_LONG).show();
        }catch(Exception e){Toast.makeText(this,"تعذر التصدير: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult scan = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (scan != null) {
            if (scan.getContents() != null) { vibrate(); handleBarcode(scan.getContents(), true); }
            return;
        }
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode==RESULT_OK && data!=null && data.getData()!=null){
            if(requestCode==REQ_IMPORT_XML) importXml(data.getData());
            else if(requestCode==REQ_EXPORT_XML && pendingExportInventory!=null) writeInventoryXml(data.getData(),pendingExportInventory);
        }
    }

    @Override
    public void onBackPressed() {
        if (currentInventory != null) showInventories(); else showHome();
    }

    private void loadData() {
        try {
            String s=prefs.getString("data","{}"); JSONObject root=new JSONObject(s);
            JSONArray ms=root.optJSONArray("materials"); if(ms!=null) for(int i=0;i<ms.length();i++) materials.add(MaterialItem.from(ms.getJSONObject(i)));
            JSONArray ins=root.optJSONArray("inventories"); if(ins!=null) for(int i=0;i<ins.length();i++) inventories.add(InventorySession.from(ins.getJSONObject(i)));
        } catch(Exception ignored) {}
    }

    private void saveData() {
        try {
            JSONObject r=new JSONObject(); JSONArray ms=new JSONArray(); for(MaterialItem m:materials) ms.put(m.toJson());
            JSONArray ins=new JSONArray(); for(InventorySession s:inventories) ins.put(s.toJson());
            r.put("materials",ms); r.put("inventories",ins); prefs.edit().putString("data",r.toString()).apply();
        }catch(Exception e){ Toast.makeText(this,"تعذر حفظ البيانات",Toast.LENGTH_SHORT).show(); }
    }

    private LinearLayout verticalBody() {
        ScrollView sc = new ScrollView(this); root.addView(sc,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        LinearLayout body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); sc.addView(body);
        return body;
    }

    private View menuButton(String icon, String label, int iconColor, View.OnClickListener click) {
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(20),dp(8),dp(20),dp(8)); row.setBackground(makeRounded(CARD,10));
        TextView labelV=text(label,23,Color.rgb(25,25,25),false); labelV.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT); row.addView(labelV,new LinearLayout.LayoutParams(0,dp(82),1f));
        TextView ic=text(icon,32,iconColor,true); ic.setGravity(Gravity.CENTER); row.addView(ic,new LinearLayout.LayoutParams(dp(80),dp(82))); row.setOnClickListener(click);
        return row;
    }

    private Button wideButton(String s,int bg,int fg){Button b=new Button(this);b.setText(s);b.setTextSize(21);b.setTextColor(fg);b.setAllCaps(false);b.setBackground(makeRounded(bg,8));b.setGravity(Gravity.CENTER);return b;}
    private Button smallButton(String s,int bg,int fg){Button b=wideButton(s,bg,fg);b.setPadding(0,0,0,0);return b;}
    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextSize(18);e.setTextColor(Color.BLACK);e.setHintTextColor(Color.GRAY);e.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);e.setSingleLine(true);e.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return e;}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);t.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);t.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return t;}
    private View space(int h){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));return v;}
    private android.graphics.drawable.GradientDrawable makeRounded(int color,int r){android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setColor(color);g.setCornerRadius(dp(r));return g;}
    private android.graphics.drawable.GradientDrawable makeBordered(int fill,int stroke,int sw,int r){android.graphics.drawable.GradientDrawable g=makeRounded(fill,r);g.setStroke(dp(sw),stroke);return g;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private int countGroups(){java.util.HashSet<String>s=new java.util.HashSet<>();for(MaterialItem m:materials)s.add(m.group);return s.size();}
    private int countMaterialLines(InventorySession s){return s.counts.size();}
    private void vibrate(){try{Vibrator v=(Vibrator)getSystemService(VIBRATOR_SERVICE);if(v!=null&&v.hasVibrator()){if(Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createOneShot(70,VibrationEffect.DEFAULT_AMPLITUDE));else v.vibrate(70);}}catch(Exception ignored){}}
    private static String child(Element e,String tag){NodeList n=e.getElementsByTagName(tag);if(n.getLength()==0)return"";Node x=n.item(0);return x==null?"":x.getTextContent().trim();}
    private static String val(String s,String d){return s==null||s.trim().isEmpty()?d:s.trim();}
    private static double parse(String s){try{return Double.parseDouble(s);}catch(Exception e){return 0;}}
    private static String fmt(double d){return Math.rint(d)==d?String.valueOf((long)d):String.format(Locale.US,"%.3f",d).replaceAll("0+$","").replaceAll("\\.$","");}
    private static String safeName(String s){return s.replaceAll("[^\\p{L}\\p{N}_-]+","_");}
    private static String xml(String s){return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}

    static class MaterialItem {
        String id="", code="", name="", group="", barcode="", unit="وحدة"; double purchasePrice=0;
        String key(){return !id.isEmpty()?id:(!code.isEmpty()?code:(!barcode.isEmpty()?barcode:name));}
        JSONObject toJson() throws Exception {JSONObject o=new JSONObject();o.put("id",id);o.put("code",code);o.put("name",name);o.put("group",group);o.put("barcode",barcode);o.put("unit",unit);o.put("price",purchasePrice);return o;}
        static MaterialItem from(JSONObject o){MaterialItem m=new MaterialItem();m.id=o.optString("id");m.code=o.optString("code");m.name=o.optString("name");m.group=o.optString("group");m.barcode=o.optString("barcode");m.unit=o.optString("unit","وحدة");m.purchasePrice=o.optDouble("price",0);return m;}
    }

    static class InventorySession {
        String id="",name="",date=""; Map<String,Double> counts=new LinkedHashMap<>();
        JSONObject toJson() throws Exception {JSONObject o=new JSONObject();o.put("id",id);o.put("name",name);o.put("date",date);JSONObject c=new JSONObject();for(Map.Entry<String,Double>e:counts.entrySet())c.put(e.getKey(),e.getValue());o.put("counts",c);return o;}
        static InventorySession from(JSONObject o){InventorySession s=new InventorySession();s.id=o.optString("id");s.name=o.optString("name");s.date=o.optString("date");JSONObject c=o.optJSONObject("counts");if(c!=null){java.util.Iterator<String>it=c.keys();while(it.hasNext()){String k=it.next();s.counts.put(k,c.optDouble(k,0));}}return s;}
    }
}
