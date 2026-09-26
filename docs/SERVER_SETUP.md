# Serveur HopNote : connexion Notion simple

Le serveur n'est pas un espace de stockage de notes. Il sert uniquement à associer une installation HopNote à une connexion Notion, puis à transmettre une capture vers Notion. Les jetons Notion sont chiffrés dans D1 avec AES-GCM.

## Hébergement retenu

Cloudflare Workers + D1. Le plan gratuit convient à la bêta et évite d'héberger un serveur en continu.

## Préparation Cloudflare

1. Créer un compte Cloudflare gratuit.
2. Installer Node.js LTS si nécessaire, puis se connecter avec `npx wrangler login` depuis `server`.
3. Créer la base : `npx wrangler d1 create hopnote`.
4. Copier son `database_id` dans `server/wrangler.jsonc`.
5. Appliquer le schéma : `npx wrangler d1 migrations apply hopnote --remote`.
6. Déployer une première fois : `npx wrangler deploy` ; noter l'URL publique obtenue.

## Préparation Notion

Dans la configuration de l'intégration Notion, sélectionner **Public integration**, puis renseigner l'URL de rappel :

`https://<ton-worker>.workers.dev/v1/notion/oauth/callback`

Ajouter ensuite les secrets Cloudflare, jamais dans Git :

```text
npx wrangler secret put NOTION_CLIENT_ID
npx wrangler secret put NOTION_CLIENT_SECRET
npx wrangler secret put PUBLIC_BASE_URL
npx wrangler secret put TOKEN_ENCRYPTION_KEY
```

`TOKEN_ENCRYPTION_KEY` doit être une clé aléatoire de 32 octets encodée base64url. Sa perte empêcherait de relire les connexions existantes ; elle doit être sauvegardée dans un gestionnaire de mots de passe.

## Ce qui reste à relier dans l'application

- remplacer la saisie manuelle de token par l'ouverture de l'URL OAuth ;
- conserver le jeton de session HopNote chiffré sur Android ;
- créer automatiquement la page `HopNote` après le retour OAuth ;
- envoyer les captures vers `/v1/captures` et gérer les nouvelles tentatives.

Avant toute publication, il faudra ajouter une politique de confidentialité, une suppression de compte/connexion et une protection contre les abus sur les endpoints.
