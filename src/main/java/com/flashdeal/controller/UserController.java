package com.flashdeal.controller;

import com.flashdeal.constant.JwtClaimsConstant;
import com.flashdeal.domain.Result;
import com.flashdeal.domain.User;
import com.flashdeal.domain.dto.UserLoginDTO;
import com.flashdeal.properties.JwtProperties;
import com.flashdeal.service.UserService;
import com.flashdeal.utils.JwtUtil;
import com.flashdeal.domain.vo.UserLoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/user")
@Slf4j
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtProperties jwtProperties;

    /**
     * 用户登录
     *
     * @param userLoginDTO 登录参数
     * @return 登录结果
     */
    @PostMapping("/login")
    public Result<UserLoginVO> login(@RequestBody UserLoginDTO userLoginDTO) {
        log.info("用户登录：{}", userLoginDTO);

        User user = userService.login(userLoginDTO);

        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());

        String token = JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims
        );

        UserLoginVO userLoginVO = UserLoginVO.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .token(token)
                .build();
        return Result.success(userLoginVO);
    }
}