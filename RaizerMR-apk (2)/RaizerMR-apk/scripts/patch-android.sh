#!/usr/bin/env bash
# Roda depois de "npx cap add android": permissoes, tela deitada, tela cheia, icone e splash do RaizerMR.
set -euo pipefail
A=android/app/src/main
M=$A/AndroidManifest.xml
R=$A/res

# 1) tela sempre deitada (VR) e sem reiniciar ao girar
sed -i 's|android:launchMode="singleTask"|android:launchMode="singleTask" android:screenOrientation="sensorLandscape"|' "$M"

# 2) permissoes: camera + sensores (opcionais)
sed -i 's|</manifest>|    <uses-permission android:name="android.permission.CAMERA" />\n    <uses-feature android:name="android.hardware.camera" android:required="false" />\n    <uses-feature android:name="android.hardware.sensor.gyroscope" android:required="false" />\n    <uses-feature android:name="android.hardware.sensor.accelerometer" android:required="false" />\n</manifest>|' "$M"

# 3) tela cheia imersiva + tela sempre ligada + forca horizontal
PKG_DIR=$(dirname "$(find "$A/java" -name MainActivity.java)")
cat > "$PKG_DIR/MainActivity.java" << 'JAVA'
package com.raizermr.app;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.WindowManager;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    hideBars();
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) hideBars();
  }

  private void hideBars() {
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
    c.hide(WindowInsetsCompat.Type.systemBars());
    c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
  }
}
JAVA

# 4) icone (logo branca em fundo escuro, sem borda branca) e nome
cp -r icons/mipmap-* "$R/"
sed -i 's|<string name="app_name">.*</string>|<string name="app_name">RaizerMR</string>|; s|<string name="title_activity_main">.*</string>|<string name="title_activity_main">RaizerMR</string>|' "$R/values/strings.xml"
if [ -f "$R/values/ic_launcher_background.xml" ]; then
  sed -i 's|<color name="ic_launcher_background">.*</color>|<color name="ic_launcher_background">#0B0E17</color>|' "$R/values/ic_launcher_background.xml"
fi

# 5) splash escura com a logo e "Powered By RN Studio" (sem o fundo branco padrao)
find "$R" -name 'splash.png' -delete
mkdir -p "$R/drawable-nodpi" "$R/drawable"
cp splash/splash_icon.png splash/splash_logo.png splash/splash_brand.png "$R/drawable-nodpi/"
cat > "$R/drawable/splash.xml" << 'XML'
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <solid android:color="#0B0E17" />
        </shape>
    </item>
    <item android:gravity="center">
        <bitmap android:src="@drawable/splash_logo" android:gravity="center" />
    </item>
    <item android:gravity="bottom" android:bottom="28dp">
        <bitmap android:src="@drawable/splash_brand" android:gravity="bottom|center_horizontal" />
    </item>
</layer-list>
XML
if grep -q "core-splashscreen" android/app/build.gradle; then
python3 - << 'PY'
import re
p='android/app/src/main/res/values/styles.xml'
s=open(p,encoding='utf-8').read()
add='\n        <item name="windowSplashScreenBackground">#0B0E17</item>\n        <item name="windowSplashScreenAnimatedIcon">@drawable/splash_icon</item>\n        <item name="windowSplashScreenBrandingImage">@drawable/splash_brand</item>'
n=re.sub(r'(<style name="AppTheme\.NoActionBarLaunch"[^>]*>)',lambda m:m.group(1)+add,s,count=1)
open(p,'w',encoding='utf-8').write(n)
print('splash theme patched' if n!=s else 'AVISO: tema de splash nao encontrado')
PY
else
  echo "AVISO: core-splashscreen nao encontrado; splash do Android 12+ fica padrao"
fi
echo "Android patch OK"
