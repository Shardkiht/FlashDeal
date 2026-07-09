package com.flashdeal.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 后端统一返回结果
 * @param <T>
 */
@Data
public class Result<T> implements Serializable {

    public static final int SUCCESS_CODE = 1;
    public static final int FAIL_CODE = 0;

    private Integer code; // 编码：1成功，0和其它数字为失败
    private String msg;   // 错误信息
    private T data;       // 数据

    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.code = SUCCESS_CODE;
        return result;
    }

    public static <T> Result<T> success(T object) {
        Result<T> result = new Result<>();
        result.data = object;
        result.code = SUCCESS_CODE;
        return result;
    }

    public static <T> Result<T> error(String msg) {
        Result<T> result = new Result<>();
        result.msg = msg;
        result.code = FAIL_CODE;
        return result;
    }
}
