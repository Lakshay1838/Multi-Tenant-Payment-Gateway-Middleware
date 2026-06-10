package com.paymentgateway.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.paymentgateway.entity.TransactionRecord;

@Repository
public interface TransactionRecordRepository extends JpaRepository<TransactionRecord, UUID> {

    Optional<TransactionRecord> findByProviderTransactionId(String providerTransactionId);
}
