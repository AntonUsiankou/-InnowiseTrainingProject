package com.ausiankou.user.mapper;

import com.ausiankou.user.dto.PaymentCardCreateRequest;
import com.ausiankou.user.dto.PaymentCardDto;
import com.ausiankou.user.entity.PaymentCard;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentCardMapper {

    @Mapping(source = "user.id", target = "userId")
    PaymentCardDto toDto(PaymentCard card);

    PaymentCard toEntity(PaymentCardCreateRequest request);
}
