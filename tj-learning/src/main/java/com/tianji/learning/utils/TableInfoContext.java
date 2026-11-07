package com.tianji.learning.utils;

public class TableInfoContext {
    private static final ThreadLocal<String> TABLE_NAME = new ThreadLocal<>();

    public static void setTableName(String tableName) {
        TABLE_NAME.set(tableName);
    }
    public static String getTableName() {
        return TABLE_NAME.get();
    }
    public static void removeTableName() {
        TABLE_NAME.remove();
    }
}
