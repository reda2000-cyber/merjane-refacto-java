### Consignes: 
* Ignorez les migrations BDD
* Ne pas modifier les classes qui ont un commentaire: `// WARN: Should not be changed during the exercise
`
* Pour lancer les tests (depuis le sous-répertoire `api`) :
  * unitaires: `mvnw test`
  * integration: `mvnw integration-test`
  * tous: `mvnw verify`


## Refactoring du traitement des commandes

Le refactoring a mieux organisé le traitement des commandes et regroupé les règles de disponibilité dans `ProductService`.

### Approche

J’ai suivi une démarche TDD en plusieurs cycles :

1. J’ai écrit des tests pour préciser les règles de disponibilité : décrément du stock, limites de saison, expiration et notifications. Exécutés sur le code initial, ils ont révélé des écarts de comportement (phase rouge).
2. J’ai implémenté ces règles dans `ProductService` et injecté une horloge afin de tester les dates de manière déterministe. Les tests sont ensuite passés (phase verte).
3. J’ai couvert le traitement HTTP par des tests d’intégration, notamment la réponse 404 pour une commande introuvable, puis séparé le contrôleur de l’orchestration transactionnelle.
4. J’ai ajouté un test de régression pour le contrat de `notifyDelay` : le délai doit être enregistré et le produit sauvegardé avant l’envoi de la notification.

### Historique des commits

- **`test: cover product availability lifecycle rules`**
  Ajoute des tests sur la gestion du stock, les saisons, les dates d’expiration et les notifications.

- **`feat: enforce seasonal and expiry availability rules`**
  Implémente ces règles et ajoute une horloge injectable pour tester les dates de façon fiable.

- **`test: cover order processing and missing order behavior`**
  Ajoute des tests d’intégration pour le traitement des commandes et le cas où une commande est introuvable (404).

- **`refactor: separate order processing from the HTTP controller`**
  Déplace le traitement transactionnel dans un service et simplifie le contrôleur.

- **`refactor: align repository id types with entities`**
  Harmonise les types d’identifiants des dépôts avec ceux des entités.

- **`test: preserve delay notification persistence contract`**
  Ajoute un test de régression vérifiant que le délai est enregistré avant l’envoi de la notification.

### Vérification

Le build `verify` a réussi : **14 tests unitaires**, **10 tests d’intégration**, ainsi que le contrôle JaCoCo.
