# План исправления ошибки OpenCL в sugar-pocketAI

## Проблема

При попытке включить модель на GPU в проекте sugar-pocketAI возникает ошибка "не смог найти OpenCL на этом устройстве". В проекте gallery/Android переключение на GPU работает.

## Причина

После анализа обнаружены следующие различия:

1. **Манифест**: В gallery/Android манифест содержит объявления uses-native-library для libOpenCL.so, libvndksupport.so, libcdsprpc.so. В sugar-pocketAI манифест пустой, что может препятствовать загрузке библиотеки OpenCL системой.

2. **Обнаружение OpenCL**: В `HardwareInfo.java` поддержка OpenCL определяется упрощённо (`supportsOpenCL = hasAdreno`), что неверно для многих GPU.

3. **Зависимости**: Оба проекта используют библиотеку LiteRT-LM, но в gallery/Android также есть зависимости tflite-gpu, которые могут влиять на доступность GPU (хотя ошибка именно об OpenCL).

## План исправлений

### 1. Обновить манифест sugar-pocketAI

Добавить в `Pocket/PocketTest/sugar-pocketAI/src/main/AndroidManifest.xml` следующие строки внутри тега `<application>` (или создать тег `<application>` если его нет):

```xml
<uses-native-library android:name="libvndksupport.so" android:required="false"/>
<uses-native-library android:name="libOpenCL.so" android:required="false"/>
<uses-native-library android:name="libcdsprpc.so" android:required="false" />
```

Пример полного манифеста:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <uses-native-library android:name="libvndksupport.so" android:required="false"/>
        <uses-native-library android:name="libOpenCL.so" android:required="false"/>
        <uses-native-library android:name="libcdsprpc.so" android:required="false" />
    </application>
</manifest>
```

### 2. Улучшить обнаружение OpenCL в HardwareInfo.java

Заменить упрощённую проверку на более точную. Предлагается реализовать метод `isOpenCLSupported()`, который проверяет наличие библиотеки через попытку загрузки или проверку файловой системы.

Пример изменений в `HardwareInfo.java` (метод `getGpuInfo()`):

```java
private static boolean isOpenCLSupported() {
    // Способ 1: Проверка наличия файла библиотеки
    String[] possiblePaths = {
        "/system/lib/libOpenCL.so",
        "/system/lib64/libOpenCL.so",
        "/vendor/lib/libOpenCL.so",
        "/vendor/lib64/libOpenCL.so"
    };
    for (String path : possiblePaths) {
        if (new File(path).exists()) {
            return true;
        }
    }
    // Способ 2: Попытка загрузки библиотеки (осторожно)
    try {
        System.loadLibrary("OpenCL");
        return true;
    } catch (UnsatisfiedLinkError e) {
        // ignore
    }
    return false;
}
```

Затем в `getGpuInfo()` заменить строку:

```java
boolean supportsOpenCL = hasAdreno; // Partial check, CPU features needed for full support
```

на

```java
boolean supportsOpenCL = isOpenCLSupported();
```

### 3. Проверить зависимости сборки

Убедиться, что в `build.gradle.kts` модуля sugar-pocketAI указана актуальная версия библиотеки LiteRT-LM (0.9.0-alpha06). При необходимости обновить до версии, используемой в gallery/Android (проверить libs.versions.toml).

### 4. Тестирование

После внесения изменений:

- Собрать проект PocketTest.
- Запустить приложение на устройстве с поддержкой OpenCL (например, Adreno GPU).
- В интерфейсе InferenceActivity выбрать акселератор GPU и запустить вывод.
- Убедиться, что ошибка "не смог найти OpenCL" не появляется и модель работает на GPU.

## Следующие шаги

Для реализации этих исправлений необходимо переключиться в режим Code, так как требуется редактирование Java и XML файлов.

После выполнения исправлений рекомендуется провести дополнительное тестирование на различных устройствах (Mali, PowerVR) для подтверждения корректности обнаружения OpenCL.
