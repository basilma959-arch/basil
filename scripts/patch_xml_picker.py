from pathlib import Path

p = Path('app/src/main/java/com/albasil/inventory/MainActivity.java')
s = p.read_text(encoding='utf-8')
start = s.index('    private void chooseXml() {')
end = s.index('    private void confirmDeleteMaterials()', start)
new_method = '''    private void chooseXml() {
        // Show every document because many Android providers label XML files with
        // inconsistent MIME types. The app validates/parses XML after selection.
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_IMPORT_XML);
        } catch (Exception e) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.setType("*/*");
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(Intent.createChooser(fallback, "اختر ملف XML"), REQ_IMPORT_XML);
        }
    }

'''
p.write_text(s[:start] + new_method + s[end:], encoding='utf-8')
