package com.next.nexrailai.common;

public interface BaseEnum {
    /**
 * Retrieve the numeric code associated with this enum value.
 *
 * @return the integer code identifying this enum value
 */
int getCode();
    /**
 * Human-readable name associated with this enum value.
 *
 * @return the name for this enum value
 */
String getName();
}