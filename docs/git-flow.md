## Commit Messages Convention

Commit messages should take the following format

```
<type>: <commit msg>

```

### Available Types

- feat: -> for new features and additions
- fix: -> for a commit where you solve a bug or a problem
- build: -> for when you change a thing in the build process or scripts
- chore: -> for cleaning and styling code
- refactor: -> for refactoring changes
- docs: -> for documentation issues like readme, workflows, etc.

### Examples on commit messages

- `feat: implement multithreading for web crawling`
- `feat: add search query autocomplete feature`

- `fix: resolve race condition in multithreaded crawling`
- `fix: fix incorrect ranking of search results`

- `build: update Maven dependencies to latest versions`

- `chore: rename confusing variable names in search ranking logic`
- `chore: restructure project directories for better organization`

- `refactor: optimize database query execution for search results`
- `refactor: improve React component structure for better reusability`

## Branching Conventions

Our project follows a structured branching strategy to ensure a clean and maintainable codebase.

1. **Feature Branching:**

   - All development work is done in feature branches created from the `main` branch.
   - Each branch should be named descriptively based on the feature or fix being implemented (e.g., `feature/search-pagination` or `fix/cors-issue`).

2. **Pull Requests (PRs):**

   - Once development is complete, a pull request (PR) is opened to merge the feature branch into `main`.
   - The PR should be reviewed before merging to ensure code quality and maintainability.

3. **Merging Strategy - Squash and Merge:**

   - Instead of normal merging, we use **squash and merge**.

## Naming Conventions

### Java Naming Conventions

1. **Packages** (lowercase, separated by dots)

   - Example: `com.yourcompany.searchengine`

2. **Classes & Interfaces** (PascalCase)

   - Example (Class): `SearchService`
   - Example (Interface): `Searchable`

3. **Methods** (camelCase, verb-based)

   - Example: `fetchResults()`

4. **Variables** (camelCase)

   - Example: `searchQuery`

5. **Constants** (UPPER_CASE with underscores)

   - Example: `DEFAULT_PAGE_SIZE`
