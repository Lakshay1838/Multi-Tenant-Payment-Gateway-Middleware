package com.paymentgateway.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.paymentgateway.entity.IdempotencyRecord;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
}
