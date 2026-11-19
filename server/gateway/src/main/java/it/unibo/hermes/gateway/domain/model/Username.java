package it.unibo.hermes.gateway.domain.model;

import it.unibo.hermes.gateway.domain.exception.InvalidUsernameException;

import java.util.regex.Pattern;

/**
 * Canonical username: 3 to 32 characters among lowercase letters, digits and underscore.
 *
 * @param value the canonical username
 */
public record Username(String value) {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 32;
    public static final String FORMAT_DESCRIPTION =
            "must be " + MIN_LENGTH + "-" + MAX_LENGTH + " characters among a-z, 0-9 and '_'";
    private static final Pattern CANONICAL = Pattern.compile(
            "^[a-z0-9_]{" + MIN_LENGTH + "," + MAX_LENGTH + "}$");

    public Username {
        if (!isCanonical(value)) {
            throw new InvalidUsernameException("Invalid username: " + FORMAT_DESCRIPTION);
        }
    }

    /**
     * Checks if a raw username is already in canonical form.
     *
     * @param candidate a raw username
     * @return true if the candidate is already in canonical form
     */
    public static boolean isCanonical(String candidate) {
        return candidate != null && CANONICAL.matcher(candidate).matches();
    }

    /**
     * Normalises a raw username to its canonical form and validates it.
     *
     * @param raw the raw username
     * @return the canonical username
     * @throws InvalidUsernameException if the normalised value is not canonical
     */
    public static Username normalise(String raw) {
        if (raw == null) {
            throw new InvalidUsernameException("Invalid username: " + FORMAT_DESCRIPTION);
        }
        return new Username(raw.trim().toLowerCase());
    }
}