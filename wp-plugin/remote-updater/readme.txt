=== Remote Updater ===
Requires at least: 5.8
Tested up to: 6.8
Requires PHP: 7.4
Stable tag: 1.0.0
License: GPLv2 or later

Gérez les mises à jour de WordPress, des extensions, des thèmes et des traductions depuis l'application Android Remote Updater.

== Description ==

1. Installez et activez l'extension (en multisite : « Activer sur le réseau »).
2. Allez dans Réglages > Remote Updater (Réglages du réseau en multisite).
3. Cliquez sur « Générer un code de connexion ».
4. Dans l'application, touchez « Ajouter un site » et scannez le QR code (ou collez le code).

Chaque appareil possède sa propre clé, révocable à tout moment. Les actions sont
exécutées avec les droits de l'administrateur qui a créé la connexion.
Seul un hash de la clé est stocké ; utilisez HTTPS.

== API REST (wpru/v1) ==

En-tête d'authentification : X-WPRU-Key: <clé>

* GET  /status
* GET  /updates?refresh=1
* POST /update/plugins       {"plugins": ["akismet/akismet.php"]}  (vide = toutes)
* POST /update/themes        {"themes": ["twentytwentyfive"]}      (vide = tous)
* POST /update/core
* POST /update/translations
* POST /auto-update          {"type": "plugin", "item": "akismet/akismet.php", "enabled": true}
