package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOPTYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryList() {
        String key = CACHE_SHOPTYPE_KEY;
        // 1、从redis中查询类型信息
        String typeJSON = stringRedisTemplate.opsForValue().get(key);
        // 2、判断是否存在
        if (StrUtil.isNotBlank(typeJSON)) {
            // 3、如果存在则返回
            List<ShopType> shopTypeList = JSONUtil.toList(typeJSON, ShopType.class);
            return Result.ok(shopTypeList);
        }
        // 4、如果不存在，从数据库中查询
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        // 5、数据库不存在，返回错误
        if (shopTypeList == null) {
            return Result.fail("此类型店铺不存在！");
        }
        // 6、数据库中存在，写入redis，返回数据
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shopTypeList));
        return Result.ok(shopTypeList);
    }
}
