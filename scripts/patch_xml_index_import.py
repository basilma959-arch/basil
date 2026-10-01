from pathlib import Path

p = Path('app/src/main/java/com/albasil/inventory/MainActivity.java')
s = p.read_text(encoding='utf-8')

if 'private static final int REQ_STORAGE_XML = 9101;' not in s:
    s = s.replace(
        'private static final int REQ_EXPORT_XML = 3002;',
        'private static final int REQ_EXPORT_XML = 3002;\n    private static final int REQ_STORAGE_XML = 9101;'
    )

start = s.index('    private void chooseXml() {')
end = s.index('    private void confirmDeleteMaterials()', start)
choose = r'''    private void chooseXml() {
        if (Build.VERSION.SDK_INT <= 32 && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE_XML);
            return;
        }
        showIndexedXmlFiles();
    }

    private void showIndexedXmlFiles() {
        new Thread(() -> {
            final List<StorageXmlFinder.Entry> entries = StorageXmlFinder.find(this);
            runOnUiThread(() -> {
                if (entries.isEmpty()) {
                    new AlertDialog.Builder(this)
                            .setTitle("ملفات XML")
                            .setMessage("لم يعثر Android على ملفات XML في التخزين المتاح للتطبيق.")
                            .setPositiveButton("حسنًا", null)
                            .show();
                    return;
                }

                String[] names = new String[entries.size()];
                for (int i = 0; i < entries.size(); i++) names[i] = entries.get(i).name;

                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle("اختر الملف")
                        .setSingleChoiceItems(names, -1, (d, which) -> {
                            d.dismiss();
                            importXml(entries.get(which).uri);
                        })
                        .setNegativeButton("إلغاء", null)
                        .create();
                dialog.show();
            });
        }).start();
    }

'''
s = s[:start] + choose + s[end:]

start = s.index('    private void importXml(Uri uri) {')
end = s.index('    private List<Element> elementsByNames', start)
importer = r'''    private void importXml(Uri uri) {
        String fileName = displayName(uri);
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("تعذر فتح الملف");

            BasilXmlReader.Result result = BasilXmlReader.read(in);
            if (result.materials.isEmpty()) throw new Exception("لم يتم العثور على مواد داخل ملف XML");

            materials.clear();
            materials.addAll(result.materials);
            prefs.edit().putString("last_import_file", fileName).apply();
            saveData();

            new AlertDialog.Builder(this)
                    .setTitle("تم الاستيراد بنجاح")
                    .setMessage("الملف: " + fileName + "\nالمواد: " + result.materials.size() + "\nالمجموعات: " + result.groupCount)
                    .setPositiveButton("حسنًا", (d, w) -> showMaterials())
                    .show();
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("تعذر قراءة XML")
                    .setMessage("الملف: " + fileName + "\n\n" + (e.getMessage() == null ? "ملف XML غير صالح" : e.getMessage()))
                    .setPositiveButton("حسنًا", null)
                    .show();
        }
    }

'''
s = s[:start] + importer + s[end:]

s = s.replace('الاستيراد: ملفات XML الخاصة بالأمين.', 'الاستيراد: ملفات XML مباشرة من الهاتف.')
s = s.replace('الأمين', 'الباسل BM')

p.write_text(s, encoding='utf-8')
print('Patched internal indexed XML picker')
