package io.github.rkaushikdev.stemwright.http;

/**
 * Strict percent decoding of one RFC 3986 component into the text its utf-8 escapes spell, which
 * is what the paths and query strings this api answers were always decoded with.
 *
 * <p>It is not {@link java.net.URLDecoder}, which decodes a form body: that turns a plus sign into a
 * space and quietly passes over a broken escape. Here a plus sign is a plus sign, every escape must
 * be two hex digits, and the bytes the escapes spell must be well formed utf-8 for whole code points
 * (no overlong form, no surrogate, nothing past U+10FFFF). Anything else is refused with an {@link
 * IllegalArgumentException}, which is not a domain failure, so a request with a broken escape is
 * answered as an unexpected error, as it always was.
 */
final class UriComponent {

    private UriComponent() {}

    static String decode(String encoded) {
        if (encoded.indexOf('%') < 0) {
            return encoded;
        }
        StringBuilder out = new StringBuilder(encoded.length());
        int index = 0;
        while (index < encoded.length()) {
            char character = encoded.charAt(index);
            if (character != '%') {
                out.append(character);
                index += 1;
                continue;
            }
            int lead = escapedByte(encoded, index);
            index += 3;
            if (lead < 0x80) {
                out.append((char) lead);
                continue;
            }
            int length = sequenceLength(lead, encoded, index - 3);
            int point = lead & (0xff >> (length + 1));
            for (int follower = 1; follower < length; follower += 1) {
                int next = escapedByte(encoded, index);
                if ((next & 0xc0) != 0x80) {
                    throw malformed(encoded, index);
                }
                point = (point << 6) | (next & 0x3f);
                index += 3;
            }
            if (point < smallestFor(length)
                    || point > Character.MAX_CODE_POINT
                    || (point >= Character.MIN_SURROGATE && point <= Character.MAX_SURROGATE)) {
                throw malformed(encoded, index - 3 * length);
            }
            out.appendCodePoint(point);
        }
        return out.toString();
    }

    /** The byte an escape at the index spells, refusing anything but a percent and two hex digits. */
    private static int escapedByte(String encoded, int index) {
        if (index + 2 >= encoded.length() || encoded.charAt(index) != '%') {
            throw malformed(encoded, index);
        }
        int high = hexValue(encoded.charAt(index + 1));
        int low = hexValue(encoded.charAt(index + 2));
        if (high < 0 || low < 0) {
            throw malformed(encoded, index);
        }
        return high * 16 + low;
    }

    private static int hexValue(char digit) {
        if (digit >= '0' && digit <= '9') {
            return digit - '0';
        }
        if (digit >= 'a' && digit <= 'f') {
            return digit - 'a' + 10;
        }
        if (digit >= 'A' && digit <= 'F') {
            return digit - 'A' + 10;
        }
        return -1;
    }

    /** How many bytes a utf-8 sequence with this lead byte has, refusing a byte that cannot lead. */
    private static int sequenceLength(int lead, String encoded, int index) {
        if ((lead & 0xe0) == 0xc0) {
            return 2;
        }
        if ((lead & 0xf0) == 0xe0) {
            return 3;
        }
        if ((lead & 0xf8) == 0xf0) {
            return 4;
        }
        throw malformed(encoded, index);
    }

    /** The smallest code point a sequence of this length may spell; anything less is overlong. */
    private static int smallestFor(int length) {
        return switch (length) {
            case 2 -> 0x80;
            case 3 -> 0x800;
            default -> 0x10000;
        };
    }

    private static IllegalArgumentException malformed(String encoded, int index) {
        return new IllegalArgumentException("malformed percent encoding at index " + index + ": " + encoded);
    }
}
