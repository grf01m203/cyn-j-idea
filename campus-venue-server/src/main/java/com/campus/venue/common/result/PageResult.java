package com.campus.venue.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;
import java.util.function.Function;

/**
 * 统一分页结构
 */
@Data
public class PageResult<T> {

    private List<T> records;
    private long total;
    private long pageNum;
    private long pageSize;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> r = new PageResult<>();
        r.records = page.getRecords();
        r.total = page.getTotal();
        r.pageNum = page.getCurrent();
        r.pageSize = page.getSize();
        return r;
    }

    public static <E, T> PageResult<T> of(IPage<E> page, Function<E, T> mapper) {
        PageResult<T> r = new PageResult<>();
        r.records = page.getRecords().stream().map(mapper).toList();
        r.total = page.getTotal();
        r.pageNum = page.getCurrent();
        r.pageSize = page.getSize();
        return r;
    }

    public static <T> PageResult<T> of(List<T> records, long total, long pageNum, long pageSize) {
        PageResult<T> r = new PageResult<>();
        r.records = records;
        r.total = total;
        r.pageNum = pageNum;
        r.pageSize = pageSize;
        return r;
    }
}
