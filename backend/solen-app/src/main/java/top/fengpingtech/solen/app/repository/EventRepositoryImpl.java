package top.fengpingtech.solen.app.repository;

import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import top.fengpingtech.solen.app.domain.EventDomain;

public class EventRepositoryImpl implements EventRepositoryCustom {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<EventDomain> findPage(Specification<EventDomain> specification, int offset, int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<EventDomain> query = cb.createQuery(EventDomain.class);
        Root<EventDomain> root = query.from(EventDomain.class);

        query.select(root).distinct(true);
        query.where(specification.toPredicate(root, query, cb));
        query.orderBy(cb.desc(root.get("eventId")));

        TypedQuery<EventDomain> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult(offset);
        typedQuery.setMaxResults(limit);
        return typedQuery.getResultList();
    }
}
