from pathlib import Path

p = Path('app/src/main/java/com/albasil/inventory/MainActivity.java')
s = p.read_text(encoding='utf-8')

start = s.index('    private void importXml(Uri uri) {')
end = s.index('    private List<Element> elementsByNames', start)

importer = r'''    private void importXml(Uri uri) {
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

            materials.clear();
            materials.addAll(result.materials);
            prefs.edit().putString("last_import_file", fileName).apply();
            saveData();

            new AlertDialog.Builder(this)
                    .setTitle("تم الاستيراد بنجاح")
                    .setMessage("الملف: " + fileName + "\nالنوع: " + detectedType + "\nالمواد: " + result.materials.size() + "\nالمجموعات: " + result.groupCount)
                    .setPositiveButton("حسنًا", (d, w) -> showMaterials())
                    .show();
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("تعذر قراءة XML")
                    .setMessage("الملف: " + fileName + "\n\n" + (e.getMessage() == null ? "ملف XML غير صالح أو غير مدعوم" : e.getMessage()))
                    .setPositiveButton("حسنًا", null)
                    .show();
        }
    }

'''

s = s[:start] + importer + s[end:]
p.write_text(s, encoding='utf-8')
print('Patched automatic SpreadsheetML fallback')
