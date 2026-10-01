from pathlib import Path
p=Path('app/src/main/java/com/albasil/inventory/MainActivity.java')
s=p.read_text(encoding='utf-8')
old='for (int i = 0; i < mi.length(); i++) s.manualItems.add(MaterialItem.from(mi.getJSONObject(i)));'
new='''for (int i = 0; i < mi.length(); i++) {\n                    JSONObject item = mi.optJSONObject(i);\n                    if (item != null) s.manualItems.add(MaterialItem.from(item));\n                }'''
if old not in s: raise SystemExit('manualItems load line not found')
s=s.replace(old,new,1)
p.write_text(s,encoding='utf-8')
print('manual item compile fix applied')
