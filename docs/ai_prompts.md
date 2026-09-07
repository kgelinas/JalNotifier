# Documentation des Prompts IA & Format de Sortie (JalNotifier) 🤖

Ce document détaille l'ensemble des variables dynamiques (paramètres entre `{}`) disponibles pour la personnalisation des modèles de prompts IA (génération de messages d'accroche et de réponses) dans **JalNotifier**, ainsi que le format de sortie attendu du modèle de langage.

---

## 1. Paramètres entre `{}` disponibles

Dans les paramètres de l'application (**Intelligence / IA**), vous pouvez personnaliser le modèle de prompt pour l'**Introduction** (premier message) et la **Réponse** (répondre à un message). 

Lors de l'exécution, JalNotifier remplace automatiquement les balises entre `{}` par les données réelles du profil et de la conversation.

### A. Variables globales & contextuelles

| Variable | Description |
| :--- | :--- |
| `{myProfile}` | Bloc textuel complet et formaté contenant toutes les clés/valeurs du profil de l'utilisateur connecté. |
| `{otherProfile}` | Bloc textuel complet et formaté contenant toutes les clés/valeurs du profil du membre visé. |
| `{history}` | Historique des derniers messages échangés dans la conversation. |
| `{specificMessage}` | Le message spécifique sélectionné auquel vous souhaitez répondre. |

---

### B. Variables du profil du destinataire (`{clé}`)

Toutes les propriétés du profil du membre avec lequel vous échangez sont accessibles dynamiquement entre crochets. Les principales variables sont :

| Variable | Description | Exemple de valeur |
| :--- | :--- | :--- |
| `{name}` | Nom d'utilisateur ou pseudo du membre | `Alexandre` |
| `{age}` | Âge du membre | `34` |
| `{sex}` | Sexe / Genre du membre | `Homme`, `Femme`, `Couple` |
| `{city}` | Ville ou localisation | `Montréal` |
| `{social_status}` | Statut social ou matrimonial | `Célibataire` |
| `{goals}` | Recherches et objectifs sur le réseau | `Rencontre amicale`, `Relation sérieuse` |
| `{sexual_orientation}` | Orientation sexuelle | `Hétérosexuel(le)` |
| `{relationship}` | Type de relation recherchée | `Libre`, `Exclusif` |
| `{fantasies}` | Fantasmes ou centres d'intérêt | `Voyages, Resto, Soirées` |
| `{profile_descriptions}` | Bio / Description rédigée sur le profil | `Passiońné de sport et de voyages...` |

> 💡 **Note :** Toute clé JSON additionnelle présente dans les données du profil du membre peut être injectée sous la forme `{nom_de_cle}`.

---

### C. Variables de votre propre profil (`{myClé}` ou `{myclé}`)

Toutes les propriétés de votre propre profil sont accessibles en ajoutant le préfixe `my` (ex: `{myName}` ou `{myname}`).

| Variable | Description |
| :--- | :--- |
| `{myName}` / `{myname}` | Votre nom d'utilisateur / pseudo |
| `{myAge}` / `{myage}` | Votre âge |
| `{mySex}` / `{mysex}` | Votre sexe / genre |
| `{myCity}` / `{mycity}` | Votre ville |
| `{mySocial_status}` / `{mysocial_status}` | Votre statut social |
| `{myGoals}` / `{mygoals}` | Vos objectifs de recherche |
| `{mySexual_orientation}` / `{mysexual_orientation}` | Votre orientation sexuelle |
| `{myRelationship}` / `{myrelationship}` | Votre type de relation recherchée |
| `{myFantasies}` / `{myfantasies}` | Vos fantasmes / centres d'intérêt |
| `{myProfile_descriptions}` / `{myprofile_descriptions}` | Votre bio / description de profil |

---

## 2. Description du format de sortie (Output Format)

### A. Instruction système automatique

Afin de garantir une génération propre et directement réutilisable dans le champ de saisie de l'application, **JalNotifier ajoute automatiquement la consigne système suivante** à la fin de chaque prompt envoyé à l'API LLM (Gemini / OpenAI compatible) :

```text
IMPORTANT: N'inclus aucun bloc de réflexion, de planification ou de chaîne de pensée. Ne sors absolument rien d'autre que le texte des messages finaux, sans guillemets, et sépare CHAQUE option EXACTEMENT par la chaîne '|||' sans rien d'autre.
```

---

### B. Séparateur d'options de message (`|||`)

Pour permettre à l'IA de proposer plusieurs choix de messages (par exemple 2 ou 3 variantes), les options doivent être séparées par la séquence exacte **`|||`**.

#### Exemple de sortie retournée par l'IA :

```text
Salut ! J'ai vu que tu aimais voyager, quelle a été ta destination préférée récemmement ?|||Bonjour Alexandre ! Tes photos de voyage sont superbes, tu es allé dans quel coin dernièrement ?
```

---

### C. Comportement de JalNotifier selon la réponse

1. **Option unique** : Si la réponse ne contient qu'une seule proposition (pas de séparateur `|||`), le texte est directement inséré dans la zone de texte de saisie du message.
2. **Options multiples** : Si la réponse contient le séparateur `|||` avec plusieurs propositions, une fenêtre d'options (*Bottom Sheet*) s'affiche dans JalNotifier pour vous permettre de sélectionner d'un clic la proposition de votre choix.

---

### D. Formats de repli (Fallbacks) gérés

Si le modèle IA ne respecte pas strictement le séparateur `|||`, le parseur de JalNotifier tente automatiquement de découper la réponse selon les formats suivants :

1. **Puces Markdown ou Listes numérotées** :
   ```text
   1. Premier message d'accroche...
   2. Deuxième message d'accroche...
   ```
2. **Double saut de ligne (`\n\n`)** :
   ```text
   Premier message d'accroche...

   Deuxième message d me d'accroche...
   ```

---

## 3. Exemples de Prompts par défaut

### Modèle d'introduction (Prompt d'accroche)

```text
Tu es un assistant qui aide à écrire un message d'accroche sur le réseau social JALF.
Mon profil :
Nom : {myName}, Âge : {myAge}, Sexe : {mySex}, Ville : {myCity}

Leur profil :
Nom : {name}, Âge : {age}, Sexe : {sex}, Ville : {city}
Bio : {profile_descriptions}

Écris EXACTEMENT DEUX courts messages d'accroche différents en français qui soient naturels et engageants.
```

### Modèle de réponse (Prompt de conversation)

```text
Tu es un assistant qui aide à répondre à un message sur le réseau social JALF.
Mon profil :
Nom : {myName}, Âge : {myAge}, Sexe : {mySex}, Ville : {myCity}

Leur profil :
Nom : {name}, Âge : {age}, Sexe : {sex}, Ville : {city}
Bio : {profile_descriptions}

Historique de la conversation :
{history}

Message spécifique auquel répondre : "{specificMessage}"

Écris EXACTEMENT DEUX courts messages de réponse différents en français qui soient naturels et engageants.
```
