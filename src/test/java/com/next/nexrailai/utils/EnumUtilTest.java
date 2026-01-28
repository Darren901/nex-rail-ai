package com.next.nexrailai.utils;

import com.next.nexrailai.common.Constant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnumUtilTest {

    @Test
    void fromCode_ShouldReturnEnum_WhenCodeExists() {
        Constant.TicketType result = EnumUtil.fromCode(Constant.TicketType.class, 1);
        assertEquals(Constant.TicketType.ONE_WAY, result);
    }

    @Test
    void fromCode_ShouldReturnNull_WhenCodeDoesNotExist() {
        Constant.TicketType result = EnumUtil.fromCode(Constant.TicketType.class, 999);
        assertNull(result);
    }

    @Test
    void fromCode_ShouldReturnNull_WhenCodeIsNull() {
        Constant.TicketType result = EnumUtil.fromCode(Constant.TicketType.class, null);
        assertNull(result);
    }

    @Test
    void fromName_ShouldReturnEnum_WhenNameExists() {
        Constant.FareClass result = EnumUtil.fromName(Constant.FareClass.class, "成人");
        assertEquals(Constant.FareClass.ADULT, result);
    }

    @Test
    void fromName_ShouldReturnNull_WhenNameDoesNotExist() {
        Constant.FareClass result = EnumUtil.fromName(Constant.FareClass.class, "外星人");
        assertNull(result);
    }
}
