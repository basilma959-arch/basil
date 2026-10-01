package com.albasil.bm;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
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
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends Activity {
    private static final int REQ_IMPORT_XML = 3001;
    private static final int REQ_EXPORT_XML = 3002;

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
    private InventorySession pendingExportInventory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(Color.rgb(10, 18, 35));
        prefs = getSharedPreferences("albasil_bm_native", MODE_PRIVATE);
        loadData();
        showHome();
    }

    private void baseScreen(String title, Runnable backAction) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(LIGHT);
        setContentView(root);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(5), dp(14), dp(5));
        bar.setBackgroundColor(BLUE);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));

        TextView titleView = text(title, 24, Color.WHITE, true);
        titleView.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        bar.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        if (backAction != null) {
            Button back = toolbarButton("←");
            back.setTextSize(32);
            back.setContentDescription("عودة");
            back.setOnClickListener(v -> backAction.run());
            bar.addView(back, new LinearLayout.LayoutParams(dp(62), ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            Button info = toolbarButton("ⓘ");
            info.setContentDescription("عن التطبيق");
            info.setOnClickListener(v -> showAboutDialog());
            bar.addView(info, new LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.MATCH_PARENT));

            Button settings = toolbarButton("🔧");
            settings.setContentDescription("الإعدادات");
            settings.setOnClickListener(v -> showSettingsDialog());
            bar.addView(settings, new LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.MATCH_PARENT));

            Button help = toolbarButton("?");
            help.setContentDescription("المساعدة");
            help.setOnClickListener(v -> showHelpDialog());
            bar.addView(help, new LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    private Button toolbarButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(24);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("الباسل BM")
                .setMessage("تطبيق مستقل لإدارة المواد والجرد بالباركود.\n\nالإصدار: 1.0.1\nالاستيراد: ملفات XML الخاصة بالأمين.\nالبيانات تحفظ محليًا على الهاتف.")
                .setPositiveButton("حسنًا", null)
                .show();
    }

    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle("طريقة الاستخدام")
                .setMessage("1- افتح «المواد» ثم «استيراد من ملف XML».\n2- اختر ملف المواد XML مباشرة من الهاتف.\n3- أنشئ جردة جديدة.\n4- اكتب الباركود أو استخدم الكاميرا.\n5- سجل الكمية.\n6- اضغط مطولًا على الجردة للتصدير أو الحذف.")
                .setPositiveButton("حسنًا", null)
                .show();
    }

    private void showSettingsDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(dp(20), dp(10), dp(20), dp(6));

        CheckBox vibration = new CheckBox(this);
        vibration.setText("اهتزاز عند قراءة/تسجيل الباركود");
        vibration.setTextSize(17);
        vibration.setChecked(prefs.getBoolean("vibrate_enabled", true));
        box.addView(vibration);

        CheckBox reopen = new CheckBox(this);
        reopen.setText("فتح الكاميرا تلقائيًا بعد تسجيل الصنف");
        reopen.setTextSize(17);
        reopen.setChecked(prefs.getBoolean("auto_reopen_scanner", true));
        box.addView(reopen);

        new AlertDialog.Builder(this)
                .setTitle("الإعدادات")
                .setView(box)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", (d, w) -> prefs.edit()
                        .putBoolean("vibrate_enabled", vibration.isChecked())
                        .putBoolean("auto_reopen_scanner", reopen.isChecked())
                        .apply())
                .show();
    }

    private void showHome() {
        currentInventory = null;
        baseScreen("الباسل BM", null);
        LinearLayout body = verticalBody();
        body.setPadding(dp(20), dp(30), dp(20), dp(20));

        body.addView(menuButton("📄", "جردة جديدة", Color.rgb(16, 163, 74), v -> createInventoryDialog()));
        body.addView(space(14));
        body.addView(menuButton("📋", "الجردات", Color.rgb(255, 165, 0), v -> showInventories()));
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
        box.addView(nLab);
        box.addView(name);
        box.addView(dLab);
        box.addView(date);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setView(box)
                .setNegativeButton("إلغاء الأمر", null)
                .setPositiveButton("موافق", null)
                .create();
        dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String nm = name.getText().toString().trim();
            if (nm.isEmpty()) {
                name.setError("أدخل اسم الجردة");
                return;
            }
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
        currentInventory = null;
        baseScreen("الجردات", this::showHome);
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
            TextView head = text(monthName(s.date) + "   (1.0)    ˄", 21, Color.WHITE, true);
            head.setPadding(dp(12), dp(8), dp(12), dp(8));
            head.setBackgroundColor(BLUE);
            card.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
            TextView nm = text(s.date + " - " + s.name, 21, Color.BLACK, false);
            nm.setGravity(Gravity.RIGHT);
            nm.setPadding(0, dp(8), 0, 0);
            card.addView(nm);
            TextView sub = text("عدد المواد: " + countMaterialLines(s) + "     عدد المجموعات: " + countGroups(), 15, Color.GRAY, false);
            sub.setGravity(Gravity.RIGHT);
            card.addView(sub);
            card.setOnClickListener(v -> showInventory(s));
            card.setOnLongClickListener(v -> {
                inventoryMenu(s);
                return true;
            });
            body.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(145)));
            body.addView(space(12));
        }
        Button back = wideButton("←     عودة", CARD, Color.BLACK);
        back.setOnClickListener(v -> showHome());
        body.addView(back);
    }

    private String monthName(String date) {
        try {
            String[] p = date.split("-");
            int m = Integer.parseInt(p[1]);
            String[] a = {"", "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"};
            return m >= 1 && m <= 12 ? a[m] : "الجرد";
        } catch (Exception e) {
            return "الجرد";
        }
    }

    private void inventoryMenu(InventorySession s) {
        new AlertDialog.Builder(this)
                .setTitle(s.name)
                .setItems(new String[]{"فتح", "تصدير XML", "حذف الجردة"}, (d, which) -> {
                    if (which == 0) {
                        showInventory(s);
                    } else if (which == 1) {
                        exportInventory(s);
                    } else {
                        new AlertDialog.Builder(this)
                                .setMessage("حذف الجردة؟")
                                .setNegativeButton("إلغاء", null)
                                .setPositiveButton("حذف", (a, b) -> {
                                    inventories.remove(s);
                                    saveData();
                                    showInventories();
                                }).show();
                    }
                }).show();
    }

    private void showMaterials() {
        currentInventory = null;
        baseScreen("المواد", this::showHome);
        LinearLayout body = verticalBody();
        body.setPadding(dp(20), dp(28), dp(20), dp(20));
        body.addView(menuButton("📁", "استيراد من ملف XML", GOLD, v -> chooseXml()));
        body.addView(space(16));
        body.addView(menuButton("⊖", "حذف المواد", Color.RED, v -> confirmDeleteMaterials()));
        body.addView(space(16));

        TextView count = text("عدد المواد الحالية: " + materials.size() + "     المجموعات: " + countGroups(), 16, Color.DKGRAY, false);
        count.setGravity(Gravity.CENTER);
        body.addView(count, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        String lastFile = prefs.getString("last_import_file", "");
        if (!lastFile.isEmpty()) {
            TextView last = text("آخر ملف: " + lastFile, 14, Color.GRAY, false);
            last.setGravity(Gravity.CENTER);
            body.addView(last, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        }

        body.addView(space(16));
        Button back = wideButton("←     عودة", CARD, Color.BLACK);
        back.setOnClickListener(v -> showHome());
        body.addView(back);
    }

    private void chooseXml() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/xml");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/xml",
                "text/xml",
                "application/octet-stream",
                "text/plain"
        });
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_IMPORT_XML);
        } catch (Exception e) {
            Intent fallback = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.setType("*/*");
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(fallback, REQ_IMPORT_XML);
        }
    }

    private void confirmDeleteMaterials() {
        new AlertDialog.Builder(this)
                .setTitle("حذف المواد")
                .setMessage("سيتم حذف جميع المواد. الجردات المسجلة ستظل موجودة.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حذف", (d, w) -> {
                    materials.clear();
                    saveData();
                    showMaterials();
                }).show();
    }

    private void showInventory(InventorySession session) {
        currentInventory = session;
        baseScreen(session.date + " - " + session.name, this::showInventories);
        LinearLayout body = verticalBody();
        body.setPadding(dp(16), dp(12), dp(16), dp(18));

        TextView help = text("طريقة إدخال الجرد: اكتب الباركود أو استخدم الكاميرا، ثم أدخل الكمية", 15, Color.GRAY, false);
        help.setGravity(Gravity.RIGHT);
        body.addView(help);

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
            if (!code.isEmpty()) {
                handleBarcode(code, false);
                barcode.setText("");
                return true;
            }
            return false;
        });
        barcode.setOnKeyListener((v, key, e) -> {
            if (key == KeyEvent.KEYCODE_ENTER && e.getAction() == KeyEvent.ACTION_UP) {
                String code = barcode.getText().toString().trim();
                if (!code.isEmpty()) {
                    handleBarcode(code, false);
                    barcode.setText("");
                }
                return true;
            }
            return false;
        });

        Map<String, List<MaterialItem>> grouped = new LinkedHashMap<>();
        for (MaterialItem m : materials) {
            grouped.computeIfAbsent(m.group.isEmpty() ? "بدون مجموعة" : m.group, k -> new ArrayList<>()).add(m);
        }
        if (grouped.isEmpty()) {
            TextView no = text("لا توجد مواد. استورد ملف XML أولًا من شاشة المواد.", 18, Color.GRAY, false);
            no.setGravity(Gravity.CENTER);
            body.addView(no, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(140)));
        }
        for (Map.Entry<String, List<MaterialItem>> en : grouped.entrySet()) {
            TextView gh = text(en.getKey() + "   (1.0)    ˄", 20, Color.WHITE, false);
            gh.setPadding(dp(12), dp(8), dp(12), dp(8));
            gh.setBackgroundColor(BLUE);
            body.addView(gh, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
            for (MaterialItem m : en.getValue()) {
                Double q = session.counts.get(m.key());
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(12), dp(8), dp(12), dp(8));
                row.setBackgroundColor(Color.WHITE);
                TextView n = text(m.name, 18, q == null ? Color.DKGRAY : PINK, true);
                n.setGravity(Gravity.RIGHT);
                row.addView(n);
                String mainBarcode = !m.barcode.isEmpty() ? m.barcode : (!m.barcode2.isEmpty() ? m.barcode2 : m.barcode3);
                TextView info = text("الكمية: " + (q == null ? "0" : fmt(q)) + "    الوحدة: " + m.unit + "    الكود: " + m.code + "    الباركود: " + mainBarcode, 13, Color.GRAY, false);
                info.setGravity(Gravity.RIGHT);
                row.addView(info);
                row.setOnClickListener(v -> quantityDialog(m, false));
                body.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)));
            }
        }
        Button back = wideButton("←     عودة", CARD, Color.BLACK);
        back.setOnClickListener(v -> showInventories());
        body.addView(back);
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
            if (m.matches(code)) {
                found = m;
                break;
            }
        }
        if (found == null) {
            Toast.makeText(this, "باركود/كود غير مسجل: " + code, Toast.LENGTH_LONG).show();
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

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(10), dp(24), dp(6));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.addView(text(m.name, 20, PINK, true));
        box.addView(text("الكود: " + m.code + "   الباركود: " + m.barcode + "   الوحدة: " + m.unit, 14, Color.GRAY, false));
        box.addView(text("الكمية", 18, Color.BLACK, false));
        box.addView(qty);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setView(box)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("تسجيل", null)
                .create();
        dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                double q = Double.parseDouble(qty.getText().toString().trim());
                currentInventory.counts.put(m.key(), q);
                saveData();
                vibrate();
                dlg.dismiss();
                if (fromCamera) delayedReopenScanner();
                else showInventory(currentInventory);
            } catch (Exception e) {
                qty.setError("أدخل كمية صحيحة");
            }
        }));
        dlg.setOnCancelListener(d -> {
            if (fromCamera) delayedReopenScanner();
        });
        dlg.show();
        qty.requestFocus();
    }

    private void delayedReopenScanner() {
        if (!prefs.getBoolean("auto_reopen_scanner", true)) {
            if (currentInventory != null) showInventory(currentInventory);
            return;
        }
        if (root != null) {
            root.postDelayed(() -> {
                if (currentInventory != null) startScanner();
            }, 350);
        }
    }

    private void importXml(Uri uri) {
        String fileName = displayName(uri);
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("تعذر فتح الملف");

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document doc = factory.newDocumentBuilder().parse(in);
            doc.getDocumentElement().normalize();

            Map<String, String> groups = new HashMap<>();
            List<Element> groupNodes = elementsByNames(doc, "G", "Group", "MaterialGroup", "MatGroup");
            for (Element g : groupNodes) {
                String guid = firstText(g, "gPtr", "GroupGuid", "Guid", "ID", "Id");
                String name = firstText(g, "GroupName", "Name", "Group");
                if (!guid.isEmpty() && !name.isEmpty()) groups.put(guid, name);
            }

            List<Element> materialNodes = elementsByNames(doc, "M", "Material", "Product");
            if (materialNodes.isEmpty()) {
                for (Element e : allElements(doc)) {
                    if (!directText(e, "MatName", "MaterialName", "ProductName").isEmpty()) {
                        materialNodes.add(e);
                    }
                }
            }

            List<MaterialItem> imported = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Element e : materialNodes) {
                MaterialItem m = new MaterialItem();
                m.id = val(firstText(e, "mptr", "MatGuid", "MaterialGuid", "Guid", "ID", "Id"), UUID.randomUUID().toString());
                m.code = firstText(e, "MatCode", "MaterialCode", "ProductCode", "Code", "ItemCode");
                m.name = firstText(e, "MatName", "MaterialName", "ProductName", "Name", "ItemName");
                m.barcode = firstText(e, "MatBarCode", "MatBarcode", "BarCode", "Barcode", "EAN");
                m.barcode2 = firstText(e, "MatBarCode2", "MatBarcode2", "BarCode2", "Barcode2");
                m.barcode3 = firstText(e, "MatBarCode3", "MatBarcode3", "BarCode3", "Barcode3");
                m.unit = val(firstText(e, "MatUnity", "Unit", "UnitName", "MainUnit"), "وحدة");
                m.unit2 = firstText(e, "MatUnit2", "Unit2", "SecondUnit");
                m.unit3 = firstText(e, "MatUnit3", "Unit3", "ThirdUnit");
                m.factor2 = parseOr(firstText(e, "MatUnit2Factor", "Unit2Factor", "SecondUnitFactor"), 1);
                m.factor3 = parseOr(firstText(e, "MatUnit3Factor", "Unit3Factor", "ThirdUnitFactor"), 1);
                m.purchasePrice = parseOr(firstText(e, "PurchasePrice", "LastPurchase", "LastPurchasePrice", "MatPurchasePrice"), 0);
                m.sourceQty = parseOr(firstText(e, "SourceQuantity", "Quantity", "Qty", "BookQuantity"), 0);

                String groupGuid = firstText(e, "MatGroupGuid", "GroupGuid", "MaterialGroupGuid");
                String directGroup = firstText(e, "GroupName", "MaterialGroupName", "Group");
                m.group = val(groups.get(groupGuid), val(directGroup, "بدون مجموعة"));

                if (m.name.isEmpty() && m.code.isEmpty() && m.barcode.isEmpty()) continue;
                String dedupe = !m.id.isEmpty() ? m.id : (!m.code.isEmpty() ? "C:" + m.code : "B:" + m.barcode);
                if (seen.add(dedupe)) imported.add(m);
            }

            if (imported.isEmpty()) {
                throw new Exception("لم يتم العثور على مواد صالحة. يجب أن يحتوي ملف الأمين على MatName/MatCode أو ما يعادلهما.");
            }

            materials.clear();
            materials.addAll(imported);
            prefs.edit().putString("last_import_file", fileName).apply();
            saveData();

            new AlertDialog.Builder(this)
                    .setTitle("تم الاستيراد بنجاح")
                    .setMessage("الملف: " + fileName + "\nالمواد: " + imported.size() + "\nالمجموعات: " + countGroups() + "\n\nتمت قراءة الكود والاسم والباركودات والوحدة وآخر شراء وعوامل التحويل من XML.")
                    .setPositiveButton("حسنًا", (d, w) -> showMaterials())
                    .show();
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("تعذر استيراد XML")
                    .setMessage("الملف: " + fileName + "\n\n" + val(e.getMessage(), "صيغة XML غير متوافقة"))
                    .setPositiveButton("حسنًا", null)
                    .show();
        }
    }

    private List<Element> elementsByNames(Document doc, String... names) {
        Set<String> wanted = new HashSet<>();
        for (String n : names) wanted.add(n.toLowerCase(Locale.ROOT));
        List<Element> out = new ArrayList<>();
        NodeList all = doc.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Node n = all.item(i);
            if (!(n instanceof Element)) continue;
            Element e = (Element) n;
            if (wanted.contains(simpleTag(e).toLowerCase(Locale.ROOT))) out.add(e);
        }
        return out;
    }

    private List<Element> allElements(Document doc) {
        List<Element> out = new ArrayList<>();
        NodeList all = doc.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            if (all.item(i) instanceof Element) out.add((Element) all.item(i));
        }
        return out;
    }

    private static String simpleTag(Element e) {
        String n = e.getLocalName();
        if (n == null || n.isEmpty()) n = e.getTagName();
        int p = n.indexOf(':');
        return p >= 0 ? n.substring(p + 1) : n;
    }

    private static String directText(Element e, String... names) {
        Set<String> wanted = new HashSet<>();
        for (String n : names) wanted.add(n.toLowerCase(Locale.ROOT));
        NodeList kids = e.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n instanceof Element) {
                Element c = (Element) n;
                if (wanted.contains(simpleTag(c).toLowerCase(Locale.ROOT))) {
                    String v = c.getTextContent();
                    return v == null ? "" : v.trim();
                }
            }
        }
        return "";
    }

    private static String firstText(Element e, String... names) {
        String direct = directText(e, names);
        if (!direct.isEmpty()) return direct;
        Set<String> wanted = new HashSet<>();
        for (String n : names) wanted.add(n.toLowerCase(Locale.ROOT));
        NodeList all = e.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            if (all.item(i) instanceof Element) {
                Element c = (Element) all.item(i);
                if (wanted.contains(simpleTag(c).toLowerCase(Locale.ROOT))) {
                    String v = c.getTextContent();
                    return v == null ? "" : v.trim();
                }
            }
        }
        return "";
    }

    private String displayName(Uri uri) {
        String name = "ملف XML";
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int ix = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (ix >= 0) name = c.getString(ix);
            }
        } catch (Exception ignored) {
        }
        return val(name, "ملف XML");
    }

    private void exportInventory(InventorySession s) {
        pendingExportInventory = s;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/xml");
        i.putExtra(Intent.EXTRA_TITLE, "AL_BASIL_BM_" + safeName(s.name) + "_" + s.date + ".xml");
        startActivityForResult(i, REQ_EXPORT_XML);
    }

    private void writeInventoryXml(Uri uri, InventorySession s) {
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new Exception("تعذر إنشاء الملف");
            StringBuilder x = new StringBuilder();
            x.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            x.append("<ALBASILStockTake name=\"").append(xml(s.name)).append("\" date=\"").append(xml(s.date)).append("\">\n");
            for (MaterialItem m : materials) {
                Double q = s.counts.get(m.key());
                if (q == null) continue;
                x.append("  <Item><Code>").append(xml(m.code))
                        .append("</Code><Barcode>").append(xml(m.barcode))
                        .append("</Barcode><Name>").append(xml(m.name))
                        .append("</Name><Qty>").append(fmt(q))
                        .append("</Qty></Item>\n");
            }
            x.append("</ALBASILStockTake>");
            out.write(x.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            Toast.makeText(this, "تم تصدير الجرد", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "تعذر التصدير: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult scan = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (scan != null) {
            if (scan.getContents() != null) {
                vibrate();
                handleBarcode(scan.getContents(), true);
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            if (requestCode == REQ_IMPORT_XML) {
                importXml(data.getData());
            } else if (requestCode == REQ_EXPORT_XML && pendingExportInventory != null) {
                writeInventoryXml(data.getData(), pendingExportInventory);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (currentInventory != null) showInventories();
        else showHome();
    }

    private void loadData() {
        try {
            String s = prefs.getString("data", "{}");
            JSONObject root = new JSONObject(s);
            JSONArray ms = root.optJSONArray("materials");
            if (ms != null) {
                for (int i = 0; i < ms.length(); i++) materials.add(MaterialItem.from(ms.getJSONObject(i)));
            }
            JSONArray ins = root.optJSONArray("inventories");
            if (ins != null) {
                for (int i = 0; i < ins.length(); i++) inventories.add(InventorySession.from(ins.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
    }

    private void saveData() {
        try {
            JSONObject r = new JSONObject();
            JSONArray ms = new JSONArray();
            for (MaterialItem m : materials) ms.put(m.toJson());
            JSONArray ins = new JSONArray();
            for (InventorySession s : inventories) ins.put(s.toJson());
            r.put("materials", ms);
            r.put("inventories", ins);
            prefs.edit().putString("data", r.toString()).apply();
        } catch (Exception e) {
            Toast.makeText(this, "تعذر حفظ البيانات", Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout verticalBody() {
        ScrollView sc = new ScrollView(this);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sc.addView(body);
        return body;
    }

    private View menuButton(String icon, String label, int iconColor, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(8), dp(20), dp(8));
        row.setBackground(makeRounded(CARD, 10));
        TextView labelV = text(label, 23, Color.rgb(25, 25, 25), false);
        labelV.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        row.addView(labelV, new LinearLayout.LayoutParams(0, dp(82), 1f));
        TextView ic = text(icon, 32, iconColor, true);
        ic.setGravity(Gravity.CENTER);
        row.addView(ic, new LinearLayout.LayoutParams(dp(80), dp(82)));
        row.setOnClickListener(click);
        return row;
    }

    private Button wideButton(String s, int bg, int fg) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(21);
        b.setTextColor(fg);
        b.setAllCaps(false);
        b.setBackground(makeRounded(bg, 8));
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private Button smallButton(String s, int bg, int fg) {
        Button b = wideButton(s, bg, fg);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(18);
        e.setTextColor(Color.BLACK);
        e.setHintTextColor(Color.GRAY);
        e.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        e.setSingleLine(true);
        e.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return e;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        t.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return t;
    }

    private View space(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    private android.graphics.drawable.GradientDrawable makeRounded(int color, int r) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    private android.graphics.drawable.GradientDrawable makeBordered(int fill, int stroke, int sw, int r) {
        android.graphics.drawable.GradientDrawable g = makeRounded(fill, r);
        g.setStroke(dp(sw), stroke);
        return g;
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private int countGroups() {
        Set<String> s = new HashSet<>();
        for (MaterialItem m : materials) {
            if (!m.group.trim().isEmpty()) s.add(m.group);
        }
        return s.size();
    }

    private int countMaterialLines(InventorySession s) {
        return s.counts.size();
    }

    private void vibrate() {
        if (!prefs.getBoolean("vibrate_enabled", true)) return;
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE));
                else v.vibrate(70);
            }
        } catch (Exception ignored) {
        }
    }

    private static String val(String s, String d) {
        return s == null || s.trim().isEmpty() ? d : s.trim();
    }

    private static double parseOr(String s, double d) {
        try {
            if (s == null || s.trim().isEmpty()) return d;
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (Exception e) {
            return d;
        }
    }

    private static String fmt(double d) {
        return Math.rint(d) == d ? String.valueOf((long) d) : String.format(Locale.US, "%.3f", d).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String safeName(String s) {
        return s.replaceAll("[^\\p{L}\\p{N}_-]+", "_");
    }

    private static String xml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static class MaterialItem {
        String id = "";
        String code = "";
        String name = "";
        String group = "";
        String barcode = "";
        String barcode2 = "";
        String barcode3 = "";
        String unit = "وحدة";
        String unit2 = "";
        String unit3 = "";
        double factor2 = 1;
        double factor3 = 1;
        double purchasePrice = 0;
        double sourceQty = 0;

        String key() {
            return !id.isEmpty() ? id : (!code.isEmpty() ? code : (!barcode.isEmpty() ? barcode : name));
        }

        boolean matches(String value) {
            String v = value == null ? "" : value.trim();
            return v.equals(code) || v.equals(barcode) || v.equals(barcode2) || v.equals(barcode3);
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("code", code);
            o.put("name", name);
            o.put("group", group);
            o.put("barcode", barcode);
            o.put("barcode2", barcode2);
            o.put("barcode3", barcode3);
            o.put("unit", unit);
            o.put("unit2", unit2);
            o.put("unit3", unit3);
            o.put("factor2", factor2);
            o.put("factor3", factor3);
            o.put("price", purchasePrice);
            o.put("sourceQty", sourceQty);
            return o;
        }

        static MaterialItem from(JSONObject o) {
            MaterialItem m = new MaterialItem();
            m.id = o.optString("id");
            m.code = o.optString("code");
            m.name = o.optString("name");
            m.group = o.optString("group");
            m.barcode = o.optString("barcode");
            m.barcode2 = o.optString("barcode2");
            m.barcode3 = o.optString("barcode3");
            m.unit = o.optString("unit", "وحدة");
            m.unit2 = o.optString("unit2");
            m.unit3 = o.optString("unit3");
            m.factor2 = o.optDouble("factor2", 1);
            m.factor3 = o.optDouble("factor3", 1);
            m.purchasePrice = o.optDouble("price", 0);
            m.sourceQty = o.optDouble("sourceQty", 0);
            return m;
        }
    }

    static class InventorySession {
        String id = "";
        String name = "";
        String date = "";
        Map<String, Double> counts = new LinkedHashMap<>();

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", name);
            o.put("date", date);
            JSONObject c = new JSONObject();
            for (Map.Entry<String, Double> e : counts.entrySet()) c.put(e.getKey(), e.getValue());
            o.put("counts", c);
            return o;
        }

        static InventorySession from(JSONObject o) {
            InventorySession s = new InventorySession();
            s.id = o.optString("id");
            s.name = o.optString("name");
            s.date = o.optString("date");
            JSONObject c = o.optJSONObject("counts");
            if (c != null) {
                java.util.Iterator<String> it = c.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    s.counts.put(k, c.optDouble(k, 0));
                }
            }
            return s;
        }
    }
}
