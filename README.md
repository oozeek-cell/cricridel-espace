# Cricri de l'espace — Compilation APK automatique via GitHub Actions

Ce dépôt contient l'intégralité du code source Android (Kotlin + JBox2D) ainsi que le workflow `.github/workflows/build-apk.yml` qui compile l'APK dans le cloud à chaque `push`.

## Récupérer l'APK compilé
1. Ouvre l'onglet **Actions** de ton dépôt GitHub (ou la section **Releases** à droite de la page d'accueil du dépôt).
2. Dès que le workflow **Build Cricri de l'espace APK** affiche une coche verte (`✓`), télécharge `app-debug.apk` directement depuis **Releases** (ou l'artifact `CricriDeLEspace-debug-apk`).
