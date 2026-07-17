# Plugin News Announcer

Détecte automatiquement les plugins ajoutés/mis à jour/retirés à chaque démarrage
d'un serveur Paper, récupère le changelog GitHub (ou Spigot, ou fichier local),
et diffuse l'annonce sur Discord, dans le lobby (via BungeeCord), et sur ton site web.

## Composition

- `plugin-news-announcer/` → le plugin Paper à installer sur **chaque serveur** (survie, skyblock, etc.)
- `plugin-news-announcer-bungee/` → le module BungeeCord à installer **une seule fois sur le proxy**, qui reçoit les annonces et les diffuse aux joueurs du lobby

## Installation

### 1. Compiler

Depuis chaque dossier :
```bash
mvn clean package
```
Le jar buildé se trouve dans `target/`.

### 2. Déployer

- `plugin-news-announcer.jar` → dans `plugins/` de **chaque serveur Paper** que tu veux surveiller
- `plugin-news-announcer-bungee.jar` → dans `plugins/` de ton **proxy BungeeCord**

### 3. Configurer

Au premier lancement, le fichier `plugins/PluginNewsAnnouncer/config.yml` est généré.
Édite-le pour chaque serveur :

```yaml
server-name: "Survie"   # change selon le serveur

discord:
  webhook-url: "https://discord.com/api/webhooks/TON_ID/TON_TOKEN"

website:
  api-url: "https://tonsite.com/api/plugin-news"
  api-key: "ta-clé"

github:
  token: "ghp_xxx"   # optionnel, augmente la limite de requêtes GitHub

plugins:
  WorldGuard:
    source: github
    repo: "EngineHub/WorldGuard"
    tag-prefix: "v"
```

### 4. Ajouter un nouveau plugin GitHub à surveiller

Il suffit d'ajouter une entrée dans la section `plugins:` :

```yaml
  NomDuPlugin:
    source: github
    repo: "Auteur/NomDuRepo"
    tag-prefix: ""   # ou "v" selon comment le repo tague ses releases
```

Le nom **doit correspondre exactement** au champ `name:` du `plugin.yml` interne du jar
(généralement identique au nom du fichier, mais vérifie si besoin en dézippant le jar).

## Comment ça marche

1. Au démarrage, le plugin scanne `plugins/*.jar` et lit nom+version dans chaque `plugin.yml`
2. Il compare avec `plugins_snapshot.yml` (sauvegardé au dernier démarrage)
3. Pour chaque plugin **mis à jour**, il va chercher les notes de version sur GitHub
   (release correspondant au tag de la nouvelle version), Spigot, ou un fichier local
4. Il construit un message et l'envoie sur Discord, le site, et le lobby
5. Il met à jour le snapshot pour la prochaine comparaison

## Notes importantes

- **Rate limit GitHub** : sans token, 60 requêtes/heure. Avec un token (gratuit, scope
  `public_repo` en lecture suffit), 5000/heure. À configurer si tu as beaucoup de plugins.
- **Annonce lobby** : comme aucun joueur n'est connecté au moment du démarrage du serveur,
  le message est mis en attente et envoyé dès la première connexion d'un joueur.
- **Tags GitHub non trouvés directement** : le fetcher retente automatiquement en listant
  les releases récentes et en cherchant un tag qui contient le numéro de version, donc
  ça fonctionne même si tous tes repos ne suivent pas exactement le même format de tag.
- Le endpoint de ton site (`website.api-url`) doit accepter un POST JSON avec la structure :
  `{ server, timestamp, updates: [{ plugin, type, oldVersion, newVersion, changelog }] }`
