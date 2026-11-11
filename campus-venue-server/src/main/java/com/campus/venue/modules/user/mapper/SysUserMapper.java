package com.campus.venue.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.venue.modules.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 逻辑删除并释放唯一索引占位（username / student_no 追加后缀）。
     * 项目开启了 MyBatis-Plus 逻辑删除（logic-delete-field=deleted），
     * updateById 不会把 deleted=1 写入 SET，因此这里用原生 SQL 更新。
     */
    @Update("UPDATE sys_user SET username = #{username}, student_no = #{studentNo}, "
            + "status = #{status}, deleted = 1, update_time = NOW() WHERE id = #{id}")
    int markDeleted(@Param("id") Long id,
                    @Param("username") String username,
                    @Param("studentNo") String studentNo,
                    @Param("status") Integer status);
}
