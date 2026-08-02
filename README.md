# 🎉 Plugin News Announcer

**Plugin Minecraft Paper pour announceur automatiquement les mises à jour de plugins sur Discord, votre site web et via un livre interactif dans le lobby !**

---

## ✨ Fonctionnalités principales

- **🔍 Détection automatique** : Scanne les plugins ajoutés, mis à jour ou retirés à chaque démarrage du serveur
- **📦 Association automatique GitHub** : Plus besoin de configurer chaque plugin manuellement - entrez simplement votre pseudo GitHub et le plugin fait le reste
- **💬 Annonces Discord** : Notification instantanée avec le nom du plugin, l'ancienne et nouvelle version, et le changelog
- **🌐 Intégration site web** : Envoie les actualités directement à votre API
- **📖 Livre interactif** : Les joueurs reçoivent un livre personnalisé au lobby avec les nouveautés qu'ils n'ont pas encore vues
- **🔄 Suivi par joueur** : Le système se souvient de ce que chaque joueur a déjà vu pour éviter les répétitions

---

## 🏗️ Architecture du système

Ce projet est composé de **deux plugins** qui fonctionnent ensemble :

### plugin-news-announcer
À installer sur **chaque serveur Paper** (survie, skyblock, etc.)
- Détecte les changements de plugins au démarrage
- Résout automatiquement le repo GitHub correspondant
- Récupère le changelog depuis GitHub
- Envoie les annonces sur Discord et votre site web
- Transmet les nouveautés au serveur lobby

### plugin-news-lobby
À installer **uniquement sur le serveur lobby**
- Reçoit les nouveautés de tous les mondes
- Stocke l'historique agrégé
- Gère l'envoi du livre personnalisé aux joueurs
- Commande `/nouveautes` pour revoir l'historique

**Aucun plugin custom n'est nécessaire côté proxy BungeeCord** : le relai utilise le canal natif `BungeeCord`/`Forward`, supporté nativement.

---

## 🚀 Installation rapide

### 1. Compiler les plugins

```bash
cd plugin-news-announcer && mvn clean package
cd plugin-news-lobby && mvn clean package
```

### 2. Déployer les fichiers JAR

- `plugin-news-announcer.jar` → dossier `plugins/` de chaque serveur Paper
- `plugin-news-lobby.jar` → dossier `plugins/` du serveur lobby uniquement

### 3. Configurer `plugin-news-announcer`

Éditez `plugins/PluginNewsAnnouncer/config.yml` :

```yaml
server-name: "MonServeur"

github:
  username: "VotrePseudoGitHub"
  token: "ghp_xxxx"          # Recommandé : augmente la limite d'API
  default-tag-prefix: "v"

discord:
  webhook-url: "https://discord.com/api/webhooks/..."

website:
  api-url: "https://monsite.com/api/plugin-news"
  api-key: "ma-cle"

lobby:
  target-server: "lobby"     # Nom du serveur dans BungeeCord
```

### 4. Configurer `plugin-news-lobby`

Éditez `plugins/PluginNewsLobby/config.yml` :

```yaml
book-title: "📢 Nouveautés du serveur"
retention-days: 60           # Historique conservé
first-join-window-days: 7   # Fenêtre pour les nouvelles connexions
review-window-days: 30       # Fenêtre pour /nouveautes
chat-ping:
  enabled: true
```

---

## 📋 Comment ça marche

1. **Au démarrage** : Le serveur scanne ses plugins et les compare au snapshot précédent
2. **Pour chaque mise à jour** : Le plugin trouve automatiquement le repo GitHub et récupère les notes de version
3. **Annonce immédiate** : Notification Discord + envoi à votre site web
4. **Transmission au lobby** : Les nouveautés sont envoyées au serveur lobby
5. **À la connexion au lobby** :
   - Première visite → Livre avec les nouveautés des 7 derniers jours
   - Visites suivantes → Livre avec uniquement les nouveautés non vues
6. **Commande `/nouveautes`** : Permet de revoir l'historique des 30 derniers jours

---

## ⚙️ Notes importantes

- **Matching plugin/repo** : Le matching se fait par normalisation du nom (minuscules, sans espaces/tirets). Un override manuel est possible dans `plugins:` si nécessaire.
- **Rate limit GitHub** : 60 requêtes/heure sans token, 5000/heure avec un Personal Access Token (scope `public_repo` suffit).
- **Format des tags** : `default-tag-prefix` sert de première tentative, puis le système tente sans préfixe et en fuzzy-match.

---

## 📦 Téléchargement

Téléchargez les dernières versions dans la section [Releases](https://github.com/herocraftlol/Plugin-Update-Announcer/releases).

---

## 📝 Licence

Ce projet est open source et disponible sous licence MIT.
