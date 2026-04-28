# Plan: SELECT * EXCLUDE / REPLACE for Trino

Branch: `feature/select-star-exclude-replace`
Base: `master` (origin up-to-date)
Source spec: `duckdb_star_modifiers_port.md`

## Scope

Implement DuckDB-style:
- `SELECT * EXCLUDE (col, ...) FROM t`
- `SELECT * REPLACE (expr AS col, ...) FROM t`
- Combinable: `SELECT * EXCLUDE (a) REPLACE (lower(b) AS b) FROM t`
- Qualified form: `SELECT t.* EXCLUDE (col) FROM t`

Validation rules (per DuckDB):
- Duplicate entry in EXCLUDE → ParsingException
- Duplicate alias in REPLACE → ParsingException
- Same name in both EXCLUDE and REPLACE → ParsingException
- Unmatched EXCLUDE entry → semanticException COLUMN_NOT_FOUND
- Unmatched REPLACE entry → semanticException COLUMN_NOT_FOUND
- REPLACE alias forces output name = original column name (schema preserved)

Out of scope:
- RENAME modifier (DuckDB extra; not in spec)
- EXCLUDE/REPLACE with `expr.*` row-type target (reject NOT_SUPPORTED if used)
- `RENAME` token handling beyond existing usage

## Workstream / Files

| # | File | Change | Status |
|---|------|--------|--------|
| 1 | `core/trino-grammar/src/main/antlr4/io/trino/grammar/sql/SqlBase.g4` | Add `EXCLUDE` token + nonReserved entry; extend `selectItem`; add `excludeClause`/`replaceClause`/`replaceItem` | DONE |
| 2 | `core/trino-parser/src/main/java/io/trino/sql/tree/ReplaceItem.java` | New AST node | TODO |
| 3 | `core/trino-parser/src/main/java/io/trino/sql/tree/AllColumns.java` | Add `excludeList` + `replaceList` fields, ctors, getters, equals/hashCode/getChildren/toString/shallowEquals | TODO |
| 4 | `core/trino-parser/src/main/java/io/trino/sql/tree/AstVisitor.java` | Add `visitReplaceItem` default | TODO |
| 5 | `core/trino-parser/src/main/java/io/trino/sql/parser/AstBuilder.java` | `visitSelectAll` builds excludeList + replaceList; new `visitReplaceItem`; dup/overlap validation → ParsingException | TODO |
| 6 | `core/trino-parser/src/main/java/io/trino/sql/SqlFormatter.java` | Format `EXCLUDE (...)` / `REPLACE (...)` for `*` | TODO |
| 7 | `core/trino-main/src/main/java/io/trino/sql/analyzer/StatementAnalyzer.java` | `analyzeAllColumnsFromTable` applies skip/substitution; post-validate matched sets; reject EXCLUDE/REPLACE with row-type target | TODO |
| 8 | Tests: `core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java` | Parser-level happy + error cases | TODO |
| 9 | Tests: `core/trino-main/src/test/java/io/trino/sql/analyzer/TestAnalyzer.java` | Analyzer error-path cases | TODO |
| 10 | Tests: `testing/trino-tests/src/test/java/io/trino/tests/TestSelect.java` (or similar) | E2E result-shape test | TODO |

## Approach Details

### Grammar
- New keyword `EXCLUDE` listed in nonReserved + token block, just after `EXCLUDING`.
- `REPLACE` already nonReserved. Reused without adding token.
- `selectItem` `selectAll` alternatives extended with optional `excludeClause? replaceClause?` after the asterisk; column aliases (`AS (...)`) remain trailing on qualified form.

### AST
- `AllColumns` gains:
  - `List<QualifiedName> excludeList`
  - `List<ReplaceItem> replaceList`
- New ctor takes them; deprecated ctors keep empty defaults to preserve API.
- `ReplaceItem extends Node` with `Expression expression`, `Identifier columnName`. Children = expression + columnName.

### Parser-side validation (AstBuilder.visitSelectAll)
- Check duplicate qualified names in EXCLUDE → ParsingException.
- Check duplicate alias in REPLACE (case-insensitive) → ParsingException.
- Reject overlap between EXCLUDE last-component and REPLACE alias (case-insensitive).

### Analyzer (analyzeAllColumnsFromTable)
- Build:
  - excludeKeys: case-insensitive set of QualifiedName (suffix-match logic respecting target prefix).
  - replaceMap: column-name (lower) → Expression.
- Iterate fields:
  1. If field matches excludeKey → record match, skip.
  2. Build base FieldReference / DereferenceExpression as today.
  3. If field name (lower) matches replaceMap → swap fieldExpression with REPLACE expression (analyzed in same scope).
  4. Build `Field newField` keeping original column name (REPLACE preserves schema).
- Post-loop: any unmatched EXCLUDE / REPLACE → semanticException COLUMN_NOT_FOUND.
- aliases-count validation runs against EXCLUDE-applied column count.
- For row-type `expr.*` path: if EXCLUDE/REPLACE non-empty → semanticException NOT_SUPPORTED.

### EXCLUDE name-matching semantics
DuckDB: `qualified_column_set_t` matches optional `(catalog, schema, table)` prefix. For Trino MVP:
- Single-identifier EXCLUDE `(c)` matches by column name (case-insensitive).
- Qualified EXCLUDE (e.g. `t.c`) requires field.relationAlias suffix-match: last name = column, leading parts match relation alias parts (best-effort with existing `field.getRelationAlias()`).

### Formatter
- Render after `*`:
  - ` EXCLUDE (a, b)` if non-empty
  - ` REPLACE (expr AS col, ...)` if non-empty

## Tests

Parser:
```java
assertStatement("SELECT * EXCLUDE (city) FROM addresses", ...);
assertStatement("SELECT * REPLACE (lower(city) AS city) FROM addresses", ...);
assertStatement("SELECT t.* EXCLUDE (city) FROM addresses t", ...);
assertStatement("SELECT * EXCLUDE (a) REPLACE (lower(b) AS b) FROM t", ...);

assertInvalidStatement("SELECT * EXCLUDE (a, a) FROM t", "Duplicate entry .* EXCLUDE");
assertInvalidStatement("SELECT * REPLACE (1 AS a, 2 AS a) FROM t", "Duplicate entry .* REPLACE");
assertInvalidStatement("SELECT * EXCLUDE (a) REPLACE (1 AS a) FROM t", "Column .* in both EXCLUDE and REPLACE");
```

Analyzer:
```java
analyze("SELECT * EXCLUDE (a) FROM (VALUES (1, 2)) t(a, b)");
assertFails(...).hasMessage("...EXCLUDE list not found...");
assertFails(...).hasMessage("...REPLACE list not found...");
```

E2E:
```sql
WITH t(a, b, c) AS (VALUES (1, 2, 3)) SELECT * EXCLUDE (b) FROM t      -- (a, c)
WITH t(a, b)    AS (VALUES (1, 'X')) SELECT * REPLACE (lower(b) AS b) FROM t  -- (1, 'x')
```

## Risks

- Grammar conflict: `REPLACE` overlapping with `CREATE OR REPLACE`. Mitigation: REPLACE only matched in `selectItem` after asterisk; ANTLR look-ahead handles.
- `column aliases` ambiguity with REPLACE in qualified form: ordering enforced `excludeClause? replaceClause? (AS columnAliases)?`.
- Qualified EXCLUDE matching: depends on field.relationAlias presence. Keep MVP as single-identifier match; allow qualified prefix only when relation alias is set.

## Progress Log

- [x] Branch created (`feature/select-star-exclude-replace`)
- [x] Grammar updated (EXCLUDE token, selectItem, excludeClause, replaceClause, replaceItem)
- [x] AST: ReplaceItem
- [x] AST: AllColumns extended
- [x] AstVisitor.visitReplaceItem
- [x] AstBuilder.visitSelectAll + parse-time validation (dup, overlap)
- [x] SqlFormatter (renders EXCLUDE/REPLACE)
- [x] Analyzer (skip + substitute + post-validate; row-type target rejected)
- [x] Tests
  - `TestSqlParser.testAllColumnsExcludeReplace` (parser happy + 3 invalid)
  - `TestSqlFormatter.testAllColumnsExcludeReplace` (formatter round-trip: EXCLUDE/REPLACE/combined/qualified)
  - `TestAnalyzer.testSelectAllExcludeReplace` (analyzer happy + 3 errors + qualified EXCLUDE entry + trailing alias count)
  - `TestSelectAll.testSelectAllExcludeReplace` (E2E result + schema preservation + qualified EXCLUDE + sibling-ref REPLACE + JOIN + case preservation + ORDER BY excluded col)
- [x] Build green (parser + formatter + analyzer + e2e)
