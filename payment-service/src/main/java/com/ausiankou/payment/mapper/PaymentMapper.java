package com.ausiankou.payment.mapper;

import com.ausiankou.payment.dto.PaymentDto;
import com.ausiankou.payment.entity.Payment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentMapper {
    PaymentDto toDto(Payment payment);
}
