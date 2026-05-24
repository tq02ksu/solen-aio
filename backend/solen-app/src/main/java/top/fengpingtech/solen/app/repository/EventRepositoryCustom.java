package top.fengpingtech.solen.app.repository;

import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import top.fengpingtech.solen.app.domain.EventDomain;

public interface EventRepositoryCustom {
    List<EventDomain> findPage(Specification<EventDomain> specification, int offset, int limit);
}
