package com.flashdeal.service;

import com.flashdeal.domain.User;
import com.flashdeal.domain.dto.UserLoginDTO;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 用户登录（手机号登录/自动注册）
     *
     * @param userLoginDTO 登录参数
     * @return 登录成功的用户信息
     */
    User login(UserLoginDTO userLoginDTO);
}
