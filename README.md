<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHJumpPads

*Plaques de saut (remplace JumpPads, reprend son fichier jumppads.yml).*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Plaques de saut (remplace JumpPads, reprend son fichier jumppads.yml).

Utilisé sur : **Opale**.

## Commandes

Commande `/shjumppads` (alias `/shjp`). Chaque sous-commande a sa permission, `shjumppads.admin` les donne toutes.

| Commande | Description | Permission |
|---|---|---|
| `/shjumppads create [puissance] [angle] [direction\|auto\|here]` | Crée une plaque sur le bloc visé ou sous vos pieds. | `shjumppads.admin.create` |
| `/shjumppads delete` | Supprime la plaque visée. | `shjumppads.admin.delete` |
| `/shjumppads set <power\|angle\|direction\|particle\|permission> <valeur\|none>` | Modifie la plaque visée. | `shjumppads.admin.set` |
| `/shjumppads info` | Affiche les réglages de la plaque visée. | `shjumppads.admin.info` |
| `/shjumppads list` | Liste les plaques (50 au maximum). | `shjumppads.admin.list` |
| `/shjumppads reload` | Recharge `config.yml` et `messages.yml`. | `shjumppads.admin.reload` |

`list` et `reload` marchent aussi depuis la console. `reload` ne relit pas `jumppads.yml` (les plaques en mémoire font foi), sauf s'il était illisible au démarrage. Une erreur de syntaxe annule le rechargement.

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shjumppads.use` | Être lancé par une plaque de saut. | `true` |
| `shjumppads.bypass.maxpower` | Dépasser la puissance maximale à la création et à la modification. | `op` |
| `shjumppads.admin` | Toute l'administration (parent des permissions ci-dessous). | `op` |
| `shjumppads.admin.create` | `create`. | `op` |
| `shjumppads.admin.delete` | `delete`. | `op` |
| `shjumppads.admin.set` | `set`. | `op` |
| `shjumppads.admin.info` | `info`. | `op` |
| `shjumppads.admin.list` | `list`. | `op` |
| `shjumppads.admin.reload` | `reload`. | `op` |

Chaque plaque peut aussi exiger une permission de son choix (`set permission <noeud>`, par exemple `shjumppads.pad.vip`), vérifiée en plus de `shjumppads.use`.

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`). Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHJumpPads/
├── <a href="src/main/resources/config.yml">config.yml</a>
├── <a href="src/main/resources/messages.yml">messages.yml</a>
└── jumppads.yml   plaques posées (créé par le plugin)
</pre>

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
