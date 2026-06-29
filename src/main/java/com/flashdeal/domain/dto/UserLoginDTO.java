package com.flashdeal.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * C端用户登录请求参数
 */
@Data
public class UserLoginDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 手机号
     */
    private String phone;

}