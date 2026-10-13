# Journal des modifications — Remote Updater

## 1.3.0 — 13 octobre 2026

### Application Android
- L'app se met à jour toute seule : nouvelle version signalée sur l'accueil et par notification, installation en un geste
- Réglages › Application : version installée et recherche d'une mise à jour
- Le fichier téléchargé est vérifié (empreinte SHA-256) avant l'installation

### Extension WordPress
- Les nouvelles versions de l'extension apparaissent dans Extensions › Mises à jour (et dans l'app), comme une extension officielle
- Compatible avec les mises à jour automatiques de WordPress

## 1.2.0 — 13 octobre 2026

### Application Android
- Dossiers clients : regroupez vos sites par client (nom + couleur), repliables sur l'accueil
- « Mettre à jour » un client en un clic : toutes les mises à jour de ses sites sont mises en file
- Classement d'un site depuis sa fiche (menu ⋮ › Classer dans un dossier) ou dès sa connexion
- Supprimer un dossier ne retire pas ses sites (ils passent « sans dossier »)
- Application signée et optimisée (2,6 Mo au lieu de 21 Mo) ; à installer après désinstallation d'une version de test
- La barre d'état garde un fond uni quand on fait défiler l'accueil
- Pastille du compteur de mises à jour (barre du bas) entièrement visible, posée sur le coin de l'icône

### Extension WordPress
- Numéro de version aligné sur l'application (1.2.0)

## 1.1.0 — 13 octobre 2026

### Application Android
- Nouveau design « WordPress Pro » : couleurs unies WordPress (mode clair et nuit), police Plus Jakarta Sans, icônes modernes
- Barre de navigation à 4 onglets : Accueil, Mises à jour, Historique, Réglages
- File de mises à jour : chaque élément affiche son état (en file, en cours avec chronomètre, terminé, échec) et la progression globale
- Bouton « Arrêter » et « Réessayer » en cas d'échec
- Sélection multiple des mises à jour à installer
- Compte local : numéro de téléphone + mot de passe, code PIN à 4 chiffres, empreinte digitale
- Notifications : nouvelles mises à jour disponibles et fin d'une série de mises à jour
- Vraies favicons des sites connectés
- Historique des 7 derniers jours avec statistiques

### Extension WordPress
- L'API renvoie l'icône du site (`icon_url`)

## 1.0.0 — 13 octobre 2026

### Application Android
- Connexion des sites par QR code ou code de connexion
- Plusieurs sites, multisite pris en charge
- Mise à jour du cœur, des extensions, des thèmes et des traductions
- Mise à jour manuelle par fichier `.zip`
- Mises à jour automatiques par extension / thème

### Extension WordPress
- API REST sécurisée `wpru/v1` (une clé par appareil, révocable)
- Page Réglages › Remote Updater avec QR code
- Téléversement de paquets `.zip` (remplacement de la version installée)
