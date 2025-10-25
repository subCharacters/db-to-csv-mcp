CREATE TABLE items (
    id INT PRIMARY KEY,
    name VARCHAR(100),
    price INT
);

INSERT INTO items (id, name, price) VALUES
    (1, 'apple', 100),
    (2, 'banana', 120),
    (3, 'orange', 150),
    (4, 'grape', 180),
    (5, 'kiwi', 200),
    (6, 'lemon', 130),
    (7, 'pineapple', 320),
    (8, 'strawberry', 260);

CREATE TABLE customers (
    id INT PRIMARY KEY,
    name VARCHAR(120),
    tier VARCHAR(20),
    created_at TIMESTAMP
);

INSERT INTO customers (id, name, tier, created_at) VALUES
    (1, 'Alice Kim', 'GOLD', CURRENT_TIMESTAMP),
    (2, 'Brian Lee', 'SILVER', CURRENT_TIMESTAMP),
    (3, 'Chloe Park', 'BRONZE', CURRENT_TIMESTAMP);

CREATE TABLE orders (
    id INT PRIMARY KEY,
    customer_id INT,
    item_id INT,
    quantity INT,
    order_total INT,
    ordered_at TIMESTAMP,
    status VARCHAR(20)
);

INSERT INTO orders (id, customer_id, item_id, quantity, order_total, ordered_at, status) VALUES
    (1001, 1, 1, 3, 300, CURRENT_TIMESTAMP, 'PAID'),
    (1002, 1, 8, 1, 260, CURRENT_TIMESTAMP, 'PAID'),
    (1003, 2, 5, 2, 400, CURRENT_TIMESTAMP, 'SHIPPED'),
    (1004, 3, 3, 1, 150, CURRENT_TIMESTAMP, 'PENDING'),
    (1005, 2, 6, 4, 520, CURRENT_TIMESTAMP, 'PAID');
