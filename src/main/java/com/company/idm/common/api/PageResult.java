package com.company.idm.common.api;

import java.util.List;

/**
 * V2 通用分页结果：items + total + pageNum + pageSize。
 */
public record PageResult<T>(List<T> items, long total, long pageNum, long pageSize) {

    public static <T> PageResult<T> of(List<T> items, long total, long pageNum, long pageSize) {
        return new PageResult<>(items, total, pageNum, pageSize);
    }

    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return new PageResult<>(List.of(), 0L, pageNum, pageSize);
    }
}
