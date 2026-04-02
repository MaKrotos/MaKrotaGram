# 🐾 MaKrotaGram
[![Crowdin](https://badges.crowdin.net/e/a094217ac83905ae1625526d59bba8dc/localized.svg)](https://neko.crowdin.com/nekogram)  
MaKrotaGram is a third-party Telegram client with not many but useful modifications.

- Website: https://nekogram.app
- Telegram channel: https://t.me/nekoupdates
- Downloads: https://nekogram.app/download
- Feedback: https://github.com/MaKrotaGram/MaKrotaGram/issues

## API, Protocol documentation

Telegram API manuals: https://core.telegram.org/api

MTProto protocol manuals: https://core.telegram.org/mtproto

## Compilation Guide

1. Download the MaKrotaGram source code ( `git clone https://github.com/MaKrotaGram/MaKrotaGram.git` )
1. Fill out storeFile, storePassword, keyAlias, keyPassword in local.properties to access your release.keystore
1. Go to https://console.firebase.google.com/, create two android apps with application IDs tw.fdw.makrotagram and tw.fdw.makrotagram.beta, turn on firebase messaging and download `google-services.json`, which should be copied into `TMessagesProj` folder.
1. Open the project in the Studio (note that it should be opened, NOT imported).
1. Fill out values in `TMessagesProj/src/main/java/tw/nekomimi/nekogram/Extra.java` – there’s a link for each of the variables showing where and which data to obtain.
1. You are ready to compile MaKrotaGram.

## Localization

MaKrotaGram is forked from Telegram, thus most locales follows the translations of Telegram for Android, checkout https://translations.telegram.org/en/android/.

As for the MaKrotaGram specialized strings, we use Crowdin to translate MaKrotaGram. Join project at https://neko.crowdin.com/nekogram. Help us bring MaKrotaGram to the world!
