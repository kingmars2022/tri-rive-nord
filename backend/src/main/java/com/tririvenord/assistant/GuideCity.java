package com.tririvenord.assistant;

import com.tririvenord.municipality.Municipality;

/**
 * The town whose guide an answer comes from, as the answer names it.
 *
 * <p>Answers used to say "According to the Blainville sorting guide" whatever
 * town was asked about, because the wording predates the other three: a
 * Rosemere resident got Rosemere's bin under Blainville's name. The town now
 * travels with the question, so whatever words an answer, the name and the
 * website it points to are the town's own.
 *
 * @param name    the town's name as residents read it
 * @param website the bare host of the town's page ("ville.rosemere.qc.ca"),
 *                or null when no page is on file
 */
public record GuideCity(String name, String website) {

    public static GuideCity of(Municipality municipality) {
        return new GuideCity(municipality.name(), hostOf(municipality.sourceUrl()));
    }

    /** The bare host, because a resident is being told where to look, not given a link. */
    static String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String host = java.net.URI.create(url).getHost();
            return host == null ? url : host.replaceFirst("^www\\.", "");
        } catch (RuntimeException e) {
            return url;
        }
    }
}
