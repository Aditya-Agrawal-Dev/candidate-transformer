package com.eightfold.transformer.normalization;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

public final class PhoneNormalizer {

    private static final PhoneNumberUtil PHONE_UTIL = PhoneNumberUtil.getInstance();

    private PhoneNormalizer() {
    }

    /**
     * @param raw          the raw phone string, in any common format
     * @param defaultRegion ISO 3166-1 alpha-2 region to assume for numbers with no
     *                      country code (e.g. "US"); may be null to require an explicit "+"
     * @return E.164 formatted number, or null if it cannot be parsed as a valid number
     *         (never guesses).
     */
    public static String normalize(String raw, String defaultRegion) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String region = defaultRegion != null ? defaultRegion : "ZZ";
        try {
            PhoneNumber number = PHONE_UTIL.parse(raw, region);
            if (!PHONE_UTIL.isValidNumber(number)) {
                return null;
            }
            return PHONE_UTIL.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            return null;
        }
    }

    /** Convenience overload defaulting region detection to US when no "+" prefix is present. */
    public static String normalize(String raw) {
        return normalize(raw, "US");
    }

    public static boolean isValidE164(String value) {
        return value != null && value.matches("^\\+[1-9]\\d{6,14}$");
    }
}
