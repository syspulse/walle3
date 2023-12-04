CREATE DATABASE IF NOT EXISTS wallet_db;
CREATE USER IF NOT EXISTS 'wallet_user'@'%' IDENTIFIED BY 'wallet_pass';
GRANT ALL PRIVILEGES ON wallet_db.* TO 'wallet_user'@'%' WITH GRANT OPTION;

