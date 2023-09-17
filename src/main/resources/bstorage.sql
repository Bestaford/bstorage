CREATE TABLE IF NOT EXISTS FILES (
    ID VARCHAR PRIMARY KEY,
    FILE_ID VARCHAR NOT NULL,
    TAGS VARCHAR,
    USER_ID BIGINT NOT NULL,
    DATETIME TIMESTAMP NOT NULL
)