from pathlib import Path

p = Path('app/src/main/java/com/albasil/inventory/MainActivity.java')
s = p.read_text(encoding='utf-8')

old = '''    private void startScanner() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 9001);
            return;
        }
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
        integrator.setPrompt("وجّه الكاميرا إلى باركود الصنف");
        integrator.setBeepEnabled(true);
        integrator.setOrientationLocked(true);
        integrator.initiateScan();
    }
'''

new = '''    private void startScanner() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 9001);
                return;
            }
            IntentIntegrator integrator = new IntentIntegrator(this);
            integrator.setCaptureActivity(ScannerActivity.class);
            integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
            integrator.setPrompt("وجّه الكاميرا إلى باركود الصنف");
            integrator.setBeepEnabled(true);
            integrator.setOrientationLocked(true);
            integrator.setCameraId(0);
            integrator.initiateScan();
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("تعذر تشغيل الكاميرا")
                    .setMessage("لم يتمكن التطبيق من فتح ماسح الباركود. تأكد من السماح للتطبيق باستخدام الكاميرا من إعدادات الهاتف.\\n\\n" + e.getClass().getSimpleName())
                    .setPositiveButton("حسنًا", null)
                    .show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 9001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanner();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("صلاحية الكاميرا مطلوبة")
                        .setMessage("يحتاج ماسح الباركود إلى صلاحية الكاميرا. يمكنك تفعيلها من إعدادات الهاتف > التطبيقات > الباسل BM > الأذونات > الكاميرا.")
                        .setPositiveButton("حسنًا", null)
                        .show();
            }
        }
    }
'''

if old not in s:
    raise SystemExit('startScanner block not found')

s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')
print('Patched camera scanner flow')
