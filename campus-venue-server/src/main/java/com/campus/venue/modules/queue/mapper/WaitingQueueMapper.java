package com.campus.venue.modules.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.venue.modules.queue.entity.WaitingQueue;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WaitingQueueMapper extends BaseMapper<WaitingQueue> {
}
