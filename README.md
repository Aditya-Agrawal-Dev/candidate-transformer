# Multi-Source Candidate Data Transformer

Eightfold Engineering Internship Assignment (Jul-Dec 2026) — a transformation
engine that ingests messy, multi-source candidate data and produces a single
canonical candidate profile: merged, normalized, confidence-scored,
provenance-tracked, and re-shapeable at runtime without code changes.

## Problem

Eightfold ingests candidate information from many places at once, and
downstream products need one clean, canonical profile per candidate, with
normalized formats, deduplication across sources, and a record of where each
value came from and how confident we are in it. This system is that
transformer: structured + unstructured sources in, one trustworthy
`CandidateProfile` out.

## Sources implemented

| Group | Source | Status     |
|---|---|------------|
| Structured | Recruiter CSV (`name,email,phone,current_company,title,...`) |implemented 
| Structured | ATS JSON (own field names, mapped explicitly) |implemented 
| Unstructured | Resume (PDF via Apache PDFBox, DOCX via Apache POI, or plain `.txt`) | implemented 
| Unstructured | Recruiter notes (`.txt`, free text) | implemented 
| Unstructured | GitHub profile URL | Planned Extension 
| Unstructured | LinkedIn profile URL | Planned Extension 

This satisfies the requirement of at least one structured **and** one
unstructured source — with two of each implemented for a stronger merge demo.

## Architecture

Clean Architecture, organized by responsibility, not by source:

```
com.eightfold.transformer
├── model/           canonical domain model (immutable records): CandidateProfile, Skill,
│                    Experience, Education, Location, Links, Provenance, SourceType, ExtractionMethod
├── source/          pre-normalization intermediate representation (RawCandidateData, RawValue<T>)
├── parser/          Strategy + Factory pattern: one SourceParser per source type
│                    (RecruiterCsvParser, AtsJsonParser, ResumeParser, RecruiterNotesParser,
│                    ParserFactory, FreeTextHeuristics for shared regex extraction)
├── normalization/   pure, stateless normalizers: EmailNormalizer, PhoneNormalizer (E.164 via
│                    libphonenumber), DateNormalizer (YYYY-MM), CountryNormalizer (ISO-3166
│                    alpha-2), SkillNormalizer (alias table), UrlNormalizer, NameNormalizer
├── merge/           deterministic conflict resolution: Candidate<T>, MergeResult<T>,
│                    ScalarFieldMerger (consensus -> reliability -> determinism), MergeEngine
│                    (orchestrates per-field merging + list union/dedup for the whole profile)
├── confidence/       ConfidenceEngine: field confidence, skill confidence, overall confidence
├── provenance/       ProvenanceTracker: accumulates field -> source -> method records
├── config/           FieldSpec / OutputConfig: deserialized runtime projection config
├── projection/       ProjectionEngine + CanonicalPathResolver + NormalizationReapplier:
│                     canonical model -> runtime-configurable output, fully decoupled
├── validation/       ProfileValidator + ValidationError: schema/shape sanity checks
├── application/      TransformerPipeline: orchestrates parse -> merge -> validate -> project
├── cli/              TransformerCli: the `java -jar transformer.jar ...` entry point
└── util/             exception hierarchy (TransformerException and subclasses)
```

### Pipeline

```
detect & parse (per source, isolated failure)
        │
        ▼
normalize (inside merge: email/phone/date/country/skill/url/name normalizers)
        │
        ▼
merge (deterministic conflict resolution: consensus → source reliability → determinism)
        │
        ▼
confidence (per-field + per-skill + overall, weighted)
        │   (provenance recorded alongside every merge decision)
        ▼
validate (schema sanity: malformed values, range checks, missing required fields)
        │
        ▼
project-to-output (default canonical schema, or a runtime JSON config — no code changes)
```

A single bad/missing source is isolated at the parser layer and never reaches
the merge engine; the pipeline always returns a (possibly sparse) profile
rather than crashing.

## Normalization rules

| Field | Rule |
|---|---|
| Emails | lowercase, trimmed, deduplicated, structurally validated |
| Phones | E.164 via Google's `libphonenumber` (default region `US` when no `+` prefix; rejects unparsable numbers rather than guessing) |
| Dates | `YYYY-MM`; recognizes `YYYY`, `MM/YYYY`, `YYYY-MM-DD`, `Mon YYYY`, `Month YYYY`; `present`/`current`/`now`/`ongoing` → literal `"present"` |
| Country | ISO 3166-1 alpha-2, via an alias table plus a full JVM-locale fallback (`"USA"`, `"United States"` → `US`) |
| Skills | canonical mapping table (`skill-aliases.json`) - e.g. `js → JavaScript`, `springboot → Spring Boot`, `postgres → PostgreSQL`, `c plus plus → C++`; unknown skills are whitespace-normalized and title-cased rather than dropped |
| URLs | scheme/host normalized; LinkedIn/GitHub specifically canonicalized to `https://www.linkedin.com/in/<handle>` / `https://github.com/<handle>` |
| Names | collapse whitespace; fix ALL-CAPS/all-lowercase casing while leaving genuinely mixed-case names (e.g. `McDonald`, `O'Brien`) untouched |

## Merge / conflict-resolution policy

Every scalar field (name, headline, location parts, links, years of
experience) is resolved deterministically by `ScalarFieldMerger`:

1. **Consensus** — the value with the most *distinct sources* independently
   agreeing wins. Two sources agreeing beats one source's lone claim,
   regardless of which source it is.
2. **Source reliability** — ties in consensus are broken by the most-trusted
   source's priority: `Recruiter CSV (1) > ATS JSON (2) > Resume (3) >
   GitHub (4) > LinkedIn (5) > Recruiter Notes (6)`.
3. **Determinism** — any remaining tie is broken by the lexicographically
   smallest source identifier, so the same inputs always produce the same
   output.

List fields (emails, phones, skills, other links) are **unions**: normalized,
deduplicated, with every contributing source tracked for confidence purposes.
Experience/education entries are deduplicated by `(company,title)` /
`(institution,degree)`, keeping the most *complete* entry per group
(tie-broken by source priority), then sorted most-recent-first.

## Confidence scoring

- Each source has a base trust level (`RECRUITER_CSV=0.95` down to
  `RECRUITER_NOTES=0.45`).
- Field confidence = base confidence of the winning source + a bonus per
  additional independently-corroborating source, capped at `0.99`.
- A value seen **only** in a resume is capped at a medium ceiling (`0.70`),
  since resumes are self-reported prose, not verified data.
- Skill confidence scales with the number of distinct corroborating sources,
  with an extra boost if any corroborating source is structured (CSV/ATS).
- `overall_confidence` is a weighted average across whichever fields are
  actually present (identity + skills weighted highest); a profile missing a
  low-weight field like education is not unfairly punished for it.

## Provenance

Every merge decision is recorded as `{ field, source, method }` — e.g.

```json
{ "field": "skills[\"PostgreSQL\"]", "source": "resume.pdf, ats.json", "method": "canonicalized via skill-aliases mapping; confidence from 2 corroborating source(s)" }
```

## Runtime-configurable output

No config → the full canonical schema (every field, confidence + provenance
included). A config JSON reshapes the output with **no code changes**:

```json
{
  "fields": [
    { "path": "full_name", "type": "string", "required": true },
    { "path": "primary_email", "from": "emails[0]", "type": "string", "required": true },
    { "path": "phone", "from": "phones[0]", "type": "string", "normalize": "E164" },
    { "path": "skills", "from": "skills[].name", "type": "string[]", "normalize": "canonical" }
  ],
  "include_confidence": true,
  "include_provenance": false,
  "on_missing": "null"
}
```

- `path` is the **output** key (dot-notation nests, e.g. `"contact.linkedin"`).
- `from` is the **canonical** source path (defaults to `path`); supports
  scalar paths (`location.city`), indexed list access (`emails[0]`), and
  wildcard sub-field projection (`skills[].name`, `experience[].company`).
- `normalize` re-applies a normalizer at projection time (`E164`,
  `canonical`, `ISO3166`, `YYYY-MM`, `LOWERCASE_TRIM`).
- `on_missing` is `null` (emit `null`), `omit` (drop the key), or `error`
  (throw, listing every missing required field) — evaluated per-field but
  only `required` fields can trigger `error`.

The projection layer (`CanonicalPathResolver` + `ProjectionEngine`) only
knows the canonical model through string paths, so it never has to change
when a config changes — only the JSON does.

## Validation

`ProfileValidator` runs after merge and before projection, checking: missing
`candidate_id`, invalid emails/phones after normalization, non-ISO country
codes, malformed dates, implausible education years, out-of-range
confidence scores. Findings are split into `ERROR` (would block output under
`on_missing=error`) and `WARNING` (logged, never blocking). A missing or
garbage *source* never blocks the run — it's filtered out before merge, with
the skip reason logged and surfaced via `--out`/stderr.

## Edge cases handled

- Missing source file → logged, skipped, run continues.
- Malformed CSV / invalid JSON syntax → parser marks itself malformed, source excluded.
- Empty file → treated the same as malformed/missing.
- Conflicting values across sources → resolved deterministically (see merge policy above).
- Same email in different casing across sources → normalized and deduplicated to one.
- Resume with no extractable text (e.g. scanned image PDF) → marked malformed, skipped, never crashes.
- Required field missing under a strict (`on_missing=error`) config → pipeline throws a clear, listed error instead of emitting bad data.
- Unknown skill tokens → preserved (title-cased), never silently dropped.

## Descoped (explicitly, under time pressure)

- **GitHub / LinkedIn live profile fetching** — both require either a network
  call to a third-party API (GitHub REST/GraphQL) or scraping a page with no
  public API (LinkedIn). The merge engine, confidence engine, and
  `SourceType` enum already model these as first-class sources (priorities 4
  and 5) so adding `GitHubProfileParser`/`LinkedInProfileParser` later is a
  pure addition, not a redesign — they'd just implement `SourceParser` and
  call an HTTP client.
- **Full NLP/NER resume parsing** — `ResumeParser`/`FreeTextHeuristics` use
  regex + keyword-section heuristics, not a trained NER model. This is an
  explicit, documented tradeoff: good enough for clean/typical resumes,
  weaker on heavily stylized layouts (multi-column PDFs, tables-as-resumes).
- **A UI** — the assignment explicitly says a CLI is sufficient and lower
  priority than the engine; no UI was built.

## How to run

Requires Java 21 and Maven (with internet access to resolve dependencies —
Jackson, PDFBox, POI, libphonenumber, JUnit 5 — on first build).

```bash
mvn clean install
```

This compiles, runs all tests (`mvn test`), and produces a runnable fat jar
at `target/transformer.jar`.

### Default schema, all four sample sources

```bash
java -jar target/transformer.jar \
  --csv sample-data/recruiter.csv \
  --ats-json sample-data/ats.json \
  --resume sample-data/resume.pdf \
  --notes sample-data/recruiter_notes.txt \
  --out output.json
```

### Custom output config

```bash
java -jar target/transformer.jar \
  --csv sample-data/recruiter.csv \
  --ats-json sample-data/ats.json \
  --resume sample-data/resume.pdf \
  --config sample-data/custom-config.json \
  --out output-custom.json
```

### Strict config that errors on a missing required field

```bash
java -jar target/transformer.jar \
  --notes sample-data/empty_notes.txt \
  --config sample-data/custom-config-strict.json
# exits with code 2 and prints the missing-field validation errors to stderr
```

### All CLI flags

```
--csv <path>           Recruiter CSV export (structured)
--ats-json <path>      ATS JSON blob (structured)
--resume <path>        Resume file: .pdf, .docx, or .txt (unstructured, repeatable)
--notes <path>         Recruiter notes .txt (unstructured, repeatable)
--config <path>        Runtime output-projection config JSON (optional)
--out <path>           Write the output JSON to a file (optional; always printed to stdout too)
--candidate-id <id>    Override the generated candidate_id
--help, -h             Show usage
```

## Sample data

All under `sample-data/`:

- `recruiter.csv` — two rows for the same candidate (a recruiter editing a
  spreadsheet over time), exercising last-write-wins-within-source merging.
- `ats.json` — same candidate, different field names, slightly different
  job title (`Staff` vs an earlier `Senior`), exercising cross-source
  conflict resolution.
- `resume.pdf` / `resume.docx` / `resume.txt` — the same resume content in
  three formats, demonstrating PDFBox/POI/plain-text extraction all feed the
  same heuristics.
- `recruiter_notes.txt` — free-text notes mentioning skills and location.
- `malformed_recruiter.csv`, `malformed_ats.json`, `empty_notes.txt` —
  deliberately broken/empty inputs for the "never crash" requirement.
- `custom-config.json` — the example projection config from the spec.
- `custom-config-strict.json` — a config that exercises `on_missing=error`.

## Testing

```bash
mvn test
```

- `normalization/*Test` — unit tests per normalizer (valid/invalid/edge inputs).
- `merge/ScalarFieldMergerTest` — consensus vs. reliability vs. determinism resolution rules.
- `merge/MergeEngineTest` — full-profile merging, conflicting emails, malformed-source exclusion, skill confidence ordering.
- `confidence/ConfidenceEngineTest` — base/corroboration/cap behavior.
- `validation/ProfileValidatorTest` — blocking vs. warning-level findings.
- `projection/ProjectionEngineTest` — default schema shape, field selection/renaming, `on_missing` policies, nested/dotted output, wildcard sub-field projection.
- `integration/EndToEndPipelineTest` — runs the full pipeline against the real sample data: all sources together, custom config, DOCX resume, malformed CSV, malformed JSON, missing file, empty notes, zero sources, invalid config path, and a strict-config error case.


## Assumptions

- One CSV/JSON/resume/notes input set represents **one candidate** (this
  assignment's scope is single-candidate transformation, not batch
  candidate matching/clustering across files).
- "Last write wins within a single structured source" (e.g. two CSV rows for
  the same person) models a recruiter correcting a row, not two different
  candidates.
- A default US region is assumed for phone numbers with no explicit country
  code, since that's the common case for a US-headquartered ATS; this is
  configurable in `PhoneNormalizer.normalize(raw, region)`.
- `years_experience` candidates are grouped to the nearest 0.5 years for
  consensus purposes (e.g. `8.0` and `7.8` are treated as agreeing), since
  exact-decimal agreement across independently-reported sources is unrealistic.

## Future improvements

- Live GitHub/LinkedIn source parsers (HTTP client + API/scraping adapter
  implementing `SourceParser`, no changes needed elsewhere).
- A real NER model for resume parsing instead of regex/keyword heuristics.
- Batch mode: ingest a directory of many candidates' source files and emit
  one JSON-lines file instead of a single profile per CLI invocation.
- A small embedded web UI (or a thin `/transform` HTTP endpoint via Spring
  Boot) on top of the same `TransformerPipeline`, for the "minimal UI"
  option the assignment mentions as a lower-priority alternative to the CLI.
