from pathlib import Path

p = Path('app/src/main/java/com/albasil/inventory/MainActivity.java')
s = p.read_text(encoding='utf-8')
start = s.index('    private void importXml(Uri uri) {')
end = s.index('    private List<Element> elementsByNames(', start)
new_method = r'''    private void importXml(Uri uri) {
        String fileName = displayName(uri);
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("تعذر فتح الملف");

            AmeenXmlReader.Result result = AmeenXmlReader.read(in);
            if (result.materials.isEmpty()) {
                throw new Exception("لم يتم العثور على مواد داخل ملف XML");
            }

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
                    .setTitle("تعذر استيراد XML")
                    .setMessage("الملف: " + fileName + "\n\n" + val(e.getMessage(), "ملف XML غير صالح"))
                    .setPositiveButton("حسنًا", null)
                    .show();
        }
    }

'''
p.write_text(s[:start] + new_method + s[end:], encoding='utf-8')
print('Patched MainActivity to use AmeenXmlReader')
