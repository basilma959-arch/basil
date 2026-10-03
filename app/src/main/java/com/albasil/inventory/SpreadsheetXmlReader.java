package com.albasil.bm;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Reads Microsoft Excel 2003 XML / SpreadsheetML files. */
final class SpreadsheetXmlReader {

    static BasilXmlReader.Result read(InputStream in) throws Exception {
        XmlPullParser p = Xml.newPullParser();
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        p.setInput(in, "UTF-8");

        List<Sheet> sheets = new ArrayList<>();
        int event = p.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && local(p.getName()).equals("Worksheet")) {
                sheets.add(readWorksheet(p));
            }
            event = p.next();
        }

        List<MainActivity.MaterialItem> out = readNormalTables(sheets);
        if (out.isEmpty()) out = readSplitFieldSheets(sheets);
        return new BasilXmlReader.Result(out, 0);
    }

    /** Handles the common case: one worksheet with columns such as code/name/barcode/unit/group. */
    private static List<MainActivity.MaterialItem> readNormalTables(List<Sheet> sheets) {
        List<MainActivity.MaterialItem> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (Sheet sheet : sheets) {
            if (sheet.rows.isEmpty()) continue;
            Integer headerRowNo = firstMeaningfulRow(sheet.rows);
            if (headerRowNo == null) continue;
            List<String> headers = sheet.rows.get(headerRowNo);

            Map<Field,Integer> cols = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                Field f = classify(headers.get(i));
                if (f != Field.UNKNOWN && !cols.containsKey(f)) cols.put(f, i);
            }
            if (!cols.containsKey(Field.NAME) && !cols.containsKey(Field.CODE) && !cols.containsKey(Field.BARCODE)) continue;

            for (Map.Entry<Integer,List<String>> e : sheet.rows.entrySet()) {
                if (e.getKey() <= headerRowNo) continue;
                List<String> row = e.getValue();
                MainActivity.MaterialItem m = new MainActivity.MaterialItem();
                m.id = UUID.randomUUID().toString();
                m.code = value(row, cols.get(Field.CODE));
                m.name = value(row, cols.get(Field.NAME));
                m.barcode = value(row, cols.get(Field.BARCODE));
                m.unit = defaultValue(value(row, cols.get(Field.UNIT)), "وحدة");
                m.group = defaultValue(value(row, cols.get(Field.GROUP)), "بدون مجموعة");
                m.purchasePrice = number(value(row, cols.get(Field.PURCHASE_PRICE)), 0);
                m.sourceQty = number(value(row, cols.get(Field.QUANTITY)), 0);

                if (empty(m.code) && empty(m.name) && empty(m.barcode)) continue;
                String key = (!empty(m.code) ? "C:" + m.code : !empty(m.barcode) ? "B:" + m.barcode : "N:" + m.name).toLowerCase(Locale.ROOT);
                if (seen.add(key)) out.add(m);
            }
        }
        return out;
    }

    /**
     * Handles exported reports where each field is placed in its own worksheet.
     * Values are joined using the original SpreadsheetML logical row number.
     */
    private static List<MainActivity.MaterialItem> readSplitFieldSheets(List<Sheet> sheets) {
        Map<Field,Map<Integer,String>> fields = new HashMap<>();

        for (Sheet sheet : sheets) {
            Integer headerRowNo = firstMeaningfulRow(sheet.rows);
            if (headerRowNo == null) continue;
            List<String> header = sheet.rows.get(headerRowNo);
            String first = firstNonEmpty(header);
            Field field = classify(first);
            if (field == Field.UNKNOWN) continue;

            Map<Integer,String> values = fields.computeIfAbsent(field, k -> new LinkedHashMap<>());
            for (Map.Entry<Integer,List<String>> e : sheet.rows.entrySet()) {
                if (e.getKey() <= headerRowNo) continue;
                String v = firstNonEmpty(e.getValue());
                if (!empty(v)) values.put(e.getKey(), v);
            }
        }

        Set<Integer> rowIds = new LinkedHashSet<>();
        for (Map<Integer,String> m : fields.values()) rowIds.addAll(m.keySet());
        List<Integer> sorted = new ArrayList<>(rowIds);
        Collections.sort(sorted);

        List<MainActivity.MaterialItem> out = new ArrayList<>();
        for (Integer rowNo : sorted) {
            MainActivity.MaterialItem m = new MainActivity.MaterialItem();
            m.id = UUID.randomUUID().toString();
            m.code = get(fields, Field.CODE, rowNo);
            m.name = get(fields, Field.NAME, rowNo);
            m.barcode = get(fields, Field.BARCODE, rowNo);
            m.unit = defaultValue(get(fields, Field.UNIT, rowNo), "وحدة");
            m.group = defaultValue(get(fields, Field.GROUP, rowNo), "بدون مجموعة");
            m.purchasePrice = number(get(fields, Field.PURCHASE_PRICE, rowNo), 0);
            m.sourceQty = number(get(fields, Field.QUANTITY, rowNo), 0);
            if (!empty(m.code) || !empty(m.name) || !empty(m.barcode)) out.add(m);
        }
        return out;
    }

    private static Sheet readWorksheet(XmlPullParser p) throws Exception {
        Sheet sheet = new Sheet();
        sheet.name = attributeLocal(p, "Name");
        int depth = p.getDepth();
        int logicalRow = 0;

        while (true) {
            int ev = p.next();
            if (ev == XmlPullParser.END_DOCUMENT) break;
            if (ev == XmlPullParser.END_TAG && p.getDepth() == depth && local(p.getName()).equals("Worksheet")) break;
            if (ev != XmlPullParser.START_TAG || !local(p.getName()).equals("Row")) continue;

            int explicitRow = intValue(attributeLocal(p, "Index"), -1);
            logicalRow = explicitRow > 0 ? explicitRow : logicalRow + 1;
            List<String> row = readRow(p);
            if (hasAny(row)) sheet.rows.put(logicalRow, row);
        }
        return sheet;
    }

    private static List<String> readRow(XmlPullParser p) throws Exception {
        List<String> row = new ArrayList<>();
        int depth = p.getDepth();
        int logicalCol = 0;

        while (true) {
            int ev = p.next();
            if (ev == XmlPullParser.END_DOCUMENT) break;
            if (ev == XmlPullParser.END_TAG && p.getDepth() == depth && local(p.getName()).equals("Row")) break;
            if (ev != XmlPullParser.START_TAG || !local(p.getName()).equals("Cell")) continue;

            int explicitCol = intValue(attributeLocal(p, "Index"), -1);
            logicalCol = explicitCol > 0 ? explicitCol : logicalCol + 1;
            while (row.size() < logicalCol) row.add("");
            row.set(logicalCol - 1, readCell(p));
        }
        return row;
    }

    private static String readCell(XmlPullParser p) throws Exception {
        int depth = p.getDepth();
        String value = "";
        while (true) {
            int ev = p.next();
            if (ev == XmlPullParser.END_DOCUMENT) break;
            if (ev == XmlPullParser.END_TAG && p.getDepth() == depth && local(p.getName()).equals("Cell")) break;
            if (ev == XmlPullParser.START_TAG && local(p.getName()).equals("Data")) {
                value = readElementText(p);
            }
        }
        return value == null ? "" : value.trim();
    }

    private static String readElementText(XmlPullParser p) throws Exception {
        int depth = p.getDepth();
        StringBuilder sb = new StringBuilder();
        while (true) {
            int ev = p.next();
            if (ev == XmlPullParser.END_DOCUMENT) break;
            if (ev == XmlPullParser.TEXT || ev == XmlPullParser.CDSECT || ev == XmlPullParser.ENTITY_REF) {
                if (p.getText() != null) sb.append(p.getText());
            } else if (ev == XmlPullParser.END_TAG && p.getDepth() == depth) {
                break;
            }
        }
        return sb.toString().trim();
    }

    private static String attributeLocal(XmlPullParser p, String wanted) {
        for (int i = 0; i < p.getAttributeCount(); i++) {
            String name = local(p.getAttributeName(i));
            if (wanted.equalsIgnoreCase(name)) return p.getAttributeValue(i);
        }
        return "";
    }

    private static String local(String s) {
        if (s == null) return "";
        int i = s.indexOf(':');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    private static Integer firstMeaningfulRow(Map<Integer,List<String>> rows) {
        for (Map.Entry<Integer,List<String>> e : rows.entrySet()) if (hasAny(e.getValue())) return e.getKey();
        return null;
    }

    private static boolean hasAny(List<String> row) {
        if (row == null) return false;
        for (String s : row) if (!empty(s)) return true;
        return false;
    }

    private static String firstNonEmpty(List<String> row) {
        if (row == null) return "";
        for (String s : row) if (!empty(s)) return s.trim();
        return "";
    }

    private static String value(List<String> row, Integer index) {
        if (index == null || index < 0 || row == null || index >= row.size()) return "";
        String v = row.get(index);
        return v == null ? "" : v.trim();
    }

    private static String get(Map<Field,Map<Integer,String>> fields, Field field, int row) {
        Map<Integer,String> m = fields.get(field);
        return m == null ? "" : defaultValue(m.get(row), "");
    }

    private static Field classify(String raw) {
        String h = normalize(raw);
        if (h.isEmpty()) return Field.UNKNOWN;

        if (matches(h, "اسم الصنف", "اسم المادة", "اسم المنتج", "الصنف", "المادة", "product name", "item name", "name")) return Field.NAME;
        if (matches(h, "رمز الصنف", "كود الصنف", "كود المادة", "كود المنتج", "الرمز", "الكود", "item code", "product code", "sku", "code")) return Field.CODE;
        if (matches(h, "الباركود", "باركود", "barcode", "ean", "upc")) return Field.BARCODE;
        if (matches(h, "الوحدة", "وحدة", "unit", "uom")) return Field.UNIT;
        if (matches(h, "المجموعة", "مجموعة", "التصنيف", "القسم", "group", "category")) return Field.GROUP;
        if (matches(h, "سعر الشراء", "سعر التكلفة", "التكلفة", "purchase price", "cost", "cost price")) return Field.PURCHASE_PRICE;
        if (matches(h, "الكمية", "الرصيد", "المخزون", "quantity", "qty", "stock")) return Field.QUANTITY;
        return Field.UNKNOWN;
    }

    private static boolean matches(String h, String... names) {
        for (String n : names) if (h.equals(normalize(n))) return true;
        return false;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ");
    }

    private static boolean empty(String s) { return s == null || s.trim().isEmpty(); }
    private static String defaultValue(String s, String fallback) { return empty(s) ? fallback : s.trim(); }
    private static int intValue(String s, int fallback) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; } }
    private static double number(String s, double fallback) { try { return Double.parseDouble(s.trim().replace(',', '.')); } catch (Exception e) { return fallback; } }

    private enum Field { NAME, CODE, BARCODE, UNIT, GROUP, PURCHASE_PRICE, QUANTITY, UNKNOWN }

    private static final class Sheet {
        String name = "";
        final Map<Integer,List<String>> rows = new LinkedHashMap<>();
    }

    private SpreadsheetXmlReader() {}
}
