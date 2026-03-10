package com.ausiankou.mapper;

import com.ausiankou.dto.PaymentCardDto;
import com.ausiankou.entity.PaymentCard;
import com.ausiankou.entity.User;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring",
        uses = {UserMapper.class},
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PaymentCardMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "user", source = "user", qualifiedByName = "toDtoIgnoreCards")
    PaymentCardDto toDto(PaymentCard card);

    List<PaymentCardDto> toDtoList(List<PaymentCard> cards);

    default PaymentCard toEntityWithUser(PaymentCardDto cardDto, User user){
        if(cardDto == null) return null;
        return PaymentCard.builder()
                .number(cardDto.getNumber())
                .holder(cardDto.getHolder())
                .expirationDate(cardDto.getExpirationDate())
                .active(cardDto.getActive() != null ? cardDto.getActive() : true)
                .user(user)
                .build();
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    void updatedEntityFromDto(PaymentCardDto cardDto, @MappingTarget PaymentCard paymentCard);

    @Named("toDtoListIgnoreUser")
    default List<PaymentCardDto> toDtoListIgnoreUser(List<PaymentCard> cards){
        return cards.stream()
                .map(this::toDtoIgnoreUser)
                .toList();
    }

    @Named("toDtoIgnoreUser")
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "userId",source = "user.id")
    PaymentCardDto toDtoIgnoreUser(PaymentCard card);
}
