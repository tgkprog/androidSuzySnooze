#!/bin/bash

SRC="/data/code/gt/tandroid/sel2inSnooze/iconMake/icon.png"
DEST="/data/code/gt/tandroid/sel2inSnooze/app/src/main/res"

mkdir -p $DEST/mipmap-mdpi
mkdir -p $DEST/mipmap-hdpi
mkdir -p $DEST/mipmap-xhdpi
mkdir -p $DEST/mipmap-xxhdpi
mkdir -p $DEST/mipmap-xxxhdpi

convert $SRC -resize 48x48   $DEST/mipmap-mdpi/ic_launcher.png
convert $SRC -resize 72x72   $DEST/mipmap-hdpi/ic_launcher.png
convert $SRC -resize 96x96   $DEST/mipmap-xhdpi/ic_launcher.png
convert $SRC -resize 144x144 $DEST/mipmap-xxhdpi/ic_launcher.png
convert $SRC -resize 192x192 $DEST/mipmap-xxxhdpi/ic_launcher.png

convert $SRC -resize 512x512 /data/code/gt/tandroid/sel2inSnooze/iconMake/playstore-512.png

echo "✅ Icons generated successfully."

