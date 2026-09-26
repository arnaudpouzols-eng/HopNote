# Connecter Notion à HopNote

HopNote n'utilise pas de serveur : le jeton reste chiffré sur le téléphone et les appels vont directement de l'application à Notion.

## Une seule préparation dans Notion

1. Dans le portail développeur Notion, crée une **connexion interne** pour ton espace de travail et récupère son jeton d'installation.
2. Dans Notion, choisis une page parent (par exemple `Inbox`). Dans son menu `•••`, utilise **Add connections** et ajoute la connexion HopNote.
3. Dans HopNote, ouvre **Réglages → Notion**, saisis le jeton directement sur le téléphone et colle le lien de cette page parent.
4. Appuie sur **Connecter et créer HopNote**.

HopNote crée alors automatiquement une sous-page nommée `HopNote`. Si elle existe déjà, elle est réutilisée.

Ne colle jamais le jeton dans une conversation, un ticket ou le dépôt Git.

