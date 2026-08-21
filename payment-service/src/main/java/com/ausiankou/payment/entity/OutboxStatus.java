package com.ausiankou.payment.entity;

public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED   // exhausted max attempts - needs manual/ops attention
}
