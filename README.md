# HopNote

> **Capturer d'abord, organiser ensuite.**

HopNote est une application Android pensée pour enregistrer une pensée en quelques secondes : on écrit ou on parle, et la capture reste disponible hors ligne dans un flux chronologique unique.

## État actuel — v0.1.5

- Capture de texte à lancement immédiat
- Capture vocale native (lorsqu'un service de reconnaissance est disponible)
- Enregistrement vocal automatique, avec annulation pendant cinq secondes
- Stockage local offline avec Room
- Flux chronologique unique
- Métadonnées limitées : identifiant, texte, date/heure, source, état de synchro
- Réglages préparant les futures connexions Google et Notion
- Clavier prêt dès l'ouverture et micro à accès direct
- Interface nocturne : violet ultraviolet cohérent sur toutes les actions et tous les signaux

Il n'y a volontairement ni catégorie, ni projet, ni tag, ni priorité, ni distinction note/tâche.

## Démarrer

1. Ouvrir le dossier `android/` dans Android Studio (version stable récente).
2. Laisser Android Studio installer le SDK Android 35 si nécessaire.
3. Lancer l'app sur un appareil ou émulateur Android 8.0+.

La reconnaissance vocale dépend des services installés sur l'appareil. La capture de texte et le stockage local fonctionnent sans réseau.

## Versions

La stratégie de versions et le backlog sont dans [docs/VERSIONS.md](docs/VERSIONS.md) et [docs/BACKLOG.md](docs/BACKLOG.md).
