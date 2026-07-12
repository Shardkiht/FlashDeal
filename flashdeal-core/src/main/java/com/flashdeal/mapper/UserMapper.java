package com.flashdeal.mapper;

import com.flashdeal.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    /**
     * 根据手机号查询用户
     */
    @Select("select * from user where phone = #{phone}")
    User getByPhone(String phone);

    /**
     * 插入用户数据
     */
    void insert(User user);

    /**
     * 根据id查询用户
     */
    @Select("select * from user where id = #{id}")
    User getById(Long id);
}
