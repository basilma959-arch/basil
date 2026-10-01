from pathlib import Path

p = Path('app/src/main/java/com/albasil/inventory/MainActivity.java')
s = p.read_text(encoding='utf-8')

# imports needed for special app access screen
if 'import android.provider.Settings;' not in s:
    s = s.replace('import android.provider.OpenableColumns;\n', 'import android.provider.OpenableColumns;\nimport android.provider.Settings;\n')
if 'import android.os.Environment;' not in s:
    s = s.replace('import android.os.Bundle;\n', 'import android.os.Bundle;\nimport android.os.Environment;\n')

if 'private static final int REQ_STORAGE_XML = 9101;' not in s:
    s = s.replace(
        'private static final int REQ_EXPORT_XML = 3002;',
        'private static final int REQ_EXPORT_XML = 3002;\n    private static final int REQ_STORAGE_XML = 9101;\n    private boolean pendingXmlAfterAllFilesAccess = false;'
    )
elif 'private boolean pendingXmlAfterAllFilesAccess = false;' not in s:
    s = s.replace('private static final int REQ_STORAGE_XML = 9101;', 'private static final int REQ_STORAGE_XML = 9101;\n    private boolean pendingXmlAfterAllFilesAccess = false;')

start = s.index('    private void chooseXml() {')
end = s.index('    private void confirmDeleteMaterials()', start)
choose = r'''    private void chooseXml() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                new AlertDialog.Builder(this)
                        .setTitle("السماح بالوصول إلى الملفات")
                        .setMessage("للبحث عن ملفات XML في أكبر نطاق متاح على الهاتف، فعّل للباسل BM خيار «السماح بالوصول إلى كل الملفات» من الشاشة التالية، ثم ارجع للتطبيق.")
                        .setNegativeButton("إلغاء", null)
                        .setPositiveButton("فتح الإعدادات", (d, w) -> openAllFilesAccessSettings())
                        .show();
                return;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE_XML);
            return;
        }
        showIndexedXmlFiles();
    }

    private void openAllFilesAccessSettings() {
        pendingXmlAfterAllFilesAccess = true;
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(i);
            } catch (Exception ex) {
                pendingXmlAfterAllFilesAccess = false;
                new AlertDialog.Builder(this)
                        .setTitle("تعذر فتح الإعدادات")
                        .setMessage("افتح إعدادات الهاتف > التطبيقات > وصول خاص > الوصول إلى كل الملفات، ثم فعّل الباسل BM.")
                        .setPositiveButton("حسنًا", null)
                        .show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingXmlAfterAllFilesAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pendingXmlAfterAllFilesAccess = false;
            if (Environment.isExternalStorageManager()) {
                showIndexedXmlFiles();
            }
        }
    }

    private void showIndexedXmlFiles() {
        new Thread(() -> {
            final List<StorageXmlFinder.Entry> entries = StorageXmlFinder.find(this);
            runOnUiThread(() -> {
                if (entries.isEmpty()) {
                    String msg = "لم يتم العثور على ملفات XML في التخزين الذي يسمح Android للتطبيق بقراءته.";
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                        msg += "\n\nيمكن توسيع البحث بتفعيل الوصول إلى كل الملفات.";
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("ملفات XML")
                            .setMessage(msg)
                            .setNegativeButton("إغلاق", null)
                            .setPositiveButton("إعادة البحث", (d, w) -> chooseXml())
                            .show();
                    return;
                }

                String[] names = new String[entries.size()];
                for (int i = 0; i < entries.size(); i++) names[i] = entries.get(i).name;

                final int[] selected = {-1};
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle("اختر الملف")
                        .setSingleChoiceItems(names, -1, (d, which) -> selected[0] = which)
                        .setNegativeButton("إلغاء", null)
                        .setPositiveButton("استيراد", null)
                        .create();
                dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    if (selected[0] < 0) {
                        Toast.makeText(this, "اختر ملف XML أولًا", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Uri chosen = entries.get(selected[0]).uri;
                    dialog.dismiss();
                    importXml(chosen);
                }));
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
print('Patched XML discovery with all-files access flow')
