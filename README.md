# LWM Maintenance — Remote Updater

Gérez les mises à jour de vos sites WordPress (cœur, extensions, thèmes, traductions) depuis une application Android.

## Contenu

| Dossier | Description |
|---|---|
| `wp-plugin/remote-updater/` | Extension WordPress : API REST sécurisée (`wpru/v1`), page de connexion par QR code, compatible multisite |
| `android-app/` | Application Android (Kotlin, Jetpack Compose) |
| `dist/` | Derniers paquets : `remote-updater.zip` (extension) et `RemoteUpdater.apk` (app, version signée) |
| `captures/` | Captures d'écran de l'app |

## Installation

1. **WordPress** : Extensions › Ajouter › Téléverser `dist/remote-updater.zip`, puis activer (« Activer sur le réseau » en multisite).
2. **Android** : installer `dist/RemoteUpdater.apk`.
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

## Publier une nouvelle version

1. Changer le numéro de version (identique partout) :
   - `android-app/app/build.gradle.kts` : `versionName` et `versionCode` (+1 à chaque version)
   - `wp-plugin/remote-updater/remote-updater.php` : en-tête `Version` et `WPRU_VERSION`
   - `wp-plugin/remote-updater/readme.txt` : `Stable tag`
2. Ajouter une section `## X.Y.Z — date` en haut de `CHANGELOG.md` (les lignes `- …` deviennent les notes de version).
3. Commiter, puis pousser un tag : `git tag v1.3.0 && git push origin v1.3.0`.

GitHub Actions (`.github/workflows/release.yml`) construit l'APK signé et le `.zip`, puis publie la release avec `latest.json`. Ensuite, sans rien faire de plus :
- l'application propose la mise à jour (accueil, notification, Réglages › Application) ;
- WordPress affiche la mise à jour de l'extension (Extensions › Mises à jour, et dans l'app) ;
- la page lewebmax.com/remote-updater/ pointe vers les nouveaux fichiers et affiche les nouveautés.

Secrets GitHub requis (Settings › Secrets and variables › Actions) : `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Compiler l'app

```bash
cd android-app
./gradlew assembleRelease
```

JDK 17 et Android SDK (compileSdk 36) requis. La version release est signée avec la clé de `android-app/signing/` (hors dépôt, à sauvegarder : sans elle, impossible de publier une mise à jour installable par-dessus).

> Design « WordPress Pro » : couleurs unies (bleu admin WordPress), mode nuit, file de mises à jour avec progression en direct, favicons des sites.
