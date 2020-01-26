# 国信大学生証リーダー

NFC 対応 Android 端末で国際信州学院大学の IC 学生証の記録内容を読み取るアプリです。kotlin で実装しています。

## インストール

こちらの APK をインストールしてください。
[suiIDreader-v1_1.apk](https://scc.kokushin-u.jp/static/app/suiIDreader-v1_1.apk)

## 使い方

端末に学生証をかざすと内容が表示されます。
![学生証リーダー画面](https://scc.kokushin-u.jp/static/app/suiidreader-ss.png)

## データ記録フォーマット

国信大の学生証は FeliCa Lite-S が搭載されており図のようなフォーマットで記録されています。
![学生証データフォーマットver.0](https://scc.kokushin-u.jp/wp-content/uploads/sites/2/2020/01/sui-id-shiyou-1.png)
![学生証データフォーマットver.1](https://scc.kokushin-u.jp/wp-content/uploads/sites/2/2020/01/sui-id-shiyou-ver1.png)
2019 年末の C97 で発行業務を行った時点ではver.0を使用し2020年1月以降はver.1を使用しています。
