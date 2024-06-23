set VERSION=0.0.2
docker build -t grozadanut/cloud-product-synchronizer:latest -t grozadanut/cloud-product-synchronizer:%VERSION% --target production .
docker push grozadanut/cloud-product-synchronizer:%VERSION%
docker push grozadanut/cloud-product-synchronizer:latest
PAUSE