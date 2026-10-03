from pathlib import Path

p = Path('app/src/main/java/com/albasil/inventory/MainActivity.java')
s = p.read_text(encoding='utf-8')

# Mark manual inventory-only items and attach long-press actions.
old = '''                TextView n = text(m.name, 18, q == null ? Color.DKGRAY : PINK, true);
                n.setGravity(Gravity.RIGHT);
                row.addView(n);
                String mainBarcode = !m.barcode.isEmpty() ? m.barcode : (!m.barcode2.isEmpty() ? m.barcode2 : m.barcode3);
                TextView info = text("الكمية: " + (q == null ? "0" : fmt(q)) + "    الوحدة: " + m.unit + "    الكود: " + m.code + "    الباركود: " + mainBarcode, 13, Color.GRAY, false);
                info.setGravity(Gravity.RIGHT);
                row.addView(info);
                row.setOnClickListener(v -> quantityDialog(m, false));
                body.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)));'''
new = '''                boolean manualOnly = session.manualItems.contains(m);
                TextView n = text(m.name + (manualOnly ? "   [يدوي]" : ""), 18, q == null ? Color.DKGRAY : PINK, true);
                n.setGravity(Gravity.RIGHT);
                row.addView(n);
                String mainBarcode = !m.barcode.isEmpty() ? m.barcode : (!m.barcode2.isEmpty() ? m.barcode2 : m.barcode3);
                TextView info = text("الكمية: " + (q == null ? "0" : fmt(q)) + "    الوحدة: " + m.unit + "    الكود: " + m.code + "    الباركود: " + mainBarcode + (manualOnly ? "    • ضغط مطول للتعديل" : ""), 13, Color.GRAY, false);
                info.setGravity(Gravity.RIGHT);
                row.addView(info);
                row.setOnClickListener(v -> quantityDialog(m, false));
                if (manualOnly) {
                    row.setOnLongClickListener(v -> {
                        manualItemActions(session, m);
                        return true;
                    });
                }
                body.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)));'''
if old not in s:
    raise SystemExit('manual material row block not found')
s = s.replace(old, new, 1)

marker = '    private void startScanner() {'
methods = r'''    private void manualItemActions(InventorySession session, MaterialItem m) {
        new AlertDialog.Builder(this)
                .setTitle(m.name)
                .setItems(new String[]{"تعديل المادة", "حفظ ضمن قائمة المواد الدائمة", "حذف من الجرد"}, (d, which) -> {
                    if (which == 0) {
                        editManualItemDialog(session, m);
                    } else if (which == 1) {
                        promoteManualItem(session, m);
                    } else {
                        confirmDeleteManualItem(session, m);
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void editManualItemDialog(InventorySession session, MaterialItem m) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(dp(22), dp(8), dp(22), dp(4));

        EditText name = input("اسم المادة *");
        name.setText(m.name);
        EditText code = input("الكود");
        code.setText(m.code);
        EditText barcode = input("الباركود");
        barcode.setText(m.barcode);
        EditText unit = input("الوحدة");
        unit.setText(val(m.unit, "وحدة"));
        EditText group = input("المجموعة");
        group.setText(val(m.group, "مواد يدوية"));
        EditText qty = input("الكمية *");
        qty.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Double oldQty = session.counts.get(m.key());
        qty.setText(oldQty == null ? "1" : fmt(oldQty));

        box.addView(name);
        box.addView(code);
        box.addView(barcode);
        box.addView(unit);
        box.addView(group);
        box.addView(qty);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("تعديل المادة اليدوية")
                .setView(box)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", null)
                .create();

        dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String nm = name.getText().toString().trim();
            if (nm.isEmpty()) {
                name.setError("أدخل اسم المادة");
                return;
            }

            double q;
            try {
                q = Double.parseDouble(qty.getText().toString().trim().replace(',', '.'));
            } catch (Exception e) {
                qty.setError("أدخل كمية صحيحة");
                return;
            }

            String newCode = code.getText().toString().trim();
            String newBarcode = barcode.getText().toString().trim();
            MaterialItem conflict = findIdentityConflict(session, m, newCode, newBarcode);
            if (conflict != null) {
                new AlertDialog.Builder(this)
                        .setTitle("بيانات مكررة")
                        .setMessage("يوجد صنف آخر بنفس الكود أو الباركود:\n" + conflict.name + "\n\nغيّر الكود/الباركود ثم أعد الحفظ.")
                        .setPositiveButton("حسنًا", null)
                        .show();
                return;
            }

            m.name = nm;
            m.code = newCode;
            m.barcode = newBarcode;
            m.unit = val(unit.getText().toString(), "وحدة");
            m.group = val(group.getText().toString(), "مواد يدوية");
            session.counts.put(m.key(), q);
            saveData();
            dlg.dismiss();
            showInventory(session);
        }));
        dlg.show();
        name.requestFocus();
    }

    private MaterialItem findIdentityConflict(InventorySession session, MaterialItem self, String code, String barcode) {
        for (MaterialItem x : materials) {
            if (x == self) continue;
            if (sameManualIdentity(x, code, barcode)) return x;
        }
        for (MaterialItem x : session.manualItems) {
            if (x == self) continue;
            if (sameManualIdentity(x, code, barcode)) return x;
        }
        return null;
    }

    private boolean sameManualIdentity(MaterialItem x, String code, String barcode) {
        if (code != null && !code.trim().isEmpty() && x.code != null && code.trim().equalsIgnoreCase(x.code.trim())) return true;
        if (barcode != null && !barcode.trim().isEmpty()) {
            String b = barcode.trim();
            if (x.barcode != null && b.equalsIgnoreCase(x.barcode.trim())) return true;
            if (x.barcode2 != null && b.equalsIgnoreCase(x.barcode2.trim())) return true;
            if (x.barcode3 != null && b.equalsIgnoreCase(x.barcode3.trim())) return true;
        }
        return false;
    }

    private void promoteManualItem(InventorySession session, MaterialItem m) {
        MaterialItem existing = null;
        for (MaterialItem x : materials) {
            if (sameManualIdentity(x, m.code, m.barcode)) {
                existing = x;
                break;
            }
        }

        if (existing == null) {
            session.manualItems.remove(m);
            materials.add(m);
            saveData();
            Toast.makeText(this, "تم حفظ المادة ضمن قائمة المواد الدائمة", Toast.LENGTH_SHORT).show();
            showInventory(session);
            return;
        }

        final MaterialItem duplicate = existing;
        new AlertDialog.Builder(this)
                .setTitle("الصنف موجود بالفعل")
                .setMessage("يوجد صنف دائم بنفس الكود أو الباركود:\n" + duplicate.name + "\n\nهل تريد دمج المادة اليدوية معه؟ ستظل الكمية المسجلة في الجرد محفوظة.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("دمج", (d, w) -> {
                    Double q = session.counts.remove(m.key());
                    if (q != null) session.counts.put(duplicate.key(), q);

                    duplicate.name = val(m.name, duplicate.name);
                    duplicate.code = val(m.code, duplicate.code);
                    duplicate.barcode = val(m.barcode, duplicate.barcode);
                    duplicate.unit = val(m.unit, duplicate.unit);
                    duplicate.group = val(m.group, duplicate.group);
                    session.manualItems.remove(m);
                    saveData();
                    Toast.makeText(this, "تم دمج المادة مع الصنف الدائم", Toast.LENGTH_SHORT).show();
                    showInventory(session);
                })
                .show();
    }

    private void confirmDeleteManualItem(InventorySession session, MaterialItem m) {
        new AlertDialog.Builder(this)
                .setTitle("حذف المادة اليدوية")
                .setMessage("حذف «" + m.name + "» من هذه الجردة؟\n\nسيتم حذف الكمية المسجلة لهذه المادة من الجرد أيضًا.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حذف", (d, w) -> {
                    session.counts.remove(m.key());
                    session.manualItems.remove(m);
                    saveData();
                    showInventory(session);
                })
                .show();
    }

'''
if marker not in s:
    raise SystemExit('startScanner marker not found')
s = s.replace(marker, methods + marker, 1)

p.write_text(s, encoding='utf-8')
print('Patched manual item management')
