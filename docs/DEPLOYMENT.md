# Hyperfeeds VPS deployment

The production service is deployed by `.github/workflows/deploy.yml` whenever `main`
is updated. It publishes an immutable Docker image, uploads the production Compose
file, starts PostgreSQL and the API, and checks the health endpoint.

## GitHub Actions secrets

- `SERVER_IP`: `62.171.128.245`
- `SCP_USERNAME`: VPS SSH user
- `SCP_PASSWORD`: VPS SSH password
- `DOCKER_USERNAME`: Docker Hub username
- `DOCKER_PASSWORD`: Docker Hub access token

The VPS user must have passwordless `sudo` access to Docker and to `/opt/hyperfeeds`.
An SSH key should replace password authentication after the initial deployment.

## VPS files

Keep production secrets in `/opt/hyperfeeds/.env` with owner `root:root` and mode
`0600`. The deployment workflow never replaces this file.

The API is temporarily exposed at:

```text
http://62.171.128.245:8081/api
```

The Paynow callback is:

```text
http://62.171.128.245:8081/api/payments/paynow/callback
```

Open TCP port `8081` for temporary IP-based access. Replace it with an HTTPS reverse
proxy and close the public port before production launch.

## Manual operations

```bash
cd /opt/hyperfeeds
sudo HYPERFEEDS_IMAGE=DOCKER_USER/hyperfeeds-service:TAG docker compose --env-file .env up -d
sudo docker compose --env-file .env ps
sudo docker compose --env-file .env logs --tail=200 api
```

PostgreSQL data is stored in the named Docker volume `hyperfeeds-postgres` and is not
removed during normal deployments.
