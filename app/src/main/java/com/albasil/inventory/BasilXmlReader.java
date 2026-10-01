package com.albasil.bm;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Direct XML reader used by AL BASIL BM. */
final class BasilXmlReader {
    static final class Result {
        final List<MainActivity.MaterialItem> materials;
        final int groupCount;
        Result(List<MainActivity.MaterialItem> materials, int groupCount) {
            this.materials = materials;
            this.groupCount = groupCount;
        }
    }

    static Result read(InputStream in) throws Exception {
        XmlPullParser p = Xml.newPullParser();
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        p.setInput(in, "UTF-8");

        Map<String,String> groups = new HashMap<>();
        List<PendingMaterial> pending = new ArrayList<>();

        int event = p.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String tag = p.getName();
                if ("G".equals(tag)) readGroup(p, groups);
                else if ("M".equals(tag)) pending.add(readMaterial(p));
            }
            event = p.next();
        }

        List<MainActivity.MaterialItem> out = new ArrayList<>();
        for (PendingMaterial pm : pending) {
            MainActivity.MaterialItem m = pm.item;
            String groupName = groups.get(pm.groupGuid);
            m.group = (groupName == null || groupName.trim().isEmpty()) ? "بدون مجموعة" : groupName.trim();
            if (!m.name.isEmpty() || !m.code.isEmpty() || !m.barcode.isEmpty()) out.add(m);
        }
        return new Result(out, groups.size());
    }

    private static void readGroup(XmlPullParser p, Map<String,String> groups) throws Exception {
        String guid = "";
        String name = "";
        int depth = p.getDepth();
        while (true) {
            int ev = p.next();
            if (ev == XmlPullParser.END_DOCUMENT) break;
            if (ev == XmlPullParser.END_TAG && p.getDepth() == depth && "G".equals(p.getName())) break;
            if (ev != XmlPullParser.START_TAG) continue;
            String tag = p.getName();
            if ("gPtr".equals(tag)) guid = readText(p);
            else if ("GroupName".equals(tag)) name = readText(p);
            else skip(p);
        }
        if (!guid.isEmpty()) groups.put(guid, name);
    }

    private static PendingMaterial readMaterial(XmlPullParser p) throws Exception {
        MainActivity.MaterialItem m = new MainActivity.MaterialItem();
        m.id = UUID.randomUUID().toString();
        String groupGuid = "";
        int depth = p.getDepth();
        while (true) {
            int ev = p.next();
            if (ev == XmlPullParser.END_DOCUMENT) break;
            if (ev == XmlPullParser.END_TAG && p.getDepth() == depth && "M".equals(p.getName())) break;
            if (ev != XmlPullParser.START_TAG) continue;
            String tag = p.getName();
            switch (tag) {
                case "mptr": m.id = valueOr(readText(p), m.id); break;
                case "MatGroupGuid": groupGuid = readText(p); break;
                case "MatCode": m.code = readText(p); break;
                case "MatName": m.name = readText(p); break;
                case "MatBarCode": m.barcode = readText(p); break;
                case "MatBarCode2": m.barcode2 = readText(p); break;
                case "MatBarCode3": m.barcode3 = readText(p); break;
                case "MatUnity": m.unit = valueOr(readText(p), "وحدة"); break;
                case "MatUnit2": m.unit2 = readText(p); break;
                case "MatUnit3": m.unit3 = readText(p); break;
                case "MatUnit2Factor": m.factor2 = number(readText(p), 1); break;
                case "MatUnit3Factor": m.factor3 = number(readText(p), 1); break;
                case "PurchasePrice": m.purchasePrice = number(readText(p), 0); break;
                case "SourceQuantity": m.sourceQty = number(readText(p), 0); break;
                default: skip(p); break;
            }
        }
        return new PendingMaterial(m, groupGuid);
    }

    private static String readText(XmlPullParser p) throws Exception {
        String value = "";
        int ev = p.next();
        if (ev == XmlPullParser.TEXT) {
            value = p.getText() == null ? "" : p.getText().trim();
            p.nextTag();
        }
        return value;
    }

    private static void skip(XmlPullParser p) throws Exception {
        if (p.getEventType() != XmlPullParser.START_TAG) return;
        int depth = 1;
        while (depth != 0) {
            int ev = p.next();
            if (ev == XmlPullParser.START_TAG) depth++;
            else if (ev == XmlPullParser.END_TAG) depth--;
            else if (ev == XmlPullParser.END_DOCUMENT) break;
        }
    }

    private static String valueOr(String s, String fallback) {
        return s == null || s.trim().isEmpty() ? fallback : s.trim();
    }

    private static double number(String s, double fallback) {
        try {
            if (s == null || s.trim().isEmpty()) return fallback;
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static final class PendingMaterial {
        final MainActivity.MaterialItem item;
        final String groupGuid;
        PendingMaterial(MainActivity.MaterialItem item, String groupGuid) {
            this.item = item;
            this.groupGuid = groupGuid;
        }
    }

    private BasilXmlReader() {}
}
