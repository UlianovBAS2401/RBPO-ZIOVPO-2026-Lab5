INSERT INTO users (id, username, password, full_name, email, phone, role, status, created_at)
VALUES
    (1, 'ivanov', '$2a$10$QA6WNJl0A4GQ95LAUL0w4OVqhWfkAc5gPJ5pz9REhHMacjiiifUi2', 'Иванов Иван Иванович', 'ivanov@example.com', '+79991112233', 'ROLE_USER', 'ACTIVE', CURRENT_TIMESTAMP),
    (2, 'petrov', '$2a$10$QA6WNJl0A4GQ95LAUL0w4OVqhWfkAc5gPJ5pz9REhHMacjiiifUi2', 'Петров Петр Сергеевич', 'petrov@example.com', '+79992223344', 'ROLE_USER', 'ACTIVE', CURRENT_TIMESTAMP),
    (3, 'sidorova', '$2a$10$QA6WNJl0A4GQ95LAUL0w4OVqhWfkAc5gPJ5pz9REhHMacjiiifUi2', 'Сидорова Анна Павловна', 'sidorova@example.com', '+79993334455', 'ROLE_USER', 'BLOCKED', CURRENT_TIMESTAMP),
    (4, 'admin', '$2a$10$ABGZwlkDQ0nZNJO6P8vEV.kGcR9Dv4Q5Bx0VzFRANxQknT8DN/KPy', 'Главный Администратор', 'admin@example.com', '+79990000001', 'ROLE_ADMIN', 'ACTIVE', CURRENT_TIMESTAMP),
    (5, 'operator', '$2a$10$qb6cG6M6oT7zClfn6k4PP./eO3sDLpnTHY57gQUtqAvCzUbLXvWLy', 'Оператор Расчетов', 'operator@example.com', '+79990000002', 'ROLE_OPERATOR', 'ACTIVE', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

INSERT INTO premises (id, address, area, user_id, created_at)
VALUES
    (1, 'г. Москва, ул. Ленина, д. 10, кв. 15', 54.50, 1, CURRENT_TIMESTAMP),
    (2, 'г. Москва, ул. Ленина, д. 10, кв. 16', 72.00, 2, CURRENT_TIMESTAMP),
    (3, 'г. Москва, ул. Гагарина, д. 5', 120.00, 3, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

INSERT INTO meters (id, premises_id, resource_type, serial_number, unit_rate, status, created_at)
VALUES
    (1, 1, 'COLD_WATER', 'MTR-CW-1001', 50.90, 'ACTIVE', CURRENT_TIMESTAMP),
    (2, 1, 'ELECTRICITY', 'MTR-EL-2001', 6.43, 'ACTIVE', CURRENT_TIMESTAMP),
    (3, 2, 'COLD_WATER', 'MTR-CW-1002', 50.90, 'ACTIVE', CURRENT_TIMESTAMP),
    (4, 3, 'GAS', 'MTR-GAS-3001', 7.80, 'ACTIVE', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

INSERT INTO meter_readings (id, meter_id, period, reading_value, verification_status, recorded_at)
VALUES
    (1, 1, '2026-08', 120.000, 'VERIFIED', CURRENT_TIMESTAMP - INTERVAL '2 month'),
    (2, 1, '2026-09', 128.500, 'VERIFIED', CURRENT_TIMESTAMP - INTERVAL '1 month'),
    (3, 2, '2026-09', 340.000, 'VERIFIED', CURRENT_TIMESTAMP - INTERVAL '1 month'),
    (4, 3, '2026-09', 85.000, 'VERIFIED', CURRENT_TIMESTAMP - INTERVAL '1 month')
ON CONFLICT (id) DO NOTHING;

INSERT INTO utility_charges (id, premises_id, meter_id, period, volume, amount, status, created_at)
VALUES
    (1, 1, 1, '2026-09', 8.500, 432.65, 'PAID', CURRENT_TIMESTAMP - INTERVAL '25 day'),
    (2, 1, 2, '2026-09', 55.000, 353.65, 'CALCULATED', CURRENT_TIMESTAMP - INTERVAL '20 day')
ON CONFLICT (id) DO NOTHING;

SELECT setval('users_id_seq', (SELECT COALESCE(MAX(id), 1) FROM users));
SELECT setval('premises_id_seq', (SELECT COALESCE(MAX(id), 1) FROM premises));
SELECT setval('meters_id_seq', (SELECT COALESCE(MAX(id), 1) FROM meters));
SELECT setval('meter_readings_id_seq', (SELECT COALESCE(MAX(id), 1) FROM meter_readings));
SELECT setval('utility_charges_id_seq', (SELECT COALESCE(MAX(id), 1) FROM utility_charges));
