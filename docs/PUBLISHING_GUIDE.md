# Publishing Guide

## 1. Создание keystore

Команда:

```powershell
keytool -genkeypair -v `
  -keystore signing\release-keystore.jks `
  -alias release `
  -keyalg RSA `
  -keysize 4096 `
  -validity 9125
```

После этого создайте локальный файл `keystore.properties` по образцу `keystore.properties.example`.

## 2. Где хранить ключи

- `signing/release-keystore.jks` хранить вне git
- `keystore.properties` хранить вне git
- резервную копию keystore держать в защищённом password manager и в отдельном офлайн-хранилище

## 3. Сборка release

APK:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleRelease
```

AAB:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat bundleRelease
```

Артефакты:

- `build/outputs/apk/release/`
- `build/outputs/bundle/release/`

## 4. Google Play Billing

Зависимость уже добавлена:

```gradle
implementation 'com.android.billingclient:billing-ktx:7.1.1'
```

Дальше нужно:

1. Создать товар `premium_unlock` в Play Console
2. Подключить `BillingClient`
3. Загружать каталог товаров через `queryProductDetailsAsync`
4. Запускать покупку через `launchBillingFlow`
5. На успешной покупке сохранять entitlement `isPremium = true`
6. Проверять покупки при запуске приложения и после восстановления

## 5. Перед релизом

- заменить `privacy@yourdomain.example`
- разместить Privacy Policy по HTTPS
- проверить финальный package name, иконку и название
- загрузить только подписанный release AAB
