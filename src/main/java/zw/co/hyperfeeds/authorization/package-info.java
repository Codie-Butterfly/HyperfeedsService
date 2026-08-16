/**
 * Authorization policies shared by feature modules.
 *
 * <p>Branch-owned service methods should use
 * {@code @PreAuthorize("@branchAccess.canAccess(authentication, #branchId)")}.
 * Role gates use Spring's standard {@code hasRole} and {@code hasAnyRole} expressions.
 */
package zw.co.hyperfeeds.authorization;
