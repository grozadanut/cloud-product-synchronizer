set VERSION=0.0.5
docker build -t grozadanut/cloud-product-synchronizer:latest --target production .
docker push grozadanut/cloud-product-synchronizer:latest
PAUSE