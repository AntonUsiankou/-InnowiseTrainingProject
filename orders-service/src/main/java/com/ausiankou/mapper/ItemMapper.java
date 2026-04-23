package com.ausiankou.mapper;

import com.ausiankou.dto.ItemInfo;
import com.ausiankou.entity.Item;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ItemMapper {
    Item toEntity(ItemInfo itemInfo);
    ItemInfo toDto(Item item);
}
