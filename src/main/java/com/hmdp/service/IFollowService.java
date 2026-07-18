package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注 or 取消关注
     * @param followUserId 关注 or 取消关注的用户id
     * @param isFollow false：取消关注  true：关注
     * @return
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     * 判断当前用户是否关注了博主
     * @param followUserId 博主id
     * @return
     */
    Result isFollow(Long followUserId);

    /**
     * 查询共同关注
     * @param followUserId 关注的用户id
     * @return
     */
    Result followCommons(Long followUserId);
}
