package com.ausiankou.mapper;

import com.ausiankou.dto.UserCreateDto;
import com.ausiankou.dto.UserDto;
import com.ausiankou.entity.User;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring",
        uses = {PaymentCardMapper.class},
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    @Mapping(target = "paymentCards", ignore = true)
    UserDto toDto(User user);

    List<UserDto> toDtoList(List<User> users);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "paymentCards", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "role", constant = "USER")
    User toEntity(UserCreateDto createDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "paymentCards", ignore = true)
    void updateEntityFromDto(UserDto userDto, @MappingTarget User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void partialUpdate(UserDto userDto, @MappingTarget User user);

    @Named("toDtoIgnoreCards")
    @Mapping(target = "paymentCards", ignore = true)
    UserDto toDtoIgnoreCards(User user);
}
