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

## v0.2 — Compte et sauvegarde

- [ ] Authentification Google
- [ ] Firebase / Firestore avec synchronisation des captures
- [ ] File de synchronisation et résolution d'échecs réseau
- [ ] Écran de statut de sauvegarde, sans interrompre la capture

## v0.3 — Web et Notion

- [ ] Interface Web Next.js déployée sur Vercel
- [ ] Consultation du flux depuis le Web
- [ ] Connexion Notion explicite
- [ ] Synchronisation unidirectionnelle vers une unique page « HopNote »
- [ ] Idempotence : aucune capture dupliquée dans Notion

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
- [ ] Gestion des données, confidentialité et suppression de compte
- [ ] Monitoring des échecs de synchronisation
- [ ] Accessibilité et localisation
- [ ] Politique de confidentialité, fiche Play Store et support
