# Oryn — Plan de sécurité du catalogue communautaire

## Gate de livraison
**Aucune activation publique du catalogue communautaire avant l'audit Cloudflare Security Audit Skill et la résolution de tous les constats bloquants.**

Skill de référence : https://github.com/cloudflare/security-audit-skill
Fichier principal : `skills/security-audit/SKILL.md`
Compagnons prioritaires : `WEB-PROTOCOL-AND-AUTH.md`, `CLOUD-AND-DEPLOYMENT.md`, `DATA-ISOLATION-AND-LIFECYCLE.md`, `RESOURCE-EXHAUSTION-AND-AVAILABILITY.md`, `SUPPLY-CHAIN-AND-RELEASE.md`, `DESKTOP-MOBILE-AND-LOCAL-IPC.md`.

## Surfaces à auditer
1. Authentification Android, deep links, PKCE, renouvellement et révocation de session.
2. Autorisation multi-utilisateur : aucune lecture, modification, retrait ou suppression inter-propriétaires.
3. RLS Postgres sur chaque table, y compris tables auxiliaires ; privilèges SQL minimaux.
4. Storage privé : chemins objet, upload, lecture, suppression, URL signées, politiques `storage.objects`.
5. Cycle de vie : consentement, publication, retrait, suppression, copies, restauration et cache.
6. Import EPUB : archive ZIP, chemins, entrées malformées, taille décompressée, compression extrême, XML/OPF, images et métadonnées non fiables.
7. Quotas et disponibilité : taille, débit, pagination, coût Storage, abus d'upload et de téléchargement.
8. Données privées : PII, e-mail, identifiants, journaux, analytics et erreurs.
9. Edge Functions / secrets / configuration : aucune clé privilégiée dans l'APK, les logs ou les artefacts.
10. Dépendances, CI, signature et chaîne de publication Android.

## Règles d'acceptation
- Toutes les tables contenant des données utilisateur ont RLS activé.
- Les politiques sont testées avec au moins deux utilisateurs fictifs et un rôle anonyme.
- Les opérations Storage sont testées avec des objets appartenant à deux utilisateurs différents.
- Aucun client ne peut s'auto-attribuer un propriétaire, un statut publié, une approbation ou un quota.
- Le statut `published` n'est attribuable que par une fonction/autorité serveur de confiance.
- Un retrait rend le livre immédiatement introuvable et non téléchargeable, même avec une ancienne URL signée après expiration.
- Les erreurs n'exposent ni clés, ni tokens, ni données d'autres comptes.
- Les fichiers EPUB sont validés côté serveur ; le MIME type déclaré par le client n'est pas une preuve.
- Les limites de taille, débit et quotas sont explicites et testées.
- Les secrets privilégiés restent côté serveur.
- Les constats sont classés avec preuves ; les hypothèses dépendant de la configuration Supabase restent `needs_validation`, pas des vulnérabilités confirmées.

## Méthode Cloudflare obligatoire
Appliquer le workflow complet en six phases après intégration :
1. Reconnaissance : architecture, acteurs, frontières de confiance, entrées, copies et sinks.
2. Chasse guidée par couverture : couverture déterministe, chasse par surface, critique des angles morts.
3. Validation indépendante de chaque candidat : un vérificateur distinct doit tenter de réfuter le constat.
4. Sorties structurées : `findings.json` validé par le schéma et les validateurs du skill.
5. Vérification indépendante des constats finaux.
6. Rapport : `REPORT.md`, `FINDINGS-DETAIL.md`, `NEEDS-VALIDATION.md`.

Ne pas simuler les capacités manquantes : le skill exige des agents indépendants et un sandbox OS contraignant (réseau externe coupé, environnement allowlisté, source en lecture seule, écritures limitées au scratch, limites de ressources). Si ces contrôles ne sont pas disponibles, aucun build/test contrôlé par la cible ne doit être exécuté ; les points concernés restent `needs_validation` avec un plan de vérification sûr.

## Interdictions
- Aucun test sur un endpoint de production, un vrai compte ou les données d'autres utilisateurs.
- Aucun secret `service_role` dans l'application Android.
- Aucun bucket public pour les EPUB.
- Aucun test destructif, scan de disponibilité ou publication réelle pendant l'audit.
- Aucun constat de vulnérabilité fondé uniquement sur une bonne pratique absente : une frontière et un impact concrets sont requis.

## Livrables obligatoires avant publication
- Schéma SQL et politiques RLS versionnés.
- Configuration Storage et Edge Functions documentée.
- Tests négatifs d'isolation entre utilisateurs.
- Résultat du workflow Cloudflare avec son commit audité, son périmètre et ses limites.
- Liste des constats corrigés et des éléments encore `needs_validation`.
