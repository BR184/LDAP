# LDAP Offline Package Standard

## 1. Purpose

This document is the release contract for the internal offline Docker Compose package.
Every package under `offline-images` must follow this document before it is copied to
an offline Linux server.

LDAP is a shared external interface. GitLab, Mattermost, Roundcube, Nextcloud and
other clients must use the same port, base DN, bind DN and login filter.

## 2. Source Of Truth

The following files define the package contract:

- `offline-images/.env`: release ports, credentials and service settings.
- `offline-images/docker-compose.yml`: services, mounts, health checks and networking.
- `offline-images/openldap/bootstrap/*.ldif`: first-start LDAP directory content.
- `offline-images/DEPLOY-OFFLINE.md`: deployment commands for the operator.
- `scripts/verify-offline-package.ps1`: mandatory pre-release validation.

Do not copy configuration from an older package by hand. Update the current package,
then run the validator.

## 3. Stable External Contract

These values must not change silently:

| Item | Required value |
| --- | --- |
| Web UI host port | `80` |
| Backend host port | `8081` |
| MySQL host port | `3307` |
| LDAP host port | `389` |
| LDAP protocol | `ldap://` |
| LDAP domain | `corp.local` |
| LDAP base DN | `dc=corp,dc=local` |
| People OU | `ou=people,dc=corp,dc=local` |
| Groups OU | `ou=groups,dc=corp,dc=local` |
| Bind DN | `cn=admin,dc=corp,dc=local` |
| Internal application LDAP URL | `ldap://openldap:389` |
| External client LDAP URL | `ldap://<server-ip>:389` |

The LDAP password has two configuration names and they must be identical:

```text
LDAP_ADMIN_PASSWORD
APP_LDAP_BIND_PASSWORD
```

The first value initializes OpenLDAP on an empty data directory. The second value is
used by the IDM backend. Mail and other external systems must use the same bind
password.

Changing `LDAP_ADMIN_PASSWORD` after `data/openldap` has been initialized does not
change the existing LDAP password. A password change is an LDAP data migration, not
an `.env` edit.

## 4. LDAP Initialization Contract

`openldap/bootstrap/01-base-ou.ldif` must contain the base OUs and this initial account:

```ldif
dn: uid=admin,ou=people,dc=corp,dc=local
objectClass: inetOrgPerson
uid: admin
cn: admin
sn: admin
mail: admin@crowncad.com
employeeType: ENABLED
userPassword: 123456
```

The initial account is for first login and deployment verification only. Change its
password immediately after the first successful login and update all client bind
configurations.

The standard user filter is:

```text
(&(objectClass=inetOrgPerson)(employeeType=ENABLED)(|(uid=?)(mail=?)))
```

The standard mailbox filter is:

```text
(&(objectClass=inetOrgPerson)(mail=?)(employeeType=ENABLED))
```

## 5. Compose Service Contract

The Compose service names are stable:

```text
openldap
mysql
idm-app
idm-web
```

Required internal addresses:

```text
idm-app -> mysql:3306
idm-app -> openldap:389
idm-web -> idm-app:8081
```

Do not write the deployment server IP into `docker-compose.yml`, Dockerfiles or the
application image. The server IP belongs only in an external client's LDAP URL.

All data mounts must be relative to the package root:

```text
./data/mysql
./data/openldap/database
./data/openldap/config
./imports
```

## 6. Clean Package Layout

```text
offline-images/
├─ .env
├─ docker-compose.yml
├─ DEPLOY-OFFLINE.md
├─ CHECKSUMS.txt
├─ images/corp-idm-platform-internal.tar
├─ images/corp-idm-web-internal.tar
├─ images/mysql-8.0.tar
├─ images/openldap-1.5.0.tar
├─ openldap/bootstrap/01-base-ou.ldif
├─ data/mysql/.gitkeep
├─ data/openldap/config/.gitkeep
├─ data/openldap/database/.gitkeep
└─ imports/.gitkeep
```

A clean release must not contain a developer machine's live MySQL or LDAP database.
Migration data must be delivered separately with rollback instructions.

## 7. Packaging Procedure

Run these checks from `E:\ldap`:

```powershell
mvn test
Set-Location frontend
npm run check
npm run build
Set-Location ..
```

Build and export all four images:

```powershell
docker build -t corp-idm-platform:internal .
docker build -t corp-idm-web:internal frontend
docker save -o offline-images/images/corp-idm-platform-internal.tar corp-idm-platform:internal
docker save -o offline-images/images/corp-idm-web-internal.tar corp-idm-web:internal
docker save -o offline-images/images/mysql-8.0.tar mysql:8.0
docker save -o offline-images/images/openldap-1.5.0.tar osixia/openldap:1.5.0
```

Before validation, remove generated runtime files from `data/mysql` and
`data/openldap`, then restore the `.gitkeep` files.

## 8. Mandatory Validation

Run the validator after assembling the package:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-offline-package.ps1 -PackageRoot .\offline-images
```

It checks required files, Compose syntax and service names, fixed ports, service-name
URLs, LDAP password consistency, the admin LDIF, clean data directories, image tags,
image archives and hardcoded private IP addresses.

Do not release a package if the validator reports a failure.

## 9. Offline Server Verification

```bash
cd /path/to/offline-images
sha256sum -c CHECKSUMS.txt
docker compose config
docker compose up -d
docker compose ps
```

Verify LDAP from an external client:

```bash
ldapsearch -x \
  -H ldap://<LDAP_SERVER_IP>:389 \
  -D "cn=admin,dc=corp,dc=local" \
  -W \
  -b "ou=people,dc=corp,dc=local" \
  "(uid=admin)" dn uid mail employeeType
```

`nc -vz <LDAP_SERVER_IP> 389` is only a TCP test. It is not an LDAP bind or user
authentication test. Every client integration must pass an LDAP search and a real
user password-bind test.

## 10. Client Integration Rules

GitLab, Mattermost, Roundcube, Nextcloud and other clients must use:

```text
Server: <LDAP_SERVER_IP>
Port: 389
Base DN: ou=people,dc=corp,dc=local
Bind DN: cn=admin,dc=corp,dc=local
User attribute: uid
Email attribute: mail
User filter: (&(objectClass=inetOrgPerson)(employeeType=ENABLED)(|(uid=?)(mail=?)))
```

After changing `.env`, recreate client containers. `docker compose restart` alone
does not apply changed environment variables:

```bash
docker compose up -d --force-recreate
```

## 11. Change Management

These changes require an explicit compatibility review, release note and client
configuration update:

- LDAP port or protocol.
- LDAP base DN, OU or Bind DN.
- Login or mailbox filters.
- LDAP attribute names.
- Compose service names or internal ports.
- Persistent data layout.

Do not silently change `.env` defaults in an image-only rebuild. A breaking change
requires a new package version and updated client templates.

## 12. Release Checklist

- [ ] Backend tests pass.
- [ ] Frontend check and build pass.
- [ ] All four image archives exist.
- [ ] MySQL and LDAP data directories are clean.
- [ ] The bootstrap LDIF contains `admin`.
- [ ] LDAP external port is `389`.
- [ ] `LDAP_ADMIN_PASSWORD` equals `APP_LDAP_BIND_PASSWORD`.
- [ ] `APP_LDAP_URL` uses `openldap:389`.
- [ ] No server IP is hardcoded in Compose or Dockerfiles.
- [ ] `docker compose config` passes.
- [ ] `sha256sum -c CHECKSUMS.txt` passes.
- [ ] LDAP search and user bind were tested.
- [ ] Package compatibility notes are recorded.
