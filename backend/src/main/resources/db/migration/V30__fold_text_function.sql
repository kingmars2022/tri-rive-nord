-- --------------------------------------------------------------------------
-- fold_text(): compare text the way residents type it.
--
-- On MySQL the column collation (utf8mb4_0900_ai_ci) ignored accents and
-- case, so "riviere" found "Rivière" without any code saying so. PostgreSQL
-- compares exactly. The unaccent extension would do it, but it needs
-- privileges a hosted free plan may not grant, so this is plain SQL: map the
-- accented Latin letters French uses to their base letter, then lowercase.
-- Uppercase accented letters are mapped explicitly rather than left to
-- lower(), which leaves them alone under the C locale.
--
-- Used by the street lookup (MunicipalityMapper.findStreets).
-- --------------------------------------------------------------------------

create function fold_text(s text) returns text
    language sql immutable parallel safe
as $$
    select lower(translate(s,
        'ÀÁÂÃÄÅàáâãäåÇçÈÉÊËèéêëÌÍÎÏìíîïÑñÒÓÔÕÖòóôõöÙÚÛÜùúûüÝýÿ',
        'AAAAAAaaaaaaCcEEEEeeeeIIIIiiiiNnOOOOOoooooUUUUuuuuYyy'))
$$;
