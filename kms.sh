
docker run -p 4599:8080 --mount type=bind,source="$(pwd)"/data_kms,target=/data nsmithuk/local-kms
