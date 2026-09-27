# AL BASIL Android

هذا المشروع يغلّف نسخة AL BASIL HTML نفسها داخل تطبيق Android WebView آمن عبر HTTPS local asset origin.

## الوظائف
- نفس واجهة AL BASIL الحالية.
- استيراد XLSX/CSV/TXT/TSV من منتقي ملفات Android.
- الجرد اليدوي والباركود وكاميرا الهاتف.
- حفظ البيانات محليًا داخل WebView.
- حفظ التصدير والنسخ الاحتياطية في Downloads/AL BASIL على Android 10+.
- GitHub Actions يبني AL_BASIL.apk تلقائيًا.

## البناء عبر GitHub
ارفع محتويات المشروع إلى مستودع GitHub ثم افتح Actions > Build AL BASIL APK > Run workflow. بعد النجاح نزّل Artifact باسم AL_BASIL_APK.
