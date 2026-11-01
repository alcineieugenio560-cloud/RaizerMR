#!/usr/bin/env bash
# Roda depois de "npx cap add android": permissoes, tela deitada, tela cheia imersiva e icone do RaizerMR.
set -euo pipefail
A=android/app/src/main
M=$A/AndroidManifest.xml

# 1) tela sempre deitada (VR) e sem reiniciar ao girar
sed -i 's|android:launchMode="singleTask"|android:launchMode="singleTask" android:screenOrientation="sensorLandscape"|' "$M"

# 2) permissoes: camera + sensores (giroscopio) opcionais
sed -i 's|</manifest>|    <uses-permission android:name="android.permission.CAMERA" />\n    <uses-feature android:name="android.hardware.camera" android:required="false" />\n    <uses-feature android:name="android.hardware.sensor.gyroscope" android:required="false" />\n    <uses-feature android:name="android.hardware.sensor.accelerometer" android:required="false" />\n</manifest>|' "$M"

# 3) tela cheia imersiva + tela sempre ligada
PKG_DIR=$(dirname "$(find "$A/java" -name MainActivity.java)")
cat > "$PKG_DIR/MainActivity.java" << 'JAVA'
package com.raizermr.app;

import android.os.Bundle;
import android.view.WindowManager;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
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

# 4) icone e nome
cp -r icons/mipmap-* "$A/res/"
sed -i 's|<string name="app_name">.*</string>|<string name="app_name">RaizerMR</string>|; s|<string name="title_activity_main">.*</string>|<string name="title_activity_main">RaizerMR</string>|' "$A/res/values/strings.xml"
if [ -f "$A/res/values/ic_launcher_background.xml" ]; then
  sed -i 's|<color name="ic_launcher_background">.*</color>|<color name="ic_launcher_background">#F2F4F8</color>|' "$A/res/values/ic_launcher_background.xml"
fi
echo "Android patch OK"
