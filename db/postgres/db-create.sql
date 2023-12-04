CREATE DATABASE wallet_db;
CREATE USER wallet_user WITH PASSWORD 'wallet_pass';
GRANT CONNECT ON DATABASE wallet_db TO wallet_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO wallet_user;
