package com.next.nexrailai.utils;

import com.next.nexrailai.common.BaseEnum;

import java.util.Arrays;

public class EnumUtil {

    /**
     * 透過 Code 查找 Enum
     */
    public static <T extends Enum<T> & BaseEnum> T fromCode(Class<T> enumClass, Integer code) {
        if (code == null) return null;
        return Arrays.stream(enumClass.getEnumConstants())
                .filter(e -> e.getCode() == code)
                .findFirst()
                .orElse(null);
    }

    /**
     * 透過 Name 查找 Enum
     */
    public static <T extends Enum<T> & BaseEnum> T fromName(Class<T> enumClass, String name) {
        if (name == null) return null;
        return Arrays.stream(enumClass.getEnumConstants())
                .filter(e -> e.getName().equals(name))
                .findFirst()
                .orElse(null);
    }
}
