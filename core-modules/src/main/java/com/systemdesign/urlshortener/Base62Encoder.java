package com.systemdesign.urlshortener;

import lombok.extern.slf4j.Slf4j;

/**
 * Base62 encoder for generating short URLs.
 * 
 * Base62 uses: [a-zA-Z0-9] = 62 characters
 * 
 * Benefits:
 * - URL-safe (no special characters)
 * - Case-sensitive for more combinations
 * - Compact representation
 * 
 * Length examples:
 * - 6 characters: 62^6 = 56.8 billion URLs
 * - 7 characters: 62^7 = 3.5 trillion URLs
 * - 8 characters: 62^8 = 218 trillion URLs
 */
@Slf4j
public class Base62Encoder {
    
    private static final String BASE62_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = 62;
    
    /**
     * Encode a number to Base62 string.
     * 
     * @param number The number to encode (typically an auto-increment ID)
     * @return Base62 encoded string
     */
    public static String encode(long number) {
        if (number == 0) {
            return String.valueOf(BASE62_CHARS.charAt(0));
        }
        
        StringBuilder result = new StringBuilder();
        while (number > 0) {
            int remainder = (int) (number % BASE);
            result.append(BASE62_CHARS.charAt(remainder));
            number /= BASE;
        }
        
        return result.reverse().toString();
    }
    
    /**
     * Decode a Base62 string back to a number.
     * 
     * @param encoded The Base62 encoded string
     * @return The original number
     */
    public static long decode(String encoded) {
        long result = 0;
        long power = 1;
        
        for (int i = encoded.length() - 1; i >= 0; i--) {
            char c = encoded.charAt(i);
            int digit = BASE62_CHARS.indexOf(c);
            
            if (digit == -1) {
                throw new IllegalArgumentException("Invalid Base62 character: " + c);
            }
            
            result += digit * power;
            power *= BASE;
        }
        
        return result;
    }
    
    /**
     * Generate a short code of fixed length by padding.
     * 
     * @param number The number to encode
     * @param length Desired length of the short code
     * @return Padded Base62 string
     */
    public static String encodeWithLength(long number, int length) {
        String encoded = encode(number);
        
        if (encoded.length() > length) {
            throw new IllegalArgumentException(
                    "Number " + number + " requires more than " + length + " characters");
        }
        
        // Pad with leading zeros if needed
        while (encoded.length() < length) {
            encoded = BASE62_CHARS.charAt(0) + encoded;
        }
        
        return encoded;
    }
}

