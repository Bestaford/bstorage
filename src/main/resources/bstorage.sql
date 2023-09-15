CREATE TABLE IF NOT EXISTS files (
    file_unique_id VARCHAR PRIMARY KEY,
    file_id VARCHAR NOT NULL,
    caption VARCHAR NOT NULL,
    user_id BIGINT NOT NULL,
    datetime TIMESTAMP NOT NULL
)