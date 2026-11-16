package br.com.balancecontrol.order;

import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Optional filters built as real predicates (instead of "(:p is null or col = :p)"), so
 * PostgreSQL always sees a query shape that can use the matching index.
 */
final class OrderSpecs {

    private OrderSpecs() {
    }

    static Specification<Order> filter(Long agencyId, OrderStatus status, UUID externalId) {
        Specification<Order> spec = Specification.where(null);
        if (agencyId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("agency").get("id"), agencyId));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (externalId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("externalId"), externalId));
        }
        return spec;
    }
}
