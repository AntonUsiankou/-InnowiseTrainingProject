package com.ausiankou.payment.mapper;

import com.ausiankou.payment.dto.PaymentRequest;
import com.ausiankou.payment.dto.PaymentResponse;
import com.ausiankou.payment.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    PaymentMapper INSTANCE = Mappers.getMapper(PaymentMapper.class);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "timestamp", ignore = true)
    Payment toEntity(PaymentRequest request);

    @Mapping(source = "status", target = "status")
    PaymentResponse toResponse(Payment payment);
}
