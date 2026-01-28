package com.next.nexrailai.utils;

import com.next.nexrailai.common.BaseEnum;

import java.util.Arrays;

public class EnumUtil {

    /**
     * Locate an enum constant by its integer code.
     *
     * @param enumClass the enum class to search
     * @param code the integer code to match; may be {@code null}
     * @return the first enum constant whose {@code getCode()} equals {@code code}, or {@code null} if {@code code} is {@code null} or no match is found
     */
    public static <T extends Enum<T> & BaseEnum> T fromCode(Class<T> enumClass, Integer code) {
        if (code == null) return null;
        return Arrays.stream(enumClass.getEnumConstants())
                .filter(e -> e.getCode() == code)
                .findFirst()
                .orElse(null);
    }

    /**
     * Finds an enum constant whose BaseEnum.getName() equals the given name.
     *
     * @param enumClass the enum class to search
     * @param name the `getName()` value to match; may be null
     * @return the matching enum constant, or `null` if `name` is null or no constant matches
     */
    public static <T extends Enum<T> & BaseEnum> T fromName(Class<T> enumClass, String name) {
        if (name == null) return null;
        return Arrays.stream(enumClass.getEnumConstants())
                .filter(e -> e.getName().equals(name))
                .findFirst()
                .orElse(null);
    }
}