# JALF REST API: Metadata

This document consolidates all static collections and metadata definitions used by the JALF application.

## 1. Alcohol consumption preferences
**Endpoint**: `GET /rest/alcohol-uses`

Returns a list of all available alcohol consumption preferences.

```json
{
    "alcohol_uses": [ // Array of alcohol consumption preferences
        {
            "alcohol_use_link": "/rest/alcohol-uses/ALCOHOLID", // Link to specific preference
            "description": "Aucune importance" // Localized label
        }
    ]
}
```

---

**Endpoint**: `GET /rest/alcohol-uses/{id}`

Returns a specific alcohol consumption preference.

```json
{
    "description": "string",
    "alcohol_use_link": "/rest/alcohol-uses/ALCOHOLUSEID"
}
```

---

## 2. Drug use preferences
**Endpoint**: `GET /rest/drug-uses`

Returns all drug usage preferences.

```json
{
    "drug_uses": [ // Array of drug use preferences/status
        {
            "drug_use_link": "/rest/drug-uses/DRUGID", // Link to specific preference
            "description": "Aucune importance" // Localized label
        }
    ]
}
```

---

**Endpoint**: `GET /rest/drug-uses/{id}`

Returns a specific drug use preference.

```json
{
    "drug_uses": [
        {
            "description": "string",
            "drug_use_link": "/rest/drug-uses/DRUGUSEID"
        }
    ]
}
```

---

## 3. Ethnic groups
**Endpoint**: `GET /rest/ethnic-groups`

Returns all ethnic group categories.

```json
{
    "ethnic_groups": [ // Array of ethnic group categories
        {
            "ethnic_group_link": "/rest/ethnic-groups/ETHNICID", // Link to specific ethnicity
            "description": "Aucune importance" // Localized label
        }
    ]
}
```

---

**Endpoint**: `GET /rest/ethnic-groups/{id}`

Returns a specific ethnic group.

```json
{
    "ethnic_group_link": "/rest/ethnic-groups/ETHNICID",
    "description": "string"
}
```

---

## 4. Fantasies
**Endpoint**: `GET /rest/fantasies`

Returns all available fantasies categorized by rank.

```json
{
    "name": null,
    "total_items_count": 152,
    "items": [ // Primary list used for search and list selection
        {
            "rank": 0, // Order within the search list
            "fantasy_link": "/rest/fantasies/FANTASYID", // Link to specific resource
            "description": "string" // Localized label
        }
    ],
    "fantasies": [ // Alpha-sorted list of all possible fantasies
        {
            "fantasy_link": "/rest/fantasies/FANTASYID", // Link to specific resource
            "description": "string" // Localized label
        }
    ]
}
```

---

**Endpoint**: `GET /rest/fantasies/{id}`

Returns a specific fantasy definition.

```json
{
    "fantasy_link": "/rest/fantasies/FANTASYID",
    "description": "string" // fantasy description
}
```

---

## 5. Relationship goals
**Endpoint**: `GET /rest/goals`

Returns a list of relationship goals/motives.

```json
{
    "goals": [ // Array of relationship goals/motives
        {
            "goal_link": "/rest/goals/GOALID", // Link to specific goal
            "description": "Aucune importance" // Localized label (e.g. Friendship, Romance)
        }
    ]
}
```

---

**Endpoint**: `GET /rest/goals/{id}`

Returns a specific relationship goal.

```json
{
    "goal_link": "/rest/goals/GOALID",
    "description": "string" // goal description
}
```

---

## 6. Heights
**Endpoint**: `GET /rest/heights`

Returns a list of all available height increments in both metric and imperial units.

```json
{
    "heights": [ // Array of height options
        {
            "height_link": "/rest/heights/HEIGHTID", // Link to specific height increment
            "metric": "1,22 m", // Metric label
            "imperial": "4'0\"" // Imperial label
        }
    ]
}
```

---

**Endpoint**: `GET /rest/heights/{id}`

Returns a specific height increment.

```json
{
    "imperial": "string", // in feet ex 4'0\"
    "metric": "string", // in meters ex 1,22m
    "height_link": "/rest/heights/HEIGHTSID"
}
```

---

## 7. Occupations
**Endpoint**: `GET /rest/occupations`

Returns all occupation/employment categories.

```json
{
    "occupations": [ // Array of occupation/employment categories
        {
            "occupation_link": "/rest/occupations/OCCUPATIONID", // Link to specific occupation
            "description": "Employé/employée" // Localized label
        }
    ]
}
```

---

**Endpoint**: `GET /rest/occupations/{id}`

Returns a specific occupation category.

```json
{
    "description": "string",
    "occupation_link": "/rest/occupations/OCCUPATIONID"
}
```

---

## 8. Privileges
**Endpoint**: `GET /rest/privileges`

Returns all membership privilege/rank levels (e.g. User, Premium, VIP, Gold).

```json
{
    "privileges": [ // Array of membership privilege/rank levels
        {
            "privilege_link": "/rest/privileges/0", // Link to specific privilege
            "description": "Utilisateur" // Localized label (e.g. User, Premium)
        }
    ]
}
```

---

**Endpoint**: `GET /rest/privileges/{id}`

Returns detailed metadata for a specific privilege level, including UI icons.

```json
{
    "privilege_link": "/rest/privileges/PRIVILEGEID",
    "description": "string", // privilege description
    "icon": {
        "jalf_22x19_icon_uriref": "/jalf/images/icons/ic_PRIVILEGEDESCRIPTION.png",
        "jalf_20x20_icon_uriref": "/jalf/images/icons/PRIVILEGEDESCRIPTION_20.png"
    }
}
```

---

## 9. Regions & Geography
**Endpoint**: `GET /rest/regions`

Returns the top-level list of regions (usually countries).

```json
{
    "sub_regions": { // Geographical sub-region container
        "description": { // Localized metadata for the sub-region type
            "singular": "pays", // "country"
            "plural": "pays" // "countries"
        },
        "list": [ // Sorted list of regions/countries
            {
                "region_link": "/rest/regions/REGIONID/SUBREGIONID/LOCALID", // Link to specific region resource
                "name": "Canada" // Localized region name
            }
        ]
    }
}
```

---

**Endpoint**: `GET /rest/regions/{id}`

Returns details for a specific region and lists its sub-regions (e.g. Provinces/States).

```json
{
    "icon": {
        "jalf_icon_uriref": "/images/Drapeaux/Pays/REGIONID.jpg"
    },
    "name": "string",
    "description": {
        "plural": "string", // country name plural
        "singular": "string" // country name singular
    },
    "sub_regions": {
        "description": {
            "plural": "string", // subregion name plural
            "singular": "string" // subregion name singular
        },
        "list": [
            {
                "name": "string", // subregion name
                "region_link": "/rest/regions/REGIONID"
            }
        ]
    }
}
```

---

**Endpoint**: `GET /rest/regions/{id}/{sub_id}`

Returns details for a sub-region and lists its local areas (e.g. Cities).

```json
{
    "icon": {
        "jalf_icon_uriref": "/images/Drapeaux/Pays/REGION+SUBREGIONID.jpg" // ex: /images/Drapeaux/Pays/109.jpg (100+9)
    },
    "name": "string",
    "description": {
        "plural": "string", // subregion name plural
        "singular": "string" // subregion name singular
    },
    "sub_regions": {
        "description": {
            "singular": "string", // sub-subregion name singular
            "plural": "string" // sub-subregion name plural
        },
        "list": [
            {
                "region_link": "/rest/regions/REGIONID/SUBREGIONID/SUBSUBREGIONID",
                "name": "string" // sub-subregion name
            }
        ]
    }
}
```

---

## 10. Schedules & Availability
**Endpoint**: `GET /rest/schedules-available`

Returns a list of all possible availability schedules.

```json
{
    "schedules_available": [ // Array of availability options
        {
            "schedule_available_link": "/rest/schedules-available/SCHEDULEID", // Link to specific availability
            "description": "Aucune importance" // Localized label (e.g. Evenings, Weekends)
        }
    ]
}
```

---

**Endpoint**: `GET /rest/schedules-available/{id}`

Returns a specific availability option.

```json
{
    "schedule_available_link": "/rest/schedules-available/SCHEDID",
    "description": "string"
}
```

---

## 11. Sexes & Genders
**Endpoint**: `GET /rest/sexes`

Returns all possible gender/sex categories, including icon resources for the UI.

```json
{
    "name": "Sexes", // Display name for the category
    "items": [ // Primary list used for search and basic selection
        {
            "rank": 0, // Order within the simple list
            "description": "Homme", // Localized label
            "sex_link": "/rest/sexes/SEXID" // Link to the specific resource
        }
    ],
    "sexes": [ // Detailed profile-centric list with icon resources
        {
            "sex_link": "/rest/sexes/SEXID", // Link to the specific resource
            "description": "Homme", // Localized label
            "presentation_order": 1, // Sort order for profile display
            "with_partner": false, // Indicates if this category involves a partner (e.g. Couples)
            "icon": { // Nested icon resources for various UI contexts
                "jalf_144x189_icon_uriref": "/jalf/images/icons/avatarH.png", // Large avatar icon URI
                "jalf_144x189_icon_link": "/jalf/images/icons/avatarH.png", // Direct link to large icon
                "jalf_20x20_icon_uriref": "/jalf/images/icons/ic_sex_1_20.png", // Small toolbar icon URI
                "jalf_20x20_icon_link": "/jalf/images/icons/ic_sex_1_20.png" // Direct link to small icon
            }
        }
    ],
    "total_items_count": 7 // Count of primary items
}
```

---

**Endpoint**: `GET /rest/sexes/{id}`

Returns detailed metadata for a specific sex category.

```json
{
    "icon": {
        "jalf_20x20_icon_uriref": "relativeprofileimagespng",
        "jalf_20x20_icon_link": "relativeiconimagespng",
        "jalf_144x189_icon_uriref": "relativeprofileimagespng",
        "jalf_144x189_icon_link": "relativeiconimagespng"
    },
    "description": "string", // name of sex (men , woman etc)
    "presentation_order": 0, // Sex ID
    "sex_link": "/rest/sexes/SEXID",
    "with_partner": true //  false for without partner
}
```

---

## 12. Sexual Orientations
**Endpoint**: `GET /rest/sexual-orientations`

Returns all available sexual orientation options.

```json
{
    "sexual_orientations": [ // Array of sexual orientation options
        {
            "description": "string", // Localized label for the orientation (e.g. "Heterosexual", "Bisexual")
            "sexual_orientation_link": "/rest/sexual-orientations/ORIENTATIONID" // REST link to the specific orientation
        }
    ]
}
```

---

**Endpoint**: `GET /rest/sexual-orientations/{id}`

Returns a specific sexual orientation definition.

```json
{
    "description": "string",
    "sexual_orientation_link": "/rest/sexual-orientations/ORIENTATIONID"
}
```

---

## 13. Smoking habits
**Endpoint**: `GET /rest/smoking`

Returns all smoking preference options.

```json
{
    "smoking": [ // Array of smoking preference options
        {
            "smoking_link": "/rest/smoking/SMOKINGID", // Link to specific smoking preference
            "description": "Aucune importance" // Localized label
        }
    ]
}
```

---

**Endpoint**: `GET /rest/smoking/{id}`

Returns a specific smoking preference.

```json
{
    "smoking_link": "/rest/smoking/SMOKINGID",
    "description": "string"
}
```

---

## 14. Social Statuses
**Endpoint**: `GET /rest/social-statuses`

Returns a list of relationship/social statuses.

```json
{
    "social_statuses": [ // List of relationship statuses
        {
            "social_status_link": "/rest/social-statuses/STATUSID", // Link to specific status
            "description": "Célibataire" // Localized label (e.g. Single, Married)
        }
    ]
}
```

---

**Endpoint**: `GET /rest/social-statuses/{id}`

Returns a specific social status definition.

```json
{
    "social_status_link": "/rest/social-statuses/SOCIALSTATUSID",
    "description": "string"
}
```

---

## 15. Weights
**Endpoint**: `GET /rest/weights`

Returns a list of all available weight increments in both metric and imperial units.

```json
{
    "weights": [ // Array of weight options
        {
            "weight_link": "/rest/weights/WEIGHTID", // Link to specific weight increment
            "metric": "40 kg", // Metric label
            "imperial": "90 lbs" // Imperial label
        }
    ]
}
```

---

**Endpoint**: `GET /rest/weights/{id}`

Returns a specific weight increment.

```json
{
    "metric": "strings", // in kilograms ex 40 kg
    "imperial": "string", // in pounds ex 90 lbs
    "weight_link": "/rest/weights/WEIGHTID"
}
```

---

## 16. Zodiac Signs
**Endpoint**: `GET /rest/zodiac-signs`

Returns a list of all astrological zodiac signs and their corresponding icons.

```json
{
    "zodiac_signs": [ // Array of astrological zodiac signs
        {
            "zodiac_sign_link": "/rest/zodiac-signs/ZODIACID", // Link to specific sign resource
            "description": "Bélier 21 Mars au 20 Avril", // Localized label with date range
            "icon": { // Nested icon resource
                "jalf_24x20_icon_uriref": "/jalf/images/icons/astro_1.png" // Small monochrome icon URI
            }
        }
    ]
}
```

---

**Endpoint**: `GET /rest/zodiac-signs/{id}`

Returns detailed metadata for a specific zodiac sign.

```json
{
    "description": "string", // zodiac sign description ex: Bélier 21 Mars au 20 Avril
    "icon": {
        "jalf_24x20_icon_uriref": "picturerelativeurl" // ex: /jalf/images/icons/astro_1.png
    },
    "zodiac_sign_link": "/rest/zodiac-signs/ZODIACID"
}
```
