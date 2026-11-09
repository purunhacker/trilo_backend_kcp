-- Translations of a term version.
--
-- A term version is the unit users consent to: user_term_consents references term_version_id, and
-- activating a new version invalidates prior consent. Storing each language as its own version row
-- would therefore split one agreement into several, scattering consent records and forcing a
-- re-consent whenever a translation is added. A translation is the same agreement written in
-- another language, so it hangs off the version instead.
--
-- The Korean text stays on term_versions.title/content as the original: clients that do not ask
-- for a language keep receiving exactly what they receive today, and any language without a
-- translation falls back to it.
CREATE TABLE auth.term_version_translations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    term_version_id UUID NOT NULL REFERENCES auth.term_versions(id) ON DELETE CASCADE,
    -- BCP-47 tag, normalised to lower case ("en", "ja", "zh-cn").
    language        VARCHAR(16) NOT NULL,
    title           TEXT NOT NULL,
    content         TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_term_version_translations UNIQUE (term_version_id, language)
);

CREATE INDEX idx_term_version_translations_version
    ON auth.term_version_translations(term_version_id);
