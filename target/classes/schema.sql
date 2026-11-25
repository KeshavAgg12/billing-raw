CREATE TABLE IF NOT EXISTS set_type (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    sizes VARCHAR(200) NOT NULL
);

CREATE TABLE IF NOT EXISTS article (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    article_number VARCHAR(50),
    company_name VARCHAR(100),
    color VARCHAR(50),
    cp DECIMAL(10,2) NOT NULL,
    sp DECIMAL(10,2) NOT NULL
);

CREATE TABLE IF NOT EXISTS article_set_type (
    article_id BIGINT NOT NULL,
    set_type_id BIGINT NOT NULL,
    PRIMARY KEY (article_id, set_type_id),
    FOREIGN KEY (article_id) REFERENCES article(id),
    FOREIGN KEY (set_type_id) REFERENCES set_type(id)
);

CREATE TABLE IF NOT EXISTS stock (
    article_id BIGINT NOT NULL,
    size INT NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (article_id, size),
    FOREIGN KEY (article_id) REFERENCES article(id)
);

CREATE TABLE IF NOT EXISTS purchase (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    article_id BIGINT NOT NULL,
    size INT NOT NULL,
    quantity INT NOT NULL,
    cp DECIMAL(10,2) NOT NULL,
    purchase_date DATE NOT NULL,
    FOREIGN KEY (article_id) REFERENCES article(id)
);

CREATE TABLE IF NOT EXISTS customer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(30),
    address VARCHAR(200)
);

CREATE TABLE IF NOT EXISTS invoice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    invoice_date DATE NOT NULL,
    total DECIMAL(12,2) NOT NULL,
    FOREIGN KEY (customer_id) REFERENCES customer(id)
);

CREATE TABLE IF NOT EXISTS invoice_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    article_id BIGINT NOT NULL,
    type VARCHAR(10) NOT NULL,
    set_type_id BIGINT,
    size INT,
    quantity INT NOT NULL,
    pieces INT NOT NULL,
    rate DECIMAL(10,2) NOT NULL,
    cp DECIMAL(10,2) NOT NULL,
    line_total DECIMAL(12,2) NOT NULL,
    FOREIGN KEY (invoice_id) REFERENCES invoice(id),
    FOREIGN KEY (article_id) REFERENCES article(id)
);

CREATE TABLE IF NOT EXISTS payment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    payment_date DATE NOT NULL,
    note VARCHAR(200),
    FOREIGN KEY (customer_id) REFERENCES customer(id)
)
