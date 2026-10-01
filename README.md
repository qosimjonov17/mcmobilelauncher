# MC Mobile Launcher

Android telefonlarda **Minecraft: Java Edition** oʻynash uchun oʻzbekcha launcher.

Loyiha ochiq manbali [Amethyst (Angel Aura MC)](https://github.com/AngelAuraMC/Amethyst-Android) launcheri asosida qurilgan.
Amethyst esa [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) va [Boardwalk](https://github.com/zhuowei/Boardwalk) davomchisi.
Asl loyihaning README fayli: [UPSTREAM_README.md](UPSTREAM_README.md).

## Imkoniyatlar

- Minecraft Java Edition ning deyarli barcha versiyalari (eng eskilaridan eng yangilarigacha)
- Fabric, Forge, NeoForge, Quilt, OptiFine va modpaklar
- Microsoft akkaunti orqali kirish (litsenziyali oʻyin)
- Sensorli boshqaruv, joystik, giroskop va geympad
- Toʻliq **oʻzbek tilidagi** interfeys

## APK faylni qanday olish mumkin (kompyuter shart emas)

Repozitoriyga har safar oʻzgarish yuborilganda GitHub APK faylni avtomatik yigʻadi:

1. GitHub da repozitoriyni oching va **Actions** boʻlimiga oʻting.
2. Eng yuqoridagi yashil ✅ belgili **Android CI** ishga tushirishini tanlang (yigʻish ~30–60 daqiqa davom etadi).
3. Sahifa pastidagi **Artifacts** boʻlimidan **app-debug (recommended)** ni yuklab oling.
   Agar roʻyxatda hech narsa boʻlmasa: chap tomondan **Android CI** ni tanlang → **Run workflow** → **Run workflow**.
4. ZIP ichidagi `app-debug.apk` ni telefonga oʻtkazib oʻrnating
   (Android “Nomaʼlum manbalardan oʻrnatish”ga ruxsat soʻraydi).

## Kompyuterda yigʻish (dasturchilar uchun)

Kerak boʻladi: JDK 21, Android SDK va NDK (Android Studio bilan birga oʻrnatish eng oson).

```bash
git clone --recursive https://github.com/qosimjonov17/mcmobilelauncher.git
cd mcmobilelauncher
./gradlew :app_pojavlauncher:assembleDebug
```

Tayyor APK: `app_pojavlauncher/build/outputs/apk/debug/`.

## Loyiha tuzilishi (qisqacha)

| Papka | Vazifasi |
|---|---|
| `app_pojavlauncher/` | Asosiy Android ilova (Java + C) |
| `app_pojavlauncher/src/main/res/values-uz/strings.xml` | Oʻzbekcha tarjima |
| `app_pojavlauncher/build.gradle` | Ilova nomi, paket ID (`uz.mcmobile.launcher`), versiya |
| `jre_lwjgl3glfw/` | Minecraft ishlatadigan LWJGL/GLFW ning Android versiyasi |
| `.github/workflows/android.yml` | APK ni avtomatik yigʻuvchi GitHub Actions |

## Asl loyihadan yangilanishlarni olish

```bash
git remote add upstream https://github.com/AngelAuraMC/Amethyst-Android.git
git fetch upstream v3_openjdk
git merge upstream/v3_openjdk
```

## Huquqiy eslatma

- Bu launcher Minecraft fayllarini oʻz ichida tarqatmaydi — ular toʻgʻridan-toʻgʻri Mojang serverlaridan yuklab olinadi.
- Toʻliq oʻyin uchun Minecraft: Java Edition sotib olingan Microsoft akkaunti kerak.
- Loyiha Mojang Studios yoki Microsoft bilan bogʻliq emas.

## Litsenziya

Loyiha [GNU LGPLv3](LICENSE) litsenziyasi ostida tarqatiladi, xuddi asl Amethyst loyihasi kabi.
Bu shuni anglatadiki, launcherning manba kodi har doim ochiq qolishi va asl mualliflar koʻrsatilishi kerak.
Barcha mualliflik huquqlari va kutubxonalar roʻyxati: [UPSTREAM_README.md](UPSTREAM_README.md#credits--dependencies).
