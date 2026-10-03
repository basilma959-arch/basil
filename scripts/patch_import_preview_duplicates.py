from pathlib import Path

p = Path('app/src/main/java/com/albasil/inventory/MainActivity.java')
s = p.read_text(encoding='utf-8')

start = s.index('    private void importXml(Uri uri) {')
end = s.index('    private List<Element> elementsByNames', start)

block = r'''    private void importXml(Uri uri) {
        String fileName = displayName(uri);
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("تعذر فتح الملف");

            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) buffer.write(chunk, 0, n);
            byte[] xmlBytes = buffer.toByteArray();

            BasilXmlReader.Result result = null;
            Exception firstError = null;
            try {
                result = BasilXmlReader.read(new java.io.ByteArrayInputStream(xmlBytes));
            } catch (Exception e) {
                firstError = e;
            }

            String detectedType = "XML المواد";
            if (result == null || result.materials.isEmpty()) {
                try {
                    result = SpreadsheetXmlReader.read(new java.io.ByteArrayInputStream(xmlBytes));
                    detectedType = "Excel XML / SpreadsheetML";
                } catch (Exception spreadsheetError) {
                    if (firstError != null) spreadsheetError.addSuppressed(firstError);
                    throw spreadsheetError;
                }
            }

            if (result == null || result.materials.isEmpty()) {
                throw new Exception("لم يتم العثور على مواد قابلة للاستيراد داخل ملف XML");
            }

            showImportPreview(fileName, detectedType, result);
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("تعذر قراءة XML")
                    .setMessage("الملف: " + fileName + "\n\n" + (e.getMessage() == null ? "ملف XML غير صالح أو غير مدعوم" : e.getMessage()))
                    .setPositiveButton("حسنًا", null)
                    .show();
        }
    }

    private void showImportPreview(String fileName, String detectedType, BasilXmlReader.Result result) {
        int duplicateCount = countIncomingDuplicates(result.materials);
        StringBuilder msg = new StringBuilder();
        msg.append("الملف: ").append(fileName)
                .append("\nالنوع: ").append(detectedType)
                .append("\nالمواد في الملف: ").append(result.materials.size())
                .append("\nالمواد الحالية: ").append(materials.size())
                .append("\nالمكرر مع القائمة الحالية: ").append(duplicateCount);
        if (result.groupCount > 0) msg.append("\nالمجموعات في الملف: ").append(result.groupCount);

        msg.append("\n\nمعاينة أول ").append(Math.min(5, result.materials.size())).append(" أصناف:");
        for (int i = 0; i < result.materials.size() && i < 5; i++) {
            MaterialItem m = result.materials.get(i);
            msg.append("\n\n").append(i + 1).append("- ")
                    .append(val(m.name, "بدون اسم"));
            if (m.code != null && !m.code.trim().isEmpty()) msg.append("\n   الكود: ").append(m.code);
            if (m.barcode != null && !m.barcode.trim().isEmpty()) msg.append("\n   الباركود: ").append(m.barcode);
            if (m.group != null && !m.group.trim().isEmpty()) msg.append("\n   المجموعة: ").append(m.group);
        }

        new AlertDialog.Builder(this)
                .setTitle("معاينة قبل الاستيراد")
                .setMessage(msg.toString())
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("متابعة", (d, w) -> showImportModeDialog(fileName, detectedType, result))
                .show();
    }

    private void showImportModeDialog(String fileName, String detectedType, BasilXmlReader.Result result) {
        final String[] options = new String[]{
                "دمج وتحديث المكرر (موصى به)",
                "دمج وتجاهل المكرر",
                "استبدال جميع المواد الحالية",
                "إضافة الكل كمواد جديدة"
        };
        final int[] selected = {0};

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("طريقة الاستيراد")
                .setSingleChoiceItems(options, 0, (d, which) -> selected[0] = which)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("تنفيذ", null)
                .create();

        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int mode = selected[0];
            dialog.dismiss();
            if (mode == 2 && !materials.isEmpty()) {
                new AlertDialog.Builder(this)
                        .setTitle("تأكيد الاستبدال الكامل")
                        .setMessage("سيتم حذف قائمة المواد الحالية واستبدالها بمواد الملف.\n\nالجردات القديمة ستظل محفوظة، لكن المواد المحذوفة قد لا تظهر في شاشات تعتمد على قائمة المواد الحالية.\n\nهل تريد المتابعة؟")
                        .setNegativeButton("إلغاء", null)
                        .setPositiveButton("استبدال", (d, w) -> applyImportMode(fileName, detectedType, result, mode))
                        .show();
            } else {
                applyImportMode(fileName, detectedType, result, mode);
            }
        }));
        dialog.show();
    }

    private void applyImportMode(String fileName, String detectedType, BasilXmlReader.Result result, int mode) {
        int added = 0;
        int updated = 0;
        int skipped = 0;

        if (mode == 2) {
            materials.clear();
            for (MaterialItem incoming : result.materials) {
                MaterialItem copy = copyMaterial(incoming);
                if (copy.id == null || copy.id.trim().isEmpty() || materialIdExists(copy.id)) {
                    copy.id = UUID.randomUUID().toString();
                }
                materials.add(copy);
                added++;
            }
        } else if (mode == 3) {
            for (MaterialItem incoming : result.materials) {
                MaterialItem copy = copyMaterial(incoming);
                copy.id = UUID.randomUUID().toString();
                materials.add(copy);
                added++;
            }
        } else {
            for (MaterialItem incoming : result.materials) {
                MaterialItem existing = findDuplicateMaterial(incoming);
                if (existing != null) {
                    if (mode == 0) {
                        updateMaterialPreserveId(existing, incoming);
                        updated++;
                    } else {
                        skipped++;
                    }
                } else {
                    MaterialItem copy = copyMaterial(incoming);
                    if (copy.id == null || copy.id.trim().isEmpty() || materialIdExists(copy.id)) {
                        copy.id = UUID.randomUUID().toString();
                    }
                    materials.add(copy);
                    added++;
                }
            }
        }

        prefs.edit().putString("last_import_file", fileName).apply();
        saveData();

        String modeText = mode == 0 ? "دمج وتحديث المكرر" :
                mode == 1 ? "دمج وتجاهل المكرر" :
                mode == 2 ? "استبدال كامل" : "إضافة الكل كجديد";

        StringBuilder summary = new StringBuilder();
        summary.append("الملف: ").append(fileName)
                .append("\nالنوع: ").append(detectedType)
                .append("\nالطريقة: ").append(modeText)
                .append("\n\nتمت الإضافة: ").append(added);
        if (updated > 0) summary.append("\nتم تحديث المكرر: ").append(updated);
        if (skipped > 0) summary.append("\nتم تجاهل المكرر: ").append(skipped);
        summary.append("\nإجمالي المواد الآن: ").append(materials.size());

        new AlertDialog.Builder(this)
                .setTitle("تم الاستيراد بنجاح")
                .setMessage(summary.toString())
                .setPositiveButton("حسنًا", (d, w) -> showMaterials())
                .show();
    }

    private int countIncomingDuplicates(List<MaterialItem> incoming) {
        int count = 0;
        for (MaterialItem m : incoming) if (findDuplicateMaterial(m) != null) count++;
        return count;
    }

    private MaterialItem findDuplicateMaterial(MaterialItem incoming) {
        if (incoming == null) return null;
        for (MaterialItem existing : materials) {
            if (sameNonEmpty(existing.code, incoming.code)) return existing;
            if (sharesBarcode(existing, incoming)) return existing;
        }
        return null;
    }

    private boolean sharesBarcode(MaterialItem a, MaterialItem b) {
        String[] aa = new String[]{a.barcode, a.barcode2, a.barcode3};
        String[] bb = new String[]{b.barcode, b.barcode2, b.barcode3};
        for (String x : aa) {
            if (x == null || x.trim().isEmpty()) continue;
            for (String y : bb) if (sameNonEmpty(x, y)) return true;
        }
        return false;
    }

    private boolean sameNonEmpty(String a, String b) {
        return a != null && b != null && !a.trim().isEmpty() && !b.trim().isEmpty() && a.trim().equalsIgnoreCase(b.trim());
    }

    private boolean materialIdExists(String id) {
        if (id == null || id.trim().isEmpty()) return false;
        for (MaterialItem m : materials) if (id.equals(m.id)) return true;
        return false;
    }

    private MaterialItem copyMaterial(MaterialItem src) {
        MaterialItem m = new MaterialItem();
        m.id = src.id;
        m.code = src.code;
        m.name = src.name;
        m.group = src.group;
        m.barcode = src.barcode;
        m.barcode2 = src.barcode2;
        m.barcode3 = src.barcode3;
        m.unit = src.unit;
        m.unit2 = src.unit2;
        m.unit3 = src.unit3;
        m.factor2 = src.factor2;
        m.factor3 = src.factor3;
        m.purchasePrice = src.purchasePrice;
        m.sourceQty = src.sourceQty;
        return m;
    }

    private void updateMaterialPreserveId(MaterialItem target, MaterialItem src) {
        String keepId = target.id;
        target.code = val(src.code, target.code);
        target.name = val(src.name, target.name);
        target.group = val(src.group, target.group);
        target.barcode = val(src.barcode, target.barcode);
        target.barcode2 = val(src.barcode2, target.barcode2);
        target.barcode3 = val(src.barcode3, target.barcode3);
        target.unit = val(src.unit, target.unit);
        target.unit2 = val(src.unit2, target.unit2);
        target.unit3 = val(src.unit3, target.unit3);
        target.factor2 = src.factor2;
        target.factor3 = src.factor3;
        target.purchasePrice = src.purchasePrice;
        target.sourceQty = src.sourceQty;
        target.id = keepId;
    }

'''

s = s[:start] + block + s[end:]
p.write_text(s, encoding='utf-8')
print('Patched XML import preview and duplicate handling')
