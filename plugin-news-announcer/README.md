# 📢 Plugin News Announcer

**Plugin Minecraft Paper** qui détecte automatiquement les mises à jour de plugins sur tes serveurs et diffuse les nouveautés où tu le souhaites !

---

## ✨ Fonctionnalités

🔍 **Détection automatique** — Scanne les plugins à chaque redémarrage du serveur et détecte les ajouts, mises à jour et suppressions.

🤖 **Résolution GitHub intelligente** — Associe automatiquement chaque plugin à son dépôt GitHub en utilisant simplement ton nom d'utilisateur. Plus besoin de configurer une liste manuelle !

📢 **Annonces multiples** — Diffuse les nouveautés simultanément sur :
- **Discord** via webhook
- **Ton site web** via API
- **Le lobby** pour une expérience centralisée

📖 **Livre personnalisé** — Chaque joueur reçoit un livre interactif dans le lobby contenant uniquement les nouveautés qu'il n'a pas encore vues.

⚡ **Simple à configurer** — Aucune liste à maintenir, le plugin fait tout automatiquement.

---

## 🏗️ Architecture du projet

Ce projet est composé de **2 plugins complémentaires** :

| Plugin | Emplacement | Rôle |
|--------|-------------|------|
| **plugin-news-announcer** | Chaque serveur Paper | Détecte les changements de plugins, récupère les changelogs depuis GitHub, et envoie les annonces |
| **plugin-news-lobby** | Serveur lobby uniquement | Reçoit les annonces de tous les mondes, stocke l'historique, et gère l'affichage du livre

## Composition (2 projets)

- `plugin-news-announcer/` → à installer sur **chaque serveur Paper** (survie, skyblock, etc.)
  Détecte les changements de plugins, résout le changelog, envoie Discord/site web,
  et transmet les nouveautés au lobby.

- `plugin-news-lobby/` → à installer **uniquement sur le serveur lobby**.
  Reçoit les nouveautés de tous les mondes, les stocke, gère le livre et la commande
  `/nouveautes`.

**Aucun plugin custom n'est nécessaire côté proxy BungeeCord** : le relai utilise le
canal natif `BungeeCord`/`Forward`, supporté nativement, rien à installer ni configurer
sur le proxy lui-même.

## Installation

### 1. Compiler
Dans chaque dossier :
```bash
mvn clean package
```

### 2. Déployer
- `plugin-news-announcer.jar` → `plugins/` de chaque serveur Paper (monde)
- `plugin-news-lobby.jar` → `plugins/` du serveur lobby uniquement

### 3. Configurer le plugin monde (`plugins/PluginNewsAnnouncer/config.yml`)

```yaml
server-name: "Survie"

github:
  username: "TonPseudoGithub"
  token: "ghp_xxxx"          # recommandé, augmente la limite de requêtes API
  default-tag-prefix: "v"

discord:
  webhook-url: "https://discord.com/api/webhooks/..."

website:
  api-url: "https://tonsite.com/api/plugin-news"
  api-key: "ta-clé"

lobby:
  target-server: "lobby"     # doit correspondre au nom du serveur dans Bungee
```

**Tu n'as plus besoin de déclarer chaque plugin un par un.** Le plugin liste tous
tes repos GitHub publics et associe automatiquement chaque plugin détecté par
ressemblance de nom. Un override manuel reste possible dans `plugins:` si un nom
ne correspond pas assez (voir les exemples commentés dans le fichier généré).

### 4. Configurer le plugin lobby (`plugins/PluginNewsLobby/config.yml`)

```yaml
book-title: "Nouveautés du serveur"
retention-days: 60          # combien de temps l'historique agrégé est conservé
first-join-window-days: 7   # fenêtre montrée à un joueur qui se connecte pour la 1ère fois
review-window-days: 30      # fenêtre montrée par /nouveautes
chat-ping:
  enabled: true
```

## Comportement

1. Au démarrage d'un monde : scan des plugins, comparaison avec le snapshot précédent
2. Pour chaque plugin mis à jour : recherche automatique du repo GitHub correspondant,
   récupération des notes de version de la release correspondante
3. Annonce immédiate sur Discord + site web (par monde)
4. Transmission au lobby (dès qu'un joueur est en ligne sur le monde, pour permettre l'envoi)
5. Le lobby stocke l'entrée dans son historique agrégé, envoie un ping de chat aux
   joueurs déjà connectés au lobby
6. À la connexion d'un joueur au lobby :
   - Première connexion jamais vue → livre avec les nouveautés des 7 derniers jours max
   - Connexions suivantes → livre avec uniquement ce qui est nouveau depuis sa dernière visite
   - Rien de nouveau → aucun livre ne s'ouvre
7. Un joueur peut toujours retaper `/nouveautes` pour revoir l'historique récent
   (30 jours par défaut), sans que ça affecte le suivi automatique

## Notes importantes

- **Nom du plugin vs nom du repo** : le matching se fait par normalisation du nom
  (minuscules, sans espaces/tirets). Si un repo ne ressemble pas du tout au nom du
  plugin, ajoute un override manuel dans `plugins:` de la config du monde.
- **Rate limit GitHub** : 60 req/h sans token, 5000/h avec un Personal Access Token
  (scope `public_repo` suffit).
- **Format des tags** : `default-tag-prefix` sert de première tentative ; en cas
  d'échec le fetcher retente sans préfixe, puis en fuzzy-match sur les releases récentes.
- Le endpoint de ton site doit accepter un POST JSON :
  `{ server, timestamp, updates: [{ plugin, type, oldVersion, newVersion, changelog }] }`
