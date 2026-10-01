from pathlib import Path
p=Path('app/src/main/java/com/albasil/inventory/MainActivity.java')
s=p.read_text(encoding='utf-8')

needle='        body.addView(scanRow);\n\n        barcode.setOnEditorActionListener'
insert='''        body.addView(scanRow);\n\n        Button manualAdd = wideButton("+   مادة جديدة", Color.rgb(16, 163, 74), Color.WHITE);\n        manualAdd.setOnClickListener(v -> manualItemDialog(session));\n        body.addView(manualAdd, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));\n        body.addView(space(10));\n\n        barcode.setOnEditorActionListener'''
if needle not in s: raise SystemExit('insert point not found')
s=s.replace(needle,insert,1)

# Show imported materials plus manual items belonging to this inventory.
old='''        Map<String, List<MaterialItem>> grouped = new LinkedHashMap<>();
        for (MaterialItem m : materials) {
            grouped.computeIfAbsent(m.group.isEmpty() ? "بدون مجموعة" : m.group, k -> new ArrayList<>()).add(m);
        }'''
new='''        Map<String, List<MaterialItem>> grouped = new LinkedHashMap<>();
        List<MaterialItem> visibleMaterials = new ArrayList<>();
        visibleMaterials.addAll(materials);
        visibleMaterials.addAll(session.manualItems);
        for (MaterialItem m : visibleMaterials) {
            grouped.computeIfAbsent(m.group.isEmpty() ? "بدون مجموعة" : m.group, k -> new ArrayList<>()).add(m);
        }'''
if old not in s: raise SystemExit('group block not found')
s=s.replace(old,new,1)

# Search barcode/code in permanent materials and current inventory manual items.
old='''        MaterialItem found = null;
        for (MaterialItem m : materials) {
            if (m.matches(code)) {
                found = m;
                break;
            }
        }'''
new='''        MaterialItem found = null;
        List<MaterialItem> searchable = new ArrayList<>();
        searchable.addAll(materials);
        if (currentInventory != null) searchable.addAll(currentInventory.manualItems);
        for (MaterialItem m : searchable) {
            if (m.matches(code)) {
                found = m;
                break;
            }
        }'''
if old not in s: raise SystemExit('barcode search block not found')
s=s.replace(old,new,1)

marker='    private void startScanner() {'
method='''    private void manualItemDialog(InventorySession session) {\n        LinearLayout box = new LinearLayout(this);\n        box.setOrientation(LinearLayout.VERTICAL);\n        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);\n        box.setPadding(dp(22), dp(8), dp(22), dp(4));\n\n        EditText name = input("اسم المادة *");\n        EditText code = input("الكود");\n        EditText barcode = input("الباركود");\n        EditText unit = input("الوحدة");\n        unit.setText("وحدة");\n        EditText group = input("المجموعة");\n        EditText qty = input("الكمية *");\n        qty.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);\n        qty.setText("1");\n        CheckBox saveMaterial = new CheckBox(this);\n        saveMaterial.setText("حفظ المادة ضمن قائمة المواد للجردات القادمة");\n        saveMaterial.setChecked(false);\n\n        box.addView(name); box.addView(code); box.addView(barcode);\n        box.addView(unit); box.addView(group); box.addView(qty); box.addView(saveMaterial);\n\n        AlertDialog dlg = new AlertDialog.Builder(this)\n                .setTitle("إضافة مادة جديدة")\n                .setView(box)\n                .setNegativeButton("إلغاء", null)\n                .setPositiveButton("إضافة", null)\n                .create();\n        dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {\n            String nm = name.getText().toString().trim();\n            if (nm.isEmpty()) { name.setError("أدخل اسم المادة"); return; }\n            double q;\n            try { q = Double.parseDouble(qty.getText().toString().trim().replace(',', '.')); }\n            catch (Exception e) { qty.setError("أدخل كمية صحيحة"); return; }\n\n            MaterialItem m = new MaterialItem();\n            m.id = "manual_" + UUID.randomUUID();\n            m.name = nm;\n            m.code = code.getText().toString().trim();\n            m.barcode = barcode.getText().toString().trim();\n            m.unit = val(unit.getText().toString(), "وحدة");\n            m.group = val(group.getText().toString(), "مواد يدوية");\n\n            if (saveMaterial.isChecked()) materials.add(m);\n            else session.manualItems.add(m);\n\n            session.counts.put(m.key(), q);\n            saveData();\n            dlg.dismiss();\n            showInventory(session);\n        }));\n        dlg.show();\n        name.requestFocus();\n    }\n\n'''
if marker not in s: raise SystemExit('startScanner marker not found')
s=s.replace(marker,method+marker,1)

# Export both permanent and inventory-specific manual items.
old='''            for (MaterialItem m : materials) {
                Double q = s.counts.get(m.key());
                if (q == null) continue;'''
new='''            List<MaterialItem> exportMaterials = new ArrayList<>();
            exportMaterials.addAll(materials);
            exportMaterials.addAll(s.manualItems);
            for (MaterialItem m : exportMaterials) {
                Double q = s.counts.get(m.key());
                if (q == null) continue;'''
if old not in s: raise SystemExit('export materials block not found')
s=s.replace(old,new,1)

# Persist manual items inside each inventory session.
old='''        Map<String, Double> counts = new LinkedHashMap<>();

        JSONObject toJson() throws Exception {'''
new='''        Map<String, Double> counts = new LinkedHashMap<>();
        List<MaterialItem> manualItems = new ArrayList<>();

        JSONObject toJson() throws Exception {'''
if old not in s: raise SystemExit('inventory fields block not found')
s=s.replace(old,new,1)

old='''            o.put("counts", c);
            return o;'''
new='''            o.put("counts", c);
            JSONArray mi = new JSONArray();
            for (MaterialItem m : manualItems) mi.put(m.toJson());
            o.put("manualItems", mi);
            return o;'''
if old not in s: raise SystemExit('inventory toJson block not found')
s=s.replace(old,new,1)

old='''            if (c != null) {
                java.util.Iterator<String> it = c.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    s.counts.put(k, c.optDouble(k, 0));
                }
            }
            return s;'''
new='''            if (c != null) {
                java.util.Iterator<String> it = c.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    s.counts.put(k, c.optDouble(k, 0));
                }
            }
            JSONArray mi = o.optJSONArray("manualItems");
            if (mi != null) {
                for (int i = 0; i < mi.length(); i++) s.manualItems.add(MaterialItem.from(mi.getJSONObject(i)));
            }
            return s;'''
if old not in s: raise SystemExit('inventory fromJson block not found')
s=s.replace(old,new,1)

p.write_text(s,encoding='utf-8')
print('manual item patch ready')
