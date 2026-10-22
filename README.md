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

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/shjumppads` | `/shjp` | Gestion des plaques de saut. | — |

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shjumppads.use` | Utiliser les plaques de saut. | `true` |
| `shjumppads.admin` | Toutes les commandes de gestion. | `op` |
| `shjumppads.maxpower.bypass` | Dépasser la puissance maximale à la création. | `op` |

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`). Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHJumpPads/
└── <a href="src/main/resources/config.yml">config.yml</a>
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
