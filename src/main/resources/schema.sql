CREATE TABLE IF NOT EXISTS transaction_records (
    id UUID PRIMARY KEY,
    merchant_id VARCHAR(100) NOT NULL,
    amount NUMERIC(15,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_transaction_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);