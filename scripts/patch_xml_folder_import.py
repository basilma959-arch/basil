from pathlib import Path

p = Path('app/src/main/java/com/albasil/inventory/MainActivity.java')
s = p.read_text(encoding='utf-8')

# Imports
if 'import android.provider.DocumentsContract;' not in s:
    s = s.replace('import android.provider.OpenableColumns;\n', 'import android.provider.OpenableColumns;\nimport android.provider.DocumentsContract;\n')

# Request code
s = s.replace('private static final int REQ_EXPORT_XML = 3002;', 'private static final int REQ_EXPORT_XML = 3002;\n    private static final int REQ_IMPORT_XML_FOLDER = 3003;')

# Folder picker only -- never opens gallery/images
start = s.index('    private void chooseXml() {')
end = s.index('    private void confirmDeleteMaterials()', start)
choose = '''    private void chooseXml() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_IMPORT_XML_FOLDER);
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("اختيار مجلد XML")
                    .setMessage("تعذر فتح مدير الملفات على هذا الهاتف.")
                    .setPositiveButton("حسنًا", null)
                    .show();
        }
    }

    private void showXmlFilesInFolder(Uri treeUri) {
        final ArrayList<String> names = new ArrayList<>();
        final ArrayList<Uri> uris = new ArrayList<>();
        Cursor c = null;
        try {
            String treeId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeId);
            String[] projection = new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
            };
            c = getContentResolver().query(children, projection, null, null, DocumentsContract.Document.COLUMN_DISPLAY_NAME + " ASC");
            if (c != null) {
                int idIx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
                int nameIx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                int mimeIx = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
                while (c.moveToNext()) {
                    String docId = idIx >= 0 ? c.getString(idIx) : "";
                    String name = nameIx >= 0 ? c.getString(nameIx) : "";
                    String mime = mimeIx >= 0 ? c.getString(mimeIx) : "";
                    if (name == null) name = "";
                    String lower = name.toLowerCase(Locale.ROOT);
                    boolean isXml = lower.endsWith(".xml") || "application/xml".equalsIgnoreCase(mime) || "text/xml".equalsIgnoreCase(mime);
                    if (isXml && docId != null && !docId.isEmpty()) {
                        names.add(name);
                        uris.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, docId));
                    }
                }
            }
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("قراءة المجلد")
                    .setMessage("تعذر قراءة ملفات XML داخل المجلد: " + (e.getMessage() == null ? "" : e.getMessage()))
                    .setPositiveButton("حسنًا", null)
                    .show();
            return;
        } finally {
            if (c != null) c.close();
        }

        if (names.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("لا توجد ملفات XML")
                    .setMessage("لم يتم العثور على أي ملف ينتهي بـ .xml داخل المجلد الذي اخترته.")
                    .setNegativeButton("إلغاء", null)
                    .setPositiveButton("اختيار مجلد آخر", (d, w) -> chooseXml())
                    .show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("اختر ملف XML")
                .setItems(names.toArray(new String[0]), (d, which) -> importXml(uris.get(which)))
                .setNegativeButton("إلغاء", null)
                .show();
    }

'''
s = s[:start] + choose + s[end:]

# Replace XML importer with direct AL BASIL reader.
start = s.index('    private void importXml(Uri uri) {')
end = s.index('    private List<Element> elementsByNames', start)
importer = '''    private void importXml(Uri uri) {
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
                    .setMessage("الملف: " + fileName + "\\nالمواد: " + result.materials.size() + "\\nالمجموعات: " + result.groupCount)
                    .setPositiveButton("حسنًا", (d, w) -> showMaterials())
                    .show();
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("تعذر قراءة XML")
                    .setMessage("الملف: " + fileName + "\\n\\n" + (e.getMessage() == null ? "ملف XML غير صالح" : e.getMessage()))
                    .setPositiveButton("حسنًا", null)
                    .show();
        }
    }

'''
s = s[:start] + importer + s[end:]

# Handle folder result before generic file result.
old = '''        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            if (requestCode == REQ_IMPORT_XML) {
                importXml(data.getData());
            } else if (requestCode == REQ_EXPORT_XML && pendingExportInventory != null) {
                writeInventoryXml(data.getData(), pendingExportInventory);
            }
        }'''
new = '''        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            if (requestCode == REQ_IMPORT_XML_FOLDER) {
                Uri treeUri = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {}
                showXmlFilesInFolder(treeUri);
            } else if (requestCode == REQ_IMPORT_XML) {
                importXml(data.getData());
            } else if (requestCode == REQ_EXPORT_XML && pendingExportInventory != null) {
                writeInventoryXml(data.getData(), pendingExportInventory);
            }
        }'''
if old not in s:
    raise SystemExit('onActivityResult block not found')
s = s.replace(old, new)

# Remove visible old-brand wording from About text.
s = s.replace('الاستيراد: ملفات XML الخاصة بالأمين.', 'الاستيراد: ملفات XML مباشرة من الهاتف.')

p.write_text(s, encoding='utf-8')
