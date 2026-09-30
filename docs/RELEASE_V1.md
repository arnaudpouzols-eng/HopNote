# HopNote v1.0.0 — checklist de publication

## Validé localement

- Version Android : `1.0.0` (`versionCode 100`), cible Android 16 / API 36.
- Capture texte et vocale, stockage local Room et nettoyage configurable.
- Synchronisation Notion en file persistante : reprise au retour du réseau et nouvelles tentatives progressives.
- Sauvegardes Android, transferts d'appareil et trafic HTTP non chiffré désactivés.
- APK de test compilé, signé avec la clé de développement et vérifié.
- Bundle release compilé pour vérifier le contenu de diffusion.
- Vérification TypeScript du Worker Cloudflare effectuée.

## À faire juste avant l'envoi Play

1. Créer une clé d'envoi Play dans Android Studio : **Build > Generate Signed Bundle / APK > Android App Bundle**.
2. Conserver la clé et son mot de passe dans une sauvegarde chiffrée privée. Ne jamais les ajouter à GitHub.
3. Générer le bundle signé `HopNote-v1.0.0.aab`.
4. Dans Google Play Console, créer l'application puis importer le bundle dans le test fermé.
5. Ajouter la fiche Play : icône, captures d'écran, description courte et longue, e-mail de contact, catégorie et lien vers la politique de confidentialité.
6. Compléter les formulaires Play : sécurité des données, accès à l'application et classification du contenu.
7. Tester l'installation depuis le canal fermé avant toute demande de production.

## Hors périmètre v1

- Commande vocale Google Assistant / Android Auto pour créer une note. Le widget vocal reste le parcours voiture pris en charge.
