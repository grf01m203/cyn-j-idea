package com.campus.venue.modules.stat.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 统计专用查询（聚合交给数据库，避免把数据拉到内存）
 */
@Mapper
public interface StatMapper {

    /** 时段热力图：按 星期 × 槽位 统计占用数 */
    @Select("""
            SELECT (WEEKDAY(slot_date) + 1) AS weekday, slot_no AS slotNo, COUNT(*) AS cnt
            FROM reservation_slot
            WHERE slot_date BETWEEN #{start} AND #{end}
            GROUP BY weekday, slotNo
            """)
    List<Map<String, Object>> heatmap(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** 预约趋势 */
    @Select("""
            SELECT resv_date AS date,
                   COUNT(*) AS resvCount,
                   SUM(CASE WHEN status IN (5, 7) THEN 1 ELSE 0 END) AS cancelCount,
                   SUM(CASE WHEN status IN (2, 3) THEN 1 ELSE 0 END) AS signCount
            FROM reservation
            WHERE deleted = 0 AND resv_date BETWEEN #{start} AND #{end}
            GROUP BY resv_date
            ORDER BY resv_date
            """)
    List<Map<String, Object>> trend(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** 指定日期区间的场地可预约槽位总数（用于热力图分母） */
    @Select("""
            SELECT (WEEKDAY(slot_date) + 1) AS weekday, slot_date AS slotDate, COUNT(*) AS cnt
            FROM reservation_slot
            WHERE slot_date BETWEEN #{start} AND #{end}
            GROUP BY weekday, slotDate
            """)
    List<Map<String, Object>> slotDateCount(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
