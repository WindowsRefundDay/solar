#!/system/bin/sh
# ponytail: Generic/Stock/Rockbox = Y1-Rockbox.kl (wheel 105/106→126/127).
# mtk-tpd-kpd must mirror mtk-kpd (163/165→MEDIA_NEXT/PREVIOUS) — not the full PC keyboard map.
SRC=/system/etc/solar/Y1-Rockbox.kl
[ -f "$SRC" ] || exit 0

KPD=/system/usr/keylayout/mtk-kpd.kl
TPD=/system/usr/keylayout/mtk-tpd-kpd.kl

# Skip remount + writes entirely when every target already matches (avoid flash writes on every boot).
needs_write=0
for f in Generic.kl Stock.kl Rockbox.kl Y1-Rockbox.kl; do
    cmp -s "$SRC" "/system/usr/keylayout/$f" || needs_write=1
done
if [ -f "$KPD" ]; then
    grep -qxF 'key 105   MEDIA_PLAY' "$KPD" || needs_write=1
    grep -qxF 'key 106   MEDIA_PAUSE' "$KPD" || needs_write=1
fi
[ "$needs_write" -eq 0 ] && exit 0

mount -o remount,rw /system 2>/dev/null || true
for f in Generic.kl Stock.kl Rockbox.kl Y1-Rockbox.kl; do
    cmp -s "$SRC" "/system/usr/keylayout/$f" && continue
    cp "$SRC" "/system/usr/keylayout/$f"
    chmod 644 "/system/usr/keylayout/$f"
done
# mtk-kpd keeps GPIO keys; patch wheel scancodes 105/106 (stock maps them to DPAD_LEFT/RIGHT).
if [ -f "$KPD" ]; then
    sed -i 's/^key 105[[:space:]].*/key 105   MEDIA_PLAY/' "$KPD"
    sed -i 's/^key 106[[:space:]].*/key 106   MEDIA_PAUSE/' "$KPD"
    chmod 644 "$KPD"
    if ! cmp -s "$KPD" "$TPD" 2>/dev/null; then
        cp "$KPD" "$TPD"
        chmod 644 "$TPD"
    fi
fi
