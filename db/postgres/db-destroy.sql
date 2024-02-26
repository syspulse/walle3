REASSIGN OWNED BY wallet_user TO postgres;  -- or some other trusted role
DROP OWNED BY wallet_user;

DROP TABLE wallet_secret;

-- SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'wallet_db';
DROP DATABASE wallet_db WITH (FORCE);

DROP USER wallet_user;

