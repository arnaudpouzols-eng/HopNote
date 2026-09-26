# Backlog HopNote

Principe directeur : **une idée arrive → HopNote la garde**. L'organisation n'appartient pas au parcours de capture.

## v0.1 — Capture locale

- [x] Projet Android Kotlin / Jetpack Compose
- [x] Saisie texte au lancement
- [x] Dictée vocale native
- [x] Dictée enregistrée automatiquement avec annulation courte
- [x] Base Room offline
- [x] Flux chronologique unique
- [x] Modèle minimal : `id`, `text`, `createdAt`, `source`, `syncStatus`
- [x] Écran Réglages préparant Google et Notion
- [ ] Tests unitaires du dépôt et tests d'interface de capture
- [ ] Gestion robuste des erreurs et permissions voix
- [x] Vérification d'installation sur appareil Android réel

## v0.2 — Copie Notion directe

- [x] Création guidée d'une intégration Notion personnelle
- [x] Stockage chiffré du jeton d'intégration sur l'appareil
- [x] Choix d'une page parent autorisée et création automatique d'une page « HopNote »
- [ ] Création idempotente d'un bloc par capture
- [ ] File locale de synchronisation et reprise après échec réseau
- [ ] État de synchro lisible dans Réglages, sans interrompre la capture

## v0.3 — Fiabilité de la copie

- [ ] Relance manuelle d'une synchronisation échouée
- [ ] Indication du dernier envoi réussi
- [ ] Diagnostic local des échecs sans exposer le contenu des captures
- [ ] Tests offline / retour réseau / doublons

## v0.4 — Raccourcis Android

- [ ] Widget Android minimal, placé sur l'écran d'accueil
- [ ] Action « Texte » : ouvre HopNote directement dans le champ déjà actif
- [ ] Action « Voix » : ouvre un parcours de dictée dédié et lance la capture après un seul appui
- [ ] Retour de confirmation bref, puis retour automatique à l'écran précédent
- [ ] Aucun flux, historique, catégorie ni réglage dans le widget
- [ ] Raccourcis de lanceur
- [ ] Partage Android « Envoyer vers HopNote »
- [ ] Démarrage et sauvegarde mesurés pour préserver la rapidité

## v0.5 — En mobilité

- [ ] Commandes vocales adaptées à la voiture
- [ ] Retour minimal et non distrayant
- [ ] Validation sur Android Auto / contraintes de sécurité applicables

## v0.6 — Organisation à la demande

- [ ] Résumé d'une période sélectionnée
- [ ] Regroupement et extraction de tâches à la demande
- [ ] Prévisualisation et contrôle utilisateur avant toute modification externe
- [ ] Aucun traitement IA dans le chemin de capture

## Pré-requis de publication v1.0

- [ ] Tests de bout en bout sur appareils réels
- [ ] Gestion des données, confidentialité et suppression des données locales
- [ ] Journal local et lisible des échecs de synchronisation
- [ ] Accessibilité et localisation
- [ ] Politique de confidentialité, fiche Play Store et support

## Après v1 — optionnel

- [ ] Compte Google et sauvegarde multi-appareils
- [ ] Firebase / Firestore si le besoin de restauration apparaît
- [ ] Interface Web Next.js / Vercel si la consultation sur PC devient utile
