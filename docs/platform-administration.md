# Platform Administration

## Access and Installation

On commercial-v1, /platform and /platform/** require ROLE_PLATFORM_ADMIN.
ROLE_ADMIN and ROLE_CAJERO remain restricted to their own tenant.
The platform area has a separate header and supports the existing Claro/Sunset themes.

V1 through V9 are immutable. V10 creates ROLE_PLATFORM_ADMIN if missing.
When no platform administrator exists, only the lowest-ID existing ROLE_ADMIN
receives it. Existing users, tenant relationships and operational data are retained.
For a fresh installation, SetupService creates the initial user with both
ROLE_ADMIN and ROLE_PLATFORM_ADMIN after Flyway completes.

Platform administrators log in at /admin/login and land on /platform/tenants.
Their AppUser still has a mandatory tenant_id. Global services never resolve
CurrentTenant and do not enable switching into another tenant.

## Tenant Management

The platform area supports searching, creating, viewing, editing, suspending and
reactivating tenants. Codes are stable lowercase slugs and cannot be edited.
Usernames remain globally unique. There is no physical tenant deletion.

Provisioning is one transaction: Tenant, Business, BusinessSettings, MATRIZ branch,
PRINCIPAL warehouse, CAJA01 register, one administrator with ROLE_ADMIN only,
Publico General customer, nine product categories and six expense categories.
No products, suppliers, purchases, sales or inventory are copied.
A failure rolls back every inserted record.

Suspension changes Tenant.active only. SessionRevocationService expires normal
users' registered sessions immediately after transaction commit, not on rollback.
UserService applies the same policy to edits, toggles and deletion/deactivation.
AccountAccessFilter uses standard Spring Security logout as a request-time backstop,
invalidating HttpSession, clearing SecurityContext and deleting JSESSIONID.
Suspended sessions redirect to /admin/login?expired; a disabled new login goes to
?suspended. Incorrect credentials still go to ?error, and valid users without the
required role receive 403 without logout. Login messages are mutually exclusive.
Reactivation allows a new login without restoring old sessions or changing AppUser.active.
An active platform administrator can access /platform even if their own tenant
is suspended; normal operational routes for that tenant remain blocked.

Tenant administrators cannot grant or remove platform privileges, edit platform
users, reset their passwords, deactivate them or delete them through UserService.
Platform users are excluded from the normal user list.
Platform detail allows resetting tenant administrators' passwords with BCrypt
and reactivating inactive administrators. Current passwords and hashes are not
included in the view models.

## Public Catalog and Deferred Work

In normal mode, / redirects anonymous visitors to /admin/login. Authenticated
PLATFORM_ADMIN, ADMIN and CAJERO go to /platform/tenants, /admin and /admin/pos,
respectively. RootController never invokes PublicTenantResolver or product queries.
SetupController is unchanged: without configuration, / and /setup render setup.

The global storefront is disabled: /producto/** and /catalog/** return 404 for
everyone. CatalogService, models, templates and the resolver remain reusable,
but no configuration, default tenant or session can enable a global storefront.
Product/catalog image uploads and login assets remain accessible.

SessionRegistry is shared with maximumSessions(1) and supported by
HttpSessionEventPublisher. This is a single-JVM policy; multiple JAR instances
will require Spring Session + Redis or an equivalent distributed registry.
Distributed sessions are deferred, not implemented here.

Deferred: tenant-aware login with UNIQUE(tenant_id, username), domains/subdomains,
public tenant routing, audited impersonation, billing, subscriptions and email
password recovery. No X-Tenant header, session tenant override or tenant query
parameter is supported.

## Verification

PlatformIntegrationTest uses real MVC, Spring Security login, method authorization,
JPA and transactions on an isolated test database. Only image filesystem and
external product-network collaborators are mocked.
PlatformMigrationTest uses mandatory MySQL 8.0.36 and MariaDB 11.4.5 Testcontainers:
empty schema through V10 and real SetupService in a clean child process; V9
upgrade with missing role, existing role, or existing platform administrator.
It compares every operational table before and after V10.
TenantMigrationTest continues checking MySQL/MariaDB indexes and foreign keys.

CI runs clean test twice, clean package (including all tests again), and the
packaged JAR from an empty directory, followed by PackagedNormalModeSmoke with
isolated MySQL 8, real setup, HEAD/GET root redirects, login, retired-route 404,
session fixation and suspended-session cookie replay rejection.
Docker is mandatory; there is no development
database or localhost fallback for migration tests.

## Query Audit

Visual QA: PlatformPreviewServer provides a loopback-only H2 fixture, excluded
from the production JAR. scripts/verify-platform.cjs uses Playwright/Chrome and
requires PREVIEW_URL, with optional PLAYWRIGHT_MODULE and CHROME_PATH.
It exercises creation through the browser, both themes, four platform screens,
1440/390/320px widths, image loading, field contrast and mobile navigation.
Screenshots and measurements are written to target/platform-visual.

Operational repository calls and aggregates use tenant IDs from CurrentTenant.
Global queries are confined to platform metadata/provisioning, setup and the
explicit public resolver. Authentication/account checks necessarily look up the
globally unique username; they do not select a tenant from request data.
Global Role lookups and external public barcode providers are not tenant-owned data.

The audit also hardened Category/Supplier form binding against internal ID or
tenant assignment, changed foreign product GETs to 404, and rejects foreign
category/supplier/customer references instead of silently accepting them.

Remaining performance limitation inherited from the sales list: collection fetch
pagination can run in memory within the tenant. Platform listing is bounded to
20 tenants per page, with per-tenant counts and admin lookups.
