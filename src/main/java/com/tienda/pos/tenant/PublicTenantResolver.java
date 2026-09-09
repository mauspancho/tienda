package com.tienda.pos.tenant;

/** Resolves the public storefront independently of the authenticated operator. */
public interface PublicTenantResolver {
    Tenant resolve();
}
