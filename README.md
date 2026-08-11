# 📢 Plugin Update Announcer

> Annonce automatiquement, sur tout un réseau Minecraft Velocity multi-serveurs, chaque
> plugin **ajouté**, **mis à jour** ou **retiré** — sur **Discord**, sur ton **site web**
> et dans un **livre interactif** au **lobby**.

Conçu pour le réseau **[herocraftlol](https://github.com/herocraftlol)**, ce pack détecte
les changements de plugins sur chaque sous-serveur Paper, retrouve tout seul le dépôt GitHub
qui correspond, récupère les notes de version, et diffuse les nouveautés aux joueurs et aux
plateformes externes — **sans aucune configuration manuelle par plugin**.

---

## ✨ En deux mots

- 🔍 **Détection automatique** au démarrage, puis **en continu** toutes les N minutes
  (par défaut 15 min) pour un suivi quasi temps réel, même à chaud.
- 🤖 **Résolution GitHub intelligente** : liste tous les dépôts publics de
  `github.com/herocraftlol` et associe chaque plugin détecté au bon repo par ressemblance
  de nom. **Aucune liste à maintenir.**
- 📢 **Annonces multiples et simultanées** : Discord (webhook), site web (API JSON),
  et lobby (livre + ping de chat).
- 📖 **Livre personnalisé par joueur** : chacun ne voit que les nouveautés qu'il n'a pas
  encore découvertes, groupées par sous-serveur puis par plugin.
- 🌉 **Plugin proxy Velocity dédié** qui contourne le [bug connu #1312](https://github.com/PaperMC/Velocity/issues/1312)
  de Velocity sur le canal legacy BungeeCord/Forward.

---

## 🧩 Composition (3 modules)

Le projet est composé de trois plugins Maven indépendants, à installer chacun au bon
endroit de ton réseau :

| Module | Type | Où l'installer | Rôle |
|--------|------|-----------------|------|
| **`plugin-news-announcer`** | Plugin Paper (Bukkit) | Sur **chaque serveur Paper** (survie, skyblock, mini-jeux, lobby…) | Détecte les changements de plugins, résout le changelog, envoie sur Discord / site web / lobby |
| **`plugin-news-lobby`** | Plugin Paper (Bukkit) | Sur le **serveur lobby** (en plus de `plugin-news-announcer`) | Reçoit les nouveautés de tous les sous-serveurs, stocke l'historique agrégé, gère le livre + `/nouveautes` |
| **`plugin-news-proxy`** | Plugin **Velocity** | Sur le **proxy lui-même** (`plugins/` de Velocity) | Relaie les messages `pluginnews:feed` entre sous-serveurs |

> ℹ️ Le module **`plugin-news-proxy`** est **nouveau dans la v1.1.0**. Il est indispensable
> au bon fonctionnement du lobby — sans lui, les messages partent des sous-serveurs mais
> n'arrivent jamais au lobby, **silencieusement et sans aucune erreur** (voir la section
> « Pourquoi un plugin proxy » ci-dessous).

---

## 🌉 Pourquoi un plugin proxy est nécessaire

Velocity expose une compatibilité avec le canal legacy `BungeeCord`/`Forward`, en théorie
suffisante pour ce genre de relai sans rien installer côté proxy. **En pratique, ce canal a
un bug connu qui empêche de relayer vers un sous-canal personnalisé** (comme
`pluginnews:feed`) — voir [PaperMC/Velocity#1312](https://github.com/PaperMC/Velocity/issues/1312).

Résultat concret : les messages partent bien du sous-serveur, mais n'arrivent jamais au
lobby, **sans aucune erreur ni côté monde ni côté lobby** — silencieusement.

Ce pack contourne donc ce bug avec `plugin-news-proxy` : un canal moderne namespacé
(`pluginnews:feed`) enregistré explicitement via l'API Velocity, indépendant du réglage
`bungee-plugin-message-channel` et fiable quelle que soit la version de Velocity.

---

## 📥 Installation

### 1. Récupérer les fichiers

Télécharge les `.jar` depuis la [page des releases](https://github.com/herocraftlol/Plugin-Update-Announcer/releases) :

- `plugin-news-announcer-v1.2.0.jar`
- `plugin-news-lobby-v1.2.0.jar`
- `plugin-news-proxy-v1.2.0.jar`

### 2. Déployer

- `plugin-news-proxy.jar` → dossier `plugins/` **du proxy Velocity lui-même**. Redémarre le
  proxy après ça — sans lui, aucun message n'arrivera jamais au lobby.
- `plugin-news-announcer.jar` → dossier `plugins/` de **chaque serveur Paper**, y compris le
  lobby (survie, skyblock, mini-jeux, lobby…). C'est ce qui détecte les changements sur ce
  serveur précis.
- `plugin-news-lobby.jar` → dossier `plugins/` du **serveur lobby en plus**. C'est ce qui
  reçoit les nouveautés de tous les sous-serveurs (y compris celles du lobby lui-même) et
  gère le livre + `/nouveautes`.

  Sur le lobby, `plugin-news-announcer` doit avoir `server-name: "Lobby"` et
  `lobby.target-server: "lobby"` (il s'envoie donc les nouveautés à lui-même, relayées par
  `plugin-news-proxy`) — c'est ce qui permet d'inclure les mises à jour de plugins du lobby
  dans le livre.

### 3. Configurer le plugin monde (`plugins/PluginNewsAnnouncer/config.yml`)

```yaml
server-name: "Survie"

github:
  username: "herocraftlol"   # déjà préconfiguré — https://github.com/herocraftlol
  token: "ghp_xxxx"          # recommandé, augmente la limite API (60 -> 5000 req/h)
  default-tag-prefix: "v"

# Relance le cycle de détection (scan des jars + vérification des dernières releases
# GitHub) toutes les N minutes, sans attendre un redémarrage. 0 = désactivé.
scan-interval-minutes: 15

discord:
  webhook-url: "https://discord.com/api/webhooks/..."

website:
  api-url: "https://tonsite.com/api/plugin-news"
  api-key: "ta-clé"

lobby:
  target-server: "lobby"     # doit correspondre au nom du serveur dans velocity.toml
```

**Tu n'as plus besoin de déclarer chaque plugin un par un.** Le plugin liste tous les repos
GitHub publics de l'organisation et associe automatiquement chaque plugin détecté par
ressemblance de nom. Un override manuel reste possible dans la section `plugins:` si un nom
ne correspond pas assez.

### 4. Configurer le plugin lobby (`plugins/PluginNewsLobby/config.yml`)

```yaml
book-title: "Nouveautés du serveur"
book-author: "HeroCraft"
retention-days: 60          # durée de conservation de l'historique agrégé
first-join-window-days: 7   # fenêtre montrée à un joueur qui se connecte pour la 1re fois
review-window-days: 30      # fenêtre montrée par /nouveautes
chat-ping:
  enabled: true
```

---

## ⚙️ Comportement en détail

1. **Au démarrage** de chaque sous-serveur : scan des plugins Paper installés, comparaison
   avec le snapshot précédent. Ce scan est ensuite **rejoué automatiquement toutes les
   `scan-interval-minutes`** (15 min par défaut) tant que le serveur tourne, pour capter un
   déploiement à chaud ou une nouvelle release publiée sur GitHub sans attendre un
   redémarrage.
2. Pour chaque plugin **ajouté / mis à jour** : recherche automatique du repo correspondant
   dans `github.com/herocraftlol`, récupération des notes de version de la release
   correspondant à la nouvelle version détectée.
3. **Annonce immédiate** sur Discord + site web (par sous-serveur).
4. **Transmission au lobby** (dès qu'un joueur est en ligne sur le sous-serveur, pour
   permettre l'envoi via `pluginnews:feed`, relayé par `plugin-news-proxy` sur le proxy).
5. Le lobby **stocke l'entrée** dans son historique agrégé (tous sous-serveurs confondus,
   lobby lui-même inclus), envoie un ping de chat aux joueurs déjà connectés au lobby.
6. **À la connexion d'un joueur** au lobby :
   - Première connexion jamais vue sur le réseau → livre avec les nouveautés des 7 derniers
     jours max (`first-join-window-days`).
   - Connexions suivantes → livre avec uniquement ce qui est nouveau depuis sa dernière
     visite, groupé par sous-serveur puis par plugin, concis et lisible.
   - Rien de nouveau depuis sa dernière visite → aucun livre ne s'ouvre.
7. Un joueur peut toujours retaper `/nouveautes` pour revoir l'historique récent
   (30 jours par défaut), sans que ça affecte le suivi automatique par joueur.

---

## 🔧 Compilation depuis les sources

Pré-requis : **JDK 17+** (testé avec JDK 21) et **Maven 3.6+**.

Dans chaque dossier de module :

```bash
cd plugin-news-announcer && mvn clean package
cd plugin-news-lobby      && mvn clean package
cd plugin-news-proxy      && mvn clean package
```

Les fichiers `.jar` sont générés dans `target/` de chaque module.

---

## 📝 Notes importantes

- **Nom du plugin vs nom du repo** : le matching se fait par normalisation du nom
  (minuscules, sans espaces/tirets). Si un repo ne ressemble pas du tout au nom du plugin,
  ajoute un override manuel dans `plugins:` de la config du monde.
- **Rate limit GitHub** : 60 req/h sans token, 5000/h avec un Personal Access Token
  (scope `public_repo` suffit).
- **Format des tags** : `default-tag-prefix` sert de première tentative ; en cas d'échec le
  fetcher retente sans préfixe, puis en fuzzy-match sur les releases récentes.
- Le endpoint de ton site doit accepter un POST JSON :
  `{ server, timestamp, updates: [{ plugin, type, oldVersion, newVersion, changelog }] }`.

---

## 🛠️ Dépannage

- **Aucun livre ne s'ouvre au lobby, aucune erreur nulle part** : c'est le symptôme
  classique du bug Velocity #1312 décrit plus haut. Vérifie que `plugin-news-proxy.jar` est
  bien installé et actif sur le **proxy Velocity** (pas un serveur Paper), et regarde ses
  logs au démarrage : tu dois voir `[PluginNewsProxy] Canal pluginnews:feed enregistré,
  relai actif entre sous-serveurs.`
- **`[PluginNewsProxy] Serveur cible "..." introuvable`** : le `lobby.target-server`
  configuré côté `plugin-news-announcer` ne correspond à aucun nom de serveur déclaré dans
  `velocity.toml` (section `[servers]`). La casse compte.
- **Rien dans `plugins/PluginNewsLobby/news_feed.json`** : le message n'est jamais arrivé
  au lobby. Vérifie dans l'ordre : `plugin-news-proxy` actif sur le proxy → nom de serveur
  cible correct → un joueur bien en ligne sur le sous-serveur au moment du scan (sinon
  l'envoi est différé jusqu'à la prochaine connexion sur CE serveur).
- **Le livre ne s'ouvre pas alors que `news_feed.json` contient des entrées récentes** :
  supprime ta ligne (ton UUID) dans `plugins/PluginNewsLobby/player_data.yml` pour forcer
  le plugin à considérer que tu n'as encore rien vu, puis reconnecte-toi.

---

## 🆕 Nouveautés de la version

### v1.2.0

- 📦 **Fichiers `.jar` compilés et publiés** pour les trois modules
  (`plugin-news-announcer`, `plugin-news-lobby`, `plugin-news-proxy`) directement dans la
  [release v1.2.0](https://github.com/herocraftlol/Plugin-Update-Announcer/releases/tag/v1.2.0),
  prêts à déposer dans les dossiers `plugins/` — plus besoin de compiler soi-même.
- ⚙️ **Configurations pré-remplies et documentées** : les `config.yml` embarqués dans les
  jars (et fournis en exemple à la racine) contiennent désormais les bons identifiants du
  réseau HeroCraft (serveur web, clé API, organisation GitHub) et des commentaires pas à pas
  pour chaque serveur (Survie, Lobby…).
- 🐛 **Jars plus légers** : `paper-api` est désormais en scope `provided`, les `.jar` finaux
  passent de ~35 Mo à ~350 ko (l'API n'est plus embarquée inutilement).
- 📖 **README principal et notes de version enrichis** : description agréable à lire,
  explication détaillée du rôle de chaque module, dépannage et installation claire.

### v1.1.0

- 🌉 **Nouveau module `plugin-news-proxy`** (plugin Velocity) qui contourne le bug connu
  [#1312](https://github.com/PaperMC/Velocity/issues/1312) de Velocity et relaie fiablement
  les messages `pluginnews:feed` entre sous-serveurs. Sans lui, les nouveautés partaient
  des sous-serveurs mais n'arrivaient jamais au lobby — silencieusement.
- 🔁 **Détection en continu** : scan des jars + vérification des releases GitHub rejoué
  automatiquement toutes les `scan-interval-minutes` (15 min par défaut), sans attendre un
  redémarrage.

### v1.0.x

- 🔍 Détection automatique des plugins ajoutés / mis à jour / retirés au démarrage.
- 🤖 Résolution GitHub intelligente par ressemblance de nom (aucune liste à maintenir).
- 📢 Annonces simultanées Discord (webhook), site web (API JSON) et lobby (livre + ping).
- 📖 Livre personnalisé par joueur, groupé par sous-serveur puis par plugin.

---

## 📦 Téléchargement

👉 [**Dernière version (v1.2.0)**](https://github.com/herocraftlol/Plugin-Update-Announcer/releases/latest)

---

_Repos GitHub préconfiguré : [https://github.com/herocraftlol](https://github.com/herocraftlol)_
