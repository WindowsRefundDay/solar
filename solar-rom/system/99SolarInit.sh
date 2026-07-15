#!/system/bin/sh
# Constant-time Solar boot entry. Slow storage/package work owns a background
# singleton so init.d/run-parts is never blocked by the SD card or PackageManager.

DEFERRED=/system/etc/solar/solar-deferred-init.sh
if [ -x "$DEFERRED" ]; then
    sh "$DEFERRED" </dev/null >/dev/null 2>&1 &
fi

if [ ! -f /system/lib/libconscrypt_jni.so ]; then
    log -p w -t SolarInit "missing /system/lib/libconscrypt_jni.so"
fi
if [ ! -f /system/etc/security/cacerts/6187b673.0 ]; then
    log -p w -t SolarInit "missing ISRG X1 cacert"
fi

exit 0
