# LWM Maintenance — Remote Updater

Gérez les mises à jour de vos sites WordPress (cœur, extensions, thèmes, traductions) depuis une application Android.

## Contenu

| Dossier | Description |
|---|---|
| `wp-plugin/remote-updater/` | Extension WordPress : API REST sécurisée (`wpru/v1`), page de connexion par QR code, compatible multisite |
| `android-app/` | Application Android (Kotlin, Jetpack Compose) |
| `dist/` | Derniers paquets : `remote-updater.zip` (extension) et `RemoteUpdater-debug.apk` (app) |
| `captures/` | Captures d'écran de l'app |

## Installation

1. **WordPress** : Extensions › Ajouter › Téléverser `dist/remote-updater.zip`, puis activer (« Activer sur le réseau » en multisite).
2. **Android** : installer `dist/RemoteUpdater-debug.apk`.
3. Dans WordPress : Réglages › Remote Updater › « Générer un code de connexion », puis scanner le QR code depuis l'app.

## Fonctionnalités

- Plusieurs sites dans la même app, multisite pris en charge
- Dossiers clients : sites regroupés par client, mise à jour d'un client en un clic
- Mise à jour du cœur, des extensions, des thèmes et des traductions (une par une ou tout d'un coup)
- Mise à jour manuelle par fichier `.zip` (extensions premium)
- Activation des mises à jour automatiques par extension / thème
- Historique des actions, notifications de nouvelles mises à jour
- Accès protégé (compte local téléphone + mot de passe, code PIN à 4 chiffres, empreinte)

## Sécurité

- Une clé d'accès par appareil, révocable depuis WordPress ; seul son hash est stocké côté serveur
- Clés chiffrées sur le téléphone (Android Keystore)
- Limitation des tentatives (serveur et application)

## Compiler l'app

```bash
cd android-app
./gradlew assembleDebug
```

JDK 17 et Android SDK (compileSdk 36) requis.

> Design « WordPress Pro » : couleurs unies (bleu admin WordPress), mode nuit, file de mises à jour avec progression en direct, favicons des sites.
