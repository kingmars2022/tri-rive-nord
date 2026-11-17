package com.tririvenord.municipality;

/**
 * One street, or one civic-number range of a street, and the sector it is in.
 *
 * <p>Exists for cities whose sectors a resident cannot name from memory.
 * Boisbriand sets the collection weekday street by street, so "which sector am
 * I in?" is answered by a table the city prints, not by a boulevard everyone
 * knows - and a resident who guesses gets another street's calendar.
 *
 * @param name       as the city prints it, civic range included when the
 *                   street is split ("Grande-Allee (nos 505 a 1700)")
 * @param sectorCode a {@code municipality_sector} code of the same city; the
 *                   database enforces that it exists
 */
public record MunicipalityStreet(String name, String sectorCode) {
}
