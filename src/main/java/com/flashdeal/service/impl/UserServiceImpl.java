package com.flashdeal.service.impl;

import com.flashdeal.common.constant.MessageConstant;
import com.flashdeal.domain.User;
import com.flashdeal.domain.dto.UserLoginDTO;
import com.flashdeal.common.exception.LoginFailedException;
import com.flashdeal.mapper.UserMapper;
import com.flashdeal.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户服务实现类
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public User login(UserLoginDTO userLoginDTO) {
        String phone = userLoginDTO.getPhone();
        if (phone == null || phone.isBlank()) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }

        User user = userMapper.getByPhone(phone);
        if (user == null) {
            // 自动注册
            user = User.builder()
                    .phone(phone)
                    .createTime(LocalDateTime.now())
                    .build();
            userMapper.insert(user);
            // 重新查询以获取自增主键
            user = userMapper.getByPhone(phone);
        }
        return user;
    }
}