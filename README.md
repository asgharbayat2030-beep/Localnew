# Local Device Manager v4

یک ابزار مدیریت دستگاه در شبکه محلی برای Android.

## امکانات نسخه 4

- Remote LAN مستقل روی پورت 8081
- Dashboard پیامک روی پورت 8080
- UI واکنش‌گرا و RTL با حالت روشن/تیره
- داشبورد وضعیت دستگاه
- Battery / Storage / RAM / Android version
- کنترل Start/Stop داشبورد از دستگاه دوم
- مشاهده و جستجوی SMS
- وضعیت شبکه و آدرس‌های سرویس
- اجرای خودکار پس از Boot
- Token برای APIهای Remote
- بدون اجرای آزاد shell command از طریق Remote
- GitHub Actions برای ساخت APK Release

## GitHub Secrets

این پروژه برای Release همان Secretهای قبلی را استفاده می‌کند:

- `LOCALSMS_KEYSTORE_BASE64`
- `LOCALSMS_KEYSTORE_PASSWORD`

فایل keystore نباید داخل Repository قرار بگیرد.

## Build

Workflow از مسیر `.github/workflows/build.yml` اجرا می‌شود و artifact با نام `LocalDeviceManager-v4-release` تولید می‌کند.

## استفاده

بعد از نصب و دادن مجوز SMS و Notification، برنامه را یک‌بار باز کنید. Remote حتی زمانی که Dashboard اصلی متوقف است باقی می‌ماند.

آدرس نمونه:

- Remote: `http://PHONE_IP:8081/?token=TOKEN`
- Dashboard: `http://PHONE_IP:8080`

هر دو دستگاه باید روی یک شبکه محلی باشند.
