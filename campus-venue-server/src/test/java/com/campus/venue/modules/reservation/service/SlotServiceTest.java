package com.campus.venue.modules.reservation.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 槽位基础算法单元测试（不依赖数据库，可在任意环境执行）
 */
class SlotServiceTest {

    @Test
    @DisplayName("槽位号换算：slotNo=30 对应 15:00")
    void slotStartTime() {
        assertEquals(LocalTime.of(0, 0), SlotService.slotStartTime(0));
        assertEquals(LocalTime.of(15, 0), SlotService.slotStartTime(30));
        assertEquals(LocalTime.of(21, 30), SlotService.slotStartTime(43));
    }

    @Test
    @DisplayName("槽位标签格式为 HH:mm-HH:mm")
    void label() {
        assertEquals("15:00-15:30", SlotService.label(30));
        assertEquals("08:00-08:30", SlotService.label(16));
        assertEquals("23:30-24:00", SlotService.label(47));
    }

    @Test
    @DisplayName("按小时计价：40 元/小时 × 1.5 小时 = 60 元")
    void calcAmount() {
        BigDecimal amount = new SlotService(null, null)
                .calcAmount(new BigDecimal("40.00"), 30, 33);
        assertEquals(new BigDecimal("60.00"), amount);
    }

    @Test
    @DisplayName("免费场地金额为 0.00")
    void calcAmountFree() {
        BigDecimal amount = new SlotService(null, null)
                .calcAmount(BigDecimal.ZERO, 16, 20);
        assertEquals(new BigDecimal("0.00"), amount);
    }

    @Test
    @DisplayName("日期+槽位 -> 时间")
    void toDateTime() {
        assertEquals(LocalDate.of(2026, 10, 20).atTime(15, 0),
                SlotService.toDateTime(LocalDate.of(2026, 10, 20), 30));
    }

    @Test
    @DisplayName("时段校验：结束槽位必须大于开始槽位")
    void invalidRange() {
        SlotService service = new SlotService(null, null);
        assertThrows(RuntimeException.class,
                () -> service.validateAndPrice(1L, LocalDate.now(), 20, 20));
    }

    @Test
    @DisplayName("槽位换算与标签一一对应（抽样）")
    void mappingConsistency() {
        for (int slotNo : new int[]{0, 16, 30, 43, 47}) {
            String label = SlotService.label(slotNo);
            assertTrue(label.contains(":"), "标签应包含时间");
            assertEquals(SlotService.slotStartTime(slotNo),
                    LocalTime.parse(label.substring(0, 5)));
        }
    }
}
