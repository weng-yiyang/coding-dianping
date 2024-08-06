package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        // TODO 1、从redis中查询商铺缓存
        String shopJSON = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        // TODO 2、判断是否存在
        if (StrUtil.isNotBlank(shopJSON)) {
            // TODO 3、存在，直接返回
            // 返回需要转成java对象返回
            Shop shop = JSONUtil.toBean(shopJSON, Shop.class);

        }


        // TODO 4、不存在，根据id查询数据库

        // TODO 5、数据库中不存在，返回错误

        // TODO 6、数据库中存在，将商铺数据写入redis，再返回商铺信息
        return null;
    }
}
