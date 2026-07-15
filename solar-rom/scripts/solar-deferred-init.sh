#!/system/bin/sh
# Bounded singleton for work that depends on mounted storage or PackageManager.

PIDFILE=/data/data/.solar_deferred_init.pid
if [ -f "$PIDFILE" ]; then
    oldpid=$(cat "$PIDFILE" 2>/dev/null)
    if [ -n "$oldpid" ] && kill -0 "$oldpid" 2>/dev/null; then
        exit 0
    fi
fi
echo $$ >"$PIDFILE"
trap 'rm -f "$PIDFILE"' 0 1 2 15

SD=/storage/sdcard0
i=0
while [ "$i" -lt 30 ] && [ ! -d "$SD" ]; do
    sleep 1
    i=$((i + 1))
done

if [ -d "$SD" ]; then
    for directory in Music Podcasts Themes JJ_Themes Videos Pictures "FM Recordings" RadioBuffer; do
        [ -d "$SD/$directory" ] || mkdir -p "$SD/$directory"
    done
    chmod 755 "$SD/Music" "$SD/Podcasts" "$SD/Themes" "$SD/JJ_Themes" \
        "$SD/Videos" "$SD/Pictures" "$SD/FM Recordings" "$SD/RadioBuffer" 2>/dev/null
fi

for script in switch-to-stock.sh switch-to-rockbox.sh; do
    if [ -f "/system/etc/solar/$script" ]; then
        cp "/system/etc/solar/$script" /data/data/
        chmod 755 "/data/data/$script"
    fi
done

[ ! -f /system/etc/solar/sync-rockbox-libs.sh ] \
    || sh /system/etc/solar/sync-rockbox-libs.sh
[ ! -f /system/etc/solar/sync-y1-keymap.sh ] \
    || sh /system/etc/solar/sync-y1-keymap.sh
[ ! -f /system/etc/solar/disable-rockbox-for-solar.sh ] \
    || sh /system/etc/solar/disable-rockbox-for-solar.sh
