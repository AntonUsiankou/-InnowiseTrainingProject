package com.ausiankou.user.mapper;

import com.ausiankou.user.dto.UserCreateRequest;
import com.ausiankou.user.dto.UserDto;
import com.ausiankou.user.dto.UserUpdateRequest;
import com.ausiankou.user.entity.User;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = PaymentCardMapper.class)
public interface UserMapper {

    UserDto toDto(User user);

    User toEntity(UserCreateRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(UserUpdateRequest request, @MappingTarget User user);
}
