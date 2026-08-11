# Plugin News Announcer

Pensé pour un réseau **Minecraft Velocity** multi-serveurs (survie, skyblock, mini-jeux,
lobby...). Détecte automatiquement les plugins ajoutés/mis à jour/retirés sur chaque
sous-serveur Paper — au démarrage, puis en continu à intervalle régulier pour un suivi
quasi temps réel — associe automatiquement chaque plugin à son repo sur
**https://github.com/herocraftlol** (aucune liste à maintenir), et diffuse les nouveautés :

- immédiatement sur **Discord** et sur ton **site web** (par sous-serveur)
- au **lobby**, agrégées pour TOUS les sous-serveurs (lobby inclus) : un ping de chat +
  un livre concis et clair, personnalisé par joueur selon ce qu'il a déjà vu

## Composition (3 projets)

- `plugin-news-announcer/` → à installer sur **chaque serveur Paper** (survie, skyblock, etc.)
  Détecte les changements de plugins, résout le changelog, envoie Discord/site web,
  et transmet les nouveautés au lobby.

- `plugin-news-lobby/` → à installer **uniquement sur le serveur lobby**.
  Reçoit les nouveautés de tous les mondes, les stocke, gère le livre et la commande
  `/nouveautes`.

- `plugin-news-proxy/` → **plugin Velocity**, à installer sur le **proxy lui-même**
  (`plugins/` de Velocity, pas d'un serveur Paper). Relaie les messages entre serveurs.

### ⚠️ Pourquoi un plugin proxy est nécessaire (et pas juste le canal BungeeCord natif)

Velocity expose une compatibilité avec le canal legacy `BungeeCord`/`Forward`, en théorie
suffisante pour ce genre de relai sans rien installer côté proxy. **En pratique, ce canal a
un bug connu qui empêche de relayer vers un sous-canal personnalisé** (comme
`pluginnews:feed`) — voir [PaperMC/Velocity#1312](https://github.com/PaperMC/Velocity/issues/1312).
Résultat concret : les messages partent bien du sous-serveur, mais n'arrivent jamais au
lobby, **sans aucune erreur ni côté monde ni côté lobby** — silencieusement.

Ce pack contourne donc ce bug avec `plugin-news-proxy` : un canal moderne namespacé
(`pluginnews:feed`) enregistré explicitement via l'API Velocity, indépendant du réglage
`bungee-plugin-message-channel` et fiable quelle que soit la version de Velocity.

Ce pack est préconfiguré pour l'organisation GitHub **https://github.com/herocraftlol** :
chaque plugin Paper installé sur un sous-serveur (survie, skyblock, mini-jeux, lobby
inclus) est automatiquement rapproché du repo correspondant dans cette organisation par
ressemblance de nom, sans rien à déclarer manuellement.

## Installation

### 1. Compiler
Dans chaque dossier :
```bash
mvn clean package
```

### 2. Déployer
- `plugin-news-proxy.jar` → `plugins/` **du proxy Velocity lui-même**. Redémarre le proxy
  après ça — sans lui, aucun message n'arrivera jamais au lobby (voir l'encart plus haut).
- `plugin-news-announcer.jar` → `plugins/` de **chaque serveur Paper**, y compris le
  lobby lui-même (survie, skyblock, mini-jeux, lobby...). C'est ce qui détecte les
  changements de plugins sur ce serveur précis.
- `plugin-news-lobby.jar` → `plugins/` du **serveur lobby en plus**. C'est ce qui reçoit
  les nouveautés de tous les sous-serveurs (y compris celles du lobby lui-même envoyées
  par `plugin-news-announcer` installé juste au-dessus) et gère le livre + `/nouveautes`.

  Sur le lobby, `plugin-news-announcer` doit avoir `server-name: "Lobby"` et
  `lobby.target-server: "lobby"` (il s'envoie donc les nouveautés à lui-même, relayées
  par `plugin-news-proxy`) — c'est ce qui permet d'inclure les mises à jour de plugins
  du lobby dans le livre, comme demandé.

### 3. Configurer le plugin monde (`plugins/PluginNewsAnnouncer/config.yml`)

```yaml
server-name: "Survie"

github:
  username: "herocraftlol"   # https://github.com/herocraftlol — déjà préconfiguré
  token: "ghp_xxxx"          # recommandé, augmente la limite de requêtes API (60 -> 5000/h)
  default-tag-prefix: "v"

# Relance le cycle de détection (scan des jars + vérification des dernières releases
# GitHub) toutes les N minutes, sans attendre un redémarrage du serveur. 0 = désactivé.
scan-interval-minutes: 15

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
book-author: "HeroCraft"     # auteur affiché sur le livre
retention-days: 60           # combien de temps l'historique agrégé est conservé
first-join-window-days: 7    # fenêtre montrée à un joueur qui se connecte pour la 1ère fois
default-window-days: 7       # fenêtre MINIMALE garantie à chaque connexion (voir plus bas)
auto-open:
  default: true               # affichage auto du livre à la connexion (activable/désactivable
  min-interval-hours: 6       # par joueur via /nouveautes auto on|off)
review-window-days: 30       # fenêtre montrée par /nouveautes (relecture manuelle)
chat-ping:
  enabled: true
http-api:                    # flux JSON pour ton site web (voir section dédiée plus bas)
  enabled: false
  port: 8085
  api-key: ""
```

## Comportement

1. Au démarrage de chaque sous-serveur (survie, skyblock, mini-jeux, lobby...) : scan des
   plugins Paper installés, comparaison avec le snapshot précédent. Ce scan est ensuite
   **rejoué automatiquement toutes les `scan-interval-minutes`** (15 min par défaut) tant
   que le serveur tourne, pour capter un déploiement à chaud ou une nouvelle release
   publiée sur GitHub sans attendre un redémarrage.
2. Pour chaque plugin ajouté/mis à jour : recherche automatique du repo correspondant
   dans https://github.com/herocraftlol, récupération des notes de version de la
   release correspondant à la nouvelle version détectée
3. Annonce immédiate sur Discord + site web (par sous-serveur)
4. Transmission au lobby (dès qu'un joueur est en ligne sur le sous-serveur, pour
   permettre l'envoi via `pluginnews:feed`, relayé par `plugin-news-proxy` sur le proxy)
5. Le lobby stocke l'entrée dans son **journal agrégé** (tous sous-serveurs confondus,
   lobby lui-même inclus — gardé en mémoire pour un accès instantané, persisté sur disque
   pour survivre aux redémarrages), envoie un ping de chat aux joueurs déjà connectés
6. À la connexion d'un joueur au lobby, le livre s'ouvre automatiquement (sauf s'il a
   désactivé ça, voir plus bas) avec le contenu suivant :
   - **Au moins** les nouveautés des `default-window-days` derniers jours (7 par défaut) —
     cette fenêtre minimale est **toujours garantie**, quelle que soit la fréquence de
     connexion du joueur
   - Élargie automatiquement si sa dernière visite remonte à plus longtemps que ça (il
     verra alors tout ce qui est nouveau depuis cette dernière visite)
   - Pas rouvert plus d'une fois toutes les `auto-open.min-interval-hours` heures (6h par
     défaut), pour ne pas spammer un joueur qui se reconnecte souvent
7. `/nouveautes` : revoir l'historique récent (30 jours par défaut) à tout moment
8. `/nouveautes auto on|off` : chaque joueur peut désactiver/réactiver l'ouverture
   automatique du livre à la connexion (il garde `/nouveautes` pour consulter à la demande)
9. `/nouveautes date jj/mm/aaaa` : consulter le journal des nouveautés d'un jour précis
   (ex: `/nouveautes date 05/08/2026`)

## Flux JSON pour le site web

Si `http-api.enabled: true`, le lobby expose deux routes en HTTP simple (JDK natif, aucune
dépendance) directement interrogeables par ton site :

- `GET http://<ip-du-lobby>:8085/news/recent?days=7&limit=7` → les nouveautés des 7
  derniers jours (paramètres facultatifs, 7/7 par défaut), triées du plus récent au
  plus ancien
- `GET http://<ip-du-lobby>:8085/news/date?date=2026-08-05` → le journal complet du
  jour précisé (format `AAAA-MM-JJ`)

Si `http-api.api-key` est renseigné, chaque requête doit inclure l'en-tête
`X-API-Key: ta-clé`. **Recommandé si ce port est exposé publiquement** — sinon,
restreins-le à ton réseau interne (pare-feu / bind local) plutôt que de l'exposer sur
Internet sans clé.

Chaque entrée JSON a la forme :
```json
{ "world": "Survie", "plugin": "GradePlugin", "type": "ADDED",
  "oldVersion": null, "newVersion": "2.1.0", "changelog": "...", "timestamp": 1754400000000 }
```

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

## Dépannage

- **Aucun livre ne s'ouvre au lobby, aucune erreur nulle part** : c'est le symptôme
  classique du bug Velocity #1312 décrit plus haut. Vérifie que `plugin-news-proxy.jar`
  est bien installé et actif sur le **proxy Velocity** (pas un serveur Paper), et regarde
  ses logs au démarrage : tu dois voir `[PluginNewsProxy] Canal pluginnews:feed
  enregistré, relai actif entre sous-serveurs.`
- **`[PluginNewsProxy] Serveur cible "..." introuvable`** : le `lobby.target-server`
  configuré côté `plugin-news-announcer` ne correspond à aucun nom de serveur déclaré
  dans `velocity.toml` (section `[servers]`). La casse compte.
- **Rien dans `plugins/PluginNewsLobby/news_feed.json`** : le message n'est jamais arrivé
  au lobby. Vérifie dans l'ordre : `plugin-news-proxy` actif sur le proxy → nom de
  serveur cible correct → un joueur bien en ligne sur le sous-serveur au moment du scan
  (sinon l'envoi est différé jusqu'à la prochaine connexion sur CE serveur).
- **Le livre ne s'ouvre pas alors que `news_feed.json` contient des entrées récentes** :
  supprime ta ligne (ton UUID) dans `plugins/PluginNewsLobby/player_data.yml` pour forcer
  le plugin à considérer que tu n'as encore rien vu, puis reconnecte-toi.
