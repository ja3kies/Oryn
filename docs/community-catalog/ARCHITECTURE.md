# Oryn — Catalogue communautaire EPUB

## Statut
Architecture cible — 2026-11-01. Aucun projet Supabase n'est encore relié au dépôt. Cette spécification ne signifie pas que le service est opérationnel.

## Objectif
Permettre à un utilisateur de partager volontairement certains fichiers EPUB avec la communauté Oryn, de rechercher les titres partagés et de recevoir des recommandations issues de ce catalogue.

## Principes non négociables
- La bibliothèque locale Room reste indépendante du compte et du réseau.
- Aucun livre n'est téléversé automatiquement : partage explicite, livre par livre, révocable.
- Seuls les fichiers EPUB sont éligibles. PDF et TXT restent strictement locaux.
- L'identité du propriétaire n'est pas affichée publiquement par défaut.
- Un livre partagé n'est visible dans les résultats qu'après validation serveur et publication.
- Le bucket Storage reste privé. Aucun lien public permanent vers un EPUB.
- Le client Android ne contient jamais de clé Supabase `secret/service_role`.
- Toute opération sensible est contrôlée côté serveur : les contrôles UI ne sont jamais une frontière de sécurité.

## Architecture
1. **Android / Kotlin / Compose**
   - Room conserve la bibliothèque personnelle.
   - Une couche `CommunityCatalogRepository` distincte expose recherche, publication, retrait, téléchargement et recommandations.
   - Supabase Auth fournit une identité facultative.
   - Le partage exige une session valide et un consentement explicite.
2. **Supabase Auth**
   - Identifiant interne : `auth.uid()`.
   - Parcours d'authentification et de récupération à définir avant l'activation publique.
   - Les liens de retour Android doivent être limités au schéma/hôte Oryn prévus.
3. **Postgres**
   - Table `shared_books` : métadonnées minimales, propriétaire, état de modération, consentement, empreinte et dates de cycle de vie.
   - États attendus : `pending`, `published`, `rejected`, `removed`.
   - Les requêtes de recherche ne retournent que les lignes `published`.
4. **Supabase Storage**
   - Bucket privé dédié aux EPUB communautaires.
   - Clé objet opaque, liée à l'identité et à l'identifiant du livre ; jamais le nom de fichier fourni par l'utilisateur.
   - Pas de lecture anonyme ni d'URL publique persistante.
5. **Validation serveur**
   - Publication après vérification de l'objet, taille, signature/structure ZIP-EPUB, métadonnées et règles de contenu.
   - Les limites de taille, quotas, débit et conservation doivent être fixées avant ouverture publique.
   - Les téléchargements passent par une autorisation vérifiée et des URL signées à durée courte, ou un mécanisme équivalent.
   - Prévoir signalement, retrait, suppression du fichier et traitement des demandes de droits d'auteur.

## Modèle de données initial
### `shared_books`
- `id` UUID généré côté serveur.
- `owner_id` UUID = `auth.uid()`.
- `title`, `author`, `language` : métadonnées normalisées et bornées.
- `storage_key` : clé opaque calculée côté serveur.
- `sha256` : empreinte complète du fichier.
- `status` : état du cycle de vie.
- `consent_at` et `rights_declaration_version` : preuve de consentement et version de déclaration.
- `created_at`, `published_at`, `removed_at`.

Les champs d'identité, e-mail, chemin local et nom de fichier original ne doivent jamais être exposés dans la recherche publique.

## Flux de partage
1. L'utilisateur choisit explicitement un EPUB local.
2. Oryn présente les métadonnées, le périmètre du partage et une déclaration confirmant que l'utilisateur dispose des droits nécessaires.
3. L'utilisateur confirme. Oryn crée une entrée `pending` puis téléverse vers le bucket privé.
4. Le serveur vérifie le fichier et applique les règles de modération.
5. Seul l'état `published` apparaît dans le catalogue.
6. Le propriétaire peut retirer son livre ; le retrait masque immédiatement la fiche et déclenche la suppression du fichier et des copies dérivées selon la politique de conservation.

## Flux de recherche et téléchargement
- Recherche authentifiée, limitée à des métadonnées publiées.
- Pagination serveur, limites de résultats et anti-abus.
- Les recommandations ne se basent que sur les livres publiés et les préférences explicitement fournies.
- Le téléchargement vérifie l'état publié, les droits d'accès, les quotas et l'existence du fichier avant de fournir un accès temporaire.
- Le téléchargement dans Oryn crée ensuite une copie locale indépendante dans Room.

## Intégration Android
- Ne pas modifier le modèle `BookEntity` pour y stocker les livres distants comme s'ils étaient locaux.
- Introduire des modèles réseau distincts (`SharedBookDto`) et mapper les données après validation.
- Garder les appels réseau hors des composables ; ViewModel + repository.
- Gérer explicitement les états hors-ligne, session expirée, quota atteint, fichier indisponible, retrait et erreur serveur.
- Le catalogue communautaire sera un espace distinct des sources externes Gutendex/OPDS.

## Compatibilité technique à résoudre avant le SDK
Le projet cible actuellement Android API 24 et Kotlin 2.0.21. La documentation Supabase Kotlin consultée indique une exigence Android API 26 (ou core library desugaring pour les versions inférieures) et impose un moteur Ktor. La version du BOM, Ktor, Kotlin Serialization et la stratégie de compatibilité devront être choisies ensemble et validées par compilation. Ne pas ajouter des versions au hasard.

## Hors périmètre de la première livraison
- Partage PDF/TXT.
- Messagerie ou profils publics.
- Recommandations basées sur un historique de lecture privé.
- Synchronisation automatique de la bibliothèque locale.
- Dépôt anonyme.
