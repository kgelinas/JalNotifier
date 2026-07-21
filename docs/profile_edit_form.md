# JALF Profile Edit Form (`/ct/profile`) & REST Mapping

This document provides a comprehensive specification of the HTML form fields on the profile editing page (`/ct/profile`) and their corresponding mappings to the REST API (`GET /rest/users/{userId}`).

## 1. Overview & Form Endpoint
- **URL**: `POST /ct/profile`
- **Encoding**: `application/x-www-form-urlencoded; charset=ISO-8859-1`
- **Purpose**: Updating user profile details in Ghost Mode / background sync.

---

## 2. HTML Form Field Mapping Table

| HTML Form Field Name | HTML Element Type | Values / Options | REST API Field (`GET /rest/users/{userId}`) | REST Link Format | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`Sexe`** | `<select>` | `1`=Homme, `2`=Femme, `4`=Couple, `8`=Travesti, `16`=Trans, `32`=Couple de femmes, `64`=Couple d'hommes | `sex_link` | `/rest/sexes/{id}` | User's own gender/sex identity. |
| **`Ornt`** | `<select>` | `1`=Hétérosexuel(le), `2`=Bi-curieux(se), `3`=Bisexuel(le), `4`=Homosexuel(le), `7`=Pansexuel(le) | `sexual_orientation_link` | `/rest/sexual-orientations/{id}` | **User's own sexual orientation**. |
| **`Marit`** | `<select>` | `1`=Célibataire, `2`=Séparé(e), `3`=En relation, `4`=Marié(e), `5`=Veuf/Veuve, `6`=Amant/Maîtresse, `7`=Autre | `social_status_link` | `/rest/social-statuses/{id}` | Civil/Social status. |
| **`Occp`** | `<select>` | `1`=Employé, `2`=Entrepreneur, `3`=Étudiant, `4`=Professionnel, `5`=Sans emploi, `6`=Retraité, `7`=A la maison, `8`=Autre, `9`=Non spécifié | `occupation_link` | `/rest/occupations/{id}` | Occupation. |
| **`Ethn`** | `<select>` | `1`=Blanc, `2`=Noir, `3`=Asiatique, `4`=Hispanique, `5`=Moyen-Orient, `6`=Autre, `7`=Amérindien, `8`=Indien, `9`=Métis | `ethnic_group_link` | `/rest/ethnic-groups/{id}` | Ethnicity. |
| **`Dispo`** | `<select>` | `1`..`11` (Schedule options) | `schedule_available_link` | `/rest/schedules-available/{id}` | Availability schedule. |
| **`Fumr`** | `<select>` | `1`=Oui, `2`=Non, `3`=Occasionnellement, `4`=Électronique | `smoking_link` | `/rest/smoking/{id}` | Smoking preferences. |
| **`Sign`** | `<select>` | `1`..`12` (Zodiac signs) | `zodiac_sign_link` | `/rest/zodiac-signs/{id}` | Zodiac sign. |
| **`Alcl`** | `<select>` | `1`..`9` (Alcohol options) | `alcohol_use_link` | `/rest/alcohol-uses/{id}` | Alcohol consumption. |
| **`Drog`** | `<select>` | `1`..`7` (Drug options) | `drug_use_link` | `/rest/drug-uses/{id}` | Drug use preferences. |
| **`Hght`** | `<select>` | `1`..`37` (Height IDs) | `height_link` | `/rest/heights/{id}` | Height preference/value. |
| **`Wght`** | `<select>` | `1`..`36` (Weight IDs) | `weight_link` | `/rest/weights/{id}` | Weight preference/value. |
| **`Goals`** | `<input type="checkbox">` | `1`, `3`, `5`..`20` (Goals) | `goals_links` | `/rest/goals/{id}` | **Multiple**. User's dating goals. |
| **`Want`** | `<input type="checkbox">` | `1`=Hommes, `2`=Femmes, `4`=Couples, `8`=Travestis, `16`=Trans, `32`=Couples de femmes, `64`=Couples d'hommes | `sexes_interested_links` | `/rest/sexes/{id}` | **Multiple**. Genders/sexes the user is interested in ("Intéressé par"). |
| **`fant_list`** | `<input type="hidden">` / checkboxes | Comma-separated fantasy IDs | `fantasies_links` | `/rest/fantasies/{id}` | **Multiple (comma-separated)**. |
| **`OrntRev`** | `<input type="checkbox">` | `1`=Hétérosexuel(le), `2`=Bi-curieux(se), `3`=Bisexuel(le), `4`=Homosexuel(le), `7`=Pansexuel(le) | **NONE (HTML ONLY)** | N/A | **HTML-only field** ("... dont l'orientation est"). Represents target user orientations sought. NOT present in REST responses. Must be extracted from HTML form as-is. |

---

## 3. Crucial Distinctions

1. **`Ornt` vs `OrntRev`**:
   - `Ornt` (REST: `sexual_orientation_link`): **Your own** sexual orientation.
   - `OrntRev` (REST: **None**): The sexual orientation of the people you are looking for ("... dont l'orientation est").

2. **`Want` vs `OrntRev`**:
   - `Want` (REST: `sexes_interested_links`): "Intéressé par" (Genders/sexes sought, e.g., Femmes = 2, Couples = 4).
   - `OrntRev` (REST: **None**): "dont l'orientation est" (Orientations of people sought).
